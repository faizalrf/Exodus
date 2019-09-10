@echo off
cls
set CLASSPATH=.;C:\Users\faisa\OneDrive\Work\Java\Exodus\bin\resources\mariadb-java-client-2.4.1.jar

mkdir resources>nul 2>nul
mkdir ddl>nul 2>nul
mkdir logs>nul 2>nul
mkdir export>nul 2>nul

copy *.jar resources>nul 2>nul
copy *.xml resources>nul 2>nul
copy *.properties resources>nul 2>nul

@echo on
java -Xms6196m -Xmx10240m mariadb.migration.Exodus
