package yuku.alkitab.base.storage;

import android.content.Context;
import android.util.Log;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Arrays;

import yuku.alkitab.base.U;
import yuku.alkitab.base.config.D;
import yuku.alkitab.base.model.Ari;
import yuku.alkitab.base.model.Book;
import yuku.alkitab.base.model.PericopeBlock;
import yuku.alkitab.base.model.PericopeIndex;
import yuku.alkitab.base.model.Version;
import yuku.bintex.BintexReader;

public class YesReader extends Reader {
	private static final String TAG = YesReader.class.getSimpleName();
	
	private String nf;
	private RandomAccessFile f;
	private ReaderDecoder readerDecoder;
	
	private long teks_dasarOffset;
	private long perikopBlok_dasarOffset;
	
	private String shortTitle;
	private String longTitle;
	private String keterangan;
	private int nkitab;
	private int perikopAda = 0; // default ga ada
	private int encoding = 1; // 1 = ascii; 2 = utf-8;
	
	public YesReader(Context context, String nf) {
		super(context);
		
		this.nf = nf;
	}
	
	/**
	 * @return ukuran seksi
	 */
	private int lewatiSampeSeksi(String seksi) throws Exception {
		f.seek(8); // setelah header
		
		while (true) {
			String namaSeksi = readNamaSeksi(f);
			
			if (namaSeksi == null || namaSeksi.equals("____________")) { //$NON-NLS-1$
				// sudah mencapai EOF. Maka kasih tau seksi ini ga ada.
				Log.d(TAG, "Seksi tidak ditemukan: " + seksi); //$NON-NLS-1$
				return -1;
			}
			
			int ukuran = readUkuranSeksi(f);
			
			if (namaSeksi.equals(seksi)) {
				return ukuran;
			} else {
				Log.d(TAG, "seksi dilewati: " + namaSeksi); //$NON-NLS-1$
				f.skipBytes(ukuran);
			}
		}
	}
	
	private synchronized void init() throws Exception {
		if (f == null) {
			f = new RandomAccessFile(nf, "r"); //$NON-NLS-1$
			f.seek(0);
			
			// cek header
			{
				byte[] buf = new byte[8];
				f.read(buf);
				if (!Arrays.equals(buf, new byte[] {(byte) 0x98, 0x58, 0x0d, 0x0a, 0x00, 0x5d, (byte) 0xe0, 0x01})) {
					throw new RuntimeException("Header ga betul. Ketemunya: " + Arrays.toString(buf)); //$NON-NLS-1$
				}
			}
			
			bacaInfoEdisi();
			
			lewatiSampeSeksi("teks________"); //$NON-NLS-1$
			teks_dasarOffset = f.getFilePointer();
			Log.d(TAG, "teks_dasarOffset = " + teks_dasarOffset); //$NON-NLS-1$
		}
	}

	@Override
	public String getLongName() {
		try {
			init();
			return longTitle;
		} catch (Exception e) {
			Log.e(TAG, "init error", e); //$NON-NLS-1$
			return ""; //$NON-NLS-1$
		}
	}

	public void bacaInfoEdisi() {
		try {
			Log.d(TAG, "bacaInfoEdisi dipanggil"); //$NON-NLS-1$
			
			int ukuran = lewatiSampeSeksi("infoEdisi___"); //$NON-NLS-1$
			byte[] buf = new byte[ukuran];
			f.read(buf);
			BintexReader in = new BintexReader(new ByteArrayInputStream(buf));
			
			String nama = null;
			while (true) {
				String key = in.readShortString();
				
				if (key.equals("versi")) { //$NON-NLS-1$
					int versi = in.readInt();
					if (versi > 2) throw new RuntimeException("Versi Edisi: " + versi + " tidak dikenal"); //$NON-NLS-1$ //$NON-NLS-2$
				} else if (key.equals("format")) { //$NON-NLS-1$ // ini deprecated, sudah diganti jadi "versi". Tapi harus tetap dikenali, kalo ga akan crash.
					in.readInt(); // buang
				} else if (key.equals("nama")) { //$NON-NLS-1$
					nama = in.readShortString();
				} else if (key.equals("shortTitle")) { //$NON-NLS-1$
					this.shortTitle = in.readShortString();
				} else if (key.equals("judul")) { //$NON-NLS-1$
					this.longTitle = in.readShortString();
				} else if (key.equals("keterangan")) { //$NON-NLS-1$
					this.keterangan = in.readLongString();
				} else if (key.equals("nkitab")) { //$NON-NLS-1$
					this.nkitab = in.readInt();
				} else if (key.equals("perikopAda")) { //$NON-NLS-1$
					this.perikopAda = in.readInt();
				} else if (key.equals("encoding")) { //$NON-NLS-1$
					this.encoding = in.readInt();
				} else if (key.equals("end")) { //$NON-NLS-1$
					break;
				} else {
					Log.w(TAG, "ada key ga dikenal di infoEdisi: " + key); //$NON-NLS-1$ 
					break;
				}
			}
			
			Log.d(TAG, "bacaInfoEdisi selesai, nama=" + nama + " judul=" + longTitle + " nkitab=" + nkitab); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		} catch (Exception e) {
			Log.e(TAG, "bacaInfoEdisi error", e); //$NON-NLS-1$
		}
	}

	@Override public Book[] bacaInfoKitab() {
		try {
			Log.d(TAG, "bacaInfoKitab dipanggil"); //$NON-NLS-1$
			
			init();
			
			Book[] res = new Book[256];
			
			int ukuran = lewatiSampeSeksi("infoKitab___"); //$NON-NLS-1$
			byte[] buf = new byte[ukuran];
			f.read(buf);
			BintexReader in = new BintexReader(new ByteArrayInputStream(buf));
			
			Log.d(TAG, "akan membaca " + this.nkitab + " kitab"); //$NON-NLS-1$ //$NON-NLS-2$
			for (int kitabIndex = 0; kitabIndex < this.nkitab; kitabIndex++) {
				Book k = new Book();
				
				// kalau true, berarti ini kitab NULL
				boolean kosong = false;
				
				for (int keyKe = 0;; keyKe++) {
					String key = in.readShortString();
					
					if (key.equals("versi")) { //$NON-NLS-1$
						int versi = in.readInt();
						if (versi > 2) throw new RuntimeException("Versi Kitab (lebih dari 2): " + versi + " tidak dikenal"); //$NON-NLS-1$ //$NON-NLS-2$
					} else if (key.equals("pos")) { //$NON-NLS-1$
						k.bookId = in.readInt();
					} else if (key.equals("nama")) { //$NON-NLS-1$
						k.nama = in.readShortString();
					} else if (key.equals("judul")) { //$NON-NLS-1$
						k.judul = in.readShortString();
					} else if (key.equals("npasal")) { //$NON-NLS-1$
						k.nchapter = in.readInt();
					} else if (key.equals("nayat")) { //$NON-NLS-1$
						k.nverses = new int[k.nchapter];
						for (int i = 0; i < k.nchapter; i++) {
							k.nverses[i] = in.readUint8();
						}
					} else if (key.equals("ayatLoncat")) { //$NON-NLS-1$
						// TODO di masa depan
						in.readInt();
					} else if (key.equals("pdbBookNumber")) { //$NON-NLS-1$
						// TODO di masa depan
						in.readInt();
					} else if (key.equals("pasal_offset")) { //$NON-NLS-1$
						k.pasal_offset = new int[k.nchapter + 1]; // harus ada +1nya kalo YesPembaca
						for (int i = 0; i < k.pasal_offset.length; i++) {
							k.pasal_offset[i] = in.readInt();
						}
					} else if (key.equals("encoding")) { //$NON-NLS-1$
						// TODO di masa depan, mungkin deprecated, karena ini lebih cocok di edisi.
						in.readInt();
					} else if (key.equals("offset")) { //$NON-NLS-1$
						k.offset = in.readInt();
					} else if (key.equals("end")) { //$NON-NLS-1$
						// sudah end sebelum baca apapun?
						if (keyKe == 0) kosong = true;
						break;
					} else {
						Log.w(TAG, "ada key ga dikenal di kitab " + k + " di infoKitab: " + key); //$NON-NLS-1$ //$NON-NLS-2$
						break;
					}
				}
				
				if (!kosong) {
					if (k.bookId < 0 || k.bookId >= res.length) {
						throw new RuntimeException("ada kitabPos yang sangat besar: " + k.bookId); //$NON-NLS-1$
					}
					res[k.bookId] = k;
				}
			}
			
			// truncate res supaya ukuran arraynya jangan terlalu besar, sampe non-null terakhir
			int lenBaru = 0;
			for (int i = 0; i < res.length; i++) {
				if (res[i] != null) lenBaru = i + 1;
			}
			Book[] resBaru = new Book[lenBaru];
			System.arraycopy(res, 0, resBaru, 0, lenBaru);
			res = resBaru;
			
			return res;
		} catch (Exception e) {
			Log.e(TAG, "bacaInfoKitab error", e); //$NON-NLS-1$
			return null;
		}
	}
	
	@Override
	public String[] muatTeks(Book book, int pasal_1, boolean janganPisahAyat, boolean hurufKecil) {
		// init pembacaDecoder
		if (readerDecoder == null) {
			if (encoding == 1) {
				readerDecoder = new ReaderDecoder.Ascii();
			} else if (encoding == 2) {
				readerDecoder = new ReaderDecoder.Utf8();
			} else {
				Log.e(TAG, "Encoding " + encoding + " not recognized!");  //$NON-NLS-1$//$NON-NLS-2$
				readerDecoder = new ReaderDecoder.Ascii();
			}
			Log.d(TAG, "encoding " + encoding + " so decoder is " + readerDecoder.getClass().getName()); //$NON-NLS-1$ //$NON-NLS-2$
		}
		
		try {
			init();
			
			if (pasal_1 > book.nchapter) {
				return null;
			}
			
			long seekTo = teks_dasarOffset;
			seekTo += book.offset;
			seekTo += book.pasal_offset[pasal_1 - 1];
			f.seek(seekTo);
			
			int length = book.pasal_offset[pasal_1] - book.pasal_offset[pasal_1 - 1];
			
			if (D.EBUG) Log.d(TAG, "muatTeks kitab=" + book.nama + " pasal_1=" + pasal_1 + " offset=" + book.offset + " offset pasal: " + book.pasal_offset[pasal_1-1]); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
			
			byte[] ba = new byte[length];
			f.read(ba);
			
			if (janganPisahAyat) {
				return new String[] {readerDecoder.jadikanStringTunggal(ba, hurufKecil)};
			} else {
				String[] xayat = readerDecoder.pisahJadiAyat(ba, hurufKecil);
				if (D.EBUG) for (int i = 0; i < xayat.length; i++) {
					Log.d(TAG, "ayat_1 " + (i+1) + ": " + U.dumpChars(xayat[i]));  //$NON-NLS-1$//$NON-NLS-2$
				}
				return xayat;
			}
		} catch (Exception e) {
			Log.e(TAG, "muatTeks error", e); //$NON-NLS-1$
			return null;
		}
	}

	@SuppressWarnings("deprecation") static String readNamaSeksi(RandomAccessFile f) throws IOException {
		byte[] buf = new byte[12];
		int read = f.read(buf);
		return read <= 0? null: new String(buf, 0);
	}

	static int readUkuranSeksi(RandomAccessFile f) throws IOException {
		return f.readInt();
	}

	@Override
	public PericopeIndex bacaIndexPerikop() {
		long wmulai = System.currentTimeMillis();
		try {
			init();
			
			if (perikopAda == 0) {
				return null;
			}
			
			int ukuran = lewatiSampeSeksi("perikopIndex"); //$NON-NLS-1$
			
			if (ukuran < 0) {
				Log.d(TAG, "Tidak ada seksi 'perikopIndex'"); //$NON-NLS-1$
				return null;
			}
			
			BintexReader in = new BintexReader(new RandomInputStream(f));
			return PericopeIndex.baca(in);
		} catch (Exception e) {
			Log.e(TAG, "bacaIndexPerikop error", e); //$NON-NLS-1$
			return null;
		} finally {
			Log.d(TAG, "Muat index perikop butuh ms: " + (System.currentTimeMillis() - wmulai)); //$NON-NLS-1$
		}
	}

	@Override
	public int loadPericope(Version version, int kitab, int pasal, int[] xari, PericopeBlock[] xblok, int max) {
		try {
			init();
			
			if (D.EBUG) Log.d(TAG, "muatPerikop dipanggil untuk kitab=" + kitab + " pasal_1=" + pasal); //$NON-NLS-1$ //$NON-NLS-2$
			
			PericopeIndex pericopeIndex = version.getIndexPerikop();
			if (pericopeIndex == null) {
				return 0; // ga ada perikop!
			}
	
			int ariMin = Ari.encode(kitab, pasal, 0);
			int ariMax = Ari.encode(kitab, pasal + 1, 0);
	
			int pertama = pericopeIndex.cariPertama(ariMin, ariMax);
			if (pertama == -1) {
				return 0;
			}
	
			int kini = pertama;
			int res = 0;
			
			if (perikopBlok_dasarOffset != 0) {
				f.seek(perikopBlok_dasarOffset);
			} else {
				lewatiSampeSeksi("perikopBlok_"); //$NON-NLS-1$
				perikopBlok_dasarOffset = f.getFilePointer();
			}
			
			BintexReader in = new BintexReader(new RandomInputStream(f));
			while (true) {
				int ari = pericopeIndex.getAri(kini);

				if (ari >= ariMax) {
					// habis. Uda ga relevan
					break;
				}

				PericopeBlock pericopeBlock = pericopeIndex.getBlok(in, kini);
				kini++;

				if (res < max) {
					xari[res] = ari;
					xblok[res] = pericopeBlock;
					res++;
				} else {
					break;
				}
			}
	
			return res;
		} catch (Exception e) {
			Log.e(TAG, "gagal muatPerikop", e); //$NON-NLS-1$
			return 0;
		}
	}

	/**
	 * Mungkin null kalo ga ada.
	 */
	public String getDescription() {
		try {
			init();
			return keterangan;
		} catch (Exception e) {
			Log.e(TAG, "init error", e); //$NON-NLS-1$
			return null;
		}
	}
}
