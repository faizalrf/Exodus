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

        if (Integer.valueOf(Util.getPropertyValue("ThreadCount")) <= 1) {
            StartExodusSingle();
        } else {
            StartExodusMulti();
        }
        return;
    }
    
    public static void StartExodusSingle() {
        MySQLConnect SourceCon = new MySQLConnect(Util.getPropertyValue("SourceDB"));
        MariaDBConnect TargetCon = new MariaDBConnect(Util.getPropertyValue("TargetDB"));
        MySQLDatabase MyDB = new MySQLDatabase(SourceCon.getDBConnection());
        
        try {
            for (SchemaHandler oSchema : MyDB.getSchemaList()) {
                if (!DryRun) {
                    //Create Migration Log Table in the Schema
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
            new Logger(LogPath + "/Exodus.err", true, "Error While Processing - " + e.getMessage());
            e.printStackTrace();
        } finally {
            SourceCon.DisconnectDB();
            TargetCon.DisconnectDB();
        }
    }

    public static void StartExodusMulti() {
        MySQLConnect SourceCon = new MySQLConnect(Util.getPropertyValue("SourceDB"));
        MariaDBConnect TargetCon = new MariaDBConnect(Util.getPropertyValue("TargetDB"));
        MySQLDatabase MyDB = new MySQLDatabase(SourceCon.getDBConnection());

        //Array to store the current active threads!
        List<MySQLExodusMulti> ThreadWorker = new ArrayList<MySQLExodusMulti>();

        int ActiveThreadCount=0, ThreadCount;

        //Number of Parallel Tables Processing
        ThreadCount = Integer.valueOf(Util.getPropertyValue("ThreadCount"));
        
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
                    new Logger(LogPath + "/Exodus.err", true, "Error while processing the main thread - " + e.getMessage());
                    e.printStackTrace();
                }
            }
	        //Cleanup Table Structure Creation Threads
	        HouseKeepThreads(ThreadWorker);

        }
        SourceCon.DisconnectDB();
        TargetCon.DisconnectDB();
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
                new Logger(LogPath + "/Exodus.err", true, "Thread Cleanup Exception - " + e.getMessage());
				e.printStackTrace();
			}
        }
        ThreadWorker.clear();
	}
}
