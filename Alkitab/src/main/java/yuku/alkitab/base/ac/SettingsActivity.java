package yuku.alkitab.base.ac;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.preference.Preference;
import yuku.alkitab.base.App;
import yuku.alkitab.base.ac.base.BasePreferenceActivity;
import yuku.alkitab.debug.R;

public class SettingsActivity extends BasePreferenceActivity {
	public static Intent createIntent() {
		return new Intent(App.context, SettingsActivity.class);
	}

	final Handler handler = new Handler();

	@SuppressWarnings("deprecation") @Override protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		addPreferencesFromResource(R.xml.settings);
		setTitle(R.string.pengaturan_alkitab);

		findPreference(getString(R.string.pref_language_key)).setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
			@Override
			public boolean onPreferenceChange(final Preference preference, final Object newValue) {
				// do this after this method returns true
				handler.post(new Runnable() {
					@Override
					public void run() {
						App.updateConfigurationWithPreferencesLocale();
					}
				});
				return true;
			}
		});
	}
}
