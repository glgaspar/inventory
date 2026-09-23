# Step 10 — Product detail (`ProductDetailActivity`)

Goal: tapping a product on the Inventory screen opens its detail: photo, name, category, what is
in stock, and its history (`GET /products/{id}/movements`), newest first. Entries show as
`+10 kg` in green, usage as `−5 kg` in red, each with its date and time.

Done when tapping *Arroz 5kg* shows `5 kg in stock · 1 package` and two history rows,
`−5 kg` (10/09 19:30) above `+10 kg` (01/09 10:00), and Back returns to the Inventory list.

Notes:
- **First use of Glide** (the image library, in the project since Part 1). Prototype-tested on
  the API 17 emulator: it loads, it downloaded a real `http://` image, and a broken URL (404)
  showed the error icon instead of crashing. The mock's products have no `imageUrl`, so for now
  you'll always see the grey "no photo" icon; real photos come with the backend.
- Logcat shows `W/Glide: Failed to find GeneratedAppGlideModule...` once. Harmless: that module
  is only needed to customize Glide, which we don't.
- Times are shown in the **device's** time zone. The emulator runs on GMT, so `19:30Z` shows as
  `19:30`; a CN51 set to Brasília time would show `16:30`.
- The screen receives the tapped `InventoryItem` itself (as JSON inside the `Intent`), so the
  header appears instantly and only the history is loaded from the API.
- This step also removes the unused `placeholder_coming_soon` string left over from Step 9.

## Concepts you meet in this step

| Concept | One-liner |
|---|---|
| `Intent` extras | `intent.putExtra(key, value)` attaches data for the next screen; `getIntent().getStringExtra(key)` reads it there. Only simple types (text, numbers, …) fit directly. |
| Object → JSON → object | An `InventoryItem` isn't a simple type, so `new Gson().toJson(item)` turns it into a `String` and `fromJson(..., InventoryItem.class)` turns it back. Same trick `SessionStore` uses for the file. |
| `intentFor(...)` factory | A `static` method on the screen that builds its own `Intent`. The key and the JSON format live in one class; callers can't get them wrong. |
| `parentActivityName` | Tells Android which screen is "up" from this one. The detail's parent is the Inventory screen, not home. |
| Glide | `Glide.with(this).load(url).into(imageView)` downloads (on a background thread), caches, resizes and shows an image. `placeholder` = while loading, `fallback` = url is `null`, `error` = download failed. |
| `ContextCompat.getColor` | Reads a `<color>` resource. `getResources().getColor(id)` is deprecated on newer Android; `ContextCompat` picks the right call for each version. |
| Colors as resources | `res/values/colors.xml` names the colors; code and layouts refer to `R.color.x`. One place to change them. |
| Plurals with two arguments | `getQuantityString(R.plurals.x, count, amountText, count)`: the first `count` picks *one*/*other*, the rest fill `%1$s`, `%2$d`. |
| Listener in a `static` ViewHolder | The holder can't see the adapter's fields, so the adapter passes the listener into the holder's constructor. The holder keeps the bound `item` to hand it to the listener on tap. |
| Method reference in a field | `new InventoryAdapter(this::openDetail)` in a field initializer: `this` already exists at that point, so it works. |

## Files

### `res/values/strings.xml` — remove `placeholder_coming_soon`, add

```xml
    <string name="detail_image">Product photo</string>
    <string name="detail_history">History</string>
    <string name="detail_empty">No movements yet</string>
    <string name="detail_failed">Could not load the history: %1$s</string>
    <plurals name="detail_stock">
        <item quantity="one">%1$s in stock · %2$d package</item>
        <item quantity="other">%1$s in stock · %2$d packages</item>
    </plurals>
    <plurals name="movement_in">
        <item quantity="one">Entry · %d package</item>
        <item quantity="other">Entry · %d packages</item>
    </plurals>
    <plurals name="movement_out">
        <item quantity="one">Usage · %d package</item>
        <item quantity="other">Usage · %d packages</item>
    </plurals>
```

### `res/values-pt-rBR/strings.xml` — remove `placeholder_coming_soon`, add

```xml
    <string name="detail_image">Foto do produto</string>
    <string name="detail_history">Histórico</string>
    <string name="detail_empty">Nenhuma movimentação ainda</string>
    <string name="detail_failed">Não foi possível carregar o histórico: %1$s</string>
    <plurals name="detail_stock">
        <item quantity="one">%1$s em estoque · %2$d pacote</item>
        <item quantity="other">%1$s em estoque · %2$d pacotes</item>
    </plurals>
    <plurals name="movement_in">
        <item quantity="one">Entrada · %d pacote</item>
        <item quantity="other">Entrada · %d pacotes</item>
    </plurals>
    <plurals name="movement_out">
        <item quantity="one">Saída · %d pacote</item>
        <item quantity="other">Saída · %d pacotes</item>
    </plurals>
```

`·` is the middle dot (U+00B7). Copy it from here, or use a plain `-` if you prefer.

### `res/values/colors.xml` — add

Before the closing `</resources>`:

```xml
    <!-- History amounts: green for entries, red for usage. Readable on light and dark backgrounds. -->
    <color name="movement_in">#FF2E7D32</color>
    <color name="movement_out">#FFC62828</color>
```

### `ui/Formats.java` — add

Import next to the `Unit` import:

```java
import com.pessimaideia.inventory.model.Movement;
```

Two methods before the class's closing `}`:

```java
    /** Date and time in the device's language and time zone: 9/22/26 1:05 PM, 22/09/26 13:05, ... */
    public static String shortDateTime(Date date, Locale locale) {
        return DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, locale).format(date);
    }

    /** "+5 kg" for an entry, "−1 un" for a usage (U+2212 minus, same width as the plus). */
    public static String signedAmount(double value, Movement.Type type, Unit unit, Locale locale) {
        String sign = type == Movement.Type.IN ? "+" : "\u2212";
        return sign + amount(value, unit, locale);
    }
```

`"−"` is a Unicode escape: the minus sign written as its code, so the source file doesn't
depend on the character surviving copy/paste.

### `test/.../ui/FormatsTest.java` — add

Import next to the `Unit` import:

```java
import com.pessimaideia.inventory.model.Movement;
```

One test before the class's closing `}`:

```java
    @Test
    public void signedAmountShowsDirection() {
        assertEquals("+5 kg", Formats.signedAmount(5, Movement.Type.IN, Unit.KG, Locale.US));
        assertEquals("\u22121,5 L", Formats.signedAmount(1.5, Movement.Type.OUT, Unit.L, PT_BR));
    }
```

`./gradlew test` → **25 tests**.

### `res/layout/activity_product_detail.xml` — new

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

    <!-- Header: photo on the left, name and stock on the right. -->
    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:gravity="center_vertical"
        android:orientation="horizontal"
        android:padding="12dp">

        <ImageView
            android:id="@+id/image"
            android:layout_width="96dp"
            android:layout_height="96dp"
            android:contentDescription="@string/detail_image"
            android:scaleType="centerCrop" />

        <LinearLayout
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:layout_marginStart="12dp"
            android:layout_marginLeft="12dp"
            android:orientation="vertical">

            <TextView
                android:id="@+id/name"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:textSize="20sp"
                android:textStyle="bold" />

            <TextView
                android:id="@+id/category"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:textSize="14sp" />

            <TextView
                android:id="@+id/stock"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginTop="4dp"
                android:textSize="16sp" />
        </LinearLayout>
    </LinearLayout>

    <TextView
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:background="?attr/colorControlHighlight"
        android:paddingStart="12dp"
        android:paddingLeft="12dp"
        android:paddingTop="6dp"
        android:paddingBottom="6dp"
        android:text="@string/detail_history"
        android:textAllCaps="true"
        android:textSize="14sp"
        android:textStyle="bold" />

    <FrameLayout
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:layout_weight="1">

        <androidx.recyclerview.widget.RecyclerView
            android:id="@+id/list"
            android:layout_width="match_parent"
            android:layout_height="match_parent" />

        <TextView
            android:id="@+id/empty"
            android:layout_width="match_parent"
            android:layout_height="match_parent"
            android:gravity="center"
            android:padding="24dp"
            android:text="@string/detail_empty"
            android:textSize="18sp"
            android:visibility="gone" />
    </FrameLayout>
</LinearLayout>
```

- `scaleType="centerCrop"`: fill the 96 dp square, cutting the edges if the photo isn't square.
- The *History* title reuses the look of the Inventory category headers.

### `res/layout/item_movement.xml` — new

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:gravity="center_vertical"
    android:minHeight="48dp"
    android:orientation="horizontal"
    android:paddingStart="12dp"
    android:paddingLeft="12dp"
    android:paddingEnd="12dp"
    android:paddingRight="12dp">

    <LinearLayout
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:layout_weight="1"
        android:orientation="vertical">

        <TextView
            android:id="@+id/when"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:textSize="16sp" />

        <TextView
            android:id="@+id/type"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:textSize="14sp" />
    </LinearLayout>

    <TextView
        android:id="@+id/amount"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:textSize="18sp"
        android:textStyle="bold" />
</LinearLayout>
```

### `ui/inventory/MovementAdapter.java` — new

```java
package com.pessimaideia.inventory.ui.inventory;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.pessimaideia.inventory.R;
import com.pessimaideia.inventory.model.Movement;
import com.pessimaideia.inventory.model.Unit;
import com.pessimaideia.inventory.ui.Formats;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** One product's history, newest first (the API already sends it in that order). */
public class MovementAdapter extends RecyclerView.Adapter<MovementAdapter.ViewHolder> {

    private final Unit unit;
    private List<Movement> movements = new ArrayList<>();

    /** Movements only carry numbers; the unit comes from the product. */
    public MovementAdapter(Unit unit) {
        this.unit = unit;
    }

    public void setMovements(List<Movement> movements) {
        this.movements = movements;
        notifyDataSetChanged();
    }

    @Override
    public int getItemCount() {
        return movements.size();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View row = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_movement, parent, false);
        return new ViewHolder(row);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(movements.get(position), unit);
    }

    static class ViewHolder extends RecyclerView.ViewHolder {

        private final TextView when;
        private final TextView type;
        private final TextView amount;

        ViewHolder(View row) {
            super(row);
            when = row.findViewById(R.id.when);
            type = row.findViewById(R.id.type);
            amount = row.findViewById(R.id.amount);
        }

        void bind(Movement movement, Unit unit) {
            Locale locale = Locale.getDefault();
            Date date = Formats.parseIso(movement.at);
            when.setText(date != null ? Formats.shortDateTime(date, locale) : movement.at);

            boolean entry = movement.type == Movement.Type.IN;
            type.setText(itemView.getResources().getQuantityString(
                    entry ? R.plurals.movement_in : R.plurals.movement_out,
                    movement.packages, movement.packages));
            amount.setText(Formats.signedAmount(movement.amount, movement.type, unit, locale));
            amount.setTextColor(ContextCompat.getColor(itemView.getContext(),
                    entry ? R.color.movement_in : R.color.movement_out));
        }
    }
}
```

A movement stores only numbers (`amount`, `packages`); the unit (`kg`, `un`, …) belongs to the
product. So the adapter is created with the product's `Unit`, and every row uses it.

### `ui/inventory/ProductDetailActivity.java` — new

```java
package com.pessimaideia.inventory.ui.inventory;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.snackbar.Snackbar;
import com.google.gson.Gson;
import com.pessimaideia.inventory.App;
import com.pessimaideia.inventory.R;
import com.pessimaideia.inventory.api.ApiCallback;
import com.pessimaideia.inventory.api.ApiException;
import com.pessimaideia.inventory.model.InventoryItem;
import com.pessimaideia.inventory.model.Movement;
import com.pessimaideia.inventory.model.Product;
import com.pessimaideia.inventory.ui.Formats;

import java.util.List;
import java.util.Locale;

public class ProductDetailActivity extends AppCompatActivity {

    private static final String EXTRA_ITEM = "item";

    /** The one way to open this screen, so the extra is always filled in the same way. */
    public static Intent intentFor(Context context, InventoryItem item) {
        return new Intent(context, ProductDetailActivity.class)
                .putExtra(EXTRA_ITEM, new Gson().toJson(item));
    }

    private InventoryItem item;
    private MovementAdapter adapter;

    private RecyclerView list;
    private ProgressBar progress;
    private TextView empty;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_product_detail);

        item = new Gson().fromJson(getIntent().getStringExtra(EXTRA_ITEM), InventoryItem.class);
        if (item == null) {
            finish();   // opened without intentFor(): nothing to show
            return;
        }
        Product product = item.product;
        setTitle(product.name);

        showHeader(product);

        list = findViewById(R.id.list);
        progress = findViewById(R.id.progress);
        empty = findViewById(R.id.empty);
        adapter = new MovementAdapter(product.unit);
        list.setLayoutManager(new LinearLayoutManager(this));
        list.addItemDecoration(new DividerItemDecoration(this, DividerItemDecoration.VERTICAL));
        list.setAdapter(adapter);

        load();
    }

    private void showHeader(Product product) {
        ImageView image = findViewById(R.id.image);
        TextView name = findViewById(R.id.name);
        TextView category = findViewById(R.id.category);
        TextView stock = findViewById(R.id.stock);

        // No imageUrl (null) shows the fallback; a failed download shows the error image.
        Glide.with(this)
                .load(product.imageUrl)
                .placeholder(android.R.drawable.ic_menu_gallery)
                .fallback(android.R.drawable.ic_menu_gallery)
                .error(android.R.drawable.ic_menu_report_image)
                .into(image);

        name.setText(product.name);
        category.setText(product.category.labelRes);
        String amount = Formats.amount(item.amount, product.unit, Locale.getDefault());
        stock.setText(getResources().getQuantityString(
                R.plurals.detail_stock, item.packages, amount, item.packages));
    }

    private void load() {
        progress.setVisibility(View.VISIBLE);
        App.from(this).api().getMovements(item.product.id, new ApiCallback<List<Movement>>() {
            @Override
            public void onSuccess(List<Movement> movements) {
                if (isDestroyed()) return;
                progress.setVisibility(View.INVISIBLE);
                adapter.setMovements(movements);
                empty.setVisibility(movements.isEmpty() ? View.VISIBLE : View.GONE);
            }

            @Override
            public void onError(ApiException error) {
                if (isDestroyed()) return;
                progress.setVisibility(View.INVISIBLE);
                Snackbar.make(list, getString(R.string.detail_failed, error.getMessage()),
                                Snackbar.LENGTH_INDEFINITE)
                        .setAction(R.string.retry, v -> load())
                        .show();
            }
        });
    }
}
```

- `if (item == null) { finish(); return; }`: if something opens this screen without the extra,
  close it instead of crashing on `item.product` a line later.
- `Glide.with(this)` ties the download to this Activity: leave the screen and Glide cancels it.

### `ui/inventory/InventoryAdapter.java` — make rows tappable

1. A listener interface at the top of the class, above `TYPE_HEADER`:

```java
    public interface Listener {
        void onItemClick(InventoryItem item);
    }
```

2. A field and a constructor. Replace the `rows` field line with:

```java
    private final Listener listener;
    private List<Row> rows = new ArrayList<>();

    public InventoryAdapter(Listener listener) {
        this.listener = listener;
    }
```

3. In `onCreateViewHolder`, pass the listener to the item holder:

```java
        return new ItemHolder(inflater.inflate(R.layout.item_inventory, parent, false), listener);
```

4. In `ItemHolder`: a field for the bound item, the listener in the constructor, and remember
   the item in `bind`. The class becomes:

```java
    static class ItemHolder extends RecyclerView.ViewHolder {
        private final TextView name;
        private final TextView packages;
        private final TextView amount;
        private final TextView updated;
        private InventoryItem item;

        ItemHolder(View view, Listener listener) {
            super(view);
            view.setOnClickListener(v -> listener.onItemClick(item));
            name = view.findViewById(R.id.name);
            packages = view.findViewById(R.id.packages);
            amount = view.findViewById(R.id.amount);
            updated = view.findViewById(R.id.updated);
        }

        void bind(InventoryItem item) {
            this.item = item;
            Locale locale = Locale.getDefault();
            name.setText(item.product.name);
            packages.setText(String.valueOf(item.packages));
            amount.setText(Formats.amount(item.amount, item.product.unit, locale));
            Date date = Formats.parseIso(item.updatedAt);
            updated.setText(date != null ? Formats.shortDate(date, locale) : "");
        }
    }
```

### `res/layout/item_inventory.xml` — show taps

Add to the root `LinearLayout`, next to `minHeight`:

```xml
    android:background="?attr/selectableItemBackground"
```

### `ui/inventory/InventoryActivity.java` — open the detail

Replace the adapter field:

```java
    private final InventoryAdapter adapter = new InventoryAdapter(this::openDetail);
```

Add this method just above the `// ---- sorting` line:

```java
    private void openDetail(InventoryItem item) {
        startActivity(ProductDetailActivity.intentFor(this, item));
    }
```

### `AndroidManifest.xml` — register the screen

After the `InventoryActivity` entry:

```xml
        <activity
            android:name=".ui.inventory.ProductDetailActivity"
            android:parentActivityName=".ui.inventory.InventoryActivity"
            android:screenOrientation="portrait" />
```

## Verify

`./gradlew test` (25 passing), then `./gradlew installDebug`. Home → **Inventory**.

1. Tapping a product row flashes the row (selectable background) and opens its detail.
2. **Arroz 5kg** → title *Arroz 5kg*; grey photo icon; *Kitchen*; `5 kg in stock · 1 package`;
   *History* with two rows, newest first:
   - `10/09/26 19:30` · *Usage · 1 package* · **−5 kg** in red
   - `01/09/26 10:00` · *Entry · 2 packages* · **+10 kg** in green
   (pt-BR shown; English is `9/10/26 7:30 PM`.)
3. Back → the Inventory list, with the same sort as before.
4. **Papel higiênico 12 rolos** → `12 un in stock · 1 package`, one row `+12 un`.
5. Send a session with coffee (`7896004000046`), open Inventory → **Café 500g** → one entry
   dated now (emulator: GMT).
6. `adb logcat -s Inventory AndroidRuntime` → no stack traces. Full logcat: one
   `W/Glide ... GeneratedAppGlideModule` line is expected; `Verifier rejected class` is not.

| Symptom | Cause |
|---|---|
| `ActivityNotFoundException ... ProductDetailActivity` | Not registered in the manifest. |
| Detail opens and closes immediately | The extra is missing: open it only through `ProductDetailActivity.intentFor(...)`, and use the same `EXTRA_ITEM` key when reading. |
| Tapping a row does nothing | `setOnClickListener` missing in `ItemHolder`, or `listener` not passed into it. |
| Detail shows the wrong product | `this.item = item;` missing in `ItemHolder.bind`, so the holder hands over whatever it was first bound to. |
| History amounts all show `kg` (or the wrong unit) | `MovementAdapter` created with a fixed unit instead of `product.unit`. |
| `Resources$NotFoundException: String resource ID` on a history row | `setText(R.plurals...)` called directly; plurals need `getQuantityString`. |
| `UnknownFormatConversionException` / `%2$d` shown literally | `getQuantityString(R.plurals.detail_stock, ...)` got its arguments in the wrong order: count, amount text, count. |
| No photo icon at all (empty square) | `fallback(...)` missing: with a `null` URL, Glide shows the fallback, not the placeholder. |

## Checklist

- [ ] strings (both languages), `placeholder_coming_soon` removed
- [ ] colors `movement_in` / `movement_out`
- [ ] `Formats.shortDateTime` / `signedAmount` + 1 test, `./gradlew test` → 25 passing
- [ ] `activity_product_detail.xml`, `item_movement.xml`
- [ ] `MovementAdapter.java`, `ProductDetailActivity.java`
- [ ] `InventoryAdapter` listener, `item_inventory.xml` background, `InventoryActivity.openDetail`
- [ ] manifest: `ProductDetailActivity`
- [ ] Verify 1–6 on the emulator
- [ ] commit: `git add -A && git commit -m "Step 10: product detail with history"`
