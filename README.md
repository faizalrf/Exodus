# Data Migration to MariaDB 10.3
### Data migration plan
A database migration plan from one to another involves the followng 
- Analysis of the Source Database
- Listing down all the objects in the source Database
- Preparing the target database
    - Creating the necessary schemas
    - Creating the necessary user accounts
- Exporting the data and all related objects from the source DB
    - Table DDL
    - Table Data (Using mysqldump as a flat text delimited file)
    - Stored Procedures / Functions
    - Views
    - Triggers on Tables
- Importing the exported objects on the Target DB
    - mysqlimport can be used to import multiple data files in parallel for high performance data loading.
- Validating the target DB against Source DB to ensure the number of records and the list of objects matches.
- Sample checking of the data manually to ensure the data was migrated correctly without any corruption
- Application level regression testing to ensure after database migration, the application code remains uneffected.
    - If any issues, applicaiton code modification accordingly.
    - Repeat regression testing untill successful.
- After Migration completed setup replication between the new Primary Database and the legacy database.
    - This is done as a fallback plan, during the parallel run period, applicaiton will connect to the new database, replicaiton will ensure the legacy database is also kept updated.
    - In case of a serious problem that requires a fallback to the old database, the old DB is always up to date and ready for a fallback any time.
- Finally once the parallel run period is completed successfully, the replicaiton between the new DB and the legacy DB is stopped and the DB us shutdown permanently.

In this article, we will review in detail on how to easily migrate database from MySQL (5.5, 5.6 & 5.7) to MariaDB 10.3. 

For production migration, we will look at a fallback plan that will alow us to set-up data replication between MariaDB 10.3 as a Master and MySQL as a Slave. MariaDB MAxScale 2.2 will help us achieve this with ease.

## 1. Migrating from MySQL 5.5 & 5.6 
Migrating from MySQL 5.5 and 5.7 is fairly simple. MariaDB 10.3 is literally a drop-in replacement. Later in thus section we will take a look at how easy this actually is.

### 1.1 Migation Setp
1. Review the server.cnf for MySQL database
2. Take note of all the parameters related to filesystem paths for example:
    ```
    [mysqld]
    datadir=/mysql/data
    log_error=/mysql/logs/mariadb.err
    tmpdir=/mysql/tmp
    innodb_log_group_home_dir=/mysql/redo_logs
    socket=/tmp/mysql.sock
    ```  
3. Install New MariaDB Server using a `tarball` with a distinct configuration so that these dont conflict with the MySQL installation. Take note of the `port` and `socket` these two should be unique compared to MySQL
    ```
    [mysqld]
    datadir=/mariadb/data
    log_error=/mariadb/logs/mariadb.err
    tmpdir=/mariadb/tmp
    innodb_log_group_home_dir=/mariadb/redo_logs
    socket=/tmp/mariadb.sock
    ```
4. Stop the MySQL and MariaDB server
5. Copy the `datadir` and all other folders from MySQL path into MariaDB specific paths
6. Start MariaDB server and login to the Database using the same UserID that exist in MysQL. Since we hage copied all the folders from MySQL to MariaDB, all the user accounts have also been copied.

### 1.2 Test Run

Login to MySQL 5.5 and take a snapshot of the databases and tables and created users that exists in MySQL.
```
Server version: 5.5.59 MySQL Community Server (GPL)

Copyright (c) 2000, 2018, Oracle and/or its affiliates. All rights reserved.

Oracle is a registered trademark of Oracle Corporation and/or its
affiliates. Other names may be trademarks of their respective
owners.

Type 'help;' or '\h' for help. Type '\c' to clear the current input statement.

mysql> show databases;
+--------------------+
| Database           |
+--------------------+
| information_schema |
| WORLD              |
| employees          |
| mysql              |
| performance_schema |
| test               |
+--------------------+
6 rows in set (0.00 sec)

mysql> show global variables like '%datadir%';
+---------------+----------------------------+
| Variable_name | Value                      |
+---------------+----------------------------+
| datadir       | /mysql-test/mysql5.5/data/ |
+---------------+----------------------------+
1 row in set (0.01 sec)

mysql> select host, user, password from mysql.user;
+----------------+-----------+-------------------------------------------+
| host           | user      | password                                  |
+----------------+-----------+-------------------------------------------+
| localhost      | root      |                                           |
| mysql-test-150 | root      |                                           |
| 127.0.0.1      | root      |                                           |
| ::1            | root      |                                           |
| localhost      |           |                                           |
| mysql-test-150 |           |                                           |
| %              | test_user | *9F69E47E519D9CA02116BF5796684F7D0D45F8FA |
+----------------+-----------+-------------------------------------------+
7 rows in set (0.00 sec)

mysql> select version();
+-----------+
| version() |
+-----------+
| 5.5.59    |
+-----------+
1 row in set (0.00 sec)
```

We can see the path for the `datadir` for mysql installation. Take note of it and proceed to install MariaDB 10.3 server using `tarball`

After instalation, login to MariaDB and verify the `datadir` parameter. This path is not impoartant as of now, we can always change it to point to the directory we want. 

Login to MariaDB 10.3 and verify the databases, users and it's version. Still the databases / users are not available from mysql. 
```
Server version: 10.3.8-MariaDB MariaDB Server

Copyright (c) 2000, 2018, Oracle, MariaDB Corporation Ab and others.

Type 'help;' or '\h' for help. Type '\c' to clear the current input statement.

MariaDB [(none)]> show databases;
+--------------------+
| Database           |
+--------------------+
| information_schema |
| mysql              |
| performance_schema |
| test               |
+--------------------+
4 rows in set (0.001 sec)

MariaDB [(none)]> select host, user, password from mysql.user;
+----------------+-----------+-------------------------------------------+
| host           | user      | password                                  |
+----------------+-----------+-------------------------------------------+
| localhost      | root      |                                           |
| mysql-test-150 | root      |                                           |
| 127.0.0.1      | root      |                                           |
| ::1            | root      |                                           |
| localhost      |           |                                           |
| mysql-test-150 |           |                                           |
+----------------+-----------+-------------------------------------------+
6 rows in set (0.001 sec)

MariaDB [(none)]> select version();
+----------------+
| version()      |
+----------------+
| 10.3.8-MariaDB |
+----------------+
1 row in set (0.000 sec)

```

Stop MariaDB process, copy the the mysql `datadir` to another location that we want to dedicate for MariaDB 10.3. Create all the required dedicated folders that existed for MySQL for instance, log, redo log etc.

Once copied, we will need to remove the old `PID` file that exists under the data folder of the mysql folders that we just copied.

We will create `/mariadb/mysql5.5` for this specific exercise.

```
[mysql@mysql-test-150 ~] mkdir -p /mariadb/mysql5.5
[mysql@mysql-test-150 ~] cd /mariadb/mysql5.5
[mysql@mysql-test-150 mysql5.5]$ pwd
/mariadb/mysql5.5
[mysql@mysql-test-150 mysql5.5]$ cp -rp /mysql-test/mysql5.5/data/ .

[mysql@mysql-test-150 data]$ pwd
/mariadb/mysql5.5/data
[mysql@mysql-test-150 data]$ ls -lrt
total 233504
drwxrwxr-x 2 mysql mysql        20 Aug 17 13:14 test
drwx------ 2 mysql mysql      4096 Aug 17 13:14 performance_schema
drwx------ 2 mysql mysql      4096 Aug 17 13:14 mysql
drwx------ 2 mysql mysql       206 Aug 17 13:17 WORLD
drwx------ 2 mysql mysql       146 Aug 17 13:32 employees
-rw-rw---- 1 mysql mysql   5242880 Aug 17 13:33 ib_logfile1
-rw-rw---- 1 mysql mysql         5 Sep  2 10:49 mysql-test-150.pid
-rw-r----- 1 mysql mysql     18885 Sep  2 10:49 mysql-test-150.err
-rw-rw---- 1 mysql mysql 228589568 Sep  2 10:50 ibdata1
-rw-rw---- 1 mysql mysql   5242880 Sep  2 10:50 ib_logfile0
[mysql@mysql-test-150 data]$ 
[mysql@mysql-test-150 data]$ rm mysql-test-150.pid
[mysql@mysql-test-150 data]$ 
```

Now that the we have copied the mysql 5.5's `datadir` and all other related folder to a different set of folders we can change the `datadir` and ther parameters in the server.cnf and  to point to new locations. Remember to remove the `pid` file in the previous step before starting MariaDB.

Following is an example of what I changed in my server.cnf, I commented out the old parameters and pointed to the new locations.
```
[mysqld]
#datadir=/mariadb/mariadb.5008/data
datadir=/mariadb/mysql5.5/data
#log_error=/mariadb/mariadb.5008/logs/mariadb.err
log_error=/mariadb/mysql5.5/logs/mariadb.err

tmpdir=/mariadb/mariadb.5008/tmp

#innodb_log_group_home_dir=/mariadb/mariadb.5008/redo_logs
innodb_log_group_home_dir=/mariadb/mysql5.5/redo_logs
```

Once confirmed, we can now start MariaDB.

Execute `mysql_upgrade` to complete the process before logging in to MariaDB. Take note of the complete path that we have used for the `mysql_upgrade` command that points to the MariaDB binbaries. We don't want ot mistakenly execute any command from MySQL binaries.

```
[mysql@mysql-test-150 bin]$ /mariadb/mariadb.5008/bin/mysql_upgrade -uroot -p -P5008 -S/tmp/mariadb.5008.sock
Enter password: 
MySQL upgrade detected
Phase 1/7: Checking and upgrading mysql database
Processing databases
mysql
mysql.columns_priv                                 OK
mysql.db                                           OK
mysql.event                                        OK
mysql.func                                         OK
mysql.help_category                                OK
mysql.help_keyword                                 OK
mysql.help_relation                                OK
mysql.help_topic                                   OK
mysql.host                                         OK
mysql.ndb_binlog_index                             OK
mysql.plugin                                       OK
mysql.proc                                         OK
mysql.procs_priv                                   OK
mysql.proxies_priv                                 OK
mysql.servers                                      OK
mysql.tables_priv                                  OK
mysql.time_zone                                    OK
mysql.time_zone_leap_second                        OK
mysql.time_zone_name                               OK
mysql.time_zone_transition                         OK
mysql.time_zone_transition_type                    OK
mysql.user                                         OK
Upgrading from a version before MariaDB-10.1
Phase 2/7: Installing used storage engines
Checking for tables with unknown storage engine
Phase 3/7: Fixing views from mysql
Phase 4/7: Running 'mysql_fix_privilege_tables'
Phase 5/7: Fixing table and database names
Phase 6/7: Checking and upgrading tables
Processing databases
WORLD
WORLD.City                                         OK
WORLD.Country                                      OK
WORLD.CountryLanguage                              OK
employees
employees.departments                              OK
employees.dept_emp                                 OK
employees.dept_manager                             OK
employees.employees                                OK
employees.salaries                                 OK
employees.titles                                   OK
information_schema
performance_schema
test
Phase 7/7: Running 'FLUSH PRIVILEGES'
OK
[mysql@mysql-test-150 bin]$ 
```

Login to MariaDB and vefify the databaes objets from MySQL now exists in MariaDB data dictionary.

```
Server version: 10.3.8-MariaDB MariaDB Server

Copyright (c) 2000, 2018, Oracle, MariaDB Corporation Ab and others.

Type 'help;' or '\h' for help. Type '\c' to clear the current input statement.

MariaDB [(none)]> show databases;
+--------------------+
| Database           |
+--------------------+
| WORLD              |
| employees          |
| information_schema |
| mysql              |
| performance_schema |
| test               |
+--------------------+
6 rows in set (0.001 sec)

MariaDB [(none)]> select host, user, password from mysql.user;
+----------------+-----------+-------------------------------------------+
| host           | user      | password                                  |
+----------------+-----------+-------------------------------------------+
| localhost      | root      |                                           |
| mysql-test-150 | root      |                                           |
| 127.0.0.1      | root      |                                           |
| ::1            | root      |                                           |
| localhost      |           |                                           |
| mysql-test-150 |           |                                           |
| %              | test_user | *9F69E47E519D9CA02116BF5796684F7D0D45F8FA |
+----------------+-----------+-------------------------------------------+
7 rows in set (0.001 sec)

MariaDB [(none)]> select version();
+----------------+
| version()      |
+----------------+
| 10.3.8-MariaDB |
+----------------+
1 row in set (0.000 sec)

MariaDB [(none)]> 
```
This concludes the Migration for MySQL 5.5, 5.6 to MariaDB 10.3

#### 1.2.1 Alternate Migration Approach

Use `mysqldump` to export out all the databases and it's related objects

```
####
# This will export a create database script without any additional objects
####
[root@mysql-test-150 test]# mysqldump --no-create-info --no-data  --skip-triggers --skip-opt --all-databases > create_databases.sql

####
# This Will Export out all the source code from the Database into db_source.sql file
####
[root@mysql-test-150 test]# mysqldump --routines --triggers --no-create-info --no-data --no-create-db --skip-opt --all-databases > db_source.sql

####
# This Will Export out all table structure into db_tables.sql
####
[root@mysql-test-150 test]# mysqldump --skip-triggers --no-data --no-create-db --skip-opt --all-databases > db_tables.sql
```

Afte the above files have been exported successfully, we will need to do some alteration with the help of the following `sed` commands on the `db_tables.sql` file. This will make the CREATE TABLE commands compatible with MariaDB.

```
[root@mariadb-250 test]# sed -i -e 's/GENERATED ALWAYS AS/AS/g' db_tables.sql
[root@mariadb-250 test]# sed -i -e ’s/VIRTUAL NULL/VIRTUAL/g’ db_tables.sql
[root@mariadb-250 test]# sed -i -e ’s/STORED NOT NULL/PERSISTENT/g’ db_tables.sql
[root@mariadb-250 test]# sed -i -e ’s/JSON/LONGBLOB/g’ db_tables.sql
```

Final stage at the source is to export the source tables's data as a FLAT text file. The script will ensure that the data is not broken in any way, if we use `|` as a column delimniter, the script will put excape characters within the exported data to make sure that if `|` exists as a text in any of the columns, it is properly escaped so that the loading system can split columns poperly.

Edit `my.cnf` for MySQL and disable **`secure-file-priv`** parameter, else we wont be able to export data into files.

```
[mysqld]
secure-file-priv = ""
```

Add the following text in a script file and grant it 750 permissions so that its executable. This script will output all the tables in all the databases into a `.txt` file. The `DestinationFolder` variable must be edited to the desired location.

```
#!/bin/bash

UserName=$1
Password=$2
#ConnString="-u${UserName} -p${Password} -S/tmp/mariadb.sock"
ConnString="-u${UserName}"
TableList="SELECT CONCAT(table_schema,'.',table_name) FROM information_schema.tables WHERE table_schema NOT IN ('information_schema','performance_schema','mysql', 'sys')"
mysql ${ConnString} -ANe"${TableList}" > /tmp/Tables.lst
mkdir -p $(pwd)/out
echo "" > $(pwd)/export.scr
for Tables in `cat /tmp/Tables.lst`
do
    DBName=`echo "${Tables}" | sed 's/\./ /g' | awk '{print $1}'`
    TableName=`echo "${Tables}" | sed 's/\./ /g' | awk '{print $2}'`
    DestinationFolder=$(pwd)/out/${DBName}
    mkdir -p -m 777 ${DestinationFolder}

    echo "echo \"mysqldump ${ConnString} -T${DestinationFolder} --fields-terminated-by=\"|\" --fields-escaped-by=\"^\" --lines-terminated-by=\"\\r\\n\" ${DBName} ${TableName}\""
    mysqldump ${ConnString} -T${DestinationFolder} --fields-terminated-by="|" --fields-escaped-by="^" --lines-terminated-by="\r\n" ${DBName} ${TableName}
    echo "mysqldump ${ConnString} -T${DestinationFolder} --fields-terminated-by=\"|\" --fields-escaped-by=\"^\" --lines-terminated-by=\"\\r\\n\" ${DBName} ${TableName}" >> $(pwd)/export.scr
done
sh $(pwd)/export.scr
```

On the target databse run the import command, this will create all the databases and its related tables without any source code (triggers, procedures etc.)

```
[root@mariadb-250 test]# mysql -uroot -ppassword < create_databases.sql
[root@mariadb-250 test]# mysql -uroot -ppassword < db_tables.sql
```

Once tese are done, we can finally import the data files exported with the help of the above mentioned shell script. The following example uses 16 threads, that means we could  load 16 tables in parallel. A smart shell script can be written to take care of this automatically.

```
mysqlimport -uroot -ppassword -S /tmp/mariadb.sock --use-threads=16 --fields-terminated-by="|" --fields-escaped-by="^" --local testdb ${INPUT_FILE 1} ${IMPORT_FILE 2} ${IMPORT_FILE 3} .... ${IMPORT_FILE 16}
```

Finally import the source code that includes the triggers as well.

```
[root@mariadb-250 test]# mysql -uroot -ppassword < db_source.sql
```






mysqldump -uroot mysql user --no-create-info --where="user = 'migration'" > rootuser.sql