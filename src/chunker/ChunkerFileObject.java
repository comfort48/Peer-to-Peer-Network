package chunker;

public class ChunkerFileObject implements java.io.Serializable {
	
	private static final long serialVersionUID = 1L;
	private int sequenceNumber;
	private String fileName;
	private byte[] fileData;
	private int chunkSize;
	
	public void setFileName(String fileName) {
		this.fileName = fileName;
	}
	
	public String getFileName() {
		return fileName;
	}
	
	public void setFileData(byte[] fileData) {
		this.fileData = fileData;
	}
	
	public byte[] getFileData() {
		return fileData;
	}
	
	public void setChunkSize(int chunksize) {
		this.chunkSize = chunksize;
	}

	public int getChunkSize() {
		return chunkSize;
	}

	public void setSequenceNumber(int sequenceNumber) {
		this.sequenceNumber = sequenceNumber;
	}
	
	public int getSequenceNumber() {
		return sequenceNumber;
	}

}
