package mariadb.migration.mysql;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import mariadb.migration.ViewHandler;

public class MySQLView implements ViewHandler {
	private String SchemaName, ViewName, FullViewName;
	private List<String> ViewScript = new ArrayList<String>();
	private Connection oCon;

	public MySQLView(Connection iCon, String iSchemaName, String iViewName) {
		oCon = iCon;
		SchemaName = iSchemaName;
        ViewName = iViewName;
        FullViewName = "`" + SchemaName + "`.`" + ViewName + "`";
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
				//Add SchemaName before the object name, can be enclosed between `` or ""
				//Remove CREATE OR REPALCE replacement
				//Changed the ViewScript to an Array List so that DROP statement can be added to the queue
				ViewScript.add("DROP VIEW IF EXISTS " + FullViewName);
				ViewScript.add(oResultSet.getString(2).replace(" `"+ViewName+"`", " `" 
								+ SchemaName + "`." + "`"+ViewName+"`").replace(" \""+ViewName+"\"", " \"" 
								+ SchemaName + "\"." + "\""+ViewName+"\""));
			}

			oResultSet.close();
			oStatement.close();
		} catch (SQLException e) {
			e.printStackTrace();
		}
    }

    public List<String> getViewScript() {
        return ViewScript;
    }
}
