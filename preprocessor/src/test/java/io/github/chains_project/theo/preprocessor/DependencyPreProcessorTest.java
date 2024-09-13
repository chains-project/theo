package io.github.chains_project.theo.preprocessor;

import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class DependencyPreProcessorTest {

    @TempDir
    Path tempDir;

    @InjectMocks
    private DependencyPreProcessor dependencyPreProcessor;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    public void testProcessPomFile() throws IOException, XmlPullParserException {
        Path monitorDir = tempDir.resolve("monitor");
        monitorDir.toFile().mkdirs();
        File monitorPomFile = monitorDir.resolve("pom.xml").toFile();
        File projectPomFile = tempDir.resolve("projectPom.xml").toFile();
        File lockFile = tempDir.resolve("lockfile.json").toFile();
        try (FileWriter writer = new FileWriter(monitorPomFile)) {
            writer.write("<project>" +
                    "<modelVersion>4.0.0</modelVersion>" +
                    "<groupId>test</groupId>" +
                    "<artifactId>monitor</artifactId>" +
                    "<version>1.0</version>" +
                    "</project>");
        }
        try (FileWriter writer = new FileWriter(projectPomFile)) {
            writer.write("<project>" +
                    "<modelVersion>4.0.0</modelVersion>" +
                    "<groupId>test</groupId>" +
                    "<artifactId>project</artifactId>" +
                    "<version>1.0.0</version>" +
                    "<dependencies>" +
                    "<dependency>" +
                    "<groupId>com.example</groupId>" +
                    "<artifactId>example-dep</artifactId>" +
                    "<version>1.0.0</version>" +
                    "</dependency></dependencies>" +
                    "</project>");
        }
        try (FileWriter writer = new FileWriter(lockFile)) {
            writer.write("""
                    {
                       "artifactID":"project",
                       "groupID":"test",
                       "version":"1.0.0",
                       "lockFileVersion":1,
                       "dependencies":[
                          {
                             "groupId":"com.example",
                             "artifactId":"example-dep",
                             "version":"1.0.0",
                             "checksumAlgorithm":"SHA-256",
                             "checksum":"25f23dc535a091e9dc80c008faf29dcb92be902e6911f77a736fbaf019908367",
                             "scope": "compile",
                             "selectedVersion": "1.0.0",
                             "included": true,
                             "id":"com.example:example-dep:1.0.0",
                             "children":[]
                           }
                       ]
                    }""");
        }
        // Has to test this end-to-end because other methods have private access
        dependencyPreProcessor.processPomFile(monitorPomFile.toPath(), lockFile.toPath(), projectPomFile.toPath());
        MavenXpp3Reader reader = new MavenXpp3Reader();
        Model monitorPomModel;
        try (FileReader monitorPomReader = new FileReader(monitorPomFile)) {
            monitorPomModel = reader.read(monitorPomReader);
        }
        assertTrue(monitorPomModel.getDependencies().stream().anyMatch(
                dep -> "com.example".equals(dep.getGroupId()) && "example-dep".equals(dep.getArtifactId()) &&
                        "1.0.0".equals(dep.getVersion())));

    }
}
