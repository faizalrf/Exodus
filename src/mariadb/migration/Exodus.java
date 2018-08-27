package mariadb.migration;
import mariadb.migration.mysql.*;
public class Exodus {
    public static void main(String[] args) {
    	StartExodus();
    }
    
    public static void StartExodus() {
        MySQLConnect SourceCon = new MySQLConnect(Util.getPropertyValue("SourceDB"));
        MariaDBConnect TargetCon = new MariaDBConnect(Util.getPropertyValue("TargetDB"));
        
        ExodusProgress.CreateProgressLogTable();
        
        MySQLDatabase MyDB = new MySQLDatabase(SourceCon.getDBConnection());
        for (SchemaHandler oSchema : MyDB.getSchemaList()) {
            for (TableHandler Tab : oSchema.getTables()) {
                if (!Tab.getMigrationSkipped()) {
                	MySQLExodusSingle SingleTable = new MySQLExodusSingle(Tab, "MIGRATE");
                    SingleTable.start();
                    System.out.println("Thread Started!");
                }
            }
        }
        
        SourceCon.DisconnectDB();
        TargetCon.DisconnectDB();
    }
}
