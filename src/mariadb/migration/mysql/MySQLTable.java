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
					RawColumnList, InsertBindList, PrimaryKey, PrimaryKeyBind, TableSelectScript, TargetInsertScript, MyPrimaryKeyScript, AdditionalCriteria,
					DeltaDBName;
	private ColumnCollectionHandler MyCol;

	private List<String> MyPKCols = new ArrayList<String>();
	private List<String> MyIndexes = new ArrayList<String>();
	private List<String> MyForeignKeys = new ArrayList<String>();
	private List<String> MyTriggers = new ArrayList<String>();
	private List<String> MyConstraints = new ArrayList<String>();
	private List<String> MyCheckConstraints = new ArrayList<String>();
	private List<String> MyDeltaScripts = new ArrayList<String>();
	private Connection oCon;
	private long RowCount, DeltaRowCount;
	private boolean isMigrationSkipped = false;

	public MySQLTable(Connection iCon, String iSchemaName, String iTableName) {
		oCon = iCon;
		SchemaName = iSchemaName;
		TableName = iTableName;
		FullTableName = SchemaName + "." + TableName;
		DeltaDBName = "ExodusDb";

		//Becasue Schema is common, so delta table names should be prefixed with the Schema Names
		FullDeltaTableName = DeltaDBName + "." + SchemaName.toLowerCase() + "_" + TableName.toLowerCase();
		
		AdditionalCriteria = Util.getPropertyValue(FullTableName + ".AdditionalCriteria");

		if (AdditionalCriteria.isEmpty()) {
			AdditionalCriteria = "1=1";
		}

		setMigrationSkipped();
		if (isMigrationSkipped) {
			TableScript = "";
			RowCount = 0;
			DeltaRowCount = 0;
			SelectColumnList = "";
			InsertBindList = "";
			PrimaryKey = "";
			PrimaryKeyBind = "";
		} else {
			MyCol = new MySQLColumnsCollection(iCon, iSchemaName, iTableName);
			setTableScript();
			setRecordCount();
			setColumnList();
			setConstraints();
			setPrimaryKeys();
			setForeignKeys();
			setCheckConstraints();
			setIndexes();
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

			oStatement.close();
			oResultSet.close();
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
				//System.out.println(TableScript);
			}
			
			oStatement.close();
			oResultSet.close();
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

		System.out.print(Util.rPad("Parsing Table Structure " + FullTableName, 61, " ") + "--> ");
		for (ColumnHandler Col : MyCol.getColumnList()) {
			ColumnName = Col.getName();
			ColumnExpression = "A." + Col.getName();

			SelectColumnList += ColumnExpression + ",";
			RawColumnList += ColumnName + ",";
			InsertBindList += "?,";

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
			//System.out.print("*");
		}
		System.out.print(Util.rPad("COLUMNS [" + Util.lPad(String.valueOf(MyCol.getColumnList().size()), 3, " ") + "]", 14, " "));
		System.out.print("-->  ROWS [" + Util.lPad(Util.numberFormat.format(RowCount), 13, " ") + "]\n");

		if (!SelectColumnList.isEmpty()) {
			SelectColumnList = SelectColumnList.substring(0, SelectColumnList.length() - 1);
			InsertBindList = InsertBindList.substring(0, InsertBindList.length() - 1);
			RawColumnList = RawColumnList.substring(0, RawColumnList.length() - 1);
			TableSelectScript = "SELECT " + SelectColumnList + " FROM " + FullTableName + " A";
			TargetInsertScript = "INSERT INTO " + FullTableName + "(" + RawColumnList + ") VALUES (" + InsertBindList + ")";
		}

		if (!PrimaryKey.isEmpty()) {
			// Remove the last ", "
			PrimaryKey = PrimaryKey.substring(0, PrimaryKey.length() - 1);
			PrimaryKeyBind = PrimaryKeyBind.substring(0, PrimaryKeyBind.length() - 1);

			// Remove the last " AND "
			DeltaPKColList = DeltaPKColList.substring(0, DeltaPKColList.length() - 5);

			// Delta SELECT Script with OUTER JOIN and IS NULL
			DeltaSelectScript = TableSelectScript + " LEFT OUTER JOIN " + FullDeltaTableName + " B ON " + DeltaPKColList
					+ " WHERE " + FirstKeyCol + " IS NULL AND " + AdditionalCriteria;
					// + " ORDER BY " + PrimaryKey;
			DeltaTableScript = "CREATE TABLE IF NOT EXISTS " + FullDeltaTableName + "("
					+ DeltaColList.substring(0, DeltaColList.length() - 1) + ") engine=MyISAM";

			// Done this after already used in the previous statement
			TableSelectScript +=  " WHERE " + AdditionalCriteria;
			// + " ORDER BY " + PrimaryKey;

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

			oStatement.close();
			oResultSet.close();
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

			oStatement.close();
			oResultSet.close();
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

			oStatement.close();
			oResultSet.close();
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	public void setTriggers() {
		String ScriptSQL, TriggerScript = "";
		Statement oStatement;
		ResultSet oResultSet;

		ScriptSQL = "SELECT A.TRIGGER_SCHEMA, A.TRIGGER_NAME, A.ACTION_STATEMENT, A.ACTION_ORIENTATION, A.ACTION_TIMING, A.EVENT_MANIPULATION, CONCAT('\"', SUBSTR(DEFINER, 1, INSTR(DEFINER, '@')-1), '\"@\"', SUBSTR(DEFINER, INSTR(DEFINER, '@')+1), '\"') DEFINER, "
				+ "COALESCE(CASE WHEN A.ACTION_ORDER = 1 THEN "
				+ "CONCAT('FOLLOWS ', (SELECT B.TRIGGER_NAME FROM information_schema.triggers B "
				+ "WHERE B.EVENT_OBJECT_SCHEMA = A.EVENT_OBJECT_SCHEMA AND B.EVENT_OBJECT_TABLE = A.EVENT_OBJECT_TABLE "
				+ "AND B.ACTION_ORDER = A.ACTION_ORDER - 1), '') ELSE '' END, '') FOLLOWS FROM information_schema.triggers A "
				+ "WHERE A.EVENT_OBJECT_SCHEMA = '" + SchemaName + "'AND A.EVENT_OBJECT_TABLE = '" + TableName
				+ "' ORDER BY A.ACTION_ORDER";

		try {
			oStatement = oCon.createStatement();
			oResultSet = oStatement.executeQuery(ScriptSQL);

			while (oResultSet.next()) {
				TriggerScript = "CREATE DEFINER=" + oResultSet.getString("DEFINER") + " TRIGGER "
						+ oResultSet.getString("TRIGGER_SCHEMA") + "." + oResultSet.getString("TRIGGER_NAME") + " "
						+ oResultSet.getString("ACTION_TIMING") + " " + oResultSet.getString("EVENT_MANIPULATION")
						+ " ON " + FullTableName + " FOR EACH " + oResultSet.getString("ACTION_ORIENTATION") + " "
						+ oResultSet.getString("FOLLOWS") + "\n" + oResultSet.getString("ACTION_STATEMENT");
				MyTriggers.add(TriggerScript);
			}

			oStatement.close();
			oResultSet.close();
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
		ScriptSQL = "SELECT COUNT(*) ROW_COUNT FROM (SELECT 1 FROM " + SchemaName + "." + TableName + " WHERE " + AdditionalCriteria + ") SubQ";

		try {
			oStatement = oCon.createStatement();
			oResultSet = oStatement.executeQuery(ScriptSQL);

			// ENGINE=INNODB DEFAULT CHARA1010CTER SET utf8 COLLATE utf8_bin ROW_FORMAT=DYNAMIC
			oResultSet.next();
			RowCount = oResultSet.getLong("ROW_COUNT");
			oStatement.close();
			oResultSet.close();
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

			oStatement.close();
			oResultSet.close();
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
}
