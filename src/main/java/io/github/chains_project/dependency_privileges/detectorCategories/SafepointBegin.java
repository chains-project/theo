package io.github.chains_project.dependency_privileges.detectorCategories;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class SafepointBegin extends AbstractCategory {

    public final String totalThreadCount;
    public final String jniCriticalThreadCount;

    /**
     * Create a new SafepointBegin object that stores information about the beginning of a safe point.
     *
     * @param method                 the method which called a safe point
     * @param className              the class of the method
     * @param calledBy               the methods which called the respective method as found in the stacktrace
     * @param totalThreadCount       the total number of threads at the start of safe point
     * @param jniCriticalThreadCount the number of threads in JNI critical sections
     */
    @JsonCreator
    SafepointBegin(@JsonProperty("method") String method,
                   @JsonProperty("className") String className,
                   @JsonProperty("calledBy") List<SubMethod> calledBy,
                   @JsonProperty("totalThreadCount") String totalThreadCount,
                   @JsonProperty("jniCriticalThreadCount") String jniCriticalThreadCount) {
        super(method, className, calledBy);
        this.totalThreadCount = totalThreadCount;
        this.jniCriticalThreadCount = jniCriticalThreadCount;
    }
}
