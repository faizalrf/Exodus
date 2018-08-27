package mariadb.migration.mysql;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

import mariadb.migration.SchemaHandler;
import mariadb.migration.TableHandler;

public class MySQLSchema implements SchemaHandler {
	private String SchemaName, SchemaScript;
	private Connection oCon;
	
	//Object Array to store Table OBJECTs for the Schema/Database
	private List<TableHandler> SchemaTables = new ArrayList<TableHandler>(); 
	private List<String> SchemaViews = new ArrayList<String>();
	private List<String> SchemaSequences = new ArrayList<String>();
	private List<String> StoredProcedures = new ArrayList<String>();
	private List<String> StoredFunctions = new ArrayList<String>();
	
    public MySQLSchema(Connection iCon, String iSchemaName) {
    	oCon = iCon;
    	SchemaName = iSchemaName;
    	setSchema();
    }

    private void setSchema() {
        String ScriptSQL ;
        Statement oStatement;
        ResultSet oResultSet;

        ScriptSQL = "SELECT SCHEMA_NAME, DEFAULT_CHARACTER_SET_NAME, DEFAULT_COLLATION_NAME FROM INFORMATION_SCHEMA.SCHEMATA WHERE SCHEMA_NAME='" + SchemaName + "'";
        
        try {
        	oStatement = oCon.createStatement();
        	oResultSet = oStatement.executeQuery(ScriptSQL);
            
            while (oResultSet.next()) {
            	SchemaScript = "CREATE DATABASE " + oResultSet.getString("SCHEMA_NAME") + " CHARACTER SET " + oResultSet.getString("DEFAULT_COLLATION_NAME") + " COLLATE " + oResultSet.getString("DEFAULT_COLLATION_NAME");
            	//Read the Tables List for the Current Schema.
            	setTables();
            	
            	//Read the Views List and Scripts for the Current Schema.
            	setViewsList();
            	
            	//Read the Sequence List for the Current Schema.
            	setSequencesList();
            	
            	//Read the Stored Procedures List for the Current Schema.
            	setStoredProceduresList();
            	
            	//Get the Stored Functions List for the Current Schema.
            	setStoredFunctionsList();
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
        String ConstraintSQL;
        Statement oStatement;
        ResultSet oResultSet;
        ConstraintSQL = "SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES " +
        				"WHERE TABLE_SCHEMA='" + SchemaName + "' AND TABLE_TYPE = 'BASE TABLE'";

        try {
        	oStatement = oCon.createStatement();
        	oResultSet = oStatement.executeQuery(ConstraintSQL);
            
            while (oResultSet.next()) {
            	SchemaTables.add(new MySQLTable(oCon, SchemaName, oResultSet.getString("TABLE_NAME")));
            }
            oStatement.close();
            oResultSet.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void setViewsList() {};
    public void setSequencesList() {};
    public void setStoredProceduresList() {};
    public void setStoredFunctionsList() {};
    
    public List<TableHandler> getTables() {
    	return SchemaTables;
    }
    
    public List<String> getViewsList() {
    	return SchemaViews;
    }
    
    public List<String> getSequencesList() {
    	return SchemaSequences;
    }
    
    public List<String> getStoredProceduresList() {
    	return StoredProcedures;
    }
    
    public List<String> getStoredFunctionsList() {
    	return StoredFunctions;
    }

}
