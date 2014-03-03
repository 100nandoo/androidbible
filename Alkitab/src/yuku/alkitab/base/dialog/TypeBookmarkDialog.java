package yuku.alkitab.base.dialog;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import yuku.afw.V;
import yuku.alkitab.base.S;
import yuku.alkitab.base.U;
import yuku.alkitab.base.dialog.LabelEditorDialog.OkListener;
import yuku.alkitab.debug.R;
import yuku.alkitab.model.Label;
import yuku.alkitab.model.Marker;
import yuku.devoxx.flowlayout.FlowLayout;

import java.util.Date;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

public class TypeBookmarkDialog {
	public interface Listener {
		void onOk();
	}

	final Context context;
	final AlertDialog dialog;
	FlowLayout panelLabels;
	LabelAdapter adapter;
	EditText tCaption;

	Marker marker;
	int ariForNewBookmark;
	String defaultCaption;

	// optional
	Listener listener;

	// current labels (can be not in the db)
	SortedSet<Label> labels = new TreeSet<>();

	/**
	 * Open the bookmark edit dialog, editing existing bookmark.
	 * @param context Activity context to create dialogs
	 */
	public TypeBookmarkDialog(Context context, long _id) {
		this(context, S.getDb().getMarkerById(_id), null);
	}

	/**
	 * Open the bookmark edit dialog for an existing note by ari and ordering (starting from 0).
	 */
	public TypeBookmarkDialog(Context context, int ari, int ordering) {
		this(context, S.getDb().getMarker(ari, Marker.Kind.bookmark, ordering), null);
	}

	/**
	 * Open the bookmark edit dialog for a new bookmark by ari.
	 */
	public TypeBookmarkDialog(Context context, int ari) {
		this(context, null, S.activeVersion.reference(ari));
		this.ariForNewBookmark = ari;
	}

	private TypeBookmarkDialog(final Context context, Marker marker, String reference) {
		this.context = context;
		this.marker = marker;

		if (reference == null) {
			reference = S.activeVersion.reference(marker.ari);
		}
		defaultCaption = reference;

		View dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_edit_bookmark, null);
		this.panelLabels = V.get(dialogView, R.id.panelLabels);

		tCaption = V.get(dialogView, R.id.tCaption);
		final Button bAddLabel = V.get(dialogView, R.id.bAddLabel);

		bAddLabel.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				adapter = new LabelAdapter();

				AlertDialog.Builder b = new AlertDialog.Builder(context)
				.setTitle(R.string.add_label_title)
				.setAdapter(adapter, bAddLabel_dialog_itemSelected)
				.setNegativeButton(R.string.cancel, null);

				adapter.setDialogContext(b.getContext());

				b.show();
			}
		});

		if (marker != null) {
			labels = new TreeSet<>();
			List<Label> ll = S.getDb().listLabelsByMarkerId(marker._id);
			if (ll != null) labels.addAll(ll);
		}
		setLabelsText();

		tCaption.setText(marker != null? marker.caption: reference);

		this.dialog = new AlertDialog.Builder(context)
		.setView(dialogView)
		.setTitle(reference)
		.setIcon(R.drawable.ic_attr_bookmark)
		.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
			@Override public void onClick(DialogInterface dialog, int which) {
				bOk_click();
			}
		})
		.setNegativeButton(R.string.cancel, null)
		.show();
	}

	void bOk_click() {
		String caption = tCaption.getText().toString();

		// If there is no caption, show reference
		if (caption.length() == 0 || caption.trim().length() == 0) {
			caption = defaultCaption;
		}

		final Date now = new Date();
		if (marker != null) { // update existing
			marker.caption = caption;
			marker.modifyTime = now;
			S.getDb().updateMarker(marker);
		} else { // add new
			marker = S.getDb().insertMarker(ariForNewBookmark, Marker.Kind.bookmark, caption, 1, now, now);
		}

		S.getDb().updateLabels(marker, labels);

		if (listener != null) listener.onOk();
	}

	public void setListener(Listener listener) {
		this.listener = listener;
	}
	
	OnClickListener bAddLabel_dialog_itemSelected = new OnClickListener() {
		@Override public void onClick(DialogInterface _unused_, int which) {
			if (which == adapter.getCount() - 1) { // new label
				LabelEditorDialog.show(context, "", context.getString(R.string.create_label_title), new OkListener() { //$NON-NLS-1$
					@Override public void onOk(String judul) {
						Label labelBaru = S.getDb().insertLabel(judul, null);
						if (labelBaru != null) {
							labels.add(labelBaru);
							setLabelsText();
						}
					}
				});
			} else {
				Label label = adapter.getItem(which);
				labels.add(label);
				setLabelsText();
			}
		}
	};
	
	private View.OnClickListener label_click = new View.OnClickListener() {
		@Override public void onClick(View v) {
			final Label label = (Label) v.getTag(R.id.TAG_label);
			if (label == null) return;
			
			new AlertDialog.Builder(context)
			.setMessage(context.getString(R.string.do_you_want_to_remove_the_label_label_from_this_bookmark, label.title))
			.setPositiveButton(R.string.ok, new OnClickListener() {
				@Override public void onClick(DialogInterface dialog, int which) {
					labels.remove(label);
					setLabelsText();
				}
			})
			.setNegativeButton(R.string.cancel, null)
			.show();
		}
	};

	public void show() {

	}
	
	void setLabelsText() {
		// remove all first
		final int childCount = panelLabels.getChildCount();
		if (childCount > 1) {
			panelLabels.removeViews(1, childCount - 2);
		}
		
		int pos = 1;
		for (Label label: labels) {
			panelLabels.addView(getLabelView(label), pos++);
		}
	}
	
	private View getLabelView(Label label) {
		TextView res = (TextView) LayoutInflater.from(context).inflate(R.layout.label_x, null);
		res.setLayoutParams(panelLabels.generateDefaultLayoutParams());
		res.setText(label.title);
		res.setTag(R.id.TAG_label, label);
		res.setOnClickListener(label_click);
		
		U.applyLabelColor(label, res);
		
		return res;
	}

	class LabelAdapter extends BaseAdapter {
		private List<Label> labels;
		private Context dialogContext;

		public LabelAdapter() {
			labels = S.getDb().listAllLabels();
			dialogContext = context;
		}
		
		public void setDialogContext(Context dialogContext) {
			this.dialogContext = dialogContext;
		}
		
		@Override public int getCount() {
			return labels.size() + 1;
		}

		@Override public Label getItem(int position) {
			return (position < 0 || position >= labels.size())? null: labels.get(position);
		}

		@Override public long getItemId(int position) {
			return position;
		}

		@Override public View getView(int position, View convertView, ViewGroup parent) {
			int type = getItemViewType(position);
			View res = convertView != null? convertView: LayoutInflater.from(dialogContext).inflate(type == 0? R.layout.item_label_chooser: android.R.layout.simple_list_item_1, null);

			if (type == 0) {
				TextView text1 = V.get(res, android.R.id.text1);
				Label label = getItem(position);
				text1.setText(label.title);
				U.applyLabelColor(label, text1);
			} else {
				TextView text1 = V.get(res, android.R.id.text1);
				text1.setText(context.getString(R.string.create_label_titik3));
			}
			
			return res;
		}
		
		@Override public int getViewTypeCount() {
			return 2;
		}
		
		@Override public int getItemViewType(int position) {
			if (position == getCount() - 1) return 1;
			return 0;
		}
	}
}
