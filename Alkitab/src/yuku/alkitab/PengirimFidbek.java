package yuku.alkitab;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;

import android.app.Activity;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.util.Log;

public class PengirimFidbek {
	private final Activity activity;
	private ArrayList<String> xisi;
	private boolean lagiKirim = false;

	public PengirimFidbek(Activity activity) {
		this.activity = activity;
	}

	public void tambah(String isi) {
		muat();

		xisi.add(isi);
		simpan();
	}

	private synchronized void simpan() {
		if (xisi == null)
			return;

		SharedPreferences preferences = activity.getSharedPreferences(
				S.NAMA_PREFERENCES, 0);
		Editor editor = preferences.edit();

		editor.putInt("nfidbek", xisi.size());

		for (int i = 0; i < xisi.size(); i++) {
			editor.putString("fidbek/" + i + "/isi", xisi.get(i));
		}
		editor.commit();
	}

	public synchronized void cobaKirim() {
		muat();
		
		if (lagiKirim || xisi.size() == 0)
			return;
		lagiKirim = true;

		new Pengirim().start();
	}

	private synchronized void muat() {
		if (xisi == null) {
			xisi = new ArrayList<String>();
			SharedPreferences preferences = activity.getSharedPreferences(
					S.NAMA_PREFERENCES, 0);

			int nfidbek = preferences.getInt("nfidbek", 0);

			for (int i = 0; i < nfidbek; i++) {
				String isi = preferences
						.getString("fidbek/" + i + "/isi", null);
				if (isi != null) {
					xisi.add(isi);
				}
			}
		}
	}

	private class Pengirim extends Thread {
		@Override
		public void run() {
			boolean berhasil = false;
			
			try {
				HttpClient client = new DefaultHttpClient();
				HttpPost post = new HttpPost(
						"http://www.kejut.com/prog/android/fidbek/kirim.php");
				ArrayList<NameValuePair> params = new ArrayList<NameValuePair>();
				params.add(new BasicNameValuePair("package/name", activity
						.getPackageName()));

				for (String isi : xisi) {
					params.add(new BasicNameValuePair("fidbek/isi", isi));
				}

				post.setEntity(new UrlEncodedFormEntity(params, "utf-8"));
				HttpResponse response = client.execute(post);
				
				HttpEntity entity = response.getEntity();
				InputStream content = entity.getContent();
				
				while (true) {
					byte[] b = new byte[4096];
					int read = content.read(b);
					
					if (read <= 0) break;
					Log.i("PengirimFidbek", new String(b, 0, read));
				}
				
				berhasil = true;
			} catch (IOException e) {
				Log.w("PengirimFidbek", "waktu post", e);
			}

			if (berhasil) {
				synchronized (PengirimFidbek.this) {
					xisi.clear();
				}

				simpan();
			}

			lagiKirim = false;
		}
	}
}
