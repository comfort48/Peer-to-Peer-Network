package peer1;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ConnectException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import chunker.ChunkerFileObject;

public class Peer1 {

	static String rootDirectory = System.getProperty("user.dir");
	static String client1_location = rootDirectory + "/peer1";
	static String chunksLocation = client1_location + "/chunks";

	ServerSocket receiveSocket;
	Socket connectionSocket;
	Socket clientSocket;
	ObjectInputStream inStream;
	ObjectOutputStream outStream;
	Set<Integer> chunkList;
	private int mainServerPort;
	private int clientServerPort;
	static int clientNeighborPort;

	public static void main(String[] args) {

		Peer1 c = new Peer1();

		new File(client1_location + "/chunks/").mkdirs();

		int totalFilesToReceive;
		try {
			c.mainServerPort = Integer.parseInt(args[0]);
			c.TCPCliConnect(c.mainServerPort);
			c.outStream.writeObject(args[1]);
			totalFilesToReceive = (int) c.inStream.readObject();
			clientNeighborPort = c.mainServerPort;
			c.chunkList = Collections.synchronizedSet(new LinkedHashSet<Integer>());
			for (int i = 1; i <= totalFilesToReceive; i++)
				c.chunkList.add(i);

			int filesToReceive = (int) c.inStream.readObject();
			while (filesToReceive > 0) {
				ChunkerFileObject rChunkObj = c.receiveChunk();
				if (rChunkObj != null)
					c.createChunkFile(chunksLocation, rChunkObj);
				else
					System.out.println("Chunk received is null!");
				filesToReceive--;
			}
			c.TCPCliDisconnect();

			c.clientServerPort = Integer.parseInt(args[1]);
			Thread thread = new Thread(new Runnable() {
				public void run() {
					c.TCPCliServconnect(c.clientServerPort);

				}
			});
			thread.start();
			clientNeighborPort = Integer.parseInt(args[2]);
			c.TCPCliConnect(clientNeighborPort);
			c.outStream.writeObject(c.clientServerPort);
			System.out.println("Client1 totalFilesToReceive: " + totalFilesToReceive);

			while (true) {
				System.out.println("Client1 files to download: " + c.chunkList);
				if (!c.chunkList.isEmpty()) {
					Integer[] a = c.chunkList.toArray(new Integer[c.chunkList.size()]);

					for (int i = 0; i < a.length; i++) {
						c.outStream.writeObject(a[i]);
						c.outStream.flush();
						ChunkerFileObject rChunkObj = c.receiveChunk();
						if (rChunkObj != null)
							c.createChunkFile(chunksLocation, rChunkObj);
					}
				} else {
					c.outStream.writeObject(-1);
					c.outStream.flush();
					break;
				}
				Thread.sleep(2000);
			}
			c.TCPCliDisconnect();
			c.combineChunks();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void TCPCliServconnect(int port) {
		try {
			int neighbourCount = 1;
			receiveSocket = new ServerSocket(port);
			System.out.println(this.getClass().getName() + " Client1-Server socket created, accepting connections...");
			while (true) {
				if (neighbourCount > 0) {
					neighbourCount--;
					connectionSocket = receiveSocket.accept();
					System.out.println("New client connection accepted: " + connectionSocket);
					// Client-Server thread creation and sending n files to handle
					new CliServerThread(connectionSocket, chunksLocation).start();

				} else {
					System.out.println("Cannot serve more clients!");
					break;
				}

			}

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void TCPCliConnect(int port) throws InterruptedException {
		boolean tcpFlag = true;
		while (tcpFlag) {
			try {

				tcpFlag = false;
				clientSocket = new Socket("127.0.0.1", port);
				System.out.println("Client1 connected to : " + clientSocket);
				inStream = new ObjectInputStream(clientSocket.getInputStream());
				outStream = new ObjectOutputStream(clientSocket.getOutputStream());
			} catch (ConnectException e) {

				System.out.println("Unable to connect to socket at: " + port + "... trying again...");
				Thread.sleep(5000);
				tcpFlag = true;
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	public ChunkerFileObject receiveChunk() {
		ChunkerFileObject chunkObj = null;
		try {
			chunkObj = (ChunkerFileObject) inStream.readObject();
			if(chunkObj != null) {
				System.out.println("Received Chunk Id: "+chunkObj.getSequenceNumber()+" from Peer with port: "+"["+clientNeighborPort+"]");
			}
		} 
		catch (Exception e) {
			e.printStackTrace();
		}
		return chunkObj;
	}

	public void TCPCliDisconnect() {
		try {
			inStream.close();
			clientSocket.close();
			System.out.println("Client1 connection closed!");
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public synchronized void createChunkFile(String chunksLocation, ChunkerFileObject rChunkObj) {
		try {
			System.out.println("Construct received chunk - " + rChunkObj.getFileName());
			FileOutputStream fileOutStream = new FileOutputStream(new File(chunksLocation, rChunkObj.getFileName()));
			BufferedOutputStream bufferOutStream = new BufferedOutputStream(fileOutStream);
			bufferOutStream.write(rChunkObj.getFileData(), 0, rChunkObj.getChunkSize());

			chunkList.remove(rChunkObj.getSequenceNumber());

			bufferOutStream.flush();
			bufferOutStream.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void combineChunks() {

		String chunksLocation = client1_location + "/chunks";
		File[] files = new File(chunksLocation).listFiles();
		byte[] chunk = new byte[102400];
		try {
			FileOutputStream fileOutStream = new FileOutputStream(
					new File(client1_location + "/" + (files[0].getName()).split("_")[1]));
			BufferedOutputStream bufferOutStream = new BufferedOutputStream(fileOutStream);
			for (File f : files) {
				FileInputStream fileInStream = new FileInputStream(f);
				BufferedInputStream bufferInStream = new BufferedInputStream(fileInStream);
				int bytesRead = 0;
				while ((bytesRead = bufferInStream.read(chunk)) > 0) {
					bufferOutStream.write(chunk, 0, bytesRead);
				}
				fileInStream.close();
			}
			fileOutStream.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}

class CliServerThread extends Thread {

	private Socket socket;
	ObjectOutputStream outStream;
	ObjectInputStream inStream;
	String chunkLoc;
	int uploadNeighborPort;
	int ChunkNum;

	CliServerThread(Socket s, String chunkLoc) {
		this.socket = s;
		this.chunkLoc = chunkLoc;
	}

	public void run() {
		try {
			outStream = new ObjectOutputStream(socket.getOutputStream());
			inStream = new ObjectInputStream(socket.getInputStream());
			uploadNeighborPort = (int) inStream.readObject();

			while (true) {
				ChunkNum = (int) inStream.readObject();
				if (ChunkNum < 0)
					break;
				File[] files = new File(chunkLoc).listFiles();
				String[] fileName;
				File currentFile = null;
				boolean haveFile = false;
				for (int i = 0; i < files.length; i++) {
					currentFile = files[i];
					fileName = files[i].getName().split("_");
					if (ChunkNum == Integer.parseInt(fileName[0])) {
						haveFile = true;
						break;
					}
				}
				ChunkerFileObject sChunkObj;
				if (haveFile) {
					sChunkObj = constructChuckFileObject(currentFile, ChunkNum);
				} else {
					sChunkObj = constructChuckFileObject(null, -1);
				}
				sendChunkObject(sChunkObj);
			}
			TCPServDisconnect();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public synchronized ChunkerFileObject constructChuckFileObject(File file, int chunkNum) throws IOException {
		ChunkerFileObject chunkObj = null;
		if (chunkNum > 0) {
			byte[] chunk = new byte[102400];
			chunkObj = new ChunkerFileObject();
			System.out.println("Construct chunk object to send to other peers - " + file.getName());

			chunkObj.setSequenceNumber(chunkNum);

			chunkObj.setFileName(file.getName());
			FileInputStream fileInStream = new FileInputStream(file);

			BufferedInputStream bufferInStream = new BufferedInputStream(fileInStream);

			int bytesRead = bufferInStream.read(chunk);

			chunkObj.setChunkSize(bytesRead);

			chunkObj.setFileData(chunk);

			bufferInStream.close();
			fileInStream.close();
		}
		return chunkObj;
	}

	public void sendChunkObject(ChunkerFileObject sChunkObj) {
		try {
			if(sChunkObj != null) {
				System.out.println("Sending Chunk number: "+ChunkNum+" to peer with Port: " +"["+uploadNeighborPort+"]");
			}
			else {
				System.out.println("Don't have requested chunk: "+ChunkNum+" at this time, Try again later!");
			}
			outStream.writeObject(sChunkObj);
			outStream.flush();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public synchronized void TCPServDisconnect() {
		try {
			outStream.close();
			socket.close();
			System.out.println("Client1 - server socket closed " +" with Peer Port "+"["+uploadNeighborPort+"]");
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
