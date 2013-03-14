package yuku.alkitab.yes2;

import android.util.Log;

import java.util.Arrays;
import java.util.List;

import yuku.alkitab.base.model.Ari;
import yuku.alkitab.base.model.Book;
import yuku.alkitab.base.model.PericopeBlock;
import yuku.alkitab.base.model.SingleChapterVerses;
import yuku.alkitab.base.model.Version;
import yuku.alkitab.base.storage.BibleReader;
import yuku.alkitab.yes2.compress.SnappyInputStream;
import yuku.alkitab.yes2.io.RandomInputStream;
import yuku.alkitab.yes2.io.Yes2VerseTextDecoder;
import yuku.alkitab.yes2.model.SectionIndex;
import yuku.alkitab.yes2.model.Yes2Book;
import yuku.alkitab.yes2.section.BooksInfoSection;
import yuku.alkitab.yes2.section.PericopesSection;
import yuku.alkitab.yes2.section.TextSection;
import yuku.alkitab.yes2.section.VersionInfoSection;
import yuku.bintex.BintexReader;
import yuku.bintex.ValueMap;

public class Yes2Reader implements BibleReader {
	private static final String TAG = Yes2Reader.class.getSimpleName();

	private RandomInputStream file_;
	private SectionIndex sectionIndex_;

	// cached in memory
	private VersionInfoSection versionInfo_;
	private PericopesSection pericopesSection_;
	private TextSectionReader textSectionReader_;
	
	static class Yes2SingleChapterVerses extends SingleChapterVerses {
		private final String[] verses;

		public Yes2SingleChapterVerses(String[] verses) {
			this.verses = verses;
		}
		
		@Override public String getVerse(int verse_0) {
			return verses[verse_0];
		}

		@Override public int getVerseCount() {
			return verses.length;
		}
	}
	
	/** 
	 * This class simplify many operations regarding reading the verse texts from the yes file.
	 * This stores the offset to the beginning of text section content 
	 * and also understands the text section attributes (compression, encryption etc.)
	 */
	static class TextSectionReader {
		private final RandomInputStream file_;
		private final Yes2VerseTextDecoder decoder_;
		private final long sectionContentOffset_;
		private BintexReader br_;
		
		private int block_size = 0; // 0 means no compression
		private SnappyInputStream snappyInputStream;
		private int[] compressed_block_sizes;
		private int[] compressed_block_offsets;
		
		public TextSectionReader(RandomInputStream file, Yes2VerseTextDecoder decoder, ValueMap sectionAttributes, long sectionContentOffset) throws Exception {
			file_ = file;
			decoder_ = decoder;
			sectionContentOffset_ = sectionContentOffset;
			br_ = new BintexReader(null);
			
			if (sectionAttributes != null) {
				String compressionName = sectionAttributes.getString("compression.name");
				if (compressionName != null) {
					if ("snappy-blocks".equals(compressionName)) {
						int compressionVersion = sectionAttributes.getInt("compression.version", 0);
						if (compressionVersion > 1) {
							throw new Exception("Compression " + compressionName + " version " + compressionVersion + " is not supported");
						}
						ValueMap compressionInfo = sectionAttributes.getSimpleMap("compression.info");
						block_size = compressionInfo.getInt("block_size");
						compressed_block_sizes = compressionInfo.getIntArray("compressed_block_sizes");
						{ // convert compressed_block_sizes into offsets
							compressed_block_offsets = new int[compressed_block_sizes.length + 1];
							int c = 0;
							for (int i = 0, len = compressed_block_sizes.length; i < len; i++) {
								compressed_block_offsets[i] = c;
								c += compressed_block_sizes[i];
							}
							compressed_block_offsets[compressed_block_sizes.length] = c;
						}
						snappyInputStream = new SnappyInputStream(file_, sectionContentOffset, block_size, compressed_block_sizes, compressed_block_offsets);
					} else {
						throw new Exception("Compression " + compressionName + " is not supported");
					}
				}
			}
		}

		public Yes2SingleChapterVerses loadVerseText(Yes2Book yes2Book, int chapter_1, boolean dontSeparateVerses, boolean lowercase) throws Exception {
			int contentOffset = yes2Book.offset; 
			contentOffset += yes2Book.chapter_offsets[chapter_1 - 1];
			
			BintexReader br;
			if (block_size != 0) { // compressed!
				snappyInputStream.seek(contentOffset);
				br = br_.reuse(snappyInputStream);
			} else {
				file_.seek(sectionContentOffset_ + contentOffset);
				br = br_.reuse(file_);
			}

			int verse_count = yes2Book.verse_counts[chapter_1 - 1];
			if (dontSeparateVerses) {
				return new Yes2SingleChapterVerses(new String[] { 
					decoder_.makeIntoSingleString(br, verse_count, lowercase),
				});
			} else {
				return new Yes2SingleChapterVerses(decoder_.separateIntoVerses(br, verse_count, lowercase));
			}
		}
	}
	
	public Yes2Reader(RandomInputStream input) {
		this.file_ = input;
	}

	/** Read section index */
	private synchronized void loadSectionIndex() throws Exception {
		if (sectionIndex_ != null) { // we have read it previously.
			return;
		}
			
		file_.seek(0);

		{ // check header
			byte[] buf = new byte[8];
			file_.read(buf);
			if (!Arrays.equals(buf, new byte[] { (byte) 0x98, 0x58, 0x0d, 0x0a, 0x00, 0x5d, (byte) 0xe0, 0x02 /* yes version 2 */})) {
				throw new RuntimeException("YES2: Header is incorrect. Found: " + Arrays.toString(buf)); //$NON-NLS-1$
			}
		}
		
		file_.seek(12); // start of sectionIndex
		sectionIndex_ = SectionIndex.read(file_);
	}
	
	private synchronized void loadVersionInfo() throws Exception {
		if (seekToSection("versionInfo")) {
			versionInfo_ = new VersionInfoSection.Reader().read(file_);
		}
	}
	
	private synchronized boolean seekToSection(String sectionName) throws Exception {
		loadSectionIndex();
		
		if (sectionIndex_ == null) {
			Log.e(TAG, "@@seekToSection Could not load section index");
			return false;
		}
		
		if (sectionIndex_.seekToSectionContent(sectionName, file_)) {
			return true;
		} else {
			Log.e(TAG, "@@seekToSection Could not seek to section: " + sectionName);
			return false;
		}
	}

	@Override public String getShortName() {
		try {
			loadVersionInfo();
			return versionInfo_.shortName;
		} catch (Exception e) {
			Log.e(TAG, "yes load version info error", e); //$NON-NLS-1$
			return ""; //$NON-NLS-1$
		}
	}

	@Override public String getLongName() {
		try {
			loadVersionInfo();
			return versionInfo_.longName;
		} catch (Exception e) {
			Log.e(TAG, "yes load version info error", e); //$NON-NLS-1$
			return ""; //$NON-NLS-1$
		}
	}

	public String getDescription() {
		try {
			loadVersionInfo();
			return versionInfo_.description;
		} catch (Exception e) {
			Log.e(TAG, "yes load version info error", e); //$NON-NLS-1$
			return ""; //$NON-NLS-1$
		}
	}

	@Override public Book[] loadBooks() {
		try {
			loadVersionInfo();

			if (seekToSection(BooksInfoSection.SECTION_NAME)) {
				BooksInfoSection section = new BooksInfoSection.Reader().read(file_);
				List<Yes2Book> books = section.yes2Books;
				return books.toArray(new Yes2Book[books.size()]);
			}
			
			Log.e(TAG, "no section named " + BooksInfoSection.SECTION_NAME); //$NON-NLS-1$
			return null;
		} catch (Exception e) {
			Log.e(TAG, "loadBooks error", e); //$NON-NLS-1$
			return null;
		}
	}

	@Override public Yes2SingleChapterVerses loadVerseText(Book book, int chapter_1, boolean dontSeparateVerses, boolean lowercase) {
		Yes2Book yes2Book = (Yes2Book) book;
		
		try {
			if (chapter_1 <= 0 || chapter_1 > yes2Book.chapter_count) {
				return null;
			}

			if (textSectionReader_ == null) {
				ValueMap sectionAttributes = sectionIndex_.getSectionAttributes(TextSection.SECTION_NAME, file_);
				long sectionContentOffset = sectionIndex_.getAbsoluteOffsetForSectionContent(TextSection.SECTION_NAME);
				
				// init text decoder 
				Yes2VerseTextDecoder decoder;
				int textEncoding = versionInfo_.textEncoding;
				if (textEncoding == 1) {
					decoder = new Yes2VerseTextDecoder.Ascii();
				} else if (textEncoding == 2) {
					decoder = new Yes2VerseTextDecoder.Utf8();
				} else {
					Log.e(TAG, "Text encoding " + textEncoding + " not supported! Fallback to ascii."); //$NON-NLS-1$ //$NON-NLS-2$
					decoder = new Yes2VerseTextDecoder.Ascii();
				}
				
				textSectionReader_ = new TextSectionReader(file_, decoder, sectionAttributes, sectionContentOffset);
			}
			
			return textSectionReader_.loadVerseText(yes2Book, chapter_1, dontSeparateVerses, lowercase);
		} catch (Exception e) {
			Log.e(TAG, "loadVerseText error", e); //$NON-NLS-1$
			return null;
		}
	}

	@Override public int loadPericope(Version version, int bookId, int chapter_1, int[] aris, PericopeBlock[] blocks, int max) {
		try {
			loadVersionInfo();
			
			if (versionInfo_.hasPericopes == 0) {
				return 0;
			}
			
			if (pericopesSection_ == null) { // not yet loaded!
				if (seekToSection(PericopesSection.SECTION_NAME)) {
					pericopesSection_ = new PericopesSection.Reader().read(file_);
				} else {
					return 0;
				}
			}
			
			if (pericopesSection_ == null) { 
				Log.e(TAG, "Didn't succeed in loading pericopes section");
				return 0;
			}
		
			int ariMin = Ari.encode(bookId, chapter_1, 0);
			int ariMax = Ari.encode(bookId, chapter_1 + 1, 0);

			return pericopesSection_.getPericopesForAris(ariMin, ariMax, aris, blocks, max);
		} catch (Exception e) {
			Log.e(TAG, "General exception in loading pericope block", e); //$NON-NLS-1$
			return 0;
		}
	}
}
