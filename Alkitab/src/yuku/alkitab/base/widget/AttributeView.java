package yuku.alkitab.base.widget;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.support.v4.view.MotionEventCompat;
import android.util.AttributeSet;
import android.util.SparseArray;
import android.view.MotionEvent;
import android.view.View;
import yuku.alkitab.debug.R;
import yuku.alkitab.model.Book;

public class AttributeView extends View {

	public static final int PROGRESS_MARK_BITS_START = 8;
	public static final int PROGRESS_MARK_TOTAL_COUNT = 5;
	public static final int PROGRESS_MARK_BIT_MASK = (1 << PROGRESS_MARK_BITS_START) * ((1 << PROGRESS_MARK_TOTAL_COUNT) - 1);

	static Bitmap bookmarkBitmap = null;
	static Bitmap noteBitmap = null;
	static Bitmap[] progressMarkBitmap = new Bitmap[PROGRESS_MARK_TOTAL_COUNT];
	static Paint alphaPaint = new Paint();

	private boolean showBookmark;
	private boolean showNote;
	private int attribute;

	private VersesView.AttributeListener attributeListener;
	private Book book;
	private int chapter_1;
	private int verse_1;

	private static SparseArray<Long> progressMarkAnimationStartTimes = new SparseArray<Long>();
	private int drawOffsetLeft;

	public AttributeView(final Context context) {
		super(context);
		init();
	}

	public AttributeView(final Context context, final AttributeSet attrs) {
		super(context, attrs);
		init();
	}

	private void init() {
		drawOffsetLeft = Math.round(1 * getResources().getDisplayMetrics().density);
	}

	public void showBookmark(final boolean showBookmark) {
		this.showBookmark = showBookmark;
		requestLayout();
		invalidate();
	}

	public void showNote(final boolean showNote) {
		this.showNote = showNote;
		requestLayout();
		invalidate();
	}

	public void showProgressMarks(final int attribute) {
		this.attribute = attribute;
		requestLayout();
		invalidate();
	}

	public boolean isShowingSomething() {
		return showBookmark || showNote || ((attribute & PROGRESS_MARK_BIT_MASK) != 0);
	}

	Bitmap getBookmarkBitmap() {
		if (bookmarkBitmap == null) {
			bookmarkBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.ic_attr_bookmark);
		}
		return bookmarkBitmap;
	}

	Bitmap getNoteBitmap() {
		if (noteBitmap == null) {
			noteBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.ic_attr_note);
		}
		return noteBitmap;
	}

	Bitmap getProgressMarkBitmapByPresetId(int preset_id) {
		if (progressMarkBitmap[preset_id] == null) {
			progressMarkBitmap[preset_id] = BitmapFactory.decodeResource(getResources(), getProgressMarkIconResource(preset_id));
		}
		return progressMarkBitmap[preset_id];
	}

	@Override
	protected void onMeasure(final int widthMeasureSpec, final int heightMeasureSpec) {
		int totalHeight = 0;
		int totalWidth = 0;
		if (showBookmark) {
			final Bitmap bookmarkBitmap = getBookmarkBitmap();
			totalHeight += bookmarkBitmap.getHeight();
			if (totalWidth < bookmarkBitmap.getWidth()) {
				totalWidth = bookmarkBitmap.getWidth();
			}
		}
		if (showNote) {
			final Bitmap noteBitmap = getNoteBitmap();
			totalHeight += noteBitmap.getHeight();
			if (totalWidth < noteBitmap.getWidth()) {
				totalWidth = noteBitmap.getWidth();
			}
		}
		if (attribute != 0) {
			for (int preset_id = 0; preset_id < PROGRESS_MARK_TOTAL_COUNT; preset_id++) {
				if (isProgressMarkSetFromAttribute(preset_id)) {
					final Bitmap progressMarkBitmapById = getProgressMarkBitmapByPresetId(preset_id);
					totalHeight += progressMarkBitmapById.getHeight();
					if (totalWidth < progressMarkBitmapById.getWidth()) {
						totalWidth = progressMarkBitmapById.getWidth();
					}
				}
			}
		}

		setMeasuredDimension(totalWidth, totalHeight);
	}

	private boolean isProgressMarkSetFromAttribute(final int preset_id) {
		return (attribute & (1 << (preset_id + PROGRESS_MARK_BITS_START))) != 0;
	}

	@Override
	protected void onDraw(final Canvas canvas) {
		int totalHeight = 0;
		if (showBookmark) {
			final Bitmap bookmarkBitmap = getBookmarkBitmap();
			canvas.drawBitmap(bookmarkBitmap, drawOffsetLeft, totalHeight, null);
			totalHeight += bookmarkBitmap.getHeight();
		}
		if (showNote) {
			final Bitmap noteBitmap = getNoteBitmap();
			canvas.drawBitmap(noteBitmap, drawOffsetLeft, totalHeight, null);
			totalHeight += noteBitmap.getHeight();
		}
		if (attribute != 0) {
			for (int preset_id = 0; preset_id < PROGRESS_MARK_TOTAL_COUNT; preset_id++) {
				if (isProgressMarkSetFromAttribute(preset_id)) {
					final Bitmap progressMarkBitmapById = getProgressMarkBitmapByPresetId(preset_id);
					final Long animationStartTime = progressMarkAnimationStartTimes.get(preset_id);
					final Paint p;
					if (animationStartTime == null) {
						p = null;
					} else {
						final int animationElapsed = (int) (System.currentTimeMillis() - animationStartTime);
						final int animationDuration = 800;
						if (animationElapsed >= animationDuration) {
							p = null;
						} else {
							alphaPaint.setAlpha(animationElapsed * 255 / animationDuration);
							p = alphaPaint;
							invalidate(); // animation is still running so request for invalidate
						}
					}
					canvas.drawBitmap(progressMarkBitmapById, 0, totalHeight, p);
					totalHeight += progressMarkBitmapById.getHeight();
				}
			}
		}
	}

	@Override
	public boolean onTouchEvent(final MotionEvent event) {
		float y = event.getY();
		int totalHeight = 0;
		if (MotionEventCompat.getActionMasked(event) == MotionEvent.ACTION_UP) {
			if (showBookmark) {
				final Bitmap bookmarkBitmap = getBookmarkBitmap();
				totalHeight += bookmarkBitmap.getHeight();
				if (totalHeight > y) {
					attributeListener.onBookmarkAttributeClick(book, chapter_1, verse_1);
					return true;
				}
			}
			if (showNote) {
				final Bitmap noteBitmap = getNoteBitmap();
				totalHeight += noteBitmap.getHeight();
				if (totalHeight > y) {
					attributeListener.onNoteAttributeClick(book, chapter_1, verse_1);
					return true;
				}
			}
			if (attribute != 0) {
				for (int preset_id = 0; preset_id < PROGRESS_MARK_TOTAL_COUNT; preset_id++) {
					if (isProgressMarkSetFromAttribute(preset_id)) {
						final Bitmap progressMarkBitmapById = getProgressMarkBitmapByPresetId(preset_id);
						totalHeight += progressMarkBitmapById.getHeight();
						if (totalHeight > y) {
							attributeListener.onProgressMarkAttributeClick(preset_id);
							return true;
						}
					}
				}
			}
		} else if (MotionEventCompat.getActionMasked(event) == MotionEvent.ACTION_DOWN) {
			return true;
		}
		return false;
	}

	public void setAttributeListener(VersesView.AttributeListener attributeListener, Book book, int chapter_1, int verse_1) {
		this.attributeListener = attributeListener;
		this.book = book;
		this.chapter_1 = chapter_1;
		this.verse_1 = verse_1;
	}

	public static void startAnimationForProgressMark(final int preset_id) {
		progressMarkAnimationStartTimes.put(preset_id, System.currentTimeMillis());
	}

	public static int getDefaultProgressMarkStringResource(int preset_id) {
		switch (preset_id) {
			case 0:
				return R.string.pm_progress_1;
			case 1:
				return R.string.pm_progress_2;
			case 2:
				return R.string.pm_progress_3;
			case 3:
				return R.string.pm_progress_4;
			case 4:
				return R.string.pm_progress_5;
		}
		return 0;
	}

	public static int getProgressMarkIconResource(int preset_id) {
		switch (preset_id) {
			case 0:
				return R.drawable.ic_attr_progress_mark_1;
			case 1:
				return R.drawable.ic_attr_progress_mark_2;
			case 2:
				return R.drawable.ic_attr_progress_mark_3;
			case 3:
				return R.drawable.ic_attr_progress_mark_4;
			case 4:
				return R.drawable.ic_attr_progress_mark_5;
		}
		return 0;
	}
}
