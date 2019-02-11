package mariadb.migration;

import java.util.List;

public interface SourceCodeHandler {
    void setSourceScript();
    void setSourceType(String iObjectType);
    List<String> getSourceScript();
    String getSQLMode();
    String getSourceType();
    String getFullObjectName();
}
