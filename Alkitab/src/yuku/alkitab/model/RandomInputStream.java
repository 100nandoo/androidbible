package yuku.alkitab.model;

import java.io.*;

public class RandomInputStream extends InputStream {
	private RandomAccessFile f;

	public RandomInputStream(RandomAccessFile f) {
		this.f = f;
	}
	
	@Override
	public int read(byte[] b) throws IOException {
		return f.read(b);
	}
	
	@Override
	public int read(byte[] b, int offset, int length) throws IOException {
		return f.read(b, offset, length);
	}
	
	@Override
	public int read() throws IOException {
		return f.read();
	}

}
