package yuku.alkitab;


import android.content.*;
import android.content.res.*;
import android.os.*;
import android.preference.*;
import android.util.*;
import android.view.*;
import android.widget.*;
import android.widget.SeekBar.OnSeekBarChangeListener;

public class UkuranHuruf2Preference extends DialogPreference implements OnSeekBarChangeListener {
	private static final int OFSET_minukuran = 2;
	
    /**
     * The edit text shown in the dialog.
     */
    private SeekBar seekbar;
    private float ukuran;
	private final Context context;
	private TextView lContoh;
	private TextView lUkuranHuruf;
    
    public UkuranHuruf2Preference(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
		this.context = context;
    }

    public UkuranHuruf2Preference(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public UkuranHuruf2Preference(Context context) {
        this(context, null);
    }
    
    @Override
    protected View onCreateDialogView() {
    	View res = LayoutInflater.from(context).inflate(R.layout.ukuranhuruf2_pref_dialog, null);
    	
    	seekbar = (SeekBar) res.findViewById(R.id.seekbar);
    	lContoh = (TextView) res.findViewById(R.id.lContoh);
    	lUkuranHuruf = (TextView) res.findViewById(R.id.lUkuranHuruf);
    	
    	seekbar.setOnSeekBarChangeListener(this);
    	
    	return res;
    }
    
    /**
     * Saves the text to the {@link SharedPreferences}.
     * 
     * @param text The text to save
     */
    public void setUkuran(float ukuran) {
        final boolean wasBlocking = shouldDisableDependents();
        
        this.ukuran = ukuran;
        
        persistFloat(ukuran);
        
        final boolean isBlocking = shouldDisableDependents(); 
        if (isBlocking != wasBlocking) {
            notifyDependencyChange(isBlocking);
        }
    }
    
    @Override
    protected void onBindDialogView(View view) {
        super.onBindDialogView(view);

        seekbar.setProgress((int) ((ukuran - OFSET_minukuran) * 2.f));
    }

    @Override
    protected void onDialogClosed(boolean positiveResult) {
        super.onDialogClosed(positiveResult);
        
        if (positiveResult) {
            float value = (float) seekbar.getProgress() * 0.5f + OFSET_minukuran;
            if (callChangeListener(value)) {
                setUkuran(value);
            }
        }
    }

    @Override
    protected Object onGetDefaultValue(TypedArray a, int index) {
        return a.getFloat(index, 17.f);
    }

    @Override
    protected void onSetInitialValue(boolean restoreValue, Object defaultValue) {
        setUkuran(restoreValue ? getPersistedFloat(ukuran): (Float) defaultValue);
    }

    @Override
    protected Parcelable onSaveInstanceState() {
        final Parcelable superState = super.onSaveInstanceState();
        if (isPersistent()) {
            // No need to save instance state since it's persistent
            return superState;
        }
        
        final SavedState myState = new SavedState(superState);
        myState.ukuran = ukuran;
        return myState;
    }

    @Override
    protected void onRestoreInstanceState(Parcelable state) {
        if (state == null || !state.getClass().equals(SavedState.class)) {
            // Didn't save state for us in onSaveInstanceState
            super.onRestoreInstanceState(state);
            return;
        }
         
        SavedState myState = (SavedState) state;
        super.onRestoreInstanceState(myState.getSuperState());
        setUkuran(myState.ukuran);
    }

	@Override
	public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
		float betulan = OFSET_minukuran + (float) progress * 0.5f;
		
		lUkuranHuruf.setText(String.valueOf(betulan));
		lContoh.setTextSize(TypedValue.COMPLEX_UNIT_DIP, betulan);
	}

	@Override
	public void onStartTrackingTouch(SeekBar seekBar) {
		// diam
	}

	@Override
	public void onStopTrackingTouch(SeekBar seekBar) {
		// diam
	}
    

    private static class SavedState extends BaseSavedState {
        float ukuran;
        
        public SavedState(Parcel source) {
            super(source);
            ukuran = source.readFloat();
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            super.writeToParcel(dest, flags);
            dest.writeFloat(ukuran);
        }

        public SavedState(Parcelable superState) {
            super(superState);
        }

        public static final Parcelable.Creator<SavedState> CREATOR = new Parcelable.Creator<SavedState>() {
            public SavedState createFromParcel(Parcel in) {
                return new SavedState(in);
            }

            public SavedState[] newArray(int size) {
                return new SavedState[size];
            }
        };
    }
}