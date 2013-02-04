package yuku.alkitab.base.widget;

import android.content.Context;
import android.os.Build;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.util.Log;
import android.util.SparseBooleanArray;
import android.view.KeyEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;

import yuku.afw.storage.Preferences;
import yuku.alkitab.R;
import yuku.alkitab.base.S;
import yuku.alkitab.base.U;
import yuku.alkitab.base.compat.Api8;
import yuku.alkitab.base.model.Book;
import yuku.alkitab.base.model.PericopeBlock;
import yuku.alkitab.base.model.SingleChapterVerses;
import yuku.alkitab.base.util.IntArrayList;
import yuku.alkitab.base.widget.VerseAdapter.AttributeListener;

public class VersesView extends ListView {
	public static final String TAG = VersesView.class.getSimpleName();
	
	public interface SelectedVersesListener {
		void onSomeVersesSelected(VersesView v);
		void onNoVersesSelected(VersesView v);
	}

	VerseAdapter adapter;
	private SelectedVersesListener listener;

	public VersesView(Context context) {
		super(context);
		init();
	}
	
	public VersesView(Context context, AttributeSet attrs) {
		super(context, attrs);
		init();
	}

	private void init() {
		setAdapter(adapter = new VerseAdapter.Factory().create(getContext()));
		setOnItemClickListener(itemClick);
		setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
	}
	
	@Override public VerseAdapter getAdapter() {
		return adapter;
	}

	public void setParallelListener(yuku.alkitab.base.widget.CallbackSpan.OnClickListener parallelListener) {
		adapter.setParallelListener(parallelListener);
	}

	public void setAttributeListener(AttributeListener attributeListener) {
		adapter.setAttributeListener(attributeListener);
	}
	
	public void loadAttributeMap() {
		adapter.loadAttributeMap();
	}
	
	public String getVerse(int verse_1) {
		return adapter.getVerse(verse_1);
	}

	public void scrollToShowVerse(int mainVerse_1) {
		int position = adapter.getPositionOfPericopeBeginningFromVerse(mainVerse_1);
		if (Build.VERSION.SDK_INT >= 8) {
			Api8.ListView_smoothScrollToPosition(this, position);
		} else {
			setSelectionFromTop(position, getVerticalFadingEdgeLength());
		}
	}

	
	/**
	 * @return 1-based verse
	 */
	public int getVerseBasedOnScroll() {
		return adapter.getVerseFromPosition(getPositionBasedOnScroll());
	}
	
	public int getPositionBasedOnScroll() {
		int pos = getFirstVisiblePosition();

		// check if the top one has been scrolled 
		View child = getChildAt(0); 
		if (child != null) {
			int top = child.getTop();
			if (top == 0) {
				return pos;
			}
			int bottom = child.getBottom();
			if (bottom > getVerticalFadingEdgeLength()) {
				return pos;
			} else {
				return pos+1;
			}
		}
		
		return pos;
	}

	public void setData(Book book, int chapter_1, SingleChapterVerses verses, int[] pericopeAris, PericopeBlock[] pericopeBlocks, int nblock) {
		adapter.setData(book, chapter_1, verses, pericopeAris, pericopeBlocks, nblock);
	}

	private OnItemClickListener itemClick = new OnItemClickListener() {
		@Override public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
			adapter.notifyDataSetChanged();
			hideOrShowContextMenuButton();
		}
	};

	public void uncheckAll() {
		SparseBooleanArray checkedPositions = getCheckedItemPositions();
		if (checkedPositions != null && checkedPositions.size() > 0) {
			for (int i = checkedPositions.size() - 1; i >= 0; i--) {
				if (checkedPositions.valueAt(i)) {
					setItemChecked(checkedPositions.keyAt(i), false);
				}
			}
		}
		if (listener != null) listener.onNoVersesSelected(this);
	}

	void hideOrShowContextMenuButton() {
		SparseBooleanArray checkedPositions = getCheckedItemPositions();
		boolean anyChecked = false;
		for (int i = 0; i < checkedPositions.size(); i++) if (checkedPositions.valueAt(i)) {
			anyChecked = true; 
			break;
		}
		
		if (anyChecked) {
			if (listener != null) listener.onSomeVersesSelected(this);
		} else {
			if (listener != null) listener.onNoVersesSelected(this);
		}
	}

	public IntArrayList getSelectedVerses_1() {
		// count how many are selected
		SparseBooleanArray positions = getCheckedItemPositions();
		if (positions == null) {
			return new IntArrayList(0);
		}
		
		IntArrayList res = new IntArrayList(positions.size());
		for (int i = 0, len = positions.size(); i < len; i++) {
			if (positions.valueAt(i)) {
				int position = positions.keyAt(i);
				int verse_1 = adapter.getVerseFromPosition(position);
				if (verse_1 >= 1) res.add(verse_1);
			}
		}
		return res;
	}
	
	@Override public void onRestoreInstanceState(Parcelable state) {
		super.onRestoreInstanceState(state);
		
		hideOrShowContextMenuButton();
	}
	
	public boolean press(int keyCode) {
		String volumeButtonsForNavigation = Preferences.getString(getContext().getString(R.string.pref_tombolVolumeBuatPindah_key), getContext().getString(R.string.pref_tombolVolumeBuatPindah_default));
		if (U.equals(volumeButtonsForNavigation, "pasal" /* chapter */)) { //$NON-NLS-1$
			if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) keyCode = KeyEvent.KEYCODE_DPAD_RIGHT;
			if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) keyCode = KeyEvent.KEYCODE_DPAD_LEFT;
		} else if (U.equals(volumeButtonsForNavigation, "ayat" /* verse */)) { //$NON-NLS-1$
			if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) keyCode = KeyEvent.KEYCODE_DPAD_DOWN;
			if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) keyCode = KeyEvent.KEYCODE_DPAD_UP;
		}

		if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
			int oldPos = getPositionBasedOnScroll();
			if (oldPos < adapter.getCount() - 1) {
				setSelectionFromTop(oldPos+1, getVerticalFadingEdgeLength());
			}
			return true;
		} else if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
			int oldPos = getPositionBasedOnScroll();
			if (oldPos >= 1) {
				int newPos = oldPos - 1;
				while (newPos > 0) { // cek disabled, kalo iya, mundurin lagi
					if (adapter.isEnabled(newPos)) break;
					newPos--;
				}
				setSelectionFromTop(newPos, getVerticalFadingEdgeLength());
			} else {
				setSelectionFromTop(0, getVerticalFadingEdgeLength());
			}
			return true;
		}
		return false;
	}

	public void setDataWithRetainSelectedVerses(boolean retainSelectedVerses, int chapter_1, int[] pericope_aris, PericopeBlock[] pericope_blocks, int nblock, SingleChapterVerses verses) {
		IntArrayList selectedVerses_1 = null;
		if (retainSelectedVerses) {
			selectedVerses_1 = getSelectedVerses_1();
		}
		
		//# fill adapter with new data. make sure all checked states are reset
		uncheckAll();
		setData(S.activeBook, chapter_1, verses, pericope_aris, pericope_blocks, nblock);
		loadAttributeMap();
		
		if (selectedVerses_1 != null) {
			for (int i = 0, len = selectedVerses_1.size(); i < len; i++) {
				int pos = adapter.getPositionAbaikanPerikopDariAyat(selectedVerses_1.get(i));
				if (pos != -1) {
					setItemChecked(pos, true);
				}
			}
		}
	}

	public void scrollToVerse(int verse_1) {
		final int position = adapter.getPositionOfPericopeBeginningFromVerse(verse_1);
		
		if (position == -1) {
			Log.w(TAG, "could not find verse=" + verse_1 + ", weird!"); //$NON-NLS-1$ //$NON-NLS-2$
		} else {
			// TODO use smooth scroll?
			setSelectionFromTop(position, getVerticalFadingEdgeLength());
		}
	}

	public void setSelectedVersesListener(SelectedVersesListener listener) {
		this.listener = listener;
	}
}
