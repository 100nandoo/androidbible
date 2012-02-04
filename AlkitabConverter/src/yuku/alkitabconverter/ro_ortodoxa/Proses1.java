package yuku.alkitabconverter.ro_ortodoxa;

import java.io.File;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

import yuku.alkitab.yes.YesFile;
import yuku.alkitab.yes.YesFile.InfoEdisi;
import yuku.alkitab.yes.YesFile.InfoKitab;
import yuku.alkitab.yes.YesFile.Teks;
import yuku.alkitabconverter.bdb.BdbProses.Rec;
import yuku.alkitabconverter.util.Hitungan31102;
import yuku.alkitabconverter.util.RecUtil;
import yuku.alkitabconverter.yes_common.YesCommon;

public class Proses1 {
	static String INPUT_TEKS_1 = "./bahan/ro-ortodoxa/in/ro-ortodoxa-bare.txt";
	public static String INPUT_TEKS_ENCODING = "utf-8";
	public static int INPUT_TEKS_ENCODING_YES = 2; // 1: ascii; 2: utf-8;
	public static String INPUT_KITAB = "./bahan/ro-ortodoxa/in/ro-ortodoxa-kitab.txt";
	static String OUTPUT_YES = "./bahan/ro-ortodoxa/out/ro-ortodoxa.yes";
	public static int OUTPUT_ADA_PERIKOP = 0;
	static String INFO_NAMA = "ro-ortodoxa";
	static String INFO_JUDUL = "Biblia Ortodoxă";
	static String INFO_KETERANGAN = "Biblia Ortodoxă (Biblia Sinodală / Bible of the Holy Synod)";

	List<Rec> xrec = new ArrayList<Rec>();

	public static void main(String[] args) throws Exception {
		new Proses1().u();
	}

	private void u() throws Exception {
		Scanner sc = new Scanner(new File(INPUT_TEKS_1), INPUT_TEKS_ENCODING);
		
		List<Rec> xrec = new ArrayList<Rec>();
		int offset_0 = 0;
		
		while (sc.hasNextLine()) {
			String line = sc.nextLine();
			line = line.trim();
			
			// ayat
			Rec rec = new Rec();
			rec.kitab_1 = Hitungan31102.kitab_1(offset_0);
			rec.pasal_1 = Hitungan31102.pasal_1(offset_0);
			rec.ayat_1 = Hitungan31102.ayat_1(offset_0);
			rec.isi = line;
			
			xrec.add(rec);
			offset_0++;
		}
		
		System.out.println("Total verses: " + xrec.size());

		////////// PROSES KE YES

		final InfoEdisi infoEdisi = YesCommon.infoEdisi(INFO_NAMA, INFO_JUDUL, RecUtil.hitungKitab(xrec), OUTPUT_ADA_PERIKOP, INFO_KETERANGAN, INPUT_TEKS_ENCODING_YES);
		final InfoKitab infoKitab = YesCommon.infoKitab(xrec, INPUT_KITAB, INPUT_TEKS_ENCODING, INPUT_TEKS_ENCODING_YES);
		final Teks teks = YesCommon.teks(xrec, INPUT_TEKS_ENCODING);
		
		YesFile file = YesCommon.bikinYesFile(infoEdisi, infoKitab, teks);
		
		file.output(new RandomAccessFile(OUTPUT_YES, "rw"));
	}
}
