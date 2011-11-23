
package yuku.alkitab.base.ac;

import android.app.*;
import android.content.*;
import android.database.*;
import android.os.*;
import android.util.*;
import android.view.*;
import android.view.ContextMenu.ContextMenuInfo;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.*;

import gnu.trove.list.*;
import gnu.trove.list.array.*;
import gnu.trove.map.hash.*;

import java.io.*;
import java.util.*;

import org.xml.sax.*;
import org.xml.sax.ext.*;
import org.xmlpull.v1.*;

import yuku.alkitab.*;
import yuku.alkitab.base.*;
import yuku.alkitab.base.dialog.*;
import yuku.alkitab.base.dialog.LabelEditorDialog.OkListener;
import yuku.alkitab.base.model.*;
import yuku.alkitab.base.storage.*;

public class BukmakActivity extends ListActivity {
	public static final String TAG = BukmakActivity.class.getSimpleName();
	
    // out
	public static final String EXTRA_ariTerpilih = "ariTerpilih"; //$NON-NLS-1$

	private static final int REQCODE_bukmakList = 1;

	BukmakFilterAdapter adapter;
	
	@Override protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		S.siapinKitab();
		S.bacaPengaturan();
		
		setContentView(R.layout.activity_bukmak);
		setTitle(R.string.judul_bukmak_activity);
		
		adapter = new BukmakFilterAdapter();
		adapter.reload();
		
		ListView listView = getListView();
		listView.setAdapter(adapter);

		registerForContextMenu(listView);
	}

	private void bikinMenu(Menu menu) {
		menu.clear();
		getMenuInflater().inflate(R.menu.activity_bukmak, menu);
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		bikinMenu(menu);
		
		return true;
	}
	
	@Override
	public boolean onMenuOpened(int featureId, Menu menu) {
		if (menu != null) {
			bikinMenu(menu);
		}
		
		return true;
	}
	
	void msgbox(String title, String message) {
		new AlertDialog.Builder(this)
		.setTitle(title)
		.setMessage(message)
		.setPositiveButton(R.string.ok, null)
		.show();
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		if (item.getItemId() == R.id.menuImpor) {
			final File f = getFileBackup();
			
			new AlertDialog.Builder(this)
			.setTitle(R.string.impor_judul)
			.setMessage(getString(R.string.impor_pembatas_buku_dan_catatan_dari_tanya, f.getAbsolutePath()))
			.setNegativeButton(R.string.no, null)
			.setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					if (!f.exists() || !f.canRead()) {
						msgbox(getString(R.string.impor_judul), getString(R.string.file_tidak_bisa_dibaca_file, f.getAbsolutePath()));
						return;
					}

					new AlertDialog.Builder(BukmakActivity.this)
					.setTitle(R.string.impor_judul)
					.setMessage(R.string.apakah_anda_mau_menumpuk_pembatas_buku_dan_catatan_tanya)
					.setNegativeButton(R.string.no, new DialogInterface.OnClickListener() {
						@Override
						public void onClick(DialogInterface dialog, int which) {
							impor(false);
						}
					})
					.setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
						@Override
						public void onClick(DialogInterface dialog, int which) {
							impor(true);
						}
					})
					.show();
				}
			})
			.show();
			
			return true;
		} else if (item.getItemId() == R.id.menuEkspor) {
			new AlertDialog.Builder(this)
			.setTitle(R.string.ekspor_judul)
			.setMessage(R.string.ekspor_pembatas_buku_dan_catatan_tanya)
			.setNegativeButton(R.string.no, null)
			.setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					ekspor();
				}
			})
			.show();
			
			return true;
		}
		return false;
	}
	
	File getFileBackup() {
		File dir = new File(Environment.getExternalStorageDirectory(), "bible"); //$NON-NLS-1$
		if (!dir.exists()) {
			dir.mkdir();
		}
		
		return new File(dir, getPackageName() + "-backup.xml"); //$NON-NLS-1$
	}

	public void impor(boolean tumpuk) {
		new AsyncTask<Boolean, Integer, Object>() {
			ProgressDialog pd;
			int count_bukmak = 0;
			int count_label = 0;
			
			@Override
			protected void onPreExecute() {
				pd = new ProgressDialog(BukmakActivity.this);
				pd.setTitle(R.string.impor_judul);
				pd.setMessage(getString(R.string.mengimpor_titiktiga));
				pd.setIndeterminate(true);
				pd.setCancelable(false);
				pd.show();
			}
			
			@Override protected Object doInBackground(Boolean... params) {
				final boolean tumpuk = params[0];
				
				final List<Bukmak2> xbukmak = new ArrayList<Bukmak2>();
				final TObjectIntHashMap<Bukmak2> bukmakToRelIdMap = new TObjectIntHashMap<Bukmak2>();
				final List<Label> xlabel = new ArrayList<Label>();
				final TObjectIntHashMap<Label> labelToRelIdMap = new TObjectIntHashMap<Label>();
				final TIntLongHashMap labelRelIdToAbsIdMap = new TIntLongHashMap();
				final TIntObjectHashMap<TIntList> bukmak2RelIdToLabelRelIdsMap = new TIntObjectHashMap<TIntList>();
				
				try {
					File in = getFileBackup();
					FileInputStream fis = new FileInputStream(in);
					
					Xml.parse(fis, Xml.Encoding.UTF_8, new DefaultHandler2() {
						@Override public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
							if (localName.equals(Bukmak2.XMLTAG_Bukmak2)) {
								Bukmak2 bukmak = Bukmak2.dariAttributes(attributes);
								int bukmak2_relId = Bukmak2.getRelId(attributes); 
								xbukmak.add(bukmak);
								bukmakToRelIdMap.put(bukmak, bukmak2_relId);
								count_bukmak++;
							} else if (localName.equals(Label.XMLTAG_Label)) {
								Label label = Label.dariAttributes(attributes);
								int label_relId = Label.getRelId(attributes); 
								xlabel.add(label);
								labelToRelIdMap.put(label, label_relId);
								count_label++;
							} else if (localName.equals(Bukmak2_Label_XMLTAG_Bukmak2_Label)) {
								int bukmak2_relId = Integer.parseInt(attributes.getValue("", Bukmak2_Label_XMLATTR_bukmak2_relId));
								int label_relId = Integer.parseInt(attributes.getValue("", Bukmak2_Label_XMLATTR_label_relId));
								
								TIntList labelRelIds = bukmak2RelIdToLabelRelIdsMap.get(bukmak2_relId);
								if (labelRelIds == null) {
									labelRelIds = new TIntArrayList();
									bukmak2RelIdToLabelRelIdsMap.put(bukmak2_relId, labelRelIds);
								}
								labelRelIds.add(label_relId);
							}
						}
					});
					fis.close();
				} catch (Exception e) {
					return e;
				}
				
				{ // bikin label-label yang diperlukan, juga map relId dengan id dari label.
					HashMap<String, Label> judulMap = new HashMap<String, Label>();
					List<Label> xlabelLama = S.getDb().listSemuaLabel();
					
					for (Label labelLama: xlabelLama) {
						judulMap.put(labelLama.judul, labelLama);
					}
					
					for (Label label: xlabel) {
						// cari apakah label yang judulnya persis sama udah ada
						Label labelLama = judulMap.get(label.judul);
						if (labelLama != null) {
							labelRelIdToAbsIdMap.put(labelToRelIdMap.get(label), labelLama._id);
							Log.d(TAG, "label (lama) r->a : " + labelToRelIdMap.get(label) + "->" + labelLama._id);
						} else { // belum ada, harus bikin baru
							Label labelBaru = S.getDb().tambahLabel(label.judul);
							labelRelIdToAbsIdMap.put(labelToRelIdMap.get(label), labelBaru._id);
							Log.d(TAG, "label (baru) r->a : " + labelToRelIdMap.get(label) + "->" + labelBaru._id);
						}
					}
				}
				
				S.getDb().importBukmak(xbukmak, tumpuk, bukmakToRelIdMap, labelRelIdToAbsIdMap, bukmak2RelIdToLabelRelIdsMap);
			
				return null;
			}

			@Override protected void onPostExecute(Object result) {
				pd.dismiss();
				
				if (result instanceof Exception) {
					msgbox(getString(R.string.impor_judul), getString(R.string.terjadi_kesalahan_ketika_mengimpor_pesan, ((Exception) result).getMessage()));
				} else {
					msgbox(getString(R.string.impor_judul), getString(R.string.impor_berhasil_angka_diproses, count_bukmak, count_label));
				}
				
				adapter.reload();
			}
		}.execute((Boolean)tumpuk);
	}
	
	public void ekspor() {
		new AsyncTask<Void, Integer, Object>() {
			ProgressDialog pd;
			
			@Override
			protected void onPreExecute() {
				pd = new ProgressDialog(BukmakActivity.this);
				pd.setTitle(R.string.ekspor_judul);
				pd.setMessage(getString(R.string.mengekspor_titiktiga));
				pd.setIndeterminate(true);
				pd.setCancelable(false);
				pd.show();
			}
			
			@Override
			protected Object doInBackground(Void... params) {
				File out = getFileBackup();
				try {
					FileOutputStream fos = new FileOutputStream(out);
					
					XmlSerializer xml = Xml.newSerializer();
					xml.setOutput(fos, "utf-8"); //$NON-NLS-1$
					xml.startDocument("utf-8", null); //$NON-NLS-1$
					xml.startTag(null, "backup"); //$NON-NLS-1$
					
					List<Bukmak2> xbukmak = new ArrayList<Bukmak2>();
					{ // write bookmarks
						Cursor cursor = S.getDb().listSemuaBukmak();
						try {
							while (cursor.moveToNext()) {
								Bukmak2 bukmak = Bukmak2.dariCursor(cursor);
								xbukmak.add(bukmak); // daftarkan bukmak
								bukmak.writeXml(xml, xbukmak.size() /* 1-based relId */);
							}
						} finally {
							cursor.close();
						}
					}
					
					TLongIntHashMap labelAbsIdToRelIdMap = new TLongIntHashMap();
					List<Label> xlabel = S.getDb().listSemuaLabel();
					{ // write labels
						for (int i = 0; i < xlabel.size(); i++) {
							Label label = xlabel.get(i);
							label.writeXml(xml, i + 1 /* 1-based relId */);
							labelAbsIdToRelIdMap.put(label._id, i + 1 /* 1-based relId */);
						}
					}
					
					{ // write mapping from bukmak to label
						for (int bukmak2_relId_0 = 0; bukmak2_relId_0 < xbukmak.size(); bukmak2_relId_0++) {
							Bukmak2 bukmak = xbukmak.get(bukmak2_relId_0);
							TLongList labelIds = S.getDb().listLabelIds(bukmak._id);
							if (labelIds != null && labelIds.size() > 0) {
								for (int i = 0; i < labelIds.size(); i++) {
									long labelId = labelIds.get(i);
									
									// we now need 2 relids, bukmak relid and label relid
									int bukmak2_relId = bukmak2_relId_0 + 1; // 1-based
									int label_relId = labelAbsIdToRelIdMap.get(labelId);
									
									if (label_relId != labelAbsIdToRelIdMap.getNoEntryValue()) { // just in case
										writeBukmak2_LabelXml(xml, bukmak2_relId, label_relId);
									}
								}
							}
						}
					}
					
					xml.endTag(null, "backup"); //$NON-NLS-1$
					xml.endDocument();
					fos.close();

					return out.getAbsolutePath();
				} catch (Exception e) {
					return e;
				}
			}
			
			@Override
			protected void onPostExecute(Object result) {
				pd.dismiss();
				
				if (result instanceof String) {
					msgbox(getString(R.string.ekspor_judul), getString(R.string.ekspor_berhasil_file_yang_dihasilkan_file, result));
				} else if (result instanceof Exception) {
					msgbox(getString(R.string.ekspor_judul), getString(R.string.terjadi_kesalahan_ketika_mengekspor_pesan, ((Exception) result).getMessage()));
				}
			}
		}.execute();
	}

	public static final String Bukmak2_Label_XMLTAG_Bukmak2_Label = "Bukmak2_Label"; //$NON-NLS-1$
	private static final String Bukmak2_Label_XMLATTR_bukmak2_relId = "bukmak2_relId"; //$NON-NLS-1$
	private static final String Bukmak2_Label_XMLATTR_label_relId = "label_relId"; //$NON-NLS-1$

	void writeBukmak2_LabelXml(XmlSerializer xml, int bukmak2_relId, int label_relId) throws IOException {
		xml.startTag(null, Bukmak2_Label_XMLTAG_Bukmak2_Label);
		xml.attribute(null, Bukmak2_Label_XMLATTR_bukmak2_relId, String.valueOf(bukmak2_relId));
		xml.attribute(null, Bukmak2_Label_XMLATTR_label_relId, String.valueOf(label_relId));
		xml.endTag(null, Bukmak2_Label_XMLTAG_Bukmak2_Label);
	}

	@Override
	protected void onListItemClick(ListView l, View v, int position, long id) {
		Intent intent;
		if (position == 0) {
			intent = BukmakListActivity.createIntent(this, Db.Bukmak2.jenis_bukmak, 0);
		} else if (position == 1) {
			intent = BukmakListActivity.createIntent(this, Db.Bukmak2.jenis_catatan, 0);
		} else if (position == 2) {
			intent = BukmakListActivity.createIntent(this, Db.Bukmak2.jenis_stabilo, 0);
		} else if (position == 3) {
			intent = BukmakListActivity.createIntent(this, Db.Bukmak2.jenis_bukmak, BukmakListActivity.LABELID_noLabel);
		} else {
			Label label = adapter.getItem(position);
			if (label != null) {
				intent = BukmakListActivity.createIntent(this, Db.Bukmak2.jenis_bukmak, label._id);
			} else {
				return;
			}
		}
		startActivityForResult(intent, REQCODE_bukmakList);
	}
	
	@Override public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
		getMenuInflater().inflate(R.menu.context_bukmak, menu);

		MenuItem menuRenameLabel = menu.findItem(R.id.menuRenameLabel);
		MenuItem menuDeleteLabel = menu.findItem(R.id.menuDeleteLabel);

		AdapterContextMenuInfo info = (AdapterContextMenuInfo) menuInfo;
		if (info.position < 4) {
			menuRenameLabel.setEnabled(false);
			menuDeleteLabel.setEnabled(false);
		} else {
			menuRenameLabel.setEnabled(true);
			menuDeleteLabel.setEnabled(true);
		}
	}

	@Override public boolean onContextItemSelected(MenuItem item) {
		AdapterContextMenuInfo info = (AdapterContextMenuInfo) item.getMenuInfo();

		int itemId = item.getItemId();
		if (itemId == R.id.menuRenameLabel) {
			final Label label = adapter.getItem(info.position);
			if (label == null) {
				return true;
			}
			
			LabelEditorDialog.show(this, label.judul, getString(R.string.rename_label_title), new OkListener() {
				@Override public void onOk(String judul) {
					S.getDb().renameLabel(label, judul);
					adapter.reload();
				}
			});

			return true;
		} else if (itemId == R.id.menuDeleteLabel) {
			final Label label = adapter.getItem(info.position);
			if (label == null) {
				return true;
			}
			
			int nbukmak = S.getDb().countBukmakDenganLabel(label);

			if (nbukmak == 0) {
				// tiada, langsung hapus aja!
				S.getDb().hapusLabelById(label._id);
				adapter.reload();
			} else {
				new AlertDialog.Builder(this)
				.setTitle(R.string.delete_label_title)
				.setMessage(getString(R.string.are_you_sure_you_want_to_delete_the_label_label, label.judul, nbukmak))
				.setNegativeButton(R.string.cancel, null)
				.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
					@Override public void onClick(DialogInterface dialog, int which) {
						S.getDb().hapusLabelById(label._id);
						adapter.reload();
					}
				})
				.show();
			}
			
			return true;
		}

		return false;
	}
	
	@Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (requestCode == REQCODE_bukmakList) {
			if (resultCode == RESULT_OK) {
				int ari = data.getIntExtra(BukmakActivity.EXTRA_ariTerpilih, 0);
				if (ari != 0) { // 0 berarti ga ada apa2, karena ga ada pasal 0 ayat 0
					Intent res = new Intent();
					res.putExtra(EXTRA_ariTerpilih, ari);
					
					setResult(RESULT_OK, res);
					finish();
				}
			}
		}
		
		adapter.reload();
	}
	
	class BukmakFilterAdapter extends BaseAdapter {
		// 0. [icon] All bookmarks
		// 1. [icon] Notes
		// 2. [icon] Highlights
		// 3. Unlabeled bookmarks
		// 4. dst label2
		
		List<Label> labels;
		private String[] presetCaptions = {
			getString(R.string.bmcat_all_bookmarks),
			getString(R.string.bmcat_notes),
			getString(R.string.bmcat_highlights),
			getString(R.string.bmcat_unlabeled_bookmarks),
		};
		
		private boolean hasLabels() {
			return labels != null && labels.size() > 0;
		}
		
		@Override public int getCount() {
			return 3 + (hasLabels()? 1 + labels.size(): 0);
		}

		@Override public Label getItem(int position) {
			if (position < 4) return null;
			return labels.get(position - 4);
		}

		@Override public long getItemId(int position) {
			return position;
		}

		@Override public View getView(int position, View convertView, ViewGroup parent) {
			View res = convertView != null? convertView: getLayoutInflater().inflate(R.layout.item_bukmakfilter, null);
			
			ImageView imgFilterIcon = U.getView(res, R.id.imgFilterIcon);
			if (position < 3) {
				imgFilterIcon.setVisibility(View.VISIBLE);
				imgFilterIcon.setImageResource(position == 0? R.drawable.jenis_bukmak: position == 1? R.drawable.jenis_catatan: position == 2? R.drawable.warnastabilo_checked: 0);
				imgFilterIcon.setBackgroundColor(position == 2? 0xffffff00: 0);
			} else {
				imgFilterIcon.setVisibility(View.GONE);
			}
			
			TextView lFilterCaption = U.getView(res, R.id.lFilterCaption);
			if (position < 4) {
				lFilterCaption.setVisibility(View.VISIBLE);
				lFilterCaption.setText(presetCaptions[position]);
			} else {
				lFilterCaption.setVisibility(View.GONE);
			}
			
			TextView lFilterLabel = U.getView(res, R.id.lFilterLabel);
			if (position < 4) {
				lFilterLabel.setVisibility(View.GONE);
			} else {
				Label label = getItem(position);
				lFilterLabel.setVisibility(View.VISIBLE);
				lFilterLabel.setText(label.judul);
			}
			
			return res;
		}
		
		private void reload() {
			labels = S.getDb().listSemuaLabel();
			notifyDataSetChanged();
		}
	}
}
