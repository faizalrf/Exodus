package mariadb.migration.mysql;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

import mariadb.migration.DBConHandler;
import mariadb.migration.DBCredentialsReader;
import mariadb.migration.DataSources;
import mariadb.migration.Util;

public class MySQLConnect implements DBConHandler {
    private String dbUserName;
    private String dbPassword;
    private String dbUrl;
    private String ConnectionName;
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

    public Connection ConnectDB() {
        try {
            dbConnection = DriverManager.getConnection(dbUrl, dbUserName, dbPassword);
            dbConnection.setAutoCommit(false);
            //System.out.println("Successfully connected to : " + dbUrl);
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
            //System.out.println("*** Connection Closed ***");
        } catch (SQLException e) {
            System.out.println("****** ERROR ******");
            System.out.println("Unable to Disconnect from : " + ConnectionName + " -> " + dbUrl);
            e.printStackTrace();
            System.out.println("- END -");
        }
    }
}
