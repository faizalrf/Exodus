package mariadb.migration;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class MariaDBConnect implements DBConHandler {
    private String dbUserName;
    private String dbPassword;
    private String dbUrl;
    private String ConnectionName;
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
        dbUrl="jdbc:mariadb://" + hostName + ":" + portNumber.toString() + "/" + dbName + "?" + Util.getPropertyValue("TargetConnectParams");
        dbConnection = ConnectDB();
    }
    
    public Connection getDBConnection() { return dbConnection; }

	public Connection ConnectDB() {
        try {
            dbConnection = DriverManager.getConnection(dbUrl, dbUserName, dbPassword);            
            //dbConnection.setAutoCommit(false);
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
