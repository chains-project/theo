package io.github.chains_project.dependency_privileges;

import io.github.chains_project.maven_lockfile.AbstractLockfileMojo;
import io.github.chains_project.maven_lockfile.JsonUtils;
import io.github.chains_project.maven_lockfile.LockFileFacade;
import io.github.chains_project.maven_lockfile.checksum.AbstractChecksumCalculator;
import io.github.chains_project.maven_lockfile.data.Config;
import io.github.chains_project.maven_lockfile.data.Environment;
import io.github.chains_project.maven_lockfile.data.LockFile;
import io.github.chains_project.maven_lockfile.data.MetaData;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * This plugin generates a lockfile with finer level information on runtime access privileges
 */
@Mojo(
        name = "generate",
        defaultPhase = LifecyclePhase.GENERATE_RESOURCES,
        requiresDependencyResolution = ResolutionScope.COMPILE,
        requiresOnline = true)
public class GenerateReportMojo extends AbstractLockfileMojo {

    @Parameter(defaultValue = "true", property = "getConfigFromFile")
    String getConfigFromFile;

    /**
     * Generate a report with access privileges for the dependencies of the current project.
     *
     * @throws MojoExecutionException if the file could not be written or the generation failed.
     */
    public void execute() throws MojoExecutionException {
        try {
            Environment environment = generateMetaInformation();
            Path lockFilePath = Path.of(project.getBasedir().getAbsolutePath(), "access-privileges.json");
            LockFile lockFileFromFile =
                    Files.exists(lockFilePath) ? LockFile.readLockFile(lockFilePath) : null;
            Config config = Boolean.parseBoolean(getConfigFromFile) ? getConfig(lockFileFromFile) : getConfig();
            MetaData metaData = new MetaData(environment, config);

            AbstractChecksumCalculator checksumCalculator = getChecksumCalculator(config);
            LockFile lockFile = LockFileFacade.generateLockFileFromProject(
                    session, project, dependencyCollectorBuilder, checksumCalculator, metaData);

            Files.writeString(lockFilePath, JsonUtils.toJson(lockFile));
        } catch (IOException e) {
            getLog().error(e);
        }
    }

    private Config getConfig(LockFile lockFileFromFile) {
        if (lockFileFromFile == null || lockFileFromFile.getConfig() == null) {
            return getConfig();
        }
        return lockFileFromFile.getConfig();
    }
}