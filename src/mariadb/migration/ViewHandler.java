package mariadb.migration;

import java.util.List;

public interface ViewHandler {
    void setViewScript();
    List<String> getViewScript();
    String getFullViewName();
}
