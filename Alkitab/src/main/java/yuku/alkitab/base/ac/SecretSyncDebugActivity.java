package yuku.alkitab.base.ac;

import android.app.AlertDialog;
import android.os.Bundle;
import android.util.Pair;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import com.google.gson.Gson;
import com.squareup.okhttp.Call;
import com.squareup.okhttp.Callback;
import com.squareup.okhttp.FormEncodingBuilder;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.RequestBody;
import com.squareup.okhttp.Response;
import yuku.afw.V;
import yuku.afw.storage.Preferences;
import yuku.alkitab.base.App;
import yuku.alkitab.base.S;
import yuku.alkitab.base.U;
import yuku.alkitab.base.ac.base.BaseActivity;
import yuku.alkitab.base.model.SyncShadow;
import yuku.alkitab.base.storage.InternalDb;
import yuku.alkitab.base.storage.Prefkey;
import yuku.alkitab.base.sync.Sync;
import yuku.alkitab.base.util.Sqlitil;
import yuku.alkitab.debug.R;
import yuku.alkitab.model.Label;
import yuku.alkitab.model.Marker;
import yuku.alkitab.model.Marker_Label;
import yuku.alkitab.model.util.Gid;

import java.io.IOException;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SecretSyncDebugActivity extends BaseActivity {
	public static final String TAG = SecretSyncDebugActivity.class.getSimpleName();

	EditText tServer;
	Button bServerSave;
	Button bServerReset;
	TextView tUser;
	EditText tUserEmail;
	CheckBox cMakeDirtyMarker;
	CheckBox cMakeDirtyLabel;
	CheckBox cMakeDirtyMarker_Label;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_secret_sync_debug);

		tServer = V.get(this, R.id.tServer);
		tUser = V.get(this, R.id.tUser);
		tUserEmail = V.get(this, R.id.tUserEmail);
		cMakeDirtyMarker = V.get(this, R.id.cMakeDirtyMarker);
		cMakeDirtyLabel = V.get(this, R.id.cMakeDirtyLabel);
		cMakeDirtyMarker_Label = V.get(this, R.id.cMakeDirtyMarker_Label);

		V.get(this, R.id.bServerSave).setOnClickListener(v -> new AlertDialog.Builder(this)
			.setMessage("This will reset your synced shadow to revision 0.")
			.setPositiveButton(R.string.ok, (d, w) -> {
				Preferences.setString(Prefkey.sync_server_prefix, tServer.getText().toString().trim());

				// do the same as logging out
				bLogout_click.onClick(null);
			})
			.setNegativeButton(R.string.cancel, null)
			.show());

		V.get(this, R.id.bServerReset).setOnClickListener(v -> new AlertDialog.Builder(this)
			.setMessage("This will reset your synced shadow to revision 0.")
			.setPositiveButton(R.string.ok, (d, w) -> {
				Preferences.remove(Prefkey.sync_server_prefix);

				// do the same as logging out
				bLogout_click.onClick(null);

				tServer.setText("");
			})
			.setNegativeButton(R.string.cancel, null)
			.show());

		V.get(this, R.id.bMabelClientState).setOnClickListener(bMabelClientState_click);
		V.get(this, R.id.bGenerateDummies).setOnClickListener(bGenerateDummies_click);
		V.get(this, R.id.bRegisterNewUser).setOnClickListener(bRegisterNewUser_click);
		V.get(this, R.id.bGetAuthToken).setOnClickListener(bGetAuthToken_click);
		V.get(this, R.id.bLogout).setOnClickListener(bLogout_click);
		V.get(this, R.id.bSync).setOnClickListener(bSync_click);

		displayUser();
	}

	View.OnClickListener bMabelClientState_click = v -> {
		final StringBuilder sb = new StringBuilder();
		final Pair<Sync.MabelClientState, List<Sync.Entity<Sync.MabelContent>>> pair = Sync.getMabelClientStateAndCurrentEntities();
		final Sync.MabelClientState clientState = pair.first;

		sb.append("Base revno: ").append(clientState.base_revno).append('\n');
		sb.append("Delta operations: \n");

		for (final Sync.Operation<Sync.MabelContent> operation : clientState.delta.operations) {
			sb.append("\u2022 ").append(operation).append('\n');
		}

		new AlertDialog.Builder(this)
			.setMessage(sb)
			.setPositiveButton(R.string.ok, null)
			.show();
	};

	int rand(int n) {
		return (int) (Math.random() * n);
	}

	View.OnClickListener bGenerateDummies_click = v -> {
		final Label label1 = S.getDb().insertLabel(randomString("L1_", 1, 3, 8), U.encodeLabelBackgroundColor(rand(0xffffff)));
		final Label label2 = S.getDb().insertLabel(randomString("L2_", 1, 3, 8), U.encodeLabelBackgroundColor(rand(0xffffff)));

		for (int i = 0; i < 10; i++) {
			final Marker marker = S.getDb().insertMarker(0x000101 + rand(30), Marker.Kind.values()[rand(3)], randomString("M" + i + "_", rand(2) + 1, 4, 7), rand(2) + 1, new Date(), new Date());
			final Set<Label> labelSet = new HashSet<>();
			if (rand(10) < 5) {
				labelSet.add(label1);
			}
			if (rand(10) < 3) {
				labelSet.add(label2);
			}
			S.getDb().updateLabels(marker, labelSet);
		}

		new AlertDialog.Builder(this)
			.setMessage("10 markers, 2 labels generated.")
			.setPositiveButton(R.string.ok, null)
			.show();
	};

	private String randomString(final String prefix, final int word_count, final int minwordlen, final int maxwordlen) {
		final StringBuilder sb = new StringBuilder(prefix);
		for (int i = 0; i < word_count; i++) {
			for (int j = 0, wordlen = rand(maxwordlen - minwordlen) + minwordlen; j < wordlen; j++) {
				if (j % 2 == 0) {
					sb.append("bcdfghjklmnpqrstvwyz".charAt(rand(20)));
				} else {
					sb.append("aeiou".charAt(rand(5)));
				}
			}
			if (i != word_count - 1) {
				sb.append(' ');
			}
		}
		return sb.toString();
	}

	static class ResponseJson {
		public boolean success;
		public String message;
	}

	static class UserJson {
		public String email;
	}

	static class DebugCreateUserResponseJson extends ResponseJson {
		public UserJson user;
		public String simpleToken;
	}

	View.OnClickListener bRegisterNewUser_click = v -> {
		final Call call = App.downloadCall(Sync.getEffectiveServerPrefix() + "/sync/api/debug_create_user");
		call.enqueue(new Callback() {
			@Override
			public void onFailure(final Request request, final IOException e) {
				runOnUiThread(() -> new AlertDialog.Builder(SecretSyncDebugActivity.this)
					.setMessage("Error: " + e.getMessage())
					.setPositiveButton(R.string.ok, null)
					.show());
			}

			@Override
			public void onResponse(final Response response) throws IOException {
				final DebugCreateUserResponseJson debugCreateUserResponse = new Gson().fromJson(response.body().charStream(), DebugCreateUserResponseJson.class);
				runOnUiThread(() -> {
					if (debugCreateUserResponse.success) {
						final String email = debugCreateUserResponse.user.email;
						final String simpleToken = debugCreateUserResponse.simpleToken;

						Preferences.hold();
						Preferences.setString(Prefkey.sync_user_email, email);
						Preferences.setString(Prefkey.sync_simpleToken, simpleToken);
						Preferences.setInt(Prefkey.sync_token_obtained_time, Sqlitil.nowDateTime());
						Preferences.unhold();

						displayUser();

						new AlertDialog.Builder(SecretSyncDebugActivity.this)
							.setMessage("User email: " + email + "\nsimpleToken: " + simpleToken)
							.setPositiveButton(R.string.ok, null)
							.show();
					} else {
						new AlertDialog.Builder(SecretSyncDebugActivity.this)
							.setMessage(debugCreateUserResponse.message)
							.setPositiveButton(R.string.ok, null)
							.show();
					}
				});
			}
		});
	};

	static class DebugGetAuthTokenResponseJson extends ResponseJson {
		public String simpleToken;
	}

	View.OnClickListener bGetAuthToken_click = v -> {
		final String user_email = tUserEmail.getText().toString();

		final RequestBody requestBody = new FormEncodingBuilder()
			.add("user_email", user_email)
			.build();

		final Call call = App.getOkHttpClient().newCall(
			new Request.Builder()
				.url(Sync.getEffectiveServerPrefix() + "/sync/api/debug_get_auth_token")
				.post(requestBody)
				.build()
		);

		call.enqueue(new Callback() {
			@Override
			public void onFailure(final Request request, final IOException e) {
				runOnUiThread(() -> new AlertDialog.Builder(SecretSyncDebugActivity.this)
					.setMessage("Error: " + e.getMessage())
					.setPositiveButton(R.string.ok, null)
					.show());
			}

			@Override
			public void onResponse(final Response response) throws IOException {
				final DebugGetAuthTokenResponseJson debugCreateUserResponse = new Gson().fromJson(response.body().charStream(), DebugGetAuthTokenResponseJson.class);
				runOnUiThread(() -> {
					if (debugCreateUserResponse.success) {
						final String simpleToken = debugCreateUserResponse.simpleToken;

						Preferences.hold();
						Preferences.setString(Prefkey.sync_user_email, user_email);
						Preferences.setString(Prefkey.sync_simpleToken, simpleToken);
						Preferences.setInt(Prefkey.sync_token_obtained_time, Sqlitil.nowDateTime());
						Preferences.unhold();

						displayUser();

						new AlertDialog.Builder(SecretSyncDebugActivity.this)
							.setMessage("User email: " + user_email + "\nsimpleToken: " + simpleToken)
							.setPositiveButton(R.string.ok, null)
							.show();
					} else {
						new AlertDialog.Builder(SecretSyncDebugActivity.this)
							.setMessage(debugCreateUserResponse.message)
							.setPositiveButton(R.string.ok, null)
							.show();
					}
				});
			}
		});

	};

	void displayUser() {
		tUser.setText(Preferences.getString(Prefkey.sync_user_email, "not logged in") + ": " + Preferences.getString(Prefkey.sync_simpleToken));
	}

	View.OnClickListener bLogout_click = v -> {
		Preferences.hold();
		Preferences.remove(Prefkey.sync_user_email);
		Preferences.remove(Prefkey.sync_simpleToken);
		Preferences.remove(Prefkey.sync_token_obtained_time);
		Preferences.unhold();

		S.getDb().deleteSyncShadowBySyncSetName(SyncShadow.SYNC_SET_MABEL);

		displayUser();
	};

	public static class DebugSyncResponseJson extends ResponseJson {
		public int final_revno;
		public Sync.Delta<Sync.MabelContent> append_delta;
	}

	View.OnClickListener bSync_click = v -> {
		final String simpleToken = Preferences.getString(Prefkey.sync_simpleToken);
		if (simpleToken == null) {
			new AlertDialog.Builder(this)
				.setMessage("not logged in")
				.setPositiveButton(R.string.ok, null)
				.show();
			return;
		}

		final Pair<Sync.MabelClientState, List<Sync.Entity<Sync.MabelContent>>> pair = Sync.getMabelClientStateAndCurrentEntities();
		final Sync.MabelClientState clientState = pair.first;
		final List<Sync.Entity<Sync.MabelContent>> entitiesBeforeSync = pair.second;

		final RequestBody requestBody = new FormEncodingBuilder()
			.add("simpleToken", simpleToken)
			.add("syncSetName", SyncShadow.SYNC_SET_MABEL)
			.add("clientState", new Gson().toJson(clientState))
			.build();

		final Call call = App.getOkHttpClient().newCall(
			new Request.Builder()
				.url(Sync.getEffectiveServerPrefix() + "/sync/api/debug_sync")
				.post(requestBody)
				.build()
		);

		if (cMakeDirtyMarker.isChecked()) {
			S.getDb().insertMarker(0x000101 + rand(30), Marker.Kind.values()[rand(3)], randomString("MMD0_", rand(2) + 1, 4, 7), rand(2) + 1, new Date(), new Date());
		}

		if (cMakeDirtyLabel.isChecked()) {
			S.getDb().insertLabel(randomString("LMD_", 1, 3, 8), U.encodeLabelBackgroundColor(rand(0xffffff)));
		}

		if (cMakeDirtyMarker_Label.isChecked()) {
			final List<Label> labels = S.getDb().listAllLabels();
			final List<Marker> markers = S.getDb().listAllMarkers();
			if (labels.size() > 0 && markers.size() > 0) {
				final Marker_Label marker_label = Marker_Label.createEmptyMarker_Label();
				marker_label.gid = Gid.newGid();
				marker_label.marker_gid = markers.get(0).gid;
				marker_label.label_gid = labels.get(0).gid;
				S.getDb().insertOrUpdateMarker_Label(marker_label);
			} else {
				new AlertDialog.Builder(this)
					.setMessage("not enough markers and labels to create marker_label")
					.setPositiveButton(R.string.ok, null)
					.show();
				return;
			}
		}

		call.enqueue(new Callback() {
			@Override
			public void onFailure(final Request request, final IOException e) {
				runOnUiThread(() -> new AlertDialog.Builder(SecretSyncDebugActivity.this)
					.setMessage("Error: " + e.getMessage())
					.setPositiveButton(R.string.ok, null)
					.show());
			}

			@Override
			public void onResponse(final Response response) throws IOException {
				final DebugSyncResponseJson debugSyncResponse = new Gson().fromJson(response.body().charStream(), DebugSyncResponseJson.class);
				runOnUiThread(() -> {
					if (debugSyncResponse.success) {
						final int final_revno = debugSyncResponse.final_revno;
						final Sync.Delta<Sync.MabelContent> append_delta = debugSyncResponse.append_delta;

						final InternalDb.ApplyAppendDeltaResult applyResult = S.getDb().applyAppendDelta(final_revno, append_delta, entitiesBeforeSync);

						new AlertDialog.Builder(SecretSyncDebugActivity.this)
							.setMessage("Final revno: " + final_revno + "\nApply result: " + applyResult + "\nAppend delta: " + append_delta)
							.setPositiveButton(R.string.ok, null)
							.show();
					} else {
						new AlertDialog.Builder(SecretSyncDebugActivity.this)
							.setMessage(debugSyncResponse.message)
							.setPositiveButton(R.string.ok, null)
							.show();
					}
				});
			}
		});
	};
}
