[![Build Status][ci-shield]][ci-link]

# Theo
Theo is a tool designed to monitor access privileges originating from third-party dependencies. By running the test 
suite and/or executing a workload, it captures runtime information and maps resource accesses to specific dependencies. 

## Motivation

When new dependencies are added to a project or when their versions get updated, the code diff only shows basic changes 
added to the dependency config files. However, these updates can introduce thousands of lines of changes within the 
third-party libraries, which are not easily visible.

While there are tools that provide information on CVEs or generate call graphs, none offer a clear visualization of 
access privileges or highlight security-sensitive API changes inside third-party libraries. Moreover, existing tools 
rely on static analysis, lacking the insights that dynamic code analysis offers.

Having real-time, runtime information about the actual capabilities of third-party libraries can help developers make 
better-informed decisions when managing and updating dependencies.

## How it works

**Components**: Theo consists of a test generator, preprocessor, and monitor.

**Test Generator (optional)**: If the existing test suite doesn’t cover all possible APIs, the test generator can create
dummy tests without assertions to invoke all reachable APIs. It uses the [Spoon](https://github.com/INRIA/spoon) library
for generating these API invocations. [More info](testGenerator/README.md)

**Preprocessor**: Theo uses the [maven-lockfile](https://github.com/chains-project/maven-lockfile) to create a lockfile
for the project. The preprocessor also temporarily adds project dependencies to the classpath of the monitor by modifying 
the pom.xml. [More info](preprocessor/README.md)

**Monitor**: Theo uses [Java Flight Recorder (JFR)](https://openjdk.org/jeps/328) to capture runtime data during program
execution. Then, a report is generated with access privilege data collected by JFR. [More info](monitor/README.md)

## Usage

To run the tool, execute the [`run_workflow.sh`](run_workflow.sh).

Here's a breakdown of what it does.

- Packages the preprocessor
- Generates a lockfile for the project under consideration
- Adds the dependencies of the project to the classpath of the monitor
- Packages the monitor
- Runs the test cases or the workload depending on the use case with JFR attached
- Runs the monitor and generates the reports
- Resets the pom files

Alternatively, execute the `preprocessor` and the `monitor` following the steps given in [`preprocessor/README.md`](preprocessor/README.md) 
and [`monitor/README.md`](monitor/README.md) respectively.

## Work in progress
 
 - Use [classport](https://github.com/chains-project/classport) instead of adding all the dependencies to the pom file to get the dependency information at runtime. 
   If we use classport, then we can remove the preprocessor and the shader. We can also execute the jars instead of using mvn exec. 
   All of that is needed only because we need dependency information at runtime.
 - Visualize the changes in the generated reports between two versions of code.
 - Add a proper test suite.
 - Curating a set of client projects that use dependencies with privileges, and experiment with them.

## Reports

An example report is given below.

```json
{
  "org.example:hello-world:1.0-SNAPSHOT" : [ {
    "detectorCategory" : "FileWrite",
    "events" : [ {
      "type" : "fileWrite",
      "method" : "sayHello",
      "className" : "HelloWorld.java",
      "calledBy" : [ {
        "dependency" : "org.example:hello-world:1.0-SNAPSHOT",
        "methods" : [ "greet" ]
      } ],
      "filePath" : "/theo/hello-world/output.txt"
    } ]
  }, {
    "detectorCategory" : "ProcessStart",
    "events" : [ {
      "type" : "processStart",
      "method" : "sayHello",
      "className" : "HelloWorld.java",
      "calledBy" : [ {
        "dependency" : "org.example:hello-world:1.0-SNAPSHOT",
        "methods" : [ "greet" ]
      } ],
      "directory" : null,
      "command" : "ls"
    } ]
  } ]
}
```
This report contains two access categories (privileges) used by the dependency `org.example:hello-world:1.0-SNAPSHOT`. 
Each `event` represents an instance where the given privilege is invoked. The `method` and `className` fields refer to the 
specific method and class of the dependency that directly accessed the privilege. The `calledBy` field indicates other 
methods from third-party libraries that invoked the corresponding method.

## Miscellaneous

- Theo stands for *Third Eye Open*. 
- *[Third Eye Blind](https://www.youtube.com/channel/UCHdCnspLnD7bgi_U6E44W6g)* is an American rock band.
- In Hinduism and Buddhism, the *[third eye](https://en.wikipedia.org/wiki/Third_eye)* symbolises the power of knowledge, the detection of evil, and consciousness. 

<!-- references -->

[ci-shield]: https://github.com/chains-project/theo/workflows/test.yml/badge.svg?branch=master
[ci-link]: https://github.com/chains-project/theo/actions
