package io.github.chains_project.dependency_privileges.detector;

import io.github.chains_project.dependency_privileges.detectorCategories.AbstractCategory;
import io.github.chains_project.dependency_privileges.detectorCategories.DetectorCategoryFactory;
import io.github.chains_project.dependency_privileges.utils.AccessRecord;
import io.github.chains_project.dependency_privileges.utils.DependencyParserUtils;
import io.github.chains_project.dependency_privileges.utils.FrameInfo;
import jdk.jfr.consumer.*;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.function.Predicate;

/**
 * This class includes the methods to configure the JFR to monitor runtime accesses and
 * to process the recordings from the JFR.
 */
public class Processor {

    private final Predicate<RecordedClassLoader> bootstrapMethodPredicate = classLoader ->
            classLoader == null || classLoader.getName().equals("bootstrap");
    private final Predicate<RecordedClass> methodRemovablePredicate = type -> {
        String jarPath = getJarPath(type);
        return (jarPath == null || jarPath.contains("surefire-booter") || jarPath.contains("junit") ||
                jarPath.contains("dependency-privileges"));
    };
    DependencyParserUtils dependencyParserUtils = new DependencyParserUtils("/home/yogya/Documents/KTH/java-hello-world-with-maven/lockfile.json");

    public final List<AccessRecord> readRecordings(String detectorCategory, Path tempFile, List<AccessRecord> allAccessRecords)
            throws IOException {
        try (RecordingFile recordingFile = new RecordingFile(tempFile)) {
            while (recordingFile.hasMoreEvents()) {
                RecordedEvent event = recordingFile.readEvent();
                if (event.getStackTrace() == null) {
                    continue;
                }
                RecordedStackTrace stackTrace = event.getStackTrace();
                List<FrameInfo> frameInfo = constructStackTrace(stackTrace);
                if (frameInfo == null || frameInfo.isEmpty()) {
                    continue;
                }
                // ToDo: this is not working. Improve the construct stack trace method
                FrameInfo frame = frameInfo.get(frameInfo.size() - 1); // Assuming getLast() is correct
                String method = frame.getMethodName();
                String className = frame.getClassName();
                String depName = frame.getDependency();
                if (depName == null) {
                    continue;
                }
                // ToDo: remove the base dependency from the calledBy list
                List<AbstractCategory.SubMethod> calledBy = getCalledByTrace(frameInfo);
                try {
                    AbstractCategory category = DetectorCategoryFactory.createCategory(method, className, calledBy,
                            event, detectorCategory, tempFile);
                    if (category != null) {
                        AccessRecord.DetectorEvent detectorEvent = new AccessRecord.DetectorEvent(detectorCategory,
                                List.of(category));
                        boolean foundMatch = false;
                        for(AccessRecord accessRecord : allAccessRecords) {
                            int index = allAccessRecords.indexOf(accessRecord);
                            if (accessRecord.getDependency().equals(depName)) {
                                List<AccessRecord.DetectorEvent> existingDetectorEvents =
                                        new ArrayList<>(accessRecord.getRecords());
                                // ToDO: there will be multiple detector events. Check if it exists and handle duplicates.
                                existingDetectorEvents.add(detectorEvent);
                                accessRecord.setRecords(existingDetectorEvents);
                                allAccessRecords.set(index, accessRecord);
                                foundMatch = true;
                                break;
                            }
                        }
                        if (!foundMatch) {
                            allAccessRecords.add(new AccessRecord(depName, List.of(detectorEvent)));
                        }
                    }
                } catch (NullPointerException | IllegalArgumentException e) {
                    if(detectorCategory.contains("FileWrite")) {
                        System.err.println(e);
                    }
                    //
                    // ToDo: handle exceptions
                }
            }
        }
        return allAccessRecords;
    }

    private List<FrameInfo> constructStackTrace(RecordedStackTrace stackTrace) {
        return stackTrace.getFrames().stream()
                .filter(RecordedFrame::isJavaFrame)
                .filter(frame -> {
                    RecordedMethod method = frame.getMethod();
                    RecordedClass type = method.getType();
                    return isMethodValid(type, method);
                })
                .map(this::createFrameInfo)
                .toList();
    }

    private boolean isMethodValid(RecordedClass type, RecordedMethod method) {
        return !bootstrapMethodPredicate.test(type.getClassLoader())
                && !methodRemovablePredicate.test(method.getType())
                && dependencyParserUtils.findDepDetails(getJarPath(type)) != null;
    }

    private FrameInfo createFrameInfo(RecordedFrame frame) {
        RecordedMethod method = frame.getMethod();
        RecordedClass type = method.getType();
        String depName = dependencyParserUtils.findDepDetails(getJarPath(type));
        RecordedClassLoader classLoader = type.getClassLoader();
        String classLoaderName = classLoader == null ? null : classLoader.getName();
        return new FrameInfo(
                classLoaderName,
                getModule(type),
                type.getName(),
                method.getName(),
                getClass(type),
                depName
        );
    }

    private String getClass(RecordedClass type) {
        try {
            Class<?> clazz = Class.forName(type.getName());
            Class<?> current = clazz;
            while ((current = current.getDeclaringClass()) != null) {
                clazz = current;
            }
            return clazz.getSimpleName() + ".java";
        } catch (ClassNotFoundException ignored) {
            return null;
        }
    }

    private String getModule(RecordedClass type) {
        try {
            return Class.forName(type.getName()).getModule().getName();
        } catch (ClassNotFoundException ignored) {
            return null;
        }
    }

    private String getJarPath(RecordedClass type) {
        try {
            Class<?> clazz = Class.forName(type.getName());
            String jarLocation = clazz.getProtectionDomain().getCodeSource().getLocation().toString();
            if (jarLocation.contains("target")) {
                return null;
            }
            // return jarLocation.substring(jarLocation.lastIndexOf("/.m2") +1);
            return jarLocation;
            // ToDo: handle errors in a better way.
        } catch (ClassNotFoundException | NullPointerException ignored) {
            return null;
        }
    }

    private List<AbstractCategory.SubMethod> getCalledByTrace(List<FrameInfo> frameInfo) {
        List<AbstractCategory.SubMethod> calledBy = new ArrayList<>();
        for (FrameInfo info : frameInfo) {
            String depName = info.getDependency();
            boolean foundDependency = false;
            for (AbstractCategory.SubMethod subMethod : calledBy) {
                if (subMethod.getDependency().equals(depName)) {
                    int index = calledBy.indexOf(subMethod);
                    List<String> methods = new ArrayList<>(subMethod.getMethods());
                    methods.add(concatClassMethodName(info.getClassName(), info.getMethodName()));
                    subMethod.setMethods(methods);
                    calledBy.set(index, subMethod);
                    foundDependency = true;
                    break;
                }
            }
            if (!foundDependency && depName != null) {
                AbstractCategory.SubMethod subMethod = new AbstractCategory.SubMethod(
                        depName,
                        Collections.singletonList(concatClassMethodName(info.getClassName(), info.getMethodName()))
                );
                calledBy.add(subMethod);
            }
        }
        return calledBy;
    }

    private String concatClassMethodName(String className, String methodName) {
        return (className.replace(".java", "").concat(methodName));
    }
}
