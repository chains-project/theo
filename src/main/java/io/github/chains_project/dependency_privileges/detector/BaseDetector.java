package io.github.chains_project.dependency_privileges.detector;

import jdk.jfr.Recording;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * This class includes the basic extensions for test cases to monitor runtime privileges.
 */
public class BaseDetector {
    private static final List<ExtendedDetector.DetectionCategory> detectors =
            new ArrayList<>(EnumSet.allOf(ExtendedDetector.DetectionCategory.class));

    private Map<String, Path> tempFiles = new HashMap<>();
    private Map<String, Recording> recordings = new HashMap<>();

    public void beforeEach() throws IOException {
        for (ExtendedDetector.DetectionCategory category : detectors) {
            Path tempFile = Files.createTempFile("test-recording-" + category.toString(), ".jfr");
            String tempFileKey = category + "_recordFile";
            String recordingKey = category + "_recording";
            tempFiles.put(tempFileKey, tempFile);
            Recording recording = new Recording();
            recordings.put(recordingKey, recording);
            recording.setToDisk(true);
            recording.setDestination(tempFile);
            recording.enable("jdk." + category).withStackTrace();
            recording.start();
        }
    }

    public void afterEach() throws IOException {
        ExtendedDetector extendedDetector = new ExtendedDetector();
        for (ExtendedDetector.DetectionCategory category : detectors) {
            String tempFileKey = category + "_recordFile";
            Path tempFile = tempFiles.get(tempFileKey);
            String recordingKey = category + "_recording";
            try (Recording recording = recordings.get(recordingKey)) {
                recording.stop();
            }
            // ToDO: move this to the end and into mojo
            extendedDetector.ReadRecording(category.toString(), tempFile);
        }
    }
}