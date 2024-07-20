package io.github.chains_project.theo.preprocessor;

import io.github.chains_project.maven_lockfile.data.LockFile;
import io.github.chains_project.maven_lockfile.graph.DependencyNode;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.model.io.xpp3.MavenXpp3Writer;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

/**
 * This class includes methods to prepare the monitor. This includes preprocessing the monitor to add the classes from
 * dependencies of the project under test into the classpath of the monitor. To do that, we create a new pom file with
 * all the dependencies.
 */
public class DependencyPreProcessor {

    /**
     * Adds the dependencies of the project to the monitor's pom file.
     */
    public void processPomFile(Path lockfilePath) {
        File pomFile = new File("../monitor/org.xml");
        File pomLockFile = new File("../monitor/pom.xml");
        try {
            LockFile lockFile = LockFile.readLockFile(lockfilePath);
            List<Dependency> filteredDependencies = getNearestVersionDependency(lockFile);
            Model pomModel = readPomFile(pomFile);
            updateDependencies(pomModel, filteredDependencies);
            writePomLockFile(pomModel, pomLockFile);
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

    private void updateDependencies(Model pomModel, List<Dependency> filteredDependencies) {
        List<Dependency> pomDependencies = pomModel.getDependencies();
        pomDependencies.addAll(filteredDependencies);
        pomModel.setDependencies(pomDependencies);
    }

    private void writePomLockFile(Model pomModel, File pomLockFile) throws IOException {
        try (FileWriter fileWriter = new FileWriter(pomLockFile)) {
            MavenXpp3Writer writer = new MavenXpp3Writer();
            writer.write(fileWriter, pomModel);
        }
    }

    private List<Dependency> getNearestVersionDependency(LockFile lockFileFromFile) {
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

    /**
     * Converts a DependencyNode to a Maven Model Dependency.
     *
     * @param dep the DependencyNode to convert
     * @return the converted Dependency
     */
    private Dependency toMavenDependency(DependencyNode dep) {
        Dependency mavenDep = new Dependency();
        mavenDep.setGroupId(dep.getGroupId().getValue());
        mavenDep.setArtifactId(dep.getArtifactId().getValue());
        mavenDep.setVersion(dep.getVersion().getValue());
        mavenDep.setScope(dep.getScope().getValue());
        return mavenDep;
    }
}
