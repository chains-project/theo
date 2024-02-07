package io.github.chains_project.dependency_privileges.detectorTestEngine;

import org.junit.platform.engine.support.descriptor.AbstractTestDescriptor;

import java.lang.reflect.Method;


public class TestMethodDescriptor extends AbstractTestDescriptor {

    private final Method method;

    public TestMethodDescriptor(Method method, TestClassDescriptor parent) {
        super(
                parent.getUniqueId().append("method", method.getName()),
                method.getName(),
                MethodSource.from(method)
        );
        this.method = method;
        setParent(parent);
    }

    public Method getTestMethod() {
        return method;
    }


    @Override
    public Type getType() {
        return Type.TEST;
    }

}
