package io.github.chains_project.theo.preprocessor;

import io.github.chains_project.maven_lockfile.data.LockFile;
import io.github.chains_project.maven_lockfile.graph.DependencyNode;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.DependencyManagement;
import org.apache.maven.model.Model;
import org.apache.maven.model.Repository;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.model.io.xpp3.MavenXpp3Writer;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

/**
 * This class includes methods to prepare the monitor. This includes preprocessing the monitor to add the classes from
 * dependencies of the project under test into the classpath of the monitor. To do that, we create a new pom file with
 * all the dependencies.
 */
public class DependencyPreProcessor {

    /**
     * Adds the dependencies of the project to the monitor's pom file.
     */
    public void processPomFile(Path monitorPomFilePath, Path lockfilePath, Path projectPomFilePath) {
        File projectPomFile = new File(projectPomFilePath.toUri());
        try {
            File monitorPomFile = monitorPomFilePath.toFile();
            Model monitorPomModel = readPomFile(monitorPomFile);
            Model projectPomModel = readPomFile(projectPomFile);
            LockFile lockFile = LockFile.readLockFile(lockfilePath);
            List<Dependency> lockfileDependencies = getNearestVersionDependency(lockFile);
            updateDependencies(monitorPomModel, lockfileDependencies, projectPomModel);
            writePomFile(monitorPomModel, monitorPomFile);
        } catch (IOException | XmlPullParserException e) {
            throw new RuntimeException("Could not create the pom file with the dependencies", e);
        }
    }

    private Model readPomFile(File pomFile) throws IOException, XmlPullParserException {
        try (FileReader fileReader = new FileReader(pomFile)) {
            MavenXpp3Reader reader = new MavenXpp3Reader();
            return reader.read(fileReader);
        }
    }

    private List<Dependency> getNearestVersionDependency(LockFile lockFileFromFile) {
        // This is a slightly modified version of the same method inside the maven lockfile maven plugin
        var deps = lockFileFromFile.getDependencies();
        Map<String, Dependency> nearestVersionMap = new HashMap<>();
        Queue<DependencyNode> depQueue = new ArrayDeque<>(deps);
        while (!depQueue.isEmpty()) {
            var depNode = depQueue.poll();
            Dependency dep = toMavenDependency(depNode);
            String key = dep.getGroupId() + ":" + dep.getArtifactId();
            if (depNode.isIncluded()) {
                nearestVersionMap.put(key, dep);
            }
            depQueue.addAll(depNode.getChildren());
        }
        return new ArrayList<>(nearestVersionMap.values());
    }

    private void updateDependencies(Model monitorPomModel, List<Dependency> lockfileDependencies,
                                    Model projectPomModel) {
        List<Dependency> monitorDependencies = monitorPomModel.getDependencies();
        // We handle the pom type dependencies separately because the maven lockfile does not store the type
        List<Dependency> projectDependencies = projectPomModel.getDependencies().stream()
                .filter(dep -> "pom".equals(dep.getType()))
                .collect(Collectors.toList());
        for (Dependency lockfileDep : lockfileDependencies) {
            // If the dependency type is pom, add it
            if (isDependencyPresent(projectDependencies, lockfileDep)) {
                lockfileDep.setType("pom");
            }
            projectDependencies.add(lockfileDep);
        }
        projectDependencies.addAll(monitorDependencies);
        monitorPomModel.setDependencies(projectDependencies);
        DependencyManagement projectDepManagement = projectPomModel.getDependencyManagement();
        // This overlooks the dependency management tags in other pom files
        monitorPomModel.setDependencyManagement(projectDepManagement);
        List<Repository> projectRepositories = projectPomModel.getRepositories();
        // This also overlooks the repositories defined in other pom files
        monitorPomModel.setRepositories(projectRepositories);
    }

    private boolean isDependencyPresent(List<Dependency> dependencies, Dependency dependency) {
        for (Dependency dep : dependencies) {
            if (dep.getGroupId().equals(dependency.getGroupId()) &&
                    dep.getArtifactId().equals(dependency.getArtifactId())) {
                return true;
            }
        }
        return false;
    }

    private Dependency toMavenDependency(DependencyNode dep) {
        Dependency mavenDep = new Dependency();
        mavenDep.setGroupId(dep.getGroupId().getValue());
        mavenDep.setArtifactId(dep.getArtifactId().getValue());
        mavenDep.setVersion(dep.getVersion().getValue());
        mavenDep.setScope(dep.getScope().getValue());
        return mavenDep;
    }

    private void writePomFile(Model pomModel, File pomLockFile) throws IOException {
        try (FileWriter fileWriter = new FileWriter(pomLockFile)) {
            MavenXpp3Writer writer = new MavenXpp3Writer();
            writer.write(fileWriter, pomModel);
        }
    }
}
