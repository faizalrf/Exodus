package mariadb.migration;
import java.util.List;
public interface DatabaseHandler {
	void setSchemaList();
	void setUserList();
    List<SchemaHandler> getSchemaList();
    List<UserHandler> getUserList();
}
