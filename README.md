[![Build Status][ci-shield]][ci-link]
[![Coverage Status][coverage-shield]][ci-link]

# Theo
Theo is a tool to monitor access privileges originating from third-party dependencies.
It extracts runtime information by running the test suite and/or running a workload. Then it maps the resource accesses 
to dependencies. 

## How it works

1. Theo consists of a preprocessor, a shader and a monitor. 
2. During the preprocessing stage, the [maven-lockfile](https://github.com/chains-project/maven-lockfile) is used to generate a lockfile for the project. The preprocessor also adds the project dependencies to the classpath of the monitor by temporarily adding them to the pom file.
3. The shader shades the compile scoped dependencies in the monitor, so that there will not be any conflicts with the classes loaded from temporarily added dependencies.
4. The monitoring part of the running program is done using the [Java Flight Recorder (JFR)](https://openjdk.org/jeps/328). Then the dependency information in the lockfile will be extended with access privilege information collected by the JFR.  

## Usage

To run the tool, execute the `run_workflow.sh`.

Here's a breakdown of what it does.

- Packages the preprocessor
- Generates a lockfile for the project under consideration
- Adds the dependencies of the project to the classpath of the monitor
- Packages the monitor
- Runs the test cases or the workload depending on the use case with JFR attached
- Runs the monitor and generates the reports
- Resets the pom files

## Reports

## Miscellaneous

- Theo stands for *Third Eye Open*. 
- *[Third Eye Blind](https://www.youtube.com/channel/UCHdCnspLnD7bgi_U6E44W6g)* is an American rock band.
- In Hinduism and Buddhism, the *[third eye](https://en.wikipedia.org/wiki/Third_eye)* symbolises the power of knowledge, the detection of evil, and consciousness. 

<!-- references -->

[ci-shield]: https://github.com/chains-project/theo/workflows/CI/badge.svg?branch=master
[ci-link]: https://github.com/chains-project/theo/actions
[coverage-shield]: https://github.com/chains-project/theo/blob/master/.github/badges/jacoco.svg