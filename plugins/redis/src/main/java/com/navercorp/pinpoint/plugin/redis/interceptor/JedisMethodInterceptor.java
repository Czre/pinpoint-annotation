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

import com.navercorp.pinpoint.bootstrap.context.MethodDescriptor;
import com.navercorp.pinpoint.bootstrap.context.SpanEventRecorder;
import com.navercorp.pinpoint.bootstrap.context.TraceContext;
import com.navercorp.pinpoint.bootstrap.interceptor.SpanEventSimpleAroundInterceptorForPlugin;
import com.navercorp.pinpoint.bootstrap.interceptor.scope.InterceptorScope;
import com.navercorp.pinpoint.bootstrap.interceptor.scope.InterceptorScopeInvocation;
import com.navercorp.pinpoint.common.trace.AnnotationKey;
import com.navercorp.pinpoint.common.util.IntBooleanIntBooleanValue;
import com.navercorp.pinpoint.plugin.redis.CommandContext;
import com.navercorp.pinpoint.plugin.redis.CommandContextFactory;
import com.navercorp.pinpoint.plugin.redis.EndPointAccessor;
import com.navercorp.pinpoint.plugin.redis.RedisConstants;

/**
 * Jedis (redis client) method interceptor
 * doInBeforeTrace()在需要植入的方法执行之前添加一个命令上下文附件,以便之后的方法植入可以获取或添加一些想要的数据
 * doInAfterTrace() 根据判断添加一些SpanEvent数据 记录
 * @author jaehong.kim
 */
public class JedisMethodInterceptor extends SpanEventSimpleAroundInterceptorForPlugin {

    private InterceptorScope interceptorScope;
    private boolean io;

    public JedisMethodInterceptor(TraceContext traceContext, MethodDescriptor methodDescriptor, InterceptorScope interceptorScope, boolean io) {
        super(traceContext, methodDescriptor);

        this.interceptorScope = interceptorScope;
        this.io = io;
    }

    /**
     * 在跟踪之前执行获取当前调用,当该对象不为空时,创建一个附件为命令上下文(CommandContextFactory)
     * @param recorder
     * @param target
     * @param args
     */
    @Override
    public void doInBeforeTrace(SpanEventRecorder recorder, Object target, Object[] args) {
        final InterceptorScopeInvocation invocation = interceptorScope.getCurrentInvocation();
        if (invocation != null) {
            invocation.getOrCreateAttachment(CommandContextFactory.COMMAND_CONTEXT_FACTORY);
        }
    }

    @Override
    public void doInAfterTrace(SpanEventRecorder recorder, Object target, Object[] args, Object result, Throwable throwable) {
        String endPoint = null;

        if (target instanceof EndPointAccessor) {
            // 获取endPoint数据
            endPoint = ((EndPointAccessor) target)._$PINPOINT$_getEndPoint();
        }

        final InterceptorScopeInvocation invocation = interceptorScope.getCurrentInvocation();
        final Object attachment = getAttachment(invocation);
        // 匹配附件是否为命令上下文类型
        if (attachment instanceof CommandContext) {
            final CommandContext commandContext = (CommandContext) attachment;
            if (logger.isDebugEnabled()) {
                logger.debug("Check command context {}", commandContext);
            }
            recordIo(recorder, commandContext);
            // 记录完成之后清除附件
            invocation.removeAttachment();
        }

        // 记录api 终节点 目的地,也就是redis服务名称 服务类型 异常
        recorder.recordApi(getMethodDescriptor());
        recorder.recordEndPoint(endPoint != null ? endPoint : "Unknown");
        recorder.recordDestinationId(RedisConstants.REDIS.getName());
        recorder.recordServiceType(RedisConstants.REDIS);
        recorder.recordException(throwable);
    }

    /**
     * 计算redis io过程时间 并记录属性 io操作的时间
     * @param recorder
     * @param callContext
     */
    private void recordIo(SpanEventRecorder recorder, CommandContext callContext) {
        if (io) {
            IntBooleanIntBooleanValue value = new IntBooleanIntBooleanValue((int) callContext.getWriteElapsedTime(), callContext.isWriteFail(), (int) callContext.getReadElapsedTime(), callContext.isReadFail());
            recorder.recordAttribute(AnnotationKey.REDIS_IO, value);
        }
    }

    private Object getAttachment(InterceptorScopeInvocation invocation) {
        if (invocation == null) {
            return null;
        }
        return invocation.getAttachment();
    }
}