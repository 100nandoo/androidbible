package yuku.alkitab.reminder.widget;

import android.content.Context;
import android.preference.Preference;
import android.text.format.DateFormat;
import android.util.AttributeSet;
import android.view.View;
import android.widget.TimePicker;

import java.util.Locale;

public class ReminderTimePreference extends Preference{
	public ReminderTimePreference(final Context context, final AttributeSet attrs, final int defStyle) {
		super(context, attrs, defStyle);
	}

	public ReminderTimePreference(final Context context, final AttributeSet attrs) {
		super(context, attrs);
	}

	@Override protected void onBindView(View view) {
		super.onBindView(view);
	}

	@Override protected void onClick() {
		super.onClick();

		String currentValue = getPersistedString(null);
		final int hour = currentValue == null? 12: Integer.parseInt(currentValue.substring(0, 2));
		final int minute = currentValue == null? 0: Integer.parseInt(currentValue.substring(2, 4));

		HackedTimePickerDialog dialog = new HackedTimePickerDialog(getContext(), getTitle(), "Set", "Turn off", new HackedTimePickerDialog.HackedTimePickerListener() {
			@Override public void onTimeSet(TimePicker view, int hourOfDay, int minute) {
				persistString(String.format(Locale.US, "%02d%02d", hourOfDay, minute));
				notifyChanged();
			}

			@Override public void onTimeOff(TimePicker view) {
				persistString(null);
				notifyChanged();
			}
		}, hour, minute, DateFormat.is24HourFormat(getContext()));
		dialog.show();
	}

	@Override public boolean shouldDisableDependents() {
		return getPersistedString(null) == null;
	}
}