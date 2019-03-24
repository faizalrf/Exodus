package mariadb.migration.mysql;

import java.sql.SQLException;
import mariadb.migration.DBConHandler;
import mariadb.migration.MariaDBConnect;
import mariadb.migration.ExodusWorker;
import mariadb.migration.Logger;
import mariadb.migration.TableHandler;
import mariadb.migration.Util;

public class MySQLExodusSingle {
	private DBConHandler SourceCon, TargetCon;
	private TableHandler Table;
	long RowsMigrated=0;
	
	//Start Progress for the Current Table #Table#
	String MigrationTask;

	public MySQLExodusSingle(TableHandler iTable) {
		Table = iTable;
	
		SourceCon = new MySQLConnect(Util.getPropertyValue("SourceDB"));
		TargetCon = new MariaDBConnect(Util.getPropertyValue("TargetDB"));

		//Dry Run or normal Migration
		if (Util.getPropertyValue("DryRun").equals("NO") && Util.getPropertyValue("MigrateData").equals("YES")) {
			MigrationTask = "MIGRATE";
		} else {
			MigrationTask = "SKIP";
		}		
	}
	
	public void start() {
		try {
			//If Table has not already migrated or the OverWrite Flag is set to YES, then proceed
			if (!Table.hasTableMigrated() || Util.getPropertyValue("OverWriteTables").equals("YES")) {
				ExodusWorker MySQLExodusWorker = new ExodusWorker(SourceCon, TargetCon, Table, MigrationTask);
				RowsMigrated = MySQLExodusWorker.Exodus();
			} else {
				System.out.println("Processing Table " + Util.rPad(Table.getFullTableName(), 63, " ") + "--> " + "Already Migrated, SKIPPED!");
			}
		} catch (SQLException e) {
			new Logger(Util.getPropertyValue("LogPath") + "/Exodus.err", e.getMessage(), true);
			e.printStackTrace();
		} finally {			
			SourceCon.DisconnectDB();
			TargetCon.DisconnectDB();
		}
	}
}
