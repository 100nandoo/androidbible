package yuku.alkitabconverter.bdb;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map.Entry;
import java.util.Scanner;

public class BdbProses {
	public static class Rec implements Comparable<Rec> {
		public int kitab_1;
		public int pasal_1;
		public int ayat_1;
		public String isi;
		
		@Override public int compareTo(Rec o) {
			if (this.kitab_1 != o.kitab_1) return this.kitab_1 - o.kitab_1;
			if (this.pasal_1 != o.pasal_1) return this.pasal_1 - o.pasal_1;
			if (this.ayat_1 != o.ayat_1) return this.ayat_1 - o.ayat_1;
			return 0;
		}
	}
	
	public ArrayList<Rec> parse(String nf, String charsetName) throws Exception {
		LinkedHashMap<Integer, Integer> nn = new LinkedHashMap<Integer, Integer>();
		ArrayList<Rec> res = new ArrayList<Rec>();
		
		Scanner sc = new Scanner(new File(nf), charsetName);
		
		int lastNo = 0;
		int lastKitab_1 = 1;
		int lastPasal_1 = 1;
		int lastAyat_1 = 0;
		
		while (sc.hasNextLine()) {
			String baris = sc.nextLine();
			
			String[] xkolom = baris.split("\t");
			int no = xkolom[0].startsWith("tambah")? 0: Integer.parseInt(xkolom[0]);
			int kitab_1 = Integer.parseInt(xkolom[1]);
			int pasal_1 = Integer.parseInt(xkolom[2]);
			int ayat_1 = Integer.parseInt(xkolom[3]);
			String isi = xkolom[4];
			if (xkolom.length != 5) {
				throw new RuntimeException("kolom ngaco");
			}
			
			if (ayat_1 != lastAyat_1 + 1) {
				if (pasal_1 != lastPasal_1 + 1) {
					if (kitab_1 != lastKitab_1 + 1) {
						throw new RuntimeException("urutan ngaco");
					}
				}
			}
			
			if (no != lastNo + 1) {
				System.out.println("no ngaco: " + no + "; ini gapapa kalo emang sengaja");
			}
			
			nn.put(kitab_1, (nn.get(kitab_1) == null? 0: nn.get(kitab_1)) + 1);
			
			Rec rec = new Rec();
			rec.kitab_1 = kitab_1;
			rec.pasal_1 = pasal_1;
			rec.ayat_1 = ayat_1;
			rec.isi = isi;
			
			res.add(rec);
			
			lastNo = no;
			lastKitab_1 = kitab_1;
			lastPasal_1 = pasal_1;
			lastAyat_1 = ayat_1;
		}
		
		for (Entry<Integer, Integer> e: nn.entrySet()) {
			System.out.println(e.getKey() + ": " + e.getValue());
		}
		
		System.out.println("selesai");
		
		return res;
	}

}
