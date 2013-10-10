package yuku.alkitab.base.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.UnderlineSpan;
import android.util.Log;
import android.widget.RemoteViews;
import yuku.afw.storage.Preferences;
import yuku.alkitab.R;
import yuku.alkitab.base.S;
import yuku.alkitab.base.U;
import yuku.alkitab.base.ac.VersionsActivity;
import yuku.alkitab.base.config.AppConfig;
import yuku.alkitab.base.model.Version;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Random;

public class DailyVerseAppWidget extends AppWidgetProvider {
	public static final String TAG = DailyVerseAppWidget.class.getSimpleName();

	@Override
	public void onUpdate(final Context context, final AppWidgetManager appWidgetManager, final int[] appWidgetIds) {
		update(context, appWidgetManager, appWidgetIds, 0);
	}

	private void update(final Context context, final AppWidgetManager appWidgetManager, final int[] appWidgetIds, int buttonRequest) {

		final int N = appWidgetIds.length;
		for (int i = 0; i < N; i++) {
			int appWidgetId = appWidgetIds[i];
			String version = Preferences.getString("app_widget_" + appWidgetId + "_version", S.activeVersionId);
			buildUpdate(context, appWidgetManager, appWidgetId, version, buttonRequest);
		}
	}

	@Override
	public void onReceive(final Context context, final Intent intent) {

		//Bypass onReceive since the super does not pass the intent to onUpdate
		Bundle bundle = intent.getExtras();
		String bundleString = bundle.getString("app_widget_action");
		if (bundleString!=null && bundleString.equals("update_widget")) {
			intent.setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
			int[] arrayInt = bundle.getIntArray(AppWidgetManager.EXTRA_APPWIDGET_IDS);
			if (arrayInt != null) {
				intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, arrayInt);
				int buttonRequest = intent.getIntExtra("app_widget_button", 0);
				update(context, AppWidgetManager.getInstance(context), arrayInt, buttonRequest);
			}
		} else {
			super.onReceive(context, intent);
		}
	}

	@Override
	public void onDeleted(final Context context, final int[] appWidgetIds) {
		super.onDeleted(context, appWidgetIds);
		for (int appWidgetId : appWidgetIds) {
			String key = "app_widget_" + appWidgetId;
			Preferences.remove(key);
			Preferences.remove(key + "_version");
		}

	}

	public static void buildUpdate(Context context, AppWidgetManager appWidgetManager, int appWidgetId, String version, int buttonRequest) {

		//Get the version and the verses
		Version bibleVersion;
		if (version == null) {
			bibleVersion = S.activeVersion;
		} else {
			bibleVersion = loadLastVersion(version);
		}
		Integer[] aris = getVerse(appWidgetId, buttonRequest);
		SpannableStringBuilder verseText = getText(bibleVersion, aris);

		//Make new view
		RemoteViews remoteViews = new RemoteViews(context.getPackageName(), R.layout.app_widget);
		SimpleDateFormat format = new SimpleDateFormat("hh:mm:ss dd-MM-yyyy");
		remoteViews.setTextViewText(R.id.tAppWidgetVerse, verseText + " gen at " + format.format(new Date())); //TODO delete last strings. for debugging only

		//------Set Intent to update widget
		int[] ids = {appWidgetId};
		//--Prev button
		{
			Intent intentPrev = new Intent(context, DailyVerseAppWidget.class);
			Bundle bundle = new Bundle();
			bundle.putIntArray(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids);
			bundle.putString("app_widget_action", "update_widget");
	        bundle.putInt("app_widget_button", 1);
			intentPrev.putExtras(bundle);

			PendingIntent pendingIntentPrev = PendingIntent.getBroadcast(context, appWidgetId * 10 + 1, intentPrev, PendingIntent.FLAG_CANCEL_CURRENT);
			remoteViews.setOnClickPendingIntent(R.id.bPrev, pendingIntentPrev);
		}

		//--Next button
		{
			Intent intentNext = new Intent(context, DailyVerseAppWidget.class);
			Bundle bundle = new Bundle();
			bundle.putIntArray(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids);
			bundle.putString("app_widget_action", "update_widget");
			bundle.putInt("app_widget_button", 2);
			intentNext.putExtras(bundle);

			PendingIntent pendingIntentNext = PendingIntent.getBroadcast(context, appWidgetId * 10 + 2, intentNext, PendingIntent.FLAG_CANCEL_CURRENT);
			remoteViews.setOnClickPendingIntent(R.id.bNext, pendingIntentNext);
		}

		//------End set Intent to update widget

		//-----Set Intent to open bible
		Intent intentOpenBible = new Intent("yuku.alkitab.action.VIEW");
		intentOpenBible.putExtra("ari", aris[0]);
		PendingIntent pendingIntentOpenBible = PendingIntent.getActivity(context, appWidgetId, intentOpenBible, PendingIntent.FLAG_CANCEL_CURRENT);

		remoteViews.setOnClickPendingIntent(R.id.tAppWidgetVerse, pendingIntentOpenBible);
		//-----End of Intent to open bible

		appWidgetManager.updateAppWidget(appWidgetId, remoteViews);
	}

	private static SpannableStringBuilder getText(Version version, Integer[] aris) {
		SpannableStringBuilder sb = new SpannableStringBuilder();
		for (int ari : aris) {
			int length = sb.length();
			if (length != 0) {
				sb.append("\n");
			}
			String reference = version.reference(ari);
			sb.append(reference + " ");
			sb.setSpan(new UnderlineSpan(), length, length + reference.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
			String verseText = U.removeSpecialCodes(version.loadVerseText(ari));
			sb.append(verseText);
		}

		return sb;
	}

	private static Integer[] getVerse(int appWidgetId, int request) {
		List<Integer[]> aris = new ArrayList<Integer[]>();

		aris.add(new Integer[] {2753296});
		aris.add(new Integer[] {257});
		aris.add(new Integer[] {2884375, 257});
		aris.add(new Integer[] {3146258,3146259});
		aris.add(new Integer[] {2952461, 2952461});
		aris.add(new Integer[] {2557449});
		aris.add(new Integer[] {2557473});
		aris.add(new Integer[] {3211787});
		aris.add(new Integer[] {1185537,1185538});
		aris.add(new Integer[] {1245702});
		aris.add(new Integer[] {527384});

		String key = "app_widget_" + appWidgetId;

		int add = 0;
		if (request == 1) {
			add = -1;
		} else if (request == 2) {
			add = 1;
		}
		int numOfClick = Preferences.getInt(key, 0) + add;
		Preferences.setInt(key, numOfClick);

		Date date = new Date();
		Calendar calendar = Calendar.getInstance();
		calendar.setTime(date);
		long year = calendar.get(Calendar.YEAR);
		long day = calendar.get(Calendar.DAY_OF_YEAR);
		long randomDay = (year - 1900) * 1000 + day;

		long randomNumberSeed = randomDay * 10000000 + appWidgetId * 100000 + numOfClick * 1000;
		Random r = new Random(randomNumberSeed);
		int random = (int) (r.nextDouble() * aris.size());
		return aris.get(random);
	}

	private static Version loadLastVersion(String lastVersion) {
		AppConfig c = AppConfig.get();

		if (lastVersion == null || VersionsActivity.MVersionInternal.getVersionInternalId().equals(lastVersion)) {
			return null;
		}

		// coba preset dulu!
		for (VersionsActivity.MVersionPreset preset: c.presets) { // 2. preset
			if (preset.getVersionId().equals(lastVersion)) {
				if (preset.hasDataFile()) {
					return preset.getVersion();
				} else {
					return null;
				}
			}
		}

		// masih belum cocok, mari kita cari di daftar yes
		List<VersionsActivity.MVersionYes> yeses = S.getDb().listAllVersions();
		for (VersionsActivity.MVersionYes yes: yeses) {
			if (yes.getVersionId().equals(lastVersion)) {
				if (yes.hasDataFile()) {
					return yes.getVersion();
				} else {
					return null;
				}
			}
		}
		return null;
	}

}
