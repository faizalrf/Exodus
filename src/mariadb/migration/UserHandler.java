package mariadb.migration;

import java.util.List;
public interface UserHandler {
    String getUserName();
    String getSchemaName();
    List<String> getPrivileges();
}
