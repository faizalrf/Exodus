package mariadb.migration;

public interface ViewHandler {
    void setViewScript();
    String getViewScript();
}
