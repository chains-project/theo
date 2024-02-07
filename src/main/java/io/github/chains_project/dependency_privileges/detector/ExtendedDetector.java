package io.github.chains_project.dependency_privileges.detector;

import io.github.chains_project.dependency_privileges.util.DependencyParserUtils;
import io.github.chains_project.dependency_privileges.util.FrameInfo;
import jdk.jfr.consumer.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * This class includes the methods to configure the JFR to monitor runtime accesses and
 * to process the recordings from the JFR.
 */
public class ExtendedDetector {

    private final List<String> namespaces = new ArrayList<>();
    private final Map<String, HashSet<String>> accessPrivileges = new HashMap<>();

    public void ReadRecording(String detectorCategory, Path tempFile) throws IOException {
        // System.out.println(detectorCategory + "*************************************");
        try (RecordingFile recordingFile = new RecordingFile(tempFile)) {
            while (recordingFile.hasMoreEvents()) {
                RecordedEvent event = recordingFile.readEvent();
                if (event.getStackTrace() == null) {
                    continue;
                }
                RecordedStackTrace stackTrace = event.getStackTrace();
                List<FrameInfo> frameInfo = constructStackTrace(stackTrace);
                for (FrameInfo frame : frameInfo) {
                    if (frame.getClassLoaderName() == null) {
                        continue;
                    }
                    if (frame.getClassLoaderName().equals("bootstrap")) {
                        continue;
                    }
//                    System.out.println("ClassLoader: " + frame.getClassLoaderName());
//                    System.out.println("Module: " + frame.getModule());
//                    System.out.println("Type: " + frame.getTypeName());
//                    System.out.println("Method: " + frame.getMethodName());
//                    System.out.println("Class: " + frame.getClassName());
//                    System.out.println("Jar: " + frame.getJarPath());
//                    System.out.println();
                    HashSet<String> updatedPrivilege = accessPrivileges.computeIfAbsent(frame.getJarPath(),
                            k -> new HashSet<>());
                    updatedPrivilege.add(detectorCategory);
                    accessPrivileges.put(frame.getJarPath(), updatedPrivilege);
                    try {
                        if (detectorCategory.contains("File")) {
                            String raw = event.getString("path");
                            if (raw == null || Path.of(raw).toAbsolutePath().equals(tempFile)) {
                                continue;
                            }
//                            System.out.println("File path" + Path.of(raw).toAbsolutePath());
                        }
                    } catch (NullPointerException | IllegalArgumentException ignored) {
                    }
                }
//                System.out.println("-------------------------------");
            }
        } finally {
            addPrivileges();
            Files.delete(tempFile);
        }
    }

    private List<FrameInfo> constructStackTrace(RecordedStackTrace stackTrace) {
        List<FrameInfo> frameInfoList = stackTrace.getFrames().stream()
                .filter(RecordedFrame::isJavaFrame)
                .map(frame -> {
                    RecordedMethod method = frame.getMethod();
                    RecordedClass type = method.getType();
                    RecordedClassLoader classLoader = type.getClassLoader();

                    String classLoaderName = classLoader == null ? null : classLoader.getName();
                    return new FrameInfo(
                            classLoaderName,
                            getModule(type),
                            type.getName(),
                            method.getName(),
                            getClass(type),
                            getJarPath(type)
                    );
                }).toList();
        return frameInfoList;
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

    private void addPrivileges() throws IOException {
        // ToDO: move this code to Mojo. Path is hardcoded. Get the value from project instead.
        String project = "/home/yogya/Documents/KTH/java-hello-world-with-maven";
        String outputFilePath = project + "/access-privileges.json";
        DependencyParserUtils dependencyParser = new DependencyParserUtils(outputFilePath);
        for (Map.Entry<String, HashSet<String>> entry : accessPrivileges.entrySet()) {
            String jarPath = entry.getKey();
            HashSet<String> privileges = entry.getValue();
            dependencyParser.addAccessPrivileges(jarPath, privileges);
        }
        dependencyParser.saveUpdatedLockfile(outputFilePath);
    }

    public void addNamespace(String namespace) {
        namespaces.add(namespace);
    }

    public List<String> getNamespaces() {
        return namespaces;
    }

    /**
     * Types of access privileges that the JFR can detect.
     */
    public enum DetectionCategory {

        // Writing data to a file
        FILEWRITE("FileWrite"),
        // Reading data from a file
        FILEREAD("FileRead"),
        // Flushing a writer
        FLUSH("Flush"),
        // Writing data to a socket
        // ToDO: we can get port and address.
        SOCKETWRITE("SocketWrite"),
        // Reading data from a socket
        SOCKETREAD("SocketRead"),
        // ToDO:  we can get base and top address.
        // A native library
        NATIVELIBRARY("NativeLibrary"),
        // Snapshot of a threads state when in native
        NATIVEMETHODSAMPLE("NativeMethodSample"),
        // ToDO: without read rate or write rate, this is unuseful.
        // Network utilisation
        NETWORKUTILIZATION("NetworkUtilization"),
        // Parameters used in TLS Handshake
        // ToDO: we can get peer port peer host.
        TLSHANDSHAKE("TLSHandshake"),
        // OS CPU Load
        CPULOAD("CPULoad"),
        // Redefine classes
        REDEFINECLASSES("RedefineClasses"),
        // Re-transform classes
        RETRANSFORMCLASSES("RetransformClasses"),
        // Modification of Security property
        SECURITYPROPERTYMODIFICATION("SecurityPropertyModification"),
        // Java Thread Start
        THREADSTART("ThreadStart"),
        // User mode or system mode thread CPU load
        THREADCPULOAD("ThreadCPULoad"),
        // An OS process
        PROCESSSTART("ProcessStart"),
        // command line
        SYSTEMPROCESS("SystemProcess");

        private final String name;

        private DetectionCategory(String s) {
            name = s;
        }

        @Override
        public String toString() {
            return this.name;
        }
    }

}
