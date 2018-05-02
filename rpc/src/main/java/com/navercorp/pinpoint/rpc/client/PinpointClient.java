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

package com.navercorp.pinpoint.rpc.client;

import com.navercorp.pinpoint.rpc.*;
import com.navercorp.pinpoint.rpc.stream.*;

/**
 * @author emeroad
 * @author koo.taejin
 * @author netspider
 */
public interface PinpointClient extends PinpointSocket {


    /*
        because reconnectEventListener's constructor contains Dummy and can't be access through setter,
        guarantee it is not null.
        因为重新连接Event Listener的构造函数包含Dummy，并且无法通过setter访问，请确保它不为null。
    */
    boolean addPinpointClientReconnectEventListener(PinpointClientReconnectEventListener eventListener);

    boolean removePinpointClientReconnectEventListener(PinpointClientReconnectEventListener eventListener);

    void reconnectSocketHandler(PinpointClientHandler pinpointClientHandler);

    void sendSync(byte[] bytes) ;

    Future sendAsync(byte[] bytes);

    StreamChannelContext findStreamChannel(int streamChannelId);

    /**
     * write ping packet on tcp channel
     * PinpointSocketException throws when writing fails.
     * 在tcp通道上写ping数据包Pinpoint Socket写入失败时抛出异常。
     *
     */
    void sendPing();


    boolean isClosed();

    boolean isConnected();
}
