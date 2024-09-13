package io.github.chains_project.theo.monitor.detectorCategories;

import java.util.List;

public class ThreadStart extends AbstractCategory {
    /**
     * Creates a new ThreadStart object that stores information about a
     * thread start event.
     *
     * @param method    the method which started the thread
     * @param className the class of the method
     * @param calledBy  the methods which called the respective method as found in the stacktrace
     */
    ThreadStart(String method, String className, List<SubMethod> calledBy) {
        super(method, className, calledBy);
    }
}
