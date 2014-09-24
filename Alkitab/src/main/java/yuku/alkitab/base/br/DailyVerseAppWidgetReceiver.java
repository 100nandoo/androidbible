package yuku.alkitab.base.br;

import android.annotation.SuppressLint;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.RelativeSizeSpan;
import android.text.style.SuperscriptSpan;
import android.util.Log;
import android.widget.RemoteViews;
import yuku.afw.App;
import yuku.afw.storage.Preferences;
import yuku.alkitab.base.IsiActivity;
import yuku.alkitab.base.S;
import yuku.alkitab.base.U;
import yuku.alkitab.base.model.MVersionDb;
import yuku.alkitab.base.model.MVersionInternal;
import yuku.alkitab.base.model.VersionImpl;
import yuku.alkitab.base.sv.DailyVerseAppWidgetService;
import yuku.alkitab.debug.R;
import yuku.alkitab.model.Version;
import yuku.alkitab.util.Ari;
import yuku.alkitab.util.IntArrayList;
import yuku.bintex.BintexReader;

import java.io.IOException;
import java.io.InputStream;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Random;

public class DailyVerseAppWidgetReceiver extends AppWidgetProvider {
	public static final String TAG = DailyVerseAppWidgetReceiver.class.getSimpleName();

	private static IntArrayList dailyVerses;

	@Override
	public void onUpdate(final Context context, final AppWidgetManager appWidgetManager, final int[] appWidgetIds) {
		for (int appWidgetId : appWidgetIds) {
			buildUpdate(context, appWidgetManager, appWidgetId);
		}
		ComponentName componentName = new ComponentName(context, DailyVerseAppWidgetReceiver.class);
		int[] allWidgetIds = appWidgetManager.getAppWidgetIds(componentName);
		setAlarm(context, allWidgetIds);
	}

	@Override
	public void onDeleted(final Context context, final int[] appWidgetIds) {
		super.onDeleted(context, appWidgetIds);
		for (int appWidgetId : appWidgetIds) {
			String keyPrefix = "app_widget_" + appWidgetId;
			Preferences.hold();
			try {
				Preferences.remove(keyPrefix + "_click");
				Preferences.remove(keyPrefix + "_version");
				Preferences.remove(keyPrefix + "_option_transparent_background");
				Preferences.remove(keyPrefix + "_option_dark_text");
				Preferences.remove(keyPrefix + "_option_text_size");
			} finally {
				Preferences.unhold();
			}
		}
	}

	@SuppressLint("NewApi")
	public static void buildUpdate(Context context, AppWidgetManager appWidgetManager, int appWidgetId) {
		RemoteViews remoteViews = getRemoteViewsApi14(context, appWidgetId);

		remoteViews.setTextViewText(R.id.tReference, getReference(getVersion(appWidgetId), getVerse(appWidgetId)));

		//------Set Intent to update widget
		int[] ids = {appWidgetId};
		//--App logo button
		{
			Intent intent = new Intent(App.context, IsiActivity.class);

			PendingIntent pi = PendingIntent.getActivity(context, 1, intent, PendingIntent.FLAG_CANCEL_CURRENT);
			remoteViews.setOnClickPendingIntent(R.id.imgLogo, pi);
		}

		//--Prev button
		{
			Intent intentPrev = new Intent(context, ClickReceiver.class);
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
			Intent intentNext = new Intent(context, ClickReceiver.class);
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

		PendingIntent pendingIntentOpenBible = PendingIntent.getActivity(context, appWidgetId, intentOpenBible, PendingIntent.FLAG_CANCEL_CURRENT);
		remoteViews.setPendingIntentTemplate(R.id.lsVerse, pendingIntentOpenBible);

		//-----End of Intent to open bible
		appWidgetManager.updateAppWidget(appWidgetId, remoteViews);
	}

	private static RemoteViews getRemoteViewsApi14(final Context context, final int appWidgetId) {
		Intent svcIntent = new Intent(context, DailyVerseAppWidgetService.class);
		svcIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
		svcIntent.putExtra("random", new Random().nextInt());
		svcIntent.setData(Uri.parse(svcIntent.toUri(Intent.URI_INTENT_SCHEME)));
		final boolean optionTransparentBackground = getOptionTransparentBackground(appWidgetId);
		final boolean optionDarkText = getOptionDarkText(appWidgetId);
		final float optionTextSize = getOptionTextSize(appWidgetId);
		RemoteViews remoteViews = new RemoteViews(context.getPackageName(), R.layout.daily_verse_app_widget);
		if (optionTransparentBackground) {
			remoteViews.setInt(R.id.root, "setBackgroundResource", android.R.color.transparent);
		}
		if (optionDarkText) {
			remoteViews.setTextColor(R.id.tReference, 0xff000000);
			remoteViews.setImageViewResource(R.id.bPrev, R.drawable.ic_nav_left_dark);
			remoteViews.setImageViewResource(R.id.bNext, R.drawable.ic_nav_right_dark);
		}
		remoteViews.setFloat(R.id.tReference, "setTextSize", optionTextSize);

		remoteViews.setRemoteAdapter(R.id.lsVerse, svcIntent);
		return remoteViews;
	}

	public static boolean getOptionTransparentBackground(final int appWidgetId) {
		return Preferences.getBoolean("app_widget_" + appWidgetId + "_option_transparent_background", false);
	}

	public static boolean getOptionDarkText(final int appWidgetId) {
		return Preferences.getBoolean("app_widget_" + appWidgetId + "_option_dark_text", false);
	}

	public static float getOptionTextSize(final int appWidgetId) {
		return Preferences.getFloat("app_widget_" + appWidgetId + "_option_text_size", 14.f);
	}

	public static class ClickReceiver extends BroadcastReceiver {
		public static final String TAG = ClickReceiver.class.getSimpleName();
		@Override
		public void onReceive(final Context context, final Intent intent) {
			Bundle bundle = intent.getExtras();
			if (bundle != null) {
				String appWidgetAction = bundle.getString("app_widget_action");
				if (appWidgetAction != null && appWidgetAction.equals("update_widget")) {
					int[] appWidgetIds = bundle.getIntArray(AppWidgetManager.EXTRA_APPWIDGET_IDS);
					if (appWidgetIds != null) {
						int buttonRequest = intent.getIntExtra("app_widget_button", 0);
						int add = 0;
						if (buttonRequest == 1) {
							add = -1;
						} else if (buttonRequest == 2) {
							add = 1;
						}

						for (int id : appWidgetIds) {
							String key = "app_widget_" + id + "_click";
							int numOfClick = Preferences.getInt(key, 0) + add;
							Preferences.setInt(key, numOfClick);
						}
					}
					Intent i = new Intent(context, DailyVerseAppWidgetReceiver.class);
					i.setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
					i.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, appWidgetIds);
					context.sendBroadcast(i);
				}
			}
		}
	}

	public static void setAlarm(Context context, int[] ids) {
		Calendar calendar = Calendar.getInstance();
		calendar.set(Calendar.HOUR_OF_DAY, 0);
		calendar.set(Calendar.MINUTE, 0);
		calendar.set(Calendar.SECOND, 0);
		calendar.add(Calendar.DAY_OF_YEAR, 1);

		Intent intent = new Intent(context, DailyVerseAppWidgetReceiver.class);
		intent.setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
		intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids);

		PendingIntent pi = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_CANCEL_CURRENT);

		AlarmManager mgr = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
		mgr.setRepeating(AlarmManager.RTC, calendar.getTimeInMillis(), AlarmManager.INTERVAL_DAY, pi);
	}

	public static Version getVersion(int appWidgetId) {
		final String versionId = Preferences.getString("app_widget_" + appWidgetId + "_version", S.activeVersionId);
		if (versionId == null) {
			return S.activeVersion;
		}

		tryOpenVersion:
		{
			if (MVersionInternal.getVersionInternalId().equals(versionId)) {
				return VersionImpl.getInternalVersion();
			}

			// try database versions
			for (final MVersionDb mvDb : S.getDb().listAllVersions()) {
				if (mvDb.getVersionId().equals(versionId)) {
					if (mvDb.hasDataFile()) {
						return mvDb.getVersion();
					} else {
						break tryOpenVersion;
					}
				}
			}
		}

		Log.w(TAG, "Version selected for widget id " + appWidgetId + ": " + versionId + " is no longer available. Reverting to internal version.");
		return VersionImpl.getInternalVersion();
	}

	public static int[] getVerse(int appWidgetId) {
		final IntArrayList dailyVerses = listAllDailyVerses();
		int size = dailyVerses.size();

		String key = "app_widget_" + appWidgetId + "_click";
		int numOfClick = Preferences.getInt(key, 0);

		final Calendar calendar = GregorianCalendar.getInstance();
		long year = calendar.get(Calendar.YEAR);
		long day = calendar.get(Calendar.DAY_OF_YEAR);
		long randomDay = ((year - 1900) << 9) | day;
		long randomNumberSeed = (appWidgetId << 20) | (randomDay + numOfClick);
		Random r = new Random(randomNumberSeed);
		int random = r.nextInt(size);

		final int verseCount = dailyVerses.get(random) & 0xff;
		final int[] verses = new int[verseCount];
		verses[0] = dailyVerses.get(random) >>> 8;
		for (int i = 1; i < verseCount; i++) {
			verses[i] = verses[i - 1] + 1;
		}
		return verses;
	}

	private static IntArrayList listAllDailyVerses() {
		if (dailyVerses == null) {
			dailyVerses = new IntArrayList();
			try {
				InputStream is = App.context.getResources().openRawResource(R.raw.daily_verses_bt);
				BintexReader br = new BintexReader(is);
				while (true) {
					int ari = br.readInt();
					if (ari == -1) {
						break;
					}
					final int verseCount = br.readUint8();
					dailyVerses.add(ari << 8 | verseCount);
				}
				br.close();
			} catch (IOException e) {
				Log.w(TAG, "Error reading daily verses", e);
			}
		}
		return dailyVerses;
	}

	public static SpannableStringBuilder getText(Version version, int ari, boolean showVerseNumber) {
		SpannableStringBuilder sb = new SpannableStringBuilder();
		String verseText = U.removeSpecialCodes(version.loadVerseText(ari));
		if (showVerseNumber) {
			String verseNumber = "" + Ari.toVerse(ari);
			sb.append(verseNumber + " " + verseText);
			sb.setSpan(new RelativeSizeSpan(0.7f), 0, verseNumber.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
			sb.setSpan(new SuperscriptSpan(), 0, verseNumber.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
		} else {
			sb.append(verseText);
		}
		return sb;
	}

	public static SpannableStringBuilder getReference(Version version, int[] ari) {
		SpannableStringBuilder sb = new SpannableStringBuilder();
		String reference = version.reference(ari[0]);
		sb.append(reference);
		if (ari.length > 1) {
			int lastVerse = Ari.toVerse(ari[0]) + ari.length - 1;
			sb.append("-" + lastVerse);
		}
		return sb;
	}
}