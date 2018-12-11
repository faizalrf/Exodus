package mariadb.migration.mysql;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import mariadb.migration.ViewHandler;

public class MySQLView implements ViewHandler {
	private String SchemaName, ViewName, ViewScript, FullViewName;
	private Connection oCon;

	public MySQLView(Connection iCon, String iSchemaName, String iViewName) {
		oCon = iCon;
		SchemaName = iSchemaName;
        ViewName = iViewName;
        FullViewName = SchemaName + "." + ViewName;
        setViewScript();
	}
    
    public void setViewScript() {
        String ScriptSQL;
        
		Statement oStatement;
		ResultSet oResultSet;
		ScriptSQL = "SHOW CREATE VIEW " + FullViewName;
		
		try {
			oStatement = oCon.createStatement();
			oResultSet = oStatement.executeQuery(ScriptSQL);
			if (oResultSet.next()) {
				ViewScript = oResultSet.getString(2).replace("CREATE", "CREATE OR REPLACE").replace(" `"+ViewName+"`", " `" + SchemaName + "`." + "`"+ViewName+"`" );
			}

			oResultSet.close();
			oStatement.close();
		} catch (SQLException e) {
			e.printStackTrace();
		}
    }

    public String getViewScript() {
        return ViewScript;
    }
}
