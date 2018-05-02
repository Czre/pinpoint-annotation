/*
 * Copyright 2014 NAVER Corp.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.navercorp.pinpoint.rpc.server;

import com.navercorp.pinpoint.common.annotations.VisibleForTesting;
import com.navercorp.pinpoint.common.util.Assert;
import com.navercorp.pinpoint.common.util.CpuUtils;
import com.navercorp.pinpoint.common.util.PinpointThreadFactory;
import com.navercorp.pinpoint.rpc.PinpointSocket;
import com.navercorp.pinpoint.rpc.PinpointSocketException;
import com.navercorp.pinpoint.rpc.cluster.ClusterOption;
import com.navercorp.pinpoint.rpc.packet.ServerClosePacket;
import com.navercorp.pinpoint.rpc.server.handler.ServerStateChangeEventHandler;
import com.navercorp.pinpoint.rpc.stream.DisabledServerStreamChannelMessageListener;
import com.navercorp.pinpoint.rpc.stream.ServerStreamChannelMessageListener;
import com.navercorp.pinpoint.rpc.util.LoggerFactorySetup;
import com.navercorp.pinpoint.rpc.util.TimerFactory;
import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelHandler;
import org.jboss.netty.channel.group.ChannelGroup;
import org.jboss.netty.channel.group.DefaultChannelGroup;
import org.jboss.netty.channel.socket.nio.NioServerBossPool;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;
import org.jboss.netty.channel.socket.nio.NioWorkerPool;
import org.jboss.netty.util.ThreadNameDeterminer;
import org.jboss.netty.util.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * @author Taejin Koo
 */
public class PinpointServerAcceptor implements PinpointServerConfig {

    private static final long DEFAULT_TIMEOUT_MILLIS = 3 * 1000;
    private static final long CHANNEL_CLOSE_MAXIMUM_WAITING_TIME_MILLIS = 3 * 1000;
    private static final int HEALTH_CHECK_INTERVAL_TIME_MILLIS = 5 * 60 * 1000;
    // 获取当前可用的CPU数量 * 2 这一步是为了获取系统资源而做的处理
    // 为什么？
    private static final int WORKER_COUNT = CpuUtils.workerCount();

    static {
        LoggerFactorySetup.setupSlf4jLoggerFactory();
    }

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ChannelGroup channelGroup = new DefaultChannelGroup("PinpointServerFactory");
    private final PinpointServerChannelHandler nettyChannelHandler = new PinpointServerChannelHandler();
    private final Timer healthCheckTimer;
    private final HealthCheckManager healthCheckManager;
    private final Timer requestManagerTimer;
    private final ClusterOption clusterOption;
    private volatile boolean released;
    private ServerBootstrap bootstrap;
    private InetAddress[] ignoreAddressList;
    private Channel serverChannel;
    private ServerMessageListener messageListener = SimpleServerMessageListener.SIMPLEX_INSTANCE;
    private ServerStreamChannelMessageListener serverStreamChannelMessageListener = DisabledServerStreamChannelMessageListener.INSTANCE;
    private List<ServerStateChangeEventHandler> stateChangeEventHandler = new ArrayList<ServerStateChangeEventHandler>();
    private long defaultRequestTimeout = DEFAULT_TIMEOUT_MILLIS;

    public PinpointServerAcceptor() {
        // 获取一个初始化的集群配置 默认状态是false,id为"",roles为空list
        this(ClusterOption.DISABLE_CLUSTER_OPTION);
    }

    public PinpointServerAcceptor(ClusterOption clusterOption) {
        ServerBootstrap bootstrap = createBootStrap(1, WORKER_COUNT);
        setOptions(bootstrap);
        addPipeline(bootstrap);
        this.bootstrap = bootstrap;

        this.healthCheckTimer = TimerFactory.createHashedWheelTimer("PinpointServerSocket-HealthCheckTimer", 50, TimeUnit.MILLISECONDS, 512);
        this.healthCheckManager = new HealthCheckManager(healthCheckTimer, channelGroup);

        this.requestManagerTimer = TimerFactory.createHashedWheelTimer("PinpointServerSocket-RequestManager", 50, TimeUnit.MILLISECONDS, 512);

        this.clusterOption = clusterOption;
    }

    /**
     * 这个方法创建两个NIO线程组 Boss和worker
     * 并返回一个serverBootstrap对象 将创建boss和worker线程组放入SocketChannelFactory
     *
     * @param bossCount   1个老板
     * @param workerCount double工人？
     * @return
     */
    private ServerBootstrap createBootStrap(int bossCount, int workerCount) {
        // profiler, collector
        // 创建缓存线程池
        ExecutorService boss = Executors.newCachedThreadPool(new PinpointThreadFactory("Pinpoint-Server-Boss", true));
        NioServerBossPool nioServerBossPool = new NioServerBossPool(boss, bossCount, ThreadNameDeterminer.CURRENT);

        ExecutorService worker = Executors.newCachedThreadPool(new PinpointThreadFactory("Pinpoint-Server-Worker", true));
        NioWorkerPool nioWorkerPool = new NioWorkerPool(worker, workerCount, ThreadNameDeterminer.CURRENT);

        NioServerSocketChannelFactory nioClientSocketChannelFactory = new NioServerSocketChannelFactory(nioServerBossPool, nioWorkerPool);
        return new ServerBootstrap(nioClientSocketChannelFactory);
    }

    /**
     * Netty的属性设置
     * @param bootstrap
     */
    private void setOptions(ServerBootstrap bootstrap) {
        // is read/write timeout necessary? don't need it because of NIO?
        // write timeout should be set through additional interceptor. write
        // timeout exists.

        // tcp setting
        // TCP_NODELAY就是用于启用或关于Nagle算法。如果要求高实时性，有数据发送时就马上发送，就将该选项设置为true关闭Nagle算法；如果要减少发送次数减少网络交互，就设置为false等累积一定大小后再发送。默认为false。
        bootstrap.setOption("child.tcpNoDelay", true);
        bootstrap.setOption("child.keepAlive", true);
        // buffer setting
        bootstrap.setOption("child.sendBufferSize", 1024 * 64);
        bootstrap.setOption("child.receiveBufferSize", 1024 * 64);

        // bootstrap.setOption("child.soLinger", 0);
    }

    private void addPipeline(ServerBootstrap bootstrap) {
        ServerPipelineFactory serverPipelineFactory = new ServerPipelineFactory(nettyChannelHandler);
        bootstrap.setPipelineFactory(serverPipelineFactory);
    }

    @VisibleForTesting
    void setPipelineFactory(ChannelPipelineFactory channelPipelineFactory) {
        if (channelPipelineFactory == null) {
            throw new NullPointerException("channelPipelineFactory must not be null");
        }
        bootstrap.setPipelineFactory(channelPipelineFactory);
    }

    public void bind(String host, int port) throws PinpointSocketException {
        InetSocketAddress bindAddress = new InetSocketAddress(host, port);
        bind(bindAddress);
    }

    /**
     * 创建阻塞线程绑定到指定本地地址的新通道 知道绑定成功
     * @param bindAddress
     * @throws PinpointSocketException
     */
    public void bind(InetSocketAddress bindAddress) throws PinpointSocketException {
        if (released) {
            return;
        }
        logger.info("bind() {}", bindAddress);
        this.serverChannel = bootstrap.bind(bindAddress);
        // 5分钟时间
        healthCheckManager.start(HEALTH_CHECK_INTERVAL_TIME_MILLIS);
    }

    private DefaultPinpointServer createPinpointServer(Channel channel) {
        DefaultPinpointServer pinpointServer = new DefaultPinpointServer(channel, this);
        return pinpointServer;
    }

    @Override
    public long getDefaultRequestTimeout() {
        return defaultRequestTimeout;
    }

    public void setDefaultRequestTimeout(long defaultRequestTimeout) {
        this.defaultRequestTimeout = defaultRequestTimeout;
    }

    private boolean isIgnoreAddress(Channel channel) {
        if (ignoreAddressList == null) {
            return false;
        }
        final InetSocketAddress remoteAddress = (InetSocketAddress) channel.getRemoteAddress();
        if (remoteAddress == null) {
            return false;
        }
        InetAddress address = remoteAddress.getAddress();
        for (InetAddress ignore : ignoreAddressList) {
            if (ignore.equals(address)) {
                return true;
            }
        }
        return false;
    }

    public void setIgnoreAddressList(InetAddress[] ignoreAddressList) {
        Assert.requireNonNull(ignoreAddressList, "ignoreAddressList must not be null");

        this.ignoreAddressList = ignoreAddressList;
    }

    @Override
    public ServerMessageListener getMessageListener() {
        return messageListener;
    }

    public void setMessageListener(ServerMessageListener messageListener) {
        Assert.requireNonNull(messageListener, "messageListener must not be null");

        this.messageListener = messageListener;
    }

    @Override
    public List<ServerStateChangeEventHandler> getStateChangeEventHandlers() {
        return stateChangeEventHandler;
    }

    public void addStateChangeEventHandler(ServerStateChangeEventHandler stateChangeEventHandler) {
        Assert.requireNonNull(stateChangeEventHandler, "stateChangeEventHandler must not be null");

        this.stateChangeEventHandler.add(stateChangeEventHandler);
    }

    @Override
    public ServerStreamChannelMessageListener getStreamMessageListener() {
        return serverStreamChannelMessageListener;
    }

    public void setServerStreamChannelMessageListener(ServerStreamChannelMessageListener serverStreamChannelMessageListener) {
        Assert.requireNonNull(serverStreamChannelMessageListener, "serverStreamChannelMessageListener must not be null");

        this.serverStreamChannelMessageListener = serverStreamChannelMessageListener;
    }

    @Override
    public Timer getRequestManagerTimer() {
        return requestManagerTimer;
    }

    @Override
    public ClusterOption getClusterOption() {
        return clusterOption;
    }

    public void close() {
        synchronized (this) {
            if (released) {
                return;
            }
            released = true;
        }
        healthCheckManager.stop();
        healthCheckTimer.stop();

        closePinpointServer();

        if (serverChannel != null) {
            ChannelFuture close = serverChannel.close();
            close.awaitUninterruptibly(CHANNEL_CLOSE_MAXIMUM_WAITING_TIME_MILLIS, TimeUnit.MILLISECONDS);
            serverChannel = null;
        }
        if (bootstrap != null) {
            bootstrap.releaseExternalResources();
            bootstrap = null;
        }

        // clear the request first and remove timer
        requestManagerTimer.stop();
    }

    private void closePinpointServer() {
        for (Channel channel : channelGroup) {
            DefaultPinpointServer pinpointServer = (DefaultPinpointServer) channel.getAttachment();

            if (pinpointServer != null) {
                pinpointServer.sendClosePacket();
            }
        }
    }

    public List<PinpointSocket> getWritableSocketList() {
        List<PinpointSocket> pinpointServerList = new ArrayList<PinpointSocket>();

        for (Channel channel : channelGroup) {
            DefaultPinpointServer pinpointServer = (DefaultPinpointServer) channel.getAttachment();
            if (pinpointServer != null && pinpointServer.isEnableDuplexCommunication()) {
                pinpointServerList.add(pinpointServer);
            }
        }

        return pinpointServerList;
    }

    /**
     * 这儿关于管道的服务 重写了主要的业务方法
     */
    class PinpointServerChannelHandler extends SimpleChannelHandler {
        /**
         * 首先获取管道
         * 在关闭数据包服务中添加一个监听 并重写operationComplete方法
         * 使用默认的pinpoint服务创建管道服务
         * set pinpoint服务
         * 管道组添加该管道 并启动服务
         * @param ctx
         * @param e
         * @throws Exception
         */
        @Override
        public void channelConnected(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
            final Channel channel = e.getChannel();
            logger.info("channelConnected started. channel:{}", channel);

            if (released) {
                logger.warn("already released. channel:{}", channel);
                channel.write(new ServerClosePacket()).addListener(new ChannelFutureListener() {
                    @Override
                    public void operationComplete(ChannelFuture future) throws Exception {
                        future.getChannel().close();
                    }
                });
                return;
            }

            boolean isIgnore = isIgnoreAddress(channel);
            if (isIgnore) {
                logger.debug("channelConnected ignore address. channel:" + channel);
                return;
            }

            DefaultPinpointServer pinpointServer = createPinpointServer(channel);

            channel.setAttachment(pinpointServer);
            channelGroup.add(channel);

            pinpointServer.start();

            super.channelConnected(ctx, e);
        }

        /**
         * 关闭管道
         * @param ctx
         * @param e
         * @throws Exception
         */
        @Override
        public void channelDisconnected(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
            final Channel channel = e.getChannel();

            DefaultPinpointServer pinpointServer = (DefaultPinpointServer) channel.getAttachment();
            if (pinpointServer != null) {
                pinpointServer.stop(released);
            }

            super.channelDisconnected(ctx, e);
        }

        /**
         * 管道关闭 由pinpointServerFactory生成的管道组会删除这个管道
         * 当对方先关闭套接字并断开连接时，通道关闭事件也可能发生应该考虑这一点。
         *
         * @param ctx
         * @param e
         * @throws Exception
         */
        @Override
        public void channelClosed(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
            final Channel channel = e.getChannel();

            channelGroup.remove(channel);

            super.channelClosed(ctx, e);
        }

        /**
         * 获取附件 该方法主要是加入pinpoint服务来触发消息
         *
         * @param ctx
         * @param e
         * @throws Exception
         */
        @Override
        public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
            final Channel channel = e.getChannel();

            DefaultPinpointServer pinpointServer = (DefaultPinpointServer) channel.getAttachment();
            if (pinpointServer != null) {
                Object message = e.getMessage();

                pinpointServer.messageReceived(message);
            }

            super.messageReceived(ctx, e);
        }
    }

}
