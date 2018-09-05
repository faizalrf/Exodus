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

Copy the the above datadir to another location that we want to dedicate for MariaDB 10.3. Create all the required dedicated folders that existed for MySQL for instance, log, redo log etc.

Once copied, we will need to remove the old `PID` file that exists under teh data folder.

```
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

We can see the path for the datadir. Take note of it and proceed to install MariaDB 10.3 server using `tarball`

After instalation, login to mysql and verify the `datadir` parameter. This path is not impoartant as of now, we can always change it to point to the directory we want. 

Login to MariaDB 10.3
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

Stop/kill MariaDB process.

Change the `datadir` and ther parameters in the server.cnf and  to point to new locations. Remember to remove the `pid` file in the previous step before starting MariaDB.

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

Execute `mysql_upgrade` to complete the process before logging in to MariaDB.

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

Login to MariaDB and vefify the databaes objets from MySQL now exists in MariaDB 
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
# mysqlimport -uroot -ppassword -S /tmp/mariadb.sock --use-threads=16 --fields-terminated-by="|" --fields-escaped-by="^" --local testdb ${INPUT_FILE1} ${IMPORT_FILE2} ${IMPORT_FILE3} .... ${IMPORT_FILE16}
```

On the target databse run the import command, this will create all the databases without any objects.
```
[root@mariadb-250 test]# mysql -uroot -ppassword < create_databases.sql
```

### 1.3 Setting up Replication Between MariaDB 10.3 & MySQL 5.x
This process is the same for any MySQL 5.x versions. This requires MariaDB MaxSCale to be configured as a binlog router, install MaxScale using RPM or a tarball, edit `maxscale.cnf` file from the default location /etc/maxscale.cnf or from the location specified in case of a tarball install. 

#### 1.3.1 Remove all the contents of the file and add the following.

```
[maxscale]
threads=auto

[binlog-router]
type=service
router=binlogrouter
user=repl_user
passwd=secretpassword
server_id=2000
master_id=1000
binlogdir=/var/lib/maxscale
mariadb10-compatibility=0
version_string=5.5.59-log
filestem=mariadb-bin

[binlog-listener]
type=listener
service=binlog-router
protocol=MySQLClient
port=6603
```

- **[maxscale]** section specifies that number of threads will be auto decided by MaxScale
- **[binlog-router]** section as follows
    - type and router options are indicating that this maxscale will be working as a binlog router service
    - **user & password** are the maxscale user and password that we will use to connec to MariaDB prompt
    - **server_id** is the MaxScale ID, this is used when setting up replication between MaxScale and MySQL
    - **master_id** this is the MariaDB server's ID from server.cnf, in this example we specified MariaDB's server_id as 1000, that is why `master_id` is specified as 1000. Since MariaDB will be the master node,
    - **binlogdir** is where we want to store binary logs that are received from MariaDB server. These logs will be sent to MySQL 5.5 or 5.7 node for replication
    - **mariadb10-compatibility** is given a value `0` which also means `false`. Setting this to false, means that non MariaDB databases can use these `binlogs`, for instance MySQL 5.x in our case.
    -  **version_string** should follow the target database's version number, in our case I am using `MySQL 5.5.59` hence `5.5.59-log` value is specified. While setting up for MySQL 5.7.23 this value will be `5.7.23-log`
    - **filestem** is the name of binlog file names, this should be the same name as specified in the MariaDB's server.cnf under `log_bin` variable
- **[binlog-listener]** is the listener for MariaDB sql clients to connect to. The only purpose of this listener for our use is to connect to MaxScale's query interface and set it up as a slave to MariaDB. And then, for MySQL 5.x to connect to MaxScale as a slave.
    - Data Flow will be as follows `MariaDB Binary Logs -> MaxScale (Relay Logs) -> MySQL (Relay Logs)`
    - **protocol** is defined as MySQLClient, which means that any client using MySQL drivers can connect to MaxScale at port **6603** using MaxScale's `ip` and `user` / `password` as specified in the `[binlog-router]` section.

#### 1.3.2 Configure MariaDB as a Master node

Before we enable binlogs in MariaDB server, we will create a new user that MaxScale will be used to define MaxScale as a Slave to MariaDB. This user/password is also mentioned in the maxscale.cnf

```
[mysql@mysql-test-150 ~]$ /mariadb/mariadb.5008/bin/mysql -h192.168.56.150 -uroot -p -P5008
Enter password: 
Welcome to the MariaDB monitor.  Commands end with ; or \g.
Your MariaDB connection id is 10
Server version: 10.3.8-MariaDB-log MariaDB Server

Copyright (c) 2000, 2018, Oracle, MariaDB Corporation Ab and others.

Type 'help;' or '\h' for help. Type '\c' to clear the current input statement.

MariaDB [(none)]> CREATE USER repl_user@'%' IDENTIFIED BY 'secretpassword';
Query OK, 0 rows affected (0.001 sec)

MariaDB [(none)]> GRANT REPLICATION SLAVE ON *.* TO repl_user@'%';
Query OK, 0 rows affected (0.001 sec)

MariaDB [(none)]> CREATE USER app_user@'%' IDENTIFIED BY 'secretpassword';
Query OK, 0 rows affected (0.001 sec)

MariaDB [(none)]> GRANT SELECT, INSERT, UPDATE, DELETE ON *.* TO app_user@'%' IDENTIFIED BY 'secretpassword';
Query OK, 0 rows affected (0.001 sec)

MariaDB [(none)]> FLUSH PRIVILEGES;
Query OK, 0 rows affected (0.001 sec)
```

Edit MariaDB's `server.cnf` file and enable `binlogs` + specify a `server_id`, the server_id in the following is the same value as what we used in the `master_id` for MaxScale's configuration. 

```
[mysqld]
server_id=1000
log_bin = mariadb-bin
binlog_format = ROW
```

Restart MariaDB and login in to the SQL interface, create a test table and to extract binlog configurations.

```
Your MariaDB connection id is 9
Server version: 10.3.8-MariaDB-log MariaDB Server

Copyright (c) 2000, 2018, Oracle, MariaDB Corporation Ab and others.

Type 'help;' or '\h' for help. Type '\c' to clear the current input statement.

MariaDB [(none)]> 
MariaDB [(none)]> 
MariaDB [(none)]> 
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
6 rows in set (0.003 sec)

MariaDB [(none)]> use employees;
Database changed

MariaDB [employees]> show tables;
+---------------------+
| Tables_in_employees |
+---------------------+
| departments         |
| dept_emp            |
| dept_manager        |
| employees           |
| salaries            |
| titles              |
+---------------------+
6 rows in set (0.001 sec)
```

We have created a new table and inserted 3 records. Lets check if the binlogs are generated in the MariaDB's `datadir` location?

```
[mysql@mysql-test-150 data]$ pwd
/mariadb/mysql5.5/data
[mysql@mysql-test-150 data]$ ls -rlt
total 245824
drwxrwxr-x 2 mysql mysql        20 Sep  2 14:15 test
-rw-r----- 1 mysql mysql     18885 Sep  2 14:15 mysql-test-150.err
-rw-rw---- 1 mysql mysql   5242880 Sep  2 14:15 ib_logfile0
drwx------ 2 mysql mysql       206 Sep  2 14:15 WORLD
-rw-rw---- 1 mysql mysql   5242880 Sep  2 14:15 ib_logfile1
-rw-rw---- 1 mysql mysql         0 Sep  2 14:17 multi-master.info
drwx------ 2 mysql mysql      4096 Sep  2 14:18 mysql
drwx------ 2 mysql mysql        20 Sep  2 14:18 performance_schema
-rw-rw-r-- 1 mysql mysql        15 Sep  2 14:18 mysql_upgrade_info
-rw-rw---- 1 mysql mysql      3542 Sep  2 14:21 ib_buffer_pool
-rw-rw---- 1 mysql mysql        52 Sep  2 14:21 aria_log_control
-rw-rw---- 1 mysql mysql     16384 Sep  2 14:21 aria_log.00000001
-rw-rw---- 1 mysql mysql        21 Sep  2 14:21 mariadb-bin.index
-rw-rw---- 1 mysql mysql         5 Sep  2 14:21 mysql-test-150.pid
-rw-rw---- 1 mysql mysql  12582912 Sep  2 14:21 ibtmp1
drwx------ 2 mysql mysql       184 Sep  2 14:22 employees
-rw-rw---- 1 mysql mysql       814 Sep  2 14:22 mariadb-bin.000001
-rw-rw---- 1 mysql mysql 228589568 Sep  2 14:22 ibdata1
[mysql@mysql-test-150 data]$ 
```

Get the MariaDB's `master status` output

```
[mysql@mysql-test-150 ~]$ /mariadb/mariadb.5008/bin/mysql -h192.168.56.150 -uroot -p -P5008
Enter password: 
Welcome to the MariaDB monitor.  Commands end with ; or \g.
Your MariaDB connection id is 10
Server version: 10.3.8-MariaDB-log MariaDB Server

Copyright (c) 2000, 2018, Oracle, MariaDB Corporation Ab and others.

Type 'help;' or '\h' for help. Type '\c' to clear the current input statement.

MariaDB [(none)]> select version();
+--------------------+
| version()          |
+--------------------+
| 10.3.8-MariaDB-log |
+--------------------+
1 row in set (0.001 sec)

MariaDB [(none)]> show master status\G
*************************** 1. row ***************************
            File: mariadb-bin.000001
        Position: 330
    Binlog_Do_DB: 
Binlog_Ignore_DB: 
1 row in set (0.000 sec) 
```

As per MariaDB, the binlog filename is `mariadb-bin.000001` which is undetstandable because we have just enabled binlogs. We will need this file name in the next section when we setup MaxScale as binlog router to for MariaDB binlogs.

#### 1.3.3 Set-up MaxScale as a Slave to MariaDB
Login to MaxScale's MySQL interface

The mysql command passes hostname `-h` the IP address of the maxscale server, `-u` username and `-p` password as specified in teh `maxscale.cnf` file. Finally the port `6603` which is also speified in the `[binlog-listner]` of the maxscale.cnf file.

We will use the `CHANGE MASTER TO` command to define MariaDB server as the master to MaxScale. The MASTER_LOG_FILE and MASTER_LOG_POS as 4 are used from the above master status which was executed in MariaDB.

```
[mysql@mysql-test-150 ~]$ /mariadb/mariadb.5008/bin/mysql -h192.168.56.150 -urepl_user -psecretpassword -P6603
Welcome to the MariaDB monitor.  Commands end with ; or \g.
Your MySQL connection id is 4
Server version: 5.5.59-log

Copyright (c) 2000, 2018, Oracle, MariaDB Corporation Ab and others.

Type 'help;' or '\h' for help. Type '\c' to clear the current input statement.

MySQL [(none)]> CHANGE MASTER TO MASTER_HOST='192.168.56.150', MASTER_PORT=5008, MASTER_USER='repl_user', MASTER_PASSWORD='secretpassword', MASTER_LOG_FILE='mariadb-bin.000001', MASTER_LOG_POS=4;
Query OK, 0 rows affected (0.001 sec)

MySQL [(none)]> start slave;
Query OK, 0 rows affected (0.006 sec)

MySQL [(none)]> show slave status\G
*************************** 1. row ***************************
               Slave_IO_State: Binlog Dump
                  Master_Host: 192.168.56.150
                  Master_User: repl_user
                  Master_Port: 5008
                Connect_Retry: 60
              Master_Log_File: mariadb-bin.000001
          Read_Master_Log_Pos: 330
               Relay_Log_File: mariadb-bin.000001
                Relay_Log_Pos: 330
        Relay_Master_Log_File: mariadb-bin.000001
             Slave_IO_Running: Yes
            Slave_SQL_Running: Yes
              Replicate_Do_DB: 
          Replicate_Ignore_DB: 
           Replicate_Do_Table: 
       Replicate_Ignore_Table: 
      Replicate_Wild_Do_Table: 
  Replicate_Wild_Ignore_Table: 
                   Last_Errno: 0
                   Last_Error: 
                 Skip_Counter: 0
          Exec_Master_Log_Pos: 330
              Relay_Log_Space: 330
              Until_Condition: None
               Until_Log_File: 
                Until_Log_Pos: 0
           Master_SSL_Allowed: No
           Master_SSL_CA_File: 
           Master_SSL_CA_Path: 
              Master_SSL_Cert: 
            Master_SSL_Cipher: 
               Master_SSL_Key: 
        Seconds_Behind_Master: 0
Master_SSL_Verify_Server_Cert: No
                Last_IO_Errno: 0
                Last_IO_Error: 
               Last_SQL_Errno: 0
               Last_SQL_Error: 
  Replicate_Ignore_Server_Ids: 
             Master_Server_Id: 1000
                  Master_UUID: b9430e66-aee6-11e8-907f-080027b0d166
             Master_Info_File: /var/lib/maxscale/master.ini
                    SQL_Delay: 0
          SQL_Remaining_Delay: NULL
      Slave_SQL_Running_State: Slave running
           Master_Retry_Count: 1000
                  Master_Bind: 
      Last_IO_Error_TimeStamp: 
     Last_SQL_Error_Timestamp: 
               Master_SSL_Crl: 
           Master_SSL_Crlpath: 
           Retrieved_Gtid_Set: 
            Executed_Gtid_Set: 
                Auto_Position: 
1 row in set (0.001 sec)

MySQL [(none)]> 
```

This concludes our MaxScale setup. Now MaxScale is running as a binlog router. We can verify that the `binlog` directory specified in the maxscale.cnf is successfully receiving binlogs from the MariaDB master.

```
[mysql@mysql-test-150 ~]$ cd /var/lib/maxscale
[mysql@mysql-test-150 maxscale]$ ls -rlt
total 16
drwxr-xr-x 2 maxscale maxscale    6 Sep  2 15:00 maxscale.cnf.d
-rw-r--r-- 1 maxscale maxscale   54 Sep  2 15:00 maxadmin-users
-rw-r--r-- 1 maxscale maxscale   84 Sep  2 15:00 passwd
drwxr-xr-x 2 maxscale maxscale    6 Sep  2 15:31 data6908
drwx------ 2 maxscale maxscale  224 Sep  2 15:31 cache
-rw-r--r-- 1 maxscale maxscale 2058 Sep  2 15:36 mariadb-bin.000001
-rw------- 1 maxscale maxscale  193 Sep  2 15:37 master.ini
[mysql@mysql-test-150 maxscale]$ 
```

#### 1.3.4 Setup MySQL as a Slave to MaxScale
Finally we are ready to set MySQL as a slave to MaxScale. Now that MaxScle is acting as a BinLog router, MySQL will be able to receive the data for replication.

Login to MaxScale's SQL interface and take note of the MASTER STATUS.

```
[mysql@mysql-test-150 maxscale]$ /mariadb/mariadb.5008/bin/mysql -h192.168.56.150 -urepl_user -psecretpassword -P6603
Welcome to the MariaDB monitor.  Commands end with ; or \g.
Your MySQL connection id is 8
Server version: 5.5.59-log MariaDB Server

Copyright (c) 2000, 2018, Oracle, MariaDB Corporation Ab and others.

Type 'help;' or '\h' for help. Type '\c' to clear the current input statement.

MySQL [(none)]> 
MySQL [(none)]> 
MySQL [(none)]> 
MySQL [(none)]> show master status\G
*************************** 1. row ***************************
            File: mariadb-bin.000001
        Position: 4
    Binlog_Do_DB: 
Binlog_Ignore_DB: 
Execute_Gtid_Set: 
1 row in set (0.001 sec)

MySQL [(none)]> 
```

Login to MySLQ and execute the CHANGE MASTER command to define MaxScale as a Master to MySQL.

Before that, we will need to create the repl_user@'%' in MySQL as well. 

```
Server version: 5.5.59 MySQL Community Server (GPL)

Copyright (c) 2000, 2018, Oracle and/or its affiliates. All rights reserved.

Oracle is a registered trademark of Oracle Corporation and/or its
affiliates. Other names may be trademarks of their respective
owners.

Type 'help;' or '\h' for help. Type '\c' to clear the current input statement.

mysql> 
mysql> 
mysql> show master status\G
Empty set (0.00 sec)

mysql> show slave status\G
Empty set (0.00 sec)

mysql> CHANGE MASTER TO MASTER_HOST='192.168.56.150', MASTER_PORT=6603, MASTER_USER='repl_user', MASTER_PASSWORD='secretpassword', MASTER_LOG_FILE='mariadb-bin.000001', MASTER_LOG_POS=4;
Query OK, 0 rows affected (0.03 sec)

mysql> start slave;
ERROR 1200 (HY000): The server is not configured as slave; fix in config file or with CHANGE MASTER TO
mysql> 
```

`start slave` command is telling us that we have not specified a server_id for the MySQL. edit my.cnf / server.cnf file and define a server_id that does not conflict with the MariaDB or MaxScale.

my.cnf for MySQL
```
[mysqld]
server_id=10
```

Restart MySQL DB and try to start slave again.

```
Server version: 5.5.59 MySQL Community Server (GPL)

Copyright (c) 2000, 2018, Oracle and/or its affiliates. All rights reserved.

Oracle is a registered trademark of Oracle Corporation and/or its
affiliates. Other names may be trademarks of their respective
owners.

Type 'help;' or '\h' for help. Type '\c' to clear the current input statement.

mysql> stop slave;
Query OK, 0 rows affected (0.00 sec)

mysql> CHANGE MASTER TO MASTER_HOST='192.168.56.150', MASTER_PORT=6603, MASTER_USER='repl_user', MASTER_PASSWORD='secretpassword', MASTER_LOG_FILE='mariadb-bin.000001', MASTER_LOG_POS=4;
Query OK, 0 rows affected (0.02 sec)

mysql> start slave;
Query OK, 0 rows affected (0.01 sec)

mysql> show slave status\G
*************************** 1. row ***************************
               Slave_IO_State: Checking master version
                  Master_Host: 192.168.56.150
                  Master_User: repl_user
                  Master_Port: 6603
                Connect_Retry: 60
              Master_Log_File: mariadb_bin.000001
          Read_Master_Log_Pos: 4
               Relay_Log_File: mysql-test-150-relay-bin.000001
                Relay_Log_Pos: 4
        Relay_Master_Log_File: mariadb_bin.000001
             Slave_IO_Running: Yes
            Slave_SQL_Running: Yes
              Replicate_Do_DB: 
          Replicate_Ignore_DB: 
           Replicate_Do_Table: 
       Replicate_Ignore_Table: 
      Replicate_Wild_Do_Table: 
  Replicate_Wild_Ignore_Table: 
                   Last_Errno: 0
                   Last_Error: 
                 Skip_Counter: 0
          Exec_Master_Log_Pos: 4
              Relay_Log_Space: 107
              Until_Condition: None
               Until_Log_File: 
                Until_Log_Pos: 0
           Master_SSL_Allowed: No
           Master_SSL_CA_File: 
           Master_SSL_CA_Path: 
              Master_SSL_Cert: 
            Master_SSL_Cipher: 
               Master_SSL_Key: 
        Seconds_Behind_Master: 0
Master_SSL_Verify_Server_Cert: No
                Last_IO_Errno: 0
                Last_IO_Error: 
               Last_SQL_Errno: 0
               Last_SQL_Error: 
  Replicate_Ignore_Server_Ids: 
             Master_Server_Id: 1000
1 row in set (0.00 sec)

mysql> 
```

We have successfully setup the link from `MariaDB -> MaxScale -> MySQL 5.x` lets do a quick test to see if the data actually flows back from MariaDB to MySQL?

Login to MariaDB which is our new source and create a new DB and a Table with some data. This new DB and table should be now automatically cretead in teh MySQL DB.

```
Server version: 10.3.8-MariaDB-log MariaDB Server

Copyright (c) 2000, 2018, Oracle, MariaDB Corporation Ab and others.

Type 'help;' or '\h' for help. Type '\c' to clear the current input statement.

MariaDB [(none)]> 
MariaDB [(none)]> 
MariaDB [(none)]> 
MariaDB [(none)]> 
MariaDB [(none)]> create database test_db;
Query OK, 1 row affected (0.001 sec)

MariaDB [(none)]> use test_db;
Database changed
MariaDB [test_db]> create table test_tab(id serial, col varchar(100));
Query OK, 0 rows affected (0.016 sec)

MariaDB [test_db]> insert into test_tab(col) values ('First Row');
Query OK, 1 row affected (0.001 sec)

MariaDB [test_db]> commit;
Query OK, 0 rows affected (0.000 sec)
```

