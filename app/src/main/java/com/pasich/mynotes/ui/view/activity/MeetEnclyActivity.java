package com.pasich.mynotes.ui.view.activity;

import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.pasich.mynotes.R;
import com.pasich.mynotes.base.activity.BaseActivity;
import com.pasich.mynotes.databinding.ActivityMeetEnclyBinding;
import com.pasich.mynotes.utils.constants.SnackBarInfo;
import com.pasich.mynotes.utils.encly.AndroidEnclyInspector;
import com.pasich.mynotes.utils.encly.EnclyHandoff;
import com.pasich.mynotes.utils.encly.EnclyMigrationRepository;
import com.pasich.mynotes.utils.encly.EnclyReceiverCheck;
import com.pasich.mynotes.utils.encly.HandoffPayloadBuilder;
import com.pasich.mynotes.utils.encly.HandoffResult;
import dagger.hilt.android.AndroidEntryPoint;
import io.reactivex.Completable;
import io.reactivex.Single;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.schedulers.Schedulers;
import java.io.File;
import java.util.Objects;
import javax.inject.Inject;

/**
 * "Meet Encly": introduces the successor app and hands the whole library over to it, one way
 * (pasichDev/MyNotes#167). Nothing is deleted here unless the user asks for it as a separate,
 * confirmed step after Encly reported a successful import.
 */
@AndroidEntryPoint
public class MeetEnclyActivity extends BaseActivity {

    private static final String TAG = "MeetEnclyActivity";
    private static final String STATE_HAS_RESULT = "encly_has_result";
    private static final String STATE_RESULT_CODE = "encly_result_code";
    private static final String STATE_NOTES = "encly_notes";
    private static final String STATE_TASKS = "encly_tasks";
    private static final String STATE_TAGS = "encly_tags";
    private static final String STATE_SKIPPED = "encly_skipped";
    private static final String STATE_REASON = "encly_reason";
    private static final String STATE_CLEARED = "encly_cleared";

    @Inject EnclyMigrationRepository repository;

    private final CompositeDisposable disposables = new CompositeDisposable();
    private ActivityMeetEnclyBinding binding;

    @Nullable private EnclyReceiverCheck.Result receiver;
    @Nullable private HandoffPayloadBuilder.Summary summary;
    private int categories;
    private boolean busy;

    @Nullable private HandoffResult result;
    private int resultCode;
    private int[] resultCounts = new int[4];
    @Nullable private String resultReason;
    private boolean cleared;

    private final ActivityResultLauncher<Intent> enclyLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(), this::onEnclyResult);

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        selectTheme();
        binding = ActivityMeetEnclyBinding.inflate(getLayoutInflater());
        setupEdgeToEdgeInsets(binding.getRoot());
        setContentView(binding.getRoot());
        setSupportActionBar(binding.toolbar);
        Objects.requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(true);

        if (savedInstanceState == null) {
            // A hand-off file left behind by a crash or a killed process is plain text: sweep it.
            clearArchivesInBackground();
        } else {
            restoreResult(savedInstanceState);
        }
        initListeners();
        loadSummary();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // The user may have just installed or updated Encly from the store.
        refreshReceiver();
        render();
    }

    @Override
    public void initListeners() {
        binding.primaryButton.setOnClickListener(v -> onPrimaryClicked());
        binding.clearButton.setOnClickListener(v -> confirmClear());
    }

    private void refreshReceiver() {
        receiver = EnclyReceiverCheck.check(new AndroidEnclyInspector(this, probeUri()));
    }

    @NonNull
    private Uri probeUri() {
        return Uri.parse("content://" + authority() + "/encly_handoff/handoff.zip");
    }

    @NonNull
    private String authority() {
        return getPackageName() + ".provider";
    }

    private void loadSummary() {
        disposables.add(
                Single.fromCallable(repository::load)
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(
                                library -> {
                                    summary = library.summary();
                                    categories = library.categories.size();
                                    render();
                                },
                                error -> Log.e(TAG, "load summary", error)));
    }

    private void render() {
        if (binding == null) return;
        if (summary != null) {
            binding.movesText.setText(
                    String.join(
                            "\n",
                            getString(R.string.meet_encly_moves_notes, summary.notes),
                            getString(R.string.meet_encly_moves_tasks, summary.tasks),
                            getString(R.string.meet_encly_moves_tags, summary.tags),
                            getString(R.string.meet_encly_moves_categories, categories)));
            binding.staysText.setText(
                    String.join(
                            "\n",
                            getString(R.string.meet_encly_stays_attachments, summary.attachments),
                            getString(R.string.meet_encly_stays_reminders, summary.reminders),
                            getString(R.string.meet_encly_stays_pinned, summary.pinned)));
        }

        EnclyReceiverCheck.Status status =
                receiver == null ? EnclyReceiverCheck.Status.NOT_INSTALLED : receiver.status;
        switch (status) {
            case READY -> {
                binding.primaryButton.setText(R.string.meet_encly_move);
                binding.statusText.setVisibility(View.GONE);
            }
            case NEEDS_UPDATE -> {
                binding.primaryButton.setText(R.string.meet_encly_update);
                binding.statusText.setText(R.string.meet_encly_needs_update);
                binding.statusText.setVisibility(View.VISIBLE);
            }
            case UNTRUSTED -> {
                binding.primaryButton.setText(R.string.meet_encly_get);
                binding.statusText.setText(R.string.meet_encly_untrusted);
                binding.statusText.setVisibility(View.VISIBLE);
            }
            default -> {
                binding.primaryButton.setText(R.string.meet_encly_get);
                binding.statusText.setVisibility(View.GONE);
            }
        }
        binding.primaryButton.setEnabled(!busy);
        binding.progress.setVisibility(busy ? View.VISIBLE : View.GONE);
        renderResult();
    }

    private void renderResult() {
        if (result == null) {
            binding.resultCard.setVisibility(View.GONE);
            return;
        }
        binding.resultCard.setVisibility(View.VISIBLE);
        if (result.success) {
            String text =
                    getString(
                            R.string.meet_encly_result_ok,
                            result.notes,
                            result.tasks,
                            result.tags,
                            result.skipped);
            if (summary != null && summary.attachments > 0) {
                text +=
                        "\n"
                                + getString(
                                        R.string.meet_encly_result_attachments,
                                        summary.attachments);
            }
            binding.resultText.setText(text);
            binding.clearHint.setVisibility(cleared ? View.GONE : View.VISIBLE);
            binding.clearButton.setVisibility(cleared ? View.GONE : View.VISIBLE);
            binding.clearButton.setEnabled(!busy);
        } else {
            binding.resultText.setText(failureMessage(result.reason));
            binding.clearHint.setVisibility(View.GONE);
            binding.clearButton.setVisibility(View.GONE);
        }
    }

    private int failureMessage(@Nullable HandoffResult.Reason reason) {
        if (reason == null) return R.string.meet_encly_result_cancelled;
        return switch (reason) {
            case CANCELLED -> R.string.meet_encly_result_cancelled;
            case UNTRUSTED_CALLER -> R.string.meet_encly_result_untrusted;
            case UNSUPPORTED_SCHEMA -> R.string.meet_encly_result_unsupported;
            case INVALID_PAYLOAD -> R.string.meet_encly_result_invalid;
            case TOO_LARGE -> R.string.meet_encly_result_too_large;
            case FAILED -> R.string.meet_encly_result_failed;
        };
    }

    private void onPrimaryClicked() {
        if (busy) return;
        refreshReceiver();
        if (receiver != null && receiver.status == EnclyReceiverCheck.Status.READY) {
            startHandoff(Objects.requireNonNull(receiver.activityClassName));
        } else {
            render();
            openStore();
        }
    }

    private void openStore() {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(EnclyHandoff.MARKET_URI)));
        } catch (ActivityNotFoundException noStore) {
            try {
                startActivity(
                        new Intent(Intent.ACTION_VIEW, Uri.parse(EnclyHandoff.WEB_STORE_URI)));
            } catch (ActivityNotFoundException noBrowser) {
                Log.w(TAG, "No app can open the store page", noBrowser);
            }
        }
    }

    private void startHandoff(@NonNull String activityClassName) {
        busy = true;
        result = null;
        render();
        disposables.add(
                Single.fromCallable(
                                () -> {
                                    repository.clearArchives();
                                    return repository.writeArchive(repository.load());
                                })
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(
                                file -> launchEncly(file, activityClassName),
                                error -> {
                                    Log.e(TAG, "prepare hand-off", error);
                                    clearArchivesInBackground();
                                    busy = false;
                                    render();
                                    onInfoSnack(
                                            R.string.meet_encly_error_prepare,
                                            null,
                                            SnackBarInfo.Error,
                                            Snackbar.LENGTH_LONG);
                                }));
    }

    private void launchEncly(@NonNull File file, @NonNull String activityClassName) {
        try {
            Uri uri = FileProvider.getUriForFile(this, authority(), file);
            Intent intent =
                    new Intent(EnclyHandoff.ACTION_IMPORT)
                            .setComponent(
                                    new ComponentName(
                                            EnclyHandoff.ENCLY_PACKAGE, activityClassName))
                            .setDataAndType(uri, EnclyHandoff.MIME_TYPE)
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            // For a result: Encly identifies the sender by getCallingPackage(), which is only set
            // for an activity started for a result.
            enclyLauncher.launch(intent);
        } catch (ActivityNotFoundException | IllegalArgumentException | SecurityException e) {
            Log.e(TAG, "launch Encly", e);
            clearArchivesInBackground();
            onInfoSnack(
                    R.string.meet_encly_error_prepare,
                    null,
                    SnackBarInfo.Error,
                    Snackbar.LENGTH_LONG);
        } finally {
            busy = false;
            render();
        }
    }

    private void onEnclyResult(@NonNull ActivityResult activityResult) {
        // Whatever Encly answered, the plain-text copy goes now.
        clearArchivesInBackground();
        Intent data = activityResult.getData();
        Bundle extras = data == null ? null : data.getExtras();
        resultCode = activityResult.getResultCode();
        resultCounts =
                new int[] {
                    intOr(extras, EnclyHandoff.EXTRA_NOTES),
                    intOr(extras, EnclyHandoff.EXTRA_TASKS),
                    intOr(extras, EnclyHandoff.EXTRA_TAGS),
                    intOr(extras, EnclyHandoff.EXTRA_SKIPPED)
                };
        resultReason = extras == null ? null : extras.getString(EnclyHandoff.EXTRA_REASON);
        result = parse(extras);
        cleared = false;
        busy = false;
        render();
    }

    @NonNull
    private HandoffResult parse(@Nullable Bundle extras) {
        return HandoffResult.parse(
                resultCode,
                intExtra(extras, EnclyHandoff.EXTRA_NOTES),
                intExtra(extras, EnclyHandoff.EXTRA_TASKS),
                intExtra(extras, EnclyHandoff.EXTRA_TAGS),
                intExtra(extras, EnclyHandoff.EXTRA_SKIPPED),
                resultReason);
    }

    @Nullable
    private static Integer intExtra(@Nullable Bundle extras, @NonNull String key) {
        if (extras == null || !extras.containsKey(key)) return null;
        return extras.getInt(key);
    }

    private static int intOr(@Nullable Bundle extras, @NonNull String key) {
        Integer value = intExtra(extras, key);
        return value == null ? -1 : value;
    }

    private void confirmClear() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.meet_encly_clear_confirm_title)
                .setMessage(R.string.meet_encly_clear_confirm_message)
                .setNegativeButton(R.string.cancel, (dialog, which) -> dialog.dismiss())
                .setPositiveButton(
                        R.string.meet_encly_clear_confirm,
                        (dialog, which) -> {
                            dialog.dismiss();
                            clearMyNotes();
                        })
                .show();
    }

    private void clearMyNotes() {
        busy = true;
        render();
        disposables.add(
                Completable.fromAction(repository::clearAllData)
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(
                                () -> {
                                    busy = false;
                                    cleared = true;
                                    loadSummary();
                                    render();
                                    onInfoSnack(
                                            R.string.meet_encly_cleared,
                                            null,
                                            SnackBarInfo.Success,
                                            Snackbar.LENGTH_LONG);
                                },
                                error -> {
                                    Log.e(TAG, "clear My Notes", error);
                                    busy = false;
                                    render();
                                    onInfoSnack(
                                            R.string.meet_encly_clear_failed,
                                            null,
                                            SnackBarInfo.Error,
                                            Snackbar.LENGTH_LONG);
                                }));
    }

    private void clearArchivesInBackground() {
        disposables.add(
                Completable.fromAction(repository::clearArchives)
                        .subscribeOn(Schedulers.io())
                        .subscribe(() -> {}, error -> Log.e(TAG, "clear hand-off files", error)));
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(STATE_HAS_RESULT, result != null);
        outState.putInt(STATE_RESULT_CODE, resultCode);
        outState.putInt(STATE_NOTES, resultCounts[0]);
        outState.putInt(STATE_TASKS, resultCounts[1]);
        outState.putInt(STATE_TAGS, resultCounts[2]);
        outState.putInt(STATE_SKIPPED, resultCounts[3]);
        outState.putString(STATE_REASON, resultReason);
        outState.putBoolean(STATE_CLEARED, cleared);
    }

    private void restoreResult(@NonNull Bundle state) {
        cleared = state.getBoolean(STATE_CLEARED, false);
        if (!state.getBoolean(STATE_HAS_RESULT, false)) return;
        resultCode = state.getInt(STATE_RESULT_CODE);
        resultCounts =
                new int[] {
                    state.getInt(STATE_NOTES, -1),
                    state.getInt(STATE_TASKS, -1),
                    state.getInt(STATE_TAGS, -1),
                    state.getInt(STATE_SKIPPED, -1)
                };
        resultReason = state.getString(STATE_REASON);
        result =
                HandoffResult.parse(
                        resultCode,
                        orNull(resultCounts[0]),
                        orNull(resultCounts[1]),
                        orNull(resultCounts[2]),
                        orNull(resultCounts[3]),
                        resultReason);
    }

    @Nullable
    private static Integer orNull(int value) {
        return value < 0 ? null : value;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        disposables.clear();
        super.onDestroy();
    }
}
