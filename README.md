# Exodus 1.0
Exodus is a Java based solution to Migrate MySQL 5.x databases to MariaDB. With a minimum configuration this tool can migrate the entire database including, user accounts, stored procedures, triggers, views, tables etc. With just one execution. 

Demo / Tutorial: https://youtu.be/iuG8xhCUVEw

#### Database Setup
Download the entire bin folder as a ZIP using the "CLONE" function. Once downloaded edit /bin/dbdetails.xml with the connection details for both source and target databases. 

Make sure the user used to connect to the Target (MariaDB) database uses a user with `ALL` and `WITH GRANT OPTION` privileges as it will try to create database and users based on the source (MySQL)

Here is an extract from my own setup for the migration user on MariaDB.
```
MariaDB [(none)]> show grants for migration;
+-------------------------------------------------------------------------------------------------------------------------------------+
| Grants for migration@%                                                                                                              |
+-------------------------------------------------------------------------------------------------------------------------------------+
| GRANT ALL PRIVILEGES ON *.* TO 'migration'@'%' IDENTIFIED BY PASSWORD '*9677B3E0EA32E863BCE766756E363CF03A6C7E4C' WITH GRANT OPTION |
+-------------------------------------------------------------------------------------------------------------------------------------+
1 row in set (0.000 sec)

MariaDB [(none)]>
```

This user will be a temporary one, once migration is done, this user can be removed once no longer needed.

For the source user, we don't need `WITH GRANT OPTION` but `ALL ON *.*` is needed as it needs to read the schema and structure from all the tables and databases on the source.

Following is my setup for the migraiton user on MySQL database;
```
mysql> show grants for migration;
+------------------------------------------------+
| Grants for migration@%                         |
+------------------------------------------------+
| GRANT ALL PRIVILEGES ON *.* TO 'migration'@'%' |
+------------------------------------------------+
1 row in set (0.00 sec)

mysql>
```

#### Exodus Configuration

Edit the /bin/Exodus.properties file and modify the following section as needed. Start with 1 `ThreadCount` Change the `LogPath`, `DDLPath` and `ExportPath` according to the setup, the folders are already there in the donloaded ZIP file, just need to edit the paths to specify complete path from `/` for Linux and `C:\\` for Windows. 

- Take note, we have to user doubble back slash `\\` for paths in Windows

```
# ThreadCount is for parallel Table loading, each thread will take care of ONE table.
ThreadCount=1

TargetDB=MariaDB
TargetConnectParams=useUnicode=yes&characterEncoding=utf8&rewriteBatchedStatements=true
#useBatchMultiSend=true&useServerPrepStmts=false&

#Paths with reference to the current folder. Do not use "/" at the end of the path
LogPath=/home/faisal/Work/Java/Exodus/src/logs
DDLPath=/home/faisal/Work/Java/Exodus/src/ddl
ExportPath=/home/faisal/Work/Java/Exodus/src/export

##Path for Windows
#LogPath=C:\\Users\\faisa\\OneDrive\\Work\\Java\\Exodus\\src\\logs
#DDLPath=C:\\Users\\faisa\\OneDrive\\Work\\Java\\Exodus\\src\\ddl
#ExportPath=C:\\Users\\faisa\\OneDrive\\Work\\Java\\Exodus\\src\\export

#WHERE Clause Additional Criteria, following is an Example
SCHEMANAME.TABLENAME.WHERECriteria = COL1 = 74196328 AND COL2 LIKE 'SOMETHING%'

#Additional Criteria like LIMIT or ORDER BY etc., following is an Example
SCHEMANAME.TABLEname.AdditionalCriteria = LIMIT 13

#Just Scans through the source database and starget database and validates of any problems. (YES/NO)
DryRun=NO

#This is only applicable for data migration, if set to YES, on a batch failure, the same batch will be re-tried in Single ROW mode (YES/NO)
RetryOnErrors=YES

#This will truncate and overwrite all the tables weather fully or partially migrated, migration can be re-run and it will overwrite all the tables every time
OverWriteTables=NO

#This will truncate the tables that were partially migrated previously, With this property enabled, migration can be re-run and it will continue from the last table (YES/NO)
OverwritePartiallyMigratedTables=YES

#This dictates if Table structure and data migration will be done, use NO to skip (YES/NO)
MigrateData=YES

#Create (YES/NO) for Additional Objects while Migrating (YES/NO)
CreateViews=YES
CreatePLSQL=YES
CreateTriggers=YES

#This dictates of user Grants will be migrated or not, use NO to skip Grants migration (YES/NO)
UserGrants=YES

#TransactionSize is the size of the Batch, COMMIT will be executed after for each batch size.
TransactionSize=5000

#The following Names should always be there, any additional tables, just add on to the list or use "AND additional expression"
SkipTableMigration=TABLE_NAME IN ('MigrationLog', 'MigrationLogDETAIL')
```

For first time, change the `DryRun=NO` to `YES` so that we can be sure of our setup.

The TargetConnectParams has now `rewriteBatchedStatements=true` this will rewrite bulk statements automatically for much faster writes!

`WHERECriteria` and `AdditionalCriteria` have been added to take care of extra WHERE clause for individual tables and additional expression like `ORDER BY` or `LIMIT n`

Other important paramneters

- `DryRun`
  - When enabled, this will force Exodus to just do tables and object scans without changing anything on the target database. Advisable to run once with DryRun=YES to ensure that connectivity works and the tables/objects can be read properly.
- `RetryOnErrors`
  - Since the commit is done on batches which is decided by `TransactionSize` property, in case of any data errors the entire batch is rolled back, if `RetryOnErrors=YES`, on hitting error while executing a batch, the batch mode will be disabled and the failed batch will be re-tried in single row mode. This will cause only the failed rows to skip while the rest of the data will migrate successfully. 
- `OverWriteTables`
  - If this flag is set to `YES`, all the tables will be TRUNCATED before writing data from source. This is good when doing reverse migration as a fallback plan or just want to re-migrate everything.
- `OverwritePartiallyMigratedTables`
  - In case the migration was cancelled while a table was being migrated, upon re-running the Exodus, the already completed tables will be skipped however the partially done table will get a "TRUNCATE" call and migration will start from row one for it. This makes the re-running the batch easier and simple.
- `UsersToMigrate`
  - This is a SQL compatible syntax that defines which DB users are to be migrated
- `DatabaseToMigrate`
  - List of databases *Case Sensitive* to be migrated, again this is compatible with SQL syntax
- `TablesToMigrate`
  - Is ths list of tables that we want to migrate, this is also case sensitive if you want to migrate a specific list of tables, normally for all tables just use the following to migrate all the tables
    - `TablesToMigrate = TABLE_NAME LIKE '%'`
- `SkipTableMigration`
  - This defines the list of tables that you want to skip from the Migration process, this only useful if you have specify "%" for `TablesToMigrate`
- `CreateViews`
  - YES/NO will decide if Views will be migrated or not.
- `CreatePLSQL`
  - YES/NO will decide if PL/SQL (Stored Procedures / Stored Functions) will be migrated or not.
- `CreateTriggers`
  - YES/NO will decide if Triggers will be migrated or not.
- `UserGrants`
  - YES/NO will decide if User Grants will be migrated or not.
- `MigrateData`
  - YES/NO will decide if Table's DDL and Data will be migrated or not.
- `SkipTableMigration`
  - This currently hold `MigraitonLog` and `MigrationLogDetail` values. This is useful for reverse migration because we don't want to migrate the Migration log that exists in the source in the case of a fallback migration for instance `MariaDB -> MySQL` 
  - Any additional tables to be skipped should just be added on to the criteria list

*One important thing to take note is, don't select MySQL internal tables and databases for migration for instance `mysql`, `sys` etc.!*

#### Exodus Database Connections

Edit the `Exodus/bin/dbdetails.xml` and edit the UserName, Password, HostName, PortNumber and a default DataBaseName for both MySQL and MariaDB.

DefaultDatabase can be set to `mysql` this is just the first database Exodus will connect. Since all the databases will be created by Exodus, one don't have to create the target databases in advance.

```
<DataSources>
	<DataProvider type="MySQL">
		<UserName>migration</UserName>
		<Password>secretpassword</Password>
		<HostName>192.168.56.158</HostName>
		<PortNumber>3306</PortNumber>
		<DatabaseName>mysql</DatabaseName>
	</DataProvider>
	<DataProvider type="MariaDB">
		<UserName>secretpassword</UserName>
		<Password>password</Password>
		<HostName>192.168.56.80</HostName>
		<PortNumber>4006</PortNumber>
		<DatabaseName>mysql</DatabaseName>
	</DataProvider>
</DataSources>
```

#### Exodus Execution

You will need Java JRE 8 or higher to run this, I am using Java 8.x 

```
C:\> java -version
java version "1.8.0_191"
```

- For Windows, edit the `WindowsExec.cmd` script and modify the CLASSPATH to point to your `bin\resources` folder depending on where you extracted the ZIP file.

- For Windows, edit the `exec` script and modify the CLASSPATH to point to your `bin/resources` folder depending on where you extracted the ZIP file.

*remember to keep the first dot `.` in the CLASSPATH as it needs to points back to your current path.*

Make sure `java -version` works for your session and then execute either of the above two scripts depending on your environment.

This script can run from a third machine which has access to both MySQL and MariaDB databases.
