package mariadb.migration.mysql;

import mariadb.migration.ColumnHandler;
public class MySQLColumn implements ColumnHandler {
    private String ColumnName;
    private int Position;
    private String DataType;
    private String ColumnDataType;
    private int Length;
    private int Scale;
    private String DefaultValue;
    private String Nulls;
    private String SpecialText;
    private String Identity;
    private String CharacterSet;
    private String Collation;
    private String Comment;
    private String ColumnScript;
    private boolean isPrimaryKey;
    
    public MySQLColumn() {
    	ColumnName="";
        Position=0;
        DataType="";
        ColumnDataType="";
        Length=0;
        Scale=0;
        DefaultValue="";
        Nulls="";
        SpecialText="";
        Identity="";
        CharacterSet="";
        Collation="";
        Comment="";
        isPrimaryKey=false;
    	ColumnScript="";
    }
    public void setName(String pName) { ColumnName = pName; setColumnScript(); }
    public String getName() { return ColumnName; }
    public void setPosition(int pPosition) {Position = pPosition; setColumnScript(); }
    public int getPosition() { return Position;}
    public void setDataType(String pDataType) { DataType = pDataType; setColumnScript(); }
    public String getDataType() { return DataType; }
    public void setColumnDataType(String pColumnDataType) { ColumnDataType = pColumnDataType; setColumnScript(); }
    public String getColumnDataType() { return ColumnDataType; }
    public void setLength(int pLength) { Length = pLength; setColumnScript(); }
    public int getLength() { return Length; }
    public void setScale(int pScale) { Scale = pScale; setColumnScript(); }
    public int getScale() { return Scale; }
    public void setDefaultValue(String pDefaultValue) {	DefaultValue = pDefaultValue; setColumnScript(); }
    public String getDefaultValue() { return DefaultValue; }
    public void setNulls(String pNulls) { Nulls = pNulls; setColumnScript(); }
    public String getNulls() { return Nulls; }
    public void setSpecialText(String pSpecialText) { SpecialText = pSpecialText; setColumnScript(); }
    public String getSpecialText() { return SpecialText; }
    public void setIdentity(String pIdentity) { Identity = pIdentity; setColumnScript(); }
    public String getIdentity() { return Identity; }
    public void setCharacterSet(String pCharacterSet) { CharacterSet = pCharacterSet; setColumnScript(); }
    public String getCharacterSet() { return CharacterSet; }
    public void setCollation(String pCollation) { Collation = pCollation; setColumnScript(); }
    public String getCollation() { return Collation; }
    public void setComment(String pComment) { Comment = pComment; setColumnScript(); }
    public String getComment() { return Comment; }
    public void setIsPrimaryKey(boolean pIsPrimaryKey) { isPrimaryKey = pIsPrimaryKey; setColumnScript(); }
    public boolean getIsPrimaryKey() { return isPrimaryKey; }
    
    public void setColumnScript() {
        ColumnScript = ColumnName;
    	if (!ColumnDataType.isEmpty()) {
    		ColumnScript += " " + ColumnDataType + " " + Nulls;
    		if (!DefaultValue.isEmpty()) {
	    		if ((DataType.startsWith("date") || DataType.startsWith("time") || DefaultValue.startsWith("(") || DataType.startsWith("bit")) && (!DataType.contains("char") && !DataType.contains("text"))) {
                    //This is for DATE and TIMESTAMP DEFAULTS SET AS 0000-00-00
                    if (!DefaultValue.startsWith("0000")) {
                        ColumnScript += " default " + DefaultValue;
                    }
	    		} else {
	    			ColumnScript += " default '" + DefaultValue + "'";	    			
	    		}
    		}
    		if (!SpecialText.isEmpty()) {
    			ColumnScript += " " + SpecialText;
    			if (SpecialText.equals("auto_increment") && isPrimaryKey) {
    				ColumnScript += " key";
    			}
    			if (!Comment.isEmpty()) {
    				ColumnScript += " COMMENT='" + Comment + "'";
    			}
    		}
    	}
    }
    
    public String getColumnScript() {
    	return ColumnScript;
    }
}
