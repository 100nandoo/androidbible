package yuku.alkitab.base.model;

import java.io.IOException;
import java.util.Date;

import org.xml.sax.Attributes;
import org.xmlpull.v1.XmlSerializer;

import yuku.andoutil.Sqlitil;
import android.content.ContentValues;
import android.database.Cursor;

public class Bukmak2 {
	public int _id;
	public int ari;
	public int jenis;
	public String tulisan;
	public Date waktuTambah;
	public Date waktuUbah;
	
	public Bukmak2(int ari, int jenis, String tulisan, Date waktuTambah, Date waktuUbah) {
		this.ari = ari;
		this.jenis = jenis;
		this.tulisan = tulisan;
		this.waktuTambah = waktuTambah;
		this.waktuUbah = waktuUbah;
	}
	
	/**
	 * _id ga termasuk
	 */
	public ContentValues toContentValues() {
		ContentValues res = new ContentValues();
		
		res.put(AlkitabDb.KOLOM_Bukmak2_ari, ari);
		res.put(AlkitabDb.KOLOM_Bukmak2_jenis, jenis);
		res.put(AlkitabDb.KOLOM_Bukmak2_tulisan, tulisan);
		res.put(AlkitabDb.KOLOM_Bukmak2_waktuTambah, Sqlitil.toInt(waktuTambah));
		res.put(AlkitabDb.KOLOM_Bukmak2_waktuUbah, Sqlitil.toInt(waktuUbah));
		
		return res;
	}
	
	
	public static final String XMLTAG_Bukmak2 = "Bukmak2";
	private static final String XMLATTR_ari = "ari";
	private static final String XMLATTR_jenis = "jenis";
	private static final String XMLATTR_tulisan = "tulisan";
	private static final String XMLATTR_waktuTambah = "waktuTambah";
	private static final String XMLATTR_waktuUbah = "waktuUbah";
	private static final String XMLVAL_bukmak = "bukmak";
	private static final String XMLVAL_catatan = "catatan";
	
	public void writeXml(XmlSerializer xml) throws IOException {
		xml.startTag(null, XMLTAG_Bukmak2);
		xml.attribute(null, XMLATTR_ari, String.valueOf(ari));
		xml.attribute(null, XMLATTR_jenis, jenis == AlkitabDb.ENUM_Bukmak2_jenis_bukmak? XMLVAL_bukmak: jenis == AlkitabDb.ENUM_Bukmak2_jenis_catatan? XMLVAL_catatan: String.valueOf(jenis));
		if (tulisan != null) {
			xml.attribute(null, XMLATTR_tulisan, tulisan);
		}
		if (waktuTambah != null) {
			xml.attribute(null, XMLATTR_waktuTambah, String.valueOf(Sqlitil.toInt(waktuTambah)));
		}
		if (waktuUbah != null) {
			xml.attribute(null, XMLATTR_waktuUbah, String.valueOf(Sqlitil.toInt(waktuUbah)));
		}
		xml.endTag(null, XMLTAG_Bukmak2);
	}
	
	public static Bukmak2 dariCursor(Cursor cursor) {
		int ari = cursor.getInt(cursor.getColumnIndexOrThrow(AlkitabDb.KOLOM_Bukmak2_ari));
		int jenis = cursor.getInt(cursor.getColumnIndexOrThrow(AlkitabDb.KOLOM_Bukmak2_jenis));
		
		return dariCursor(cursor, ari, jenis);
	}

	public static Bukmak2 dariCursor(Cursor cursor, int ari, int jenis) {
		String tulisan = cursor.getString(cursor.getColumnIndexOrThrow(AlkitabDb.KOLOM_Bukmak2_tulisan));
		Date waktuTambah = Sqlitil.toDate(cursor.getInt(cursor.getColumnIndexOrThrow(AlkitabDb.KOLOM_Bukmak2_waktuTambah)));
		Date waktuUbah = Sqlitil.toDate(cursor.getInt(cursor.getColumnIndexOrThrow(AlkitabDb.KOLOM_Bukmak2_waktuUbah)));
		
		return new Bukmak2(ari, jenis, tulisan, waktuTambah, waktuUbah);
	}

	public static Bukmak2 dariAttributes(Attributes attributes) {
		int ari = Integer.parseInt(attributes.getValue("", XMLATTR_ari));
		String jenis_s = attributes.getValue("", XMLATTR_jenis);
		int jenis = jenis_s.equals(XMLVAL_bukmak)? AlkitabDb.ENUM_Bukmak2_jenis_bukmak: jenis_s.equals(XMLVAL_catatan)? AlkitabDb.ENUM_Bukmak2_jenis_catatan: Integer.parseInt(jenis_s);
		String tulisan = attributes.getValue("", XMLATTR_tulisan);
		Date waktuTambah = Sqlitil.toDate(Integer.parseInt(attributes.getValue("", XMLATTR_waktuTambah)));
		Date waktuUbah = Sqlitil.toDate(Integer.parseInt(attributes.getValue("", XMLATTR_waktuUbah)));
		
		return new Bukmak2(ari, jenis, tulisan, waktuTambah, waktuUbah);
	}
}
