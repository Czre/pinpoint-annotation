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

package com.navercorp.pinpoint.plugin.redis.interceptor;

import java.net.URI;

import com.navercorp.pinpoint.common.plugin.util.HostAndPort;
import com.navercorp.pinpoint.plugin.redis.EndPointUtils;
import redis.clients.jedis.JedisShardInfo;

import com.navercorp.pinpoint.bootstrap.context.MethodDescriptor;
import com.navercorp.pinpoint.bootstrap.context.TraceContext;
import com.navercorp.pinpoint.bootstrap.interceptor.AroundInterceptor;
import com.navercorp.pinpoint.bootstrap.logging.PLogger;
import com.navercorp.pinpoint.bootstrap.logging.PLoggerFactory;
import com.navercorp.pinpoint.plugin.redis.EndPointAccessor;

/**
 * Jedis (redis client) constructor interceptor
 * - trace endPoint
 * 这个Interceptor类主要的内容就是将链接redis数据库指定为endpoint 终节点
 * 将jedis binaryJedis也就是redis客户端的构造函数,基本都是链接数据库的构造函数,植入SetEndPointInterceptor方法
 * @author jaehong.kim
 */
public class SetEndPointInterceptor implements AroundInterceptor {

    private final PLogger logger = PLoggerFactory.getLogger(this.getClass());
    private final boolean isDebug = logger.isDebugEnabled();

    public SetEndPointInterceptor(TraceContext traceContext, MethodDescriptor methodDescriptor) {
    }

    /**
     * 在方法执行之前加入字节码以添加Trace数据
     * @param target
     * @param args
     */
    @Override
    public void before(Object target, Object[] args) {
        if (isDebug) {
            logger.beforeInterceptor(target, args);
        }

        try {
            if (!validate(target, args)) {
                return;
            }

            final String endPoint = getEndPoint(args);
            // 在这个接口中传递String endPoint?
            ((EndPointAccessor) target)._$PINPOINT$_setEndPoint(endPoint);
        } catch (Throwable t) {
            if (logger.isWarnEnabled()) {
                logger.warn("Failed to BEFORE process. {}", t.getMessage(), t);
            }
        }
    }

    private String getEndPoint(Object[] args) {
        // 拿到终节点信息,大意就是ip和端口咯
        final Object argZero = args[0];
        if (argZero instanceof String) {
            System.out.println(1);
            return EndPointUtils.getEndPoint(args);
        } else if (argZero instanceof URI) {
            final URI uri = (URI) argZero;
            System.out.println(2);
            return HostAndPort.toHostAndPortString(uri.getHost(), uri.getPort());
        } else if (argZero instanceof JedisShardInfo) {
            final JedisShardInfo info = (JedisShardInfo) argZero;
            System.out.println(3);
            return HostAndPort.toHostAndPortString(info.getHost(), info.getPort());
        }
        return "Unknown";
    }

    private boolean validate(final Object target, final Object[] args) {
        if (args == null || args.length == 0 || args[0] == null) {
            if (isDebug) {
                logger.debug("Invalid arguments. Null or not found args({}).", args);
            }
            return false;
        }

        if (!(target instanceof EndPointAccessor)) {
            if (isDebug) {
                logger.debug("Invalid target object. Need field accessor({}).", EndPointAccessor.class.getName());
            }
            return false;
        }
        return true;
    }

    @Override
    public void after(Object target, Object[] args, Object result, Throwable throwable) {
    }
}