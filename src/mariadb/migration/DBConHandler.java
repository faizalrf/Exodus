package mariadb.migration;

import java.sql.Connection;
public interface DBConHandler {
    Connection ConnectDB();
    Connection getDBConnection();
    void SetCurrentSchema(String SchemaName);
    void DisconnectDB();
}
