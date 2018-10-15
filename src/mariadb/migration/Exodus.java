package mariadb.migration;

import java.util.ArrayList;
import java.util.List;

import mariadb.migration.mysql.*;
public class Exodus {
    public static boolean DryRun = Util.getPropertyValue("DryRun").equals("YES"), MigrationErrors;
    public static String LogPath = Util.getPropertyValue("LogPath");

    public static void main(String[] args) {
        MigrationErrors = false;
        MySQLConnect SourceCon = new MySQLConnect(Util.getPropertyValue("SourceDB"));
        MariaDBConnect TargetCon = new MariaDBConnect(Util.getPropertyValue("TargetDB"));

        if (Integer.valueOf(Util.getPropertyValue("ThreadCount")) <= 1) {
            StartExodusSingle(SourceCon, TargetCon);
        } else {
            StartExodusMulti(SourceCon, TargetCon);
        }

        
        System.out.println("\n\n");
        return;
    }
    
    public static void StartExodusSingle(DBConHandler SourceCon, DBConHandler TargetCon) {
        MySQLDatabase MyDB = new MySQLDatabase(SourceCon.getDBConnection());
        System.out.println("\n\n------------------------------------------------------");
        System.out.println("- Parsing Completed, Starting Single Threaded Process");
        System.out.println("------------------------------------------------------");
        try {
            for (SchemaHandler oSchema : MyDB.getSchemaList()) {
                //This Function creates the Progress table, Database and User accounts if needed
                if (!PreMigrationSetup(TargetCon, MyDB, oSchema)) {
                    return;
                }

                for (TableHandler Tab : oSchema.getTables()) {
                    if (!Tab.getMigrationSkipped()) {
                        MySQLExodusSingle SingleTable = new MySQLExodusSingle(Tab);
                        SingleTable.start();
                    }
                }

                if (!DryRun) {
                    CreateOtherObjects(oSchema);
                }
            }
            
        } catch (Exception e) {
            System.out.println("Error While Processing");
            new Logger(LogPath + "/Exodus.err", "Error While Processing - " + e.getMessage(), true);
            e.printStackTrace();
        } finally {
            //Execute Additional Post-Migration Scripts at the end of Migration
            Util.ExecuteScript(TargetCon, Util.GetExtraStatements("MySQL.PostLoadStatements"));

            SourceCon.DisconnectDB();
            TargetCon.DisconnectDB();
        }
    }

    public static void StartExodusMulti(DBConHandler SourceCon, DBConHandler TargetCon) {
        MySQLDatabase MyDB = new MySQLDatabase(SourceCon.getDBConnection());
        System.out.println("\n\n------------------------------------------------------");
        System.out.println("- Parsing Completed, Starting Multi Threaded Process");
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

                if (!DryRun) {
                    CreateOtherObjects(oSchema);
                }
            }
        } catch (Exception e) {
            System.out.println("\nError: " + e.getMessage());
            e.printStackTrace();
        } finally {
            //Execute Additional Post-Migration Scripts at the end of Migration
            Util.ExecuteScript(TargetCon, Util.GetExtraStatements("MySQL.PostLoadStatements"));
            
            //Cleanup Table Structure Creation Threads
            HouseKeepThreads(ThreadWorker);
            
            //Disconnect from Databases
            SourceCon.DisconnectDB();
            TargetCon.DisconnectDB();            
        }
    }        

    //Responsible for Cleaning up the Worker Threads
	private static void HouseKeepThreads(List<MySQLExodusMulti> ThreadWorker) {
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
            //System.out.println();
        }
        ThreadWorker.clear();
    }
    
    private static boolean PreMigrationSetup(DBConHandler TargetCon, DatabaseHandler MyDB, SchemaHandler Schema) {
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

    public static void CreateOtherObjects(SchemaHandler oSchema) {
        //Create Views
        /*
            mysql -u username INFORMATION_SCHEMA --skip-column-names --batch -e "select table_name from tables where table_type = 'VIEW' and table_schema = 'database'" | xargs mysqldump -u username database > views.sql
        */
        for (ViewHandler View : oSchema.getViewsList()) {

        }
        
        //Create Triggers
        /*
            mysqldump --routines --no-create-info --no-data --no-create-db --skip-opt mydb > sourcecode.sql
        */
        for (SourceCodeHandler Source : oSchema.getSourceCodeList()) {

        }

        //Create Stored Procedures / Functions
        for (TableHandler Tab : oSchema.getTables()) {
            for (String Trigger: Tab.getTriggers()) {

            }
        }

    }
}
