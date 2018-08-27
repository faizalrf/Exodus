package mariadb.migration;
public class DataMapping {
	private String SourceType="";
	private String TargetType="";
	private String Sizeable="";
	private String Scaleable="";
	private long MinSize=0;
	private long MaxSize=0;
	
	public DataMapping() {}
	
	public void setSourceType(String pSourceType) { SourceType = pSourceType; }
	public String getSourceType() { return SourceType; }

	public void setTargetType(String pTargetType) { TargetType = pTargetType; }
	public String getTargetType() { return TargetType; }

	public void setSizeable(String pSizeable) { Sizeable = pSizeable; }
	public String getSizeable() { return Sizeable; }

	public void setScaleable(String pScaleable) { Scaleable = pScaleable; }
	public String getScaleable() { return Scaleable; }
	
	public void setMinSize(long pMinSize) { MinSize = pMinSize; }
	public long getMinSize() { return MinSize; }
	
	public void setMaxSize(long pMaxSize) { MaxSize = pMaxSize; }
	public long getMaxSize() { return MaxSize; }
	
}
