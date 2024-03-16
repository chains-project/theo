package io.github.chains_project.dependency_privileges.utils;

public class FrameInfo {
    private String classLoaderName;
    private String module;
    private String typeName;
    private String methodName;
    private String className;
    private String dependency;

    public FrameInfo(String classLoaderName, String module, String typeName, String methodName, String className,
                     String dependency) {
        this.classLoaderName = classLoaderName;
        this.module = module;
        this.typeName = typeName;
        this.methodName = methodName;
        this.className = className;
        this.dependency = dependency;
    }

    public String getClassLoaderName() {
        return classLoaderName;
    }

    public String getModule() {
        return module;
    }

    public String getTypeName() {
        return typeName;
    }

    public String getMethodName() {
        return methodName;
    }

    public String getClassName() {
        return className;
    }
    public String getDependency() {
        return dependency;
    }

    @Override
    public String toString() {
        return ("Frame{classLoaderName = %s, module = %s, typeName = %s, methodName = %s, className = %s, " +
                "dependency = %s,}")
                .formatted(classLoaderName, module, typeName, methodName, className, dependency);
    }

}
