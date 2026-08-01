package io.github.chains_project.theo.package_miner;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.chains_project.theo.package_miner.model.PackageInfo;
import io.github.chains_project.theo.package_miner.model.VersionHistory;
import io.github.chains_project.theo.package_miner.model.VersionInfo;
import io.github.chains_project.theo.package_miner.util.CheckpointManager;
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
        description = "Analyze all org.springframework packages: run per-version static analysis and build version history.")
public class ProcessSpringframeworkCommand implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(ProcessSpringframeworkCommand.class);
    private static final long SCAN_TIMEOUT_MINUTES = 30;
    private static final int VERSION_HISTORY_YEARS = 5;
    private static final Pattern PRE_RELEASE = Pattern.compile(
            "SNAPSHOT|alpha|beta|-rc|-m\\d|milestone|nightly|dev|preview|incubating",
            Pattern.CASE_INSENSITIVE);

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

        // Load all packages from selected_packages.json
        CheckpointManager checkpoint = new CheckpointManager(outputDir);
        List<PackageInfo> allPackages = checkpoint.loadPackageList();
        if (allPackages == null || allPackages.isEmpty()) {
            log.error("No selected_packages.json found in {}.", outputDir);
            return;
        }

        // Filter to org.springframework only
        List<PackageInfo> springPackages = allPackages.stream()
                .filter(p -> p.groupId().startsWith("org.springframework"))
                .toList();

        if (springPackages.isEmpty()) {
            log.info("No org.springframework packages found in selected_packages.json.");
            return;
        }

        log.info("Found {} org.springframework packages in selected_packages.json.", springPackages.size());

        MavenCentralClient client = new MavenCentralClient();
        PackageAnalyzer analyzer = new PackageAnalyzer(analyzerJar, outputDir, client);
        VersionHistoryTracker tracker = new VersionHistoryTracker();

        int succeeded = 0;
        int failed = 0;
        int skippedHistory = 0;

        for (int i = 0; i < springPackages.size(); i++) {
            PackageInfo pkg = springPackages.get(i);
            log.info("[{}/{}] Processing {}...", i + 1, springPackages.size(), pkg.coordinate());

            // Skip if version history already exists
            Path historyFile = outputDir.resolve("version-history")
                    .resolve(pkg.groupId() + "_" + pkg.artifactId() + "-history.json");
            if (Files.exists(historyFile)) {
                log.info("  Version history already exists, skipping.");
                skippedHistory++;
                continue;
            }

            boolean success = processVersionHistory(pkg, client, analyzer, tracker);
            if (success) {
                succeeded++;
            } else {
                failed++;
            }
        }

        log.info("=============================================================");
        log.info("  SPRINGFRAMEWORK PROCESSING RESULTS");
        log.info("  Total:                  {}", springPackages.size());
        log.info("  Succeeded:              {}", succeeded);
        log.info("  Failed:                 {}", failed);
        log.info("  Skipped (had history):  {}", skippedHistory);
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

        log.info("  Analyzing {} stable versions...", stableVersions.size());

        List<VersionHistoryTracker.VersionReportEntry> reportEntries = new ArrayList<>();
        boolean firstVersion = true;

        for (VersionInfo ver : stableVersions) {
            try {
                PackageInfo versionPkg = ver.toPackageInfo();

                String reportKey = ver.groupId() + "_" + ver.artifactId() + "_" + ver.version();
                Path existingReport = outputDir.resolve("reports").resolve(reportKey + "-report.json");

                // Reuse existing report if available
                if (Files.exists(existingReport) && Files.size(existingReport) > 0) {
                    log.info("    {} — reusing existing report.", ver.version());
                    reportEntries.add(new VersionHistoryTracker.VersionReportEntry(
                            ver.version(), ver.timestamp(), existingReport));
                    firstVersion = false;
                    continue;
                }

                // No report exists — run analysis
                log.info("    {} — analyzing...", ver.version());
                Path bytecodeJar = client.downloadBytecodeJarForVersion(ver, downloadDir);
                if (bytecodeJar == null) {
                    log.warn("    {} — download failed, skipping.", ver.version());
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
                        log.warn("    {} — first version timed out, skipping entire package.", ver.version());
                        return false;
                    }
                    log.warn("    {} — timed out, skipping.", ver.version());
                    continue;
                } finally {
                    timeoutExecutor.shutdownNow();
                }

                firstVersion = false;

                if (result.analyzerSucceeded()) {
                    Path reportFile = outputDir.resolve("reports").resolve(reportKey + "-report.json");
                    if (Files.exists(reportFile)) {
                        log.info("    {} — analysis succeeded.", ver.version());
                        reportEntries.add(new VersionHistoryTracker.VersionReportEntry(
                                ver.version(), ver.timestamp(), reportFile));
                    }
                } else {
                    log.warn("    {} — analyzer failed.", ver.version());
                }
            } catch (OutOfMemoryError e) {
                log.warn("    {} — OOM, skipping.", ver.coordinate());
            } catch (Exception e) {
                log.warn("    {} — failed: {}", ver.coordinate(), e.getMessage());
            }
        }

        if (reportEntries.size() >= 2) {
            try {
                VersionHistory.PackageVersionHistory history =
                        tracker.buildHistory(pkg.groupId(), pkg.artifactId(), reportEntries);
                tracker.saveHistory(history, outputDir);
                log.info("  Done: {} versions analyzed, permission changes: {}",
                        reportEntries.size(), history.hasPermissionChanges());
                return true;
            } catch (IOException e) {
                log.error("  Failed to save version history.", e);
            }
        } else {
            log.info("  Only {} usable reports out of {} versions, not enough for history.",
                    reportEntries.size(), stableVersions.size());
        }

        return false;
    }
}
