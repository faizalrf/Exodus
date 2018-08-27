package mariadb.migration;
import java.sql.Connection;
public interface DBConHandler {
    Connection ConnectDB();
    public Connection getDBConnection();
    void DisconnectDB();
}
