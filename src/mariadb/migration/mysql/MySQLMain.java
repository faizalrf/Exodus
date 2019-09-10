package mariadb.migration.mysql;

import java.util.ArrayList;
import java.util.List;

import mariadb.migration.*;

public class MySQLMain {
    public boolean DryRun = Util.getPropertyValue("DryRun").equals("YES"), MigrationErrors;
    public String LogPath = Util.getPropertyValue("LogPath");

    public MySQLMain() {
        MigrationErrors = false;
        MySQLConnect SourceCon = new MySQLConnect(Util.getPropertyValue("SourceDB"));

        if (!DryRun) {
            //Execute These Additional Pre-Load Scripts at the begining of the session
            MariaDBConnect TmpTargetCon = new MariaDBConnect(Util.getPropertyValue("TargetDB"));
            Util.ExecuteScript(TmpTargetCon, Util.GetExtraStatements("MariaDB.PreLoadStatements"));
            Util.ExecuteScript(TmpTargetCon, SourceCon.getSQLMode());
            TmpTargetCon.DisconnectDB();
        }

        MariaDBConnect TargetCon = new MariaDBConnect(Util.getPropertyValue("TargetDB"));

        if (Integer.valueOf(Util.getPropertyValue("ThreadCount")) <= 1) {
            StartExodusSingle(SourceCon, TargetCon);
        } else {
            StartExodusMulti(SourceCon, TargetCon);
        }

        if (!DryRun) {
            //Execute Additional Post-Migration Scripts at the end of Migration
            MariaDBConnect TmpTargetCon = new MariaDBConnect(Util.getPropertyValue("TargetDB"));
            Util.ExecuteScript(TmpTargetCon, Util.GetExtraStatements("MariaDB.PostLoadStatements"));
            Util.ExecuteScript(TmpTargetCon, TargetCon.getSQLMode());
            TmpTargetCon.DisconnectDB();
        }

        System.out.println("\n\n");
        return;
    }

    public void StartExodusSingle(DBConHandler SourceCon, DBConHandler TargetCon) {
        //Read The Database
        MySQLDatabase MyDB = new MySQLDatabase(SourceCon.getDBConnection());
        System.out.println("\n\n-------------------------------------------------------");
        
        if (DryRun) {
            System.out.println("- Dry Run, Skipping Actual Migration Steps -");
            System.out.println("-------------------------------------------------------");
            return;
        }

        System.out.println("- Parsing Completed, Starting Single Threaded Process -");
        System.out.println("-------------------------------------------------------");

        try {
            for (SchemaHandler oSchema : MyDB.getSchemaList()) {
                //This Function creates the Progress table, Database and User accounts if needed
                if (!PreMigrationSetup(TargetCon, MyDB, oSchema)) {
                    return;
                }
                //Switch to the respective Schema
                TargetCon.SetCurrentSchema(oSchema.getSchemaName());
                
                System.out.println("\n-------------------------------------------------------");
                System.out.println("- Starting `" + oSchema.getSchemaName() + "` Migration");
                System.out.println("-------------------------------------------------------");

                for (TableHandler Tab : oSchema.getTables()) {
                    if (!Tab.getMigrationSkipped()) {
                        MySQLExodusSingle SingleTable = new MySQLExodusSingle(Tab);
                        SingleTable.start();
                    }
                }
                //Create PLSQL, Triggers, Views etc.
                CreateOtherObjects(oSchema, TargetCon);
            }
            //Create User Grants after all the Tables/Views and PLSQL have been created
            CreateUserGrants(TargetCon, MyDB);
        } catch (Exception e) {
            System.out.println("Error While Processing");
            new Logger(LogPath + "/Exodus.err", "Error While Processing - " + e.getMessage(), true);
            e.printStackTrace();
        } finally {
            SourceCon.DisconnectDB();
            TargetCon.DisconnectDB();
        }
    }

    public void StartExodusMulti(DBConHandler SourceCon, DBConHandler TargetCon) {
        MySQLDatabase MyDB = new MySQLDatabase(SourceCon.getDBConnection());

        System.out.println("\n\n-------------------------------------------------------");

        if (DryRun) {
            System.out.println("- Dry Run, Skipping Actual Migration Steps -");
            System.out.println("-------------------------------------------------------");
            return;
        }

        System.out.println("- Parsing Completed, Starting Multi Threaded Process -");
        System.out.println("------------------------------------------------------");

        //Array to store the current active threads!
        List<MySQLExodusMulti> ThreadWorker = new ArrayList<MySQLExodusMulti>();

        int ThreadCount;

        //Number of Parallel Tables Processing
        ThreadCount = Integer.valueOf(Util.getPropertyValue("ThreadCount"));
        try {
            //To Create new threads for each table
            for (SchemaHandler oSchema : MyDB.getSchemaList()) {
                //This Function creates the Progress table, Database and User accounts if needed
                if (!PreMigrationSetup(TargetCon, MyDB, oSchema)) {
                    return;
                }
                //Switch to the respective Schema
                TargetCon.SetCurrentSchema(oSchema.getSchemaName());

                System.out.println("\n-------------------------------------------------------");
                System.out.println("- Starting `" + oSchema.getSchemaName() + "` Migration");
                System.out.println("-------------------------------------------------------");

                for (TableHandler Tab : oSchema.getTables()) {
                    try {
                        //Add a new table to the list of threads. 
                        ThreadWorker.add(new MySQLExodusMulti(Tab));
                        ThreadWorker.get(ThreadWorker.size()-1).start();

                        //Stop adding once the number of tables has reached the Max Allowed Thread Count as specified in the property file.
                        while (ThreadWorker.size() == ThreadCount) {
                            for (int CurrentThread=0; CurrentThread < ThreadWorker.size(); CurrentThread++) {
                                //Remove the completed threads so that new tables can be added to the queue
                                if (!ThreadWorker.get(CurrentThread).isThreadActive()) {
                                    ThreadWorker.remove(CurrentThread);
                                }
                            }
                            //Rest for Half a second before checking the threads status again
                            //Thread.sleep(500);
                        }
                        if (TargetCon.getDBConnection().isClosed() || SourceCon.getDBConnection().isClosed()) {
                            break;
                        }
                    } catch (Exception e) {
                        MigrationErrors = true;
                        System.out.println("\nError while processing the main thread");
                        //Log the error to the log file
                        new Logger(LogPath + "/Exodus.err", "Error while processing the main thread - " + e.getMessage(), true);
                        e.printStackTrace();
                    }
                }
                //Cleanup Table Structure Creation Threads
                HouseKeepThreads(ThreadWorker);

                //Create PLSQL, Triggers, Views etc.
                CreateOtherObjects(oSchema, TargetCon);
            }
            //Create User Grants after all the Tables/Views and PLSQL have been created
            CreateUserGrants(TargetCon, MyDB);
        } catch (Exception e) {
            System.out.println("\nError: " + e.getMessage());
            e.printStackTrace();
        } finally {                        
            //Disconnect from Databases
            SourceCon.DisconnectDB();
            TargetCon.DisconnectDB();            
        }
    }        

    //Responsible for Cleaning up the Worker Threads
	private void HouseKeepThreads(List<MySQLExodusMulti> ThreadWorker) {
		int CurrentThreadCount = ThreadWorker.size();
		
        while (CurrentThreadCount > 0) {
        	for (int x=0; x < CurrentThreadCount; x++)
        		//If any thread has completed, remove it from the list and decrease ActiveThreadCount by 1
        		if (!ThreadWorker.get(x).isThreadActive()) {
        			ThreadWorker.remove(x);
        			CurrentThreadCount--;
        	        System.out.print("\nThread Removed, Current Queue Size: " + CurrentThreadCount);
        		}
        	try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				System.out.println("Thread Cleanup Exception");
                new Logger(LogPath + "/Exodus.err", "Thread Cleanup Exception - " + e.getMessage(), true);
				e.printStackTrace();
            }
        }
        ThreadWorker.clear();
    }

    private boolean PreMigrationSetup(DBConHandler TargetCon, DatabaseHandler MyDB, SchemaHandler Schema) {
        if (!DryRun) {
            //Create Target Schema if not already there
            if (Util.ExecuteScript(TargetCon, Schema.getSchemaScript()) < 0) {
                System.out.println("\nFailed to create database, Aborting!\n");
                return false;
            }

            //Create Database first then create Migration Log Table in the Schema
            ExodusProgress.CreateProgressLogTable(Schema.getSchemaName());
            Util.ExecuteScript(TargetCon, MyDB.getCreateUserScript());
        } else {
            System.out.println("\nSkip Database/User Accounts Migration");
        }

        return true;
    }
    
    //Migrate PLSQL, Triggers, Views based on Property CreateViews,  CreatePLSQL, CreateTriggers    
    public void CreateOtherObjects(SchemaHandler oSchema, DBConHandler TargetCon) {
        if (DryRun) {
            return;
        }
        //Set Current Schema to ensure the objects are being created in the correct schema and not `mysql`
        TargetCon.SetCurrentSchema(oSchema.getSchemaName());

        if (Util.getPropertyValue("CreateViews").equals("YES")) {
            System.out.println("-\nMigrating Views...");

            //Create Views
            for (ViewHandler View : oSchema.getViewsList()) {
				System.out.print(Util.rPad("Migrating View Script " + View.getFullViewName(), 80, " ") + "--> [ .. ]");
                Util.ExecuteScript(TargetCon, View.getViewScript());
				System.out.print("\r" + Util.rPad("Migrating View Script " + View.getFullViewName(), 80, " ") + "--> [ OK ]\n");
                for (String SQL : View.getViewScript()) {
                    new Logger(Util.getPropertyValue("DDLPath") + "/Views.sql", SQL + ";\n", true, false);
                }
            }
            System.out.println("Migration of Views Completed...");
        }

        //Create Triggers/PLSQL
        if (Util.getPropertyValue("CreatePLSQL").equals("YES")) {
            System.out.println("-\nMigrating Stored Routines Scripts...");
            new Logger(Util.getPropertyValue("DDLPath") + "/PLSQL.sql", "DELIMITER //", true, false);
            
            //Create Stored Procedures / Functions
            for (SourceCodeHandler Source : oSchema.getSourceCodeList()) {
                //Util.ExecuteScript(TargetCon, Source.getSQLMode());
				System.out.print(Util.rPad("Migrating " + Source.getSourceType() + " Code Script " + Source.getFullObjectName(), 80, " ") + "--> [ .. ]");
                Util.ExecuteScript(TargetCon, Source.getSourceScript());
				System.out.print("\r" + Util.rPad("Migrating " + Source.getSourceType() + " Code Script " + Source.getFullObjectName(), 80, " ") + "--> [ OK ]\n");
                //new Logger(Util.getPropertyValue("DDLPath") + "/PLSQL.sql", Source.getSQLMode() + "//\n", true, false);
                for (String SQL : Source.getSourceScript()) {
                    new Logger(Util.getPropertyValue("DDLPath") + "/PLSQL.sql", SQL + "//\n", true, false);                    
                }
            }
            new Logger(Util.getPropertyValue("DDLPath") + "/PLSQL.sql", "DELIMITER ;", true, false);
            System.out.println("Migration of Stored Routines Scripts Completed...");
        }

        if (Util.getPropertyValue("CreateTriggers").equals("YES")) {
            System.out.println("-\nMigrating Trigger Scripts...");
            new Logger(Util.getPropertyValue("DDLPath") + "/Triggers.sql", "DELIMITER //", true, false);
            
            //Create Triggers
            for (TableHandler Tab : oSchema.getTables()) {
                System.out.print(Util.rPad("Migrating Trigger For Table " + Tab.getFullTableName(), 80, " ") + "--> [ .. ]");
                for (String TriggerScript: Tab.getTriggers()) {
                    Util.ExecuteScript(TargetCon, TriggerScript);
                    new Logger(Util.getPropertyValue("DDLPath") + "/Triggers.sql", TriggerScript + "//\n", true, false);
                }
                System.out.print("\r" + Util.rPad("Migrating Trigger For Table " + Tab.getFullTableName(), 80, " ") + "--> [ OK ]\n");
            }
            new Logger(Util.getPropertyValue("DDLPath") + "/Triggers.sql", "DELIMITER ;", true, false);
            System.out.println("Migration of Trigger Scripts Completed...");
        }
    }
    
    //Migrate User Grants to MariaDB based on UserGrants property
    private void CreateUserGrants(DBConHandler TargetCon, DatabaseHandler MyDB) {
        if (DryRun) {
            return;
        }
        if (Util.getPropertyValue("UserGrants").equals("YES")) {
            System.out.println("-\nExecuting User Grants...");
            //Grants for the User Accounts
            Util.ExecuteScript(TargetCon, MyDB.getUserGrantsScript());
            System.out.println("Execution of User Grants Completed...");
        } else {
            System.out.println("\nSkip User Grants");
        }
    }
}
