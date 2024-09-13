package io.github.chains_project.theo.testGenerator;

import picocli.CommandLine;

import java.nio.file.Path;
import java.util.concurrent.Callable;

@CommandLine.Command(name = "call", mixinStandardHelpOptions = true, version = "0.1")
public class Main {

    public static void main(String[] args) {
        int exitCode = new CommandLine(new GenerateTests()).execute(args);
        System.exit(exitCode);
    }

    @CommandLine.Command(name = "generate", mixinStandardHelpOptions = true, version = "0.1")
    private static class GenerateTests implements Callable<Integer> {
        @CommandLine.Option(
                names = {"-p", "--path"},
                paramLabel = "PATH",
                description = "The path to the target Maven project",
                required = true)
        Path projectPath;

        @CommandLine.Option(
                names = {"-r", "--root"},
                paramLabel = "ROOT",
                description = "The root package name of the target Maven project. " +
                        "This is used to filter out the methods coming from other modules in the same project.",
                defaultValue = "")
        String rootPackage;

        @Override
        public Integer call() throws Exception {
            new TheoTestGenerator(projectPath, rootPackage).analyzeWithSpoon();
            return 0;
        }
    }
}
