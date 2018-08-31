package mariadb.migration;
import mariadb.migration.mysql.*;
public class Exodus {
    public static void main(String[] args) {
    	StartExodus();
    }
    
    public static void StartExodus() {
        MySQLConnect SourceCon = new MySQLConnect(Util.getPropertyValue("SourceDB"));
        MariaDBConnect TargetCon = new MariaDBConnect(Util.getPropertyValue("TargetDB"));
        MySQLDatabase MyDB = new MySQLDatabase(SourceCon.getDBConnection());
        
        for (SchemaHandler oSchema : MyDB.getSchemaList()) {
            if (Util.getPropertyValue("DryRun").equals("NO")) {
                //Create Migration Log Table in the Schema
                ExodusProgress.CreateProgressLogTable(oSchema.getSchemaName());
            }
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
