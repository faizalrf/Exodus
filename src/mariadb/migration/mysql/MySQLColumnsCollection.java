package mariadb.migration.mysql;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import mariadb.migration.ColumnHandler;
import mariadb.migration.ColumnCollectionHandler;

public class MySQLColumnsCollection implements ColumnCollectionHandler {
	private Connection oCon;
	private String SchemaName, TableName, ColCollectionScript;
	private List<ColumnHandler> ColumnsList = new ArrayList<ColumnHandler>();
	
	public MySQLColumnsCollection(Connection iCon, String iSchemaName, String iTableName) {
		oCon = iCon;
		SchemaName = iSchemaName;
		TableName = iTableName;
		setColumnsList();
	}

	public void setColumnsList() {
        String ScriptSQL;
        Statement oStatement;
        ResultSet oResultSet;
        ScriptSQL = "SELECT COLUMN_NAME, ORDINAL_POSITION, COALESCE(COLUMN_DEFAULT, '') COLUMN_DEFAULT, (CASE WHEN IS_NULLABLE = 'YES' THEN 'null' ELSE 'not null' END) AS IS_NULLABLE, DATA_TYPE, COLUMN_TYPE, " +
        					"NUMERIC_PRECISION, NUMERIC_SCALE, CHARACTER_SET_NAME, COLLATION_NAME, (CASE WHEN COLUMN_KEY = 'PRI' THEN 1 ELSE 0 END) AS ISPK, " + 
        					"COLUMN_COMMENT, EXTRA FROM INFORMATION_SCHEMA.COLUMNS " +
        					"WHERE TABLE_SCHEMA = '" + SchemaName + "' AND TABLE_NAME = '" + TableName + "' ORDER BY ORDINAL_POSITION";
        
        try {
        	oStatement = oCon.createStatement();
        	oResultSet = oStatement.executeQuery(ScriptSQL);
            ColumnsList.clear();
            while (oResultSet.next()) {
                MySQLColumn MySQLCol = new MySQLColumn();
                MySQLCol.setName(oResultSet.getString("COLUMN_NAME"));
                MySQLCol.setPosition(oResultSet.getInt("ORDINAL_POSITION"));
                MySQLCol.setLength(oResultSet.getInt("NUMERIC_PRECISION"));
                MySQLCol.setScale(oResultSet.getInt("NUMERIC_SCALE"));
                MySQLCol.setDataType(oResultSet.getString("DATA_TYPE"));
                MySQLCol.setNulls(oResultSet.getString("IS_NULLABLE"));
                MySQLCol.setDefaultValue(oResultSet.getString("COLUMN_DEFAULT"));
                MySQLCol.setColumnDataType(oResultSet.getString("COLUMN_TYPE"));
                MySQLCol.setCharacterSet(oResultSet.getString("CHARACTER_SET_NAME"));
                MySQLCol.setCollation(oResultSet.getString("COLLATION_NAME"));
                MySQLCol.setIsPrimaryKey(oResultSet.getBoolean("ISPK"));
                MySQLCol.setSpecialText(oResultSet.getString("EXTRA"));
                MySQLCol.setComment(oResultSet.getString("COLUMN_COMMENT"));
                
            	ColumnsList.add(MySQLCol);
            	
			}
			setSQLScript();
            oStatement.close();
            oResultSet.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
	}
	
	public void setSQLScript() {
		ColCollectionScript="";
		for ( ColumnHandler MySQLCol : ColumnsList ) {
			ColCollectionScript += MySQLCol.getColumnScript() + ",";
		}
		if (!ColCollectionScript.isEmpty()) {
			ColCollectionScript = ColCollectionScript.substring(0, ColCollectionScript.length()-1);
		}
	}
	
	public List<ColumnHandler> getColumnList() {
		return ColumnsList;
	}

	public void convertDataType() {

	}

	public String getSQLScript() {
		return ColCollectionScript;
	}

}
