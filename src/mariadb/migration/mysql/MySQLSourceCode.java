package mariadb.migration.mysql;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import mariadb.migration.SourceCodeHandler;

public class MySQLSourceCode implements SourceCodeHandler {
	private String SchemaName, ObjectName, ObjectType, SourceCodeScript, SqlMode, FullObjectName;
	private Connection oCon;

	public MySQLSourceCode(Connection iCon, String iSchemaName, String iObjectName, String iObjectType) {
		oCon = iCon;
		SchemaName = iSchemaName;
        ObjectName = iObjectName;
        ObjectType = iObjectType;
        FullObjectName = SchemaName + "." + ObjectName;
        setSourceScript();
	}

    public void setSourceScript() {
        String ScriptSQL;
        
		Statement oStatement;
		ResultSet oResultSet;
		ScriptSQL = "SHOW CREATE " + ObjectType + " " + FullObjectName;
		
		try {
			oStatement = oCon.createStatement();
			oResultSet = oStatement.executeQuery(ScriptSQL);
			if (oResultSet.next()) {
				//Read and append schema name before the Procedure/Function name, can be enclosed between `` or ""
				SqlMode="SET SQL_MODE = '" + oResultSet.getString(2) + "'";
				SourceCodeScript = oResultSet.getString(3).replace("CREATE ", "CREATE OR REPLACE ").replace(" `"
										+ObjectName+"`", " `" + SchemaName + "`." + "`"+ObjectName+"`" ).replace(" \""
										+ObjectName+"\"", " \"" + SchemaName + "\"." + "\""+ObjectName+"\"" );
			}

			oResultSet.close();
			oStatement.close();
		} catch (SQLException e) {
			e.printStackTrace();
		}
    }

    public void setSourceType(String iObjectType) {
        ObjectType = iObjectType;
    }

    public String getSourceScript() {
        return SourceCodeScript;
    }

	public String getSQLMode() {
        return SqlMode;
    }

    public String getSourceType() {
        return ObjectType;
    }
}
