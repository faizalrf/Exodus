set CLASSPATH=.;C:\Users\faisa\OneDrive\Work\Java\Exodus\bin\resources\mariadb-java-client-2.2.7.jar;C:\Users\faisa\OneDrive\Work\Java\Exodus\bin\resources\mysql-connector-java-8.0.12.jar

mkdir resources

   copy *.jar resources
   copy *.xml resources
   copy *.properties resources

java -Xms4196m -Xmx10240m mariadb.migration.Exodus
