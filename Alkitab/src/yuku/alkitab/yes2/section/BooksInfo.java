package yuku.alkitab.yes2.section;

import java.util.ArrayList;
import java.util.List;

import yuku.alkitab.yes2.io.RandomInputStream;
import yuku.alkitab.yes2.model.Yes2Book;
import yuku.alkitab.yes2.section.base.SectionContent;
import yuku.bintex.BintexReader;
import yuku.bintex.BintexWriter;

public class BooksInfo implements SectionContent, SectionContent.Writer {
	public List<Yes2Book> yes2Books;

	@Override public void toBytes(BintexWriter writer) throws Exception {
		int c = 0;
		
		for (Yes2Book yes2Book: yes2Books) {
			if (yes2Book != null) {
				c++;
			}
		}

		// int book_count
		writer.writeInt(c);
		
		// Book[book_count]
		for (Yes2Book yes2Book: yes2Books) {
			if (yes2Book != null) {
				yes2Book.toBytes(writer);
			}
		}
	}
	
	public static class Reader implements SectionContent.Reader<BooksInfo> {
		@Override public BooksInfo toSection(RandomInputStream input) throws Exception {
			BintexReader br = new BintexReader(input);
			
			BooksInfo res = new BooksInfo();
			int book_count = br.readInt();
			res.yes2Books = new ArrayList<Yes2Book>(book_count);
			for (int i = 0; i < book_count; i++) {
				res.yes2Books.add(Yes2Book.fromBytes(br));
			}
			return res;
		}
	}
}
