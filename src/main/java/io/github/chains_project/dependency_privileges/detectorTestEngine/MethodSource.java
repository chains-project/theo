package io.github.chains_project.dependency_privileges.detectorTestEngine;

import org.junit.platform.engine.TestSource;

import java.lang.reflect.Method;

public class MethodSource implements TestSource {

    private static final long serialVersionUID = 1L;

    private final String className;
    private final String methodName;


    public MethodSource(String className, String methodName) {
        this.className = className;
        this.methodName = methodName;
    }

    public static MethodSource from(Method testMethod) {
        return new MethodSource(testMethod.getDeclaringClass().toGenericString(), testMethod.getName());
    }

}
