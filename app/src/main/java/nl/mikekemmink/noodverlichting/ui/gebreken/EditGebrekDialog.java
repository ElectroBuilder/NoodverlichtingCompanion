package nl.mikekemmink.noodverlichting.ui.gebreken;

import android.app.Dialog;
import android.os.Bundle;
import android.widget.EditText;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

public class EditGebrekDialog extends DialogFragment {

    public interface OnSaveListener {
        void onSave(String nieuweOmschrijving);
    }

    private static final String ARG_OMS = "arg_oms";

    public static EditGebrekDialog newInstance(String omschrijving) {
        Bundle b = new Bundle();
        b.putString(ARG_OMS, omschrijving);
        EditGebrekDialog d = new EditGebrekDialog();
        d.setArguments(b);
        return d;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        EditText input = new EditText(requireContext());
        if (getArguments() != null) {
            input.setText(getArguments().getString(ARG_OMS, ""));
        }
        return new AlertDialog.Builder(requireContext())
                .setTitle("Gebrek bewerken")
                .setView(input)
                .setPositiveButton("Opslaan", (dlg, w) -> {
                    if (getParentFragment() instanceof OnSaveListener) {
                        ((OnSaveListener) getParentFragment()).onSave(input.getText().toString());
                    } else if (getActivity() instanceof OnSaveListener) {
                        ((OnSaveListener) getActivity()).onSave(input.getText().toString());
                    }
                })
                .setNegativeButton("Annuleren", null)
                .create();
    }
}
