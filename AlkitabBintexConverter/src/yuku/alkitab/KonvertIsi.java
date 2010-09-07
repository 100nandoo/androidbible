package yuku.alkitab;

import java.io.*;
import java.util.Scanner;

import yuku.bintex.BintexWriter;

public class KonvertIsi {
	public static void main(String[] args) throws Exception {
		String nama = args[0];
		
		new KonvertIsi().convert("../Alkitab/publikasi/" + nama + "_index.txt", "../Alkitab/publikasi/" + nama + "_raw/" + nama + "_index_bt.bt");
	}

	private void convert(String nfi, String nfo) throws Exception {
		Scanner sc = new Scanner(new File(nfi));
		FileOutputStream os = new FileOutputStream(nfo);
		
		BintexWriter writer = new BintexWriter(os);
		
		while (sc.hasNext()) {
			String kitab = sc.next();
			
			writer.writeShortString(kitab);
			int npasal = 0;
			
			while (true) {
				String key = sc.next();
				
				writer.writeShortString(key);
				
				if (key.equals("npasal")) { // value: int
					npasal = sc.nextInt();
					
					writer.writeInt(npasal);
				} else if (key.equals("nayat")) { // value: uint8[]
					for (int i = 0; i < npasal; i++) {
						writer.writeUint8(sc.nextInt());
					}
				} else if (key.equals("pasal_offset")) { // value: int[]
					for (int i = 0; i < npasal; i++) {
						writer.writeInt(sc.nextInt());
					}
				} else if (key.equals("uda")) { // value: ga ada
					break;
				} else { // value: String
					writer.writeShortString(sc.next());
				}
			}
			
		}
		
		writer.close();
	}
}
