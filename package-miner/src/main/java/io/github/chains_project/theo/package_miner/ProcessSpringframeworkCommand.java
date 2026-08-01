package io.github.chains_project.theo.package_miner;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.chains_project.theo.package_miner.model.PackageInfo;
import io.github.chains_project.theo.package_miner.model.VersionHistory;
import io.github.chains_project.theo.package_miner.model.VersionInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.regex.Pattern;

@CommandLine.Command(name = "process-springframework", mixinStandardHelpOptions = true,
        description = "Process previously skipped org.springframework packages (version history analysis).")
public class ProcessSpringframeworkCommand implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(ProcessSpringframeworkCommand.class);
    private static final long SCAN_TIMEOUT_MINUTES = 30;
    private static final int MAX_VERSIONS_PER_PACKAGE = 50;
    private static final int VERSION_HISTORY_YEARS = 5;
    private static final Pattern PRE_RELEASE = Pattern.compile(
            "SNAPSHOT|alpha|beta|-rc|-m\\d|milestone|nightly|dev|preview|incubating",
            Pattern.CASE_INSENSITIVE);

    private static final String SKIPPED_FILE = "skipped_springframework_for_later.json";

    @CommandLine.Option(names = {"-o", "--output-dir"}, paramLabel = "OUTPUT-DIR",
            description = "Directory with existing scan results.", required = true)
    Path outputDir;

    @CommandLine.Option(names = {"-j", "--analyzer-jar"}, paramLabel = "ANALYZER-JAR",
            description = "Path to the package-static-analyzer jar-with-dependencies JAR.", required = true)
    Path analyzerJar;

    @CommandLine.Option(names = {"--download-dir"}, paramLabel = "DOWNLOAD-DIR",
            description = "Directory for downloaded JARs. Defaults to <output-dir>/jars.")
    Path downloadDir;

    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public void run() {
        if (downloadDir == null) {
            downloadDir = outputDir.resolve("jars");
        }

        Path skippedFile = outputDir.resolve(SKIPPED_FILE);
        if (!Files.exists(skippedFile)) {
            log.error("No {} found in {}.", SKIPPED_FILE, outputDir);
            return;
        }

        List<PackageInfo> remaining;
        try {
            remaining = mapper.readValue(skippedFile.toFile(), new TypeReference<>() {});
        } catch (IOException e) {
            log.error("Failed to read {}.", skippedFile, e);
            return;
        }

        if (remaining.isEmpty()) {
            log.info("{} is empty — nothing to process.", SKIPPED_FILE);
            return;
        }

        log.info("Loaded {} springframework packages to process.", remaining.size());

        MavenCentralClient client = new MavenCentralClient();
        PackageAnalyzer analyzer = new PackageAnalyzer(analyzerJar, outputDir, client);
        VersionHistoryTracker tracker = new VersionHistoryTracker();

        int succeeded = 0;
        int failed = 0;
        int skipped = 0;

        List<PackageInfo> stillRemaining = new ArrayList<>(remaining);

        for (int i = 0; i < remaining.size(); i++) {
            PackageInfo pkg = remaining.get(i);
            log.info("[{}/{}] Processing {}...", i + 1, remaining.size(), pkg.coordinate());

            Path historyFile = outputDir.resolve("version-history")
                    .resolve(pkg.groupId() + "_" + pkg.artifactId() + "-history.json");
            if (Files.exists(historyFile)) {
                log.info("  Version history already exists, skipping.");
                stillRemaining.remove(pkg);
                saveRemaining(skippedFile, stillRemaining);
                skipped++;
                continue;
            }

            boolean success = processVersionHistory(pkg, client, analyzer, tracker);

            if (success) {
                succeeded++;
            } else {
                failed++;
            }

            stillRemaining.remove(pkg);
            saveRemaining(skippedFile, stillRemaining);
            log.info("  Removed from {}. {} remaining.", SKIPPED_FILE, stillRemaining.size());
        }

        log.info("=============================================================");
        log.info("  SPRINGFRAMEWORK PROCESSING RESULTS");
        log.info("  Total:     {}", remaining.size());
        log.info("  Succeeded: {}", succeeded);
        log.info("  Failed:    {}", failed);
        log.info("  Skipped:   {} (already had version history)", skipped);
        log.info("=============================================================");

        try {
            new VersionHistoryVisualizer().generateReport(outputDir);
            log.info("Regenerated version history visualization.");
        } catch (IOException e) {
            log.error("Failed to regenerate visualization.", e);
        }
    }

    private boolean processVersionHistory(PackageInfo pkg, MavenCentralClient client,
                                          PackageAnalyzer analyzer, VersionHistoryTracker tracker) {
        List<VersionInfo> allVersions;
        try {
            allVersions = client.fetchVersions(pkg.groupId(), pkg.artifactId(), VERSION_HISTORY_YEARS);
        } catch (Exception e) {
            log.warn("  Failed to fetch versions: {}", e.getMessage());
            return false;
        }

        List<VersionInfo> stableVersions = allVersions.stream()
                .filter(v -> !PRE_RELEASE.matcher(v.version()).find())
                .toList();

        if (stableVersions.size() <= 1) {
            log.info("  Only {} stable version(s), skipping.", stableVersions.size());
            return false;
        }

        if (stableVersions.size() > MAX_VERSIONS_PER_PACKAGE) {
            log.info("  {} stable versions, capping to {}.", stableVersions.size(), MAX_VERSIONS_PER_PACKAGE);
            stableVersions = stableVersions.subList(
                    stableVersions.size() - MAX_VERSIONS_PER_PACKAGE, stableVersions.size());
        }

        log.info("  Analyzing {} stable versions...", stableVersions.size());

        List<VersionHistoryTracker.VersionReportEntry> reportEntries = new ArrayList<>();
        boolean firstVersion = true;

        for (VersionInfo ver : stableVersions) {
            try {
                PackageInfo versionPkg = ver.toPackageInfo();

                String reportKey = ver.groupId() + "_" + ver.artifactId() + "_" + ver.version();
                Path existingReport = outputDir.resolve("reports").resolve(reportKey + "-report.json");
                if (Files.exists(existingReport) && Files.size(existingReport) > 0) {
                    reportEntries.add(new VersionHistoryTracker.VersionReportEntry(
                            ver.version(), ver.timestamp(), existingReport));
                    firstVersion = false;
                    continue;
                }

                Path bytecodeJar = client.downloadBytecodeJarForVersion(ver, downloadDir);
                if (bytecodeJar == null) {
                    continue;
                }

                Path sourceJar = client.downloadSourceJarForVersion(ver, downloadDir);

                ExecutorService timeoutExecutor = Executors.newSingleThreadExecutor();
                Future<PackageAnalyzer.AnalysisResult> analysisFuture =
                        timeoutExecutor.submit(() -> analyzer.analyze(versionPkg, bytecodeJar, sourceJar));

                PackageAnalyzer.AnalysisResult result;
                try {
                    result = analysisFuture.get(SCAN_TIMEOUT_MINUTES, TimeUnit.MINUTES);
                } catch (TimeoutException e) {
                    analysisFuture.cancel(true);
                    if (firstVersion) {
                        log.warn("  First version {} timed out, skipping entire package.", ver.coordinate());
                        return false;
                    }
                    log.warn("  Version {} timed out, skipping.", ver.coordinate());
                    continue;
                } finally {
                    timeoutExecutor.shutdownNow();
                }

                firstVersion = false;

                if (result.analyzerSucceeded()) {
                    Path reportFile = outputDir.resolve("reports").resolve(reportKey + "-report.json");
                    if (Files.exists(reportFile)) {
                        reportEntries.add(new VersionHistoryTracker.VersionReportEntry(
                                ver.version(), ver.timestamp(), reportFile));
                    }
                }
            } catch (OutOfMemoryError e) {
                log.warn("  OOM analyzing version {}, skipping.", ver.coordinate());
            } catch (Exception e) {
                log.debug("  Failed to analyze version {}: {}", ver.coordinate(), e.getMessage());
            }
        }

        if (reportEntries.size() >= 2) {
            try {
                VersionHistory.PackageVersionHistory history =
                        tracker.buildHistory(pkg.groupId(), pkg.artifactId(), reportEntries);
                tracker.saveHistory(history, outputDir);
                log.info("  {} versions analyzed, changes: {}", reportEntries.size(), history.hasPermissionChanges());
                return true;
            } catch (IOException e) {
                log.error("  Failed to save version history.", e);
            }
        } else {
            log.info("  Only {} usable reports, not enough for history.", reportEntries.size());
        }

        return false;
    }

    private void saveRemaining(Path file, List<PackageInfo> remaining) {
        try {
            mapper.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), remaining);
        } catch (IOException e) {
            log.error("Failed to update {}.", file, e);
        }
    }
}
