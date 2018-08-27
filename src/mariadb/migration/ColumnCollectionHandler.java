package mariadb.migration;

import java.util.List;

public interface ColumnCollectionHandler {
    void setColumnsList();
	void setSQLScript();
	List<ColumnHandler> getColumnList();
	void convertDataType();
    String getSQLScript();
    String getTest();
}
