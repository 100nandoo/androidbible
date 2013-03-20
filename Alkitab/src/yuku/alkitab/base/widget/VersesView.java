package yuku.alkitab.base.widget;

import android.content.Context;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.util.Log;
import android.util.SparseBooleanArray;
import android.view.KeyEvent;
import android.view.View;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.ListView;

import yuku.afw.storage.Preferences;
import yuku.alkitab.R;
import yuku.alkitab.base.U;
import yuku.alkitab.base.compat.Api8;
import yuku.alkitab.base.model.Book;
import yuku.alkitab.base.model.PericopeBlock;
import yuku.alkitab.base.model.SingleChapterVerses;
import yuku.alkitab.base.util.IntArrayList;

public class VersesView extends ListView implements AbsListView.OnScrollListener {
	public enum VerseSelectionMode {
		none,
		multiple,
		singleClick,
	}

	public static final String TAG = VersesView.class.getSimpleName();
	
	public interface SelectedVersesListener {
		void onSomeVersesSelected(VersesView v);
		void onNoVersesSelected(VersesView v);
		void onVerseSingleClick(VersesView v, int verse_1);
	}

	public interface AttributeListener {
		void onAttributeClick(Book book, int chapter_1, int verse_1, int kind);
	}

	public interface XrefListener {
		void onXrefClick(int ari, int which);
	}
	
	public interface OnVerseScrollListener {
		void onVerseScroll(VersesView v, boolean isPericope, int verse_1, float prop);
	}

	private VerseAdapter adapter;
	private SelectedVersesListener listener;
	private VerseSelectionMode verseSelectionMode;
	private Drawable originalSelector;
	private OnVerseScrollListener onVerseScrollListener;
	private AbsListView.OnScrollListener userOnScrollListener;
	private int scrollState = 0;
	
	public VersesView(Context context) {
		super(context);
		init();
	}
	
	public VersesView(Context context, AttributeSet attrs) {
		super(context, attrs);
		init();
	}

	private void init() {
		if (isInEditMode()) return;
		
		originalSelector = getSelector();
		
		setDivider(null);
		setFocusable(false);
		
		setAdapter(adapter = new VerseAdapter.Factory().create(getContext()));
		setOnItemClickListener(itemClick);
		setVerseSelectionMode(VerseSelectionMode.multiple);
		
		super.setOnScrollListener(this);
	}
	
	@Override public final void setOnScrollListener(AbsListView.OnScrollListener l) {
		userOnScrollListener = l;
	}
	
	@Override public VerseAdapter getAdapter() {
		return adapter;
	}

	public void setParallelListener(CallbackSpan.OnClickListener parallelListener) {
		adapter.setParallelListener(parallelListener);
	}

	public void setAttributeListener(VersesView.AttributeListener attributeListener) {
		adapter.setAttributeListener(attributeListener);
	}
	
	public void setXrefListener(VersesView.XrefListener xrefListener) {
		adapter.setXrefListener(xrefListener);
	}
	
	public void setVerseSelectionMode(VerseSelectionMode mode) {
		this.verseSelectionMode = mode;
		
		if (mode == VerseSelectionMode.singleClick) {
			setSelector(originalSelector);
			uncheckAll();
			setChoiceMode(ListView.CHOICE_MODE_NONE);
		} else if (mode == VerseSelectionMode.multiple) {
			setSelector(new ColorDrawable(0x0));
			setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
		} else if (mode == VerseSelectionMode.none) {
			setSelector(new ColorDrawable(0x0));
			setChoiceMode(ListView.CHOICE_MODE_NONE);
		}
	}

	public void setOnVerseScrollListener(OnVerseScrollListener onVerseScrollListener) {
		this.onVerseScrollListener = onVerseScrollListener;
	}

	// TODO external should provide the attribute map into this widget similar to setData(), 
	// instead of this widget itself accessing persistent data.
	// When this is done, we do not need to provide Book and chapter_1 as parameters to setData(),
	// because in reality, VersesViews could contain verses taken from multiple books and chapters.
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

	public void setData(Book book, int chapter_1, SingleChapterVerses verses, int[] pericopeAris, PericopeBlock[] pericopeBlocks, int nblock, int[] xrefEntryCounts) {
		adapter.setData(book, chapter_1, verses, pericopeAris, pericopeBlocks, nblock, xrefEntryCounts);
	}

	private OnItemClickListener itemClick = new OnItemClickListener() {
		@Override public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
			if (verseSelectionMode == VerseSelectionMode.singleClick) {
				if (listener != null) listener.onVerseSingleClick(VersesView.this, adapter.getVerseFromPosition(position));
			} else if (verseSelectionMode == VerseSelectionMode.multiple) {
				adapter.notifyDataSetChanged();
				hideOrShowContextMenuButton();
			}
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
		if (verseSelectionMode != VerseSelectionMode.multiple) return;
		
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
	
	@Override public Parcelable onSaveInstanceState() {
		Bundle b = new Bundle();
		Parcelable superState = super.onSaveInstanceState();
		b.putParcelable("superState", superState);
		b.putInt("verseSelectionMode", verseSelectionMode.ordinal());
		return b;
	}
	
	@Override public void onRestoreInstanceState(Parcelable state) {
		if (state instanceof Bundle) {
			Bundle b = (Bundle) state;
			super.onRestoreInstanceState(b.getParcelable("superState"));
			setVerseSelectionMode(VerseSelectionMode.values()[b.getInt("verseSelectionMode")]);
		}
		
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

	public void setDataWithRetainSelectedVerses(boolean retainSelectedVerses, Book book, int chapter_1, int[] pericope_aris, PericopeBlock[] pericope_blocks, int nblock, SingleChapterVerses verses, int[] xrefEntryCounts) {
		IntArrayList selectedVerses_1 = null;
		if (retainSelectedVerses) {
			selectedVerses_1 = getSelectedVerses_1();
		}
		
		//# fill adapter with new data. make sure all checked states are reset
		uncheckAll();
		setData(book, chapter_1, verses, pericope_aris, pericope_blocks, nblock, xrefEntryCounts);
		loadAttributeMap();
		
		if (selectedVerses_1 != null) {
			for (int i = 0, len = selectedVerses_1.size(); i < len; i++) {
				int pos = adapter.getPositionIgnoringPericopeFromVerse(selectedVerses_1.get(i));
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
			post(new Runnable() {
				@Override public void run() {
					setSelectionFromTop(position, getVerticalFadingEdgeLength());
				}
			});
		}
	}
	
	public void scrollToVerse(int verse_1, final float prop) {
		final int position = adapter.getPositionIgnoringPericopeFromVerse(verse_1);
		
		if (position == -1) {
			Log.w(TAG, "could not find verse=" + verse_1 + ", weird!"); //$NON-NLS-1$ //$NON-NLS-2$
		} else {
			post(new Runnable() {
				@Override public void run() {
					boolean needMeasure = false;
					int shifty = getVerticalFadingEdgeLength();
					int firstPos = getFirstVisiblePosition();
					if (position >= firstPos) {
						int lastPos = getLastVisiblePosition();
						if (position <= lastPos) {
							// we have this on screen, no need to measure again
							View child = getChildAt(position - firstPos);
							setSelectionFromTop(position, shifty - (int) (prop * child.getHeight()));
						} else {
							needMeasure = true;
						}
					} else {
						needMeasure = true;
					}
					
					if (needMeasure) {
						View convertView = null; // TODO optimize using recycled view
						View child = adapter.getView(position, convertView, VersesView.this);
				        child.measure(MeasureSpec.makeMeasureSpec(VersesView.this.getWidth() - VersesView.this.getPaddingLeft() - VersesView.this.getPaddingRight(), MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
				        setSelectionFromTop(position, shifty - (int) (prop * child.getMeasuredHeight()));
					}
				}
			});
		}
	}

	public void setSelectedVersesListener(SelectedVersesListener listener) {
		this.listener = listener;
	}

	@Override public void onScrollStateChanged(AbsListView view, int scrollState) {
		if (userOnScrollListener != null) userOnScrollListener.onScrollStateChanged(view, scrollState);
		
		this.scrollState = scrollState;
	}
	
	@Override public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
		if (userOnScrollListener != null) userOnScrollListener.onScroll(view, firstVisibleItem, visibleItemCount, totalItemCount);
		
		if (onVerseScrollListener == null) return;
		
		if (view.getChildCount() > 0) {
			float prop = 0.f;
			int position = -1;
			
			View firstChild = view.getChildAt(0);
			// if first child is on top, top == fading edge length
			int bottom = firstChild.getBottom();
			int remaining = bottom - view.getVerticalFadingEdgeLength();
			if (remaining >= 0) {
				position = firstVisibleItem;
				prop = 1.f - (float) remaining / firstChild.getHeight();
			} else { // we should have a second child
				if (view.getChildCount() > 1) {
					View secondChild = view.getChildAt(1);
					position = firstVisibleItem + 1;
					prop = (float) -remaining / secondChild.getHeight();
				}
			}
			
			int verse_1 = adapter.getVerseOrPericopeFromPosition(position);
			
			if (scrollState != AbsListView.OnScrollListener.SCROLL_STATE_IDLE) {
				if (verse_1 > 0) {
					onVerseScrollListener.onVerseScroll(this, false, verse_1, prop);
				} else {
					onVerseScrollListener.onVerseScroll(this, true, 0, 0);
				}
			}
		}
	}
}
