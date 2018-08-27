package mariadb.migration.mysql;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

import mariadb.migration.SchemaHandler;
import mariadb.migration.DatabaseHandler;
import mariadb.migration.UserHandler;
import mariadb.migration.Util;

public class MySQLDatabase implements DatabaseHandler {
    private Connection oCon;
    private List<SchemaHandler> oSchemaList = new ArrayList<SchemaHandler>();
    private List<UserHandler> oUserList = new ArrayList<UserHandler>();
    
    public MySQLDatabase(Connection iCon) {
        oCon = iCon;
        if (oCon != null) {
            setSchemaList();
        } else {
            System.out.println("Connection Not Available!");
        }
    }

    public void setSchemaList() {
        String ScriptSQL ;
        Statement oStatement;
        ResultSet oResultSet;

        ScriptSQL = "SELECT SCHEMA_NAME FROM INFORMATION_SCHEMA.SCHEMATA WHERE " + Util.getPropertyValue("DatabaseToMigrate");
        
        try {
        	oStatement = oCon.createStatement();
        	oResultSet = oStatement.executeQuery(ScriptSQL);
            String SchemaName="";
           	oSchemaList.clear();
            
            while (oResultSet.next()) {
            	SchemaName = oResultSet.getString("SCHEMA_NAME");
            	oSchemaList.add(new MySQLSchema(oCon, SchemaName));
            }
            oStatement.close();
            oResultSet.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
    
    public void setUserList() {
    	oUserList = null;
    }

    public List<SchemaHandler> getSchemaList() {
        return oSchemaList;
    }

    public List<UserHandler> getUserList() {
    	return oUserList;
    }
}
