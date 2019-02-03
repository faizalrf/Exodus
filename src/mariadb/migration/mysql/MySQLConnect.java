package mariadb.migration.mysql;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import mariadb.migration.DBConHandler;
import mariadb.migration.DBCredentialsReader;
import mariadb.migration.DataSources;
import mariadb.migration.Util;

public class MySQLConnect implements DBConHandler {
    private String dbUserName;
    private String dbPassword;
    private String dbUrl;
    private String ConnectionName;
    private String SQLMode;
    private DBCredentialsReader UCR;
    private DataSources UC;
    private Connection dbConnection = null;

    public MySQLConnect(String iConnectionName) {
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
        dbUrl="jdbc:mysql://" + hostName + ":" + portNumber.toString() + "/" + dbName + "?" + Util.getPropertyValue("SourceConnectParams");;
        dbConnection = ConnectDB();
    }

    public Connection getDBConnection() { return dbConnection; }

    //TODO implement methods to store and reset the SQL modes
    public Connection ConnectDB() {
        try {
            dbConnection = DriverManager.getConnection(dbUrl, dbUserName, dbPassword);
            dbConnection.setAutoCommit(false);
            setSQLMode();
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
            System.out.println("Unable to Switch to DB : " + SchemaName + " -> " + dbUrl);
            e.printStackTrace();
        }
    }

    public void setSQLMode() {
        String ScriptSQL;
        Statement oStatement;
        ResultSet oResultSet;

        ScriptSQL = "SHOW GLOBAL VARIABLES LIKE 'SQL_MODE'";
        SQLMode = "";
        
        try {
        	oStatement = dbConnection.createStatement();
        	oResultSet = oStatement.executeQuery(ScriptSQL);
            
            if (oResultSet.next()) {
                SQLMode = "SET GLOBAL SQL_MODE = '" + oResultSet.getString(2) + "'";
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
}
