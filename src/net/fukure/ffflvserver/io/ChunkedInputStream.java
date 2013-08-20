package net.fukure.ffflvserver.io;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;


public class ChunkedInputStream {
	
	private InputStream in;
	
	public ChunkedInputStream(InputStream in) {
		this.in = in;
	}
	
	private int readChunkSize() throws IOException{
		String hexSize = readline();
		//System.out.println("readChunkSize "+hexSize+" "+new String(RecvSocData.hexString(hexSize.getBytes(), 3200)));
		int size = Integer.parseInt (hexSize, 16);
		if (size <= 0){
			return 0;
		}else{
			return size;
		}
	}
	
	private byte[] readChunkBody(int size) throws IOException{
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		byte[] data = new byte[size];
		int r = 0;
		while(baos.size()<size){
			data = new byte[size-baos.size()];
			r = in.read(data);
			baos.write(data,0,r);
		}
		//System.out.println("readChunkBody "+new String(RecvSocData.hexString(data, 3200)));
		//System.out.println("readChunkBody data read "+r+" baos size "+baos.size());
		readline();//RCLF
		return baos.toByteArray();
	}
	
	public byte[] readChunk() throws IOException{
		int size = readChunkSize();
		return readChunkBody(size);
	}
	
	public String readline() throws IOException {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();

		int r = 0;
	    do {
	    	r = in.read();
	    	baos.write(r);
	    	if(baos.size()>=64*1024){
	    		//System.out.println("error"+baos.toString());
	    		baos.reset();
	    	}
	    } while (!(r==10));
	    return baos.toString().trim();
	}
	
}
