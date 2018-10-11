package mariadb.migration;

public interface SourceCodeHandler {
    void setSourceScript();
    void setSourceType();
    String getSourceScript();
    String getSourceType();
}
