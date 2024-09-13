# Theo-preprocessor

The preprocessor makes the required modifications to the `Theo-monitor` to enable monitoring of a specific project. 
Specifically, it adds the main module’s dependencies from the target project into the monitor’s pom.xml. This ensures 
that the project’s dependencies are included in the monitor’s classpath, allowing runtime-loaded classes to be mapped 
to their corresponding dependency information.
To prevent any conflicts between the monitor's existing dependencies and those added by the preprocessor temporarily, 
the preprocessor uses the `shader`, which shades compile-scoped dependencies within the monitor.

## Usage

Before running the preprocessor you should first create a [maven-lockfile](https://github.com/chains-project/maven-lockfile) 
for your project. You can create one by running the following command.
    ```
    mvn io.github.chains-project:maven-lockfile:generate
    ```

Then, follow the two steps given below.

 1. Package the preprocessor
    ```
    mvn clean package
    ```

 2. Execute the preprocessor
    ```
    mvn exec:java -Dexec.args="-p <pomfile_path> -l <lockfile_path>"
    ```
    The `pomfile_path` is the path to the pom file of your project. If your project is a multi module project, give the pom
    file path of the module with the executable main class. The `lockfile_path` is the path to the generated lockfile in the
    previous step.

## Work in progress

 - A cleaner solution is to use [classport](https://github.com/chains-project/classport) to annotate the dependencies. 
   Then, we will be able to get the dependency information when a class is loaded without needing to follow these preprocessing steps. 

## Limitations

 - The project under consideration has to use the same local maven repository (the default is `%USER_HOME%/.m2`) as the monitor.
 - Cannot consider the configs under the dependencyManagement tags in child pom files such as dependency exclusions. 
   However, the impact of this is minimal because we merely want to map the classes loaded at runtime with the dependency
   information. If a class is not loaded because it is excluded, then that has no impact on Theo.
 - Cannot consider the repositories defined under the child poms. The users will have to check and add them manually to the 
   monitor's pom file if needed.
