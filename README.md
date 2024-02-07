# dependency-privileges
dependency-privileges is a tool to monitor access privileges originating from third-party dependencies.
It extracts runtime  information by running the test suite of a project and maps the 
resource accesses to dependencies. 

## How it works

1. Dependency-privileges contains a JUnit custom test engine that will extend the test cases in the project test suite to monitor the test execution.
2. The monitoring part of the running tests is done using the Java Flight Recorder (JFR).  
3. The [maven-lockfile](https://github.com/chains-project/maven-lockfile) is used to generate a lockfile for the project, which will be extended with access privilege information collected from the JFR.

## Usage

To run the tool, add the plugin to your `pom.xml`.

```xml
<plugin>
    <groupId>io.github.chains-project</groupId>
    <artifactId>dependency-privileges</artifactId>
    <version>1.0-SNAPSHOT</version>
    <executions>
        <execution>
            <goals>
                <goal>generate</goal>
            </goals>
            <phase>compile</phase>
        </execution>
    </executions>
</plugin>
```

Then execute the test suite.

 ```sh
  mvn clean test
 ```


## Reports


