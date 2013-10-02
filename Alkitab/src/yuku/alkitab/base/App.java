package yuku.alkitab.base;

import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.preference.PreferenceManager;
import android.util.Log;
import yuku.afw.storage.Preferences;
import yuku.alkitab.R;
import yuku.alkitabfeedback.FeedbackSender;
import yuku.kirimfidbek.PengirimFidbek;

import java.util.Locale;

public class App extends yuku.afw.App {
	public static final String TAG = App.class.getSimpleName();

	private static boolean initted = false;

	@Override public void onCreate() {
		super.onCreate();
		
		Log.d(TAG, "@@onCreate");

		staticInit();
	}

	public synchronized static void staticInit() {
		if (initted) return;
		initted = true;

		final PengirimFidbek oldFeedbackSender = new PengirimFidbek(context, getInstantPreferences());
		oldFeedbackSender.activateDefaultUncaughtExceptionHandler();
		oldFeedbackSender.setOnSuccessListener(new PengirimFidbek.OnSuccessListener() {
			@Override
			public void onSuccess(final byte[] response) {
				Log.e(TAG, "KirimFidbek respon: " + new String(response, 0, response.length)); //$NON-NLS-1$
			}
		});
		oldFeedbackSender.cobaKirim();
		
		// transfer installationId from pengirimfidbek to feedbacksender 
		FeedbackSender fs = FeedbackSender.getInstance(context);
		fs.setOverrideInstallationId(oldFeedbackSender.getUniqueId());
		fs.trySend();

		PreferenceManager.setDefaultValues(context, R.xml.settings, false);
		PreferenceManager.setDefaultValues(context, R.xml.secret_settings, false);

		updateConfigurationWithPreferencesLocale();

		// all activities need at least the activeVersion from S, so initialize it here.
		S.prepareInternalVersion();
		
		// also pre-calculate calculated preferences value here
		S.calculateAppliedValuesBasedOnPreferences();
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

	public static SharedPreferences getInstantPreferences() {
		return context.getSharedPreferences(context.getPackageName(), 0);
	}
}
