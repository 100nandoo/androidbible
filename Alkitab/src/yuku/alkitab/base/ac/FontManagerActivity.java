package yuku.alkitab.base.ac;

import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import yuku.alkitab.R;
import yuku.alkitab.base.App;
import yuku.alkitab.base.U;
import yuku.alkitab.base.ac.base.BaseActivity;
import yuku.alkitab.base.rpc.SimpleHttpConnection;
import yuku.alkitab.base.sv.DownloadService;
import yuku.alkitab.base.sv.DownloadService.DownloadBinder;
import yuku.alkitab.base.sv.DownloadService.DownloadEntry;
import yuku.alkitab.base.sv.DownloadService.DownloadListener;
import yuku.alkitab.base.util.FontManager;
import yuku.alkitab.base.widget.UrlImageView;
import yuku.alkitab.base.widget.UrlImageView.OnStateChangeListener;

public class FontManagerActivity extends BaseActivity implements DownloadListener {
	public static final String TAG = FontManagerActivity.class.getSimpleName();

	public static Intent createIntent() {
		return new Intent(App.context, FontManagerActivity.class);
	}

	ListView lsFont;
	FontAdapter adapter;
	TextView lEmptyError;
	DownloadService dls;
	
	private ServiceConnection serviceConnection = new ServiceConnection() {
		@Override public void onServiceDisconnected(ComponentName name) {
			dls = null;
		}
		
		@Override public void onServiceConnected(ComponentName name, IBinder service) {
			dls = ((DownloadBinder) service).getService();
			dls.setDownloadListener(FontManagerActivity.this);
			runOnUiThread(new Runnable() {
				@Override public void run() {
					loadFontList();
				}
			});
		}
	};

	@Override protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.activity_font_manager);
		setTitle(R.string.fm_activity_title);

		lsFont = U.getView(this, R.id.lsFont);
		lEmptyError = U.getView(this, R.id.lEmptyError);
		
		lsFont.setAdapter(adapter = new FontAdapter());
		lsFont.setEmptyView(lEmptyError);
		
		bindService(new Intent(App.context, DownloadService.class), serviceConnection, BIND_AUTO_CREATE);
	}
	
	@Override protected void onDestroy() {
		super.onDestroy();
		
		if (dls != null) {
			unbindService(serviceConnection);
		}
	}

	private void loadFontList() {
		new AsyncTask<Void, Void, List<FontItem>>() {
			String errorMsg;
			
			@Override protected List<FontItem> doInBackground(Void... params) {
				SimpleHttpConnection conn = new SimpleHttpConnection("http://alkitab-host.appspot.com/addon/fonts/v1/list.txt");
				try {
					InputStream in = conn.load();
					if (in == null) {
						Exception ex = conn.getException();
						errorMsg = ex.getClass().getSimpleName() + " " + ex.getMessage();
						return null;
					} else {
						if (conn.isSameHost()) {
							List<FontItem> list = new ArrayList<FontItem>();
							Scanner sc = new Scanner(in);
							while (sc.hasNextLine()) {
								String line = sc.nextLine().trim();
								if (line.length() > 0) {
									FontItem item = new FontItem();
									item.name = line.split(" ")[0];
									list.add(item);
								}
							}
							return list;
						} else {
							errorMsg = "You need to log-in to your (Wi-Fi) network first.";
						}
						return null;
					}
				} finally {
					conn.close();
				}
			}
			
			@Override protected void onPostExecute(List<FontItem> result) {
				if (result != null) {
					lEmptyError.setText(null);
					adapter.setData(result);
				} else {
					lEmptyError.setText(errorMsg);
				}
			};
		}.execute();
	}
	
	private String getFontDownloadKey(String name) {
		return "FontManager/" + name;
	}
	
	private String getFontNameFromDownloadKey(String key) {
		return key.substring("FontManager/".length());
	}

	private String getFontDownloadDestination(String name) {
		return new File(FontManager.getFontsPath(), "download-" + name + ".zip").getAbsolutePath();
	}
	
	public static class FontItem {
		public String name;
	}
	
	public class FontAdapter extends BaseAdapter {
		List<FontItem> list;
		
		public void setData(List<FontItem> list) {
			this.list = list;
			notifyDataSetChanged();
		}

		@Override public int getCount() {
			return list == null? 0: list.size();
		}

		@Override public FontItem getItem(int position) {
			return list.get(position);
		}

		@Override public long getItemId(int position) {
			return position;
		}

		@Override public View getView(int position, View convertView, ViewGroup parent) {
			View res = convertView != null ? convertView : getLayoutInflater().inflate(R.layout.item_font_download, null);

			UrlImageView imgPreview = U.getView(res, R.id.imgPreview);
			TextView lFontName = U.getView(res, R.id.lFontName);
			View bDownload = U.getView(res, R.id.bDownload);
			View bDelete = U.getView(res, R.id.bDelete);
			ProgressBar progressbar = U.getView(res, R.id.progressbar);
			TextView lErrorMsg = U.getView(res, R.id.lErrorMsg);
			
			FontItem item = getItem(position);
			String dlkey = getFontDownloadKey(item.name);
			
			lFontName.setText(item.name);
			lFontName.setVisibility(View.VISIBLE);
			imgPreview.setTag(R.id.TAG_fontName, lFontName);
			imgPreview.setOnStateChangeListener(imgPreview_stateChange);
			imgPreview.setUrl("http://alkitab-host.appspot.com/addon/fonts/v1/preview/" + item.name + "-384x84.png");
			bDownload.setTag(R.id.TAG_fontItem, item);
			bDownload.setOnClickListener(bDownload_click);
			bDelete.setTag(R.id.TAG_fontItem, item);
			bDelete.setOnClickListener(bDelete_click);
			
			if (FontManager.isInstalled(item.name)) {
				progressbar.setIndeterminate(false);
				progressbar.setMax(100);
				progressbar.setProgress(100);
				bDownload.setVisibility(View.GONE);
				bDelete.setVisibility(View.VISIBLE);
				lErrorMsg.setVisibility(View.GONE);
			} else {
				DownloadEntry entry = dls.getEntry(dlkey);
				if (entry == null) {
					progressbar.setIndeterminate(false);
					progressbar.setMax(100);
					progressbar.setProgress(0);
					bDownload.setVisibility(View.VISIBLE);
					bDownload.setEnabled(true);
					bDelete.setVisibility(View.GONE);
					lErrorMsg.setVisibility(View.GONE);
				} else {
					if (entry.state == DownloadService.State.created) {
						progressbar.setIndeterminate(true);
						bDownload.setVisibility(View.VISIBLE);
						bDownload.setEnabled(false);
						bDelete.setVisibility(View.GONE);
						lErrorMsg.setVisibility(View.GONE);
					} else if (entry.state == DownloadService.State.downloading) {
						if (entry.length == -1) {
							progressbar.setIndeterminate(true);
						} else {
							progressbar.setIndeterminate(false);
							progressbar.setMax((int) entry.length);
							progressbar.setProgress((int) entry.progress);
						}
						bDownload.setVisibility(View.VISIBLE);
						bDownload.setEnabled(false);
						bDelete.setVisibility(View.GONE);
						lErrorMsg.setVisibility(View.GONE);
					} else if (entry.state == DownloadService.State.finished) {
						progressbar.setIndeterminate(false);
						progressbar.setMax(100); // consider full
						progressbar.setProgress(100); // consider full
						bDownload.setVisibility(View.GONE);
						bDelete.setVisibility(View.VISIBLE);
						lErrorMsg.setVisibility(View.GONE);
					} else if (entry.state == DownloadService.State.failed) {
						progressbar.setIndeterminate(false);
						progressbar.setMax(100);
						progressbar.setProgress(0);
						bDownload.setVisibility(View.VISIBLE);
						bDownload.setEnabled(true);
						bDelete.setVisibility(View.GONE);
						lErrorMsg.setVisibility(View.VISIBLE);
						lErrorMsg.setText(entry.errorMsg);
					}
				}
			}
			
			return res;
		}
		
		private OnClickListener bDownload_click = new OnClickListener() {
			@Override public void onClick(View v) {
				FontItem item = (FontItem) v.getTag(R.id.TAG_fontItem);
				
				String dlkey = getFontDownloadKey(item.name);
				dls.removeEntry(dlkey);
				
				if (dls.getEntry(dlkey) == null) {
					new File(FontManager.getFontsPath()).mkdirs();
					dls.startDownload(
						dlkey, 
						"http://alkitab-host.appspot.com/addon/fonts/v1/data/" + item.name + ".zip",
						getFontDownloadDestination(item.name)
					);
				}
				
				notifyDataSetChanged();
			}
		};
		
		private OnClickListener bDelete_click = new OnClickListener() {
			@Override public void onClick(View v) {
				final FontItem item = (FontItem) v.getTag(R.id.TAG_fontItem);
				
				new AlertDialog.Builder(FontManagerActivity.this)
				.setMessage("Do you want to delete " + item.name + "?")
				.setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
					@Override public void onClick(DialogInterface dialog, int which) {
						File fontDir = FontManager.getFontDir(item.name);
						File[] listFiles = fontDir.listFiles();
						if (listFiles != null) {
							for (File file: listFiles) {
								file.delete();
							}
						}
						fontDir.delete();
						
						dls.removeEntry(getFontDownloadKey(item.name));
						
						notifyDataSetChanged();
					}
				})
				.setNegativeButton(R.string.no, null)
				.show();
			}
		};
		
		private OnStateChangeListener imgPreview_stateChange = new OnStateChangeListener() {
			@Override public void onStateChange(UrlImageView v, UrlImageView.State newState, String url) {
				if (newState.isLoaded()) {
					TextView lFontName = (TextView) v.getTag(R.id.TAG_fontName);
					lFontName.setVisibility(View.GONE);
				}
			}
		};
	}

	@Override public void onStateChanged(DownloadEntry entry) {
		// TODO optimize
		adapter.notifyDataSetChanged();
		
		if (entry.state == DownloadService.State.finished) {
			String fontName = getFontNameFromDownloadKey(entry.key);
			try {
				String downloadedZip = getFontDownloadDestination(fontName);
				File fontDir = FontManager.getFontDir(fontName);
				fontDir.mkdirs();
				
				Log.d(TAG, "Going to unzip " + downloadedZip);
				
				ZipInputStream zis = new ZipInputStream(new BufferedInputStream(new FileInputStream(downloadedZip)));
				try {
					ZipEntry ze;
					while ((ze = zis.getNextEntry()) != null) {
						String zname = ze.getName();
						Log.d(TAG, "Extracting from zip: " + zname);
						File extractFile = new File(fontDir, zname);
						FileOutputStream fos = new FileOutputStream(extractFile);
						try {
							byte[] buf = new byte[4096];
							int count;
							while ((count = zis.read(buf)) != -1) {
								fos.write(buf, 0, count);
							}
						} finally {
							fos.close();
						}
					}
				} finally {
					zis.close();
				}
				
				new File(downloadedZip).delete();
			} catch (Exception e) {
				new AlertDialog.Builder(FontManagerActivity.this)
				.setMessage("Error when extracting font " + fontName + ": " + e.getMessage())
				.setPositiveButton(R.string.ok, null)
				.show();
			}
		}
	}

	@Override public void onProgress(DownloadEntry entry) {
		adapter.notifyDataSetChanged();
		// TODO optimize
	}
}
