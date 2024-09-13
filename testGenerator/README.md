# Theo test-generator

The Theo Test Generator analyzes the source code of your project to identify reachable methods (APIs) originating from 
third-party libraries. It generates test cases that invoke these methods through public methods in your code. 
These test cases are not designed to validate functionality, as they do not include assertions. Instead, their primary 
purpose is to ensure all reachable third-party methods are invoked, allowing the monitor to capture access privileges effectively.

## How it works 

The analysis and the test generation is done using the [Spoon](https://github.com/INRIA/spoon) library. It also uses the
[java-faker ](https://github.com/DiUS/java-faker) library to generate random primitive values and Strings as arguments for the 
generated method calls. 
When executed, a report including the reachable methods in third party libraries, and the client methods that invoke them 
will be saved in the `./methods-n-invocations-<project-name>.json` file. The generated test cases will be saved in 
`output/generated` folder.

## Usage

1. Generate an executable jar.
   ```
   mvn clean install
   ``` 
   This generates target/testGenerator-version-jar-with-dependencies.jar

2. Run the test-generator.
   ```
   java -jar <path_to_the_jar_with_dependencies> -p <path_to_the_project> -r <root_name_of_the_project>
   ```
   The `root_name` is optional. If you have a multi-module project you can define it, so that it can be used to skip the
   methods coming from other sub-modules.

## Work in progress

 - Add tests

## Limitations

 - Only supports primitive types, using mocks for non-primitive objects.
 - Does not work with classes that have private constructors.

## Related projects

 - https://github.com/ASSERT-KTH/pankti
 - https://github.com/ASSERT-KTH/proze
