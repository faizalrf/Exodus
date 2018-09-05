package mariadb.migration;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.Date;
import java.text.SimpleDateFormat;

public class Logger implements LogWriter {
	private String FileName="";
	private String FilePath;
	private BufferedWriter LogFileHandler;
	private boolean Append = false;
    SimpleDateFormat DateFormat = new SimpleDateFormat ("E yyyy.MM.dd 'at' hh:mm:ss a zzz");
	
	public Logger(String oFileName, boolean iAppendMode, String LogText) {
		FileName = oFileName;
		Append = iAppendMode;
		FilePath = Paths.get(FileName.trim()).toString();
		CreateLogFile();
		WriteLog(LogText);
		CloseLogFile();
	}

	public Logger(String oFileName, boolean iAppendMode) {
		FileName = oFileName;
		Append = iAppendMode;
		FilePath = Paths.get(FileName.trim()).toString();
		CreateLogFile();
	}

	public Logger(String oFileName) {
		FileName = oFileName;
		FilePath = Paths.get(FileName.trim()).toString();
		Append = false;
		CreateLogFile();
	}
	
	public void CreateLogFile() {
		try {
			LogFileHandler = new BufferedWriter(new FileWriter(FilePath, Append));
			LogFileHandler.flush();
		} catch (IOException x) {
		      System.out.println("Error whie opening the log file: " + FilePath + "[" + x + "]");
		}
	}

	public void WriteLog(String LogText) {
		try {
			LogFileHandler.write("[" + DateFormat.format(new Date()) + "] - " + LogText + "\n");
			LogFileHandler.flush();
		} catch (IOException x) {
			System.out.println("Error while writing to the log file: " + FilePath + "[" + x + "]");			
		}
	}

	public void CloseLogFile() {
		try {
			if (LogFileHandler != null) LogFileHandler.close();
		} catch (IOException e) {
			System.out.println("Log File Closed!");
		}
	}

	public void WriteLog(String LogText, boolean writeTimeStamp) {
		try {
			if (writeTimeStamp)
				LogFileHandler.write("[" + DateFormat.format(new Date()) + "] - " + LogText + "\n");
			else 
				LogFileHandler.write(LogText+"\n");
		} catch (IOException x) {
			System.out.println("Error while writing to the log file: " + FilePath + "[" + x + "]");
		}
	}
}
