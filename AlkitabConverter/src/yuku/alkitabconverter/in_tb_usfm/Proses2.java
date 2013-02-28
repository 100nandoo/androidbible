package yuku.alkitabconverter.in_tb_usfm;

import java.io.File;
import java.io.FileInputStream;
import java.io.FilenameFilter;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import java.util.TreeMap;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.ext.DefaultHandler2;

import yuku.alkitab.base.model.Ari;
import yuku.alkitab.yes1.Yes1File;
import yuku.alkitab.yes1.Yes1File.InfoEdisi;
import yuku.alkitab.yes1.Yes1File.InfoKitab;
import yuku.alkitab.yes1.Yes1File.PericopeData;
import yuku.alkitab.yes1.Yes1File.PericopeData.Entry;
import yuku.alkitab.yes1.Yes1File.PerikopBlok;
import yuku.alkitab.yes1.Yes1File.PerikopIndex;
import yuku.alkitab.yes1.Yes1File.Teks;
import yuku.alkitabconverter.in_tb_usfm.XrefDb.XrefEntry;
import yuku.alkitabconverter.internal_common.InternalCommon;
import yuku.alkitabconverter.internal_common.ReverseIndexer;
import yuku.alkitabconverter.util.DesktopVerseFinder;
import yuku.alkitabconverter.util.DesktopVerseParser;
import yuku.alkitabconverter.util.IntArrayList;
import yuku.alkitabconverter.util.Rec;
import yuku.alkitabconverter.util.RecUtil;
import yuku.alkitabconverter.util.TextDb;
import yuku.alkitabconverter.util.TextDb.TextProcessor;
import yuku.alkitabconverter.util.TextDb.VerseState;
import yuku.alkitabconverter.yes_common.Yes1Common;

public class Proses2 {
	final SAXParserFactory factory = SAXParserFactory.newInstance();
	
	public static String INPUT_TEKS_ENCODING = "utf-8";
	public static int INPUT_TEKS_ENCODING_YES = 2; // 1: ascii; 2: utf-8;
	public static String INPUT_KITAB = "./bahan/in-tb-usfm/in/in-tb-usfm-kitab.txt";
	static String OUTPUT_YES = "./bahan/in-tb-usfm/out/in-tb-usfm.yes";
	public static int OUTPUT_ADA_PERIKOP = 1;
	static String INFO_NAMA = "in-tb-usfm";
	static String INFO_SHORT_NAME = "TB";
	static String INFO_LONG_NAME = "Terjemahan Baru";
	static String INFO_KETERANGAN = "Terjemahan Baru (1974), Lembaga Alkitab Indonesia";
	static String INPUT_TEKS_2 = "./bahan/in-tb-usfm/mid/"; 


	TextDb teksDb = new TextDb();
	StringBuilder misteri = new StringBuilder();
	XrefDb xrefDb = new XrefDb();
	
	PericopeData pericopeData = new PericopeData();
	{
		pericopeData.entries = new ArrayList<Entry>();
	}

	public static void main(String[] args) throws Exception {
		new Proses2().u();
	}

	public void u() throws Exception {
		String[] files = new File(INPUT_TEKS_2).list(new FilenameFilter() {
			@Override public boolean accept(File dir, String name) {
				return name.endsWith("-utf8.usfx.xml");
			}
		});
		
		Arrays.sort(files);
		

		for (String file : files) {
			System.out.println("file " + file + " start;");
			
			FileInputStream in = new FileInputStream(new File(INPUT_TEKS_2, file));
			SAXParser parser = factory.newSAXParser();
			XMLReader r = parser.getXMLReader();
			System.out.println("input buffer size (old) = " + r.getProperty("http://apache.org/xml/properties/input-buffer-size"));
			r.setProperty("http://apache.org/xml/properties/input-buffer-size", 1048576);
			System.out.println("input buffer size (new) = " + r.getProperty("http://apache.org/xml/properties/input-buffer-size"));
			r.setFeature("http://xml.org/sax/features/namespaces", true);
			parser.parse(in, new Handler(Integer.parseInt(file.substring(0, 2))));
			
			System.out.println("file " + file + " done; now total rec: " + teksDb.size());
		}
		
		System.out.println("OUTPUT MISTERI:");
		System.out.println(misteri);
		
		System.out.println("OUTPUT XREF:");

		xrefDb.processEach(new XrefDb.XrefProcessor() {
			@Override public void process(XrefEntry xe, int ari, int entryIndex) {
				final List<int[]> pairs = new ArrayList<>();
				DesktopVerseFinder.findInText(xe.target, new DesktopVerseFinder.DetectorListener() {
					@Override public boolean onVerseDetected(int start, int end, String verse) {
						pairs.add(new int[] {start, end});
						return true;
					}
					
					@Override public void onNoMoreDetected() {
					}
				});
				
				String target = xe.target;
				for (int i = pairs.size() - 1; i >= 0; i--) {
					int[] pair = pairs.get(i);
					String verse = target.substring(pair[0], pair[1]);
					
					IntArrayList ariRanges = DesktopVerseParser.verseStringToAriWithShiftTb(verse);
					
					target = target.substring(0, pair[0]) + "@<" + ariRanges + "@>" + verse + "@/" + target.substring(pair[1]);
				}
				
				xe.target = target;
			}
		});
		
		xrefDb.dump();
		
		// POST-PROCESS
		
		teksDb.normalize();

		teksDb.processEach(new TextProcessor() {
			@Override public void process(int ari, VerseState as) {
				// tambah @@ kalo perlu
				if (as.text.contains("@") && !as.text.startsWith("@@")) {
					as.text = "@@" + as.text;
				}
				
				// patch for "S e l a", "S el a", and "H i g a y o n"
				as.text = as.text.replace("S e l a", "Sela");
				as.text = as.text.replace("S el a", "Sela");
				as.text = as.text.replace("H i g a y o n", "Higayon");
				
				// patch for John 12:34
				if (ari == Ari.encode(42, 12, 34)) {
					as.text = as.text.replace("bahwa@6Anak Manusia", "bahwa @6Anak Manusia");
				}
			}
		});
		
		teksDb.dump();
		
		List<Rec> xrec = teksDb.toRecList();
		
		dumpForYetTesting(InternalCommon.fileToBookNames(INPUT_KITAB), teksDb, pericopeData);
		
		System.out.println("Total rec: " + xrec.size());
		
		////////// CREATE REVERSE INDEX
		
		{
			File outDir = new File("./bahan/in-tb-usfm/raw");
			ReverseIndexer.createReverseIndex(outDir, "tb", teksDb);
		}
		
		////////// PROSES KE INTERNAL
		
		{
			File outDir = new File("./bahan/in-tb-usfm/raw");
			outDir.mkdir();
			InternalCommon.createInternalFiles(outDir, "tb", InternalCommon.fileToBookNames(INPUT_KITAB), teksDb, pericopeData);
		}
		
		////////// PROSES KE YES
		
		final InfoEdisi infoEdisi = Yes1Common.infoEdisi(INFO_NAMA, INFO_SHORT_NAME, INFO_LONG_NAME, RecUtil.hitungKitab(xrec), OUTPUT_ADA_PERIKOP, INFO_KETERANGAN, INPUT_TEKS_ENCODING_YES, null);
		final InfoKitab infoKitab = Yes1Common.infoKitab(xrec, INPUT_KITAB, INPUT_TEKS_ENCODING, INPUT_TEKS_ENCODING_YES);
		final Teks teks = Yes1Common.teks(xrec, INPUT_TEKS_ENCODING);
		
		Yes1File file = Yes1Common.bikinYesFile(infoEdisi, infoKitab, teks, new PerikopBlok(pericopeData), new PerikopIndex(pericopeData));
		
		file.output(new RandomAccessFile(OUTPUT_YES, "rw"));
	}

	private void dumpForYetTesting(List<String> bookNames, TextDb teksDb, PericopeData pericopeData) {
		class Row {
			int ari;
			int type;
			Rec rec;
			PericopeData.Entry entry;
		}
		
		List<Row> rows = new ArrayList<Row>();
		
		for (Rec rec: teksDb.toRecList()) {
			Row row = new Row();
			row.ari = Ari.encode(rec.book_1 - 1, rec.chapter_1, rec.verse_1);
			row.type = 2;
			row.rec = rec;
			rows.add(row);
		}
		
		for (PericopeData.Entry entry: pericopeData.entries) {
			Row row = new Row();
			row.ari = entry.ari;
			row.type = 1;
			row.entry = entry;
			rows.add(row);
		}
		
		Collections.sort(rows, new Comparator<Row>() {
			@Override public int compare(Row a, Row b) {
				if (a.ari != b.ari) return a.ari - b.ari;
				return a.type - b.type;
			}
		});
		
		for (int i = 0; i < bookNames.size(); i++) {
			System.out.printf("%s\t%d\t%s%n", "book_name", i + 1, bookNames.get(i));
		}
		
		for (Row row: rows) {
			if (row.type == 1) {
				System.out.printf("%s\t%d\t%d\t%d\t%s%n", "pericope", Ari.toBook(row.ari) + 1, Ari.toChapter(row.ari), Ari.toVerse(row.ari), row.entry.block.title);
				if (row.entry.block.parallels != null) {
					for (String parallel: row.entry.block.parallels) {
						System.out.printf("%s\t%s%n", "parallel", parallel);
					}
				}
			} else {
				System.out.printf("%s\t%d\t%d\t%d\t%s%n", "verse", Ari.toBook(row.ari) + 1, Ari.toChapter(row.ari), Ari.toVerse(row.ari), row.rec.text);
			}
		}
	}

	public class Handler extends DefaultHandler2 {
		private static final int LEVEL_p_r = -2;
		private static final int LEVEL_p_ms = -3;
		private static final int LEVEL_p_mr = -4;
		
		int kitab_0 = -1;
		int pasal_1 = 0;
		int ayat_1 = 0;
		
		String[] tree = new String[80];
		int depth = 0;
		
		Stack<Object> tujuanTulis = new Stack<Object>();
		Object tujuanTulis_misteri = new Object();
		Object tujuanTulis_teks = new Object();
		Object tujuanTulis_judulPerikop = new Object();
		Object tujuanTulis_xref = new Object();
		Object tujuanTulis_footnote = new Object();
		
		List<PericopeData.Entry> perikopBuffer = new ArrayList<PericopeData.Entry>();
		boolean afterThisMustStartNewPerikop = true; // if true, we have done with a pericope title, so the next text must become a new pericope title instead of appending to existing one
		
		// states
		int sLevel = 0;
		int menjorokTeks = -1; // -2 adalah para start; 0 1 2 3 4 adalah q level;
		int xref_state = -1; // 0 is initial (just encountered xref tag <x>), 1 is source, 2 is target
		
		public Handler(int kitab_0) {
			this.kitab_0 = kitab_0;
			tujuanTulis.push(tujuanTulis_misteri);
		}
		
		@Override public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
			tree[depth++] = localName;

			System.out.print("(start:) ");
			cetak();
			
			String alamat = alamat();
			if (alamat.endsWith("/c")) {
				String id = attributes.getValue("id");
				System.out.println("#c:" + id);
				pasal_1 = Integer.parseInt(id.trim());
				ayat_1 = 1; // reset ayat tiap ganti pasal
			} else if (alamat.endsWith("/v")) {
				String id = attributes.getValue("id");
				System.out.println("#v:" + id);
				try {
					ayat_1 = Integer.parseInt(id);
				} catch (NumberFormatException e) {
					System.out.println("// number format exception for: " + id);
					// get until first non number
					for (int pos = 0; pos < id.length(); pos++) {
						if (!Character.isDigit(id.charAt(pos))) {
							String s = id.substring(0, pos);
							ayat_1 = Integer.parseInt(s); 
							System.out.println("// number format exception simplified to: " + s);
							break;
						}
					}
				}
			} else if (alamat.endsWith("/f")) {
				tujuanTulis.push(tujuanTulis_footnote);
			} else if (alamat.endsWith("/p")) {
				String sfm = attributes.getValue("sfm");
				if (sfm != null) {
					if (sfm.equals("r")) {
						tujuanTulis.push(tujuanTulis_judulPerikop);
						sLevel = LEVEL_p_r;
					} else if (sfm.equals("mt")) {
						tujuanTulis.push(tujuanTulis_misteri);
					} else if (sfm.equals("ms")) {
						tujuanTulis.push(tujuanTulis_judulPerikop);
						sLevel = LEVEL_p_ms;
					} else if (sfm.equals("mr")) {
						tujuanTulis.push(tujuanTulis_judulPerikop);
						sLevel = LEVEL_p_mr;
					} else if (sfm.equals("mi")) {
						tujuanTulis.push(tujuanTulis_teks);
						menjorokTeks = 2;
					} else if (sfm.equals("pi")) { // Indented para
						tujuanTulis.push(tujuanTulis_teks);
						menjorokTeks = 1;
					} else if (sfm.equals("pc")) { // Centered para
						tujuanTulis.push(tujuanTulis_teks);
						menjorokTeks = 2;
					} else if (sfm.equals("m")) {
						/*
						 * Flush left (margin) paragraph.
						 * • No first line indent.
						 * • Followed immediately by a space and paragraph text, or by a new line and a verse marker.
						 * • Usually used to resume prose at the margin (without indent) after poetry or OT quotation (i.e. continuation of the previous paragraph).
						 */
						tujuanTulis.push(tujuanTulis_teks);
						menjorokTeks = 0; // inden 0
					} else {
						throw new RuntimeException("p@sfm ga dikenal: " + sfm);
					}
				} else {
					tujuanTulis.push(tujuanTulis_teks);
					menjorokTeks = -2;
				}
			} else if (alamat.endsWith("/q")) {
				tujuanTulis.push(tujuanTulis_teks);
				int level = Integer.parseInt(attributes.getValue("level"));
				if (level >= 1 && level <= 4) {
					menjorokTeks = level;
				} else {
					throw new RuntimeException("q level = " + level);
				}
			} else if (alamat.endsWith("/s")) {
				tujuanTulis.push(tujuanTulis_judulPerikop);
				sLevel = Integer.parseInt(attributes.getValue("level"));
			} else if (alamat.endsWith("/x")) {
				tujuanTulis.push(tujuanTulis_xref);
				xref_state = 0;
			} else if (alamat.endsWith("/x/milestone")) { // after milestone, we will have xref source 
				xref_state = 1;
			} else if (alamat.endsWith("/x/xt")) { // after xt, we will have xref target 
				xref_state = 2;
			} else if (alamat.endsWith("/wj")) {
				tujuanTulis.push(tujuanTulis_teks);
				tulis("@6");
			} else if (alamat.endsWith("/b")) { // line break
				tulis("@8");
			}
		}


		@Override public void endElement(String uri, String localName, String qName) throws SAXException {
			System.out.print("(end:) ");
			cetak();

			String alamat = alamat();
			if (alamat.endsWith("/p")) {
				tujuanTulis.pop();
			} else if (alamat.endsWith("/s")) {
				afterThisMustStartNewPerikop = true;
				tujuanTulis.pop();
			} else if (alamat.endsWith("/x")) {
				tujuanTulis.pop();
			} else if (alamat.endsWith("/wj")) {
				tulis("@5");
				tujuanTulis.pop();
			}

			tree[--depth] = null;
		}

		@Override public void characters(char[] ch, int start, int length) throws SAXException {
			String chars = new String(ch, start, length);
			if (chars.trim().length() > 0) {
				System.out.println("#text:" + chars);
				tulis(chars);
			}
		}

		private void tulis(String chars) {
			Object tujuan = tujuanTulis.peek();
			if (tujuan == tujuanTulis_misteri) {
				System.out.println("$tulis ke misteri " + kitab_0 + " " + pasal_1 + " " + ayat_1 + ":" + chars);
				misteri.append(chars).append('\n');
			} else if (tujuan == tujuanTulis_teks) {
				System.out.println("$tulis ke teks[jenis=" + menjorokTeks + "] " + kitab_0 + " " + pasal_1 + " " + ayat_1 + ":" + chars);
				teksDb.append(kitab_0, pasal_1, ayat_1, chars.replace("\n", " ").replaceAll("\\s+", " "), menjorokTeks);
				menjorokTeks = -1; // reset
				
				if (perikopBuffer.size() > 0) {
					for (PericopeData.Entry pe: perikopBuffer) {
						pe.block.title = pe.block.title.replace("\n", " ").replace("  ", " ").trim();
						System.out.println("(commit to perikopData " + kitab_0 + " " + pasal_1 + " " + ayat_1 + ":) " + pe.block.title);
						pe.ari = Ari.encode(kitab_0, pasal_1, ayat_1);
						pericopeData.entries.add(pe);
					}
					perikopBuffer.clear();
				}
			} else if (tujuan == tujuanTulis_judulPerikop) {
				// masukin ke data perikop
				String judul = chars;
				
				if (sLevel == 0 || sLevel == 1 || sLevel == LEVEL_p_mr || sLevel == LEVEL_p_ms) {
					if (afterThisMustStartNewPerikop || perikopBuffer.size() == 0) {
						PericopeData.Entry entry = new PericopeData.Entry();
						entry.ari = 0; // done later when writing teks so we know which verse this pericope starts from 
						entry.block = new PericopeData.Block();
						entry.block.version = 2;
						entry.block.title = judul;
						perikopBuffer.add(entry);
						afterThisMustStartNewPerikop = false;
						System.out.println("$tulis ke perikopBuffer (new entry) (size now: " + perikopBuffer.size() + "): " + judul);
					} else {
						perikopBuffer.get(perikopBuffer.size() - 1).block.title += judul;
						System.out.println("$tulis ke perikopBuffer (append to existing) (size now: " + perikopBuffer.size() + "): " + judul);
					}
				} else if (sLevel == LEVEL_p_r) { // paralel
					if (perikopBuffer.size() == 0) {
						throw new RuntimeException("paralel found but no perikop on buffer: " + judul);
					}
					
					PericopeData.Entry entry = perikopBuffer.get(perikopBuffer.size() - 1);
					entry.block.parallels = parseParalel(judul);
				} else if (sLevel == 2) {
					System.out.println("$tulis ke tempat sampah (perikop level 2): " + judul);
				} else {
					throw new RuntimeException("sLevel = " + sLevel + " not understood: " + judul);
				}
			} else if (tujuan == tujuanTulis_xref) {
				System.out.println("$tulis ke xref (state=" + xref_state + ") " + kitab_0 + " " + pasal_1 + " " + ayat_1 + ":" + chars);
				int ari = Ari.encode(kitab_0, pasal_1, ayat_1);
				if (xref_state == 0) {
					xrefDb.addBegin(ari);
				} else if (xref_state == 1) {
					xrefDb.addSource(ari, chars);
				} else if (xref_state == 2) {
					xrefDb.addTarget(ari, chars);
				} else {
					throw new RuntimeException("xref_state not supported");
				}
			} else if (tujuan == tujuanTulis_footnote) {
				System.out.println("$tulis ke footnote " + kitab_0 + " " + pasal_1 + " " + ayat_1 + ":" + chars);
			}
		}

		void cetak() {
			for (int i = 0; i < depth; i++) {
				System.out.print('/');
				System.out.print(tree[i]);
			}
			System.out.println();
		}

		private StringBuilder a = new StringBuilder();

		private String alamat() {
			a.setLength(0);
			for (int i = 0; i < depth; i++) {
				a.append('/').append(tree[i]);
			}
			return a.toString();
		}
	}

	/**
	 * (Mat. 23:1-36; Mrk. 12:38-40; Luk. 20:45-47) -> [Mat. 23:1-36, Mrk. 12:38-40, Luk. 20:45-47]
	 * (Mat. 10:26-33, 19-20) -> [Mat. 10:26-33, Mat. 10:19-20]
	 * (Mat. 6:25-34, 19-21) -> [Mat. 6:25-34, Mat. 6:19-21]
	 * (Mat. 10:34-36) -> [Mat. 10:34-36]
	 * (Mat. 26:57-58, 69-75; Mrk. 14:53-54, 66-72; Yoh. 18:12-18, 25-27) -> [Mat. 26:57-58, Mat. 26:69-75, Mrk. 14:53-54, Mrk. 14:66-72, Yoh. 18:12-18, Yoh. 18:25-27]
	 * (2Taw. 13:1--14:1) -> [2Taw. 13:1--14:1]
	 * (2Taw. 14:1-5, 15:16--16:13) -> [2Taw. 14:1-5, 2Taw. 15:16--16:13]
	 * (2Taw. 2:13-14, 3:15--5:1) -> [2Taw. 2:13-14, 2Taw. 3:15--5:1]
	 * (2Taw. 34:3-7, 35:1-27) -> [2Taw. 34:3-7, 2Taw. 35:1-27]
	 */
	static List<String> parseParalel(String judul) {
		List<String> res = new ArrayList<String>();

		judul = judul.trim();
		if (judul.startsWith("(")) judul = judul.substring(1);
		if (judul.endsWith(")")) judul = judul.substring(0, judul.length() - 1);

		String kitab = null;
		String pasal = null;
		String ayat = null;

		String[] alamats = judul.split("[;,]");
		for (String alamat : alamats) {
			alamat = alamat.trim();

			String[] bagians = alamat.split(" +", 2);
			String pa;
			if (bagians.length == 1) { // no kitab;
				if (kitab == null) throw new RuntimeException("no existing kitab");
				pa = bagians[0];
			} else {
				kitab = bagians[0];
				pa = bagians[1];
			}

			String[] parts = pa.split(":", 2);
			if (parts.length == 1) { // no pasal
				if (pasal == null) throw new RuntimeException("no existing pasal");
				ayat = parts[0];
			} else {
				pasal = parts[0];
				ayat = parts[1];
			}

			res.add(kitab + " " + pasal + ":" + ayat);
		}

		return res;
	}
}

class XrefDb {
	static class XrefEntry {
		String source;
		String target;
	}
	
	static interface XrefProcessor {
		void process(XrefEntry xe, int ari, int entryIndex);
	}
	
	Map<Integer, List<XrefEntry>> map = new TreeMap<>();
	
	void addBegin(int ari) {
		List<XrefEntry> list = map.get(ari);
		if (list == null) {
			list = new ArrayList<>();
			map.put(ari, list);
		}
		
		XrefEntry xe = new XrefEntry();
		list.add(xe);
	}
	
	/** must be after addBegin */
	void addSource(int ari, String source) {
		List<XrefEntry> list = map.get(ari);
		if (list == null) {
			throw new RuntimeException("Must be after addBegin (1)");
		}
		
		XrefEntry xe = list.get(list.size() - 1);
		if (xe.source != null || xe.target != null) {
			throw new RuntimeException("Must be directly after addBegin (2)");
		}
		
		xe.source = source.trim();
	}
	
	/* must be after addSource */
	void addTarget(int ari, String target) {
		List<XrefEntry> list = map.get(ari);
		if (list == null) {
			throw new RuntimeException("Must be after addSource (1)");
		}
		
		XrefEntry xe = list.get(list.size() - 1);
		if (xe.source == null || xe.target != null) {
			throw new RuntimeException("Must be directly after addSource (2)");
		}
		
		xe.target = target.trim();
	}
	
	void dump() {
		for (Map.Entry<Integer, List<XrefEntry>> e: map.entrySet()) {
			List<XrefEntry> xes = e.getValue();
			for (int i = 0; i < xes.size(); i++) {
				XrefEntry xe = xes.get(i);
				System.out.printf("xref 0x%06x(%d): [%s] [%s]%n", e.getKey(), i, xe.source, xe.target);
			}
		}
	}
	
	void processEach(XrefProcessor processor) {
		for (Map.Entry<Integer, List<XrefEntry>> e: map.entrySet()) {
			List<XrefEntry> xes = e.getValue();
			for (int i = 0; i < xes.size(); i++) {
				XrefEntry xe = xes.get(i);
				processor.process(xe, e.getKey(), i);
			}
		}
	}
}
