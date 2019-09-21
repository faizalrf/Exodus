package mariadb.migration;

import java.util.List;
public interface TableHandler {
    void setMigrationSkipped();
    void setConstraints();
    void setPrimaryKeys();
    void setForeignKeys();
    void setCheckConstraints();
    void setIndexes();
    void setTriggers();
    void setRecordCount();
    void setDeltaRecordCount();
    void setTableScript();
    
    boolean getMigrationSkipped();    
    long getRecordCount();
    int getColumnCount();
    long getDeltaRecordCount();
    String getTableName();
    String getSchemaName();
    String getFullTableName();
    String getCreateTableScript();
    String getTableSelectScript();
    String getDeltaSelectScript();
    String getTargetInsertScript();
    List<String> getTableIndexes();
    List<String> getTableForeignKeys();
    String getColumnList();
    String getRawColumnList();
    List<String> getPrimaryKeyList();
    String getTableScript();
    List<String> getDeltaTableScript();
    List<String> getTableConstraints();
    List<String> getCheckConstraints();
    List<String> getTriggers();
    public boolean hasTableMigrated();
    public String getMD5DSelectScript();    
}
