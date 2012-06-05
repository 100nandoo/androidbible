package yuku.alkitab.base.ac;

import android.os.Bundle;
import android.preference.PreferenceActivity;
import android.view.MenuItem;

import yuku.alkitab.R;
import yuku.alkitab.base.ac.base.BaseActivity;

public class PengaturanActivity extends PreferenceActivity {
	@SuppressWarnings("deprecation") @Override protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		addPreferencesFromResource(R.xml.pengaturan);
		setTitle(R.string.pengaturan_alkitab);
	}
	
	@Override public boolean onOptionsItemSelected(MenuItem item) {
		if (item.getItemId() == android.R.id.home) {
			BaseActivity.backToRootActivity(this);
			return true;
		}
		
		return super.onOptionsItemSelected(item);
	}
}
