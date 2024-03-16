package io.github.chains_project.dependency_privileges.detector;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.chains_project.dependency_privileges.detectorCategories.DetectorCategoryFactory;
import io.github.chains_project.dependency_privileges.utils.AccessRecord;
import io.github.chains_project.dependency_privileges.utils.JsonUtils;
import jdk.jfr.Recording;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * This class includes the basic extensions for test cases to monitor runtime privileges.
 */
public class BaseDetector {
    private static final List<DetectorCategoryFactory.DetectionCategory> detectors =
            new ArrayList<>(EnumSet.allOf(DetectorCategoryFactory.DetectionCategory.class));

    private Map<String, Path> tempFiles = new HashMap<>();
    private Map<String, Recording> recordings = new HashMap<>();

    public void beforeEach() throws IOException {
        for (DetectorCategoryFactory.DetectionCategory category : detectors) {
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
        Processor processor = new Processor();
        String path = "/home/yogya/Documents/KTH/java-hello-world-with-maven/onlyAccessPriv.json";
        List<AccessRecord> allAccessRecords = loadAccessPrivilegesFromFile(path);
        for (DetectorCategoryFactory.DetectionCategory category : detectors) {
            String tempFileKey = category + "_recordFile";
            Path tempFile = tempFiles.get(tempFileKey);
            String recordingKey = category + "_recording";
            try (Recording recording = recordings.get(recordingKey)) {
                recording.stop();
            }
            allAccessRecords.addAll(processor.readRecordings(category.toString(), tempFile, allAccessRecords));
        }
        JsonUtils.writeToFile(Path.of(path), convertAccessRecordsToReport(allAccessRecords));
    }

    private Map<String, List<AccessRecord.DetectorEvent>> convertAccessRecordsToReport(List<AccessRecord> accessRecords) {
        Map<String, List<AccessRecord.DetectorEvent>> report = new HashMap<>();
        for (AccessRecord accessRecord : accessRecords) {
            report.put(accessRecord.getDependency(), accessRecord.getRecords());
        }
        return report;
    }

    private List<AccessRecord> loadAccessPrivilegesFromFile(String fileName) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        File file = new File(fileName);
        List<AccessRecord> allAccessRecords = new ArrayList<>();
        Map<String, List<AccessRecord.DetectorEvent>> existingAccessRecords =
                objectMapper.readValue(file, new TypeReference<>() {});
        for (Map.Entry<String, List<AccessRecord.DetectorEvent>> entry : existingAccessRecords.entrySet()) {
            allAccessRecords.add(new AccessRecord(entry.getKey(), entry.getValue()));
        }
        return allAccessRecords;
    }

}