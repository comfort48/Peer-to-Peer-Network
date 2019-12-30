package file_owner;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import chunker.ChunkerFileObject;

public class FileOwner {

	ServerSocket serverSocket;
	Socket connectionSocket;
	
	static String rootDirectory = System.getProperty("user.dir");
	static String serverDirectory = rootDirectory + "/file_owner";
	
	static int serverPort;
	static Map<Integer, ArrayList<Integer>> clientMapper;

	public static void main(String[] args) {

		FileOwner s = new FileOwner();
		try {
			s.divideInputFile(); 
			serverPort = Integer.parseInt(args[0]);

			String chunksLocation = serverDirectory + "/chunks";
			File[] files = new File(chunksLocation).listFiles();

			int chunkCount = files.length;
			System.out.println("No. of Chunks for the given file are : " + chunkCount);

			clientMapper = new LinkedHashMap<Integer, ArrayList<Integer>>();
			for (int i = 1; i <= 5; i++) {
				ArrayList<Integer> arr = new ArrayList<Integer>();
				for (int j = i; j <= chunkCount; j += 5) {
					arr.add(j);
				}
				clientMapper.put(i, arr);

			}
			for(int i = 1; i <= chunkCount; i++) {
				System.out.println("Chunk: "+i+" - Size: "+files[i-1].length());
			}

			if (chunkCount > 0) {
				s.TCPServconnect(files);
			} else
				System.out.println("There are no files in chunks folder!");

		} 
		catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	
	public void TCPServconnect(File[] files) {
		try {
			int client = 0; 
			serverSocket = new ServerSocket(serverPort);
			System.out.println("Main Server socket created, accepting connections...");
			while (true) {
				client++;
				if (client <= 5) {
					connectionSocket = serverSocket.accept();
					new ServerThread(connectionSocket, files, clientMapper.get(client)).start();

				} 
				else {
					System.out.println("Cannot serve more clients!");
					break;
				}

			}

		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public void divideInputFile() {
		try {
			String input="test.pdf";
			File inputFile = new File(serverDirectory +"/"+input);
			Long fileLength = inputFile.length();

			System.out.println("Input File size : " + fileLength);

			String newdir = inputFile.getParent() + "/chunks/";
			File outFolder = new File(newdir);
			if (outFolder.mkdirs())
				System.out.println("Chunks Folder created");
			else
				System.out.println("Chunks folder already exits or unable to create folder for chunks");

			byte[] chunk = new byte[102400];

			FileInputStream fileInStream = new FileInputStream(inputFile);

			BufferedInputStream bufferStream = new BufferedInputStream(fileInStream);
			int index = 1;
			int bytesRead;
			while ((bytesRead = bufferStream.read(chunk)) > 0) {
				FileOutputStream fileOutStream = new FileOutputStream(
						new File(newdir, String.format("%04d", index) + "_" + inputFile.getName()));
				BufferedOutputStream bufferOutStream = new BufferedOutputStream(fileOutStream);
				bufferOutStream.write(chunk, 0, bytesRead);
				bufferOutStream.close();
				index++;
			}
			bufferStream.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

}

class ServerThread extends Thread {

	private Socket socket;
	File[] files;
	ObjectInputStream inpStream;
	ObjectOutputStream outStream;
	String incomingPort;
	ArrayList<Integer> chunkList;
	String configClient;
	int chunkId;

	ServerThread(Socket s, File[] files, ArrayList<Integer> cl) {
		this.socket = s;
		this.files = files;
		this.chunkList = cl;
	}

	public void run() {
		try {
			
			outStream = new ObjectOutputStream(socket.getOutputStream());
			inpStream = new ObjectInputStream(socket.getInputStream());
			incomingPort = (String) inpStream.readObject();
			System.out.println("New Peer connected with Port Number: "+"["+incomingPort+"]");
			outStream.writeObject(files.length);
			outStream.writeObject(chunkList.size());
			Arrays.sort(files);
			for (int i = 0; i < chunkList.size(); i++) {
				ChunkerFileObject sChunkObj = constructChuckFileObject(files[chunkList.get(i) - 1], chunkList.get(i));
				sendChunkObject(sChunkObj); // Sending Chunk object
				Thread.sleep(1000);
			}
			TCPServDisconnect();
		} 
		catch (Exception e) {
			e.printStackTrace();
		}
	}

	public ChunkerFileObject constructChuckFileObject(File file, int chunkNum) throws IOException {
		byte[] chunk = new byte[102400];
		chunkId = chunkNum;
		System.out.println("Construct object to be sent to peers - " + file.getName());
		ChunkerFileObject chunkObj = new ChunkerFileObject();

		chunkObj.setSequenceNumber(chunkNum);

		chunkObj.setFileName(file.getName());
		FileInputStream fileInStream = new FileInputStream(file);

		BufferedInputStream bufferInStream = new BufferedInputStream(fileInStream);

		int bytesRead = bufferInStream.read(chunk);

		chunkObj.setChunkSize(bytesRead);

		chunkObj.setFileData(chunk);

		bufferInStream.close();
		fileInStream.close();

		return chunkObj;
	}

	public void sendChunkObject(ChunkerFileObject sChunkObj) {
		try {

			outStream.writeObject(sChunkObj);
			outStream.flush();
			System.out.println("Sending chunk Id: "+ chunkId + " to Peer with Port Number: "+"["+incomingPort+"]");
			System.out.println(".....Sending chunk complete.....\n");
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void TCPServDisconnect() {
		try {
			outStream.close();
			socket.close();
			System.out.println("Main Server socket closed with peer with Port Number: "+"["+incomingPort+"]");
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
