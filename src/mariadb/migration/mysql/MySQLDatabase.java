package mariadb.migration.mysql;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

import mariadb.migration.SchemaHandler;
import mariadb.migration.DatabaseHandler;
import mariadb.migration.Util;
import mariadb.migration.Logger;

public class MySQLDatabase implements DatabaseHandler {
    private Connection SourceCon;
    private List<SchemaHandler> SchemaList = new ArrayList<SchemaHandler>();
    private List<String> UserScript = new ArrayList<String>();
    private List<String> UserGrantsScript = new ArrayList<String>();
    
    public MySQLDatabase(Connection iCon) {
        SourceCon = iCon;
        if (SourceCon != null) {
            setSchemaList();
            setUserList();
            writeUserScript();
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
        	oStatement = SourceCon.createStatement();
        	oResultSet = oStatement.executeQuery(ScriptSQL);
            String SchemaName="";
           	SchemaList.clear();
            
            while (oResultSet.next()) {
            	SchemaName = oResultSet.getString("SCHEMA_NAME");
            	SchemaList.add(new MySQLSchema(SourceCon, SchemaName));
            }
            oStatement.close();
            oResultSet.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
    
    public void setUserList() {
        String ScriptSQL ;
        Statement oStatement;
        ResultSet oResultSet;

        ScriptSQL = "SELECT CONCAT(user, '@''', host, '''') User FROM mysql.user WHERE " + Util.getPropertyValue("UsersToMigrate");
        
        try {
        	oStatement = SourceCon.createStatement();
        	oResultSet = oStatement.executeQuery(ScriptSQL);
            String UserName="";
            
            //Get User's list and its CREATE / GRANT scripts
            while (oResultSet.next()) {
            	UserName = oResultSet.getString("User");
                setCreateUserScript(UserName);
            }
            oStatement.close();
            oResultSet.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void setCreateUserScript(String UserName) {
        String ScriptSQL, CreateUserScript;
        Statement oStatement;
        ResultSet oResultSet;

        ScriptSQL = "SHOW CREATE USER " + UserName;
        
        try {
        	oStatement = SourceCon.createStatement();
        	oResultSet = oStatement.executeQuery(ScriptSQL);
            
            if (oResultSet.next()) {
                CreateUserScript = oResultSet.getString(1);
                CreateUserScript = CreateUserScript.substring(0, CreateUserScript.lastIndexOf("' ")+1);
                CreateUserScript = CreateUserScript.replace("CREATE USER", "CREATE USER IF NOT EXISTS");
                UserScript.add(CreateUserScript);
                setUserGrantScript(UserName);
            }
            oStatement.close();
            oResultSet.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void setUserGrantScript(String UserName) {
        String ScriptSQL;
        Statement oStatement;
        ResultSet oResultSet;

        ScriptSQL = "SHOW GRANTS FOR " + UserName;
        
        try {
        	oStatement = SourceCon.createStatement();
        	oResultSet = oStatement.executeQuery(ScriptSQL);
            
            while (oResultSet.next()) {
                UserGrantsScript.add(oResultSet.getString(1));
            }
            oStatement.close();
            oResultSet.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public List<String> getCreateUserScript() {
        return UserScript;
    }
    
    //TODO write logic to use User Grants Sctript and execute on the DB after Table's migration
    public List<String> getUserGrantsScript() {
        return UserGrantsScript;
    }

    public List<SchemaHandler> getSchemaList() {
        return SchemaList;
    }

    private void writeUserScript() {
        //Parse Through the Users list and Write to a Users.sql file.
        new Logger(Util.getPropertyValue("DDLPath") + "/" + "Users.sql", "#Users To Migrate\n", false, false);
        for (String SqlStr : UserScript) {
            new Logger(Util.getPropertyValue("DDLPath") + "/" + "Users.sql", SqlStr + ";", true, false);
        }

        //Write User Grants separately to the DDL script
        for (String SqlStr : UserGrantsScript) {
            new Logger(Util.getPropertyValue("DDLPath") + "/" + "Users.sql", SqlStr + ";", true, false);
        }
    }
}
