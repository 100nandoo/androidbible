package yuku.alkitab;

import java.io.*;
import java.util.*;

import yuku.alkitab.model.*;
import yuku.alkitab.renungan.TukangDonlot;
import yuku.bintex.BintexReader;
import yuku.kirimfidbek.PengirimFidbek;
import android.content.*;
import android.graphics.Typeface;
import android.preference.PreferenceManager;
import android.util.Log;

public class S {
	/**
	 * penerapan dari pengaturan
	 */
	public static class penerapan {
		/** dalam dp */
		public static float ukuranHuruf2dp;
		public static Typeface jenisHuruf;
		public static int tebalHuruf;
		public static int warnaHuruf; 
		public static int warnaLatar;
		public static int warnaNomerAyat;
		public static int indenParagraf;
		public static int menjorokSatu;
		public static int menjorokDua;
		public static boolean sortKitabAlfabet;
		public static boolean matikanTahanAyat;
		public static boolean nyalakanTerusLayar;
		public static boolean gebug_tehelBewarna;
		public static boolean prioritasLoncat;
		public static boolean sembunyiNavigasi;
	}
	
	/**
	 * Seting yang tetep hidup walau aktiviti dimusnahkan.
	 * Pastikan ga ada acuan ke aktiviti, supaya memori ga bocor.
	 */
	public static class penampungan {
		public static String renungan_nama = null;
		public static Date renungan_tanggalan = null;
		public static String search_carian = null; // TODO: syuh ketika search2 stabil
	}
	
	//# 33nya harus siap di siapinEdisi
	public static Edisi[] xedisi;
	public static Edisi edisiAktif;
	
	//# 22nya harus siap di siapinKitab
	public static Kitab kitabAktif;

	public static PengirimFidbek pengirimFidbek;
	public static TukangDonlot tukangDonlot;
	
	static {
		int a = R.drawable.ambilwarna_panah;
		if (a == 0) throw new RuntimeException(); // cuma mencegah project ambilwarna lupa dibuka
	}
	
	public static synchronized void siapinEdisi(Context context) {
		if (xedisi != null) return;
		
		long wmulai = System.currentTimeMillis();

		BintexReader in = new BintexReader(context.getResources().openRawResource(R.raw.edisi_index_bt));
		ArrayList<Edisi> xedisi = new ArrayList<Edisi>();

		try {
			while (true) {
				Edisi e = Edisi.baca(in);
				xedisi.add(e);
			}
		} catch (EOFException e) {
			// selesai baca
		} catch (IOException e) {
			Log.e("alki", "ngaco baca edisi index!!!");
		}

		S.xedisi = xedisi.toArray(new Edisi[xedisi.size()]);
		S.edisiAktif = S.xedisi[0]; // TODO selalu pilih edisi pertama
		
		Log.d("alki", "siapinEdisi butuh ms: " + (System.currentTimeMillis() - wmulai));
	}

	public static synchronized void siapinKitab(Context context) {
		if (S.xedisi == null || S.edisiAktif == null) {
			siapinEdisi(context);
		}
		if (S.edisiAktif.volatile_xkitab != null) return;

		Edisi edisi = S.edisiAktif;
		Log.d("alki", "siapinKitab mulai dengan edisi: " + edisi.nama);
		S.edisiAktif.volatile_xkitab = edisi.pembaca.bacaInfoKitab(context, edisi);
		S.kitabAktif = S.edisiAktif.volatile_xkitab[0]; // nanti diset sama luar 
		
		if (edisi.perikopAda != 0) {
			if (edisi.volatile_indexPerikop == null) {
				edisi.volatile_indexPerikop = edisi.pembaca.bacaIndexPerikop(context, edisi);
			}
		}
		
		Log.d("alki", "siapinKitab selesai");
	}
	
	public static void bacaPengaturan(Context context) {
		Log.d("alki", "bacaPengaturan mulai");
		SharedPreferences pengaturan = PreferenceManager.getDefaultSharedPreferences(context);

		//# atur ukuran huruf isi berdasarkan pengaturan
		{
			float ukuranHuruf2 = pengaturan.getFloat(context.getString(R.string.pref_ukuranHuruf2_key), 17.f);
			S.penerapan.ukuranHuruf2dp = ukuranHuruf2;
			Log.d("alki", "ukuranHuruf2 dalam dp = " + S.penerapan.ukuranHuruf2dp);
			
			S.penerapan.indenParagraf = (int) (context.getResources().getDimension(R.dimen.indenParagraf) * ukuranHuruf2 / 17.f);
			Log.d("alki", "indenParagraf dalam px = " + S.penerapan.indenParagraf);
			S.penerapan.menjorokSatu = (int) (context.getResources().getDimension(R.dimen.menjorokSatu) * ukuranHuruf2 / 17.f);
			Log.d("alki", "menjorokSatu dalam px = " + S.penerapan.menjorokSatu);
			S.penerapan.menjorokDua = (int) (context.getResources().getDimension(R.dimen.menjorokDua) * ukuranHuruf2 / 17.f);
			Log.d("alki", "menjorokDua dalam px = " + S.penerapan.menjorokDua);
		}
		
		//# atur jenis huruf, termasuk boldnya
		{
			String jenisHuruf_s = pengaturan.getString(context.getString(R.string.pref_jenisHuruf_key), null);
			Typeface typeface = U.typeface(jenisHuruf_s);
			S.penerapan.jenisHuruf = typeface;
			
			boolean boldHuruf_b = pengaturan.getBoolean(context.getString(R.string.pref_boldHuruf_key), false);
			S.penerapan.tebalHuruf = boldHuruf_b? Typeface.BOLD: Typeface.NORMAL;
		}
		
		//# atur warna teks, latar, dan nomer ayat
		{
			int warnaHuruf = pengaturan.getInt(context.getString(R.string.pref_warnaHuruf_int_key), 0xfff0f0f0);
			S.penerapan.warnaHuruf = warnaHuruf;

			int warnaLatar = pengaturan.getInt(context.getString(R.string.pref_warnaLatar_int_key), 0xff000000);
			S.penerapan.warnaLatar = warnaLatar;
			// Terapkan saat ini juga (liat IsiActivity)
			
			int warnaNomerAyat = pengaturan.getInt(context.getString(R.string.pref_warnaNomerAyat_int_key), 0xff8080ff);
			S.penerapan.warnaNomerAyat = warnaNomerAyat;
		}
		
		S.penerapan.prioritasLoncat = pengaturan.getBoolean(context.getString(R.string.pref_tombolAlamatLoncat_key), false);
		
		//# sembunyikan navigasi kalo perlu
		{
			boolean sembunyiNavigasi = pengaturan.getBoolean(context.getString(R.string.pref_tanpaNavigasi_key), false);
			S.penerapan.sembunyiNavigasi = sembunyiNavigasi;
			// Terapkan saat ini juga (liat IsiActivity)
		}
		
		//# abjad kah?
		{
			boolean sortKitabAlfabet = pengaturan.getBoolean(context.getString(R.string.pref_sortKitabAlfabet_key), false);
			S.penerapan.sortKitabAlfabet = sortKitabAlfabet;
		}
		
		//# jangan nyalakan context menu kah?
		{
			boolean matikanTahanAyat = pengaturan.getBoolean(context.getString(R.string.pref_matikanTahanAyat_key), false);
			S.penerapan.matikanTahanAyat = matikanTahanAyat;
		}
		
		//# layar selalu nyala kah?
		{
			boolean nyalakanTerusLayar = pengaturan.getBoolean(context.getString(R.string.pref_nyalakanTerusLayar_key), false);
			S.penerapan.nyalakanTerusLayar = nyalakanTerusLayar;
		}
		Log.d("alki", "bacaPengaturan selesai");
	}
	
	public static synchronized String[] muatTeks(Context context, Edisi edisi, Kitab kitab, int pasal_1) {
		return muatTeks(context, edisi, kitab, pasal_1, false, false);
	}

	public static synchronized String muatTeksJanganPisahAyatHurufKecil(Context context, Edisi edisi, Kitab kitab, int pasal_1) {
		return muatTeks(context, edisi, kitab, pasal_1, true, true)[0];
	}
	
	private static String[] muatTeks(Context context, Edisi edisi, Kitab kitab, int pasal_1, boolean janganPisahAyat, boolean hurufKecil) {
		return edisi.pembaca.muatTeks(context, edisi, kitab, pasal_1, janganPisahAyat, hurufKecil);
	}

	/**
	 * @return berapa yang keisi
	 */
	public static synchronized int muatPerikop(Context context, Edisi edisi, int kitab, int pasal, int[] xari, Blok[] xblok, int max) {
		return edisi.pembaca.muatPerikop(context, edisi, kitab, pasal, xari, xblok, max);
	}

	public static synchronized void siapinPengirimFidbek(final Context context) {
		if (pengirimFidbek == null) {
			pengirimFidbek = new PengirimFidbek(context, getPreferences(context));
			pengirimFidbek.activateDefaultUncaughtExceptionHandler();
			pengirimFidbek.setOnSuccessListener(new PengirimFidbek.OnSuccessListener() {
				@Override
				public void onSuccess(final byte[] response) {
					Log.e("alki", "KirimFidbek respon: " + new String(response, 0, response.length));
				}
			});
		}
	}

	public static SharedPreferences getPreferences(Context context) {
		return context.getSharedPreferences(context.getPackageName(), 0);
	}

	public static String alamat(Edisi edisi, int ari) {
		int kitab = Ari.toKitab(ari);
		int pasal_1 = Ari.toPasal(ari);
		int ayat_1 = Ari.toAyat(ari);
		
		StringBuilder hasil = new StringBuilder(40);
		if (kitab >= edisi.volatile_xkitab.length) {
			hasil.append("[").append(kitab).append("] ");
		} else {
			hasil.append(edisi.volatile_xkitab[kitab].judul).append(" ");
		}
		
		hasil.append(pasal_1).append(":").append(ayat_1);
		return hasil.toString();
	}

	public static String alamat(Kitab kitab, int pasal_1) {
		return kitab.judul + " " + pasal_1;
	}
}
