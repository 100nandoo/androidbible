package yuku.alkitab.base.storage;

import java.util.*;

import yuku.andoutil.*;

public interface PembacaDecoder {
	String[] pisahJadiAyat(byte[] ba, boolean hurufKecilkan);
	String jadikanStringTunggal(byte[] ba, boolean hurufKecilkan);

	public class Ascii implements PembacaDecoder {
		private void hurufkecilkan(byte[] ba) {
			for (int i = 0, blen = ba.length; i < blen; i++) {
				byte b = ba[i];
				if (b <= (byte)'Z' && b >= (byte)'A') {
					ba[i] |= 0x20; // perhurufkecilkan
				}
			}
		}
		
		private ArrayList<String> pisahJadiAyatBuf = new ArrayList<String>(60);
		
		@Override public String[] pisahJadiAyat(byte[] ba, boolean hurufKecilkan) {
			char[] ayatBuf = new char[4000];
			int i = 0;
			
			pisahJadiAyatBuf.clear();
			
			if (hurufKecilkan) {
				hurufkecilkan(ba);
			}
			
			//# HANYA BERLAKU KALAU SEMUA byte hanya 7-bit. Akan rusak kalo ada yang 0x80.
			int len = ba.length;
			for (int pos = 0; pos < len; pos++) {
				byte c = ba[pos];
				if (c == (byte)0x0a) {
					String satu = new String(ayatBuf, 0, i);
					pisahJadiAyatBuf.add(satu);
					i = 0;
				} else {
					ayatBuf[i++] = (char) c;
				}
			}
			
			return pisahJadiAyatBuf.toArray(new String[pisahJadiAyatBuf.size()]);
		}

		@Override public String jadikanStringTunggal(byte[] ba, boolean hurufKecilkan) {
			if (hurufKecilkan) {
				hurufkecilkan(ba);
			}

			return new String(ba, 0);
		}
	}
	
	public class Utf8 implements PembacaDecoder {
		private ArrayList<String> pisahJadiAyatBuf = new ArrayList<String>(60);
		
		@Override public String[] pisahJadiAyat(byte[] ba, boolean hurufKecilkan) {
			pisahJadiAyatBuf.clear();
			
			int len = ba.length;
			int dari = 0;
			for (int pos = 0; pos < len; pos++) {
				byte c = ba[pos];
				if (c == (byte)0x0a) {
					String satu = Utf8Decoder.toString(ba, dari, pos - dari);
					if (hurufKecilkan) satu = satu.toLowerCase();
					pisahJadiAyatBuf.add(satu);
					dari = pos + 1;
				}
			}
			
			return pisahJadiAyatBuf.toArray(new String[pisahJadiAyatBuf.size()]);
		}

		@Override public String jadikanStringTunggal(byte[] ba, boolean hurufKecilkan) {
			String semua = Utf8Decoder.toString(ba);
			if (hurufKecilkan) semua = semua.toLowerCase();
			return semua;
		}
	}
}
