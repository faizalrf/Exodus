package mariadb.migration;

import java.util.List;

public interface SchemaHandler {
    String getSchemaName();    
    String getSchemaScript();
    
    void setTables();
    void setSequencesList();
    void setSourceCodeList();

    List<TableHandler> getTables();
    List<ViewHandler> getViewsList();
    List<SourceCodeHandler> getSourceCodeList();
    List<String> getSequencesList();
}
