package io.github.chains_project.theo.testGenerator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtAbstractInvocation;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtStatement;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtType;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.*;

public class MethodProcessor extends AbstractProcessor<CtMethod<?>> {

    private static final Logger log = LoggerFactory.getLogger(MethodProcessor.class);
    private final List<TheoMethod> setOfMethods = new LinkedList<>();
    private final Set<CtType<?>> generatedTestClasses = new HashSet<>();

    private boolean isInvocationOnExternalLibraryMethod(CtAbstractInvocation<?> invocation, CtMethod<?> method) {
        String packageName = method.getDeclaringType().getPackage().getQualifiedName();
        List<String> typesToIgnore = List.of("java", packageName);
        return typesToIgnore.stream().noneMatch(t ->
                invocation.getExecutable().getDeclaringType().getQualifiedName().startsWith(t));
    }

    private boolean methodHasTestAnnotation(CtMethod<?> method) {
        return (method.getAnnotations().stream()
                .anyMatch(a -> a.toString().contains(".Test")));
    }

    private void findAndAddPublicCallers(CtMethod<?> privateMethod, CtInvocation<?> invocation,
                                         List<CtMethod<?>> methodsConsidered) {
        // Get all methods in the class and see if any public method in the class calls the private method we want
        CtType<?> declaringType = privateMethod.getDeclaringType();
        Collection<CtMethod<?>> allMethods = declaringType.getAllMethods();

        for (CtMethod<?> method : allMethods) {
            // Skip the private method itself
            if (method.equals(privateMethod)) {
                continue;
            }
            // Check if the current method has already been considered
            if (methodsConsidered.contains(method)) {
                continue;
            }
            // Check if the current method calls the private method
            if (callsMethod(method, privateMethod)) {
                methodsConsidered.add(method);
                if (method.isPublic()) {
                    // If a public method calls the private method, add the public method
                    TheoMethod testMethod = new TheoMethod(
                            method.getDeclaringType().getQualifiedName(),
                            method.getSignature(),
                            invocation.toString());
                    InvocationGenerator ig = new InvocationGenerator();
                    CtType<?> generatedTestClass = ig.generateTestCase(method);
                    setOfMethods.add(testMethod);
                    generatedTestClasses.add(generatedTestClass);
                } else {
                    // If a non-public method calls the private method, recursively check its public callers
                    findAndAddPublicCallers(method, invocation, methodsConsidered);
                }
            }
        }
    }

    private boolean callsMethod(CtMethod<?> callerMethod, CtMethod<?> targetMethod) {
        // Check the invocations inside the caller method to see if any invocation relates to the target method
        List<CtInvocation<?>> invocations = callerMethod.getElements(new TypeFilter<>(CtInvocation.class));
        for (CtInvocation<?> invocation : invocations) {
            if (invocation.getExecutable().getSignature().equals(targetMethod.getSignature())) {
                return true;
            }
        }
        return false;
    }

    public List<TheoMethod> getTestMethods() {
        return setOfMethods;
    }

    public Set<CtType<?>> getGeneratedTestClasses() {
        return generatedTestClasses;
    }

    @Override
    public void process(CtMethod<?> method) {
        try {
            if (!methodHasTestAnnotation(method) && method.getBody() != null) {
                List<CtStatement> statements = method.getBody().getStatements();
                for (CtStatement statement : statements) {
                    List<CtInvocation<?>> invocationsInStatement = statement.getElements(new TypeFilter<>(CtInvocation.class));
                    for (CtInvocation<?> invocation : invocationsInStatement) {
                        if (isInvocationOnExternalLibraryMethod(invocation, method)) {
                            if (method.isPublic()) {
                                /* This generation of a TheoMethod is not needed. Just using it for clarity and log
                                purposes. */
                                TheoMethod testMethod = new TheoMethod(
                                        method.getDeclaringType().getQualifiedName(),
                                        method.getSignature(),
                                        invocation.toString());
                                InvocationGenerator ig = new InvocationGenerator();
                                CtType<?> generatedTestClass = ig.generateTestCase(method);                                // Maintaining this list is also not necessary.
                                setOfMethods.add(testMethod);
                                generatedTestClasses.add(generatedTestClass);
                            } else {
                                findAndAddPublicCallers(method, invocation, new ArrayList<>());
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("An error occurred when processing the method {}. Continuing with other methods. ",
                    method.getSimpleName(), e);
        }
    }
}
