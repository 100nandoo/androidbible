package yuku.alkitab.base.ac;

import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.support.v4.app.NavUtils;
import android.support.v4.app.ShareCompat;
import android.support.v4.app.TaskStackBuilder;
import android.text.format.DateFormat;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.util.TypedValue;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.TextView.BufferType;
import android.widget.Toast;
import yuku.afw.storage.Preferences;
import yuku.alkitab.base.App;
import yuku.alkitab.base.S;
import yuku.alkitab.base.U;
import yuku.alkitab.base.ac.base.BaseActivity;
import yuku.alkitab.base.devotion.ArticleMorningEveningEnglish;
import yuku.alkitab.base.devotion.ArticleRenunganHarian;
import yuku.alkitab.base.devotion.ArticleSantapanHarian;
import yuku.alkitab.base.devotion.DevotionArticle;
import yuku.alkitab.base.devotion.DevotionDownloader;
import yuku.alkitab.base.devotion.DevotionDownloader.OnStatusDonlotListener;
import yuku.alkitab.base.storage.Prefkey;
import yuku.alkitab.base.util.Jumper;
import yuku.alkitab.base.widget.CallbackSpan;
import yuku.alkitab.base.widget.DevotionSelectPopup;
import yuku.alkitab.base.widget.DevotionSelectPopup.DevotionSelectPopupListener;
import yuku.alkitab.debug.R;
import yuku.alkitab.util.Ari;
import yuku.alkitabintegration.display.Launcher;

import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class DevotionActivity extends BaseActivity implements OnStatusDonlotListener {
	public static final String TAG = DevotionActivity.class.getSimpleName();

	private static final int REQCODE_share = 1;

	static ThreadLocal<SimpleDateFormat> date_format = new ThreadLocal<SimpleDateFormat>() {
		@Override protected SimpleDateFormat initialValue() {
			return new SimpleDateFormat("yyyyMMdd", Locale.US); //$NON-NLS-1$
		}
	};

	public static Intent createIntent() {
		return new Intent(App.context, DevotionActivity.class);
	}

	public enum DevotionKind {
		SH("sh", "Santapan Harian"),
		RH("rh", "Renungan Harian"),
		ME_EN("me-en", "Morning & Evening"),
		;

		public final String name;
		public final String title;

		DevotionKind(final String name, final String title) {
			this.name = name;
			this.title = title;
		}

		public static DevotionKind getByName(String name) {
			if (name == null) return null;
			for (final DevotionKind kind : values()) {
				if (name.equals(kind.name)) {
					return kind;
				}
			}
			return null;
		}

		public DevotionArticle getArticle(final String date) {
			switch (this) {
				case SH:
					return new ArticleSantapanHarian(date);
				case RH:
					return new ArticleRenunganHarian(date);
				case ME_EN:
					return new ArticleMorningEveningEnglish(date);
			}
			throw new RuntimeException("@@getArticle enum not complete");
		}
	}

	public static final DevotionKind DEFAULT_DEVOTION_KIND = DevotionKind.SH;

	TextView lContent;
	ScrollView scrollContent;
	TextView lStatus;
	
	DevotionSelectPopup popup;
	
	boolean renderSucceeded = false;
	long lastTryToDisplay = 0;
	Animation fadeOutAnim;
	
	// currently shown
	DevotionKind currentKind;
	Date currentDate;

	static class DisplayRepeater extends Handler {
		final WeakReference<DevotionActivity> ac;
		
		public DisplayRepeater(DevotionActivity activity) {
			ac = new WeakReference<>(activity);
		}
		
		@Override public void handleMessage(Message msg) {
			DevotionActivity activity = ac.get();
			if (activity == null) return;
			
			{
				long kini = SystemClock.currentThreadTimeMillis();
				if (kini - activity.lastTryToDisplay < 500) {
					return; // ANEH. Terlalu cepat.
				}
				
				activity.lastTryToDisplay = kini;
			}
			
			activity.goTo(true, 0);
			
			if (!activity.renderSucceeded) {
				activity.displayRepeater.sendEmptyMessageDelayed(0, 12000);
			}
		}
	}

	Handler displayRepeater = new DisplayRepeater(this);
	
	static class DownloadStatusDisplayer extends Handler {
		private WeakReference<DevotionActivity> ac;

		public DownloadStatusDisplayer(DevotionActivity ac) {
			this.ac = new WeakReference<>(ac);
		}
		
		@Override public void handleMessage(Message msg) {
			DevotionActivity ac = this.ac.get();
			if (ac == null) return;
			
			String s = (String) msg.obj;
			if (s != null) {
				ac.lStatus.setText(s);
				ac.lStatus.setVisibility(View.VISIBLE);
				ac.lStatus.startAnimation(ac.fadeOutAnim);
			}
		}
	}
	
	Handler downloadStatusDisplayer = new DownloadStatusDisplayer(this);

	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		setContentView(R.layout.activity_devotion);
		
		fadeOutAnim = AnimationUtils.loadAnimation(this, R.anim.fade_out);

		lContent = (TextView) findViewById(R.id.lContent);
		scrollContent = (ScrollView) findViewById(R.id.scrollContent);
		lStatus = (TextView) findViewById(R.id.lStatus);
		
		// text formats
		lContent.setTextColor(S.applied.fontColor);
		lContent.setBackgroundColor(S.applied.backgroundColor);
		lContent.setTypeface(S.applied.fontFace, S.applied.fontBold);
		lContent.setTextSize(TypedValue.COMPLEX_UNIT_DIP, S.applied.fontSize2dp);
		lContent.setLineSpacing(0, S.applied.lineSpacingMult);

		scrollContent.setBackgroundColor(S.applied.backgroundColor);
		
		popup = new DevotionSelectPopup(this);
		popup.setDevotionSelectListener(popup_listener);
		
		if (Temporaries.devotion_date == null) Temporaries.devotion_date = new Date();
		if (Temporaries.devotion_kind == null) Temporaries.devotion_kind = DEFAULT_DEVOTION_KIND;

		currentKind = Temporaries.devotion_kind;
		currentDate = Temporaries.devotion_date;
		
		// Workaround for crashes due to html tags in the title
		// We remove all rows that contain '<' in the judul
		if (Preferences.getBoolean(Prefkey.patch_devotionSlippedHtmlTags, false) == false) {
			int deleted = S.getDb().deleteDevotionsWithLessThanInTitle();
			Log.d(TAG, "patch_devotionSlippedHtmlTags: deleted " + deleted);
			Preferences.setBoolean(Prefkey.patch_devotionSlippedHtmlTags, true);
		}
		
		new Prefetcher(currentKind).start();
		
		{ // betulin ui update
			if (devotionDownloader != null) {
				devotionDownloader.setListener(this);
			}
		}
		
		display(Temporaries.devotion_scroll);
	}
	
	@Override protected void onStart() {
		super.onStart();
		
		if (Preferences.getBoolean(getString(R.string.pref_keepScreenOn_key), getResources().getBoolean(R.bool.pref_keepScreenOn_default))) {
			lContent.setKeepScreenOn(true);
		}
	}
	
	@Override protected void onDestroy() {
		super.onDestroy();

		Temporaries.devotion_kind = currentKind;
		Temporaries.devotion_date = currentDate;
		Temporaries.devotion_scroll = scrollContent.getScrollY();
	}
		
	private void buildMenu(Menu menu) {
		menu.clear();
		getMenuInflater().inflate(R.menu.activity_devotion, menu);
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		buildMenu(menu);
		
		return true;
	}
	
	@Override public boolean onPrepareOptionsMenu(Menu menu) {
		if (menu != null) {
			buildMenu(menu);
		}
		
		return true;
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		int itemId = item.getItemId();

		if (itemId == android.R.id.home) {
			// must try to create the back stack, since it is possible behind this activity is not the main activity
			final Intent upIntent = NavUtils.getParentActivityIntent(this);
			if (NavUtils.shouldUpRecreateTask(this, upIntent)) {
				TaskStackBuilder.create(this)
				.addNextIntentWithParentStack(upIntent)
				.startActivities();
			} else {
				NavUtils.navigateUpTo(this, upIntent);
			}

			return true;
		} else if (itemId == R.id.menuChangeDate) {
			View anchor = findViewById(R.id.menuChangeDate);
			popup.show(anchor);
			
			return true;
		} else if (itemId == R.id.menuCopy) {
			String toCopy = getActionBar().getTitle() + "\n" + lContent.getText();
			U.copyToClipboard(toCopy);
			
			Toast.makeText(this, R.string.renungan_sudah_disalin, Toast.LENGTH_SHORT).show();
			
			return true;
		} else if (itemId == R.id.menuShare) {
			Intent intent = ShareCompat.IntentBuilder.from(DevotionActivity.this)
			.setType("text/plain") //$NON-NLS-1$
			.setSubject(getActionBar().getTitle().toString())
			.setText(getActionBar().getTitle().toString() + '\n' + lContent.getText())
			.getIntent();
			startActivityForResult(ShareActivity.createIntent(intent, getString(R.string.bagikan_renungan)), REQCODE_share);
			
			return true;
		} else if (itemId == R.id.menuRedownload) {
			willNeed(this.currentKind, date_format.get().format(currentDate), true);
			
			return true;
		} else if (itemId == R.id.menuReminder) {
			openReminderPackage();
			return true;
		}
		
		return super.onOptionsItemSelected(item);
	}

	private void openReminderPackage() {
		final String reminderPackage = "yuku.alkitab.reminder";
		try {
			getPackageManager().getPackageInfo(reminderPackage, 0);
			startActivity(new Intent("yuku.alkitab.reminder.ACTION_REMINDER_SETTINGS"));
		} catch (PackageManager.NameNotFoundException nnfe) {
			try {
				startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + reminderPackage)));
			} catch (ActivityNotFoundException anfe) {
				startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("http://play.google.com/store/apps/details?id=" + reminderPackage)));
			} catch (Exception e) {
				displayError();
			}
		} catch (Exception e) {
			displayError();
		}
	}

	private void displayError() {
		new AlertDialog.Builder(this)
		.setMessage(R.string.dr_error_contact_reminder)
		.setPositiveButton(R.string.ok, null)
		.show();
	}

	DevotionSelectPopupListener popup_listener = new DevotionSelectPopupListener() {
		@Override public void onDismiss(DevotionSelectPopup popup) {
		}
		
		@Override public void onButtonClick(DevotionSelectPopup popup, View v) {
			int id = v.getId();
			if (id == R.id.bPrev) {
				currentDate.setTime(currentDate.getTime() - 3600*24*1000);
				display(0);
			} else if (id == R.id.bNext) {
				currentDate.setTime(currentDate.getTime() + 3600*24*1000);
				display(0);
			} else if (id == R.id.bChange) {
				int index = currentKind.ordinal();
				final DevotionKind[] values = DevotionKind.values();
				index = (index + 1) % values.length;
				currentKind = values[index];
				display(0);
			}
		}
	};

	
	void display(int scroll) {
		displayRepeater.removeMessages(0);
		
		goTo(true, scroll);
	}

	void goTo(boolean prioritize, int scroll) {
		String date = date_format.get().format(currentDate);
		DevotionArticle article = S.getDb().tryGetDevotion(currentKind.name, date);
		if (article == null || !article.getReadyToUse()) {
			willNeed(currentKind, date, prioritize);
			render(article, scroll);
			
			displayRepeater.sendEmptyMessageDelayed(0, 3000);
		} else {
			Log.d(TAG, "sudah siap tampil, kita syuh yang tersisa dari pengulang tampil"); //$NON-NLS-1$
			displayRepeater.removeMessages(0);
			
			render(article, scroll);
		}
	}

	CallbackSpan.OnClickListener verseClickListener = new CallbackSpan.OnClickListener() {
		@Override
		public void onClick(View widget, Object _data) {
			String reference = (String) _data;
			
			Log.d(TAG, "Clicked verse reference inside devotion: " + reference); //$NON-NLS-1$

			int ari;
			if (reference.startsWith("ari:")) {
				ari = Integer.parseInt(reference.substring(4));
			} else {
				Jumper jumper = new Jumper(reference);
				if (! jumper.getParseSucceeded()) {
					new AlertDialog.Builder(DevotionActivity.this)
					.setMessage(getString(R.string.alamat_tidak_sah_alamat, reference))
					.setPositiveButton(R.string.ok, null)
					.show();
					return;
				}

				// Make sure references are parsed using Indonesian book names.
				// TODO support english devotions too
				String[] bookNames = getResources().getStringArray(R.array.standard_book_names_in);
				int[] bookIds = new int[bookNames.length];
				for (int i = 0, len = bookNames.length; i < len; i++) {
					bookIds[i] = i;
				}
	
				int bookId = jumper.getBookId(bookNames, bookIds);
				int chapter_1 = jumper.getChapter();
				int verse_1 = jumper.getVerse();
				ari = Ari.encode(bookId, chapter_1, verse_1);
			}

			startActivity(Launcher.openAppAtBibleLocation(ari));
		}
	};

	private void render(DevotionArticle article, final int skrol) {
		if (article == null) {
			Log.d(TAG, "rendering null article"); //$NON-NLS-1$
		} else {
			Log.d(TAG, "rendering article name=" + article.getName() + " date=" + article.getDate() + " readyToUse=" + article.getReadyToUse()); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		}
		
		if (article != null && article.getReadyToUse()) {
			renderSucceeded = true;
			
			lContent.setText(article.getContent(verseClickListener), BufferType.SPANNABLE);
			lContent.setLinksClickable(true);
			lContent.setMovementMethod(LinkMovementMethod.getInstance());
			
			if (skrol != 0) {
				scrollContent.post(new Runnable() {
					@Override public void run() {
						scrollContent.scrollTo(0, skrol);
					}
				});
			}
		} else {
			renderSucceeded  = false;
			
			if (article == null) {
				lContent.setText(R.string.belum_tersedia_menunggu_pengambilan_data_lewat_internet_pastikan_ada);
			} else { // berarti belum siap pakai
				lContent.setText(R.string.belum_tersedia_mungkin_tanggal_yang_diminta_belum_disiapkan);
			}
		}

		String title = currentKind.title;

		{ // widget texts
			String dateDisplay = namaHari(currentDate) + ", " + DateFormat.getDateFormat(this).format(currentDate);  //$NON-NLS-1$
			
			// action bar
			getActionBar().setTitle(title);
			getActionBar().setSubtitle(dateDisplay);
			
			// popup texts
			popup.setDevotionName(title);
			popup.setDevotionDate(dateDisplay);
		}
	}

	private static final int[] WEEKDAY_NAMES_RESIDS = {R.string.hari_minggu, R.string.hari_senin, R.string.hari_selasa, R.string.hari_rabu, R.string.hari_kamis, R.string.hari_jumat, R.string.hari_sabtu};

	private String namaHari(Date date) {
		@SuppressWarnings("deprecation") int day = date.getDay();
		return getString(WEEKDAY_NAMES_RESIDS[day]);
	}

	synchronized void willNeed(DevotionKind kind, String date, boolean prioritize) {
		if (devotionDownloader == null) {
			devotionDownloader = new DevotionDownloader(this, this);
			devotionDownloader.start();
		}

		final DevotionArticle article = kind.getArticle(date);
		boolean added = devotionDownloader.add(article, prioritize);
		if (added) devotionDownloader.interruptWhenIdle();
	}
	
	static boolean prefetcherRunning = false;
	
	class Prefetcher extends Thread {
		private final DevotionKind prefetchKind;

		public Prefetcher(final DevotionKind kind) {
			prefetchKind = kind;
		}

		@Override public void run() {
			if (prefetcherRunning) {
				Log.d(TAG, "prefetcher is now running"); //$NON-NLS-1$
			}
			
			// diem dulu 6 detik
			SystemClock.sleep(6000);
			
			Date today = new Date();
			
			// hapus yang sudah lebih lama dari 6 bulan (180 hari)!
			int deleted = S.getDb().deleteDevotionsWithTouchTimeBefore(new Date(today.getTime() - 180 * 86400000L));
			if (deleted > 0) {
				Log.d(TAG, "old devotions deleted: " + deleted); //$NON-NLS-1$
			}
			
			prefetcherRunning = true;
			try {
				int DAYS = 31;
				if (prefetchKind == DevotionKind.RH) {
					DAYS = 3;
				}

				for (int i = 0; i < DAYS; i++) {
					String date = date_format.get().format(today);
					if (S.getDb().tryGetDevotion(prefetchKind.name, date) == null) {
						Log.d(TAG, "Prefetcher need to get " + date); //$NON-NLS-1$
						willNeed(prefetchKind, date, false);
						
						SystemClock.sleep(1000);
					} else {
						SystemClock.sleep(100); // biar ga berbeban aja
					}
					
					// maju ke besoknya
					today.setTime(today.getTime() + 3600*24*1000);
				}
			} finally {
				prefetcherRunning = false;
			}
		}
	}

	/**
	 * Settings that are still alive even when activities are destroyed.
	 * Ensure there is no references to any activity to prevent memory leak.
	 * 
	 * TODO this is not a good practice
	 */
	public static class Temporaries {
		public static DevotionKind devotion_kind = null;
		public static Date devotion_date = null;
		public static int devotion_scroll = 0;
	}
	
	public static DevotionDownloader devotionDownloader;

	@Override public void onDownloadStatus(final String s) {
		Message msg = Message.obtain(downloadStatusDisplayer);
		msg.obj = s;
		msg.sendToTarget();
	}
	
	@Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (requestCode == REQCODE_share) {
			if (resultCode == RESULT_OK) {
				ShareActivity.Result result = ShareActivity.obtainResult(data);
				if (result != null && result.chosenIntent != null) {
					Intent chosenIntent = result.chosenIntent;
					if (U.equals(chosenIntent.getComponent().getPackageName(), "com.facebook.katana")) { //$NON-NLS-1$
						switch (currentKind) {
							case SH:
								chosenIntent.putExtra(Intent.EXTRA_TEXT, "http://www.sabda.org/publikasi/e-sh/print/?edisi=" + date_format.get().format(currentDate)); // change text to url //$NON-NLS-1$ //$NON-NLS-2$
								break;
							case RH:
								chosenIntent.putExtra(Intent.EXTRA_TEXT, "http://www.sabda.org/publikasi/e-rh/print/?edisi=" + date_format.get().format(currentDate)); // change text to url //$NON-NLS-1$ //$NON-NLS-2$
								break;
							case ME_EN:
								chosenIntent.putExtra(Intent.EXTRA_TEXT, "http://www.ccel.org/ccel/spurgeon/morneve.d" + date_format.get().format(currentDate) + "am.html"); // change text to url //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
								break;
						}
					}
					startActivity(chosenIntent);
				}
			}
		}
	}
}
