read -p "Enter the file path to the JFR recording : " jfr
read -p "Enter the lockfile path(s): " lockfile
read -p "Enter the report file path: " testReport
mvn exec:java -Dexec.args="track-offline -j $jfr -l $lockfile -r $testReport"
