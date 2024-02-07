package io.github.chains_project.dependency_privileges.detectorTestEngine;

import org.junit.platform.commons.util.ReflectionUtils;
import org.junit.platform.engine.TestDescriptor;
import org.junit.platform.engine.support.descriptor.AbstractTestDescriptor;
import org.junit.platform.engine.support.descriptor.ClassSource;

import static org.junit.platform.commons.util.ReflectionUtils.HierarchyTraversalMode.TOP_DOWN;

public class TestClassDescriptor extends AbstractTestDescriptor {

    private final Class<?> testClass;

    public TestClassDescriptor(Class<?> testClass, TestDescriptor parent) {
        super(
                parent.getUniqueId().append("class", testClass.getName()),
                testClass.getSimpleName(),
                ClassSource.from(testClass)
        );
        this.testClass = testClass;
        setParent(parent);
        addAllChildren();
    }

    public Class<?> getTestClass() {
        return testClass;
    }

    private void addAllChildren() {

        ReflectionUtils
                .findMethods(testClass, method -> true, TOP_DOWN)
                .stream()
                .map(method -> new TestMethodDescriptor(method, this))
                .forEach(this::addChild);
    }

    @Override
    public Type getType() {
        return Type.CONTAINER;
    }
}