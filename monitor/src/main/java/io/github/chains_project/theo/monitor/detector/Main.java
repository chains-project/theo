package io.github.chains_project.theo.monitor.detector;

import shaded.picocli.CommandLine;

import java.nio.file.Path;

/**
 * This class represents the main entry point to the Detector.
 */
public class Main {

    public static void main(String[] args) {
        int exitCode = new CommandLine(new CLIEntryPoint()).execute(args);
        System.exit(exitCode);
    }

    @CommandLine.Command(subcommands = {Offline.class, Online.class}, mixinStandardHelpOptions = true, version = "0.1")
    public static class CLIEntryPoint implements Runnable {
        @Override
        public void run() {
            CommandLine.usage(this, System.out);
        }
    }

    @CommandLine.Command(name = "track-offline", mixinStandardHelpOptions = true, version = "0.1")
    private static class Offline implements Runnable {
        @CommandLine.Option(
                names = {"-j", "--jfr-recording"},
                paramLabel = "RECORDING-FILE",
                description = "The path to the file containing the JFR recordings",
                required = true
        )
        Path recordingFile;

        @CommandLine.Option(
                names = {"-l", "--lockfile"},
                paramLabel = "LOCKFILE",
                description = "The path to the lockfile generated by the maven-lockfile",
                required = true
        )
        Path lockfile;

        @CommandLine.Option(
                names = {"-r", "--report-file"},
                paramLabel = "REPORT-FILE",
                description = "The path to the JSON file where the report should be written to. If not specified," +
                        " the report will be written to a file named test-report.json",
                defaultValue = "test-report.json"
        )
        Path reportFile;

        @Override
        public void run() {
            BaseDetector baseDetector = new BaseDetector();
            Processor processor = new Processor();
            baseDetector.trackJFREventsOffline(recordingFile, reportFile, lockfile, processor);
        }
    }

    @CommandLine.Command(name = "track-online", mixinStandardHelpOptions = true, version = "0.1")
    private static class Online implements Runnable {
        @CommandLine.Option(
                names = {"-a", "--repository-path"},
                paramLabel = "REPOSITORY-PATH",
                description = "the JFR repository of the Java application under consideration. " +
                        "The default path is /tmp. If you are on Windows, this default value will not work. " +
                        "In that case, specifying a custom repository path is mandatory.",
                defaultValue = "/tmp"
        )
        Path repositoryPath;

        @CommandLine.Option(
                names = {"-l", "--lockfile"},
                paramLabel = "LOCKFILE",
                description = "The path to the lockfile generated by the maven-lockfile",
                required = true
        )
        Path lockfile;

        @CommandLine.Option(
                names = {"-r", "--report-file"},
                paramLabel = "REPORT-FILE",
                description = "The path to the JSON file where the report should be written to. If not specified," +
                        " the report will be written to a file named prod-report.json",
                defaultValue = "prod-report.json"
        )
        Path reportFile;

        @Override
        public void run() {
            BaseDetector baseDetector = new BaseDetector();
            Processor processor = new Processor();
            baseDetector.trackJFREventsOnline(repositoryPath, reportFile, lockfile, processor);
        }
    }
}
