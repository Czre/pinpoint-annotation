package com.navercorp.pinpoint.profiler;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.security.ProtectionDomain;

/**
 * 这个类主要是通过instrumentation来实现其方法transform来达到字节码增强的目的
 * @author Woonduk Kang(emeroad)
 */
public interface ClassFileTransformerDispatcher extends ClassFileTransformer {
    @Override
    byte[] transform(ClassLoader classLoader, String classInternalName, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classFileBuffer) throws IllegalClassFormatException;

}
