package io.github.chains_project.theo.testGenerator;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spoon.MavenLauncher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtCompilationUnit;
import spoon.reflect.declaration.CtType;
import spoon.reflect.factory.Factory;
import spoon.reflect.visitor.DefaultJavaPrettyPrinter;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class TheoTestGenerator {

    private static final Logger log = LoggerFactory.getLogger(TheoTestGenerator.class);
    private final Path projectPath;
    private final String projectName;
    private final String rootPackage;

    public TheoTestGenerator(Path projectPath, String rootPackage) {
        this.projectPath = projectPath;
        this.projectName = projectPath.getFileName().toString();
        /* When provided, we use the root package name to filter out the methods coming from the modules in the same
         project. We do this because we need to generate tests only for the methods coming from third party libraries.*/
        this.rootPackage = rootPackage;
    }

    private void writeJsonReportToDisk(List<TheoMethod> targetMethodList) {
        String report = "./methods-n-invocations-" + projectName + ".json";
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        try (FileWriter writer = new FileWriter(report)) {
            gson.toJson(targetMethodList, writer);
            log.info("Methods and invocations saved in " + report);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void prettyPrintGeneratedClasses(String outputDirectory, Set<CtType<?>> generatedTestClasses) {
        if (!generatedTestClasses.isEmpty()) {
            Factory factory = generatedTestClasses.iterator().next().getFactory();
            DefaultJavaPrettyPrinter printer = new DefaultJavaPrettyPrinter(factory.getEnvironment());
            for (CtType<?> generatedTestClass : generatedTestClasses) {
                CtCompilationUnit compilationUnit = factory.CompilationUnit().getOrCreate(generatedTestClass);
                // Get the package name and convert it to a folder path
                String packageName = generatedTestClass.getPackage().getQualifiedName();
                String packagePath = packageName.replace('.', File.separatorChar);
                File packageDir = new File(outputDirectory, packagePath);
                if (!packageDir.exists()) {
                    packageDir.mkdirs();
                }
                printer.calculate(compilationUnit, Collections.singletonList(generatedTestClass));
                try (FileWriter writer = new FileWriter(new File(packageDir, generatedTestClass.getSimpleName() + ".java"))) {
                    writer.write(printer.getResult());
                } catch (IOException e) {
                    log.error("Failed to write the generated test class {} in the output directory.",
                            generatedTestClass.getSimpleName(), e);
                }
            }
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
        MethodProcessor methodProcessor = new MethodProcessor(rootPackage);
        model.processWith(methodProcessor);
        // log.info("Generating invocations for the methods: {}", methodProcessor.getTestMethods());
        writeJsonReportToDisk(methodProcessor.getTestMethods());
        String outputDirectory = "./output/generated/" + projectName + "/test";
        prettyPrintGeneratedClasses(outputDirectory, methodProcessor.getGeneratedTestClasses());
    }
}
