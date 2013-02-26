package yuku.alkitabconverter.unboundbatch;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import yuku.alkitab.base.model.Ari;
import yuku.alkitabconverter.util.TextDb;
import yuku.alkitabconverter.util.TextDb.VerseState;
import yuku.alkitabconverter.yes_common.Yes2Common;

public class UnboundBatchConverter {
	static String DATA_DIR = "/Users/yuku/j/operasi/unbound";
	
	public static void main(String[] args) throws Exception {
		new UnboundBatchConverter().convertAll();
	}

	void convertAll() throws Exception {
		// look for directories starting with "ready-" 
		File[] superdirs = new File(DATA_DIR).listFiles(new FileFilter() {
			@Override public boolean accept(File f) {
				return f.isDirectory() && f.getName().startsWith("ready-");
			}
		});
	
		// look for all directories under them but only those with corresponding ".properties" file 
		for (final File superdir: superdirs) {
			File[] dirs = superdir.listFiles(new FileFilter() {
				@Override public boolean accept(File f) {
					return f.isDirectory() && new File(superdir, f.getName() + ".properties").exists();
				}
			});
			
			for (File dir: dirs) {
				processVersion(superdir, dir);
			}
		}
	}

	void processVersion(File superdir, File dir) throws Exception {
		// look for text: ending in _utf8_mapped_to_NRSVA.txt or _utf8.txt
		File textFile = null;
		boolean mapped = false;
		
		for (File f: dir.listFiles()) {
			if (f.getName().endsWith("_utf8_mapped_to_NRSVA.txt")) {
				textFile = f;
				mapped = true;
				break;
			}
		}
		
		if (textFile == null) {
			for (File f: dir.listFiles()) {
				if (f.getName().endsWith("_utf8.txt")) {
					textFile = f;
					mapped = false;
					break;
				}
			}
		}
		
		if (textFile == null) {
			throw new RuntimeException("text file not found in dir " + dir);
		}
		
		// read properties file
		Yes2Common.VersionInfo versionInfo = new Yes2Common.VersionInfo();
		String outputName;
		
		try (FileInputStream propInput = new FileInputStream(new File(superdir, dir.getName() + ".properties"))) {
			Properties prop = new Properties();
			prop.load(propInput);
			versionInfo.locale = prop.getProperty("versionInfo.locale");
			versionInfo.shortName = prop.getProperty("versionInfo.shortName");
			versionInfo.longName = prop.getProperty("versionInfo.longName");
			versionInfo.description = prop.getProperty("versionInfo.description");
			outputName = prop.getProperty("output.name");
		}
		
		TextDb textDb = processTextFile(superdir.getName(), dir.getName(), textFile, mapped);
		
		try (PrintStream ps = new PrintStream(new File("/tmp/" + outputName + ".txt"))) {
			textDb.dump(ps);
		}
		
		Yes2Common.createYesFile(new File("/tmp", outputName + ".yes"), versionInfo, textDb);
		
		System.out.println("Processing finished, total verses: " + textDb.size());
	}

	TextDb processTextFile(String categoryName, String versionName, File textFile, boolean mapped) throws Exception {
		List<String> rawLines = new ArrayList<>();
		List<String[]> brokenLines = new ArrayList<>();
		Map<String, Integer> columns = new LinkedHashMap<>();
		
		try (BufferedReader sc = new BufferedReader(new InputStreamReader(new FileInputStream(textFile), "utf-8"))) {
			
			while (true) {
				String rawLine = sc.readLine();
				if (rawLine == null) break;
				
				if (rawLine.startsWith("#columns")) {
					String[] splits = rawLine.split("\t");
					for (int i = 1; i < splits.length; i++) {
						columns.put(splits[i], i-1);
					}
				} else if (rawLine.startsWith("#") || rawLine.length() == 0) {
					// nop
				} else {
					rawLines.add(rawLine);
					brokenLines.add(rawLine.split("\t", -1));
				}
			}
			
			System.out.println(versionName + " mapped=" + mapped + " has rawLines " + rawLines.size());
			
			// check required columns
			if (!columns.containsKey("orig_book_index")) throw new RuntimeException("column orig_book_index not found");
			if (!columns.containsKey("orig_chapter")) throw new RuntimeException("column orig_chapter not found");
			if (!columns.containsKey("orig_verse")) throw new RuntimeException("column orig_verse not found");
			if (!columns.containsKey("text")) throw new RuntimeException("column text not found");
			
			// check if all verses belong to kjv
			return checkVersesAllKjv(categoryName, versionName, rawLines, brokenLines, columns, mapped);
		}
	}

	TextDb checkVersesAllKjv(String categoryName, String versionName, List<String> rawLines, List<String[]> brokenLines, Map<String, Integer> columns, boolean mapped) throws Exception {
		TextDb textDb = new TextDb();
		
		int col_text = columns.get("text");
		
		// These versions has a bug (?) that Ps 66 has only 19 verses according to NRSVA
		// but their orig_verses has 1-20 verses which is correct. The NRSVA itself contains 20 verses. 
		// So it seems like unnecessary verse shift.
		boolean versionWithPsalm66bug = Arrays.asList("afrikaans_1953_ucs2", "french_ostervald_1996_ucs2", "norwegian_ucs2", "wlc_consonants_ucs2", "wlc_ucs2", "wlc_vowels_ucs2").contains(versionName);
		
		// PATCH for missing data:
		// afrikaans_1953_ucs2
		
		if (!mapped) {
			int col_orig_book_index = columns.get("orig_book_index");
			int col_orig_chapter = columns.get("orig_chapter");
			int col_orig_verse = columns.get("orig_verse");
			
			for (String[] brokenLine : brokenLines) {
				try {
					int bookId = Integer.parseInt(brokenLine[col_orig_book_index].substring(0, 2)) - 1;
					int chapter_1 = Integer.parseInt(brokenLine[col_orig_chapter]);
					int verse_1 = Integer.parseInt(brokenLine[col_orig_verse]);
					
					if (bookId > 65) { // Apocrypha
						continue;
					}
					
					if (!KjvUtils.isValidKjv(bookId, chapter_1, verse_1)) {
						System.out.printf("NOT VALID KJV: %s %s %s\n", bookId, chapter_1, verse_1);
					} else {
						textDb.append(bookId, chapter_1, verse_1, brokenLine[col_text], -1);
					}
				} catch (Exception e) {
					System.out.println("error when processing: " + Arrays.toString(brokenLine));
					e.printStackTrace();
				}
			}
		} else {
			int col_orig_chapter = columns.get("orig_chapter");
			int col_orig_verse = columns.get("orig_verse");
			int col_nrsva_book_index = columns.get("nrsva_book_index");
			int col_nrsva_chapter = columns.get("nrsva_chapter");
			int col_nrsva_verse = columns.get("nrsva_verse");
			
			int bookId = -1;
			int chapter_1 = 0;
			int verse_1 = 0;
			int orig_chapter_1 = 0;
			int orig_verse_1 = 0;
			
			// Some of the files contain invalid NRSVA bcvs on the book of psalms,
			// we need to register that and make the NRSVA verses 1+2 -> KJV verse 1 
			// and subsequent NRSVA verses n -> KJV verse (n-1) 
			Set<Integer> psalmChaptersWithInvalidNrsvaBcv = new LinkedHashSet<>();
			int[] psalmChaptersCanHaveInvalidNrsvaBcv = {30,51,52,54,60,84,85};
			
			for (String[] brokenLine : brokenLines) {
				if (brokenLine[col_nrsva_book_index].length() == 0) {
					// use previous line's info
				} else {
					bookId = Integer.parseInt(brokenLine[col_nrsva_book_index].substring(0, 2)) - 1;
					chapter_1 = Integer.parseInt(brokenLine[col_nrsva_chapter]);
					verse_1 = Integer.parseInt(brokenLine[col_nrsva_verse]);
				}
				if (!KjvUtils.isValidKjv(bookId, chapter_1, verse_1)) {
					if (bookId == 18 /* psalms */ && Arrays.binarySearch(psalmChaptersCanHaveInvalidNrsvaBcv, chapter_1) >= 0) {
						psalmChaptersWithInvalidNrsvaBcv.add(chapter_1);
					}
				}
			}
			
			if (psalmChaptersWithInvalidNrsvaBcv.size() > 0) {
				System.out.println("warning: psalmChaptersWithInvalidNrsvaBcv = " + psalmChaptersWithInvalidNrsvaBcv);
			}
			
			for (String[] brokenLine : brokenLines) {
				try {
					if (brokenLine[col_nrsva_book_index].length() == 0) {
						// use previous line's info
					} else {
						bookId = Integer.parseInt(brokenLine[col_nrsva_book_index].substring(0, 2)) - 1;
						chapter_1 = Integer.parseInt(brokenLine[col_nrsva_chapter]);
						verse_1 = Integer.parseInt(brokenLine[col_nrsva_verse]);
					}
					
					if (brokenLine[col_orig_verse].length() == 0) {
						// use previous line's info
					} else {
						orig_chapter_1 = Integer.parseInt(brokenLine[col_orig_chapter]);
						orig_verse_1 = Integer.parseInt(brokenLine[col_orig_verse]);
					}
					
					
					if (bookId > 65) { // Apocrypha
						continue;
					}
					
					// convert NRSVA to KJV:
					// 3John 1	64N 1 NRSVA ayat 14+15 -> KJV ayat 14
					if (bookId == 63 && chapter_1 == 1 && verse_1 == 15) {
						// should have verse_1 = 14;
						verse_1 = 14;
					}
					// Rev 12	66N 12 NRSVA ayat 17+18 -> KJV ayat 17
					else if (bookId == 65 && chapter_1 == 12 && verse_1 == 18) {
						// should have verse_1 = 17;
						verse_1 = 17;
					}
					
					else if (bookId == 18 /* psalms */ && psalmChaptersWithInvalidNrsvaBcv.contains(chapter_1) && verse_1 >= 2) {
						verse_1 -= 1;
					}
					else if (bookId == 46 /* 2Cor */ && chapter_1 == 13 && verse_1 == 12) {
						if (orig_verse_1 == 12) {
							// first part of NRSVA verse 12, which is KJV verse 12
						} else if (orig_verse_1 == 13) {
							// second part of NRSVA verse 12, which is KJV verse 13
							verse_1 = 13;
						} else {
							throw new RuntimeException("This should not happen 390");
						}
					}
					else if (bookId == 46 /* 2Cor */ && chapter_1 == 13 && verse_1 == 13) {
						if (orig_verse_1 == 13) {
							// this version follows NRSVA, no need to adjust
						} else if (orig_verse_1 == 14) {
							verse_1 = 14;
						} else {
							throw new RuntimeException("This should not happen 125");
						}
					}
					
					else if (versionWithPsalm66bug && bookId == 18 /* Ps */ && chapter_1 == 66) {
						verse_1 = orig_verse_1; // just follow the original
					}
					
					if (!KjvUtils.isValidKjv(bookId, chapter_1, verse_1)) {
						throw new RuntimeException("NOT VALID KJV: " + bookId + " " + chapter_1 + " " + verse_1);
					}
					
					String prefix;
					if (chapter_1 != orig_chapter_1 || verse_1 != orig_verse_1) {
						prefix = "(" + orig_chapter_1 + "-" + orig_verse_1 + ") ";
					} else {
						prefix = "";
					}
					textDb.append(bookId, chapter_1, verse_1, prefix + brokenLine[col_text], -1, " ");
				} catch (Exception e) {
					System.out.println("error when processing: " + Arrays.toString(brokenLine));
					e.printStackTrace();
				}
			}
		}
		
		{ // check whether ALL KJV verses are stored
			final boolean[] lids = new boolean[31102];
			textDb.processEach(new TextDb.TextProcessor() {
				@Override public void process(int ari, VerseState ayatState) {
					lids[KjvUtils.ariToLid(ari) - 1] = true;
				}
			});
			List<Integer> notexists = new ArrayList<>();
			for (int i = 0; i < lids.length; i++) {
				boolean exist = lids[i];
				if (!exist) {
					notexists.add(KjvUtils.lidToAri(i + 1));
				}
			}
			
			// SPECIAL CASE FOR 2Cor 13
			// In NRSVA, 2Cor 13 has 13 verses, in KJV has 14 verses
			// NRSVA verse 12 -> KJV verse 12+13
			// NRSVA verse 13 -> KJV verse 14
			// This is for those versions originally following NRSVA (not KJV) so we need to append empty verse 14. 
			if (notexists.contains(0x2e0d0e) && !notexists.contains(0x2e0d0d)) {
				System.out.println("info: adding 2cor 13:14");
				textDb.append(0x2e0d0e, "", -1);
				notexists.remove((Integer)0x2e0d0e);
			}
			
			// SPECIAL CASE FOR Rom 16
			// In NRSVA and KJV, Rom 16 has 27 verses, but on WEB (or maybe others) it has only 24 or 25 verses. Verse 25 onwards are empty.
			if (!notexists.contains(0x2c1018)) {
				if (notexists.contains(0x2c1019)) {
					System.out.println("info: adding Rom 16:25");
					textDb.append(0x2c1019, "", -1);
					notexists.remove((Integer)0x2c1019);
				}
				if (notexists.contains(0x2c101a)) {
					System.out.println("info: adding Rom 16:26");
					textDb.append(0x2c101a, "", -1);
					notexists.remove((Integer)0x2c101a);
				}
				if (notexists.contains(0x2c101b)) {
					System.out.println("info: adding Rom 16:27");
					textDb.append(0x2c101b, "", -1);
					notexists.remove((Integer)0x2c101b);
				}
			}
			
			if (notexists.size() > 0) {
				System.out.println("warning: kjv verses not found: " + notexists.size());
				System.out.println("warning: such as: ");
				for (int i = 0; i < notexists.size() && i < 5; i++) {
					int ari = notexists.get(i);
					System.out.println("warning:  - " + Ari.toBook(ari) + " " + Ari.toChapter(ari) + " " + Ari.toVerse(ari));
				}
			}
		}
		
		return textDb;
	}
}
