package mariadb.migration.mysql;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

import mariadb.migration.SchemaHandler;
import mariadb.migration.TableHandler;
import mariadb.migration.ViewHandler;
import mariadb.migration.SourceCodeHandler;

public class MySQLSchema implements SchemaHandler {
	private String SchemaName, SchemaScript;
	private Connection SourceCon;
	
	//Object Array to store Table OBJECTs for the Schema/Database
	private List<TableHandler> SchemaTables = new ArrayList<TableHandler>(); 
	private List<ViewHandler> SchemaViews = new ArrayList<ViewHandler>();
	private List<SourceCodeHandler> StoredProcedures = new ArrayList<SourceCodeHandler>();
	private List<String> SchemaSequences = new ArrayList<String>();
	
    public MySQLSchema(Connection iSourceCon, String iSchemaName) {
        System.out.println("\n\n------------------------------------------------------");
        System.out.println("- Starting " + iSchemaName + " DB Schema Discovery");
        System.out.println("------------------------------------------------------");

        SourceCon = iSourceCon;
    	SchemaName = iSchemaName;
        setSchema();
    }

    private void setSchema() {
        String ScriptSQL ;
        Statement oStatement;
        ResultSet oResultSet;

        ScriptSQL = "SHOW CREATE DATABASE " + SchemaName;
        
        try {
        	oStatement = SourceCon.createStatement();
        	oResultSet = oStatement.executeQuery(ScriptSQL);
            
            if (oResultSet.next()) {
                SchemaScript = oResultSet.getString(2);
                SchemaScript = SchemaScript.replace("CREATE DATABASE ", "CREATE DATABASE IF NOT EXISTS ");

            	//Read the Tables List for the Current Schema, this will also handle VIEWS in the schema.
            	setTables();
            	            	
            	//Read the Sequence List for the Current Schema.
            	setSequencesList();
            	
            	//Read the Stored Procedures List for the Current Schema.
            	setSourceCodeList();
            	
            }
            oStatement.close();
            oResultSet.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public String getSchemaName() {
    	return SchemaName;
    }
        
    public String getSchemaScript() {
    	return SchemaScript;
    }
    
    public void setTables() {
        String TablesSQL;
        Statement oStatement;
        ResultSet oResultSet;
        TablesSQL = "SELECT TABLE_NAME, TABLE_TYPE FROM INFORMATION_SCHEMA.TABLES " +
        				"WHERE TABLE_SCHEMA='" + SchemaName + "' AND TABLE_TYPE IN ('BASE TABLE', 'VIEW')";

        try {
        	oStatement = SourceCon.createStatement();
        	oResultSet = oStatement.executeQuery(TablesSQL);
            
            while (oResultSet.next()) {
                //If the object is a VIEW add to View's List, else to Table's List
                if (oResultSet.getString("TABLE_TYPE").equals("VIEW")) {
                    SchemaViews.add(new MySQLView(SourceCon, SchemaName, oResultSet.getString("TABLE_NAME")));
                } else {
                    SchemaTables.add(new MySQLTable(SourceCon, SchemaName, oResultSet.getString("TABLE_NAME")));
                }
            }
            oStatement.close();
            oResultSet.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

public void setSequencesList() { /* No Sequences in MySQL */ }

    public void setSourceCodeList() {
        String SourceSQL;
        Statement oStatement;
        ResultSet oResultSet;
        SourceSQL = "SELECT ROUTINE_NAME, ROUTINE_TYPE FROM INFORMATION_SCHEMA.ROUTINES " +
        				"WHERE ROUTINE_SCHEMA='" + SchemaName + "' AND ROUTINE_TYPE IN ('PROCEDURE', 'FUNCTION')";

        try {
        	oStatement = SourceCon.createStatement();
        	oResultSet = oStatement.executeQuery(SourceSQL);
            
            while (oResultSet.next()) {
                //ROUTINE_TYPE is either PROCEDURE or FUNCTION!
                StoredProcedures.add(new MySQLSourceCode(SourceCon, SchemaName, oResultSet.getString("ROUTINE_NAME"), oResultSet.getString("ROUTINE_TYPE")));
            }
            oStatement.close();
            oResultSet.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
    
    public List<TableHandler> getTables() {
    	return SchemaTables;
    }
    
    public List<ViewHandler> getViewsList() {
    	return SchemaViews;
    }
    
    public List<String> getSequencesList() {
    	return SchemaSequences;
    }
    
    public List<SourceCodeHandler> getSourceCodeList() {
    	return StoredProcedures;
    }
}
