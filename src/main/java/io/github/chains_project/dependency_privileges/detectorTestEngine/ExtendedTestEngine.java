package io.github.chains_project.dependency_privileges.detectorTestEngine;

import io.github.chains_project.dependency_privileges.detector.BaseDetector;
import org.junit.platform.commons.support.ReflectionSupport;
import org.junit.platform.engine.*;
import org.junit.platform.engine.discovery.ClassSelector;
import org.junit.platform.engine.discovery.ClasspathRootSelector;
import org.junit.platform.engine.discovery.PackageSelector;
import org.junit.platform.engine.support.descriptor.EngineDescriptor;
import org.junit.platform.engine.support.descriptor.MethodSource;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URI;

/**
 * A custom test engine to extend the test classes with the BaseDetector.
 */
public class ExtendedTestEngine implements TestEngine {

    @Override
    public String getId() {
        return "custom-test-engine";
    }

    @Override
    public TestDescriptor discover(EngineDiscoveryRequest request, UniqueId uniqueId) {
        TestDescriptor engineDescriptor = new EngineDescriptor(uniqueId, "custom test engine");
        request.getSelectorsByType(ClasspathRootSelector.class).forEach(selector -> {
            appendTestsInClasspathRoot(selector.getClasspathRoot(), engineDescriptor);
        });

        request.getSelectorsByType(PackageSelector.class).forEach(selector -> {
            appendTestsInPackage(selector.getPackageName(), engineDescriptor);
        });

        request.getSelectorsByType(ClassSelector.class).forEach(selector -> {
            appendTestsInClass(selector.getJavaClass(), engineDescriptor);
        });

        return engineDescriptor;
    }

    private void appendTestsInClasspathRoot(URI uri, TestDescriptor engineDescriptor) {
        ReflectionSupport.findAllClassesInClasspathRoot(uri, clazz -> true, name -> true) //
                .stream()
                .map(aClass -> new TestClassDescriptor(aClass, engineDescriptor)) //
                .forEach(engineDescriptor::addChild);
    }

    private void appendTestsInPackage(String packageName, TestDescriptor engineDescriptor) {
        ReflectionSupport.findAllClassesInPackage(packageName, clazz -> true, name -> true) //
                .stream()
                .map(aClass -> new TestClassDescriptor(aClass, engineDescriptor)) //
                .forEach(engineDescriptor::addChild);
    }

    private void appendTestsInClass(Class<?> javaClass, TestDescriptor engineDescriptor) {
        engineDescriptor.addChild(new TestClassDescriptor(javaClass, engineDescriptor));
    }

    private void executeTest(TestDescriptor testDescriptor, EngineExecutionListener listener) {
        if (testDescriptor instanceof MethodSource) {
            Method testMethod = ((MethodSource) testDescriptor).getJavaMethod();
            Class<?> testClass = testMethod.getDeclaringClass();
            Object testInstance;
            try {
                testInstance = testClass.getDeclaredConstructor().newInstance();
            } catch (NoSuchMethodException | InvocationTargetException | InstantiationException |
                     IllegalAccessException e) {
                throw new RuntimeException("Error creating test instance for " + testMethod.getName(), e);
            }

            try {
                testMethod.invoke(testInstance);
                listener.executionFinished(testDescriptor, TestExecutionResult.successful());
            } catch (InvocationTargetException e) {
                Throwable cause = e.getCause();
                if (cause instanceof AssertionError) {
                    listener.executionFinished(testDescriptor, TestExecutionResult.failed(cause));
                } else {
                    throw new RuntimeException("Error executing test " + testMethod.getName(), e);
                }
            } catch (IllegalAccessException | IllegalArgumentException e) {
                throw new RuntimeException("Error executing test " + testMethod.getName(), e);
            }
        } else {
            // ToDo: Handle non-method-level descriptors (e.g., class-level) if needed
        }
    }

    @Override
    public void execute(ExecutionRequest request) {
        TestDescriptor root = request.getRootTestDescriptor();
        root.getChildren()
                .forEach(descriptor -> descriptor.getChildren()
                        .forEach(methodDescriptor -> iterDescriptor(request, methodDescriptor)));
    }

    public void iterDescriptor(ExecutionRequest request, TestDescriptor descriptor) {
        if (descriptor instanceof TestMethodDescriptor) {
            executeMethod(request, descriptor);
        }
    }

    public void executeMethod(ExecutionRequest request, TestDescriptor testDescriptor) {
        BaseDetector baseDetector = new BaseDetector();
        try {
            baseDetector.beforeEach();
        } catch (IOException e) {
            throw new RuntimeException("Error in lockfile path configuration", e);
        }
        executeTest(testDescriptor, request.getEngineExecutionListener());
        try {
            baseDetector.afterEach();
        } catch (IOException e) {
            throw new RuntimeException("Error in lockfile path configuration", e);
        }
    }
}
