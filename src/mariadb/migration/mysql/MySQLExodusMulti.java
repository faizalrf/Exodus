package mariadb.migration.mysql;

import mariadb.migration.DBConHandler;
import mariadb.migration.MariaDBConnect;
import mariadb.migration.ExodusWorker;
import mariadb.migration.Logger;
import mariadb.migration.TableHandler;
import mariadb.migration.Util;

public class MySQLExodusMulti implements Runnable {
	private DBConHandler SourceCon, TargetCon;
	private TableHandler Table;
	private Thread MySQLWorkerThread;
	private String ThreadName;
	long RowsMigrated=0;
	String MigrationTask;

	public MySQLExodusMulti(TableHandler iTable) {
		String SchemaName, TableName;
		Table = iTable;
		
		SourceCon = new MySQLConnect(Util.getPropertyValue("SourceDB"));
		TargetCon = new MariaDBConnect(Util.getPropertyValue("TargetDB"));

		SchemaName = Table.getSchemaName();
		TableName = Table.getTableName();
		ThreadName = SchemaName + "." + TableName;
		
		//Dry Run or normal Migration
		if (Util.getPropertyValue("DryRun").equals("NO")) {
			MigrationTask = "MIGRATE";
		} else {
			MigrationTask = "SKIP";
		}
	}

	public void run() {
    	try {
    		ExodusWorker MySQLExodusWorker = new ExodusWorker(SourceCon, TargetCon, Table, MigrationTask);
    		RowsMigrated = MySQLExodusWorker.Exodus();
    	}
    	catch (Exception e) {
			System.out.println("Exception While running Main Thread!");
			new Logger(".//logs//Exodus.err", true, "Exception While running Main Thread - " + e.getMessage());
			e.printStackTrace();
    	}
    }
    
	//Trigger the Thread for a given Table!
	public void start() {
    	if (MySQLWorkerThread == null) {
    		MySQLWorkerThread = new Thread (this, ThreadName);
    	}
    	MySQLWorkerThread.start();
	}

    public boolean isThreadActive() { 
		return MySQLWorkerThread.isAlive(); 
	}	
}
