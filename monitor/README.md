# Theo-monitor

Monitor can record the execution of a given project either online or offline. The offline version uses a prerecorded 
JFR recording file, whereas the online version uses the temporary data recorded and saved inside the `/tmp` folder. The
offline version is more suited to generate a report based on the test execution, and the online version to generate a 
report based on the field executions.

## How it works

Monitor uses the [Java Flight Recorder (JFR)](https://openjdk.org/jeps/328) to capture runtime data. It can record 10 types
of accesses or capabilities (to be refined).

 - ClassLoad
 - Deserialization
 - FileForce
 - FileRead
 - FileWrite
 - Native Library Access
 - Network Access
 - Process Start
 - Socket Read
 - Socket Write

When one of these events is observed by the JFR, the monitor will get its stack trace and check if it has been called by a 
third party library. If so, it will record it. 

## Usage

Executing the preprocessor first is mandatory for the Monitor.

To run the offline version,

1. Package the monitor.
   ```
   mvn clean package
   ```
2. Run the test suite of the target project using the following command.

   ```
   mvn test -DargLine=\"-XX:StartFlightRecording=name=<name>,settings=<path_to_settings.jfc>,filename=<path_to_save_the_JFR_recording>\"
   ```
   Alternatively, set an environment variable and run the tests.
   ```
   export MAVEN_OPTS=\"-XX:StartFlightRecording=name=name,settings=<path_to_settings.jfc>,filename=<path_to_save_the_JFR_recording>\"
   mvn test
   unset MAVEN_OPTS
   ```
   The `name` can be any string. The default `settings.jfc` file can be found in the root folder of Theo. The `filename`
   is the path where you want to save the JFR report.

3. Execute the Monitor.
   ```
   mvn exec:java -Dexec.args="track-offline -j <path_to_jfr_recording> -l <path_to_lockfile> -r <report_path>"
   ```
   The `report_path` is optional. The default value is `test-report.json`.

To run the online version,

1. Package the monitor.
   ```
   mvn clean package
   ```
2. Execute the Monitor.
   ```
   mvn exec:java -Dexec.args="track-online -j <repository_path> -l <path_to_lockfile> -r <report_path>"
   ```
   The `<repository_path>` and the `report_path` are optional. The default values are `/tmp` and `prod-report.json` respectively.
   However, if you are on a Windows machine, the default value of the `repository_path` will not work. Therefore, it is 
   mandatory to define a custom path.
3. Run the target project with a workload using the following command.
   ```
   java -XX:StartFlightRecording=name=<name>,settings=<path_to_the_settings.jfc_file>,filename=<path_to_save_the_JFR_recording> -XX:FlightRecorderOptions=repository=tmp -jar <path_to_the_jar>
   ```
   The `name` can be any string. The default `settings.jfc` file can be found in the root folder of Theo. The `filename`
   is the path where you want to save the JFR report. The `repository` should be the same value as what you used previously 
   for the `<repository_path>`. 

## Work in progress

 - Refine the access categories and their sub-fields.
 - Add more comprehensive tests.

## Limitations

 - Has to use mvn execute command (instead of an executable jar) as we need the dependency information of the loaded 
   classes. Potentially, we can overcome this by using the classport.
