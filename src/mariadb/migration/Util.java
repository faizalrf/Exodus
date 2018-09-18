package mariadb.migration;

import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.io.IOException;
import java.sql.SQLException;
import java.sql.Statement;

public class Util {
    public static SimpleDateFormat dateFormat = new SimpleDateFormat ("E yyyy.MM.dd 'at' hh:mm:ss a zzz");
    public static DecimalFormat numberFormat = new DecimalFormat("###,###,###,###,###");
    public static DecimalFormat digitsFormat = new DecimalFormat("00");
    public static DecimalFormat percentFormat = new DecimalFormat("###.00");
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

	public static List<String> GetExtraStatements(String Key) {
		List<String> Scripts = new ArrayList<String>();
		String Statement;
		int Counter=0;

		Statement = Util.getPropertyValue(Key + "." + (++Counter));
		while(!Statement.isEmpty()) {
			Scripts.add(Statement);
			Statement = Util.getPropertyValue(Key + "." + (++Counter));
		}
		return Scripts;
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
	public static String TimeToString(long lSeconds) {
		String SecondsRemaining="";
		long Hours, Minutes, Seconds;
		
		Hours = lSeconds / 60 / 60;
		Minutes = (lSeconds / 60) - (Hours * 60);
		Seconds = lSeconds - (Minutes * 60);

		SecondsRemaining += digitsFormat.format(Hours) + ":" + digitsFormat.format(Minutes) + ":" + digitsFormat.format(Seconds);

		return SecondsRemaining;
	}

	public static String lPad(String SourceStr, int FixedLen, String PadChar) {
    	int StrLength=SourceStr.length();
    	String TmpStr="";
    	if (StrLength < FixedLen) {
    		FixedLen -= StrLength;
	    	for (int x = 0; x < FixedLen; x++) {
	    		TmpStr += PadChar;
	    	}
    	}
    	return TmpStr + SourceStr;
    }
	
	public static String rPad(String SourceStr, int FixedLen, String PadChar) {
    	int StrLength=SourceStr.length();
    	String TmpStr="";
    	if (StrLength < FixedLen) {
    		FixedLen -= StrLength;
	    	for (int x = 0; x < FixedLen; x++) {
	    		TmpStr += PadChar;
	    	}
    	}
    	return SourceStr + TmpStr;
    }
}
