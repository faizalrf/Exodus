package mariadb.migration;

public interface SourceCodeHandler {
    void setSourceScript();
    void setSourceType(String iObjectType);
    String getSourceScript();
    String getSourceType();
}
