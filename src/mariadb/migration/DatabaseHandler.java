package mariadb.migration;
import java.util.List;
public interface DatabaseHandler {
	void setSchemaList();
    void setUserList();
    void setCreateUserScript(String UserName);
    void setUserGrantScript(String UserName);
    List<String> getCreateUserScript();
    List<String> getUserGrantsScript();
    List<SchemaHandler> getSchemaList();
}
