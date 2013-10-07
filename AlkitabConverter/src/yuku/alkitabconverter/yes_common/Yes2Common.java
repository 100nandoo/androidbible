package yuku.alkitabconverter.yes_common;

import yuku.alkitab.base.model.FootnoteEntry;
import yuku.alkitab.base.model.XrefEntry;
import yuku.alkitab.yes2.Yes2Writer;
import yuku.alkitab.yes2.compress.SnappyOutputStream;
import yuku.alkitab.yes2.io.MemoryRandomAccessFile;
import yuku.alkitab.yes2.io.RandomOutputStream;
import yuku.alkitab.yes2.model.PericopeData;
import yuku.alkitab.yes2.model.Yes2Book;
import yuku.alkitab.yes2.section.BooksInfoSection;
import yuku.alkitab.yes2.section.FootnotesSection;
import yuku.alkitab.yes2.section.PericopesSection;
import yuku.alkitab.yes2.section.VersionInfoSection;
import yuku.alkitab.yes2.section.XrefsSection;
import yuku.alkitab.yes2.section.base.SectionContent;
import yuku.alkitabconverter.util.TextDb;
import yuku.alkitabconverter.util.TextDb.VerseState;
import yuku.bintex.BintexWriter;
import yuku.bintex.ValueMap;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class Yes2Common {
	public static final String TAG = Yes2Common.class.getSimpleName();

	public static class VersionInfo {
		public String locale;
		public String shortName;
		public String longName;
		public String description;
		
		private List<String> bookNames;
		private List<String> bookAbbreviations;

		public String getBookShortName(int bookId) {
			return bookNames.get(bookId);
		}

		public String getBookAbbreviation(final int bookId) {
			if (bookAbbreviations == null) return null;
			return bookAbbreviations.get(bookId);
		}

		public void setBookNamesAndAbbreviations(List<String> bookNames, List<String> bookAbbreviations) {
			this.bookNames = bookNames;
			this.bookAbbreviations = bookAbbreviations;
		}
	}

	private static BooksInfoSection getBooksInfoSection(VersionInfo versionInfo, TextDb textDb) throws Exception {
		// no nulls allowed
		final List<Yes2Book> yes2books = new ArrayList<Yes2Book>();
		
		// for the text offset from the beginning of text section
		int offsetTotal = 0;
		// for the text offset from the beginning of book
		int offsetPassed = 0;
		
		for (int bookId: textDb.getBookIds()) {
			Yes2Book b = new Yes2Book();
			b.bookId = bookId;
			b.chapter_count = textDb.getChapterCountForBook(bookId);
			b.chapter_offsets = new int[b.chapter_count + 1];
			b.offset = offsetTotal;
			b.shortName = versionInfo.getBookShortName(bookId);
			b.abbreviation = versionInfo.getBookAbbreviation(bookId);
			b.verse_counts = new int[b.chapter_count];
			
			b.chapter_offsets[0] = 0;
			for (int chapter_0 = 0; chapter_0 < b.chapter_count; chapter_0++) {
				b.verse_counts[chapter_0] = textDb.getVerseCountForBookChapter(bookId, chapter_0 + 1);
				
				for (int verse_0 = 0; verse_0 < b.verse_counts[chapter_0]; verse_0++) {
					String verseText = textDb.getVerseText(bookId, chapter_0+1, verse_0+1);
					offsetPassed += VerseBytes.bytesForAVerse(verseText).length;
				}
				b.chapter_offsets[chapter_0 + 1] = offsetPassed;
			}

			yes2books.add(b);
			
			//# reset
			offsetTotal += offsetPassed;
			offsetPassed = 0;
		}
		
		BooksInfoSection res = new BooksInfoSection();
		res.yes2Books = yes2books;
		return res;
	}

	private static VersionInfoSection getVersionInfoSection(VersionInfo versionInfo, TextDb textDb, boolean hasPericopes) {
		VersionInfoSection res = new VersionInfoSection();
		res.book_count = textDb.getBookCount();
		res.description = versionInfo.description;
		res.hasPericopes = hasPericopes? 1: 0;
		res.locale = versionInfo.locale;
		res.longName = versionInfo.longName;
		res.shortName = versionInfo.shortName;
		res.buildTime = (int) (System.currentTimeMillis() / 1000);
		res.textEncoding = 2; // utf-8
		return res;
	}

	public static void createYesFile(final File outputFile, final VersionInfo versionInfo, final TextDb textDb, PericopeData pericopeData, boolean compressed) throws Exception {
		createYesFile(outputFile, versionInfo, textDb, pericopeData, compressed, null, null);
	}

	public static void createYesFile(final File outputFile, final VersionInfo versionInfo, final TextDb textDb, PericopeData pericopeData, boolean compressed, final LinkedHashMap<Integer, XrefEntry> xrefEntries, final LinkedHashMap<Integer, FootnoteEntry> footnoteEntries) throws Exception {
		VersionInfoSection versionInfoSection = getVersionInfoSection(versionInfo, textDb, pericopeData != null);
		BooksInfoSection booksInfoSection = getBooksInfoSection(versionInfo, textDb);
		
		Yes2Writer yesWriter = new Yes2Writer();
		yesWriter.sections.add(versionInfoSection);
		yesWriter.sections.add(booksInfoSection);
		if (pericopeData != null) {
			yesWriter.sections.add(new CompressiblePericopesSection(pericopeData, compressed));
		}
		yesWriter.sections.add(new CompressibleLazyText(textDb, compressed));

		if (xrefEntries != null) {
			yesWriter.sections.add(new CompressibleXrefsSection(xrefEntries, compressed));
		}

		if (footnoteEntries != null) {
			yesWriter.sections.add(new CompressibleFootnotesSection(footnoteEntries, compressed));
		}

		RandomAccessFile raf = new RandomAccessFile(outputFile, "rw"); //$NON-NLS-1$
		raf.setLength(0);
		RandomOutputStream output = new RandomOutputStream(raf);
		yesWriter.writeToFile(output);
		
		output.close();
	}

	/** Get the complete bytes (including information about length and/or separators for a verse */
	static class VerseBytes {
		static ThreadLocal<ByteArrayOutputStream> baos_ = new ThreadLocal<ByteArrayOutputStream>() {
			@Override protected ByteArrayOutputStream initialValue() {
				return new ByteArrayOutputStream(1000);
			}
		};
		
		static byte[] bytesForAVerse(String verse) {
			ByteArrayOutputStream baos = baos_.get();
			baos.reset();
			
			try {
				BintexWriter bw = new BintexWriter(baos);
				try {
					byte[] verse_bytes = verse.getBytes("utf-8");
					bw.writeVarUint(verse_bytes.length);
					bw.writeRaw(verse_bytes);
					return baos.toByteArray();
				} finally {
					bw.close();
				}
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}
	}

	static class CompressionInfo {
		final boolean compressed;
		final int COMPRESS_BLOCK_SIZE = 32768;
		ByteArrayOutputStream outputBuffer;
		SnappyOutputStream snappyOutputStream; // could be null. Used for flushing.
		private int[] compressed_block_sizes;

		CompressionInfo(final boolean compressed) {
			this.compressed = compressed;
		}

		OutputStream getOutputStream() {
			outputBuffer = new ByteArrayOutputStream();
			if (compressed) {
				snappyOutputStream = new SnappyOutputStream(outputBuffer, COMPRESS_BLOCK_SIZE);
				return snappyOutputStream;
			}
			return outputBuffer;
		}

		public ValueMap getSectionAttributes() {
			final ValueMap res = new ValueMap();
			if (compressed) {
				assert compressed_block_sizes != null;

				ValueMap compressionInfo = new ValueMap();
				compressionInfo.put("block_size", COMPRESS_BLOCK_SIZE);
				compressionInfo.put("compressed_block_sizes", compressed_block_sizes);
				res.put("compression.name", "snappy-blocks");
				res.put("compression.version", 1);
				res.put("compression.info", compressionInfo);
			}
			return res;
		}

		public void finalizeOutputStream() throws IOException {
			if (snappyOutputStream != null) {
				snappyOutputStream.flush();

				compressed_block_sizes = snappyOutputStream.getCompressedBlockSizes();
			}
		}

		public void writeOutputBufferTo(final RandomOutputStream output) throws IOException {
			outputBuffer.writeTo(output);
		}
	}

	/**
	 * Each verse is written as follows:
	 *
	 *	- varuint length_in_bytes
	 *  - byte[length_in_bytes] encoded_text
	 */
	static class CompressibleLazyText extends SectionContent implements SectionContent.Writer {
		final CompressionInfo compressionInfo;

		public CompressibleLazyText(TextDb textDb, boolean compressed) throws IOException {
			super("text");
			compressionInfo = new CompressionInfo(compressed);

			final BintexWriter bw = new BintexWriter(compressionInfo.getOutputStream());

			textDb.processEach(new TextDb.TextProcessor() {
				@Override public void process(int ari, VerseState verseState) {
					byte[] bytes = VerseBytes.bytesForAVerse(verseState.text);
					try {
						bw.writeRaw(bytes);
					} catch (IOException e) {
						throw new RuntimeException(e);
					}
				}
			});

			compressionInfo.finalizeOutputStream();
		}
		
		@Override public ValueMap getAttributes() {
			return compressionInfo.getSectionAttributes();
		}

		@Override public void write(RandomOutputStream output) throws Exception {
			compressionInfo.writeOutputBufferTo(output);
		}
	}

	static class CompressiblePericopesSection extends PericopesSection implements SectionContent.Writer {
		final CompressionInfo compressionInfo;

		public CompressiblePericopesSection(PericopeData pericopeData, boolean compressed) {
			super(pericopeData);
			compressionInfo = new CompressionInfo(compressed);

			try {
				MemoryRandomAccessFile mem = new MemoryRandomAccessFile();
				super.write(new RandomOutputStream(mem));

				final OutputStream os = compressionInfo.getOutputStream();
				os.write(mem.getBuffer(), mem.getBufferOffset(), mem.getBufferLength());

				compressionInfo.finalizeOutputStream();
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		}
		
		@Override public ValueMap getAttributes() {
			return compressionInfo.getSectionAttributes();
		}

		@Override public void write(RandomOutputStream output) throws Exception {
			// DO NOT CALL SUPER!
			compressionInfo.writeOutputBufferTo(output);
		}
	}

	static class CompressibleXrefsSection extends SectionContent implements SectionContent.Writer {
		final CompressionInfo compressionInfo;

		public CompressibleXrefsSection(final LinkedHashMap<Integer, XrefEntry> xrefEntries, boolean compressed) throws IOException {
			super(XrefsSection.SECTION_NAME);
			compressionInfo = new CompressionInfo(compressed);

			final BintexWriter bw = new BintexWriter(compressionInfo.getOutputStream());
			// version
			bw.writeUint8(1);

			// entry_count
			bw.writeInt(xrefEntries.size());

			// int arif[entry_count]
			for (final Map.Entry<Integer, XrefEntry> entry : xrefEntries.entrySet()) {
				bw.writeInt(entry.getKey());
			}

			// try to calculate offset for each content. So we do the following
			ByteArrayOutputStream contents = new ByteArrayOutputStream();
			BintexWriter contentsBw = new BintexWriter(contents);

			// int offsets[entry_count] 
			for (final Map.Entry<Integer, XrefEntry> entry : xrefEntries.entrySet()) {
				bw.writeInt(contentsBw.getPos());
				contentsBw.writeValueString(entry.getValue().content);
			}

			// value<string> xref_entry_contents[entry_count]
			bw.writeRaw(contents.toByteArray());

			compressionInfo.finalizeOutputStream();
		}

		@Override public ValueMap getAttributes() {
			return compressionInfo.getSectionAttributes();
		}

		@Override public void write(RandomOutputStream output) throws Exception {
			compressionInfo.writeOutputBufferTo(output);
		}
	}


	static class CompressibleFootnotesSection extends SectionContent implements SectionContent.Writer {
		final CompressionInfo compressionInfo;

		public CompressibleFootnotesSection(final LinkedHashMap<Integer, FootnoteEntry> footnoteEntries, boolean compressed) throws IOException {
			super(FootnotesSection.SECTION_NAME);
			compressionInfo = new CompressionInfo(compressed);

			final BintexWriter bw = new BintexWriter(compressionInfo.getOutputStream());
			// version
			bw.writeUint8(1);

			// entry_count
			bw.writeInt(footnoteEntries.size());

			// int arif[entry_count]
			for (final Map.Entry<Integer, FootnoteEntry> entry: footnoteEntries.entrySet()) {
				bw.writeInt(entry.getKey());
			}
			
			// try to calculate offset for each content. So we do the following
			ByteArrayOutputStream contents = new ByteArrayOutputStream();
			BintexWriter contentsBw = new BintexWriter(contents);

			// int offsets[entry_count] 
			for (final Map.Entry<Integer, FootnoteEntry> entry: footnoteEntries.entrySet()) {
				bw.writeInt(contentsBw.getPos());
				contentsBw.writeValueString(entry.getValue().content);
			}
			
			// value<string> footnote_entry_contents[entry_count]
			bw.writeRaw(contents.toByteArray());

			compressionInfo.finalizeOutputStream();
		}

		@Override public ValueMap getAttributes() {
			return compressionInfo.getSectionAttributes();
		}

		@Override public void write(RandomOutputStream output) throws Exception {
			compressionInfo.writeOutputBufferTo(output);
		}
	}


}


