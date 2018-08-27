package mariadb.migration;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class ExodusProgress {
	DBConHandler TargetCon;
	
	ResultSet ResultSetObj;
	Statement StatementObj;
	String TableName = "", SchemaName="";
	
	int SerialNo=0;
	long TotalRecords, RecordsLoaded, RecordsUpdated;
	
	//Constructor Connect to the TargetDB
	public ExodusProgress() { 
		TargetCon = new MariaDBConnect(Util.getPropertyValue("TargetDB")); 
	}
	
	//Constructor with Source Table as a parameter
	public ExodusProgress(TableHandler iTable) {
		String sqlStatement, CreateTables="";
		TableName = iTable.getTableName();
		SchemaName = iTable.getSchemaName();
		TotalRecords = iTable.getRecordCount();
		TargetCon = new MariaDBConnect(Util.getPropertyValue("TargetDB"));
		SerialNo = getSerialNo(SchemaName, TableName);
		
        try {
			Util.getPropertyValue("DryRun");
		} catch (NumberFormatException e1) {
			System.out.println("Reading Properties: " + e1.getMessage());
		}
        
        if (CreateTables.equals("NO")) {
			try {
				StatementObj = TargetCon.getDBConnection().createStatement();
	
				sqlStatement = "SELECT TotalRecords, RecordsLoaded, RecordsUpdated from MigrationLog WHERE SchemaName = '" + SchemaName + "' AND TableName = '" + TableName + "' AND SerialNo = " + SerialNo;
				ResultSetObj = StatementObj.executeQuery(sqlStatement);
				if (ResultSetObj.next()) {
					TotalRecords = ResultSetObj.getLong("TotalRecords");
					RecordsLoaded = ResultSetObj.getLong("RecordsLoaded");
					RecordsUpdated = ResultSetObj.getLong("RecordsUpdated");
				} else {
					sqlStatement = "INSERT INTO MigrationLog(SchemaName, TableName, SerialNo, TotalRecords) VALUES('" + SchemaName + "', '" + TableName + "', " + SerialNo + ", 0)";
					StatementObj.executeUpdate(sqlStatement);
				}
				TargetCon.getDBConnection().commit();				
				StatementObj.close();
				ResultSetObj.close();
				
			} catch (SQLException e) {
				e.printStackTrace();
			}
        }
	}

	public static void CreateProgressLogTable() {
		String sqlStatement;
		Statement StatementObj;
		DBConHandler TargetCon = new MariaDBConnect(Util.getPropertyValue("TargetDB"));

		try {
			StatementObj = TargetCon.getDBConnection().createStatement();
			sqlStatement = "CREATE TABLE IF NOT EXISTS MigrationLog(SchemaName VARCHAR(100), TableName VARCHAR(100) NOT NULL, SerialNo INT NOT NULL DEFAULT '1', " + 
							"StartTime TIMESTAMP NOT NULL DEFAULT Current_Timestamp(), EndTime TIMESTAMP NULL DEFAULT NULL, " + 
							"TotalRecords BIGINT(20) UNSIGNED NULL DEFAULT '0', DeltaRecords BIGINT UNSIGNED NULL DEFAULT '0', " +
							"RecordsLoaded BIGINT UNSIGNED NULL DEFAULT '0', RecordsUpdated BIGINT UNSIGNED NULL DEFAULT '0', " + 
							"LastKeyUpdated INT NOT NULL DEFAULT -1, UpdateTime TIMESTAMP NULL DEFAULT NULL ON UPDATE Current_Timestamp(), " + 
							"CreateDate TIMESTAMP NULL DEFAULT Current_Timestamp(), PRIMARY KEY (TableName, SerialNo))";
	
			StatementObj.executeUpdate(sqlStatement);
	
			sqlStatement = "CREATE TABLE IF NOT EXISTS MigrationLogDETAIL(ID Serial, SchemaName VARCHAR(100), TableName VARCHAR(100), StartTime TIMESTAMP DEFAULT Current_Timestamp(), " +
							"EndTime TIMESTAMP NULL, SQLCommand VARCHAR(10000), DBMessage VARCHAR(10000))";
	
			StatementObj.executeUpdate(sqlStatement);
			
			StatementObj.close();
			TargetCon.DisconnectDB();
		} catch (SQLException e) {
			System.out.println("*** Failed to create Migration Log Tables ***");
			e.printStackTrace();
		}
	}

	public void LogInsertProgress(long lRecordsCount) {
		String sqlStatement = "UPDATE MigrationLog SET TotalRecords = " + TotalRecords + ", RecordsLoaded = " + lRecordsCount + " WHERE SchemaName = '" + SchemaName + "' AND TableName = '" + TableName + "' AND SerialNo = " + SerialNo;
		try {
			StatementObj = TargetCon.getDBConnection().createStatement();
			StatementObj.executeUpdate(sqlStatement);
			TargetCon.getDBConnection().commit();
			StatementObj.close();
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	public void LogInsertProgress(long lRecordsCount, long lRecordsUpdated) {
		String sqlStatement = "";
		
		try {
			sqlStatement = "UPDATE MigrationLog SET TotalRecords = " + TotalRecords + ", RecordsLoaded = " + lRecordsCount + ", RecordsUpdated = " + lRecordsUpdated + " WHERE SchemaName = '" + SchemaName + "' AND TableName = '" + TableName + "' AND SerialNo = " + SerialNo;
			StatementObj = TargetCon.getDBConnection().createStatement();
			StatementObj.executeUpdate(sqlStatement);
			TargetCon.getDBConnection().commit();
			StatementObj.close();
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}
	
	public void LogInsertProgress(long lDeltaRecords, long lRecordsCount, long lRecordsUpdated) {
		String sqlStatement = "";
		try {
			//Delta Records are lDeltaRecords - lRecordsCount
			sqlStatement = "UPDATE MigrationLog SET TotalRecords = " + TotalRecords + ", DeltaRecords = " + (lDeltaRecords - lRecordsCount) + ", RecordsLoaded = " + lRecordsCount + ", RecordsUpdated = " + lRecordsUpdated + " WHERE SchemaName = '" + SchemaName + "' AND TableName = '" + TableName + "' AND SerialNo = " + SerialNo;
			StatementObj = TargetCon.getDBConnection().createStatement();
			StatementObj.executeUpdate(sqlStatement);
			TargetCon.getDBConnection().commit();
			StatementObj.close();
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}	
	public void LogUpdateProgress(long lRecordsUpdated, long LastKey) {
		String sqlStatement = "UPDATE MigrationLog SET TotalRecords = " + TotalRecords + ", RecordsUpdated = " + lRecordsUpdated + ", LastKeyUpdated = " + LastKey + " WHERE SchemaName = '" + SchemaName + "' AND TableName = '" + TableName + "' AND SerialNo = " + SerialNo;
		try {
			StatementObj = TargetCon.getDBConnection().createStatement();
			StatementObj.executeUpdate(sqlStatement);
			TargetCon.getDBConnection().commit();	
			StatementObj.close();
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	public long getLastKeyUpdated() {
		long LastKey=-1;
		ResultSet ResultSetObj;
		String sqlStatement = "SELECT MAX(LastKeyUpdated) LastKey FROM MigrationLog WHERE SchemaName = '" + SchemaName + "' AND TableName = '" + TableName + "'";
		try {
			StatementObj = TargetCon.getDBConnection().createStatement();
			ResultSetObj = StatementObj.executeQuery(sqlStatement);
			
			if (ResultSetObj.next())
				LastKey = ResultSetObj.getLong("LastKey");
			
			StatementObj.close();
			
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return LastKey;
	}
	
	public void ProgressEnd() {
		String sqlStatement = "UPDATE MigrationLog SET EndTime = Current_Timestamp() WHERE SchemaName = '" + SchemaName + "' AND TableName = '" + TableName + "' AND SerialNo = " + SerialNo;
		try {
			if (SerialNo > 0) {
				StatementObj = TargetCon.getDBConnection().createStatement();
				StatementObj.executeUpdate(sqlStatement);
				TargetCon.getDBConnection().commit();	
				StatementObj.close();
			}
		} catch (SQLException e) {
			e.printStackTrace();
		} finally {
			try {
				StatementObj.close();
			} catch (SQLException e) {
				//Just Chill!
			}
			TargetCon.DisconnectDB();			
		}
	}
	
	public long getRecordsLoaded() {
		return RecordsLoaded;
	}
	
	//Following Static Methods are utilities to check for Migration Status of individual Tables
	public static boolean hasTableMigrationCompleted(String iSchemaName, String iTableName) {
		boolean hasMigrationCompleted=false;
		ResultSet ResultSetObj;
		Statement StatementObj;
		DBConHandler TargetCon = new MariaDBConnect(Util.getPropertyValue("TargetDB"));
		
		String sqlStatement = "SELECT Count(*) as MigrationCount, (CASE WHEN TotalRecords = 0 THEN -1 ELSE Sum(TotalRecords) - Sum(RecordsLoaded) END) AS Migrated FROM MigrationLog WHERE SchemaName = '" + iSchemaName + "' AND TableName = '" + iTableName + "'";
		try {
			StatementObj = TargetCon.getDBConnection().createStatement();
			ResultSetObj = StatementObj.executeQuery(sqlStatement);
			
			//Being a COUNT(*) query, it will always have 1 record in the result-set!
			ResultSetObj.next();
			hasMigrationCompleted = (ResultSetObj.getInt("MigrationCount") > 0 && ResultSetObj.getInt("Migrated") == 0);
			
			StatementObj.close();
			
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return hasMigrationCompleted;
	}

	public static boolean hasTablePartiallyMigrated(String iSchemaName, String iTableName) {
		boolean hasTableMigrated=false;
		ResultSet ResultSetObj;
		Statement StatementObj;
		DBConHandler TargetCon = new MariaDBConnect(Util.getPropertyValue("TargetDB"));
		
		String sqlStatement = "SELECT Count(*) MigrationCount, SUM(TotalRecords) - Sum(RecordsLoaded) AS hasMigrated, MAX(RecordsLoaded) LoadedRecords FROM MigrationLog WHERE SchemaName = '" + iSchemaName + "' AND TableName = '" + iTableName + "'";
		try {
			StatementObj = TargetCon.getDBConnection().createStatement();
			ResultSetObj = StatementObj.executeQuery(sqlStatement);
			
			//Being a COUNT(*) query, it will always have 1 record in the result-set!
			ResultSetObj.next();
			hasTableMigrated = (ResultSetObj.getInt("MigrationCount") > 0 && ResultSetObj.getInt("hasMigrated") > 0);
			
			StatementObj.close();
			
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return hasTableMigrated;
	}
	
	//Method assumes that DB2Table object has not yet been instantiated!
	public static int getSerialNo(String iSchemaName, String iTableName) {
		ResultSet ResultSetObj;
		Statement StatementObj;
		DBConHandler TargetCon = new MariaDBConnect(Util.getPropertyValue("TargetDB"));
		int iSerialNo=0;
		
		String sqlStatement = "SELECT COUNT(*) AS SerialNo FROM MigrationLog WHERE SchemaName = '" + iSchemaName + "' AND TableName = '" + iTableName + "'";
		try {
			StatementObj = TargetCon.getDBConnection().createStatement();
			ResultSetObj = StatementObj.executeQuery(sqlStatement);
			
			//Being a COUNT(*) query, it will always have 1 record in the result-set!
			ResultSetObj.next();
			iSerialNo = ResultSetObj.getInt("SerialNo")+1;
			
			StatementObj.close();
			
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return iSerialNo;
	}	
}
