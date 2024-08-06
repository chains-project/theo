package io.github.chains_project.theo.testGenerator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spoon.MavenLauncher;
import spoon.reflect.CtModel;

import java.nio.file.Path;

public class TheoTestGenerator {

    private static final Logger log = LoggerFactory.getLogger(TheoTestGenerator.class);
    private final Path projectPath;
    private final String projectName;

    public TheoTestGenerator(Path projectPath) {
        this.projectPath = projectPath;
        this.projectName = projectPath.getFileName().toString();
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
        log.info("generating invocations for the methods: {}", methodProcessor.getTestMethods());
        String outputDirectory = "./output/generated/" + projectName;
        launcher.setSourceOutputDirectory(outputDirectory);
        launcher.prettyprint();
    }
}
