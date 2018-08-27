package mariadb.migration;
public interface ColumnHandler {

    void setName(String pName);
    String getName();
    void setPosition(int pPosition);
    int getPosition();
    void setDataType(String pDataType);
    String getDataType();
    void setColumnDataType(String pColumnDataType);
    String getColumnDataType();
    void setLength(int pLength);
    int getLength();
    void setScale(int pScale);
    int getScale();
    void setDefaultValue(String pDefaultValue);
    String getDefaultValue();
    void setNulls(String pNulls);
    String getNulls();
    void setSpecialText(String pSpecialText);
    String getSpecialText();
    void setIdentity(String pIdentity);
    String getIdentity();
    void setCharacterSet(String pCharacterSet);
    String getCharacterSet();
    void setCollation(String pCollation);
    String getCollation();
    void setComment(String pComment);
    String getComment();
    void setIsPrimaryKey(boolean pIsPrimaryKey);
    boolean getIsPrimaryKey();
    void setColumnScript();
    String getColumnScript();
}
