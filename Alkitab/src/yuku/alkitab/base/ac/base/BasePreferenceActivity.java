package yuku.alkitab.base.ac.base;

import android.os.Bundle;

import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.app.SherlockPreferenceActivity;
import com.actionbarsherlock.view.MenuItem;
import com.google.analytics.tracking.android.EasyTracker;

public abstract class BasePreferenceActivity extends SherlockPreferenceActivity {
	/**
	 * By default we put the up button on each activity.
	 * And the behavior is to just finish() the current activity.
	 * If we don't want this, e.g. on the root activity, change
	 * super.onCreate(Bundle) to super.onCreate(Bundle, boolean)
	 * on subclasses
	 */
	@Override protected void onCreate(Bundle savedInstanceState) {
		onCreate(savedInstanceState, true);
	}
	
	protected void onCreate(Bundle savedInstanceState, boolean withUpButton) {
		super.onCreate(savedInstanceState);
		
		if (withUpButton) {
			ActionBar actionBar = getSupportActionBar();
			if (actionBar != null) {
				actionBar.setDisplayHomeAsUpEnabled(true);
			}
		}
	}
	
	@Override public boolean onOptionsItemSelected(MenuItem item) {
		if (item.getItemId() == android.R.id.home) {
			finish();
            return true;
		}
		
		return super.onOptionsItemSelected(item);
	}
	
	@Override protected void onStart() {
		super.onStart();
	    EasyTracker.getInstance().activityStart(this);
	}
	
	@Override protected void onStop() {
		super.onStop();
		EasyTracker.getInstance().activityStop(this);
	}
}
