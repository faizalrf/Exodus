package mariadb.migration;
import java.io.File;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.DocumentBuilder;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.w3c.dom.Node;
import org.w3c.dom.Element;

public class DBCredentialsReader implements XMLParser {
	private String XMLPath="";
    private String DBType="";
	
	public DBCredentialsReader(String pXMLPath, String pDBType) {
	    XMLPath=pXMLPath;
	    DBType = pDBType;
	}

    public DataSources DBCredentials() {
        DataSources CurrentUserCredentials = new DataSources();

        try {
            File fXmlFile = new File(XMLPath);
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            Document doc = dBuilder.parse(fXmlFile);

            doc.getDocumentElement().normalize();

            NodeList nList = doc.getElementsByTagName("DataProvider");

            for (int iElement = 0; iElement < nList.getLength(); iElement++) {
                Node nNode = nList.item(iElement);

                if (nNode.getNodeType() == Node.ELEMENT_NODE) {

                    Element eElement = (Element) nNode;

                    //If DB Tag matches the source provided then read its structure
                    if (eElement.getAttribute("type").equals(DBType)) {
                        CurrentUserCredentials.setDBType(DBType);
                        CurrentUserCredentials.setDBName(eElement.getElementsByTagName("DatabaseName").item(0).getTextContent());
                        CurrentUserCredentials.setUserName(eElement.getElementsByTagName("UserName").item(0).getTextContent());
                        CurrentUserCredentials.setPassword(eElement.getElementsByTagName("Password").item(0).getTextContent());
                        CurrentUserCredentials.setHostName(eElement.getElementsByTagName("HostName").item(0).getTextContent());
                        CurrentUserCredentials.setPortNumber(Integer.parseInt(eElement.getElementsByTagName("PortNumber").item(0).getTextContent()));
                        break;
                    }
                }
            }
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }		
        return CurrentUserCredentials;
    }
}