package yuku.alkitab.base.widget;

import android.content.Context;
import android.text.SpannableStringBuilder;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.TextView.BufferType;

import yuku.alkitab.R;
import yuku.alkitab.base.S;
import yuku.alkitab.base.U;
import yuku.alkitab.base.model.Ari;
import yuku.alkitab.base.model.PericopeBlock;
import yuku.alkitab.base.util.Appearances;


public class SingleViewVerseAdapter extends VerseAdapter {
	public static final String TAG = SingleViewVerseAdapter.class.getSimpleName();
	
	public SingleViewVerseAdapter(Context context) {
		super(context);
	}

	@Override public synchronized View getView(int position, View convertView, ViewGroup parent) {
		// Harus tentukan apakah ini perikop ato ayat.
		int id = itemPointer_[position];
		
		if (id >= 0) {
			// AYAT. bukan judul perikop.

			String text = verses_.getVerse(id);
			boolean withBookmark = attributeMap_ == null ? false : (attributeMap_[id] & 0x1) != 0;
			boolean withNote = attributeMap_ == null ? false : (attributeMap_[id] & 0x2) != 0;
			boolean withHighlight = attributeMap_ == null ? false : (attributeMap_[id] & 0x4) != 0;
			int withXref = xrefEntryCounts_ == null? 0: xrefEntryCounts_[id + 1];
			int highlightColor = withHighlight ? (highlightMap_ == null ? 0 : U.alphaMixHighlight(highlightMap_[id])) : 0;

			boolean checked = false;
			if (parent instanceof ListView) {
				checked = ((ListView) parent).isItemChecked(position);
			}

			VerseItem res;
			if (convertView == null || convertView.getId() != R.layout.item_verse) {
				res = (VerseItem) inflater_.inflate(R.layout.item_verse, null);
				res.setId(R.layout.item_verse);
			} else {
				res = (VerseItem) convertView;
			}
			
			VerseTextView lText = (VerseTextView) res.findViewById(R.id.lText);
			TextView lVerseNumber = (TextView) res.findViewById(R.id.lVerseNumber);
			
			int ari = Ari.encode(book_.bookId, chapter_1_, id + 1);
			
			boolean dontPutSpacingBefore = (position > 0 && itemPointer_[position - 1] < 0) || position == 0;
			VerseRenderer.render(lText, lVerseNumber, ari, id + 1, text, highlightColor, checked, dontPutSpacingBefore, withXref);
			
			Appearances.applyTextAppearance(lText);
			if (checked) {
				lText.setTextColor(0xff000000); // override with black!
			}

			View imgAttributeBookmark = res.findViewById(R.id.imgAtributBukmak);
			imgAttributeBookmark.setVisibility(withBookmark ? View.VISIBLE : View.GONE);
			if (withBookmark) {
				setClickListenerForBookmark(imgAttributeBookmark, chapter_1_, id + 1);
			}
			View imgAttributeNote = res.findViewById(R.id.imgAtributCatatan);
			imgAttributeNote.setVisibility(withNote ? View.VISIBLE : View.GONE);
			if (withNote) {
				setClickListenerForNote(imgAttributeNote, chapter_1_, id + 1);
			}
			
//			{ // DUMP
//				Log.d(TAG, "==== DUMP verse " + (id + 1));
//				SpannedString sb = (SpannedString) lText.getText();
//				Object[] spans = sb.getSpans(0, sb.length(), Object.class);
//				for (Object span: spans) {
//					int start = sb.getSpanStart(span);
//					int end = sb.getSpanEnd(span);
//					Log.d(TAG, "Span " + span.getClass().getSimpleName() + " " + start + ".." + end + ": " + sb.toString().substring(start, end));
//				}
//			}

			return res;
		} else {
			// JUDUL PERIKOP. bukan ayat.

			View res;
			if (convertView == null || convertView.getId() != R.layout.item_pericope_header) {
				res = LayoutInflater.from(context_).inflate(R.layout.item_pericope_header, null);
				res.setId(R.layout.item_pericope_header);
			} else {
				res = convertView;
			}

			PericopeBlock pericopeBlock = pericopeBlocks_[-id - 1];

			TextView lJudul = (TextView) res.findViewById(R.id.lJudul);
			TextView lXparalel = (TextView) res.findViewById(R.id.lXparalel);

			lJudul.setText(pericopeBlock.title);

			int paddingTop;
			// turn off top padding if the position == 0 OR before this is also a pericope title
			if (position == 0 || itemPointer_[position - 1] < 0) {
				paddingTop = 0;
			} else {
				paddingTop = S.applied.pericopeSpacingTop;
			}
			
			res.setPadding(0, paddingTop, 0, S.applied.pericopeSpacingBottom);

			Appearances.applyPericopeTitleAppearance(lJudul);

			// gonekan paralel kalo ga ada
			if (pericopeBlock.parallels.length == 0) {
				lXparalel.setVisibility(View.GONE);
			} else {
				lXparalel.setVisibility(View.VISIBLE);

				SpannableStringBuilder sb = new SpannableStringBuilder("("); //$NON-NLS-1$

				int total = pericopeBlock.parallels.length;
				for (int i = 0; i < total; i++) {
					String parallel = pericopeBlock.parallels[i];

					if (i > 0) {
						// paksa new line untuk pola2 paralel tertentu
						if ((total == 6 && i == 3) || (total == 4 && i == 2) || (total == 5 && i == 3)) {
							sb.append("; \n"); //$NON-NLS-1$
						} else {
							sb.append("; "); //$NON-NLS-1$
						}
					}

                    appendParallel(sb, parallel);
				}
				sb.append(')');

				lXparalel.setText(sb, BufferType.SPANNABLE);
				Appearances.applyPericopeParallelTextAppearance(lXparalel);
			}

			return res;
		}
	}

    private void appendParallel(SpannableStringBuilder sb, String parallel) {
        int sb_len = sb.length();

        linked: {
            if (parallel.startsWith("@")) {
                Object data;
                String display;

                if (parallel.startsWith("@o:")) { // osis ref
                    int space = parallel.indexOf(' ');
                    if (space != -1) {
                        String osis = parallel.substring(3, space);
                        int dash = osis.indexOf('-');
                        if (dash != -1) {
                            osis = osis.substring(0, dash);
                        }
                        ParallelTypeOsis d = new ParallelTypeOsis();
                        d.osisStart = osis;
                        data = d;
                        display = parallel.substring(space + 1);
                    } else {
                        break linked;
                    }
                } else if (parallel.startsWith("@a:")) { // ari ref
                    int space = parallel.indexOf(' ');
                    if (space != -1) {
                        String ari_s = parallel.substring(3, space);
                        int dash = ari_s.indexOf('-');
                        if (dash != -1) {
                            ari_s = ari_s.substring(0, dash);
                        }
                        int ari = Ari.parseInt(ari_s, 0);
                        if (ari == 0) {
                            break linked;
                        }
                        ParallelTypeAri d = new ParallelTypeAri();
                        d.ariStart = ari;
                        data = d;
                        display = parallel.substring(space + 1);
                    } else {
                        break linked;
                    }
                } else if (parallel.startsWith("@lid:")) { // lid ref
                    int space = parallel.indexOf(' ');
                    if (space != -1) {
                        String lid_s = parallel.substring(5, space);
                        int dash = lid_s.indexOf('-');
                        if (dash != -1) {
                            lid_s = lid_s.substring(0, dash);
                        }
                        int lid = Ari.parseInt(lid_s, 0);
                        if (lid == 0) {
                            break linked;
                        }
                        ParallelTypeLid d = new ParallelTypeLid();
                        d.lidStart = lid;
                        data = d;
                        display = parallel.substring(space + 1);
                    } else {
                        break linked;
                    }
                } else {
                    break linked;
                }

                // if we reach this, data and display should have values, and we must not go to fallback below
                sb.append(display);
                sb.setSpan(new CallbackSpan(data, parallelListener_), sb_len, sb.length(), 0);
                return; // do not remove this
            }
        }

        // fallback if the above code fails
        sb.append(parallel);
        sb.setSpan(new CallbackSpan(parallel, parallelListener_), sb_len, sb.length(), 0);
    }
}
