package io.github.chains_project.theo.monitor.detector;

import io.github.chains_project.theo.monitor.utils.AccessRecord;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.io.FileWriter;
import java.nio.file.Path;
import java.util.ArrayList;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

public class BaseDetectorTest {

    @TempDir
    static Path tempDir;

    @BeforeAll
    public static void trackJFREventsOffline() throws Exception {

        Path recordingFilePath = tempDir.resolve("recording.jfr");
        // No assertions yet!
        // Commit events to the recording file so rf.hasMoreEvents is true
        // test process event - Can verify if processEvent/readRecordings gets called with each event - can replace any
        // with specific event
        // test convertAccessRecord method - check the report
        Path reportFile = tempDir.resolve("test-report.json");
        Path lockFile = tempDir.resolve("lockfile.json");
        // This is a dummy writer. Can write something meaningful if needed.
        try (FileWriter writer = new FileWriter(lockFile.toFile())) {
            writer.write("{}");
        }
        Processor processorMock = Mockito.mock(Processor.class);
        AccessRecord accessRecord = new AccessRecord("com.example", new ArrayList<>());
        when(processorMock.readRecordings(anyString(), any())).thenReturn(accessRecord);
        BaseDetector baseDetector = new BaseDetector();
        baseDetector.trackJFREventsOffline(recordingFilePath, reportFile, lockFile, processorMock);
    }
}
