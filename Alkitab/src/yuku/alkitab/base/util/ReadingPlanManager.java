package yuku.alkitab.base.util;

import android.util.Log;
import yuku.afw.App;
import yuku.alkitab.base.S;
import yuku.alkitab.base.model.ReadingPlan;
import yuku.alkitab.util.IntArrayList;
import yuku.bintex.BintexReader;
import yuku.bintex.ValueMap;

import java.io.IOException;
import java.io.InputStream;
import java.util.Date;

public class ReadingPlanManager {
	public static final String TAG = ReadingPlanManager.class.getSimpleName();

	private static final byte[] RPB_HEADER = {0x52, (byte) 0x8a, 0x61, 0x34, 0x00, (byte) 0xe0, (byte) 0xea};

	public static long copyReadingPlanToDb(final int resId) {

		ReadingPlan.ReadingPlanInfo info = new ReadingPlan.ReadingPlanInfo();
		byte[] buffer = null;

		try {

			//check the file has correct header and  infos
			InputStream stream = App.context.getResources().openRawResource(resId);
			BintexReader reader = new BintexReader(stream);

			readInfo(info, reader);
			stream.close();

			//start copying
			InputStream is = App.context.getResources().openRawResource(resId);
			buffer = new byte[is.available()];
			is.read(buffer);

			info.startDate = new Date().getTime();

		} catch (IOException e) {
			e.printStackTrace();
		}

		return S.getDb().insertReadingPlan(info, buffer);
	}

	public static void updateReadingPlanProgress(final long readingPlanId, final int dayNumber, final int readingSequence, final boolean checked) {
		int readingCode = toReadingCode(dayNumber, readingSequence);

		if (checked) {
			IntArrayList ids = S.getDb().getReadingPlanProgressId(readingPlanId, readingCode);
			if (ids.size() > 0) {
				S.getDb().deleteReadingPlanProgress(readingPlanId, readingCode);
			}
			S.getDb().insertReadingPlanProgress(readingPlanId, readingCode);
		} else {
			S.getDb().deleteReadingPlanProgress(readingPlanId, readingCode);
		}
	}

	public static ReadingPlan readVersion1(InputStream inputStream) {
		ReadingPlan readingPlan = new ReadingPlan();
		try {
			BintexReader reader = new BintexReader(inputStream);
			if (!readInfo(readingPlan.info, reader)) return null;
			if (readingPlan.info.version != 1) return null;

			int[][] dailyVerses = new int[readingPlan.info.duration][];
			int counter = 0;
			while (counter < readingPlan.info.duration) {
				int count = reader.readUint8();
				if (count == -1) {
					Log.d(TAG, "Error reading.");
					return null;
				}

				int[] aris = new int[count];
				for (int j = 0; j < count; j++) {
					aris[j] = reader.readInt();
				}
				dailyVerses[counter] = aris;
				counter++;
			}
			readingPlan.dailyVerses = dailyVerses;

			if (reader.readUint8() != 0) {
				Log.d(TAG, "No footer.");
				return null;
			}

		} catch (IOException e) {
			e.printStackTrace();
		}

		return readingPlan;
	}

	public static boolean readInfo(final ReadingPlan.ReadingPlanInfo readingPlanInfo, final BintexReader reader) throws IOException {
		byte[] headers = new byte[8];
		reader.readRaw(headers);
		for (int i = 0; i < 7; i++) {
			if (RPB_HEADER[i] != headers[i]) {
				return false;
			}
		}
		readingPlanInfo.version = headers[7];

		ValueMap map = reader.readValueSimpleMap();
		readingPlanInfo.title = map.getString("title");
		readingPlanInfo.description = map.getString("description");
		readingPlanInfo.duration = map.getInt("duration");

		return true;
	}

	public static int toReadingCode(int dayNumber, int readingSequence) {
		return dayNumber << 8 | readingSequence;
	}

	public static int toDayNumber(int readingCode) {
		return (readingCode & 0x00ffff00) >> 8;
	}

	public static int toSequence(int readingCode) {
		return (readingCode & 0x000000ff);
	}

	public static IntArrayList filterReadingCodesByDayStartEnd(IntArrayList readingCodes, int dayStart, int dayEnd) {
		IntArrayList res = new IntArrayList();
		int start = dayStart << 8;
		int end = (dayEnd + 1) << 8;
		for (int i = 0; i < readingCodes.size(); i++) {
			final int readingCode = readingCodes.get(i);
			if (readingCode >= start && readingCode < end) {
				res.add(readingCode);
			}
		}
		return res;
	}

	public static void writeReadMarksByDay(IntArrayList readingCodes, boolean[] readMarks, int dayNumber) {
		readingCodes = filterReadingCodesByDayStartEnd(readingCodes, dayNumber, dayNumber);
		for (int i = 0; i < readingCodes.size(); i++) {
			final int readingCode = readingCodes.get(i);
			final int sequence = toSequence(readingCode) * 2;
			readMarks[sequence] = true;
			readMarks[sequence + 1] = true;
		}
	}

}
