# Step 9 — Inventory screen (`InventoryActivity`)

Goal: **Inventory** on the home screen shows what is in stock (`GET /inventory`), grouped by
category (Kitchen, Cleaning, Bathroom, Other) with a header row for each. Tapping a column title
(Name / Packages / Amount / Updated) sorts every group by that column; tapping it again flips the
direction. The choice is remembered, even after the app restarts.

Done when the list shows the seeded stock under three headers, each column title sorts, the
active one shows ▲ or ▼, and after a force-stop the same sort comes back.

Notes:
- Sorting happens **inside** each category; the categories themselves stay in a fixed order.
- Sorting by *Amount* compares plain numbers, so `500 g` counts as more than `5 kg`. Good enough
  to spot what is running low within a group; not a unit conversion.
- Dates show in the device's language and time zone (`10/09/26` in pt-BR, `9/10/26` in English).
  The API sends UTC; `DateFormat` converts.
- Tapping a product does nothing yet: Step 10 adds the detail screen.
- This step also finishes the leftovers: the placeholder layout goes away, and `portrait` goes on
  the two activities that were missing it.

## Concepts you meet in this step

| Concept | One-liner |
|---|---|
| Several view types | `getItemViewType(position)` tells RecyclerView which kind of row a position is; `onCreateViewHolder` gets that type and builds the matching layout. Headers and products are recycled separately. |
| `instanceof` + cast | `if (holder instanceof HeaderHolder) ((HeaderHolder) holder).bind(...)`: check the real class, then treat the object as that class. |
| `static` nested class | Step 6's ViewHolder was an *inner* class because it used the adapter's `listener`. These don't need anything from the adapter, so they're `static`: no hidden link to the outer object. |
| `EnumMap` | A `Map` whose keys are one enum's constants. It iterates in the enum's declaration order, which gives the category order for free. |
| `Comparator` | An object that answers "does a come before b?" with a negative number, 0 or a positive number. `Collections.sort(list, comparator)` uses it. |
| `Collections.reverseOrder(c)` | The same comparator, backwards. **Not** `c.reversed()`: that method was added in Java 8 and only exists on Android 7 (API 24)+; on the CN51 it would crash with `NoSuchMethodError`. Lint flags it as *NewApi*. |
| `Collator` | Compares text the way people of a language expect. With `PRIMARY` strength, `Óleo`, `oleo` and `OLEO` are equal, so accents and capitals don't push words to the end of the list. `String.compareTo` would put `Óleo` after `Sabão`. |
| Tie-breaking | When two rows are equal in the chosen column, fall back to the name. Otherwise equal rows would show in whatever order the API sent them. |
| `SharedPreferences` | Small key → value storage that survives restarts. `getString(key, default)` reads; `edit().putString(...).apply()` writes in the background. |
| `enum.name()` / `valueOf` | `SortColumn.UPDATED.name()` is `"UPDATED"`; `SortColumn.valueOf("UPDATED")` turns it back. `valueOf` throws for unknown text, hence the `try`. |
| `notifyDataSetChanged()` | "Everything may have changed; redraw it all." Right after a re-sort, when nearly every row moved. |
| Styles | `<style>` in `themes.xml` bundles attributes; `style="@style/X"` applies them. Four identical column headers in two lines each instead of seven. |
| `SimpleDateFormat.parse` / `DateFormat` | `parse` turns text into a `Date` (throws `ParseException`); `setLenient(false)` refuses nonsense like month 13. `DateFormat.getDateInstance(SHORT, locale)` formats a date the local way. |

## Files

### `res/values/strings.xml` — remove `placeholder_coming_soon`, add

```xml
    <string name="col_name">Name</string>
    <string name="col_packages">Packages</string>
    <string name="col_amount">Amount</string>
    <string name="col_updated">Updated</string>
    <string name="sort_ascending" translatable="false">%1$s ▲</string>
    <string name="sort_descending" translatable="false">%1$s ▼</string>
    <string name="inventory_empty">Nothing in stock yet</string>
    <string name="inventory_failed">Could not load the inventory: %1$s</string>
```

### `res/values-pt-rBR/strings.xml` — remove `placeholder_coming_soon`, add

```xml
    <string name="col_name">Nome</string>
    <string name="col_packages">Pacotes</string>
    <string name="col_amount">Total</string>
    <string name="col_updated">Atualizado</string>
    <string name="inventory_empty">Nada no estoque ainda</string>
    <string name="inventory_failed">Não foi possível carregar o estoque: %1$s</string>
```

*Total* rather than *Quantidade*: the column is narrow.

### `res/layout/activity_placeholder.xml` — delete

Nothing uses it after this step. In Android Studio: right-click → **Delete** (it checks for uses
first).

### `res/values/themes.xml` — add a style

Before the closing `</resources>`:

```xml
    <!-- Tappable column title on the Inventory screen. -->
    <style name="InventoryColumnHeader">
        <item name="android:layout_height">match_parent</item>
        <item name="android:background">?attr/selectableItemBackground</item>
        <item name="android:textSize">14sp</item>
        <item name="android:textStyle">bold</item>
        <item name="android:maxLines">1</item>
    </style>
```

`?attr/selectableItemBackground` is the theme's "pressed" ripple/highlight, so the titles visibly
react to taps. The style has no `parent`: it isn't a theme, just a bundle of view attributes.

### `ui/Formats.java` — add date helpers

New imports (keep them in alphabetical order with the existing ones):

```java
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;
```

Two methods before the class's closing `}`:

```java
    /** Reads the API's timestamps (2026-09-22T13:05:00Z). Returns null if the text is not one. */
    @Nullable
    public static Date parseIso(String text) {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        format.setLenient(false);
        try {
            return format.parse(text);
        } catch (ParseException e) {
            return null;
        }
    }

    /** Short date in the device's language and time zone: 9/22/26, 22/09/2026, ... */
    public static String shortDate(Date date, Locale locale) {
        return DateFormat.getDateInstance(DateFormat.SHORT, locale).format(date);
    }
```

`parseIso` is the reverse of `MovementBatch.isoUtc`: same pattern, same `Locale.US`, same UTC.

### `test/.../ui/FormatsTest.java` — add

Import (if not there yet):

```java
import java.util.Date;
```

Two tests before the class's closing `}`:

```java
    @Test
    public void parseIsoReadsUtcTimestamps() {
        assertEquals(new Date(0), Formats.parseIso("1970-01-01T00:00:00Z"));
        assertEquals(new Date(90061000L), Formats.parseIso("1970-01-02T01:01:01Z"));
    }

    @Test
    public void parseIsoRejectsOtherText() {
        assertNull(Formats.parseIso("yesterday"));
        assertNull(Formats.parseIso("2026-13-40T00:00:00Z"));
    }
```

`shortDate` is not tested: its output depends on the Java version's locale data, which differs
between your desktop and Android 4.2.

### `ui/inventory/InventoryRows.java` — new

The grouping and sorting, in plain Java so it can be unit tested.

```java
package com.pessimaideia.inventory.ui.inventory;

import androidx.annotation.Nullable;

import com.pessimaideia.inventory.R;
import com.pessimaideia.inventory.model.Category;
import com.pessimaideia.inventory.model.InventoryItem;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Turns the API's flat list into the screen's rows: a header per category, then its products. */
public final class InventoryRows {

    public enum SortColumn {
        NAME(R.string.col_name),
        PACKAGES(R.string.col_packages),
        AMOUNT(R.string.col_amount),
        UPDATED(R.string.col_updated);

        public final int labelRes;

        SortColumn(int labelRes) {
            this.labelRes = labelRes;
        }
    }

    /** One line of the list: either a category header or a product, never both. */
    public static final class Row {
        @Nullable public final Category header;
        @Nullable public final InventoryItem item;

        private Row(@Nullable Category header, @Nullable InventoryItem item) {
            this.header = header;
            this.item = item;
        }

        public boolean isHeader() {
            return header != null;
        }
    }

    private InventoryRows() {
        // only static methods
    }

    public static List<Row> build(List<InventoryItem> items, SortColumn column, boolean ascending,
                                  Locale locale) {
        // EnumMap keeps its keys in the enum's order: kitchen, cleaning, bathroom, other.
        Map<Category, List<InventoryItem>> byCategory = new EnumMap<>(Category.class);
        for (InventoryItem item : items) {
            List<InventoryItem> group = byCategory.get(item.product.category);
            if (group == null) {
                group = new ArrayList<>();
                byCategory.put(item.product.category, group);
            }
            group.add(item);
        }

        Comparator<InventoryItem> order = comparator(column, locale);
        if (!ascending) {
            order = Collections.reverseOrder(order);
        }

        List<Row> rows = new ArrayList<>();
        for (Map.Entry<Category, List<InventoryItem>> entry : byCategory.entrySet()) {
            List<InventoryItem> group = entry.getValue();
            Collections.sort(group, order);
            rows.add(new Row(entry.getKey(), null));
            for (InventoryItem item : group) {
                rows.add(new Row(null, item));
            }
        }
        return rows;
    }

    /** Compares by the chosen column; equal values fall back to the name. */
    static Comparator<InventoryItem> comparator(SortColumn column, Locale locale) {
        Collator collator = Collator.getInstance(locale);
        collator.setStrength(Collator.PRIMARY);   // ignore accents and case: "Óleo" sorts with "oleo"
        return (a, b) -> {
            int result;
            switch (column) {
                case PACKAGES:
                    result = Integer.compare(a.packages, b.packages);
                    break;
                case AMOUNT:
                    result = Double.compare(a.amount, b.amount);
                    break;
                case UPDATED:
                    // Same ISO-8601 UTC format everywhere, so text order is time order.
                    result = a.updatedAt.compareTo(b.updatedAt);
                    break;
                default:
                    result = 0;
                    break;
            }
            return result != 0 ? result : collator.compare(a.product.name, b.product.name);
        };
    }
}
```

- `SortColumn` carries its title's string id, like `Category.labelRes` does.
- `Row` has two nullable fields and exactly one is set. The `private` constructor means only
  `build` can create rows, so that rule can't be broken from outside.
- `comparator(...)` is package-private (no `public`), so the test can call it but other packages
  can't.
- `switch (column)` inside the lambda: `column` is a parameter that never changes, so the lambda
  can capture it.
- `Integer.compare` / `Double.compare` return negative / 0 / positive. Never subtract
  (`a.packages - b.packages`): with big numbers it overflows and gives the wrong sign.

### `test/.../ui/inventory/InventoryRowsTest.java` — new

New folder `app/src/test/java/com/pessimaideia/inventory/ui/inventory/`.

```java
package com.pessimaideia.inventory.ui.inventory;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.pessimaideia.inventory.model.Category;
import com.pessimaideia.inventory.model.InventoryItem;
import com.pessimaideia.inventory.model.Product;
import com.pessimaideia.inventory.model.Unit;
import com.pessimaideia.inventory.ui.inventory.InventoryRows.Row;
import com.pessimaideia.inventory.ui.inventory.InventoryRows.SortColumn;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class InventoryRowsTest {

    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");

    private static InventoryItem item(String name, Category category, int packages, String updatedAt) {
        Product product = new Product(0, "", name, category, Unit.UN, 1, null);
        return new InventoryItem(product, packages, packages, updatedAt);
    }

    /** The product names of the non-header rows, in order. */
    private static List<String> names(List<Row> rows) {
        List<String> names = new ArrayList<>();
        for (Row row : rows) {
            if (!row.isHeader()) names.add(row.item.product.name);
        }
        return names;
    }

    @Test
    public void groupsByCategoryInEnumOrderWithOneHeaderEach() {
        List<Row> rows = InventoryRows.build(Arrays.asList(
                item("Detergente", Category.CLEANING, 1, "2026-09-01T00:00:00Z"),
                item("Arroz", Category.KITCHEN, 1, "2026-09-01T00:00:00Z"),
                item("Café", Category.KITCHEN, 1, "2026-09-01T00:00:00Z")),
                SortColumn.NAME, true, PT_BR);

        assertEquals(5, rows.size());
        assertEquals(Category.KITCHEN, rows.get(0).header);
        assertEquals("Arroz", rows.get(1).item.product.name);
        assertEquals("Café", rows.get(2).item.product.name);
        assertEquals(Category.CLEANING, rows.get(3).header);
        assertEquals("Detergente", rows.get(4).item.product.name);
    }

    @Test
    public void emptyCategoriesHaveNoHeader() {
        List<Row> rows = InventoryRows.build(Arrays.asList(
                item("Sabonete", Category.BATHROOM, 1, "2026-09-01T00:00:00Z")),
                SortColumn.NAME, true, PT_BR);

        assertEquals(2, rows.size());
        assertTrue(rows.get(0).isHeader());
        assertEquals(Category.BATHROOM, rows.get(0).header);
    }

    @Test
    public void nameSortIgnoresAccentsAndCase() {
        List<Row> rows = InventoryRows.build(Arrays.asList(
                item("Sabão", Category.KITCHEN, 1, "2026-09-01T00:00:00Z"),
                item("Óleo", Category.KITCHEN, 1, "2026-09-01T00:00:00Z"),
                item("feijão", Category.KITCHEN, 1, "2026-09-01T00:00:00Z"),
                item("Arroz", Category.KITCHEN, 1, "2026-09-01T00:00:00Z")),
                SortColumn.NAME, true, PT_BR);

        assertEquals(Arrays.asList("Arroz", "feijão", "Óleo", "Sabão"), names(rows));
    }

    @Test
    public void descendingReversesTheOrder() {
        List<Row> rows = InventoryRows.build(Arrays.asList(
                item("A", Category.KITCHEN, 1, "2026-09-01T00:00:00Z"),
                item("B", Category.KITCHEN, 3, "2026-09-01T00:00:00Z"),
                item("C", Category.KITCHEN, 2, "2026-09-01T00:00:00Z")),
                SortColumn.PACKAGES, false, PT_BR);

        assertEquals(Arrays.asList("B", "C", "A"), names(rows));
    }

    @Test
    public void updatedSortsByTimeAndTiesFallBackToName() {
        List<Row> rows = InventoryRows.build(Arrays.asList(
                item("Z", Category.KITCHEN, 1, "2026-09-10T08:00:00Z"),
                item("B", Category.KITCHEN, 1, "2026-09-02T00:00:00Z"),
                item("A", Category.KITCHEN, 1, "2026-09-02T00:00:00Z")),
                SortColumn.UPDATED, true, PT_BR);

        assertEquals(Arrays.asList("A", "B", "Z"), names(rows));
    }
}
```

`import com.pessimaideia.inventory.ui.inventory.InventoryRows.Row;` imports a nested class, so the
test can write `Row` instead of `InventoryRows.Row`. `./gradlew test` → **24 tests**.

### `res/layout/activity_inventory.xml` — new

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

    <!-- Column headers. Same weights and padding as item_inventory.xml so the columns line up. -->
    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="48dp"
        android:orientation="horizontal"
        android:paddingStart="12dp"
        android:paddingLeft="12dp"
        android:paddingEnd="12dp"
        android:paddingRight="12dp">

        <TextView
            android:id="@+id/col_name"
            style="@style/InventoryColumnHeader"
            android:layout_width="0dp"
            android:layout_weight="2"
            android:gravity="center_vertical|start" />

        <TextView
            android:id="@+id/col_packages"
            style="@style/InventoryColumnHeader"
            android:layout_width="0dp"
            android:layout_weight="1"
            android:gravity="center_vertical|end" />

        <TextView
            android:id="@+id/col_amount"
            style="@style/InventoryColumnHeader"
            android:layout_width="0dp"
            android:layout_weight="1.2"
            android:gravity="center_vertical|end" />

        <TextView
            android:id="@+id/col_updated"
            style="@style/InventoryColumnHeader"
            android:layout_width="0dp"
            android:layout_weight="1.3"
            android:gravity="center_vertical|end" />
    </LinearLayout>

    <View
        android:layout_width="match_parent"
        android:layout_height="1dp"
        android:background="?android:attr/listDivider" />

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
            android:textSize="18sp"
            android:text="@string/inventory_empty"
            android:visibility="gone" />
    </FrameLayout>
</LinearLayout>
```

- Columns are `0dp` wide with `layout_weight` 2 : 1 : 1.2 : 1.3: the width is split in that ratio.
  The rows use **the same weights and padding**, which is the only thing keeping headers and
  values aligned. Change one, change both.
- `gravity="...|end"` right-aligns numbers so digits line up.
- `?android:attr/listDivider`: the platform's thin divider line.

### `res/layout/item_inventory_header.xml` — new

```xml
<?xml version="1.0" encoding="utf-8"?>
<TextView xmlns:android="http://schemas.android.com/apk/res/android"
    android:id="@+id/title"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:background="?attr/colorControlHighlight"
    android:paddingStart="12dp"
    android:paddingLeft="12dp"
    android:paddingTop="6dp"
    android:paddingBottom="6dp"
    android:textAllCaps="true"
    android:textSize="14sp"
    android:textStyle="bold" />
```

`?attr/colorControlHighlight` is a light tint that follows the theme (also in dark mode).

### `res/layout/item_inventory.xml` — new

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:minHeight="48dp"
    android:gravity="center_vertical"
    android:orientation="horizontal"
    android:paddingStart="12dp"
    android:paddingLeft="12dp"
    android:paddingEnd="12dp"
    android:paddingRight="12dp">

    <TextView
        android:id="@+id/name"
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:layout_weight="2"
        android:ellipsize="end"
        android:maxLines="2"
        android:textSize="16sp" />

    <TextView
        android:id="@+id/packages"
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:layout_weight="1"
        android:gravity="end"
        android:textSize="16sp" />

    <TextView
        android:id="@+id/amount"
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:layout_weight="1.2"
        android:gravity="end"
        android:textSize="16sp" />

    <TextView
        android:id="@+id/updated"
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:layout_weight="1.3"
        android:gravity="end"
        android:textSize="14sp" />
</LinearLayout>
```

### `ui/inventory/InventoryAdapter.java` — new

```java
package com.pessimaideia.inventory.ui.inventory;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.pessimaideia.inventory.R;
import com.pessimaideia.inventory.model.Category;
import com.pessimaideia.inventory.model.InventoryItem;
import com.pessimaideia.inventory.ui.Formats;
import com.pessimaideia.inventory.ui.inventory.InventoryRows.Row;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Two kinds of rows: category headers and products. */
public class InventoryAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_HEADER = 0;
    private static final int TYPE_ITEM = 1;

    private List<Row> rows = new ArrayList<>();

    /** Replaces everything: after a re-sort, almost every row changed position anyway. */
    public void setRows(List<Row> rows) {
        this.rows = rows;
        notifyDataSetChanged();
    }

    @Override
    public int getItemCount() {
        return rows.size();
    }

    @Override
    public int getItemViewType(int position) {
        return rows.get(position).isHeader() ? TYPE_HEADER : TYPE_ITEM;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_HEADER) {
            return new HeaderHolder(inflater.inflate(R.layout.item_inventory_header, parent, false));
        }
        return new ItemHolder(inflater.inflate(R.layout.item_inventory, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Row row = rows.get(position);
        if (holder instanceof HeaderHolder) {
            ((HeaderHolder) holder).bind(row.header);
        } else {
            ((ItemHolder) holder).bind(row.item);
        }
    }

    static class HeaderHolder extends RecyclerView.ViewHolder {

        private final TextView title;

        HeaderHolder(View view) {
            super(view);
            title = view.findViewById(R.id.title);
        }

        void bind(Category category) {
            title.setText(category.labelRes);
        }
    }

    static class ItemHolder extends RecyclerView.ViewHolder {

        private final TextView name;
        private final TextView packages;
        private final TextView amount;
        private final TextView updated;

        ItemHolder(View view) {
            super(view);
            name = view.findViewById(R.id.name);
            packages = view.findViewById(R.id.packages);
            amount = view.findViewById(R.id.amount);
            updated = view.findViewById(R.id.updated);
        }

        void bind(InventoryItem item) {
            Locale locale = Locale.getDefault();
            name.setText(item.product.name);
            packages.setText(String.valueOf(item.packages));
            amount.setText(Formats.amount(item.amount, item.product.unit, locale));
            Date date = Formats.parseIso(item.updatedAt);
            updated.setText(date != null ? Formats.shortDate(date, locale) : "");
        }
    }
}
```

### `ui/inventory/InventoryActivity.java` — replace

```java
package com.pessimaideia.inventory.ui.inventory;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.snackbar.Snackbar;
import com.pessimaideia.inventory.App;
import com.pessimaideia.inventory.R;
import com.pessimaideia.inventory.api.ApiCallback;
import com.pessimaideia.inventory.api.ApiException;
import com.pessimaideia.inventory.model.InventoryItem;
import com.pessimaideia.inventory.ui.inventory.InventoryRows.SortColumn;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class InventoryActivity extends AppCompatActivity {

    private static final String PREFS = "inventory";
    private static final String KEY_COLUMN = "sort_column";
    private static final String KEY_ASCENDING = "sort_ascending";

    private final InventoryAdapter adapter = new InventoryAdapter();
    private final Map<SortColumn, TextView> headers = new EnumMap<>(SortColumn.class);

    private SharedPreferences prefs;
    private List<InventoryItem> items = new ArrayList<>();
    private SortColumn column;
    private boolean ascending;

    private RecyclerView list;
    private ProgressBar progress;
    private TextView empty;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_inventory);
        setTitle(R.string.title_inventory);

        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        column = readColumn();
        ascending = prefs.getBoolean(KEY_ASCENDING, true);

        list = findViewById(R.id.list);
        progress = findViewById(R.id.progress);
        empty = findViewById(R.id.empty);
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setAdapter(adapter);

        bindHeader(SortColumn.NAME, R.id.col_name);
        bindHeader(SortColumn.PACKAGES, R.id.col_packages);
        bindHeader(SortColumn.AMOUNT, R.id.col_amount);
        bindHeader(SortColumn.UPDATED, R.id.col_updated);
        updateHeaders();

        load();
    }

    // ---- loading -------------------------------------------------------------------------

    private void load() {
        progress.setVisibility(View.VISIBLE);
        App.from(this).api().getInventory(new ApiCallback<List<InventoryItem>>() {
            @Override
            public void onSuccess(List<InventoryItem> result) {
                if (isDestroyed()) return;
                progress.setVisibility(View.INVISIBLE);
                items = result;
                showRows();
            }

            @Override
            public void onError(ApiException error) {
                if (isDestroyed()) return;
                progress.setVisibility(View.INVISIBLE);
                Snackbar.make(list, getString(R.string.inventory_failed, error.getMessage()),
                                Snackbar.LENGTH_INDEFINITE)
                        .setAction(R.string.retry, v -> load())
                        .show();
            }
        });
    }

    private void showRows() {
        adapter.setRows(InventoryRows.build(items, column, ascending, Locale.getDefault()));
        empty.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
    }

    // ---- sorting -------------------------------------------------------------------------

    private void bindHeader(SortColumn headerColumn, int viewId) {
        TextView view = findViewById(viewId);
        headers.put(headerColumn, view);
        view.setOnClickListener(v -> sortBy(headerColumn));
    }

    /** Same column again flips the direction; a new column starts ascending. */
    private void sortBy(SortColumn newColumn) {
        if (newColumn == column) {
            ascending = !ascending;
        } else {
            column = newColumn;
            ascending = true;
        }
        prefs.edit()
                .putString(KEY_COLUMN, column.name())
                .putBoolean(KEY_ASCENDING, ascending)
                .apply();
        updateHeaders();
        showRows();
    }

    /** Every header shows its label; the active one also shows ▲ or ▼. */
    private void updateHeaders() {
        for (Map.Entry<SortColumn, TextView> entry : headers.entrySet()) {
            String label = getString(entry.getKey().labelRes);
            if (entry.getKey() == column) {
                label = getString(ascending ? R.string.sort_ascending : R.string.sort_descending, label);
            }
            entry.getValue().setText(label);
        }
    }

    private SortColumn readColumn() {
        String saved = prefs.getString(KEY_COLUMN, SortColumn.NAME.name());
        try {
            return SortColumn.valueOf(saved);
        } catch (IllegalArgumentException e) {
            return SortColumn.NAME;   // a column name from an older version of the app
        }
    }
}
```

- `new ApiCallback<List<InventoryItem>>()`: generics nest; the callback delivers a whole list.
- Sorting never asks the API again: `items` keeps the last answer and `showRows()` rebuilds the
  rows from it.
- `headers` is an `EnumMap` too, so `updateHeaders()` visits the columns in a stable order.
- `v -> sortBy(headerColumn)`: each header's listener remembers its own column.

### `AndroidManifest.xml` — `portrait` on the other two activities

`MainActivity` (keep its `<intent-filter>` as it is):

```xml
        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:screenOrientation="portrait">
```

`InventoryActivity`:

```xml
        <activity
            android:name=".ui.inventory.InventoryActivity"
            android:parentActivityName=".MainActivity"
            android:screenOrientation="portrait" />
```

## Verify

`./gradlew test` (24 passing), then `./gradlew installDebug`.

1. Home → **Inventory** → a thin bar flashes, then the column titles with **Name ▲**, and:
   - *Kitchen*: Arroz 5kg `1` `5 kg` `10/09/26`; Feijão 1kg `3` `3 kg` `01/09/26`
   - *Cleaning*: Detergente 500mL `2` `1,000 mL` (pt-BR `1.000 mL`) `05/09/26`
   - *Bathroom*: Papel higiênico 12 rolos `1` `12 un` `05/09/26`
   - no *Other* header (nothing in it). Dates in the device's format.
2. Tap **Packages** → *Packages ▲*, *Name* loses its arrow; Kitchen: Arroz (1) before Feijão (3).
3. Tap **Packages ▲** again → *Packages ▼*; Feijão first.
4. Tap **Updated** → *Updated ▲*; Kitchen: Feijão (01/09) before Arroz (10/09).
5. `adb shell am force-stop com.pessimaideia.inventory`, reopen → Inventory → still *Updated ▲*.
6. Back home → **New session**, scan `7896004000046` (coffee) and `7896004000053` (detergent) →
   **Send** → **Inventory** → Café 500g appears under Kitchen with today's date; Detergente now
   `3` packages / `1,500 mL`.
7. Headers and values line up in columns; the Up arrow / Back returns home.
8. `adb logcat -s Inventory AndroidRuntime` → no stack traces. (Full logcat shows
   `dalvikvm VFY: unable to resolve ... method` warnings from AndroidX: normal on 4.2, the
   libraries check the Android version before calling those. A problem looks like
   `Verifier rejected class`.)

| Symptom | Cause |
|---|---|
| Crash `NoSuchMethodError: java.util.Comparator.reversed` on the device, tests pass | `order.reversed()` used; Android 4.2 has no such method. Use `Collections.reverseOrder(order)`. |
| `ClassCastException: ...ItemHolder cannot be cast to ...HeaderHolder` | `getItemViewType` and `onCreateViewHolder` disagree about which type is which. |
| All rows look like headers (or all like products) | `getItemViewType` not overridden; the default returns 0 for every row. |
| `Óleo` sorted after `Sabão` | Names compared with `compareTo` instead of the `Collator`. |
| Columns don't line up | Different `layout_weight` or padding between `activity_inventory.xml` and `item_inventory.xml`. |
| Sort resets after restart | `apply()` missing after `putString`/`putBoolean`, or `onCreate` doesn't read `prefs`. |
| Arrow stays on the old column | `updateHeaders()` not called in `sortBy`. |
| Crash `IllegalArgumentException: No enum constant ...SortColumn.X` | Unguarded `SortColumn.valueOf(...)`; use `readColumn()` with its `try`. |
| `Resources$NotFoundException` / build error `activity_placeholder` | Something still refers to the deleted layout: check the `setContentView` in both activities. |

## Checklist

- [ ] strings (both languages), `placeholder_coming_soon` removed; `activity_placeholder.xml` deleted
- [ ] `InventoryColumnHeader` style in `themes.xml`
- [ ] `Formats.parseIso` / `shortDate` + 2 tests
- [ ] `InventoryRows.java` + `InventoryRowsTest.java`, `./gradlew test` → 24 passing
- [ ] `activity_inventory.xml`, `item_inventory_header.xml`, `item_inventory.xml`
- [ ] `InventoryAdapter.java`, `InventoryActivity.java`
- [ ] manifest: `portrait` on `MainActivity` and `InventoryActivity`
- [ ] Verify 1–8 on the emulator
- [ ] commit: `git add -A && git commit -m "Step 9: inventory grouped by category with sortable columns"`
