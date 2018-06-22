/*
 * Copyright 2014 NAVER Corp.
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

import com.navercorp.pinpoint.ProductInfo;
import com.navercorp.pinpoint.bootstrap.config.DefaultProfilerConfig;
import com.navercorp.pinpoint.bootstrap.config.ProfilerConfig;
import com.navercorp.pinpoint.common.Version;
import com.navercorp.pinpoint.common.service.AnnotationKeyRegistryService;
import com.navercorp.pinpoint.common.service.DefaultAnnotationKeyRegistryService;
import com.navercorp.pinpoint.common.service.DefaultServiceTypeRegistryService;
import com.navercorp.pinpoint.common.service.DefaultTraceMetadataLoaderService;
import com.navercorp.pinpoint.common.service.ServiceTypeRegistryService;
import com.navercorp.pinpoint.common.service.TraceMetadataLoaderService;
import com.navercorp.pinpoint.common.util.PinpointThreadFactory;
import com.navercorp.pinpoint.common.util.SimpleProperty;
import com.navercorp.pinpoint.common.util.SystemProperty;
import com.navercorp.pinpoint.common.util.logger.CommonLoggerFactory;
import com.navercorp.pinpoint.common.util.logger.StdoutCommonLoggerFactory;

import java.lang.instrument.Instrumentation;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author Jongho Moon
 */
class PinpointStarter {

    public static final String AGENT_TYPE = "AGENT_TYPE";
    public static final String DEFAULT_AGENT = "DEFAULT_AGENT";
    public static final String BOOT_CLASS = "com.navercorp.pinpoint.profiler.DefaultAgent";
    public static final String PLUGIN_TEST_AGENT = "PLUGIN_TEST";
    public static final String PLUGIN_TEST_BOOT_CLASS = "com.navercorp.pinpoint.test.PluginTestAgent";
    private final BootLogger logger = BootLogger.getLogger(PinpointStarter.class.getName());
    private final Map<String, String> agentArgs;
    private final BootstrapJarFile bootstrapJarFile;
    private final ClassPathResolver classPathResolver;
    private final Instrumentation instrumentation;
    private SimpleProperty systemProperty = SystemProperty.INSTANCE;


    public PinpointStarter(Map<String, String> agentArgs, BootstrapJarFile bootstrapJarFile, ClassPathResolver classPathResolver, Instrumentation instrumentation) {
        if (agentArgs == null) {
            throw new NullPointerException("agentArgs must not be null");
        }
        if (bootstrapJarFile == null) {
            throw new NullPointerException("bootstrapJarFile must not be null");
        }
        if (classPathResolver == null) {
            throw new NullPointerException("classPathResolver must not be null");
        }
        if (instrumentation == null) {
            throw new NullPointerException("instrumentation must not be null");
        }
        this.agentArgs = agentArgs;
        this.bootstrapJarFile = bootstrapJarFile;
        this.classPathResolver = classPathResolver;
        this.instrumentation = instrumentation;

    }

    boolean start() {
        // 调用System.getProperties()方法来获取系统信息
        final IdValidator idValidator = new IdValidator();
        final String agentId = idValidator.getAgentId();
        if (agentId == null) {
            return false;
        }
        final String applicationName = idValidator.getApplicationName();
        if (applicationName == null) {
            return false;
        }

        // 这一步是做什么呢?
        // 将pinpoint-agent/plugin中的所有jar包转换为URL地址
        URL[] pluginJars = classPathResolver.resolvePlugins();

        // TODO using PLogger instead of CommonLogger
        // 使用单利模式加载一个日志工厂
        CommonLoggerFactory loggerFactory = StdoutCommonLoggerFactory.INSTANCE;
        // 跟踪元数据加载服务
        TraceMetadataLoaderService typeLoaderService = new DefaultTraceMetadataLoaderService(pluginJars, loggerFactory);
        // 服务类型注册服务
        ServiceTypeRegistryService serviceTypeRegistryService = new DefaultServiceTypeRegistryService(typeLoaderService, loggerFactory);
        // 注解密钥注册服务
        AnnotationKeyRegistryService annotationKeyRegistryService = new DefaultAnnotationKeyRegistryService(typeLoaderService, loggerFactory);

        // 这一步读取配置文件pinpoint.config
        String configPath = getConfigPath(classPathResolver);
        if (configPath == null) {
            return false;
        }

        // set the path of log file as a system property
        // 将日志文件路径和版本信息写入系统属性
        saveLogFilePath(classPathResolver);

        savePinpointVersion();

        try {
            // Is it right to load the configurat1ion in the bootstrap?
            // 这个方法加载pinpoint.config配置文件
            ProfilerConfig profilerConfig = DefaultProfilerConfig.load(configPath);

            // this is the library list that must be loaded
            // 这是必须加载的库列表
            List<URL> libUrlList = resolveLib(classPathResolver);
            // 将agent中所有的jar包添加进类agent类加载器
            AgentClassLoader agentClassLoader = new AgentClassLoader(libUrlList.toArray(new URL[libUrlList.size()]));
            // 获取核心jar包pinpoint-bootstrap.jar的绝对路径
            final String bootClass = getBootClass();
            agentClassLoader.setBootClass(bootClass);
            logger.info("pinpoint agent [" + bootClass + "] starting...");

            // 创建agent参数
            AgentOption option = createAgentOption(agentId, applicationName, profilerConfig, instrumentation, pluginJars, bootstrapJarFile, serviceTypeRegistryService, annotationKeyRegistryService);
            Agent pinpointAgent = agentClassLoader.boot(option);
            pinpointAgent.start();
            registerShutdownHook(pinpointAgent);
            logger.info("pinpoint agent started normally.");
        } catch (Exception e) {
            // unexpected exception that did not be checked above
            logger.warn(ProductInfo.NAME + " start failed.", e);
            return false;
        }
        return true;
    }

    private String getBootClass() {
        // 将获取到的AgentType转换为大写 进行匹配
        final String agentType = getAgentType().toUpperCase();
        if (PLUGIN_TEST_AGENT.equals(agentType)) {
            return PLUGIN_TEST_BOOT_CLASS;
        }
        return BOOT_CLASS;
    }

    private String getAgentType() {
        String agentType = agentArgs.get(AGENT_TYPE);
        if (agentType == null) {
            return DEFAULT_AGENT;
        }
        return agentType;

    }

    private AgentOption createAgentOption(String agentId, String applicationName, ProfilerConfig profilerConfig,
                                          Instrumentation instrumentation,
                                          URL[] pluginJars,
                                          BootstrapJarFile bootstrapJarFile,
                                          ServiceTypeRegistryService serviceTypeRegistryService,
                                          AnnotationKeyRegistryService annotationKeyRegistryService) {
        List<String> bootstrapJarPaths = bootstrapJarFile.getJarNameList();
        // boot/ pinpoint-commons.jar,pinpoint-bootstrap-core.jar,pinpoint-bootstrap-core-optional.jar,pinpoint-annotations.jar 的绝对路径
        return new DefaultAgentOption(instrumentation, agentId, applicationName, profilerConfig, pluginJars, bootstrapJarPaths, serviceTypeRegistryService, annotationKeyRegistryService);
    }

    // for test
    void setSystemProperty(SimpleProperty systemProperty) {
        this.systemProperty = systemProperty;
    }

    private void registerShutdownHook(final Agent pinpointAgent) {
        final Runnable stop = new Runnable() {
            @Override
            public void run() {
                pinpointAgent.stop();
            }
        };
        PinpointThreadFactory pinpointThreadFactory = new PinpointThreadFactory("Pinpoint-shutdown-hook", false);
        Thread thread = pinpointThreadFactory.newThread(stop);
        Runtime.getRuntime().addShutdownHook(thread);
    }


    private void saveLogFilePath(ClassPathResolver classPathResolver) {
        String agentLogFilePath = classPathResolver.getAgentLogFilePath();
        logger.info("logPath:" + agentLogFilePath);

        systemProperty.setProperty(ProductInfo.NAME + ".log", agentLogFilePath);
    }

    private void savePinpointVersion() {
        logger.info("pinpoint version:" + Version.VERSION);
        systemProperty.setProperty(ProductInfo.NAME + ".version", Version.VERSION);
    }

    private String getConfigPath(ClassPathResolver classPathResolver) {
        final String configName = ProductInfo.NAME + ".config";
        String pinpointConfigFormSystemProperty = systemProperty.getProperty(configName);
        if (pinpointConfigFormSystemProperty != null) {
            logger.info(configName + " systemProperty found. " + pinpointConfigFormSystemProperty);
            return pinpointConfigFormSystemProperty;
        }

        String classPathAgentConfigPath = classPathResolver.getAgentConfigPath();
        if (classPathAgentConfigPath != null) {
            logger.info("classpath " + configName + " found. " + classPathAgentConfigPath);
            return classPathAgentConfigPath;
        }

        logger.info(configName + " file not found.");
        return null;
    }


    private List<URL> resolveLib(ClassPathResolver classPathResolver) {
        // this method may handle only absolute path,  need to handle relative path (./..agentlib/lib)
        // 此方法可能只处理绝对路径，需要处理相对路径
        String agentJarFullPath = classPathResolver.getAgentJarFullPath();
        String agentLibPath = classPathResolver.getAgentLibPath();
        List<URL> urlList = resolveLib(classPathResolver.resolveLib());
        String agentConfigPath = classPathResolver.getAgentConfigPath();

        if (logger.isInfoEnabled()) {
            logger.info("agent JarPath:" + agentJarFullPath);
            logger.info("agent LibDir:" + agentLibPath);
            for (URL url : urlList) {
                logger.info("agent Lib:" + url);
            }
            logger.info("agent config:" + agentConfigPath);
        }

        return urlList;
    }

    private List<URL> resolveLib(List<URL> urlList) {
        if (DEFAULT_AGENT.equals(getAgentType().toUpperCase())) {
            final List<URL> releaseLib = new ArrayList<URL>(urlList.size());
            for (URL url : urlList) {
                //
                if (!url.toExternalForm().contains("pinpoint-profiler-test")) {
                    releaseLib.add(url);
                }
            }
            return releaseLib;
        } else {
            logger.info("load " + PLUGIN_TEST_AGENT + " lib");
            // plugin test
            return urlList;
        }
    }

}
