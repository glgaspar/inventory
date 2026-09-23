# Step 1 — Models + `Category` enum

Goal: the "nouns" of the app as plain Java classes. No Android code yet, so this is the best
place to learn the language basics. Done when `./gradlew assembleDebug` passes.

## Java in five sentences (enough for this step)

1. Every public class lives in its own file with the same name, inside a folder matching its `package`.
2. A class has **fields** (data), a **constructor** (how to build one), and **methods**.
3. `final` on a field means it is set once in the constructor and never changes — that is how we make
   simple immutable data objects.
4. `this.name = name` distinguishes the field from the constructor parameter of the same name.
5. An `enum` is a class with a fixed set of named instances.

## Files to create

All under `app/src/main/java/com/pessimaideia/inventory/model/` (create the `model` folder).

### `Category.java`

```java
package com.pessimaideia.inventory.model;

import com.google.gson.annotations.SerializedName;

import com.pessimaideia.inventory.R;

/** Where a product is stored. Backend uses the lowercase names. */
public enum Category {
    @SerializedName("kitchen")  KITCHEN(R.string.category_kitchen),
    @SerializedName("cleaning") CLEANING(R.string.category_cleaning),
    @SerializedName("bathroom") BATHROOM(R.string.category_bathroom),
    @SerializedName("other")    OTHER(R.string.category_other);

    /** Resource id of the translated display name (see strings.xml). */
    public final int labelRes;

    Category(int labelRes) {
        this.labelRes = labelRes;
    }
}
```

What is going on:
- The four capitalised lines are the only four `Category` objects that will ever exist.
- Each carries `labelRes`, an `int` that `R` generated from `strings.xml`, so the UI can show
  "Cozinha" or "Kitchen" depending on the device locale.
- `@SerializedName` is an **annotation**: metadata that Gson reads so `KITCHEN` becomes `"kitchen"`
  in JSON and back.
- An enum constructor is private by default, so nobody can create a fifth category.

### `Unit.java`

```java
package com.pessimaideia.inventory.model;

import com.google.gson.annotations.SerializedName;

/** Unit of measure of a product's amount. */
public enum Unit {
    @SerializedName("un") UN("un"),
    @SerializedName("kg") KG("kg"),
    @SerializedName("g")  G("g"),
    @SerializedName("L")  L("L"),
    @SerializedName("mL") ML("mL");

    /** Short symbol shown next to amounts, same in every language. */
    public final String symbol;

    Unit(String symbol) {
        this.symbol = symbol;
    }
}
```

### `Product.java`

```java
package com.pessimaideia.inventory.model;

import androidx.annotation.Nullable;

/** A product the backend knows, identified by its barcode. */
public class Product {

    /** 0 means "not saved in the backend yet". */
    public final long id;
    public final String barcode;
    public final String name;
    public final Category category;
    public final Unit unit;
    /** How much of {@link #unit} one scanned package holds, e.g. rice 5 kg → 5. */
    public final double packageSize;
    @Nullable
    public final String imageUrl;

    public Product(long id, String barcode, String name, Category category,
                   Unit unit, double packageSize, @Nullable String imageUrl) {
        this.id = id;
        this.barcode = barcode;
        this.name = name;
        this.category = category;
        this.unit = unit;
        this.packageSize = packageSize;
        this.imageUrl = imageUrl;
    }
}
```

Types you are meeting: `long` = 64-bit integer, `double` = floating-point number, `String` = text.
Java has no null-safety in the type system, so `@Nullable` is a note to you and to Android Studio
that this field may be `null`; everything else we treat as never null.
Public final fields instead of getters is a deliberate simplification. You will see `getName()`
everywhere in Android code; we can switch later.

### `SessionItem.java`

```java
package com.pessimaideia.inventory.model;

/** One row of the scanning session: a product and how many packages were scanned. */
public class SessionItem {

    public final Product product;
    /** Not final: the user can change it with +/- or by re-scanning. */
    public int packages;

    public SessionItem(Product product, int packages) {
        this.product = product;
        this.packages = packages;
    }

    /** Total amount in the product's unit. */
    public double amount() {
        return packages * product.packageSize;
    }
}
```

First method with logic. Note the return type comes before the name, and that `int * double`
gives a `double` automatically.

### `Movement.java`

```java
package com.pessimaideia.inventory.model;

import com.google.gson.annotations.SerializedName;

/** One entry ("in") or usage ("out") of a product, as the backend records it. */
public class Movement {

    public enum Type {
        @SerializedName("in")  IN,
        @SerializedName("out") OUT
    }

    public final long id;
    public final long productId;
    public final Type type;
    public final double amount;
    public final int packages;
    /** ISO-8601 UTC timestamp exactly as the backend sends it, e.g. 2026-09-22T13:05:00Z. */
    public final String at;

    public Movement(long id, long productId, Type type, double amount, int packages, String at) {
        this.id = id;
        this.productId = productId;
        this.type = type;
        this.amount = amount;
        this.packages = packages;
        this.at = at;
    }
}
```

An enum can be nested inside a class; from outside you refer to it as `Movement.Type.IN`.
Timestamps stay as strings in the model because that is what the JSON carries. We parse them only
when displaying.

### `InventoryItem.java`

```java
package com.pessimaideia.inventory.model;

/** Current stock of one product, from GET /inventory. */
public class InventoryItem {

    public final Product product;
    public final double amount;
    public final int packages;
    public final String updatedAt;

    public InventoryItem(Product product, double amount, int packages, String updatedAt) {
        this.product = product;
        this.amount = amount;
        this.packages = packages;
        this.updatedAt = updatedAt;
    }
}
```

## Strings

`Category` references four resources that do not exist yet, so the build would fail without these.

Replace `app/src/main/res/values/strings.xml` with:

```xml
<resources>
    <string name="app_name">Inventory</string>

    <string name="category_kitchen">Kitchen</string>
    <string name="category_cleaning">Cleaning</string>
    <string name="category_bathroom">Bathroom</string>
    <string name="category_other">Other</string>
</resources>
```

Create the folder `app/src/main/res/values-pt-rBR/` with a `strings.xml`:

```xml
<resources>
    <string name="app_name">Inventário</string>

    <string name="category_kitchen">Cozinha</string>
    <string name="category_cleaning">Limpeza</string>
    <string name="category_bathroom">Banheiro</string>
    <string name="category_other">Outros</string>
</resources>
```

The folder suffix `pt-rBR` is what makes Android pick it when the device language is Brazilian
Portuguese. Every string must exist in the default folder; the translated one may omit strings and
Android falls back to the default.

## Verify

```sh
./gradlew assembleDebug
```

The models have no behavior to test yet, but compiling proves the syntax, imports and resource
references are right. Typical first-timer errors:

| Compiler message | Meaning |
|---|---|
| `cannot find symbol` | typo in a name, or a missing `import` |
| `';' expected` | missing semicolon on the previous line |
| `class X is public, should be declared in a file named X.java` | filename and class name differ |
| `error: package com.pessimaideia.inventory.R does not exist` | `strings.xml` not saved / `R` not regenerated — rebuild |

## Checklist

- [ ] `model/Category.java`
- [ ] `model/Unit.java`
- [ ] `model/Product.java`
- [ ] `model/SessionItem.java`
- [ ] `model/Movement.java`
- [ ] `model/InventoryItem.java`
- [ ] `values/strings.xml` updated
- [ ] `values-pt-rBR/strings.xml` created
- [ ] `./gradlew assembleDebug` → BUILD SUCCESSFUL
- [ ] commit: `git commit -am "Step 1: model classes"` (use `git add -A` first for new files)
