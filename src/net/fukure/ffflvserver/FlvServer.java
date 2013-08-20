package net.fukure.ffflvserver;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Random;
import java.util.TreeMap;

import net.fukure.ffflvserver.io.ChunkedInputStream;
import net.fukure.ffflvserver.io.Util;

public class FlvServer extends Thread{

	private boolean debug = false;
	
	private ServerSocket httpServer = null;
	private Socket clientSocket = null;
	private int defaultPort = 8080;
	private int port = defaultPort;
	
	private byte[] flvHeader = null;
	private TreeMap<Integer, byte[]> flvDataMap = new TreeMap<Integer, byte[]>();

	
	public static void main(String[] args) {
		FlvServer server = new FlvServer();
		try{
			if(args.length>0){
				server.port = Integer.parseInt(args[0]);
				if(server.port<1024) server.port = server.defaultPort;
				if(server.port>65535) server.port = server.defaultPort;
			}
		}catch(Exception e){
			server.port = server.defaultPort;
		}
		server.start();
	}
	@Override
	public void run() {
 
        try {
            System.out.println("http:"+port);
            httpServer = new ServerSocket(port);
            
            while(true){
            	clientSocket = httpServer.accept();
            	new Client(this, clientSocket);
            }
        }
        catch (Exception e) {
            System.out.println(e);
        }
	}
	
    class Client extends Thread{

		private FlvServer server;
    	private Socket socket = null;
        private BufferedInputStream is;
        private BufferedOutputStream os;
        private FileOutputStream fos = null;
        
    	public Client(FlvServer server, Socket clientSocket) {
    		this.server = server;
    		this.socket = clientSocket;
    		start();
		}
    	
    	@Override
		public void run() {
			try {
				System.out.println("http open:"+socket);
				
				is = new BufferedInputStream(socket.getInputStream());
		        os = new BufferedOutputStream(socket.getOutputStream());
		
		        ChunkedInputStream cin = new ChunkedInputStream(is);
		        String httpHeader = readHttpHeader(cin);
		        
		        if(httpHeader.startsWith("GET")){
		        	//peercaststation, player
		        	if(!isReady()){
						os.write("HTTP/1.1 400 Bad Request\r\n\r\n".getBytes());
						os.flush();
		        	}else{
						os.write("HTTP/1.1 200 OK\r\n".getBytes());
						os.write("Content-Type: video/x-flv\r\n\r\n".getBytes());
						os.flush();
						
		            	if(debug) fos = new FileOutputStream(new File("player.flv"));
		
		                playerSendDataLoop();
		        	}
		        }
		        if(httpHeader.startsWith("POST")){
		        	//ffmpeg
					if(debug) fos = new FileOutputStream(new File("ffmpeg.flv"));
					
					ffmpegReceiveLoop(cin);
		        }
		        
			} catch (Exception e) {
				//e.printStackTrace();
			}
		
			try {
				System.out.println("http close:"+socket);
				is.close();
		        os.close();
				if(debug) fos.close();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		private String readHttpHeader(ChunkedInputStream cin) throws IOException{
            StringBuffer sb = new StringBuffer();
    		String line = "";
            while((line = cin.readline()) != null){
            	if(line.length()==0) break;
	    		if(sb.length()>64*1024) continue;
            	sb.append(line).append("\r\n");
            }
            return sb.toString();
    	}
		
		void playerSendDataLoop() throws Exception{

			if(server.flvHeader==null) return;
			
			os.write(server.flvHeader);
        	os.flush();
        	if(debug) fos.write(server.flvHeader);
        	
            int latest = server.flvDataMap.lastKey();
            int sleep = 0;
            while(true){
            	if(server.flvDataMap.size()==0) break;
            	if(latest<server.flvDataMap.firstKey()) break;
            	byte[] data = server.flvDataMap.get(latest);
            	if(data!=null){
                	os.write(data);
                	os.flush();
                	if(debug) fos.write(data);
                	Thread.sleep(10);
                	sleep = 0;
                	latest++;
            	}else{
                	Thread.sleep(50);
                	sleep++;
                	if(sleep>200){
                		break;
                	}
            	}
            }
		}
		
		void ffmpegReceiveLoop(ChunkedInputStream cin) throws Exception{
			
			int frameCount = 0;
			ByteArrayOutputStream flvTagData = new ByteArrayOutputStream();
		
			byte[] chunkedData = cin.readChunk();
			
			while(chunkedData.length>0){
		
				//System.out.println("player -> server "+chunkedData.length);
				if(debug){
		        	fos.write(chunkedData);
		        	fos.flush();
				}
				
				if(FlvChunk.isFileHeader(chunkedData)){
					flvHeader = chunkedData;
					flvDataMap.clear();
				}else{
		
					flvTagData.write(chunkedData);
					
					byte[] flvTagBytes = flvTagData.toByteArray();
					ByteArrayInputStream bais = new ByteArrayInputStream(flvTagBytes);
					int type = bais.read();
					int flvDataSize = Util.readInt3bytes(bais);
					
					if(FlvChunk.isValidType(type)){
						//detasize check
						if(flvDataSize==flvTagData.size()-11-4){
							//prev tagsize check
							if(FlvChunk.getPrevTagSize(chunkedData)==flvTagData.size()-4){
								addFlvTag(frameCount++, flvTagBytes);
								flvTagData.reset();
							}
						}
					}else{
						flvTagData.reset();
					}
					
					bais.close();
					
				}
		    	
				chunkedData = cin.readChunk();
			}
			flvDataMap.clear();
		}

		void addFlvTag(int frameCount, byte[] data){
			flvDataMap.put(frameCount, data);
			if(flvDataMap.size()>100){
				flvDataMap.remove(flvDataMap.firstKey());
			}
			
        	randomOutput(data);
		}
		
		void randomOutput(byte[] data){
			if(new Random().nextInt(100)<5){
				System.out.println(Util.hexString(data, 16));
			}
		}
		
		boolean isReady(){
        	if(server.flvDataMap.size()==0 || server.flvHeader==null){
        		return false;
        	}else{
        		return true;
        	}
		}
    	
    }
}
