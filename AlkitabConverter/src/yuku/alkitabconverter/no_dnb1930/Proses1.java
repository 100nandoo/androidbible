package yuku.alkitabconverter.no_dnb1930;

import java.io.RandomAccessFile;
import java.nio.charset.Charset;
import java.util.ArrayList;

import yuku.alkitab.yes.YesFile;
import yuku.alkitab.yes.YesFile.InfoEdisi;
import yuku.alkitab.yes.YesFile.InfoKitab;
import yuku.alkitab.yes.YesFile.PericopeData;
import yuku.alkitab.yes.YesFile.PericopeData.Entry;
import yuku.alkitab.yes.YesFile.PerikopBlok;
import yuku.alkitab.yes.YesFile.PerikopIndex;
import yuku.alkitab.yes.YesFile.Teks;
import yuku.alkitabconverter.bdb.BdbProses;
import yuku.alkitabconverter.bdb.BdbProses.PericopeTester;
import yuku.alkitabconverter.util.Ari;
import yuku.alkitabconverter.util.Rec;
import yuku.alkitabconverter.util.RecUtil;
import yuku.alkitabconverter.yes_common.YesCommon;

public class Proses1 {
	static String INPUT_TEKS_1 = "./bahan/no-dnb1930/in/b_dnb30.txt";
	public static String INPUT_TEKS_ENCODING = "utf-8";
	public static int INPUT_TEKS_ENCODING_YES = 2; // 1: ascii; 2: utf-8;
	public static String INPUT_KITAB = "./bahan/no-dnb1930/in/books_name-nonums.txt";
	static String OUTPUT_YES = "./bahan/no-dnb1930/out/no-dnb1930.yes";
	public static int OUTPUT_ADA_PERIKOP = 1;
	static String INFO_NAMA = "no-dnb1930";
	static String INFO_JUDUL = "Det Norsk Bibelselskap";
	static String INFO_KETERANGAN = "Det Norsk Bibelselskap (1930)";

	final Charset utf8 = Charset.forName("utf-8");
	
	public static void main(String[] args) throws Exception {
		new Proses1().u();
	}

	private void u() throws Exception {
		BdbProses bdbProses = new BdbProses();
		bdbProses.setCombineSameVerse(true);
		bdbProses.setPericopeTester(new PericopeTester() {
			@Override public Entry getPericopeEntry(int kitab_1, int pasal_1, int ayat_1, String isi) {
				if (!isi.startsWith("  ")) { // special: 2 spaces before
					return null;
				}
				
				PericopeData.Entry res = new PericopeData.Entry();
				res.ari = Ari.encode(kitab_1 - 1, pasal_1, ayat_1);
				res.block = new PericopeData.Block();
				res.block.version = 2;
				res.block.title = isi.trim();
				return res;
			}
		});
		
		ArrayList<Rec> xrec = bdbProses.parse(INPUT_TEKS_1, "latin1");
		PericopeData pericopeData = bdbProses.getPericopeData();
		
		// remove stray @
		for (Rec rec: xrec) {
			if (rec.text.contains("@")) rec.text = rec.text.replace("@", "");
		}
		
		System.out.println("Total verses: " + xrec.size());

		////////// PROSES KE YES
		final InfoEdisi infoEdisi = YesCommon.infoEdisi(INFO_NAMA, null, INFO_JUDUL, RecUtil.hitungKitab(xrec), OUTPUT_ADA_PERIKOP, INFO_KETERANGAN, INPUT_TEKS_ENCODING_YES, null);
		final InfoKitab infoKitab = YesCommon.infoKitab(xrec, INPUT_KITAB, INPUT_TEKS_ENCODING, INPUT_TEKS_ENCODING_YES);
		final Teks teks = YesCommon.teks(xrec, INPUT_TEKS_ENCODING);
		
		YesFile file = YesCommon.bikinYesFile(infoEdisi, infoKitab, teks, new PerikopBlok(pericopeData), new PerikopIndex(pericopeData));
		
		file.output(new RandomAccessFile(OUTPUT_YES, "rw"));
	}
}
