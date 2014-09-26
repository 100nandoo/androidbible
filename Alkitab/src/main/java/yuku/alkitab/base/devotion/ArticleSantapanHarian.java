package yuku.alkitab.base.devotion;

import android.text.Html;
import android.text.Spannable;
import android.text.SpannableStringBuilder;

import yuku.alkitab.base.ac.DevotionActivity;
import yuku.alkitab.base.widget.CallbackSpan;


public class ArticleSantapanHarian extends ArticleFromSabda {
	public ArticleSantapanHarian(String date) {
		super(date);
	}

	public ArticleSantapanHarian(String date, String title, String headerHtml, String bodyHtml, boolean readyToUse) {
		super(date, title, headerHtml, bodyHtml, readyToUse);
	}

	@Override public CharSequence getContent(CallbackSpan.OnClickListener<String> verseClickListener) {
		SpannableStringBuilder sb = new SpannableStringBuilder();
		sb.append(Html.fromHtml("<h3>" + title + "</h3>")); //$NON-NLS-1$ //$NON-NLS-2$
		sb.setSpan(new CallbackSpan<>(title, verseClickListener), 0, title.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
		
		sb.append(Html.fromHtml(bodyHtml));

		String copyrightHtml = "__________<br/>" +
		"<small>Santapan Harian / e-Santapan Harian<br/>" +
		"Bahan saat teduh yang diterbitkan secara teratur oleh Persekutuan Pembaca Alkitab (PPA) dan diterbitkan secara elektronik oleh Yayasan Lembaga SABDA (YLSA). <br/>" +
		"© 1999-2014 Yayasan Lembaga SABDA (YLSA). <br/>" +
		"Isi boleh disimpan untuk tujuan pribadi atau non-komersial. Atas setiap publikasi atau pencetakan wajib menyebutkan alamat situs SABDA.org sebagai sumber tulisan dan mengirim pemberitahuan ke webmaster@sabda.org</small>";
		
		sb.append(Html.fromHtml(copyrightHtml));
		
		return sb;
	}

	@Override
	public DevotionActivity.DevotionKind getKind() {
		return DevotionActivity.DevotionKind.SH;
	}
}
