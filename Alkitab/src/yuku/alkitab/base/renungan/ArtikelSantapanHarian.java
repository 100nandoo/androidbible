package yuku.alkitab.base.renungan;


public class ArtikelSantapanHarian extends ArtikelDariSabda {
	public ArtikelSantapanHarian(String tgl) {
		super(tgl);
	}

	public ArtikelSantapanHarian(String tgl, String judul, String headerHtml, String isiHtml, boolean siapPakai) {
		super(tgl, judul, headerHtml, isiHtml, siapPakai);
	}

	@Override
	public CharSequence getKopiraitHtml() {
		return "Santapan Harian.<br/>diambil dari <b>sabda.org</b>";
	}

	@Override
	public String getNama() {
		return "sh";
	}
	
	@Override
	public String getNamaUmum() {
		return "Santapan Harian";
	}
}
