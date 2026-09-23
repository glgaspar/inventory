# Step 7 — Register an unknown product (`NewProductDialog`)

Goal: scanning a barcode the backend does not know opens a form (name, category, unit, amount per
package). **Save** creates the product (`POST /products`, mocked) and adds it to the session as
if it had been scanned. **Cancel** closes the form and nothing changes.

Done when scanning `1111` opens *New product*, saving with an empty name or amount keeps the form
open with an error, and a valid save adds a row like **Leite · 1 × 1.5 L = 1.5 L**.

Notes:
- The mock keeps new products **in memory only**. After the app restarts, `1111` is unknown to the
  API again. The session file still has the row (it stores the whole `Product`), so the list is
  fine; you would only see the form again if you scan `1111` in a *new* session.
- A scan while the form is open types into the focused field (usually *Name*), because whatever
  has focus gets the wedge's keystrokes. Harmless: you see it and delete it.
- `ScannerInput.requestFocus()` says "call after a dialog closes". Testing showed Android 4.2
  already returns focus to the hidden field when the dialog closes, so no extra call is needed.

## Concepts you meet in this step

| Concept | One-liner |
|---|---|
| Fragment | A reusable piece of UI with its own lifecycle, living inside an Activity. Android may destroy and **re-create** it on its own (e.g. after the process was killed), using the no-argument constructor. |
| `DialogFragment` | A Fragment that shows a dialog. Preferred over a bare `AlertDialog` because it survives being re-created; `show(fragmentManager, tag)` displays it. |
| Arguments `Bundle` | Since Android re-creates Fragments with the empty constructor, parameters go in `setArguments(bundle)` and are read back with `requireArguments()`. The `newInstance(...)` static factory is the usual way to do that. |
| Casting to an interface | `((Listener) requireActivity()).onNewProduct(draft)`: the dialog only knows "my Activity implements `Listener`". If it does not, you get a `ClassCastException` right away. |
| `setView` | Puts your own layout inside an `AlertDialog`, between the title and the buttons. |
| Keeping a dialog open | `setPositiveButton(text, null)` + your own click listener on `getButton(BUTTON_POSITIVE)` in `setOnShowListener`. The default listener always closes the dialog, even when the form is wrong. |
| `setError` | `editText.setError("...")` shows a red marker and a popup on that field. Cleared automatically when the user types. |
| `Spinner` + `ArrayAdapter` | A drop-down. `ArrayAdapter` turns a `List<String>` into rows using built-in layouts (`android.R.layout.simple_spinner_item`). The selected **position** maps back to the enum with `values()[position]`. |
| `enum.values()` | Array of all constants in declaration order. Same order every time, which is what makes position ↔ enum safe. |
| `Double` vs `double` | `double` is a plain number and can never be null. `Double` is an object and can be, which lets `parseAmount` say "not a number" by returning `null`. |
| `NumberFormatException` | What `Double.parseDouble("abc")` throws. Caught and turned into `null`. |
| `isStateSaved()` | After the Activity went to the background, showing a Fragment throws `IllegalStateException: Can not perform this action after onSaveInstanceState`. Checking first avoids it. |

## Files

### `res/values/strings.xml` — remove `scanned_toast` and `scan_unknown`, add

```xml
    <string name="new_product_title">New product</string>
    <string name="new_product_barcode">Barcode: %1$s</string>
    <string name="new_product_name">Name</string>
    <string name="new_product_category">Category</string>
    <string name="new_product_unit">Unit</string>
    <string name="new_product_package_size">Amount per package</string>
    <string name="save">Save</string>
    <string name="error_name_required">Enter a name</string>
    <string name="error_package_size">Enter a number greater than 0</string>
    <string name="create_failed">Could not save the product: %1$s</string>
```

`scan_unknown` was the Step 6 toast; the dialog replaces it.

### `res/values-pt-rBR/strings.xml` — remove `scan_unknown`, add

```xml
    <string name="new_product_title">Novo produto</string>
    <string name="new_product_barcode">Código: %1$s</string>
    <string name="new_product_name">Nome</string>
    <string name="new_product_category">Categoria</string>
    <string name="new_product_unit">Unidade</string>
    <string name="new_product_package_size">Quantidade por pacote</string>
    <string name="save">Salvar</string>
    <string name="error_name_required">Informe o nome</string>
    <string name="error_package_size">Informe um número maior que 0</string>
    <string name="create_failed">Não foi possível salvar o produto: %1$s</string>
```

### `ui/Formats.java` — add `parseAmount`

Add the import at the top, with the others:

```java
import androidx.annotation.Nullable;
```

And this method below `amount(...)`, before the class's closing `}`:

```java
    /**
     * Reads a number typed by the user. Both "1.5" and "1,5" mean one and a half; thousands
     * separators are not accepted. Returns null when the text is not a usable number.
     */
    @Nullable
    public static Double parseAmount(String text) {
        String normalized = text.trim().replace(',', '.');
        if (normalized.isEmpty()) return null;
        try {
            double value = Double.parseDouble(normalized);
            if (Double.isNaN(value) || Double.isInfinite(value)) return null;
            return value;
        } catch (NumberFormatException e) {
            return null;
        }
    }
```

Why both separators: in Portuguese people type `1,5`, in English `1.5`, and the CN51's number
keyboard may offer only one of them. The price is that `1.000` means *one*, not a thousand, so
thousands separators are refused rather than guessed.

`Double.parseDouble` happily accepts `"NaN"` and `"Infinity"`, and `NaN <= 0` is **false**, so it
would slip past the "greater than 0" check. Hence the explicit check.

### `test/.../ui/FormatsTest.java` — add

Import next to the existing `assertEquals` import:

```java
import static org.junit.Assert.assertNull;
```

Two tests before the class's closing `}`:

```java
    @Test
    public void parseAcceptsDotOrComma() {
        assertEquals(1.5, Formats.parseAmount("1.5"), 0.0);
        assertEquals(1.5, Formats.parseAmount("1,5"), 0.0);
        assertEquals(2.0, Formats.parseAmount(" 2 "), 0.0);
    }

    @Test
    public void parseRejectsNonNumbers() {
        assertNull(Formats.parseAmount(""));
        assertNull(Formats.parseAmount("abc"));
        assertNull(Formats.parseAmount("1.000,5"));
        assertNull(Formats.parseAmount("NaN"));
    }
```

`assertEquals(expected, actual, 0.0)`: comparing `double`s needs a third argument, the allowed
difference. `0.0` means exact, which is fine here. `Formats.parseAmount` returns a `Double`;
Java unboxes it to `double` for the comparison. `./gradlew test` → **15 tests**.

### `res/layout/dialog_new_product.xml` — new

```xml
<?xml version="1.0" encoding="utf-8"?>
<ScrollView xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="wrap_content">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="vertical"
        android:padding="16dp">

        <TextView
            android:id="@+id/barcode"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginBottom="8dp"
            android:textSize="14sp" />

        <EditText
            android:id="@+id/name"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:hint="@string/new_product_name"
            android:inputType="textCapSentences"
            android:importantForAutofill="no" />

        <TextView
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="8dp"
            android:text="@string/new_product_category" />

        <Spinner
            android:id="@+id/category"
            android:layout_width="match_parent"
            android:layout_height="48dp" />

        <TextView
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="8dp"
            android:text="@string/new_product_unit" />

        <Spinner
            android:id="@+id/unit"
            android:layout_width="match_parent"
            android:layout_height="48dp" />

        <EditText
            android:id="@+id/package_size"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="8dp"
            android:hint="@string/new_product_package_size"
            android:inputType="numberDecimal"
            android:digits="0123456789.,"
            android:importantForAutofill="no" />
    </LinearLayout>
</ScrollView>
```

- `ScrollView` around everything: on a 4" screen with the on-screen keyboard open, the form does
  not fit. A `ScrollView` must have exactly **one** child, hence the inner `LinearLayout`.
- `hint`: grey text shown while the field is empty. Doubles as the field's label here.
- `textCapSentences`: the keyboard starts with a capital letter.
- `numberDecimal` shows a number keyboard; `digits="0123456789.,"` makes sure both `.` and `,`
  can be typed (some keyboards only allow `.` with `numberDecimal`).
- `importantForAutofill="no"`: tells newer Androids not to offer saved passwords/addresses here.
  API 17 ignores it; lint asks for it.

### `ui/session/NewProductDialog.java` — new

```java
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

    /** The Activity that shows this dialog must implement this. */
    public interface Listener {
        void onNewProduct(Product draft);
    }

    private static final String ARG_BARCODE = "barcode";

    private EditText name;
    private Spinner category;
    private Spinner unit;
    private EditText packageSize;

    /** Fragments must be re-creatable by Android, so parameters go in arguments, not a constructor. */
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

    /** Same order as Category.values(), so a spinner position maps straight back to the enum. */
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
```

- `inflate(R.layout.dialog_new_product, null)`: Android Studio warns "avoid passing null as the
  view root". For a dialog there is no parent yet, so `null` is correct here; ignore it.
- `requireArguments()` / `requireContext()` / `requireActivity()`: like `getArguments()` etc., but
  throw a clear error instead of returning `null` if called at the wrong time.
- `Product` gets id `0` ("not saved yet", as documented on the field) and no image. The API
  returns a new `Product` with the real id; that is the one added to the session.
- `dismiss()` comes **after** `onNewProduct`, so the Activity has the draft before the dialog goes.

### `ui/session/SessionActivity.java` — three changes

1. The class also implements the dialog's listener. Replace the class line:

```java
public class SessionActivity extends AppCompatActivity
        implements SessionAdapter.Listener, NewProductDialog.Listener {
```

2. In `onScan`, inside `onError`, replace the `scan_unknown` toast:

```java
                if (error.isNotFound()) {
                    askForNewProduct(barcode);
                } else {
```

3. Add these two methods just above `indexOf(...)`:

```java
    private void askForNewProduct(String barcode) {
        // If the user left the screen during the lookup, showing a dialog now would crash.
        if (getSupportFragmentManager().isStateSaved()) return;
        NewProductDialog.newInstance(barcode).show(getSupportFragmentManager(), "new_product");
    }

    /** Called by NewProductDialog when the form is valid. */
    @Override
    public void onNewProduct(Product draft) {
        setLookingUp(true);
        api.createProduct(draft, new ApiCallback<Product>() {
            @Override
            public void onSuccess(Product product) {
                if (isDestroyed()) return;
                setLookingUp(false);
                addItem(product);
            }

            @Override
            public void onError(ApiException error) {
                if (isDestroyed()) return;
                setLookingUp(false);
                toast(getString(R.string.create_failed, error.getMessage()));
            }
        });
    }
```

`onNewProduct` reuses `setLookingUp` (progress bar + "busy" guard) and `addItem` (row + save)
from Step 6: once the API has answered, a new product is handled exactly like a scanned one.

The tag `"new_product"` in `show(...)` names the Fragment inside the FragmentManager. Nothing
looks it up yet; it is required and useful when debugging.

## Verify

`./gradlew test` (15 passing), then `./gradlew installDebug`. Start a new session.

1. Scan `1111` + Enter → dialog *New product*, `Barcode: 1111`, cursor in *Name*. The keyboard
   may appear (this is a real text field); that is fine.
2. Tap **Save** with everything empty → dialog stays, red marker on *Name*.
3. Type `Leite`, **Save** → dialog stays, red marker on *Amount per package*.
4. Tap the amount field, type `1,5`; pick unit `L` → **Save** → dialog closes, thin progress bar
   flashes, row **Leite · 1 × 1.5 L = 1.5 L** (pt-BR: `1,5 L`). Try `1.5` next time; same result.
5. Scan `1111` again → same row, `2 × 1.5 L = 3 L`, no dialog.
6. Scan `7896004000015` right after → Arroz row added: the scanner has focus again.
7. Scan `2222` → dialog → **Cancel** → no row. Scan `7896004000084` → Sabonete added.
8. Scan `3333` → dialog. Scan `7896004000022` while it is open → the code lands in *Name*. Back
   (◀) closes the dialog, no row added.
9. Back to home → *Continue session (3 items)*.
10. `adb logcat -s Inventory AndroidRuntime` → no stack traces.

| Symptom | Cause |
|---|---|
| `ClassCastException: SessionActivity cannot be cast to ...NewProductDialog$Listener` | `implements ..., NewProductDialog.Listener` missing on `SessionActivity`. |
| Dialog closes even when the name is empty | Listener passed to `setPositiveButton(...)` instead of `null` + `setOnShowListener`. |
| `NullPointerException` in `onShow` / `getButton` | `getButton` called before `dialog.show()`: it must be inside `setOnShowListener`. |
| `Fragment ... could not find Fragment constructor` | You added a constructor with parameters to `NewProductDialog`. Keep only `newInstance`. |
| Spinner shows text like `KITCHEN` | Adapter built from `Category.values()` directly; use the `getString(c.labelRes)` labels. |
| Wrong category/unit saved | Labels list and `values()` in different order; build labels by looping over `values()`. |
| `1,5` refused / cannot type `,` | `digits="0123456789.,"` missing, or `parseAmount` does not replace `,`. |
| `IllegalStateException: Can not perform this action after onSaveInstanceState` | `isStateSaved()` check missing in `askForNewProduct`. |

## Checklist

- [ ] strings (both languages), `scanned_toast` + `scan_unknown` removed
- [ ] `Formats.parseAmount` + 2 tests, `./gradlew test` → 15 passing
- [ ] `dialog_new_product.xml`
- [ ] `ui/session/NewProductDialog.java`
- [ ] `SessionActivity`: implements, `askForNewProduct`, `onNewProduct`
- [ ] Verify 1–10 on the emulator
- [ ] commit: `git add -A && git commit -m "Step 7: register unknown products from the session screen"`
