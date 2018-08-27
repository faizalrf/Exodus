package mariadb.migration;

import java.math.BigDecimal;
import java.sql.ParameterMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;

import mariadb.migration.mysql.MySQLConnect;

public class ExodusWorker {
	private boolean DeltaProcessing=false;
	private String MigrationTask;
	private int BATCH_SIZE = 0;
	private DBConHandler SourceCon, TargetCon;
	private TableHandler Table;
	private ExodusProgress Prog;

	public ExodusWorker(DBConHandler iSourceCon, DBConHandler iTargetCon, TableHandler iTable, String iMigrationTask) {
		Table = iTable;
		MigrationTask = iMigrationTask;
		Prog = new ExodusProgress(Table);
		
        SourceCon = iSourceCon;
        TargetCon = iTargetCon; 

        BATCH_SIZE = Integer.valueOf(Util.getPropertyValue("TransactionSize"));

		//Identify if the Table has already been partially Migrated or not!
		DeltaProcessing = ExodusProgress.hasTablePartiallyMigrated(Table.getSchemaName(), Table.getTableName());
	}
	
	//Data Migration Logic goes here... Takes care of Delta/Full migration
	public long Exodus() throws SQLException {
		String SourceSelectSQL, TargetInsertSQL, ColumnType, ErrorString;
		ResultSetMetaData SourceResultSetMeta;
	    ParameterMetaData TargetInsertMeta;
	    PreparedStatement PreparedStmt, SourcePreparedStmt;
		long MigratedRows=0, TotalRecords=0, CommitCount=0, SecondsTaken, RecordsPerSecond=0;
		float TimeforOneRecord;

		//To Track Start/End and Estimate Time of Completion
		LocalTime StartDT;
		LocalTime EndDT;
	
	    int ColumnCount, BatchRowCounter, IntValue, OverFlow, PackedBatchSize, BatchCounter=0;
		
		byte[] BlobObj;
		double DoubleValue;
		BigDecimal BigDecimalValue;
		long LongValue;
		float FloatValue;
		
		ResultSet SourceResultSetObj;
		Statement SourceStatementObj, TargetStetementObj;
		
		if (DeltaProcessing) {
			//TODO Build the Delta Logic in the SELECT Query
			SourceSelectSQL = Table.getDeltaSelectScript();
			TotalRecords = Table.getDeltaRecordCount();
		} else {
			SourceSelectSQL = Table.getTableSelectScript();
			TotalRecords = Table.getRecordCount();

			//Try to create the target table and skip this entire process if any errors
			if (Util.ExecuteScript(TargetCon, Table.getTableScript()) < 0) {
				return -1;
			}
		}
		
	    //Calculate How many extra records after perfect batches of BATCH_SIZE
	    //This will be used later to fill in the last prepare statement string building
	    OverFlow = (int)(TotalRecords % BATCH_SIZE);
	    
	    //Calculate what is the record count that will fit the batch perfectly, this is used to calculate the overflow to be handled in the last batch!
	    PackedBatchSize = BATCH_SIZE * (int)(TotalRecords / BATCH_SIZE); 
	    
	    //See if first records will be able to fit a single BATCH 
	    if (BATCH_SIZE > TotalRecords) {
	    	BATCH_SIZE = (int)TotalRecords;
	    }

		TargetInsertSQL = Table.getTargetInsertScript();
		
		try {
			SourceStatementObj = SourceCon.getDBConnection().createStatement();
			SourceResultSetObj = SourceStatementObj.executeQuery(SourceSelectSQL);
			//System.out.println(SourceSelectSQL);
			
			//Get the Meta data of the Source ResultSet, this will be used to get the list of columns in the ResultSet.
			SourceResultSetMeta = SourceResultSetObj.getMetaData();
	        ColumnCount = SourceResultSetMeta.getColumnCount();
	        PreparedStmt = TargetCon.getDBConnection().prepareStatement(TargetInsertSQL);
	        //Parse through the source query result-set!
	        //! //TODO BLOB TEST ALL 
	        
	        ErrorString="";
	        BatchRowCounter=0;
	        System.out.println("");
			PreparedStmt.clearBatch();

			StartDT = LocalTime.now();
			EndDT = LocalTime.now();
			RecordsPerSecond = BATCH_SIZE;

	        while (SourceResultSetObj.next()) {
				try {
			        for (int Col = 1; Col <= ColumnCount; Col++) {
			            ColumnType = SourceResultSetMeta.getColumnTypeName(Col);

			            switch (ColumnType) {
		                	case "INTEGER":
			                	IntValue = SourceResultSetObj.getInt(Col);
			                    if(SourceResultSetObj.wasNull())
			                    	PreparedStmt.setNull(Col, java.sql.Types.INTEGER);
			                    else
			                    	PreparedStmt.setInt(Col, IntValue);
			                    break;
			                case "VARCHAR": PreparedStmt.setString(Col, SourceResultSetObj.getString(Col));
		                    	break;
			                case "CHAR": PreparedStmt.setString(Col, SourceResultSetObj.getString(Col));
			                    break;
			                case "TIMESTAMP": PreparedStmt.setTimestamp(Col, SourceResultSetObj.getTimestamp(Col)); 
		                    	break;
			                case "DATE": PreparedStmt.setDate(Col, SourceResultSetObj.getDate(Col));
			                    break;
			                case "TIME": PreparedStmt.setTime(Col, SourceResultSetObj.getTime(Col));
			                    break;
			                case "BOOL": 
			                case "BOOLEAN": 
			                case "TINYINT": 
			                case "TINYINT UNSIGNED": 
			                    IntValue = SourceResultSetObj.getInt(Col);
			                    if(SourceResultSetObj.wasNull())
			                    	PreparedStmt.setNull(Col, java.sql.Types.TINYINT);
			                    else
			                    	PreparedStmt.setInt(Col, IntValue);
			                    break;
			                case "SMALLINT": 
			                case "SMALLINT UNSIGNED": 
			                    IntValue = SourceResultSetObj.getInt(Col);
			                    if(SourceResultSetObj.wasNull())
			                    	PreparedStmt.setNull(Col, java.sql.Types.SMALLINT);
			                    else
			                    	PreparedStmt.setInt(Col, IntValue);
			                    break;
			                case "BIGINT": 
			                case "BIGINT UNSIGNED": 
			                    LongValue = SourceResultSetObj.getLong(Col);
			                    if(SourceResultSetObj.wasNull())
			                    	PreparedStmt.setNull(Col, java.sql.Types.BIGINT);
			                    else
			                    	PreparedStmt.setLong(Col, LongValue);
			                    break;                                
			                case "DOUBLE":
			                	DoubleValue = SourceResultSetObj.getDouble(Col);
			                    if(SourceResultSetObj.wasNull())
			                    	PreparedStmt.setNull(Col, java.sql.Types.DOUBLE);
			                    else
			                    	PreparedStmt.setDouble(Col, DoubleValue);
			                    break;
			                case "DECIMAL": 
			                    BigDecimalValue = SourceResultSetObj.getBigDecimal(Col);
			                    if(SourceResultSetObj.wasNull())
			                    	PreparedStmt.setNull(Col, java.sql.Types.DECIMAL);
			                    else
			                    	PreparedStmt.setBigDecimal(Col, BigDecimalValue);
			                    break;
			                case "REAL":
			                case "FLOAT":
			                    FloatValue = SourceResultSetObj.getFloat(Col);
			                    if(SourceResultSetObj.wasNull())
			                    	PreparedStmt.setNull(Col, java.sql.Types.REAL);
			                    else
			                    	PreparedStmt.setFloat(Col, FloatValue);
			                    break;
			                case "TINYBLOB":
			                case "BLOB":
			                case "MEDIUMBLOB":
			                case "LONGBLOB":
			                	BlobObj = SourceResultSetObj.getBytes(Col);
			                	if (BlobObj != null) 
			                		//Use setBytes / getBytes for BLOB
			                		PreparedStmt.setBytes(Col, BlobObj);
			                	else   
			                		PreparedStmt.setNull(Col, java.sql.Types.BLOB);
			                    break;
			                case "CLOB": 
			                	PreparedStmt.setString(Col, SourceResultSetObj.getString(Col));
			                    break;
			                case "SQLXML": PreparedStmt.setClob(Col, SourceResultSetObj.getClob(Col));
			                    break;
			                default: 
			                	ErrorString += "\nUnknown Data Type: " + Table.getFullTableName() + "(" + SourceResultSetMeta.getColumnName(Col) + "(" + SourceResultSetMeta.getColumnTypeName(Col) + "))";
					        	throw new SQLException(ErrorString);
			            }
			        }
			        
			        PreparedStmt.addBatch();
			        BatchRowCounter++;
			        
			        //Batch has reached its COMMIT point!
			        if (BatchRowCounter % BATCH_SIZE == 0) {
			        	BatchRowCounter = 0;
			        	PreparedStmt.executeBatch();
						
						//Total Records Migrated
						CommitCount += BATCH_SIZE;
						BatchCounter++;
						
						TargetCon.getDBConnection().commit();
						
						//Don't calculate time for each commit, but wait for 10 batches to re-estimate
						if (BatchCounter % 10 == 0) {
							//Seconds Taken from Start to Now!
							SecondsTaken = ChronoUnit.SECONDS.between(StartDT, LocalTime.now());
							if (SecondsTaken == 0) {
								SecondsTaken = 1;
							}

							//Time Taken for 1 Record
							TimeforOneRecord = (float)(SecondsTaken/(float)CommitCount);
							RecordsPerSecond = (long)(((float)CommitCount / (float)SecondsTaken));

							EndDT = LocalTime.now().plusSeconds((long)((TotalRecords-CommitCount) * (TimeforOneRecord)));
						}

						System.out.print("\r" + LocalTime.now() + " - Progress..: " + Table.getFullTableName() + " [" + TotalRecords + "/" + CommitCount + "] @ " + RecordsPerSecond + " Rec/s - ETA [" + (EndDT.truncatedTo(ChronoUnit.SECONDS).toString()) + "]");
			        }
			        
				} catch (SQLException e) {
		        	TargetCon.getDBConnection().rollback();
					ErrorString="";
					e.printStackTrace();
				}
			}
	        
	        //Perform the final Commit!
	        if (OverFlow > 0) {
		        PreparedStmt.executeBatch();
	        	CommitCount += OverFlow;
	        	TargetCon.getDBConnection().commit();
				System.out.print("\r" + LocalTime.now() + " - Completed.: " + Table.getFullTableName() + " [" + TotalRecords + "/" + CommitCount + "] @ " + RecordsPerSecond + " Rec/s - ETA [" + (EndDT.truncatedTo(ChronoUnit.SECONDS).toString()) + "]");
	        }
	        
			//Close Statements and ResultSet
	        SourceResultSetObj.close();
        	SourceStatementObj.close();
        	PreparedStmt.close();        	
		} catch (SQLException e) {
        	TargetCon.getDBConnection().rollback();
			e.printStackTrace();
			
		} finally {	//Cleanup	        
	        TargetCon.DisconnectDB();
	        SourceCon.DisconnectDB();
		}
		
		return MigratedRows;
	}
}