#!/bin/bash
read -p "Do you want to monitor the execution of the test cases? (y/n)
Enter 'n' if you want to monitor the execution of a workload. : " yn
cd preprocessor || exit
mvn clean package
read -p "Generate a maven-lockfile for your project using the following command.
  mvn io.github.chains-project:maven-lockfile:generate
  Press any key to continue." -n1 -s
read -p $'\n'"Enter the pom file path(s): " pomfile
read -p $'\n'"Enter the lockfile path(s): " lockfile
mvn exec:java -Dexec.args="-p $pomfile -l $lockfile"
cd ../monitor || exit
mvn clean package
case $yn in
  [yY] )
    read -p "Run the test suite using the following command.
      mvn test -DargLine=\"-XX:StartFlightRecording=name=<name>,settings=settings.jfc,filename=<path_to_save_the_JFR_recording>\"
      Alternatively, set an environment variable and run the tests.
      export MAVEN_OPTS=\"-XX:StartFlightRecording=name=name,settings=settings.jfc,filename=<path_to_save_the_JFR_recording>\"
      mvn test
      unset MAVEN_OPTS
      Press any key to continue once the test execution is completed." -n1 -s
    read -p $'\n'"Enter the file path to the JFR recording : " jfr
    read -p "Enter a file path to save the report: " testReport
    mvn exec:java -Dexec.args="track-offline -j $jfr -l $lockfile -r $testReport"
    ;;
  [nN] )
    read -p "Enter the file path to the JFR recording : " jfr
    read -p "Enter the report file path: " testReport
    mvn exec:java -Dexec.args="track-offline -j $jfr -l $lockfile -r $testReport"
    read -p "Copy the path to the 'settings.jfc' file. (You can find it under the root folder of Theo).
      Press any key to continue." -n1 -s
    read -p $'\n'"Run the application that needs to be monitored with a workload now, using the following command.
      java -XX:StartFlightRecording=name=<name>,settings=<path_to_the_settings.jfc_file>,filename=output.jfr -XX:FlightRecorderOptions=repository=tmp -jar <path_to_the_jar>
      Press any key to continue." -n1 -s
    ;;
esac
cat org.xml > pom.xml
cd ../
cat org.xml > pom.xml
