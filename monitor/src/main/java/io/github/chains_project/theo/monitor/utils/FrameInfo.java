package io.github.chains_project.theo.monitor.utils;

public record FrameInfo(String classLoaderName, String module, String typeName, String methodName, String className,
                        String dependency) {

    @Override
    public String toString() {
        return ("Frame{classLoaderName = %s, module = %s, typeName = %s, methodName = %s, className = %s, " +
                "dependency = %s,}")
                .formatted(classLoaderName, module, typeName, methodName, className, dependency);
    }

}
