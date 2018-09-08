package mariadb.migration;

import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.List;
import java.io.IOException;
import java.sql.SQLException;
import java.sql.Statement;

public class Util {
    public static SimpleDateFormat dateFormat = new SimpleDateFormat ("E yyyy.MM.dd 'at' hh:mm:ss a zzz");
    public static DecimalFormat numberFormat = new DecimalFormat("###,###,###,###,###");
    public static ExodusPropertyReader exodusPrope = new ExodusPropertyReader("Exodus.properties");
    
    public static String getPropertyValue(String propName) {
    	String PropertyValue="";
    	try {
			PropertyValue = exodusPrope.getValue(propName);
			if (PropertyValue == null) {
				PropertyValue="";
			}
		} catch (IOException e) {
			e.printStackTrace();
		} catch (Exception ex) {
			PropertyValue="";
		}
    	return PropertyValue;
    }
    
    public static long ExecuteScript(DBConHandler TargetCon, String SQLScript) {
    	long ReturnStatus = 0;
		Statement StatementObj;

		try {
			StatementObj = TargetCon.getDBConnection().createStatement();
	
			StatementObj.executeUpdate(SQLScript);
			
			StatementObj.close();
		} catch (SQLException e) {
			System.out.println("*** Failed to Execute: " + SQLScript);
			e.printStackTrace();
			ReturnStatus = -1;
		}
    	return ReturnStatus;
    }

	public static long ExecuteScript(DBConHandler TargetCon, List<String> SQLScript) {
    	long ReturnStatus = 0;
		Statement StatementObj;

		try {
			StatementObj = TargetCon.getDBConnection().createStatement();

			for (String SQL : SQLScript) {
				StatementObj.addBatch(SQL);
			}
			
			StatementObj.executeBatch();
			StatementObj.close();
		} catch (SQLException e) {
			System.out.println("*** Failed to Execute: " + SQLScript);
			e.printStackTrace();
			ReturnStatus = -1;
		}
    	return ReturnStatus;
    }
}
