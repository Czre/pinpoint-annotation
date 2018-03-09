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

package com.navercorp.pinpoint.bootstrap;

import java.lang.instrument.Instrumentation;
import java.util.List;
import java.util.Map;
import java.util.jar.JarFile;

import com.navercorp.pinpoint.ProductInfo;

/**
 * @author emeroad
 * @author netspider
 */
public class PinpointBootStrap {

    private static final BootLogger logger = BootLogger.getLogger(PinpointBootStrap.class.getName());

    private static final LoadState STATE = new LoadState();


    public static void premain(String agentArgs, Instrumentation instrumentation) {
        // 具体可以查看 https://www.ibm.com/developerworks/cn/java/j-lo-jse61/index.html
        // agentArgs 这个是由插件 也就是我们经常使用的tomcat中的那行代码传入
        // -----------------------------------------------------------------------------------
        // CATALINA_OPTS="$CATALINA_OPTS -javaagent:/data/pp-agent/pinpoint-bootstrap-1.5.2.jar"
        // -----------------------------------------------------------------------------------
        // Instrumentation参数是由JVM自动传入
        if (agentArgs == null) {
            agentArgs = "";
        }
        logger.info(ProductInfo.NAME + " agentArgs:" + agentArgs);

        // 使用AtomicBoolean 进行并发判断是否启动
        final boolean success = STATE.start();
        if (!success) {
            logger.warn("pinpoint-bootstrap already started. skipping agent loading.");
            return;
        }
        // 这一步是将agentArgs参数(是否是那行插入的代码)通过","分割出来
        Map<String, String> agentArgsMap = argsToMap(agentArgs);

        // 获取到System.getProperty("java.class.path")
        final ClassPathResolver classPathResolver = new AgentDirBaseClassPathResolver();

        // 判断pinpoint核心插件是否加载
        if (!classPathResolver.verify()) {
            // 核心组件加载失败
            logger.warn("Agent Directory Verify fail. skipping agent loading.");
            logPinpointAgentLoadFail();
            return;
        }

        // 返回由classPathResolver.verify()初始化的参数bootstrapJarFile
        BootstrapJarFile bootstrapJarFile = classPathResolver.getBootstrapJarFile();
        // 使用Instrumentation.appendToBootstrapClassLoader()来加载jar包
        appendToBootstrapClassLoader(instrumentation, bootstrapJarFile);

        // 初始化PinpointStarter类的变量
        PinpointStarter bootStrap = new PinpointStarter(agentArgsMap, bootstrapJarFile, classPathResolver, instrumentation);

        // 启动agent 并判断是否启动失败
        if (!bootStrap.start()) {
            // 启动失败
            logPinpointAgentLoadFail();
        }

    }

    private static Map<String, String> argsToMap(String agentArgs) {
        ArgsParser argsParser = new ArgsParser();
        Map<String, String> agentArgsMap = argsParser.parse(agentArgs);
        if (!agentArgsMap.isEmpty()) {
            logger.info("agentParameter :" + agentArgs);
        }
        return agentArgsMap;
    }

    private static void appendToBootstrapClassLoader(Instrumentation instrumentation, BootstrapJarFile agentJarFile) {
        List<JarFile> jarFileList = agentJarFile.getJarFileList();
        for (JarFile jarFile : jarFileList) {
            logger.info("appendToBootstrapClassLoader:" + jarFile.getName());
            instrumentation.appendToBootstrapClassLoaderSearch(jarFile);
        }
    }


    private static void logPinpointAgentLoadFail() {
        final String errorLog =
                "*****************************************************************************\n" +
                        "* Pinpoint Agent load failure\n" +
                        "*****************************************************************************";
        System.err.println(errorLog);
    }


}
