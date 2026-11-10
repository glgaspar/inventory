package com.pessimaideia.inventory.ui.session;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import com.pessimaideia.inventory.R;
import com.pessimaideia.inventory.model.Category;
import com.pessimaideia.inventory.model.Product;
import com.pessimaideia.inventory.model.Unit;
import com.pessimaideia.inventory.ui.Formats;

import java.util.ArrayList;
import java.util.List;

/** Form for a barcode the backend does not know. Hands the filled-in Product to the Activity. */
public class NewProductDialog extends DialogFragment {
    public interface Listener {
        void onNewProduct(Product draft);
    }

    private static final String ARG_BARCODE = "barcode";

    private EditText name;
    private Spinner category;
    private Spinner unit;
    private EditText packageSize;

    public static NewProductDialog newInstance(String barcode) {
        Bundle args = new Bundle();
        args.putString(ARG_BARCODE, barcode);
        NewProductDialog dialog = new NewProductDialog();
        dialog.setArguments(args);
        return dialog;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        String barcode = requireArguments().getString(ARG_BARCODE);

        View form = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_new_product, null);
        TextView barcodeText = form.findViewById(R.id.barcode);
        barcodeText.setText(getString(R.string.new_product_barcode, barcode));
        name = form.findViewById(R.id.name);
        category = form.findViewById(R.id.category);
        unit = form.findViewById(R.id.unit);
        packageSize = form.findViewById(R.id.package_size);

        category.setAdapter(spinnerAdapter(categoryLabels()));
        unit.setAdapter(spinnerAdapter(unitLabels()));

        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle(R.string.new_product_title)
                .setView(form)
                // null here, real listener below: the default one would close the dialog even
                // when the form is invalid.
                .setPositiveButton(R.string.save, null)
                .setNegativeButton(R.string.cancel, null)
                .create();
        dialog.setOnShowListener(shown -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> save(barcode)));
        return dialog;
    }

    private void save(String barcode) {
        String productName = name.getText().toString().trim();
        Double size = Formats.parseAmount(packageSize.getText().toString());

        if (productName.isEmpty()) {
            name.setError(getString(R.string.error_name_required));
            return;
        }
        if (size == null || size <= 0) {
            packageSize.setError(getString(R.string.error_package_size));
            return;
        }

        Product draft = new Product(0, barcode, productName,
                Category.values()[category.getSelectedItemPosition()],
                Unit.values()[unit.getSelectedItemPosition()],
                size, null);
        ((Listener) requireActivity()).onNewProduct(draft);
        dismiss();
    }

    private ArrayAdapter<String> spinnerAdapter(List<String> labels) {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_item, labels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        return adapter;
    }

    private List<String> categoryLabels() {
        List<String> labels = new ArrayList<>();
        for (Category c : Category.values()) {
            labels.add(getString(c.labelRes));
        }
        return labels;
    }

    private List<String> unitLabels() {
        List<String> labels = new ArrayList<>();
        for (Unit u : Unit.values()) {
            labels.add(u.symbol);
        }
        return labels;
    }
}