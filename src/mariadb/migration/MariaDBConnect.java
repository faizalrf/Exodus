package mariadb.migration;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class MariaDBConnect implements DBConHandler {
    private String dbUserName;
    private String dbPassword;
    private String dbUrl;
    private String ConnectionName;
    private String SQLMode;
    private String LocalSQLMode;
    private DBCredentialsReader UCR;
    private DataSources UC;
    public Connection dbConnection = null;

    public MariaDBConnect(String iConnectionName) {
        UC = new DataSources();
        ConnectionName = iConnectionName;
        
        UCR = new DBCredentialsReader("resources/dbdetails.xml", ConnectionName);
        UC = UCR.DBCredentials();

        String hostName;
        Integer portNumber;
        String dbName;

        hostName = UC.getHostName();
        portNumber = UC.getPortNumber();
        dbName = UC.getDBName();
        dbUserName = UC.getUserName();
        dbPassword = UC.getPassword();
        dbUrl=Util.getPropertyValue("TargetConnectPrefix") + hostName + ":" + portNumber.toString() + "/" + dbName + "?" + Util.getPropertyValue("TargetConnectParams");
        dbConnection = ConnectDB();
    }
    
    public Connection getDBConnection() { return dbConnection; }

	public Connection ConnectDB() {
        try {
            dbConnection = DriverManager.getConnection(dbUrl, dbUserName, dbPassword);            
            dbConnection.setAutoCommit(false);
            setSQLMode();
            ResetSQLMode();
        } catch (SQLException e) {
            System.out.println("****** ERROR ******");
            System.out.println("Unable to connect to : " + ConnectionName + " -> " + dbUrl);
            e.printStackTrace();
            System.out.println("- END -");
        }
        return dbConnection;
	}

	public void DisconnectDB() {
        try {
            dbConnection.close();
        } catch (SQLException e) {
            System.out.println("****** ERROR ******");
            System.out.println("Unable to Disconnect from : " + ConnectionName + " -> " + dbUrl);
            e.printStackTrace();
            System.out.println("- END -");
        }
    }

    //Switch To a different Schema
    public void SetCurrentSchema(String SchemaName) {
        try {
            dbConnection.setCatalog(SchemaName);
        } catch (SQLException e) {
            System.out.println("****** ERROR ******");
            System.out.println("Unable to Switch to DB: " + SchemaName + " -> " + dbUrl);
            e.printStackTrace();
        }
    }    
    public void setSQLMode() {
        String ScriptSQL;
        Statement oStatement;
        ResultSet oResultSet;
        SQLMode = "";
        LocalSQLMode = "";
        
        try {
            //Get the GLOBAL SQL_MODE value
            ScriptSQL = "SHOW GLOBAL VARIABLES LIKE 'SQL_MODE'";
            
            oStatement = dbConnection.createStatement();
        	oResultSet = oStatement.executeQuery(ScriptSQL);            
            if (oResultSet.next()) {
                SQLMode = "SET GLOBAL SQL_MODE = '" + oResultSet.getString(2) + "'";
            }

            //Get the Current Session SQL_MODE value
            ScriptSQL = "SHOW VARIABLES LIKE 'SQL_MODE'";
            
            oStatement = dbConnection.createStatement();
        	oResultSet = oStatement.executeQuery(ScriptSQL);
            if (oResultSet.next()) {
                LocalSQLMode = "SET SQL_MODE = '" + oResultSet.getString(2) + "'";
            }
            oStatement.close();
            oResultSet.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
    
    public String getSQLMode() {
        return SQLMode;
    }

    //Reset the SQL Mode to ''
    public void ResetSQLMode() {
        String ScriptSQL;
        Statement oStatement;
        
        ScriptSQL = "SET SQL_MODE = ''";
        
        try {            
            oStatement = dbConnection.createStatement();
            oStatement.execute(ScriptSQL);
            oStatement.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    //Revert the SQL Mode back to the Original
    public void RevertSQLMode() {
        Statement oStatement;
        
        try {            
            oStatement = dbConnection.createStatement();
            oStatement.execute(LocalSQLMode);
            oStatement.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
