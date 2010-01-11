package yuku.alkitab;

import yuku.alkitab.model.*;
import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.content.pm.PackageManager.*;
import android.os.*;
import android.text.*;
import android.text.style.*;
import android.util.*;
import android.view.*;
import android.widget.*;

public class IsiActivity extends Activity {
	String[] xayat;
	int[] ayat_offset;
	TextView tIsi;
	ScrollView scrollIsi;
	int pasal = 0;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.isi);
		
		S.siapinEdisi(getResources());
		S.siapinKitab(getResources());
		
		tIsi = (TextView) findViewById(R.id.tIsi);
		scrollIsi = (ScrollView) findViewById(R.id.scrollIsi);
		
		tampil(0, 0); // TODO tampilin yang terakhir dong!
	}

	private int getAyatTop(int ayat) {
		Layout layout = tIsi.getLayout();
		int line = layout.getLineForOffset(ayat_offset[ayat-1]);
		return layout.getLineTop(line);
	}

	private SpannableStringBuilder siapinTampilanAyat() {
		SpannableStringBuilder res = new SpannableStringBuilder();
		
		int c = 0;
		String pengawal = "";
		String pengakhir = "\n";
		
		ayat_offset = new int[xayat.length];
		for (int i = 0; i < xayat.length; i++) {
			ayat_offset[i] = c;
			
			pengawal = (i+1) + " ";
			res.append(pengawal).append(xayat[i]).append(pengakhir);
			res.setSpan(new ForegroundColorSpan(0xff8080ff), c, c + pengawal.length() - 1, 0);
			
			c += pengawal.length() + xayat[i].length() + pengakhir.length();
		}
		
		return res;
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		new MenuInflater(this).inflate(R.menu.isi, menu);
		
		menu.add(0, 0x985801, 0, "gebug 1");
		menu.add(0, 0x985802, 0, "gebug 2");
		
		return true;
	}
	
	@Override
	public boolean onMenuItemSelected(int featureId, MenuItem item) {
		if (item.getItemId() == R.id.menuTuju) {
			Intent intent = new Intent(this, MenujuActivity.class);
			intent.putExtra("pasal", pasal);
			startActivityForResult(intent, R.id.menuTuju);
		} else if (item.getItemId() == R.id.menuKitab) {
			Intent intent = new Intent(this, KitabActivity.class);
			startActivityForResult(intent, R.id.menuKitab);
		} else if (item.getItemId() == R.id.menuEdisi) {
			Intent intent = new Intent(this, EdisiActivity.class);
			startActivityForResult(intent, R.id.menuEdisi);
		} else if (item.getItemId() == R.id.menuTentang) {
			String verName = "null";
	    	int verCode = -1;
	    	
			try {
				PackageInfo packageInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
				verName = packageInfo.versionName;
				verCode = packageInfo.versionCode;
			} catch (NameNotFoundException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
	    	
			new AlertDialog.Builder(this).setTitle(R.string.tentang_title).setMessage(
					Html.fromHtml(getString(R.string.tentang_message, verName, verCode))).show();
		} else if (item.getItemId() == 0x985801) { // debug 1
			CharSequence t = tIsi.getText();
			SpannableStringBuilder builder = new SpannableStringBuilder(t);
			builder.append("n");
			tIsi.setText(builder);
		} else if (item.getItemId() == 0x985802) { // debug 2
			CharSequence t = tIsi.getText();
			Log.w("disyuh", t.subSequence(t.length()-10, t.length()).toString());
			tIsi.setText(t.subSequence(0, t.length() - 10));
		}
		
		return super.onMenuItemSelected(featureId, item);
	}
	
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (requestCode == R.id.menuTuju) {
			if (resultCode == RESULT_OK) {
				int pasal = data.getIntExtra("pasal", 0);
				int ayat = data.getIntExtra("ayat", 0);
				
				tampil(pasal, ayat);
			}
		} else if (requestCode == R.id.menuEdisi) {
			if (resultCode == RESULT_OK) {
				String nama = data.getStringExtra("nama");
				
				for (Edisi e : S.xedisi) {
					if (e.nama.equals(nama)) {
						S.edisi = e;
						//# buang kitab2 yang uda kelod
						S.xkitab = null;
						S.kitab = null;
						break;
					}
				}
				
				S.siapinKitab(getResources());
			}
		} else if (requestCode == R.id.menuKitab) {
			if (resultCode == RESULT_OK) {
				String nama = data.getStringExtra("nama");
				
				for (Kitab k : S.xkitab) {
					if (k.nama.equals(nama)) {
						S.kitab = k;
						tampil(pasal, 0);
						break;
					}
				}
			}
		}
	}

	private void tampil(int pasal, int ayat) {
		if (pasal < 1) pasal = 1;
		if (pasal > S.kitab.npasal) pasal = S.kitab.npasal;
		
		if (ayat < 1) ayat = 1;
		if (ayat > S.kitab.nayat[pasal-1]) ayat = S.kitab.nayat[pasal-1];
		
		// muat data pake async dong.
		new AsyncTask<Integer, Void, SpannableStringBuilder>() {
			int pasal;
			int ayat;
			
			@Override
			protected SpannableStringBuilder doInBackground(Integer... params) {
				pasal = params[0];
				ayat = params[1];
				
				xayat = S.muatTeks(getResources(), pasal);
				return siapinTampilanAyat();
			}
		
			@Override
			protected void onPostExecute(SpannableStringBuilder result) {
				tIsi.setText(result);
				setTitle(getString(R.string.judulIsi, S.kitab.judul, pasal, ayat));
				IsiActivity.this.pasal = pasal;
				
				scrollIsi.post(new Runnable() {
					@Override
					public void run() {
						int y = getAyatTop(ayat);
						scrollIsi.scrollTo(0, y - ViewConfiguration.get(IsiActivity.this).getScaledFadingEdgeLength());
					}
				});
				
//				int len = result.length();
//				for (int i = 0; i < len; i += 10) {
//					String s = "";
//					for (int j = i; j < i+10 && j < len; j++) {
//						char c = result.charAt(j);
//						s += String.format("%02x(%c) ", (int)c, c);
//					}
//					Log.d("alki", s);
//				}
			}
		}.execute(pasal, ayat);
	}
	
	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
			tampil(pasal-1, 1);
			return true;
		} else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
			tampil(pasal+1, 1);
			return true;
		}
		
		return super.onKeyDown(keyCode, event);
	}
}
