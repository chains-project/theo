package io.github.chains_project.theo.testGenerator;

import com.github.javafaker.Faker;
import org.junit.jupiter.api.Test;
import spoon.reflect.code.CtBlock;
import spoon.reflect.code.CtCodeSnippetStatement;
import spoon.reflect.code.CtStatement;
import spoon.reflect.declaration.CtAnnotation;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtParameter;
import spoon.reflect.declaration.CtType;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtTypeReference;

import java.lang.reflect.Constructor;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.List;

public class InvocationGenerator {

    public CtType<?> generateTestCase(CtMethod<?> method) {
        Factory factory = method.getFactory();
        CtAnnotation<?> testAnnotation = factory.createAnnotation(factory.createCtTypeReference(Test.class));

        // Generate a new test class
        CtType<?> generatedTestClass = factory.Class().create("GeneratedTestClass");

        // Create the test method
        CtMethod<?> generatedTestMethod = factory.createMethod();
        generatedTestMethod.addAnnotation(testAnnotation);
        generatedTestMethod.setSimpleName(method.getSimpleName() + "Test");
        generatedTestMethod.setThrownTypes(method.getThrownTypes());

        // Create the method body
        CtBlock<?> methodBody = factory.createBlock();
        // Generate dummy values for method parameters
        List<CtParameter<?>> parameters = method.getParameters();
        StringBuilder paramsStringBuilder = new StringBuilder();
        for (CtParameter<?> parameter : parameters) {
            CtTypeReference<?> paramType = parameter.getType();
            String paramName = parameter.getSimpleName();
            String dummyValue = generateDummyValue(paramType);
            // Add dummy value initialization to method body
            CtCodeSnippetStatement dummyValueStatement = factory.createCodeSnippetStatement(
                    paramType.getQualifiedName() + " " + paramName + " = " + dummyValue + ";"
            );
            methodBody.addStatement(dummyValueStatement);
            // Append to parameter list for method call
            if (paramsStringBuilder.length() > 0) {
                paramsStringBuilder.append(", ");
            }
            paramsStringBuilder.append(paramName);
        }
        // Handle constructor parameters for the instance creation
        String instanceCreation = generateInstanceCreation(method.getDeclaringType());
        CtStatement instanceCreationStatement = factory.createCodeSnippetStatement(instanceCreation);
        methodBody.addStatement(instanceCreationStatement);
        // Add method invocation
        String methodInvocation = "instance." + method.getSimpleName() + "(" + paramsStringBuilder + ");";
        CtStatement methodInvocationStatement = factory.createCodeSnippetStatement(methodInvocation);
        methodBody.addStatement(methodInvocationStatement);
        // Set the method body and add the method to the class
        generatedTestMethod.setBody(methodBody);
        generatedTestClass.addMethod(generatedTestMethod);

        return generatedTestClass;
    }

    private String generateInstanceCreation(CtType<?> declaringType) {
        Constructor<?>[] constructors = declaringType.getClass().getConstructors();
        if (constructors.length == 0) {
            return declaringType.getQualifiedName() + " instance = new " + declaringType.getQualifiedName() + "();";
        }
        // Pick the first constructor
        Constructor<?> constructor = Arrays.stream(constructors).toList().get(0);
        StringBuilder constructorParams = new StringBuilder();
        for (Parameter parameter : constructor.getParameters()) {
            String dummyValue = generateDummyValue((CtTypeReference<?>) parameter.getParameterizedType());
            if (constructorParams.length() > 0) {
                constructorParams.append(", ");
            }
            constructorParams.append(dummyValue);
        }
        // Generate the instance with the params
        return declaringType.getQualifiedName() + " instance = new " + declaringType.getQualifiedName() +
                "(" + constructorParams + ");";
    }

    private String generateDummyValue(CtTypeReference<?> paramType) {
        String qualifiedName = paramType.getQualifiedName();
        Faker faker = new Faker();
        switch (qualifiedName) {
            case "byte", "short", "int", "long" -> {
                return String.valueOf(faker.number().numberBetween(1, 127));
            }
            case "float", "double" -> {
                return String.valueOf(faker.number().randomDouble(3, 1, 200));
            }
            case "char" -> {
                return "a";
            }
            case "boolean" -> {
                return faker.bool().toString();
            }
            case "java.lang.String" -> {
                return faker.rockBand().name();
            }
            default -> {
                if (paramType.isClass()) {
                    return "Mockito.mock(" + qualifiedName + ".class)";
                }
                return "null";
            }
        }
    }
}
