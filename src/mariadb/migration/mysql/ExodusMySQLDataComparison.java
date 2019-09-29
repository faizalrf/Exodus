package mariadb.migration.mysql;
import java.sql.ResultSet;
import java.sql.Statement;

import mariadb.migration.DBConHandler;
import mariadb.migration.TableHandler;
import mariadb.migration.Logger;
import mariadb.migration.Util;

public class ExodusMySQLDataComparison {
    public ExodusMySQLDataComparison() {}
    
    public static boolean CompareData(DBConHandler SourceCon, DBConHandler TargetCon, TableHandler Tab) {

        String SQLScript, VerificationLogFile, OutString;
        ResultSet SourceResultSetObj, TargetResultSetObj;
        Statement SourceStatementObj, TargetStatementObj;
        long RecCount=0, Match=0, Mismatch=0;
        boolean HasErrors = false;
        
        VerificationLogFile = Util.getPropertyValue("LogPath") + "/DataVerification.log";
        SQLScript = Tab.getMD5DSelectScript();
       
        try {
            SourceStatementObj = SourceCon.getDBConnection().createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
            SourceStatementObj.setFetchSize(10000);
            SourceResultSetObj = SourceStatementObj.executeQuery(SQLScript);

            TargetStatementObj = TargetCon.getDBConnection().createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
            TargetStatementObj.setFetchSize(10000);
            TargetResultSetObj = TargetStatementObj.executeQuery(SQLScript);

            new Logger(VerificationLogFile, SQLScript, true, true);
            new Logger(VerificationLogFile, Tab.getFullTableName(), true, true);
            while (SourceResultSetObj.next()) {
                RecCount++;
                if (!TargetResultSetObj.next()) {
                    new Logger(VerificationLogFile, "Record# " + RecCount + " not found in target", true, true);
                    HasErrors=true;
                    break;
                }

                if (SourceResultSetObj.getString(1).hashCode() == TargetResultSetObj.getString(1).hashCode()) {
                    Match++;
                } else {
                    new Logger(VerificationLogFile, "Record# " + RecCount + " Data Mismatch [" + SourceResultSetObj.getString(1) + " - " + TargetResultSetObj.getString(1) + "]", true, true);
                    Mismatch++;
                }
                //OutString = Util.rPad(StartDT.truncatedTo(ChronoUnit.SECONDS).format(DateTimeFormatter.ofPattern("HH:mm:ss")) + " - Processing " + Table.getFullTableName(), 79, " ") + " --> 100.00% [" + Util.lPad(Util.numberFormat.format(CommitCount) + " / " + Util.numberFormat.format(TotalRecords) + " @ " + Util.numberFormat.format(RecordsPerSecond) + "/s", 36, " ") + "]  - COMPLETED [" + LocalTime.now().truncatedTo(ChronoUnit.SECONDS).format(DateTimeFormatter.ofPattern("HH:mm:ss")) + "]";
                OutString = Util.rPad(Tab.getFullTableName(), 50, " ") + " -> " + Util.rPad("Rows Matched: " + Util.numberFormat.format(Match) + " / " + Util.numberFormat.format(Tab.getMD5Limit()), 50, " ") + Util.rPad(" Rows Mismatch: " + Util.numberFormat.format(Mismatch) + " / " + Util.numberFormat.format(Tab.getMD5Limit()), 36, " ");

                System.out.print("\r" + OutString);
            }
            System.out.println("");
            // Housekeeping
            TargetResultSetObj.close();
            TargetStatementObj.close();

            SourceResultSetObj.close();
            SourceStatementObj.close();
        } catch (Exception ex) {
            System.out.println(ex.getMessage());
        }
        return (HasErrors || Mismatch == 0);
    }
}