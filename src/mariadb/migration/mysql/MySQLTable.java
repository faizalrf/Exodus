package mariadb.migration.mysql;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import mariadb.migration.TableHandler;
import mariadb.migration.ColumnHandler;
import mariadb.migration.ExodusProgress;
import mariadb.migration.ColumnCollectionHandler;
import mariadb.migration.Util;

public class MySQLTable implements TableHandler {
	private String SchemaName, TableName, TableScript, DeltaSelectScript, FullTableName, FullDeltaTableName, SelectColumnList, 
					RawColumnList, InsertBindList, PrimaryKey, PrimaryKeyBind, TableSelectScript, TargetInsertScript, MyPrimaryKeyScript, 
					AdditionalCriteria, WHERECriteria, DeltaDBName, SelectMD5;
	private ColumnCollectionHandler MyCol;

	private List<String> MyPKCols = new ArrayList<String>();
	private List<String> MyIndexes = new ArrayList<String>();
	private List<String> MyForeignKeys = new ArrayList<String>();
	private List<String> MyTriggers = new ArrayList<String>();
	private List<String> MyConstraints = new ArrayList<String>();
	private List<String> MyCheckConstraints = new ArrayList<String>();
	private List<String> MyDeltaScripts = new ArrayList<String>();
	private Connection oCon;
	private long RowCount, DeltaRowCount, MD5SelectionPercent;
	private boolean isMigrationSkipped = false;

	public MySQLTable(Connection iCon, String iSchemaName, String iTableName) {
		oCon = iCon;
		SchemaName = iSchemaName;
		TableName = iTableName;
		FullTableName = "`" + SchemaName + "`.`" + TableName + "`";
		DeltaDBName = "ExodusDb";

		//Becasue Schema is common, so delta table names should be prefixed with the Schema Names
		FullDeltaTableName = DeltaDBName + "." + SchemaName.toLowerCase() + "_" + TableName.toLowerCase();
		
		//Split up the Full Table Name into Schena + Table because I have to add "`" to the Full Table Name Variable
		AdditionalCriteria = Util.getPropertyValue(SchemaName + "." + TableName + ".AdditionalCriteria");
		WHERECriteria = Util.getPropertyValue(SchemaName + "." + TableName + ".WHERECriteria");
		MD5SelectionPercent = Integer.valueOf(Util.getPropertyValue("MD5SelectionPercent"));

		if (WHERECriteria.isEmpty()) {
			WHERECriteria = "1=1";
		}

		setMigrationSkipped();
		if (isMigrationSkipped) {
			TableScript = "";
			RowCount = 0;
			DeltaRowCount = 0;
			SelectColumnList = "";
			SelectMD5 = "";
			InsertBindList = "";
			PrimaryKey = "";
			PrimaryKeyBind = "";
		} else {
			MyCol = new MySQLColumnsCollection(iCon, iSchemaName, iTableName);
			setTableScript();
			setRecordCount();
			setColumnList();
			//Commented the following as these are not reqired due to "SHOW CREATE" usage of MySQL
			//setConstraints();
			//setPrimaryKeys();
			//setForeignKeys();
			//setCheckConstraints();
			//setIndexes();
			setTriggers();
		}
	}

	public void setMigrationSkipped() {
		String ScriptSQL;
		Statement oStatement;
		ResultSet oResultSet;
		ScriptSQL = "SELECT COUNT(*) ROW_COUNT FROM information_schema.tables where TABLE_SCHEMA = '" + SchemaName
				+ "' AND TABLE_NAME = '" + TableName + "' AND " + Util.getPropertyValue("TablesToMigrate")
				+ " AND NOT (" + Util.getPropertyValue("ExportTablesToFile") + ") AND NOT ("
				+ Util.getPropertyValue("SkipTableMigration") + ")";
		
		try {
			oStatement = oCon.createStatement();
			oResultSet = oStatement.executeQuery(ScriptSQL);
			if (oResultSet.next()) {
				isMigrationSkipped = (oResultSet.getLong("ROW_COUNT") == 0);
			}

			oResultSet.close();
			oStatement.close();
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	public void setTableScript() {
		String ScriptSQL;
		//, TableScriptPrefix, TableScriptSuffix;
		Statement oStatement;
		ResultSet oResultSet;

		//Let Database give the proper CREATE TABLE script including all the constraints and indexex
		ScriptSQL = "SHOW CREATE TABLE " + FullTableName;
		try {
			oStatement = oCon.createStatement();
			oResultSet = oStatement.executeQuery(ScriptSQL);

			if (oResultSet.next()) {
				TableScript = oResultSet.getString(2);

				//Compatibility with MariaDB
				TableScript = TableScript.replace("CREATE TABLE ", "CREATE TABLE IF NOT EXISTS `" + SchemaName + "`.");
				TableScript = TableScript.replace("GENERATED ALWAYS AS", "AS");
				TableScript = TableScript.replace("VIRTUAL NULL", "VIRTUAL");
				TableScript = TableScript.replace("STORED NOT NULL", "PERSISTENT");
				//TableScript = TableScript.replace(" DEFAULT '0000-00-00 00:00:00'", " DEFAULT CURRENT_TIMESTAMP()");
				//System.out.println(TableScript);
			}
			
			oResultSet.close();
			oStatement.close();
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}
	
	// Build up Table's Column List and Bind Variables List;
	public void setColumnList() {
		String DeltaPKColList = "", FirstKeyCol = "", ColumnExpression, ColumnName, DeltaColList="", DeltaTableScript="";
		SelectColumnList = "";
		RawColumnList = "";
		InsertBindList = "";
		PrimaryKey = "";
		PrimaryKeyBind = "";
		DeltaTableScript = "";
		SelectMD5 = "";
	
		System.out.print(Util.rPad("Parsing Table Structure " + FullTableName, 80, " ") + "--> ");
		for (ColumnHandler Col : MyCol.getColumnList()) {
			ColumnName = "`" + Col.getName() + "`";

			//Added support for TimeStamp and DateTime fields
			if (Col.getDataType().toUpperCase().equals("DATETIME") && Col.getNulls().toUpperCase().contains("NOT")) {
				ColumnExpression = "COALESCE(A.`" + Col.getName() + "`, '0000-00-00 00:00:00') AS `" + Col.getName() + "`";
			} else if (Col.getDataType().toUpperCase().equals("TIMESTAMP") && Col.getNulls().toUpperCase().contains("NOT")) {
				ColumnExpression = "COALESCE(A.`" + Col.getName() + "`, '0000-00-00 00:00:00') AS `" + Col.getName() + "`";
			} else {
				ColumnExpression = "A.`" + Col.getName() + "`";
			}
			SelectColumnList += ColumnExpression + ",";
			RawColumnList += ColumnName + ",";
			InsertBindList += "?,";

			//Append IFNULL if the Column is NULLABLE
			if (Col.IsNullable()) {
				SelectMD5 += "IFNULL(" + ColumnName + ", ''),";
			} else {
				SelectMD5 += ColumnName + ",";
			}

			if (Col.getIsPrimaryKey()) {
				PrimaryKey += ColumnExpression + ",";
				PrimaryKeyBind += "?,";
				DeltaColList += ColumnName + " " + Col.getColumnDataType() + ",";
				
				// To use for IS NULL in the DELTA SELECT;
				DeltaPKColList += "B." + ColumnName + " = " + ColumnExpression + " AND ";
				if (FirstKeyCol.isEmpty()) {
					FirstKeyCol = "B." + ColumnName;
				}
			}
		}
		System.out.print(Util.rPad("COLUMNS [" + Util.lPad(String.valueOf(MyCol.getColumnList().size()), 3, " ") + "]", 14, " "));
		System.out.print("-->  ROWS [" + Util.lPad(Util.numberFormat.format(RowCount), 13, " ") + "]\n");

		if (!SelectColumnList.isEmpty()) {
			SelectColumnList = SelectColumnList.substring(0, SelectColumnList.length() - 1);
			InsertBindList = InsertBindList.substring(0, InsertBindList.length() - 1);
			RawColumnList = RawColumnList.substring(0, RawColumnList.length() - 1);
			TableSelectScript = "SELECT " + SelectColumnList + " FROM " + FullTableName + " A";
			TargetInsertScript = "INSERT INTO " + FullTableName + "(" + RawColumnList + ") VALUES (" + InsertBindList + ")";

			//To be used to Generate the Hash of a table's Row
			SelectMD5 = "SELECT MD5(CONCAT(" + SelectMD5.substring(0, SelectMD5.length() - 1) + ")) AS RowHash FROM " + FullTableName;
		}

		if (!PrimaryKey.isEmpty()) {
			// Remove the last ", "
			PrimaryKey = PrimaryKey.substring(0, PrimaryKey.length() - 1);
			PrimaryKeyBind = PrimaryKeyBind.substring(0, PrimaryKeyBind.length() - 1);

			// Remove the last " AND "
			DeltaPKColList = DeltaPKColList.substring(0, DeltaPKColList.length() - 5);

			// Delta SELECT Script with OUTER JOIN and IS NULL
			//DeltaSelectScript = TableSelectScript + " LEFT OUTER JOIN " + FullDeltaTableName + " B ON " + DeltaPKColList
			//		+ " WHERE " + FirstKeyCol + " IS NULL AND " + WHERECriteria;
			//TODO Work on the Delta Logic, Probably not gonna happen! 
			DeltaSelectScript = TableSelectScript + " WHERE " + WHERECriteria + " " + AdditionalCriteria;

			DeltaTableScript = "CREATE TABLE IF NOT EXISTS " + FullDeltaTableName + "("
					+ DeltaColList.substring(0, DeltaColList.length() - 1) + ") engine=MyISAM";

			// Done this after already used in the previous statement
			TableSelectScript +=  " WHERE " + WHERECriteria + " " + AdditionalCriteria;
			//System.out.println(TableSelectScript);
			
			//This will be handled on the main calling class...
			MyDeltaScripts.add("CREATE DATABASE IF NOT EXISTS " + DeltaDBName);
			MyDeltaScripts.add(DeltaTableScript);
		}
	}

	public void setConstraints() {
		// NO Table Constraints in MySQL
		MyConstraints.clear();
	}

	public void setPrimaryKeys() {
		String ScriptSQL;
		Statement oStatement;
		ResultSet oResultSet;

		ScriptSQL = "SELECT CONSTRAINT_NAME, GROUP_CONCAT(COLUMN_NAME ORDER BY ORDINAL_POSITION SEPARATOR ', ') AS ColList FROM information_schema.key_column_usage "
				+ "WHERE TABLE_SCHEMA = '" + SchemaName + "' AND TABLE_NAME = '" + TableName
				+ "' AND CONSTRAINT_NAME = 'PRIMARY' GROUP BY CONSTRAINT_NAME";

		try {
			oStatement = oCon.createStatement();
			oResultSet = oStatement.executeQuery(ScriptSQL);

			while (oResultSet.next()) {
				MyPrimaryKeyScript = "ALTER TABLE " + SchemaName + "." + TableName + " ADD CONSTRAINT " + TableName
						+ "_pk PRIMARY KEY (" + oResultSet.getString("ColList") + ")";
			}

			oResultSet.close();

			ScriptSQL = "SELECT COLUMN_NAME FROM information_schema.key_column_usage " + "WHERE TABLE_SCHEMA = '"
					+ SchemaName + "' AND TABLE_NAME = '" + TableName
					+ "' AND CONSTRAINT_NAME = 'PRIMARY' ORDER BY ORDINAL_POSITION";

			oResultSet = oStatement.executeQuery(ScriptSQL);

			while (oResultSet.next()) {
				MyPKCols.add(oResultSet.getString("COLUMN_NAME"));
			}

			oResultSet.close();
			oStatement.close();
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	public void setForeignKeys() {
		String ScriptSQL, FKScript = "";
		Statement oStatement;
		ResultSet oResultSet;

		ScriptSQL = "SELECT B.CONSTRAINT_NAME, A.TABLE_NAME, A.REFERENCED_TABLE_NAME, A.UPDATE_RULE, A.DELETE_RULE, GROUP_CONCAT(B.COLUMN_NAME ORDER BY ORDINAL_POSITION SEPARATOR ', ') AS FKColList, "
				+ "GROUP_CONCAT(B.REFERENCED_COLUMN_NAME ORDER BY ORDINAL_POSITION SEPARATOR ', ') AS REFColList FROM information_schema.REFERENTIAL_CONSTRAINTS A "
				+ "INNER JOIN information_schema.key_column_usage B ON A.CONSTRAINT_SCHEMA = B.TABLE_SCHEMA AND A.TABLE_NAME = B.TABLE_NAME AND A.CONSTRAINT_NAME = B.CONSTRAINT_NAME "
				+ "WHERE B.CONSTRAINT_SCHEMA = '" + SchemaName + "' AND B.TABLE_NAME = '" + TableName
				+ "' AND A.REFERENCED_TABLE_NAME IS NOT NULL AND "
				+ "A.CONSTRAINT_NAME != 'PRIMARY' AND REFERENCED_TABLE_SCHEMA IS NOT NULL "
				+ "GROUP BY B.CONSTRAINT_NAME, A.TABLE_NAME, A.REFERENCED_TABLE_NAME, A.UPDATE_RULE, A.DELETE_RULE";

		try {
			oStatement = oCon.createStatement();
			oResultSet = oStatement.executeQuery(ScriptSQL);

			while (oResultSet.next()) {
				FKScript = "ALTER TABLE " + FullTableName + " ADD CONSTRAINT " + oResultSet.getString("CONSTRAINT_NAME")
						+ " FOREIGN KEY (" + oResultSet.getString("FKColList") + ") " + "REFERENCES "
						+ oResultSet.getString("REFERENCED_TABLE_NAME") + "(" + oResultSet.getString("REFColList")
						+ ") ON DELETE " + oResultSet.getString("DELETE_RULE") + " ON UPDATE "
						+ oResultSet.getString("UPDATE_RULE");
				MyForeignKeys.add(FKScript);
			}

			oResultSet.close();
			oStatement.close();
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	public void setCheckConstraints() {
		// NO Table Check Constraints in MySQL
		MyCheckConstraints.clear();
	}

	public void setIndexes() {
		String ScriptSQL, IndexScript = "";
		Statement oStatement;
		ResultSet oResultSet;

		ScriptSQL = "SELECT CONSTRAINT_NAME, GROUP_CONCAT(COLUMN_NAME ORDER BY ORDINAL_POSITION SEPARATOR ', ') AS ColList FROM information_schema.key_column_usage "
				+ "WHERE TABLE_SCHEMA = '" + SchemaName + "' AND TABLE_NAME = '" + TableName
				+ "' AND CONSTRAINT_NAME != 'PRIMARY' AND "
				+ "REFERENCED_TABLE_SCHEMA IS NOT NULL GROUP BY CONSTRAINT_NAME";

		try {
			oStatement = oCon.createStatement();
			oResultSet = oStatement.executeQuery(ScriptSQL);

			while (oResultSet.next()) {
				IndexScript = "ALTER TABLE " + FullTableName + " ADD INDEX " + oResultSet.getString("CONSTRAINT_NAME")
						+ "(" + oResultSet.getString("ColList") + ")";
				MyIndexes.add(IndexScript);
			}

			oResultSet.close();
			oStatement.close();
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	public void setTriggers() {
		String ScriptSQL, TriggerScriptSQL, TriggerScript = "", FullTriggerName;
		Statement oStatement, TriggerScriptStmt;
		ResultSet oResultSet, TriggerScriptRs;

		ScriptSQL = "SELECT TRIGGER_SCHEMA, TRIGGER_NAME FROM information_schema.triggers "
				+ "WHERE EVENT_OBJECT_SCHEMA = '" + SchemaName + "'AND EVENT_OBJECT_TABLE = '" + TableName + "'";

		try {
			oStatement = oCon.createStatement();
			oResultSet = oStatement.executeQuery(ScriptSQL);

			while (oResultSet.next()) {
				TriggerScriptStmt = oCon.createStatement();
				FullTriggerName = "`" + oResultSet.getString("TRIGGER_SCHEMA") + "`.`" + oResultSet.getString("TRIGGER_NAME") + "`";
				TriggerScriptSQL = "SHOW CREATE TRIGGER " + FullTriggerName;
				//New Trigger Code
				TriggerScriptRs = TriggerScriptStmt.executeQuery(TriggerScriptSQL);

				//One Trigger row per Trigger name
				if (TriggerScriptRs.next()) {
					TriggerScript = TriggerScriptRs.getString(3);
					//Remove CREATE OR REPALCE replacement
					MyTriggers.add("SET SQL_MODE = '" + TriggerScriptRs.getString(2) + "'");
					MyTriggers.add("DROP TRIGGER IF EXISTS " + FullTriggerName);
					MyTriggers.add(TriggerScript);
					//System.out.println(Util.rPad("Reading Trigger Script " + FullTriggerName, 80, " ") + "--> [ OK ]");
				}

				TriggerScriptRs.close();
				TriggerScriptStmt.close();
			}

			oResultSet.close();
			oStatement.close();
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	public String getColumnList() {
		return SelectColumnList;
	}

	public String getRawColumnList() {
		return RawColumnList;
	}

	public String getColumnBindList() {
		return InsertBindList;
	}

	public String getTableScript() {
		return TableScript;
	}

	public void setRecordCount() {
		String ScriptSQL;
		Statement oStatement;
		ResultSet oResultSet;
		ScriptSQL = "SELECT COUNT(*) ROW_COUNT FROM (SELECT 1 FROM " + SchemaName + "." + TableName + " WHERE " + WHERECriteria + ") SubQ";

		try {
			oStatement = oCon.createStatement();
			oResultSet = oStatement.executeQuery(ScriptSQL);

			// ENGINE=INNODB DEFAULT CHARA1010CTER SET utf8 COLLATE utf8_bin ROW_FORMAT=DYNAMIC
			oResultSet.next();
			RowCount = oResultSet.getLong("ROW_COUNT");
			oResultSet.close();
			oStatement.close();
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	public void setDeltaRecordCount() {
		String ScriptSQL;
		Statement oStatement;
		ResultSet oResultSet;
		ScriptSQL = "SELECT COUNT(*) ROW_COUNT (SELECT 1 FROM " + SchemaName + "." + TableName + ")";

		try {
			oStatement = oCon.createStatement();
			oResultSet = oStatement.executeQuery(ScriptSQL);

			// ENGINE=INNODB DEFAULT CHARACTER SET utf8 COLLATE utf8_bin ROW_FORMAT=DYNAMIC
			oResultSet.next();
			DeltaRowCount = oResultSet.getLong("ROW_COUNT");

			oResultSet.close();
			oStatement.close();
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	public boolean getMigrationSkipped() {
		return isMigrationSkipped;
	}

	public long getRecordCount() {
		return RowCount;
	}

	public int getColumnCount() {
		return MyCol.getColumnList().size();
	}

	public String getCreateTableScript() {
		return TableScript;
	}

	public List<String> getDeltaTableScript() {
		return MyDeltaScripts;
	}

	public List<String> getTableIndexes() {
		return MyIndexes;
	}

	public List<String> getTableForeignKeys() {
		return MyForeignKeys;
	}

	public String getTablePrimaryKey() {
		return MyPrimaryKeyScript;
	}

	public List<String> getTableConstraints() {
		return MyConstraints;
	}

	public List<String> getCheckConstraints() {
		return MyCheckConstraints;
	}

	public List<String> getTriggers() {
		return MyTriggers;
	}

	public String getTableName() {
		return TableName;
	}

	public String getSchemaName() {
		return SchemaName;
	}

	public String getFullTableName() {
		return FullTableName;
	}

	public List<String> getPrimaryKeyList() {
		return MyPKCols;
	}

	public String getTableSelectScript() {
		return TableSelectScript;
	}

	public String getDeltaSelectScript() {
		return DeltaSelectScript;
	}

	public String getTargetInsertScript() {
		return TargetInsertScript;
	}

	public long getDeltaRecordCount() {
		return DeltaRowCount;
	}

	public boolean hasTableMigrated() {
		return ExodusProgress.hasTableMigrationCompleted(getSchemaName(), getTableName());
	}

	public String getMD5DSelectScript() {
		return SelectMD5 + " LIMIT " + getMD5Limit();
	}

	public long getMD5Limit() {
		long MD5Limit;
		MD5Limit = (RowCount * MD5SelectionPercent / 100);
		return MD5Limit;
	}
}
