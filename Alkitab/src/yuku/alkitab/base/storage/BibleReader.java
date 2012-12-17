package yuku.alkitab.base.storage;

import yuku.alkitab.base.model.Book;
import yuku.alkitab.base.model.PericopeBlock;
import yuku.alkitab.base.model.PericopeIndex;
import yuku.alkitab.base.model.Version;

public interface BibleReader {
	String getShortName();
	String getLongName();
	Book[] loadBooks();
	/**
	 * @return may return null if the requested is not available 
	 */
	String[] loadVerseText(Book book, int chapter_1, boolean dontSplitVerses, boolean lowercased);
	PericopeIndex loadPericopeIndex();
	int loadPericope(Version version, int bookId, int chapter_1, int[] aris, PericopeBlock[] pericopeBlocks, int max);
}
