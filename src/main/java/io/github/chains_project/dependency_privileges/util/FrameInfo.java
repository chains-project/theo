package io.github.chains_project.dependency_privileges.util;

public class FrameInfo {
    private String classLoaderName;
    private String module;
    private String typeName;
    private String methodName;
    private String className;
    private String jarPath;

    public FrameInfo(String classLoaderName, String module, String typeName, String methodName, String className,
                     String jarPath) {
        this.classLoaderName = classLoaderName;
        this.module = module;
        this.typeName = typeName;
        this.methodName = methodName;
        this.className = className;
        this.jarPath = jarPath;
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
    public String getJarPath() {
        return jarPath;
    }

}
