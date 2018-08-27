package mariadb.migration;

import java.util.List;

public interface SchemaHandler {
    String getSchemaName();    
    String getSchemaScript();
    
    void setTables();
    void setViewsList();
    void setSequencesList();
    void setStoredProceduresList();
    void setStoredFunctionsList();

    List<TableHandler> getTables();
    List<String> getViewsList();
    List<String> getSequencesList();
    List<String> getStoredProceduresList();
    List<String> getStoredFunctionsList();
}
