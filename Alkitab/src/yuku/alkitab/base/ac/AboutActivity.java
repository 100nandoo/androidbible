package yuku.alkitab.base.ac;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Html;
import android.text.SpannableStringBuilder;
import android.text.method.LinkMovementMethod;
import android.text.style.StyleSpan;
import android.text.style.URLSpan;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup.LayoutParams;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.TextView;

import yuku.afw.V;
import yuku.alkitab.R;
import yuku.alkitab.base.App;
import yuku.alkitab.base.S;
import yuku.alkitab.base.U;
import yuku.alkitab.base.ac.base.BaseActivity;

public class AboutActivity extends BaseActivity {
	public static final String TAG = AboutActivity.class.getSimpleName();

	TextView lAbout;
	TextView lTranslators;
	View bSaran;

	@Override protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_about);
		setTitle(getString(R.string.namaprog_versi_build, S.getVersionName(), S.getVersionCode()));
		
		lAbout = V.get(this, R.id.lAbout);
		lTranslators = V.get(this, R.id.lTranslators);
		bSaran = V.get(this, R.id.bSaran);
		
		// preprocess html
		lAbout.setText(Html.fromHtml(U.preprocessHtml(lAbout.getText().toString())));
		
		// disable changing color on press
		lAbout.setTextColor(lAbout.getTextColors().getDefaultColor());
		
		lAbout.setLinkTextColor(0xffc0c0ff);
		
		String[] translators = getResources().getStringArray(R.array.translators_list);
		SpannableStringBuilder sb = new SpannableStringBuilder();
		sb.append(getString(R.string.about_translators)).append('\n'); 
		sb.setSpan(new StyleSpan(Typeface.BOLD), 0, sb.length(), 0);

		for (String translator: translators) {
			sb.append("\u2022 "); //$NON-NLS-1$
			int open = translator.indexOf('[');
			int close = translator.indexOf(']');
			if (open != -1 && close > open) {
				sb.append(translator.substring(0, open));
				int sb_len = sb.length();
				sb.append(translator.substring(close + 1));
				sb.setSpan(new URLSpan(translator.substring(open + 1, close)), sb_len, sb.length(), 0);
			} else {
				sb.append(translator);
			}
			sb.append('\n');
		}
		lTranslators.setText(sb);
		lTranslators.setMovementMethod(LinkMovementMethod.getInstance());
		
		bSaran.setOnClickListener(bSaran_click);
	}

	private OnClickListener bSaran_click = new OnClickListener() {
		@Override public void onClick(View v) {
			final View dialogView = getLayoutInflater().inflate(R.layout.dialog_suggest, null);
			
			AlertDialog dialog = new AlertDialog.Builder(AboutActivity.this)
			.setTitle(R.string.beri_saran_title)
			.setView(dialogView)
			.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
				@Override public void onClick(DialogInterface dialog, int which) {
					EditText tSaran = V.get(dialogView, R.id.tSaran);
					String isi = tSaran.getText().toString();
					
					if (isi.trim().length() > 0) {
						App.pengirimFidbek.tambah(isi);
					}
					
					App.pengirimFidbek.cobaKirim();
				}
			})
			.create();
			
			dialog.getWindow().setLayout(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
			dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE | WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);
			dialog.show();
		}
	};
}
