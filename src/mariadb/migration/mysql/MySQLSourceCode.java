package mariadb.migration.mysql;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import mariadb.migration.SourceCodeHandler;
import mariadb.migration.Util;

public class MySQLSourceCode implements SourceCodeHandler {
	private String SchemaName, ObjectName, ObjectType, SqlMode, FullObjectName;
	private List<String> SourceCodeScript = new ArrayList<String>();

	private Connection oCon;

	public MySQLSourceCode(Connection iCon, String iSchemaName, String iObjectName, String iObjectType) {
		oCon = iCon;
		SchemaName = iSchemaName;
        ObjectName = iObjectName;
        ObjectType = iObjectType;
        FullObjectName = "`" + SchemaName + "`.`" + ObjectName + "`";
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

				SourceCodeScript.add(SqlMode);
				SourceCodeScript.add("DROP " + ObjectType + " IF EXISTS " + FullObjectName);

				//Custom Function to replace " with ` from start to the place until ObjectName!
				SourceCodeScript.add(Util.ReplaceString(oResultSet.getString(3), '"', '`', ObjectName));
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

    public List<String> getSourceScript() {
        return SourceCodeScript;
    }

	public String getSQLMode() {
        return SqlMode;
    }

    public String getSourceType() {
        return ObjectType;
    }
}
