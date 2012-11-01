package yuku.alkitabconverter.yet;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Scanner;

import yuku.alkitab.yes.YesFile.PericopeData;
import yuku.alkitabconverter.util.Rec;

public class YetFileInput {
	public static class YetFileInputResult {
		List<Rec> recs;
		PericopeData pericopeData;
		Map<String, String> infos;
		int numberOfBooks;
		List<String> bookNames;
		
		void addInfo(String k, String v) {
			if (infos == null) infos = new LinkedHashMap<String, String>();
			infos.put(k, v);
		}
		
		void addPericopeEntry(PericopeData.Entry e) {
			if (pericopeData == null) pericopeData = new PericopeData();
			pericopeData.addEntry(e);
		}
		
		void addRec(Rec rec) {
			if (recs == null) recs = new ArrayList<Rec>();
			recs.add(rec);
		}
		
		void addBookName(int bookId, String bookName) {
			if (bookNames == null) {
				bookNames = new ArrayList<String>();
				for (int i = 0; i < 66; i++) {
					bookNames.add(null);
				}
			}
			
			bookNames.set(bookId - 1, bookName);
		}

		void setNumberOfBooks(int numberOfBooks) {
			this.numberOfBooks = numberOfBooks;
		}
	}
	
	public YetFileInputResult parse(String nf) throws Exception {
		LinkedHashMap<Integer, Integer> nversePerBook = new LinkedHashMap<Integer, Integer>();
		
		Scanner sc = new Scanner(new File(nf), "utf-8");
		
		int lastBook_1 = 1;
		int lastChapter_1 = 1;
		int lastVerse_1 = 0;
		PericopeData.Entry lastPericopeEntry = null;
		
		YetFileInputResult res = new YetFileInputResult();
		
		while (sc.hasNextLine()) {
			String baris = sc.nextLine();
			
			String[] splits = baris.split("\t");
			String command = splits[0];
			
			if ("info".equals(command)) {
				String k = splits[1];
				String v = splits[2];
				res.addInfo(k, v);
			} else if ("pericope".equals(command)) {
				int book_1 = Integer.parseInt(splits[1]);
				int chapter_1 = Integer.parseInt(splits[2]);
				int verse_1 = Integer.parseInt(splits[3]);
				String text = splits[4];
				
				lastPericopeEntry = new PericopeData.Entry();
				res.addPericopeEntry(lastPericopeEntry);
				
				lastPericopeEntry.ari = ((book_1 - 1) << 16) | (chapter_1 << 8) | verse_1;
				lastPericopeEntry.block = new PericopeData.Block();
				lastPericopeEntry.block.version = 3;
				lastPericopeEntry.block.title = text;
			} else if ("parallel".equals(command)) {
				String text = splits[1];
				lastPericopeEntry.block.addParallel(text);
			} else if ("book_name".equals(command)) {
				int bookId = Integer.parseInt(splits[1]);
				String bookName = splits[2];
				
				res.addBookName(bookId, bookName);
			} else if ("verse".equals(command)) {
				int book_1 = Integer.parseInt(splits[1]);
				int chapter_1 = Integer.parseInt(splits[2]);
				int verse_1 = Integer.parseInt(splits[3]);
				String text = splits[4];
			
				if (verse_1 != lastVerse_1 + 1) {
					if (chapter_1 != lastChapter_1 + 1) {
						if (book_1 != lastBook_1 + 1) {
							throw new RuntimeException("wrong verse ordering: " + baris);
						}
					}
				}
				
				Rec rec = new Rec();
				rec.book_1 = book_1;
				rec.chapter_1 = chapter_1;
				rec.verse_1 = verse_1;
				rec.text = text;
				
				res.addRec(rec);
				nversePerBook.put(book_1, (nversePerBook.get(book_1) == null? 0: nversePerBook.get(book_1)) + 1);
				
				lastBook_1 = book_1;
				lastChapter_1 = chapter_1;
				lastVerse_1 = verse_1;
			}
		}
		
		for (Entry<Integer, Integer> e: nversePerBook.entrySet()) {
			System.out.println("book_1 " + e.getKey() + ": " + e.getValue() + " verses");
		}
		res.setNumberOfBooks(nversePerBook.size());
		
		System.out.println("DONE");
		return res;
	}
}
