package net.fukure.ffflvserver;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

import net.fukure.ffflvserver.io.Util;


public class FlvChunk {
	
	long startTime = 0;
	boolean record = false;
	FileOutputStream fos = null;
	
	public static void main(String[] args) throws Exception {
		FlvChunk flv = new FlvChunk(true);
		flv.test();
	}
	
	void test() throws Exception{
		Socket socket = new Socket("localhost", 8080);
        InputStream is = socket.getInputStream();
        OutputStream os = socket.getOutputStream();
        
        os.write("GET /stream/41b7622573028ffcc9101f06c8c66d9b.flv HTTP/1.1\r\n".getBytes());
        os.write("Accept: */*\r\n".getBytes());
        os.write("Host: localhost\r\n".getBytes());
        os.write("User-Agent: NSPlayer/4.1.0.3856\r\n".getBytes());
        os.write("Connection:close\r\n\r\n".getBytes());
        os.flush();
        
        while(true){
        	String line = Util.readline(is);
        	System.out.println(line);
        	if(line.equals("")) break;
        }
        
        try{
	        FlvChunk flv = new FlvChunk(record);
	        byte[] header = flv.readFlvHeader(is);
        	System.out.println(Util.hexString(header, 13));
	        while(true){
	        	byte[] data = flv.readFlvData(is);
	        	System.out.println(Util.hexString(data, 16));
	        }
        }catch(Exception e){
        	e.printStackTrace();
        }
        
        is.close();
        os.close();
        socket.close();
        
	}
	
	public FlvChunk(boolean record) throws Exception {
		this.record = record;
		if(record){
			File outFile = new File("out3.flv");
			fos = new FileOutputStream(outFile);
			fos.close();
			fos = new FileOutputStream(outFile, true);
		}
	}
	
	static boolean isFileHeader(byte[] data){
		if(data[0]==0x46 && data[1]==0x4c && data[2]==0x56){
			return true;
		}else{
			return false;
		}
	}
	
	static boolean isValidType(int type){
		if(type==0x08 || type==0x09 || type==0x12){
			return true;
		}else{
			return false;
		}
	}

	static int getPrevTagSize(byte[] data){
		byte[] prevTagSizeBytes = new byte[4];
		System.arraycopy(data, data.length-4, prevTagSizeBytes, 0, 4);
		return Util.intFrom4bytes(prevTagSizeBytes);
		//System.out.println(Util.hexString(prevSize, 16));
	}
	
	byte[] readFlvHeader(InputStream is) throws IOException{
		
		byte[] header = Util.readBytes(is, 13);
		
		if(record){
			fos.write(header);
			fos.flush();
		}
		
		if(new String(header,0,3).equals("FLV")){
			return header;
		}
		throw new IOException();
	}
	byte[] readFlvData(InputStream is) throws IOException{

		if(startTime==0){
			startTime = System.currentTimeMillis();
		}
		
		int timestamp = (int) (System.currentTimeMillis() - startTime);
		
		int type = Util.readByte(is);
		if(type!=0x08 && type!=0x09 && type!=0x12) throw new IOException();
		
		byte[] sizeBytes = Util.readBytes(is, 3);
		byte[] timestampBytes = Util.readBytes(is, 4);
		timestampBytes[0] = (byte) ((timestamp >> 16) & 0xff);
		timestampBytes[1] = (byte) ((timestamp >> 8) & 0xff);
		timestampBytes[2] = (byte) (timestamp & 0xff);
		timestampBytes[3] = (byte) (timestamp >> 24 & 0xff);
		byte[] streamIDBytes = Util.readBytes(is, 3);
		
		int size = Util.intFrom3bytes(sizeBytes);
		if(size>4*1024*1024) throw new IOException();
		
		byte[] data = Util.readBytes(is, size);

		byte[] prevSizeBytes = Util.readBytes(is, 4);
		int prevSize = Util.intFrom4bytes(prevSizeBytes);
		
		if(size+11!=prevSize) throw new IOException();
		
		if(record){
			fos.write(type);
			fos.write(sizeBytes);
			fos.write(timestampBytes);
			fos.write(streamIDBytes);
			fos.write(data);
			fos.write(prevSizeBytes);
			fos.flush();
		}
		
		return data;
	}
	byte[] getFlvHeader() throws IOException{
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		baos.write((byte)0x46);
		baos.write((byte)0x4c);
		baos.write((byte)0x56);
		baos.write((byte)0x1);
		baos.write((byte)0x5);
		baos.write((byte)0x0);
		baos.write((byte)0x0);
		baos.write((byte)0x0);
		baos.write((byte)0x9);
		baos.write((byte)0x0);
		baos.write((byte)0x0);
		baos.write((byte)0x0);
		baos.write((byte)0x0);
		
		if(record){
			fos.write(baos.toByteArray());
		}
		
		return baos.toByteArray();
	}
	byte[] getFlvData(int type, byte[] data) throws IOException{
		
		if(startTime==0){
			startTime = System.currentTimeMillis();
		}
		int timestamp = (int) (System.currentTimeMillis() - startTime);
	
		byte[] size = Util.intTo3bytes(data.length);
		byte[] time = Util.intTo3bytes(timestamp & 0xffffff);
		int ext = (timestamp >>> 24);
		byte[] streamID = Util.intTo3bytes(0);
		byte[] prevSize = Util.intTo4bytes(data.length + 11);
	
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		baos.write((byte)type);
		baos.write(size);
		baos.write(time);
		baos.write((byte)ext);
		baos.write(streamID);
		baos.write(data);
		baos.write(prevSize);
		
		if(record){
			fos.write(baos.toByteArray());
		}
		
		return baos.toByteArray();
	}

	void close() throws IOException{
		if(record){
			fos.close();
		}
	}
}
