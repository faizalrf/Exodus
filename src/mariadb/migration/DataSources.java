package mariadb.migration;
public class DataSources {
    private String dbType;
    private String hostName;
    private Integer portNumber;
    private String dbName;
    private String dbUserName;
    private String dbPassword;
    
    public DataSources() {
        dbType="";
        hostName="";
        portNumber=0;
        dbName="";
        dbUserName="";
        dbPassword="";
    }
    
    public void setHostName(String pHostName) { hostName = pHostName; }
    public String getHostName() { return hostName; }
    
    public void setPortNumber(Integer pPortNumber) { portNumber = pPortNumber; }
    public Integer getPortNumber() { return portNumber; }
    
    public void setDBName(String pDBName) { dbName = pDBName; }
    public String getDBName() { return dbName; }
    
    public void setUserName(String pDBUserName) { dbUserName = pDBUserName; }
    public String getUserName() { return dbUserName; }
    
    public void setPassword(String pDBPassword) { dbPassword = pDBPassword; }
    public String getPassword() { return dbPassword; }

    public void setDBType(String pDBType) { dbType = pDBType; }
    public String getDBType() { return dbType; }
    
}
