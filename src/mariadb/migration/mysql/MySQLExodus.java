package mariadb.migration.mysql;

import java.math.BigDecimal;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.ParameterMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;

import mariadb.migration.DBConHandler;
import mariadb.migration.MariaDBConnect;
import mariadb.migration.ExodusProgress;
import mariadb.migration.TableHandler;
import mariadb.migration.Util;

public class MySQLExodus implements Runnable {
	private DBConHandler SourceCon, TargetCon;
	private TableHandler Table;
	private String SchemaName, TableName;
	private Thread MySQLWorkerThread;
	private String ThreadName;
	private boolean DeltaProcessing=false;
	private int BATCH_SIZE = Integer.valueOf(Util.getPropertyValue("TransactionSize"));
	
	//Start Progress for the Current Table #Table#
	private ExodusProgress MProg = new ExodusProgress(Table);
	String MigrationTask;

	MySQLExodus(TableHandler iTable, String iTask) {
        SourceCon = new MySQLConnect(Util.getPropertyValue("SourceDB"));
        TargetCon = new MariaDBConnect(Util.getPropertyValue("TargetDB"));
		Table = iTable;
		SchemaName = Table.getSchemaName();
		TableName = Table.getTableName();
		ThreadName = SchemaName + "." + TableName;
		
		//Dry Run or normal Migration
		if (Util.getPropertyValue("DryRun").equals("NO")) {
			MigrationTask = iTask;
		} else {
			MigrationTask = "SKIP";
		}

		//Identify if the Table has already been partially Migrated or not!
		if (ExodusProgress.hasTablePartiallyMigrated(SchemaName, TableName)) {
			DeltaProcessing = true;
		} else {
			DeltaProcessing = false;
		}
	}
	
	public void run() {
    	try {
	    	switch (MigrationTask) {
	    	case "CREATE":
	    		ObjectCreation();
	    		break;
	    	case "DATAMIGRATION":
	    		DataMigraiton();
	    		break;
	    	case "FK":
	    		break;
	    	default:
	    		break;    		
	    	}
    	}
    	catch (Exception e) {
    		System.out.println("Exception While running Main Thread!");
			e.printStackTrace();    		
    	}
    	finally {
    		//Cleanup Connections
    		if (SourceCon != null) SourceCon.DisconnectDB();
    		if (TargetCon != null) TargetCon.DisconnectDB();
    	}
    }
    
	//Trigger the Thread for a given Table!
	public void start() {
    	if (MySQLWorkerThread == null) {
    		MySQLWorkerThread = new Thread (this, ThreadName);
    	}
    	MySQLWorkerThread.start();
    }
	
	public void ObjectCreation() {
		//TODO Some Crazy stuff here!
	}
	
	public void DataMigraiton() {
		String SourceSelectSQL, TargetInsertSQL;
		ResultSetMetaData SourceResultSetMeta;
        ParameterMetaData TargetInsertMeta;
        PreparedStatement PreparedStmt;
        
        int ColumnCount, BatchColInd=0, ColumnType, IntValue, OverFlow, PackedBatchSize;
		long TotalRecords;
		
		Blob BlobObj;
		Clob ClobObj;
		double DoubleValue;
		BigDecimal BigDecimalValue;
		long LongValue;
		float FloatValue;
		
		ResultSet SourceResultSetObj;
		Statement SourceStatementObj, TargetStetementObj;
		
		if (DeltaProcessing) {
			SourceSelectSQL = Table.getDeltaSelectScript();
			TotalRecords = Table.getDeltaRecordCount();
		} else {
			SourceSelectSQL = Table.getTableSelectScript();
			TotalRecords = Table.getRecordCount();
		}
		
        //Calculate How many extra records after perfect batches of BATCH_SIZE
        //This will be used later to fill in the last prepare statement string building
        OverFlow = (int)(TotalRecords % BATCH_SIZE);
        
        //Calculate what is the record count that will fit the batch perfectly, this is used to calculate the overflow to be handled in the last batch!
        PackedBatchSize = BATCH_SIZE * (int)(TotalRecords / BATCH_SIZE); 
        
        //See if first records will be able to fit a single BATCH 
        if (BATCH_SIZE > TotalRecords) {
        	BATCH_SIZE = OverFlow;
        }

		//Table's CLASS handles the Scripting of the SELECT / INSERT and Delta INSERT scripts automatically
		TargetInsertSQL = Table.getTargetInsertScript();
		
		try {
			SourceStatementObj = TargetCon.getDBConnection().createStatement();
			SourceResultSetObj = SourceStatementObj.executeQuery(SourceSelectSQL);
			
			//Get the Meta data of the Source ResultSet, this will be used to get the list of columns in the ResultSet.
			SourceResultSetMeta = SourceResultSetObj.getMetaData();
	        ColumnCount = SourceResultSetMeta.getColumnCount();
	        PreparedStmt = TargetCon.getDBConnection().prepareStatement(TargetInsertSQL);
	        		
			//Parse through the source query result-set!
			//NULLs are handled only for specific data types
			while (SourceResultSetObj.next()) {
				BatchColInd++;
				try {
			        for (int Col = 1; Col <= ColumnCount; Col++) {
			            ColumnType = SourceResultSetMeta.getColumnType(Col);

			            switch (ColumnType) {
			                case java.sql.Types.INTEGER:
			                	IntValue = SourceResultSetObj.getInt(Col);
			                    if(SourceResultSetObj.wasNull())
			                    	PreparedStmt.setNull(BatchColInd, java.sql.Types.INTEGER);
			                    else
			                    	PreparedStmt.setInt(BatchColInd, IntValue);
			                    break;
			                case java.sql.Types.VARCHAR: PreparedStmt.setString(BatchColInd, SourceResultSetObj.getString(Col));
		                    	break;
			                case java.sql.Types.CHAR: PreparedStmt.setString(BatchColInd, SourceResultSetObj.getString(Col));
			                    break;
			                case java.sql.Types.TIMESTAMP: PreparedStmt.setTimestamp(BatchColInd, SourceResultSetObj.getTimestamp(Col)); 
		                    	break;
			                case java.sql.Types.DATE: PreparedStmt.setDate(BatchColInd, SourceResultSetObj.getDate(Col));
			                    break;
			                case java.sql.Types.TIME: PreparedStmt.setTime(BatchColInd, SourceResultSetObj.getTime(Col));
			                    break;
			                case java.sql.Types.SMALLINT: 
			                    IntValue = SourceResultSetObj.getInt(Col);
			                    if(SourceResultSetObj.wasNull())
			                    	PreparedStmt.setNull(BatchColInd, java.sql.Types.SMALLINT);
			                    else
			                    	PreparedStmt.setInt(BatchColInd, IntValue);
			                    break;
			                case java.sql.Types.BIGINT: 
			                    LongValue = SourceResultSetObj.getLong(Col);
			                    if(SourceResultSetObj.wasNull())
			                    	PreparedStmt.setNull(BatchColInd, java.sql.Types.BIGINT);
			                    else
			                    	PreparedStmt.setLong(BatchColInd, LongValue);
			                    break;                                
			                case java.sql.Types.DOUBLE:
			                	DoubleValue = SourceResultSetObj.getDouble(Col);
			                    if(SourceResultSetObj.wasNull())
			                    	PreparedStmt.setNull(BatchColInd, java.sql.Types.DOUBLE);
			                    else
			                    	PreparedStmt.setDouble(BatchColInd, DoubleValue);
			                    break;
			                case java.sql.Types.DECIMAL: 
			                    BigDecimalValue = SourceResultSetObj.getBigDecimal(Col);
			                    if(SourceResultSetObj.wasNull())
			                    	PreparedStmt.setNull(BatchColInd, java.sql.Types.DECIMAL);
			                    else
			                    	PreparedStmt.setBigDecimal(BatchColInd, BigDecimalValue);
			                    break;
			                case java.sql.Types.REAL:
			                    FloatValue = SourceResultSetObj.getFloat(Col);
			                    if(SourceResultSetObj.wasNull())
			                    	PreparedStmt.setNull(BatchColInd, java.sql.Types.REAL);
			                    else
			                    	PreparedStmt.setFloat(BatchColInd, FloatValue);
			                    break;
			                case java.sql.Types.BLOB: 
			                	BlobObj = SourceResultSetObj.getBlob(Col);
			                	if (BlobObj != null) 
			                		PreparedStmt.setBlob(BatchColInd, BlobObj);
			                	else   
			                		PreparedStmt.setNull(BatchColInd, java.sql.Types.BLOB);
			                    break;
			                case java.sql.Types.CLOB: 
			                	ClobObj = SourceResultSetObj.getClob(Col);
			                	if (ClobObj != null) 
			                		PreparedStmt.setClob(BatchColInd, ClobObj);
			                	else   
			                		PreparedStmt.setNull(BatchColInd, java.sql.Types.CLOB);
			                    break;
			                case java.sql.Types.SQLXML: PreparedStmt.setClob(BatchColInd, SourceResultSetObj.getClob(Col));
			                    break;
			                default: PreparedStmt.setString(BatchColInd, SourceResultSetObj.getString(Col));
			            }
			        }
				} catch (SQLException e) {
					e.printStackTrace();
				}
			}
			
		} catch (SQLException e) {
			e.printStackTrace();
		}		
	}

	//Bulk Data Migration Logic goes here... Takes care of Delta/Full migration
	public void BulkDataMigraiton() {
		String SourceSelectSQL, TargetInsertSQL;
		ResultSetMetaData SourceResultSetMeta;
        ParameterMetaData TargetInsertMeta;
        PreparedStatement PreparedStmt;
        
        int ColumnCount, BatchColInd=0, ColumnType, IntValue, OverFlow, PackedBatchSize;
		long TotalRecords;
		
		Blob BlobObj;
		Clob ClobObj;
		double DoubleValue;
		BigDecimal BigDecimalValue;
		long LongValue;
		float FloatValue;
		
		ResultSet SourceResultSetObj;
		Statement SourceStatementObj, TargetStetementObj;
		
		if (DeltaProcessing) {
			SourceSelectSQL = Table.getDeltaSelectScript();
			TotalRecords = Table.getDeltaRecordCount();
		} else {
			SourceSelectSQL = Table.getTableSelectScript();
			TotalRecords = Table.getRecordCount();
		}
		
        //Calculate How many extra records after perfect batches of BATCH_SIZE
        //This will be used later to fill in the last prepare statement string building
        OverFlow = (int)(TotalRecords % BATCH_SIZE);
        
        //Calculate what is the record count that will fit the batch perfectly, this is used to calculate the overflow to be handled in the last batch!
        PackedBatchSize = BATCH_SIZE * (int)(TotalRecords / BATCH_SIZE); 
        
        //See if first records will be able to fit a single BATCH 
        if (BATCH_SIZE > TotalRecords) {
        	BATCH_SIZE = OverFlow;
        }

		//Table's CLASS handles the Scripting of the SELECT / INSERT and Delta INSERT scripts automatically
		TargetInsertSQL = Table.getTargetInsertScript();
		
		try {
			SourceStatementObj = TargetCon.getDBConnection().createStatement();
			SourceResultSetObj = SourceStatementObj.executeQuery(SourceSelectSQL);
			
			//Get the Meta data of the Source ResultSet, this will be used to get the list of columns in the ResultSet.
			SourceResultSetMeta = SourceResultSetObj.getMetaData();
	        ColumnCount = SourceResultSetMeta.getColumnCount();
	        PreparedStmt = TargetCon.getDBConnection().prepareStatement(TargetInsertSQL);
	        		
			//Parse through the source query result-set!
			//NULLs are handled only for specific data types
			while (SourceResultSetObj.next()) {
				BatchColInd++;
				try {
			        for (int Col = 1; Col <= ColumnCount; Col++) {
			            ColumnType = SourceResultSetMeta.getColumnType(Col);

			            switch (ColumnType) {
			                case java.sql.Types.INTEGER:
			                	IntValue = SourceResultSetObj.getInt(Col);
			                    if(SourceResultSetObj.wasNull())
			                    	PreparedStmt.setNull(BatchColInd, java.sql.Types.INTEGER);
			                    else
			                    	PreparedStmt.setInt(BatchColInd, IntValue);
			                    break;
			                case java.sql.Types.VARCHAR: PreparedStmt.setString(BatchColInd, SourceResultSetObj.getString(Col));
		                    	break;
			                case java.sql.Types.CHAR: PreparedStmt.setString(BatchColInd, SourceResultSetObj.getString(Col));
			                    break;
			                case java.sql.Types.TIMESTAMP: PreparedStmt.setTimestamp(BatchColInd, SourceResultSetObj.getTimestamp(Col)); 
		                    	break;
			                case java.sql.Types.DATE: PreparedStmt.setDate(BatchColInd, SourceResultSetObj.getDate(Col));
			                    break;
			                case java.sql.Types.TIME: PreparedStmt.setTime(BatchColInd, SourceResultSetObj.getTime(Col));
			                    break;
			                case java.sql.Types.SMALLINT: 
			                    IntValue = SourceResultSetObj.getInt(Col);
			                    if(SourceResultSetObj.wasNull())
			                    	PreparedStmt.setNull(BatchColInd, java.sql.Types.SMALLINT);
			                    else
			                    	PreparedStmt.setInt(BatchColInd, IntValue);
			                    break;
			                case java.sql.Types.BIGINT: 
			                    LongValue = SourceResultSetObj.getLong(Col);
			                    if(SourceResultSetObj.wasNull())
			                    	PreparedStmt.setNull(BatchColInd, java.sql.Types.BIGINT);
			                    else
			                    	PreparedStmt.setLong(BatchColInd, LongValue);
			                    break;                                
			                case java.sql.Types.DOUBLE:
			                	DoubleValue = SourceResultSetObj.getDouble(Col);
			                    if(SourceResultSetObj.wasNull())
			                    	PreparedStmt.setNull(BatchColInd, java.sql.Types.DOUBLE);
			                    else
			                    	PreparedStmt.setDouble(BatchColInd, DoubleValue);
			                    break;
			                case java.sql.Types.DECIMAL: 
			                    BigDecimalValue = SourceResultSetObj.getBigDecimal(Col);
			                    if(SourceResultSetObj.wasNull())
			                    	PreparedStmt.setNull(BatchColInd, java.sql.Types.DECIMAL);
			                    else
			                    	PreparedStmt.setBigDecimal(BatchColInd, BigDecimalValue);
			                    break;
			                case java.sql.Types.REAL:
			                    FloatValue = SourceResultSetObj.getFloat(Col);
			                    if(SourceResultSetObj.wasNull())
			                    	PreparedStmt.setNull(BatchColInd, java.sql.Types.REAL);
			                    else
			                    	PreparedStmt.setFloat(BatchColInd, FloatValue);
			                    break;
			                case java.sql.Types.BLOB: 
			                	BlobObj = SourceResultSetObj.getBlob(Col);
			                	if (BlobObj != null) 
			                		PreparedStmt.setBlob(BatchColInd, BlobObj);
			                	else   
			                		PreparedStmt.setNull(BatchColInd, java.sql.Types.BLOB);
			                    break;
			                case java.sql.Types.CLOB: 
			                	ClobObj = SourceResultSetObj.getClob(Col);
			                	if (ClobObj != null) 
			                		PreparedStmt.setClob(BatchColInd, ClobObj);
			                	else   
			                		PreparedStmt.setNull(BatchColInd, java.sql.Types.CLOB);
			                    break;
			                case java.sql.Types.SQLXML: PreparedStmt.setClob(BatchColInd, SourceResultSetObj.getClob(Col));
			                    break;
			                default: PreparedStmt.setString(BatchColInd, SourceResultSetObj.getString(Col));
			            }
			        }
				} catch (SQLException e) {
					e.printStackTrace();
				}
			}
			
		} catch (SQLException e) {
			e.printStackTrace();
		}		
	}
}
