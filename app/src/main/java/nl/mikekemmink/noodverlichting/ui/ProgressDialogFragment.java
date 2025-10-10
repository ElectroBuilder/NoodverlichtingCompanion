package nl.mikekemmink.noodverlichting.ui;

import android.app.Dialog;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import java.util.Locale;

/**
 * Lifecycle-vriendelijke voortgangsdialoog met:
 * - fase (tekst)
 * - determinate ProgressBar (percentage + teller x/y)
 * - Annuleren-knop via OnCancelRequested.
 */
public class ProgressDialogFragment extends DialogFragment {

    public interface OnCancelRequested { void onCancelRequested(); }

    private static final String ARG_TITLE   = "title";
    private static final String ARG_MESSAGE = "message";

    private TextView phaseView;
    private TextView percentView;
    private ProgressBar progressBar;

    public static ProgressDialogFragment newInstance(@Nullable String title, @Nullable String message) {
        ProgressDialogFragment f = new ProgressDialogFragment();
        Bundle b = new Bundle();
        b.putString(ARG_TITLE, title);
        b.putString(ARG_MESSAGE, message);
        f.setArguments(b);
        f.setCancelable(false);
        return f;
    }

    @NonNull @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        String title = getArguments() != null ? getArguments().getString(ARG_TITLE) : null;
        String message = getArguments() != null ? getArguments().getString(ARG_MESSAGE) : "Bezig…";

        int pad = (int) (getResources().getDisplayMetrics().density * 20);

        LinearLayout root = new LinearLayout(requireContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);
        root.setClipToPadding(true);

        phaseView = new TextView(requireContext());
        phaseView.setText(message);
        phaseView.setTextSize(16f);

        progressBar = new ProgressBar(requireContext(), null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setIndeterminate(true);
        progressBar.setMax(100);

        percentView = new TextView(requireContext());
        percentView.setText(" ");
        percentView.setGravity(Gravity.END);
        percentView.setTextSize(13f);

        root.addView(phaseView);
        root.addView(progressBar);
        root.addView(percentView);

        AlertDialog.Builder b = new AlertDialog.Builder(requireContext())
                .setView(root)
                .setCancelable(false)
                .setNegativeButton("Annuleren", (d, w) -> {
                    if (getActivity() instanceof OnCancelRequested) {
                        ((OnCancelRequested) getActivity()).onCancelRequested();
                    }
                });

        if (title != null && !title.isEmpty()) b.setTitle(title);
        return b.create();
    }

    /** Backwards-compat: alleen tekst updaten (indeterminate modus) */
    public void updateMessage(@Nullable String msg) {
        if (phaseView != null) phaseView.setText(msg != null ? msg : "Bezig…");
    }

    /** Volledige update: fase + (cur/total) + %  */
    public void updateProgress(@NonNull String phase, int current, int total) {
        if (phaseView == null || progressBar == null || percentView == null) return;

        phaseView.setText(phase);

        if (total <= 0) {
            // onbekend totaal → indeterminate
            progressBar.setIndeterminate(true);
            percentView.setText(" ");
        } else {
            progressBar.setIndeterminate(false);
            int pct = Math.max(0, Math.min(100, (int) Math.floor((current * 100f) / total)));
            progressBar.setProgress(pct);
            percentView.setText(String.format(Locale.getDefault(), "%d%%  (%d/%d)", pct, current, total));
        }
    }
}