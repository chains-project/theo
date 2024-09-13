package io.github.chains_project.theo.monitor.utils;

public record FrameInfo(String module, String typeName, String methodName, String className,
                        String dependency) {

    @Override
    public String toString() {
        return ("Frame{module = %s, typeName = %s, methodName = %s, className = %s, " +
                "dependency = %s,}")
                .formatted(module, typeName, methodName, className, dependency);
    }

}
