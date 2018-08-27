package mariadb.migration;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class ExodusPropertyReader {
    String PropertyFileName="";
    
    ExodusPropertyReader(String iPropFileName) {
    	PropertyFileName = iPropFileName;
    }

    public String getValue(String iPropertyName) throws IOException {
        InputStream inputFileObj;
        String ReturnVal = "";
        try {  	
            Properties propertiesObj = new Properties();
            
            //Get File Pointer to the Property FileName            
            inputFileObj = getClass().getClassLoader().getResourceAsStream(PropertyFileName);
            
            //Load the Property File for processing if the Pointer is valid
            if (inputFileObj != null) {
            	propertiesObj.load(inputFileObj);
            } else {
                throw new FileNotFoundException("Property File '" + PropertyFileName + "' not found!");
            }
            
            inputFileObj.close();
            
            //Read and return the property value requested for value
            ReturnVal = propertiesObj.getProperty(iPropertyName);

        } catch (Exception e) {
            System.out.println("Exception: " + PropertyFileName + " " + iPropertyName + " - " + e);
            e.printStackTrace();
        }
        return ReturnVal;
    }
}
