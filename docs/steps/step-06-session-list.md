# Step 6 — Session list (`SessionActivity` + `SessionAdapter`)

Goal: the Scanning screen becomes the real session. Each scan looks the barcode up in the API and
adds a row (or bumps the package count if the product is already there). Each row has `−` / `+`
and a remove button. Every change is saved to `session.json`, so the home screen's **Continue
session (N items)** is always right, even after the app is killed.

Done when scanning `7896004000015` twice shows one row **Arroz 5kg · 2 × 5 kg = 10 kg**, the
buttons work, and leaving and reopening the app shows the same list.

Deviations from PLAN.md:
- **No image thumbnail in the row yet.** The mock products have no `imageUrl`, and Glide on API 17
  deserves its own check (the Gson surprise was enough). Thumbnails arrive with Glide in Step 10.
- **Unknown barcode → toast** for now. Step 7 replaces that toast with the register dialog.
- **Re-scanning a product already in the list does not call the API.** We already have its
  `Product`, so the count goes up instantly.
- **Scans during a lookup are refused** with a toast, not queued. The mock answers in 300 ms; a
  queue is more code than it is worth until the real backend shows it is needed.

`session.json` is written on the main thread after every change. It is a few hundred bytes, which
takes well under a millisecond; moving it to a background thread would add real complexity
(two writes racing) for no visible gain.

## Concepts you meet in this step

| Concept | One-liner |
|---|---|
| `RecyclerView` | A scrolling list that only creates views for the rows on screen and **recycles** them as you scroll. Needs three things: a `LayoutManager` (how rows are arranged), an `Adapter` (what's in them), and data. |
| Adapter | Answers three questions: how many rows (`getItemCount`), how to build an empty row (`onCreateViewHolder`), how to fill a row for position N (`onBindViewHolder`). |
| ViewHolder | Holds a row's views after `findViewById` ran **once**. `bind()` just sets text, so scrolling stays cheap. |
| Inflating | `LayoutInflater.from(context).inflate(R.layout.x, parent, false)` turns a layout XML into view objects. `false` = "don't add it to parent; RecyclerView will". |
| `notifyItem...` | After changing the list, tell the adapter what changed: `notifyItemInserted(pos)`, `notifyItemChanged(pos)`, `notifyItemRemoved(pos)`. Forget it and the screen shows stale rows (or crashes with "Inconsistency detected"). |
| `getBindingAdapterPosition()` | Which row a tapped button belongs to **right now**. Rows move when others are removed, so never remember the position from `bind()`. Returns `NO_POSITION` during an animation. |
| Anonymous class | `new ApiCallback<Product>() { ... }` creates a one-off class inline. Needed here instead of a lambda because `ApiCallback` has **two** methods. |
| Captured variables | Inside the anonymous class, `barcode` from `onScan` is still readable because it never changes ("effectively final"). |
| `isDestroyed()` | The API answers 300 ms later. If the user left the screen meanwhile, touching its views is a bug. `isDestroyed()` exists from API 17, just in time. |
| `implements` on an Activity | `SessionActivity implements SessionAdapter.Listener` lets the adapter call back into the Activity without knowing its class. |
| `final class` + private constructor | `Formats` is a bag of static methods; nobody should create or extend it. |
| `NumberFormat` | Formats numbers for a `Locale`: `1.5` → `1.5` (en) / `1,5` (pt-BR), `1800` → `1,800` / `1.800`. |
| `translatable="false"` | For strings that are the same in every language (the `−` / `+` signs). Android then does not expect a pt-BR copy. |

## Files

### `res/values/strings.xml` — remove `scanned_toast`, add

```xml
    <string name="session_empty">Scan a product to add it</string>
    <string name="session_row_amount">%1$d × %2$s = %3$s</string>
    <string name="session_fewer">Fewer packages</string>
    <string name="session_more">More packages</string>
    <string name="session_remove">Remove</string>
    <string name="scan_busy">Still looking up the previous code</string>
    <string name="scan_unknown">Unknown product: %1$s</string>
    <string name="scan_failed">Lookup failed: %1$s</string>
    <string name="sign_minus" translatable="false">−</string>
    <string name="sign_plus" translatable="false">+</string>
```

`%1$d` is a whole number (the package count, an `int`); `%2$s` and `%3$s` are text (already
formatted amounts). Passing a `String` where `%d` expects a number crashes with
`IllegalFormatConversionException`.

The `−` is the Unicode minus sign (U+2212), which lines up with `+`. A plain hyphen `-` works
too, it just looks shorter.

### `res/values-pt-rBR/strings.xml` — remove `scanned_toast` (if you added it), add

```xml
    <string name="session_empty">Leia um produto para adicioná-lo</string>
    <string name="session_row_amount">%1$d × %2$s = %3$s</string>
    <string name="session_fewer">Menos pacotes</string>
    <string name="session_more">Mais pacotes</string>
    <string name="session_remove">Remover</string>
    <string name="scan_busy">Ainda buscando o código anterior</string>
    <string name="scan_unknown">Produto desconhecido: %1$s</string>
    <string name="scan_failed">Falha na busca: %1$s</string>
```

No `sign_minus` / `sign_plus` here: `translatable="false"` strings live only in `values/`.

### `ui/Formats.java` — new

In `app/src/main/java/com/pessimaideia/inventory/ui/`. The Inventory and detail screens will
format amounts the same way, which is why this is not inside the adapter.

```java
package com.pessimaideia.inventory.ui;

import com.pessimaideia.inventory.model.Unit;

import java.text.NumberFormat;
import java.util.Locale;

/** Number formatting shared by all screens. Pure Java, so it can be unit tested. */
public final class Formats {

    private Formats() {
        // only static methods
    }

    /** 5 → "5 kg"; 1.5 → "1.5 kg" in English, "1,5 kg" in Portuguese. */
    public static String amount(double value, Unit unit, Locale locale) {
        NumberFormat number = NumberFormat.getNumberInstance(locale);
        number.setMaximumFractionDigits(2);
        return number.format(value) + " " + unit.symbol;
    }
}
```

### `test/.../ui/FormatsTest.java` — new

New folder `app/src/test/java/com/pessimaideia/inventory/ui/`. `Formats` is plain Java, so unlike
the rest of this step it runs on the desktop JVM.

```java
package com.pessimaideia.inventory.ui;

import static org.junit.Assert.assertEquals;

import com.pessimaideia.inventory.model.Unit;

import org.junit.Test;

import java.util.Locale;

public class FormatsTest {

    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");

    @Test
    public void wholeNumberHasNoDecimals() {
        assertEquals("5 kg", Formats.amount(5, Unit.KG, Locale.US));
    }

    @Test
    public void decimalSeparatorFollowsLocale() {
        assertEquals("1.5 kg", Formats.amount(1.5, Unit.KG, Locale.US));
        assertEquals("1,5 kg", Formats.amount(1.5, Unit.KG, PT_BR));
    }

    @Test
    public void thousandsSeparatorFollowsLocale() {
        assertEquals("1,800 mL", Formats.amount(1800, Unit.ML, Locale.US));
        assertEquals("1.800 mL", Formats.amount(1800, Unit.ML, PT_BR));
    }

    @Test
    public void roundsToTwoDecimals() {
        assertEquals("0.33 L", Formats.amount(1.0 / 3, Unit.L, Locale.US));
    }
}
```

`Locale.forLanguageTag("pt-BR")` builds the same locale the device uses when set to Português
(Brasil). Run `./gradlew test`: **13 tests**, all passing (the previous 9 + these 4).

### `res/layout/activity_session.xml` — new

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical">

    <ProgressBar
        android:id="@+id/progress"
        style="?android:attr/progressBarStyleHorizontal"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:indeterminate="true"
        android:visibility="invisible" />

    <FrameLayout
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:layout_weight="1">

        <androidx.recyclerview.widget.RecyclerView
            android:id="@+id/list"
            android:layout_width="match_parent"
            android:layout_height="match_parent"
            android:focusable="false"
            android:focusableInTouchMode="false" />

        <TextView
            android:id="@+id/empty"
            android:layout_width="match_parent"
            android:layout_height="match_parent"
            android:gravity="center"
            android:padding="24dp"
            android:textSize="18sp"
            android:text="@string/session_empty" />
    </FrameLayout>
</LinearLayout>
```

- The `ProgressBar` is a thin bar across the top, `invisible` (not `gone`) so the list does not
  jump down by a few pixels every time a lookup starts.
- `layout_height="0dp"` + `layout_weight="1"` on the `FrameLayout`: "take all the height the
  progress bar did not use".
- A `FrameLayout` stacks its children, so the empty-state text sits on top of the (empty) list.
  Java shows or hides it.
- `focusable="false"` + `focusableInTouchMode="false"` on the `RecyclerView`: by default it
  **can take focus**, and whatever has focus gets the scanner's keystrokes. Without these two
  lines, the hidden scanner field can lose focus to the list, and scans stop arriving.

### `res/layout/item_session.xml` — new (one row)

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="horizontal"
    android:gravity="center_vertical"
    android:minHeight="64dp"
    android:paddingStart="12dp"
    android:paddingLeft="12dp"
    android:paddingEnd="4dp"
    android:paddingRight="4dp">

    <LinearLayout
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:layout_weight="1"
        android:orientation="vertical">

        <TextView
            android:id="@+id/name"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:textSize="18sp"
            android:textStyle="bold" />

        <TextView
            android:id="@+id/amount"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:textSize="14sp" />
    </LinearLayout>

    <Button
        android:id="@+id/minus"
        android:layout_width="48dp"
        android:layout_height="48dp"
        android:text="@string/sign_minus"
        android:textSize="20sp"
        android:contentDescription="@string/session_fewer" />

    <TextView
        android:id="@+id/packages"
        android:layout_width="40dp"
        android:layout_height="wrap_content"
        android:gravity="center"
        android:textSize="18sp" />

    <Button
        android:id="@+id/plus"
        android:layout_width="48dp"
        android:layout_height="48dp"
        android:text="@string/sign_plus"
        android:textSize="20sp"
        android:contentDescription="@string/session_more" />

    <ImageButton
        android:id="@+id/remove"
        android:layout_width="48dp"
        android:layout_height="48dp"
        android:background="?attr/selectableItemBackground"
        android:src="@android:drawable/ic_menu_delete"
        android:contentDescription="@string/session_remove" />
</LinearLayout>
```

- `paddingStart` **and** `paddingLeft`: `Start`/`End` are right-to-left aware and exist from
  API 17; the `Left`/`Right` pair is there because lint asks for both when `minSdk` is 17.
- Buttons are 48 dp: a comfortable fingertip on a 4" screen.
- `@android:drawable/ic_menu_delete` is a trash icon built into Android itself (note the
  `android:` prefix: the platform's resources, not yours).
- `contentDescription`: what a screen reader says for an icon-only button. Lint warns without it.

### `ui/session/SessionAdapter.java` — new

```java
package com.pessimaideia.inventory.ui.session;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.pessimaideia.inventory.R;
import com.pessimaideia.inventory.model.SessionItem;
import com.pessimaideia.inventory.ui.Formats;

import java.util.List;
import java.util.Locale;

/** Shows the session's items. It never changes the list itself; it asks the Listener to. */
public class SessionAdapter extends RecyclerView.Adapter<SessionAdapter.ViewHolder> {

    public interface Listener {
        void onChangePackages(int position, int delta);
        void onRemove(int position);
    }

    private final List<SessionItem> items;
    private final Listener listener;

    public SessionAdapter(List<SessionItem> items, Listener listener) {
        this.items = items;
        this.listener = listener;
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View row = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_session, parent, false);
        return new ViewHolder(row);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(items.get(position));
    }

    /** One row. Finds its views once; bind() just fills them in. */
    class ViewHolder extends RecyclerView.ViewHolder {

        private final TextView name;
        private final TextView amount;
        private final TextView packages;
        private final View minus;

        ViewHolder(View row) {
            super(row);
            name = row.findViewById(R.id.name);
            amount = row.findViewById(R.id.amount);
            packages = row.findViewById(R.id.packages);
            minus = row.findViewById(R.id.minus);

            minus.setOnClickListener(v -> changePackages(-1));
            row.findViewById(R.id.plus).setOnClickListener(v -> changePackages(+1));
            row.findViewById(R.id.remove).setOnClickListener(v -> {
                int position = getBindingAdapterPosition();
                if (position != RecyclerView.NO_POSITION) listener.onRemove(position);
            });
        }

        void bind(SessionItem item) {
            Context context = itemView.getContext();
            Locale locale = Locale.getDefault();
            name.setText(item.product.name);
            amount.setText(context.getString(R.string.session_row_amount,
                    item.packages,
                    Formats.amount(item.product.packageSize, item.product.unit, locale),
                    Formats.amount(item.amount(), item.product.unit, locale)));
            packages.setText(String.valueOf(item.packages));
            minus.setEnabled(item.packages > 1);
        }

        private void changePackages(int delta) {
            int position = getBindingAdapterPosition();
            if (position != RecyclerView.NO_POSITION) listener.onChangePackages(position, delta);
        }
    }
}
```

- `class ViewHolder` is an **inner** class (no `static`), so it can use the adapter's `listener`.
- The click listeners are set **once** in the ViewHolder's constructor, not in `bind()`. They ask
  for the position at the moment of the tap, which stays correct after rows move.
- The adapter only displays and reports. The Activity owns the list, changes it, saves it, and
  calls `notify...`. One owner means one place to look when something is wrong.
- `@NonNull`: the annotations RecyclerView's own methods carry. Keep them on overrides, or
  Android Studio shows a warning.

### `ui/session/SessionActivity.java` — replace

```java
package com.pessimaideia.inventory.ui.session;

import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.pessimaideia.inventory.App;
import com.pessimaideia.inventory.R;
import com.pessimaideia.inventory.api.ApiCallback;
import com.pessimaideia.inventory.api.ApiException;
import com.pessimaideia.inventory.api.InventoryApi;
import com.pessimaideia.inventory.data.SessionStore;
import com.pessimaideia.inventory.model.Product;
import com.pessimaideia.inventory.model.SessionItem;
import com.pessimaideia.inventory.scanner.ScannerInput;
import com.pessimaideia.inventory.scanner.WedgeScannerInput;

import java.util.List;

public class SessionActivity extends AppCompatActivity implements SessionAdapter.Listener {

    private final ScannerInput scanner = new WedgeScannerInput();

    private InventoryApi api;
    private SessionStore store;
    /** The session. The adapter shows this same list; every change is saved to disk. */
    private List<SessionItem> items;
    private SessionAdapter adapter;

    private RecyclerView list;
    private ProgressBar progress;
    private TextView empty;

    /** True while waiting for the API; scans that arrive meanwhile are refused. */
    private boolean lookingUp;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_session);
        setTitle(R.string.title_session);

        App app = App.from(this);
        api = app.api();
        store = app.sessionStore();
        items = store.load();

        list = findViewById(R.id.list);
        progress = findViewById(R.id.progress);
        empty = findViewById(R.id.empty);

        adapter = new SessionAdapter(items, this);
        list.setLayoutManager(new LinearLayoutManager(this));
        list.addItemDecoration(new DividerItemDecoration(this, DividerItemDecoration.VERTICAL));
        list.setAdapter(adapter);
        updateEmptyState();

        scanner.attach(this, this::onScan);
    }

    @Override
    protected void onResume() {
        super.onResume();
        scanner.requestFocus();
    }

    // ---- scanning ------------------------------------------------------------------------

    private void onScan(String barcode) {
        if (lookingUp) {
            toast(getString(R.string.scan_busy));
            return;
        }
        int position = indexOf(barcode);
        if (position >= 0) {
            // Already in the session: no need to ask the API again.
            onChangePackages(position, +1);
            return;
        }
        setLookingUp(true);
        api.getProductByBarcode(barcode, new ApiCallback<Product>() {
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
                if (error.isNotFound()) {
                    toast(getString(R.string.scan_unknown, barcode));   // Step 7: register dialog
                } else {
                    toast(getString(R.string.scan_failed, error.getMessage()));
                }
            }
        });
    }

    private int indexOf(String barcode) {
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).product.barcode.equals(barcode)) return i;
        }
        return -1;
    }

    private void setLookingUp(boolean value) {
        lookingUp = value;
        progress.setVisibility(value ? View.VISIBLE : View.INVISIBLE);
    }

    // ---- changing the list ---------------------------------------------------------------

    private void addItem(Product product) {
        items.add(new SessionItem(product, 1));
        int position = items.size() - 1;
        adapter.notifyItemInserted(position);
        list.scrollToPosition(position);
        save();
    }

    @Override
    public void onChangePackages(int position, int delta) {
        SessionItem item = items.get(position);
        if (item.packages + delta < 1) return;
        item.packages += delta;
        adapter.notifyItemChanged(position);
        list.scrollToPosition(position);
        save();
    }

    @Override
    public void onRemove(int position) {
        items.remove(position);
        adapter.notifyItemRemoved(position);
        save();
    }

    private void save() {
        store.save(items);
        updateEmptyState();
    }

    private void updateEmptyState() {
        empty.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void toast(String text) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
    }
}
```

Reading order that makes sense: `onCreate` (wire things up) → `onScan` (the main path) →
`addItem` / `onChangePackages` / `onRemove` (every change ends in `save()`).

- `store.load()` returns the saved session, or an empty list. The adapter gets **the same list
  object**, so after `items.add(...)` the adapter already sees the new row; `notifyItemInserted`
  just tells it to draw it.
- `onScan` → `api.getProductByBarcode(...)` **returns immediately**. The answer arrives ~300 ms
  later in `onSuccess` / `onError`, on the main thread (the mock's `MainThreadPoster` sees to
  that), so touching views there is safe.
- `store.save(items)` with an empty list deletes the file (Step 3), so removing the last row makes
  **Continue session** disappear from the home screen.
- `activity_placeholder.xml` is still used by `InventoryActivity`; leave it.

## Verify

`./gradlew test` first (13 passing), then `./gradlew installDebug`. Start from an empty session
(home shows no Continue button; if it does, tap **New session** → *Discard*).

1. **New session** → empty text *Scan a product to add it*, no keyboard.
2. Type `7896004000015` + Enter → a thin bar flashes at the top, then row **Arroz 5kg** /
   `1 × 5 kg = 5 kg`, count `1`, `−` greyed out.
3. `7896004000015` + Enter again → same row, `2 × 5 kg = 10 kg`, `−` enabled. No progress bar
   (no API call).
4. `7896004000039` + Enter → second row **Óleo de soja 900mL** `1 × 900 mL = 900 mL`.
5. Tap `+` on the oil row → `2 × 900 mL = 1,800 mL` (pt-BR: `1.800 mL`). Tap `−` → back to 1.
6. **After tapping `+`**, scan `7896004000022` + Enter → a third row appears. (Scans still work
   after touching the list: the focus fix.)
7. `0000` + Enter → toast *Unknown product: 0000*, no row.
8. Back → home shows **Continue session (3 items)**. Tap it → same list.
9. `adb shell am force-stop com.pessimaideia.inventory`, reopen → Continue still shows 3 items
   (the list came from disk, not memory).
10. Remove every row with the trash button → empty text again. Back → no Continue button.
11. `adb logcat -s Inventory AndroidRuntime` → a `Scanned:` line per scan, no stack traces.

Seeded barcodes to play with (from `MockInventoryApi.seed()`): `7896004000015` rice,
`…022` beans, `…039` oil, `…046` coffee, `…053` detergent, `…060` washing powder,
`…077` toilet paper, `…084` soap.

| Symptom | Cause |
|---|---|
| Nothing appears, logcat says `No adapter attached; skipping layout` or `No layout manager attached` | `setAdapter` or `setLayoutManager` missing. |
| Scans work until you tap a row, then stop | The `RecyclerView` took focus: `focusable="false"` / `focusableInTouchMode="false"` missing in `activity_session.xml`. |
| Row appears only after scrolling or rotating | `notifyItemInserted` / `notifyItemChanged` not called. |
| `IndexOutOfBoundsException: Inconsistency detected` | The list changed without the matching `notify...` call, or the wrong position was passed. |
| Wrong row changes after removing another | Position remembered from `bind()`; use `getBindingAdapterPosition()` at tap time. |
| `IllegalFormatConversionException: d != java.lang.String` | `session_row_amount` got a `String` for `%1$d`; pass `item.packages` (the `int`). |
| `NullPointerException` in `setLookingUp` / `updateEmptyState` | `findViewById` ran before `setContentView`, or an `@+id` in XML does not match the `R.id` in Java. |
| Continue count wrong / list gone after force-stop | `save()` not called after a change. |
| Crash about a destroyed Activity when leaving mid-lookup | `if (isDestroyed()) return;` missing at the top of `onSuccess` / `onError`. |

## Checklist

- [ ] strings (both languages), `scanned_toast` removed
- [ ] `ui/Formats.java` + `test/.../ui/FormatsTest.java`, `./gradlew test` → 13 passing
- [ ] `activity_session.xml`, `item_session.xml`
- [ ] `ui/session/SessionAdapter.java`
- [ ] `ui/session/SessionActivity.java`
- [ ] Verify 1–11 on the emulator
- [ ] commit: `git add -A && git commit -m "Step 6: session list with lookup, +/- and persistence"`
