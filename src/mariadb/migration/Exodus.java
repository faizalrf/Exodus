package mariadb.migration;

import java.util.ArrayList;
import java.util.List;

import mariadb.migration.mysql.*;
public class Exodus {
    public static boolean DryRun, MigrationErrors;
    public static String LogPath;

    public static void main(String[] args) {
        DryRun = Util.getPropertyValue("DryRun").equals("YES");
        LogPath = Util.getPropertyValue("LogPath");
        MigrationErrors = false;
        MySQLConnect SourceCon = new MySQLConnect(Util.getPropertyValue("SourceDB"));
        MariaDBConnect TargetCon = new MariaDBConnect(Util.getPropertyValue("TargetDB"));

        if (Integer.valueOf(Util.getPropertyValue("ThreadCount")) <= 1) {
            StartExodusSingle(SourceCon, TargetCon);
        } else {
            StartExodusMulti(SourceCon, TargetCon);
        }
        return;
    }
    
    public static void StartExodusSingle(DBConHandler SourceCon, DBConHandler TargetCon) {
        MySQLDatabase MyDB = new MySQLDatabase(SourceCon.getDBConnection());
        
        try {
            for (SchemaHandler oSchema : MyDB.getSchemaList()) {
                if (!DryRun) {
                    //Create Target Schema if not already there
                    if (Util.ExecuteScript(TargetCon, oSchema.getSchemaScript()) < 0) {
                        System.out.println("\nFailed to create database, Aborting!\n");
                        return;
                    }

                    //Create Database first then create Migration Log Table in the Schema
                    ExodusProgress.CreateProgressLogTable(oSchema.getSchemaName());
                }

                for (TableHandler Tab : oSchema.getTables()) {
                    if (!Tab.getMigrationSkipped()) {
                        MySQLExodusSingle SingleTable = new MySQLExodusSingle(Tab);
                        SingleTable.start();
                    }
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

        //Array to store the current active threads!
        List<MySQLExodusMulti> ThreadWorker = new ArrayList<MySQLExodusMulti>();

        int ActiveThreadCount=0, ThreadCount;

        //Number of Parallel Tables Processing
        ThreadCount = Integer.valueOf(Util.getPropertyValue("ThreadCount"));
        try {
            //To Create new threads for each table
            for (SchemaHandler oSchema : MyDB.getSchemaList()) {
                if (!DryRun) {
                    //Create Migration Log Table in the Schema
                    ExodusProgress.CreateProgressLogTable(oSchema.getSchemaName());
                }
                for (TableHandler Tab : oSchema.getTables()) {
                    try {
                        //Keeps track of the number of Threads started
                        ActiveThreadCount++;
                        
                        //Add a new table to the list of threads. 
                        ThreadWorker.add(new MySQLExodusMulti(Tab));
                        ThreadWorker.get(ThreadWorker.size()-1).start();
                        
                        //Stop adding once the number of tables has reached the Max Allowed Thread Count as specified in the property file.
                        while (ThreadWorker.size() == ThreadCount) {
                            for (int CurrentThread=0; CurrentThread < ActiveThreadCount; CurrentThread++) {
                                //Remove the completed threads so that new tables can be added to the queue
                                if (!ThreadWorker.get(CurrentThread).isThreadActive()) {
                                    ThreadWorker.remove(CurrentThread);
                                    ActiveThreadCount--;
                                }
                            }
                        }
                        if (TargetCon.getDBConnection().isClosed() || SourceCon.getDBConnection().isClosed()) {
                            break;
                        }
                    } catch (Exception e) {
                        MigrationErrors = true;
                        System.out.println("Error while processing the main thread");
                        //Log the error to the log file
                        new Logger(LogPath + "/Exodus.err", "Error while processing the main thread - " + e.getMessage(), true);
                        e.printStackTrace();
                    }
                }
                //Cleanup Table Structure Creation Threads
                HouseKeepThreads(ThreadWorker);

            }
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
            e.printStackTrace();
        } finally {
            //Execute Additional Post-Migration Scripts at the end of Migration
            Util.ExecuteScript(TargetCon, Util.GetExtraStatements("MySQL.PostLoadStatements"));

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
        	        System.out.println("Thread Removed, Current Queue Size: " + CurrentThreadCount);
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
}
