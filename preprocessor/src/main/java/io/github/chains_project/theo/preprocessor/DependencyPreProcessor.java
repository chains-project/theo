package io.github.chains_project.theo.preprocessor;

import org.apache.maven.model.*;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.model.io.xpp3.MavenXpp3Writer;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * This class includes methods to prepare the monitor. This includes preprocessing the monitor to add the classes from
 * dependencies of the project under test into the classpath of the monitor. To do that, we create a new pom file with
 * all the dependencies.
 */
public class DependencyPreProcessor {

    /**
     * Adds the dependencies of the project to the monitor's pom file.
     */
    public void processPomFile(Path projectPomFilePath) {
        File pomFile = new File("../monitor/pom.xml");
        File projectPomFile = new File(projectPomFilePath.toUri());
        try {
            Model monitorPomModel = readPomFile(pomFile);
            Model projectPomModel = readPomFile(projectPomFile);
            updateDependencies(monitorPomModel, projectPomModel);
            writePomLockFile(monitorPomModel, pomFile);
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

    private void updateDependencies(Model monitorPomModel, Model projectPomModel) {
        List<Dependency> projectDependencies = projectPomModel.getDependencies();
        DependencyManagement projectDepManagement = projectPomModel.getDependencyManagement();
        Parent projectDepParent = projectPomModel.getParent();
        List<Dependency> monitorPomDependencies = monitorPomModel.getDependencies();
        monitorPomDependencies.addAll(projectDependencies);
        monitorPomModel.setDependencies(monitorPomDependencies);
        monitorPomModel.setDependencies(monitorPomDependencies);
        monitorPomModel.setDependencyManagement(projectDepManagement);
        if (projectPomModel.getParent() != null) {
            monitorPomModel.setParent(projectDepParent);
        }
        List<Repository> projectRepositories = projectPomModel.getRepositories();
        monitorPomModel.setRepositories(projectRepositories);
    }

    private void writePomLockFile(Model pomModel, File pomLockFile) throws IOException {
        try (FileWriter fileWriter = new FileWriter(pomLockFile)) {
            MavenXpp3Writer writer = new MavenXpp3Writer();
            writer.write(fileWriter, pomModel);
        }
    }
}
