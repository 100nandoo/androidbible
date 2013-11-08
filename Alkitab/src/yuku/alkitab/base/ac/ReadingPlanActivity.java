package yuku.alkitab.base.ac;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.widget.PopupMenu;
import android.text.SpannableStringBuilder;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.DatePicker;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import yuku.afw.App;
import yuku.afw.V;
import yuku.afw.storage.Preferences;
import yuku.alkitab.base.S;
import yuku.alkitab.base.config.AppConfig;
import yuku.alkitab.base.model.Ari;
import yuku.alkitab.base.model.ReadingPlan;
import yuku.alkitab.base.model.Version;
import yuku.alkitab.base.storage.Prefkey;
import yuku.alkitab.base.util.IntArrayList;
import yuku.alkitab.base.util.ReadingPlanManager;
import yuku.alkitab.debug.R;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;

public class ReadingPlanActivity extends ActionBarActivity {
	public static final String READING_PLAN_ARI_RANGES = "reading_plan_ari_ranges";
	public static final String READING_PLAN_ID = "reading_plan_id";
	public static final String READING_PLAN_DAY_NUMBER = "reading_plan_day_number";

	private ReadingPlan readingPlan;
	private List<ReadingPlan.ReadingPlanInfo> downloadedReadingPlanInfos;
	private int todayNumber;
	private int dayNumber;
	private IntArrayList readingCodes;
	private boolean newDropDownItems;

	private ImageButton bLeft;
	private ImageButton bRight;
	private Button bToday;
	private ListView lsTodayReadings;
	private ReadingPlanAdapter readingPlanAdapter;
	private ActionBar actionBar;
	private LinearLayout llNavigations;
	private FrameLayout flNoData;
	private Button bDownload;

	public static Intent createIntent(int dayNumber) {
		Intent intent = new Intent(App.context, ReadingPlanActivity.class);
		intent.putExtra(READING_PLAN_DAY_NUMBER, dayNumber);
		return intent;
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.activity_reading_plan);
		llNavigations = V.get(this, R.id.llNavigations);
		flNoData = V.get(this, R.id.flNoDataContainer);

		lsTodayReadings = V.get(this, R.id.lsTodayReadings);
		bToday = V.get(this, R.id.bToday);
		bLeft = V.get(this, R.id.bLeft);
		bRight = V.get(this, R.id.bRight);
		bDownload = V.get(this, R.id.bDownload);
		actionBar = getSupportActionBar();

		long id = Preferences.getLong(Prefkey.active_reading_plan, 0);
		loadReadingPlan(id);
		loadReadingPlanProgress();
		loadDayNumber();
		prepareDropDownNavigation();
		prepareDisplay();

	}

	@Override
	public boolean onCreateOptionsMenu(final Menu menu) {
		getMenuInflater().inflate(R.menu.activity_reading_plan, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(final MenuItem item) {
		int itemId = item.getItemId();
		if (itemId == R.id.menuReset) {
			resetReadingPlan();
			return true;
		} else if (itemId == R.id.menuDownload) {
			downloadReadingPlan();
			return true;
		} else if (itemId == R.id.menuDelete) {
			deleteReadingPlan();
			return true;
		}
		return super.onOptionsItemSelected(item);
	}

	public void goToIsiActivity(final int dayNumber, final int sequence) {
		final int[] selectedVerses = readingPlan.dailyVerses.get(dayNumber);
		int ari = selectedVerses[sequence * 2];

		Intent intent = new Intent();
		intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
		intent.putExtra("ari", ari);
		intent.putExtra(READING_PLAN_ID, readingPlan.info.id);
		intent.putExtra(READING_PLAN_DAY_NUMBER, dayNumber);
		intent.putExtra(READING_PLAN_ARI_RANGES, selectedVerses);
		setResult(RESULT_OK, intent);
		finish();
	}

	public void prepareDisplay() {
		if (readingPlan == null) {
			llNavigations.setVisibility(View.GONE);
			lsTodayReadings.setVisibility(View.GONE);
			flNoData.setVisibility(View.VISIBLE);

			bDownload.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(final View v) {
					downloadReadingPlan();
				}
			});
			return;
		}
		llNavigations.setVisibility(View.VISIBLE);
		lsTodayReadings.setVisibility(View.VISIBLE);
		flNoData.setVisibility(View.GONE);

		//Listviews
		readingPlanAdapter = new ReadingPlanAdapter();
		readingPlanAdapter.load();
		lsTodayReadings.setAdapter(readingPlanAdapter);

		lsTodayReadings.setOnItemClickListener(new AdapterView.OnItemClickListener() {
			@Override
			public void onItemClick(final AdapterView<?> parent, final View view, final int position, final long id) {
				final int todayReadingsSize = readingPlan.dailyVerses.get(dayNumber).length / 2;
				if (position < todayReadingsSize) {
					goToIsiActivity(dayNumber, position);
				} else if (position > todayReadingsSize) {
					goToIsiActivity(position - todayReadingsSize - 1, 0);
				}
			}

		});

		//buttons

		updateButtonStatus();

		bToday.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(final View v) {
				final PopupMenu popupMenu = new PopupMenu(ReadingPlanActivity.this, v);
				popupMenu.getMenu().add(Menu.NONE, 1, 1, "Show calendar");
				popupMenu.getMenu().add(Menu.NONE, 2, 2, "Go to first unread");

				popupMenu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
					@Override
					public boolean onMenuItemClick(final MenuItem menuItem) {
						popupMenu.dismiss();
						int itemId = menuItem.getItemId();
						if (itemId == 1) {
							showCalendar();
						} else if (itemId == 2) {
							gotoFirstUnread();
						}
						return true;
					}
				});
				popupMenu.show();
			}

			private void gotoFirstUnread() {
				dayNumber = findFirstUnreadDay(readingPlan.info.duration - 1);
				changeDay(0);
			}

			public void showCalendar() {Calendar calendar = Calendar.getInstance();
				calendar.setTime(new Date(readingPlan.info.startDate));
				calendar.add(Calendar.DATE, dayNumber);

				DatePickerDialog.OnDateSetListener dateSetListener = new DatePickerDialog.OnDateSetListener() {
			        @Override
			        public void onDateSet(final DatePicker view, final int year, final int monthOfYear, final int dayOfMonth) {
				        Calendar newCalendar = new GregorianCalendar(year, monthOfYear, dayOfMonth);
				        newCalendar.set(Calendar.HOUR_OF_DAY, 0);
				        newCalendar.set(Calendar.MINUTE, 1);                 //TODO: find another way to calculate difference
				        newCalendar.set(Calendar.SECOND, 0);

				        Calendar startCalendar = GregorianCalendar.getInstance();
				        startCalendar.setTime(new Date(readingPlan.info.startDate));
				        startCalendar.set(Calendar.HOUR_OF_DAY, 0);
				        startCalendar.set(Calendar.MINUTE, 0);
				        startCalendar.set(Calendar.SECOND, 0);

				        int newDay = (int) ((newCalendar.getTime().getTime() - startCalendar.getTime().getTime()) / (1000 * 60 * 60 * 24));
				        if (newDay < 0) {
					        newDay = 0;
				        } else if (newDay >= readingPlan.info.duration) {
					        newDay = readingPlan.info.duration - 1;
				        }
				        dayNumber = newDay;
				        changeDay(0);
			        }
		        };

				DatePickerDialog datePickerDialog = new DatePickerDialog(ReadingPlanActivity.this, dateSetListener, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH));
				datePickerDialog.show();
			}
		});

		bLeft.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(final View v) {
				changeDay(-1);
			}
		});

		bRight.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(final View v) {
				changeDay(+1);
			}
		});
	}

	public boolean prepareDropDownNavigation() {
		if (downloadedReadingPlanInfos.size() == 0) {
			actionBar.setDisplayShowTitleEnabled(true);
			actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_STANDARD);
			return true;
		}

		actionBar.setDisplayShowTitleEnabled(false);
		actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_LIST);

		long id = Preferences.getLong(Prefkey.active_reading_plan, 0);
		int itemNumber = 0;
		//Drop-down navigation
		List<String> titles = new ArrayList<String>();
		for (int i = 0; i < downloadedReadingPlanInfos.size(); i++) {
			ReadingPlan.ReadingPlanInfo info = downloadedReadingPlanInfos.get(i);
			titles.add(info.title);
			if (info.id == id) {
				itemNumber = i;
			}
		}

		ArrayAdapter<String> navigationAdapter = new ArrayAdapter<String>(this, R.layout.item_dropdown_reading_plan, titles);

		newDropDownItems = false;
		actionBar.setListNavigationCallbacks(navigationAdapter, new ActionBar.OnNavigationListener() {
			@Override
			public boolean onNavigationItemSelected(final int i, final long l) {
				if (newDropDownItems) {
					loadReadingPlan(downloadedReadingPlanInfos.get(i).id);
					loadReadingPlanProgress();
					prepareDisplay();
				}
				return true;
			}
		});
		actionBar.setSelectedNavigationItem(itemNumber);
		newDropDownItems = true;
		return false;
	}

	private void resetReadingPlan() {
		new AlertDialog.Builder(this)
		.setMessage("This action will shift your last fully read to yesterday.")
		.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
			@Override
			public void onClick(final DialogInterface dialog, final int which) {
				int lastUnreadDay = findFirstUnreadDay(dayNumber);
				Calendar calendar = GregorianCalendar.getInstance();
				calendar.add(Calendar.DATE, -lastUnreadDay);
				S.getDb().updateStartDate(readingPlan.info.id, calendar.getTime().getTime());
				loadReadingPlan(readingPlan.info.id);
				loadDayNumber();
				readingPlanAdapter.load();
				readingPlanAdapter.notifyDataSetChanged();

				updateButtonStatus();
			}
		})
		.setNegativeButton(R.string.cancel, null)
		.show();
	}

	private int findFirstUnreadDay(final int dayUntil) {
		int lastUnreadDay = dayUntil;

		loop1:
		for (int i = 0; i < dayUntil; i++) {
			boolean[] readMarks = new boolean[readingPlan.dailyVerses.get(i).length];
			ReadingPlanManager.writeReadMarksByDay(readingCodes, readMarks, i);
			for (boolean readMark : readMarks) {
				if (!readMark) {
					lastUnreadDay = i;
					break loop1;
				}
			}
		}
		return lastUnreadDay;
	}

	private void deleteReadingPlan() {
		new AlertDialog.Builder(this)
		.setMessage("Delete " + readingPlan.info.title + "?")
		.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
			@Override
			public void onClick(final DialogInterface dialog, final int which) {
				S.getDb().deleteReadingPlanById(readingPlan.info.id);
				readingPlan = null;
				Preferences.remove(Prefkey.active_reading_plan);
				loadReadingPlan(0);
				loadReadingPlanProgress();
				loadDayNumber();
				prepareDropDownNavigation();
				prepareDisplay();
			}
		})
		.setNegativeButton(R.string.cancel, null)
		.show();
	}

	private void changeDay(int day) {
		dayNumber += day;
		readingPlanAdapter.load();
		readingPlanAdapter.notifyDataSetChanged();

		updateButtonStatus();
	}

	private void updateButtonStatus() {            //TODO look disabled
		if (dayNumber == 0) {
			bLeft.setEnabled(false);
			bRight.setEnabled(true);
		} else if (dayNumber == readingPlan.info.duration - 1) {
			bLeft.setEnabled(true);
			bRight.setEnabled(false);
		} else {
			bLeft.setEnabled(true);
			bRight.setEnabled(true);
		}

		bToday.setText(getReadingDateHeader(dayNumber));

	}

	private void loadDayNumber() {
		if (readingPlan == null) {
			return;
		}

		todayNumber = (int) ((new Date().getTime() - readingPlan.info.startDate) / (1000 * 60 * 60 * 24));
		dayNumber = getIntent().getIntExtra(READING_PLAN_DAY_NUMBER, -1);
		if (dayNumber == -1) {
			dayNumber = todayNumber;
		}
	}

	private void downloadReadingPlan() {

		AppConfig config = AppConfig.get();
		final List<ReadingPlan.ReadingPlanInfo> infos = config.readingPlanInfos;
		final List<String> readingPlanTitles = new ArrayList<String>();

		final List<Integer> resources = new ArrayList<Integer>();
		for (int i = 0; i < infos.size(); i++) {
			String title = infos.get(i).title;
			boolean downloaded = false;
			for (ReadingPlan.ReadingPlanInfo downloadedReadingPlanInfo : downloadedReadingPlanInfos) {
				if (title.equals(downloadedReadingPlanInfo.title)) {
					downloaded = true;
					break;
				}
			}
			if (!downloaded) {
				readingPlanTitles.add(title);
				String filename = infos.get(i).filename.replace(".rpb", "");                        //TODO: proper method. testing only
				resources.add(getResources().getIdentifier(filename, "raw", getPackageName()));     //TODO: proper method
			}
		}

		if (readingPlanTitles.size() == 0) {
			new AlertDialog.Builder(this)
			.setMessage("No reading plan can be downloaded.")
			.setPositiveButton(R.string.ok, null)
			.show();
		} else {
			AlertDialog.Builder builder = new AlertDialog.Builder(this);
			builder.setAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, readingPlanTitles), new DialogInterface.OnClickListener() {
				@Override
				public void onClick(final DialogInterface dialog, final int which) {
					long id = ReadingPlanManager.copyReadingPlanToDb(resources.get(which));

					Preferences.setLong(Prefkey.active_reading_plan, id);
					loadDayNumber();
					loadReadingPlan(id);
					loadReadingPlanProgress();
					prepareDropDownNavigation();
					prepareDisplay();
					dialog.dismiss();
				}
			})
			.setNegativeButton("Cancel", null)
			.show();
		}
	}

	private void loadReadingPlan(long id) {

		downloadedReadingPlanInfos = S.getDb().listAllReadingPlanInfo();

		if (downloadedReadingPlanInfos.size() == 0) {
			return;
		}

		long startDate = 0;
		if (id == 0) {
			id = downloadedReadingPlanInfos.get(0).id;
			startDate = downloadedReadingPlanInfos.get(0).startDate;
		} else {
			for (ReadingPlan.ReadingPlanInfo info : downloadedReadingPlanInfos) {
				if (id == info.id) {
					startDate = info.startDate;
				}
			}
		}

		byte[] binaryReadingPlan = S.getDb().getBinaryReadingPlanById(id);

		InputStream inputStream = new ByteArrayInputStream(binaryReadingPlan);
		ReadingPlan res = ReadingPlanManager.readVersion1(inputStream);
		res.info.id = id;
		res.info.startDate = startDate;
		readingPlan = res;
		Preferences.setLong(Prefkey.active_reading_plan, id);
	}
	
	private void loadReadingPlanProgress() {
		if (readingPlan == null) {
			return;
		}
		readingCodes = S.getDb().getAllReadingCodesByReadingPlanId(readingPlan.info.id);
	}

	private float getActualPercentage() {
		float res = (float) countRead() / (float) countAllReadings() * 100;
		res = (float)Math.round(res * 100) / 100;
		return res;
	}

	private float getTargetPercentage() {
		float res = (float) countTarget() / (float) countAllReadings() * 100;
		res = (float)Math.round(res * 100) / 100;
		return res;
	}

	private int countRead() {
		IntArrayList filteredReadingCodes = ReadingPlanManager.filterReadingCodesByDayStartEnd(readingCodes, 0, todayNumber);
		for (int i = 0; i < filteredReadingCodes.size(); i++) {
		}
		return filteredReadingCodes.size();
	}

	private int countTarget() {
		int res = 0;
		for (int i = 0; i <= todayNumber; i++) {
			res += readingPlan.dailyVerses.get(i).length / 2;
		}
		return res;
	}

	private int countAllReadings() {
		int res = 0;
		for (int i = 0; i < readingPlan.info.duration; i++) {
			res += readingPlan.dailyVerses.get(i).length / 2;
		}
		return res;
	}

	public String getReadingDateHeader(final int dayNumber) {
		String date = "Day " + (dayNumber + 1) + ": ";
		if (readingPlan.info.version == 1) {
			Calendar calendar = Calendar.getInstance();
			calendar.setTime(new Date(readingPlan.info.startDate));
			calendar.add(Calendar.DATE, dayNumber);

			date += new SimpleDateFormat("MMMM dd, yyyy").format(calendar.getTime());
		}
		return date;
	}

	public static SpannableStringBuilder getReference(Version version, int[] ari) {
		SpannableStringBuilder sb = new SpannableStringBuilder();
		String book = version.getBook(Ari.toBook(ari[0])).shortName;
		sb.append(book);
		int startChapter = Ari.toChapter(ari[0]);
		int startVerse = Ari.toVerse(ari[0]);
		int lastVerse = Ari.toVerse(ari[1]);
		int lastChapter = Ari.toChapter(ari[1]);

		sb.append(" " + startChapter);

		if (startVerse == 0) {
			if (lastVerse == 0) {
				if (startChapter != lastChapter) {
					sb.append("-" + lastChapter);
				}
			} else {
				sb.append("-" + lastChapter + ":" + lastVerse);
			}
		} else {
			if (startChapter == lastChapter) {
				sb.append(":" + startVerse + "-" + lastVerse);
			} else {
				sb.append(":" + startVerse + "-" + lastChapter + ":" + lastVerse);
			}
		}

		return sb;
	}

	class ReadingPlanAdapter extends BaseAdapter {

		private int[] todayReadings;

		public void load() {
			todayReadings = readingPlan.dailyVerses.get(dayNumber);
		}

		@Override
		public int getCount() {
			return (todayReadings.length / 2) + readingPlan.info.duration + 1;
		}

		@Override
		public View getView(final int position, View convertView, final ViewGroup parent) {
			final int itemViewType = getItemViewType(position);

			if (itemViewType == 0) {
				CheckBox checkBox = new CheckBox(ReadingPlanActivity.this);
				LinearLayout layout = new LinearLayout(ReadingPlanActivity.this);
				layout.addView(checkBox);
				convertView = layout;

				boolean[] readMarks = new boolean[todayReadings.length];
				ReadingPlanManager.writeReadMarksByDay(readingCodes, readMarks, dayNumber);
				if (readMarks[position * 2]) {
					checkBox.setChecked(true);
				} else {
					checkBox.setChecked(false);
				}

				checkBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
					@Override
					public void onCheckedChanged(final CompoundButton buttonView, final boolean isChecked) {
						ReadingPlanManager.updateReadingPlanProgress(readingPlan.info.id, dayNumber, position, isChecked);
						loadReadingPlanProgress();
						load();
						notifyDataSetChanged();
					}
				});

				int start = position * 2;
				int[] aris = {todayReadings[start], todayReadings[start + 1]};
				checkBox.setText(getReference(S.activeVersion, aris));
				checkBox.setFocusable(false);
				LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
				checkBox.setLayoutParams(layoutParams);

			} else if (itemViewType == 1) {
				if (convertView == null) {
					convertView = getLayoutInflater().inflate(R.layout.item_reading_plan_summary, parent, false);
				}

				final ProgressBar pbReadingProgress = V.get(convertView, R.id.pbReadingProgress);
				final TextView tActual = V.get(convertView, R.id.tActual);
				final TextView tTarget = V.get(convertView, R.id.tTarget);
				final TextView tComment = V.get(convertView, R.id.tComment);

				float actualPercentage = getActualPercentage();
				float targetPercentage = getTargetPercentage();

				pbReadingProgress.setMax(100);
				pbReadingProgress.setProgress((int) actualPercentage);
				pbReadingProgress.setSecondaryProgress((int) targetPercentage);

				tActual.setText("You have finished: " + actualPercentage + "%");
				tTarget.setText("Target by today: " + targetPercentage + "%");

				String comment;
				if (actualPercentage == targetPercentage) {
					comment = "You are on schedule";
				} else {
					float diff = (float) Math.round((targetPercentage - actualPercentage) * 100) / 100;
					comment = "You are behind the schedule by " + diff + "%";
				}

				tComment.setText(comment);

			} else if (itemViewType == 2) {
				if (convertView == null) {
					convertView = getLayoutInflater().inflate(R.layout.item_reading_plan_one_day, parent, false);
				}

				final LinearLayout layout = V.get(convertView, R.id.llOneDayReadingPlan);

				final int currentViewTypePosition = position - todayReadings.length / 2 - 1;

				//Text title
				TextView tTitle = V.get(convertView, android.R.id.text1);
				tTitle.setText(getReadingDateHeader(currentViewTypePosition));

				//Text reading
				while (true) {
					final View reading = layout.findViewWithTag("reading");
					if (reading != null) {
						layout.removeView(reading);
					} else {
						break;
					}
				}

				int[] aris = readingPlan.dailyVerses.get(currentViewTypePosition);
				for (int i = 0; i < aris.length / 2; i++) {
					final int ariPosition = i;
					int[] ariStartEnd = {aris[i * 2], aris[i * 2 + 1]};
					final SpannableStringBuilder reference = getReference(S.activeVersion, ariStartEnd);
					CheckBox checkBox = new CheckBox(ReadingPlanActivity.this);
					checkBox.setText(reference);
					checkBox.setTag("reading");

					boolean[] readMarks = new boolean[aris.length];
					ReadingPlanManager.writeReadMarksByDay(readingCodes, readMarks, currentViewTypePosition);
					checkBox.setChecked(readMarks[ariPosition * 2]);
					checkBox.setFocusable(false);
					LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT);
					checkBox.setLayoutParams(layoutParams);
					checkBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
						@Override
						public void onCheckedChanged(final CompoundButton buttonView, final boolean isChecked) {
							ReadingPlanManager.updateReadingPlanProgress(readingPlan.info.id, currentViewTypePosition, ariPosition, isChecked);
							loadReadingPlanProgress();
							load();
							notifyDataSetChanged();
						}
					});
					layout.addView(checkBox);
				}
			}

			return convertView;
		}

		@Override
		public Object getItem(final int position) {
			return null;
		}

		@Override
		public long getItemId(final int position) {
			return 0;
		}

		@Override
		public int getViewTypeCount() {
			return 3;
		}

		@Override
		public int getItemViewType(final int position) {
			if (position < todayReadings.length / 2) {
				return 0;
			} else if (position == todayReadings.length / 2) {
				return 1;
			} else {
				return 2;
			}
		}
	}

}