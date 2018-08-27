package mariadb.migration;

public interface LogWriter {
	void CreateLogFile();
	void WriteLog(String pLogLine);
	void CloseLogFile();
}
