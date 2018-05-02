/*
 * Copyright 2017 NAVER Corp.
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

package com.navercorp.pinpoint.profiler.context.provider.stat.jvmgc;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.navercorp.pinpoint.profiler.monitor.metric.memory.DefaultMemoryMetric;
import com.navercorp.pinpoint.profiler.monitor.metric.memory.MemoryMetric;
import com.navercorp.pinpoint.profiler.monitor.metric.memory.UnknownMemoryMetric;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;

/**
 * @author dawidmalina
 * @author HyunGil Jeong
 */
public class MemoryMetricProvider implements Provider<MemoryMetric> {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    @Inject
    public MemoryMetricProvider() {
    }

    @Override
    public MemoryMetric get() {
        MemoryMetric memoryMetric = new UnknownMemoryMetric();
        MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();
        // 当ManagementFactory获取到的内存信息不为空时，将其转换成DefaultMemoryMetric再返回
        if (memoryMXBean != null) {
            memoryMetric = new DefaultMemoryMetric(memoryMXBean);
        }
        logger.info("loaded : {}", memoryMetric);
        return memoryMetric;
    }
}
