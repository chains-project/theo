package io.github.chains_project.theo.testGenerator;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spoon.MavenLauncher;
import spoon.reflect.CtModel;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public class TheoTestGenerator {

    private static final Logger log = LoggerFactory.getLogger(TheoTestGenerator.class);
    private final Path projectPath;
    private final String projectName;

    public TheoTestGenerator(Path projectPath) {
        this.projectPath = projectPath;
        this.projectName = projectPath.getFileName().toString();
    }

    private void writeJsonReportToDisk(List<TheoMethod> targetMethodList) {
        String report = "./target-methods-" + projectName + ".json";
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        try (FileWriter writer = new FileWriter(report)) {
            gson.toJson(targetMethodList, writer);
            log.info("Target methods saved in " + report);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void analyzeWithSpoon() {
        log.info("Processing project " + projectName);
        MavenLauncher launcher;
        try {
            log.info("Considering only app sources");
            launcher = new MavenLauncher(projectPath.toString(),
                    MavenLauncher.SOURCE_TYPE.APP_SOURCE);
            launcher.buildModel();
        } catch (Exception e) {
            log.error(e.getMessage());
            return;
        }
        CtModel model = launcher.getModel();
        MethodProcessor methodProcessor = new MethodProcessor();
        model.processWith(methodProcessor);
        // log.info("Generating invocations for the methods: {}", methodProcessor.getTestMethods());
        writeJsonReportToDisk(methodProcessor.getTestMethods());
        String outputDirectory = "./output/generated/" + projectName;
        launcher.setSourceOutputDirectory(outputDirectory);
        launcher.prettyprint();
    }
}
