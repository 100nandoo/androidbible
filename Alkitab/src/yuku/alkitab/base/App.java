package yuku.alkitab.base;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Build;
import android.preference.PreferenceManager;
import android.util.Log;
import yuku.afw.storage.Preferences;
import yuku.alkitab.R;
import yuku.alkitabfeedback.FeedbackSender;
import yuku.kirimfidbek.PengirimFidbek;

import java.io.File;
import java.util.Locale;

public class App extends yuku.afw.App {
	public static final String TAG = App.class.getSimpleName();

	private static boolean initted = false;
	public static PengirimFidbek pengirimFidbek;

	@Override public void onCreate() {
		super.onCreate();
		
		Log.d(TAG, "@@onCreate");

		staticInit();
	}

	public synchronized static void staticInit() {
		if (initted) return;
		initted = true;
		
		pengirimFidbek = siapinPengirimFidbek(context);
		pengirimFidbek.cobaKirim();
		
		// transfer installationId from pengirimfidbek to feedbacksender 
		FeedbackSender fs = FeedbackSender.getInstance(context);
		fs.setOverrideInstallationId(pengirimFidbek.getUniqueId());
		fs.trySend();

		PreferenceManager.setDefaultValues(context, R.xml.settings, false);
		PreferenceManager.setDefaultValues(context, R.xml.secret_settings, false);

		updateConfigurationWithPreferencesLocale();

		// all activities need at least the activeVersion from S, so initialize it here.
		S.prepareInternalVersion();
		
		// also pre-calculate calculated preferences value here
		S.calculateAppliedValuesBasedOnPreferences();

		// http://android-developers.blogspot.com/2011/09/androids-http-clients.html
		{
			// HTTP connection reuse which was buggy pre-froyo
			if (Build.VERSION.SDK_INT < Build.VERSION_CODES.FROYO) {
				System.setProperty("http.keepAlive", "false"); //$NON-NLS-1$ //$NON-NLS-2$
			}

			// Use reflection to enable HTTP response caching on devices that support it. This sample code will turn on the response cache on Ice Cream Sandwich without affecting
			// earlier releases:
			try {
				long httpCacheSize = 10 * 1024 * 1024; // 10 MiB
				File httpCacheDir = new File(context.getCacheDir(), "http"); //$NON-NLS-1$
				Class.forName("android.net.http.HttpResponseCache") //$NON-NLS-1$
				.getMethod("install", File.class, long.class) //$NON-NLS-1$
				.invoke(null, httpCacheDir, httpCacheSize);
			} catch (Exception httpResponseCacheNotAvailable) {
			}
		}
	}

	private static Locale getLocaleFromPreferences() {
		String lang = Preferences.getString(context.getString(R.string.pref_language_key), context.getString(R.string.pref_bahasa_default));
		if (lang == null || "DEFAULT".equals(lang)) { //$NON-NLS-1$
			lang = Locale.getDefault().getLanguage();
		}

		if ("zh-CN".equals(lang)) {
			return Locale.SIMPLIFIED_CHINESE;
		} else if ("zh-TW".equals(lang)) {
			return Locale.TRADITIONAL_CHINESE;
		} else {
			return new Locale(lang);
		}
	}

	@Override public void onConfigurationChanged(Configuration newConfig) {
		super.onConfigurationChanged(newConfig);

		Log.d(TAG, "@@onConfigurationChanged: config changed to: " + newConfig); //$NON-NLS-1$
		updateConfigurationWithPreferencesLocale();
	}

	public static void updateConfigurationWithPreferencesLocale() {
		final Configuration config = context.getResources().getConfiguration();
		final Locale locale = getLocaleFromPreferences();
		if (!U.equals(config.locale.getLanguage(), locale.getLanguage()) || !U.equals(config.locale.getCountry(), locale.getCountry())) {
			Log.d(TAG, "@@updateConfigurationWithPreferencesLocale: locale will be updated to: " + locale); //$NON-NLS-1$

			config.locale = locale;
			context.getResources().updateConfiguration(config, null);
		}
	}

	private static PengirimFidbek siapinPengirimFidbek(final Context context) {
		PengirimFidbek res = new PengirimFidbek(context, getPreferencesInstan());
		res.activateDefaultUncaughtExceptionHandler();
		res.setOnSuccessListener(new PengirimFidbek.OnSuccessListener() {
			@Override public void onSuccess(final byte[] response) {
				Log.e(TAG, "KirimFidbek respon: " + new String(response, 0, response.length)); //$NON-NLS-1$
			}
		});
		return res;
	}

	public static SharedPreferences getPreferencesInstan() {
		return context.getSharedPreferences(context.getPackageName(), 0);
	}
}
