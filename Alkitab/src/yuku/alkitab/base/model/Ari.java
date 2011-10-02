package yuku.alkitab.base.model;


/**
 * Alkitab resource identifier
 * @author yuku
 *
 */
public class Ari {
	public static int encode(int kitab, int pasal, int ayat) {
		return (kitab & 0xff) << 16 | (pasal & 0xff) << 8 | (ayat & 0xff);
	}
	
	public static int encode(int kitab, int pasal_ayat) {
		return (kitab & 0xff) << 16 | (pasal_ayat & 0xffff);
	}
	
	public static int encodeWithKp(int ariKp, int ayat) {
		return (ariKp & 0x00ffff00) | (ayat & 0xff);
	}
	
	/** 0..255 
	 * kitab berbasis-0 (kejadian == 0)
	 * */
	public static int toKitab(int ari) {
		return (ari & 0x00ff0000) >> 16;
	}
	
	/** 1..255 
	 * pasal berbasis-1 yang dikembalikan
	 * */
	public static int toPasal(int ari) {
		return (ari & 0x0000ff00) >> 8;
	}
	
	/** 1..255 
	 * ayat berbasis-1 yang dikembalikan
	 * */
	public static int toAyat(int ari) {
		return (ari & 0x000000ff);
	}
	
	/**
	 * Gabungan kitab dan pasal. Ayat diset ke 0.
	 */
	public static int toKitabPasal(int ari) {
		return (ari & 0x00ffff00);
	}
}
