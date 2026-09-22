# Inventario — Android (Java) grocery inventory client for Intermec CN51

## Context

Gustavo wants a home-inventory app (kitchen / cleaning / bathroom stock) that runs on an
Intermec CN51 handheld (Android 4.2.2, API 17) and uses its built-in barcode scanner.
The Android app is only the UI: scan → identify product via API → adjust amounts → send.
The backend (CRUD API over Postgres) will be built later in another repo, so the app
starts with a **mocked API** and the endpoints get documented as the app consumes them.

Gustavo has never written Java. This plan is a **guide**, not a build-it-for-me: I explain
what to do and why, review, and help with Java syntax/idioms. He writes the code.

### What is already on disk (`/home/gustavo/p/inventario`)
An Android Studio "Empty Views Activity" project, package `com.pessimaideia.inventory`,
Gradle 9.6 wrapper, AGP 9.4.1, `libs.versions.toml` catalog. Problems found:

| Problem | Where | Why it matters |
|---|---|---|
| `android {}` + `dependencies {}` pasted into **root** `build.gradle` | `build.gradle` | Root project has no Android plugin applied → build fails. Only `app/build.gradle` may have those blocks. |
| `minSdk 23` | `app/build.gradle` | CN51 is API 17. App would refuse to install. |
| `appcompat 1.8.0`, `material 1.14.0`, `constraintlayout 2.2.2`, `activity-ktx 1.13` | `gradle/libs.versions.toml` | All require minSdk ≥ 21/23/24. Must pin to last API-17-compatible versions. |
| `MainActivity` uses `EdgeToEdge`, `WindowInsetsCompat` | `app/src/main/java/.../MainActivity.java` | Comes from `activity-ktx` (Kotlin lib). Replace with plain `AppCompatActivity`. |
| `org.gradle.configuration-cache=true` | `gradle.properties` | Fine to keep; mention only if odd errors appear. |

Environment on this machine (Linux Mint 22.3, x86_64): system OpenJDK 17 (`/usr/bin/java`),
Android Studio at `/opt/android-studio` (bundled JBR 25), SDK at `~/Android/Sdk` with
`platforms/android-17`, `platforms/android-37.0`, `build-tools/36.0.0`, `platform-tools/adb`.
Nothing needs downloading except maybe SDK sources. `ANDROID_HOME` and `adb` are not on PATH.

---

## Part 1 — Project & environment configuration (step by step)

### 1.1 Shell environment
Add to `~/.zshrc`:
```sh
export ANDROID_HOME="$HOME/Android/Sdk"
export PATH="$PATH:$ANDROID_HOME/platform-tools"
```
Then `source ~/.zshrc` and check `adb version`.
Gradle uses system JDK 17 (OK: Gradle 9.6 needs 17+, AGP 9.4 needs 17+). Android Studio uses its
own JBR 25 for the IDE; both work. Optional: `Settings → Build → Gradle → Gradle JDK` = 17.

### 1.2 Fix root `build.gradle`
Replace the whole file with only:
```groovy
plugins {
    alias(libs.plugins.android.application) apply false
}
```

### 1.3 Pin API-17-compatible library versions — `gradle/libs.versions.toml`
Replace the `[versions]` / `[libraries]` values with these (last releases before AndroidX raised
its minSdk floor; verify each by syncing — Gradle prints a clear "requires minSdk N" error if wrong):

```toml
[versions]
agp = "9.4.1"
junit = "4.13.2"
appcompat = "1.6.1"          # 1.7.0+ needs 21
recyclerview = "1.3.2"       # 1.4.0+ needs 21
constraintlayout = "2.1.4"
material = "1.9.0"           # if sync complains, drop to 1.6.1
gson = "2.10.1"
glide = "4.16.0"             # image loading, minSdk 14
okhttp = "3.12.13"           # last line supporting API < 21; only needed in Part 3

[libraries]
junit            = { group = "junit", name = "junit", version.ref = "junit" }
appcompat        = { group = "androidx.appcompat", name = "appcompat", version.ref = "appcompat" }
recyclerview     = { group = "androidx.recyclerview", name = "recyclerview", version.ref = "recyclerview" }
constraintlayout = { group = "androidx.constraintlayout", name = "constraintlayout", version.ref = "constraintlayout" }
material         = { group = "com.google.android.material", name = "material", version.ref = "material" }
gson             = { group = "com.google.code.gson", name = "gson", version.ref = "gson" }
glide            = { group = "com.github.bumptech.glide", name = "glide", version.ref = "glide" }
okhttp           = { group = "com.squareup.okhttp3", name = "okhttp", version.ref = "okhttp" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
```
Remove `activity-ktx`, `espresso`, `ext-junit` (instrumented tests are not worth it on a 4.2 device).

### 1.4 `app/build.gradle`
```groovy
plugins { alias(libs.plugins.android.application) }

android {
    namespace 'com.pessimaideia.inventory'
    compileSdk { version = release(37) }      // compile against new SDK is fine; runtime is 17

    defaultConfig {
        applicationId "com.pessimaideia.inventory"
        minSdk 17
        targetSdk 37
        versionCode 1
        versionName "0.1"
    }
    buildTypes {
        release { optimization { enable false } }
    }
    compileOptions {                           // Java 8 syntax (lambdas) is desugared by AGP for any minSdk
        sourceCompatibility JavaVersion.VERSION_1_8
        targetCompatibility JavaVersion.VERSION_1_8
    }
}

dependencies {
    implementation libs.appcompat
    implementation libs.recyclerview
    implementation libs.constraintlayout
    implementation libs.material
    implementation libs.gson
    implementation libs.glide
    testImplementation libs.junit
}
```
Rules for API 17 that we must respect in code: no `java.time`, no `java.util.stream`/`Optional`
(these need `coreLibraryDesugaring` — skip it), use `long` timestamps + `SimpleDateFormat`;
lambdas are fine. HTTP later must be plain `http://` (Android 4.2 has TLS 1.2 disabled by default).

### 1.5 Manifest & MainActivity
- `AndroidManifest.xml`: add `<uses-permission android:name="android.permission.INTERNET"/>`;
  delete `dataExtractionRules`/`fullBackupContent` attributes and the two `res/xml` files
  (they are for API 31+; harmless but noise). Delete `res/mipmap-anydpi-v26` if lint complains? No — keep, it's version-qualified.
- `themes.xml`: parent `Theme.MaterialComponents.DayNight.DarkActionBar` (or `Theme.AppCompat.DayNight.DarkActionBar` if Material dropped).
- `MainActivity.java`: strip `EdgeToEdge`/insets code → just `setContentView`.
- Delete `ExampleInstrumentedTest.java` and the `androidTest` folder.

### 1.6 Sync & first build
```sh
cd ~/p/inventario && ./gradlew assembleDebug
```
Expect the wrapper to download Gradle 9.6 once. Fix whatever "requires minSdk" messages appear by
lowering that library's version.

### 1.7 Device connection (CN51)
1. On the CN51: `Settings → About → tap Build number 7×` → `Developer options → USB debugging ON`.
2. Linux udev rule so adb sees it without root: create `/etc/udev/rules.d/51-android.rules` with
   `SUBSYSTEM=="usb", ATTR{idVendor}=="067e", MODE="0666", GROUP="plugdev"` (067e = Intermec; confirm
   with `lsusb`), then `sudo udevadm control --reload-rules`, replug.
3. `adb devices` → accept the RSA prompt on the device.
4. `./gradlew installDebug` or Run ▶ in Android Studio. `adb logcat -s Inventario` for our logs
   (we will tag all logs `"Inventario"`).
5. Scanner: `Settings → Intermec Settings → Data Collection → Internal Scanner` — confirm
   **Virtual Wedge** is enabled and set the **suffix** to `\n` (Enter / carriage return). This makes
   a scan behave like typing the barcode + Enter into whatever EditText has focus.

### 1.8 Git
`git init`, commit the fixed skeleton. `.gitignore` already covers `build/`, `.gradle/`, `local.properties`.

---

## Part 2 — App architecture (what we are building)

Keep it small, plain Java, no frameworks. One package per concern:

```
com.pessimaideia.inventory
├── model/        Product, Category(enum), SessionItem, Movement, InventoryItem   (plain data classes)
├── api/          InventoryApi (interface), ApiCallback<T>, MockInventoryApi, later HttpInventoryApi
├── data/         SessionStore (persists the in-progress session as JSON on disk)
├── scanner/      ScannerInput (interface), WedgeScannerInput (hidden EditText), later IntentScannerInput
├── ui/
│   ├── MainActivity            home: "Continue session (N items)" / "New session" / "Inventory"
│   ├── session/  SessionActivity, SessionAdapter, NewProductDialog
│   └── inventory/ InventoryActivity, InventoryAdapter, ProductDetailActivity, MovementAdapter
└── App.java      Application subclass: holds the single InventoryApi + SessionStore instances
```

Threading rule (Java concept #1 for Android): network/disk work happens off the main thread.
`InventoryApi` methods are asynchronous: `void getProductByBarcode(String barcode, ApiCallback<Product> cb)`.
The mock runs on a background `ExecutorService` with a fake 300 ms delay and posts results back with
a `Handler(Looper.getMainLooper())`, so the real HTTP implementation later drops in unchanged.

Session persistence: `SessionStore` writes `List<SessionItem>` to `filesDir/session.json` via Gson
after every change; on app start, a non-empty file = "continue session". Cancel → delete file.
Send → POST, then delete file. (No SQLite needed: the inventory itself lives in the backend.)

Scanner abstraction: `ScannerInput` has `void attach(Activity, Listener)`. `WedgeScannerInput` puts a
1 dp invisible `EditText` that keeps focus (re-request focus on `onResume` and after dialogs close),
listens for Enter (`setOnEditorActionListener` + `KEYCODE_ENTER`), emits the trimmed text, clears it.
If the wedge proves flaky, swap in a `BroadcastReceiver` for Intermec's Data Intent without touching
the activities.

Units: a product has a `unit` (`"un"`, `"kg"`, `"g"`, `"L"`, `"mL"`) and a `packageSize`
(e.g. rice 5 kg → unit `kg`, packageSize 5). A scan adds **1 package**; the session row shows
packages and lets the user edit the count. Amount sent = packages × packageSize.

Categories: enum `Category { KITCHEN, CLEANING, BATHROOM, OTHER }` with a display-name string
resource each. Backend uses the same lowercase strings.

Language: follow the device's preferred language. All user-visible text goes in
`res/values/strings.xml` (English, the default) and `res/values-pt-rBR/strings.xml` (Portuguese).
Android picks the folder matching the device locale automatically; never hardcode text in
layouts or Java. Category display names, units and date formats use this same mechanism
(`SimpleDateFormat` with `Locale.getDefault()`).

---

## Part 3 — API contract (documented as the app consumes it; mocked first)

Base URL configurable in `App` (`BuildConfig` field later). All JSON, `Content-Type: application/json`.
Timestamps are ISO-8601 strings in UTC (`2026-09-22T13:05:00Z`). Live in `docs/API.md` in the repo.

| # | Method & path | Used by | Request | Response |
|---|---|---|---|---|
| 1 | `GET /products/by-barcode/{barcode}` | scan | — | `200 Product` / `404` |
| 2 | `POST /products` | register unknown code | `Product` without id | `201 Product` |
| 3 | `POST /movements` | "Send" session | `{ "type":"in", "items":[{ "productId", "packages", "amount" }], "at": ts }` | `201 { "id" }` |
| 4 | `GET /inventory` | inventory list | — | `200 [InventoryItem]` |
| 5 | `GET /products/{id}` | detail | — | `200 Product` |
| 6 | `GET /products/{id}/movements` | detail history | — | `200 [Movement]` (newest first) |

```jsonc
Product       { "id": 12, "barcode": "7891000100103", "name": "Arroz Tio João 5kg",
                "category": "kitchen", "unit": "kg", "packageSize": 5, "imageUrl": "http://.../12.jpg" }
InventoryItem { "product": Product, "amount": 15, "packages": 3, "updatedAt": ts }
Movement      { "id": 301, "productId": 12, "type": "in" | "out", "amount": 5, "packages": 1, "at": ts }
```
`type:"out"` (usage) exists in the contract because the detail screen shows it; the app only
produces `"in"` for now. Images: for the mock, `imageUrl` can be `null` → show a placeholder;
Glide loads real URLs later.

`MockInventoryApi` seeds ~8 products across 3 categories, a few movements, and returns 404 for
unknown barcodes so the register flow can be exercised. Registered products get appended in memory.

---

## Part 4 — Frontend implementation, step by step (each step = one runnable increment)

I coach; Gustavo writes. Each step ends with "run on device, see it work, commit".

**Step 0 – Skeleton compiles & installs (Part 1).** Blank screen with app name on the CN51.

**Step 1 – Models + Category enum.** Plain classes with public final fields or getters,
constructor, `equals`/`hashCode` on id. Java concepts: classes, enums with fields, `final`,
packages/imports, `@Nullable`.

**Step 2 – `InventoryApi` interface + `ApiCallback<T>` + `MockInventoryApi`.**
Java concepts: interfaces, generics, anonymous classes vs lambdas, `ExecutorService`,
`Handler`/main thread, `HashMap`/`ArrayList`. Unit test in `src/test` with JUnit:
`getProductByBarcode("known")` returns product, `"unknown"` → not found. (Handler needs a small
trick in JVM tests: allow injecting a synchronous "poster"; simplest is a `MainThreadPoster`
interface with a real and a direct implementation.)

**Step 3 – `App` (Application subclass, registered in manifest) + `SessionStore`.**
Gson to/from file in `getFilesDir()`. Java concepts: file IO with try-with-resources, checked
exceptions, `Gson` + `TypeToken` for `List<SessionItem>`.

**Step 4 – `MainActivity` home.** Three buttons; "Continue session (3 items)" shown only when
`SessionStore.load()` is non-empty; "New session" asks to discard an existing one. Layout in
`activity_main.xml` (LinearLayout is enough). Android concepts: Activity lifecycle, `Intent`
to start another activity, `onResume` to refresh the button label.

**Step 5 – `WedgeScannerInput`.** Hidden EditText + Enter detection. Test standalone in
`SessionActivity` by just `Toast`-ing the scanned code. Also test with the on-screen keyboard
typing a code + Enter (so development works without the scanner).

**Step 6 – `SessionActivity` list.** `RecyclerView` + `SessionAdapter` (ViewHolder pattern) with
rows: image thumb, name, packages `[-] 1 [+]`, remove. Scan → API lookup → if found: if already
in list, packages+1, else add row; persist via `SessionStore` after each change. Show a
`ProgressBar` while looking up; ignore scans while a lookup is in flight (queue or drop).
Java/Android concepts: RecyclerView adapter, `notifyItemChanged`, listeners passing from
adapter to activity.

**Step 7 – Register unknown product.** On 404: `NewProductDialog` (DialogFragment) pre-filled with
barcode; fields name, category spinner, unit spinner, package size. Save → `POST /products` (mock)
→ then add to session as if scanned. Re-focus the hidden EditText when the dialog closes.

**Step 8 – Send / Cancel.** Toolbar actions. Send → build `POST /movements` body → on success
clear store, toast, back to home. Cancel → confirm dialog → clear store. Handle API failure with a
retry snackbar (session stays on disk, nothing lost).

**Step 9 – `InventoryActivity`.** `GET /inventory` → group by category with header rows
(adapter with two view types) → tap column headers (Name / Packages / Amount / Updated) to sort
within each group, toggling asc/desc; remember last sort in `SharedPreferences`.
Java concepts: `Comparator`, `Collections.sort`, sorting a `Map<Category, List<...>>`.

**Step 10 – `ProductDetailActivity`.** Header (image via Glide, name, amount + unit, packages)
+ `RecyclerView` of movements with datetime (`SimpleDateFormat`, device local time) and signed
amount (`+5 kg` / `−1 un`).

**Step 11 – Polish for the CN51.** Hardware keys: keep the scan trigger from also sending
keystrokes elsewhere; disable screen rotation (`android:screenOrientation="portrait"`);
larger touch targets (48 dp); `android:windowSoftInputMode="stateAlwaysHidden"` on the session
screen so the wedge EditText never pops the keyboard.

**Step 12 (later, separate repo exists) – `HttpInventoryApi`.** OkHttp 3.12 + Gson, same
interface, base URL in `BuildConfig`. Flip one line in `App`. Add a "Server URL" settings screen.

Each step's "definition of done" is visible on the device, so the unfamiliar parts (Java) are
learned with immediate feedback.

---

## Files to create/modify (summary)

Modify: `build.gradle`, `app/build.gradle`, `gradle/libs.versions.toml`, `app/src/main/AndroidManifest.xml`,
`app/src/main/res/values/themes.xml`, `app/src/main/java/com/pessimaideia/inventory/MainActivity.java`.
Delete: `app/src/androidTest/`, `res/xml/backup_rules.xml`, `res/xml/data_extraction_rules.xml`.
Create: `docs/API.md`, `docs/PLAN.md` (this plan, trimmed), then the packages listed in Part 2.

## Verification
- `./gradlew assembleDebug` succeeds; `./gradlew test` runs the mock API tests.
- `adb install -r app/build/outputs/apk/debug/app-debug.apk` on the CN51 (API 17) installs without
  `INSTALL_FAILED_OLDER_SDK`.
- On device: scan a seeded barcode → row appears; scan again → packages 2; kill app → reopen → "Continue
  session (1 item)"; scan unknown code → register dialog → row appears; Send → home; Inventory → grouped,
  sortable; tap item → detail with history.
- `adb logcat -s Inventario` shows no uncaught exceptions during the flow.

## Working agreement (confirmed)
Gustavo types **everything**, including the Gradle/config edits in Part 1. I explain each edit and
each Java/Android concept as it comes up, give the exact content to type, and review the result
(reading files, running `./gradlew assembleDebug`, `adb` checks). After approval, first action:
walk through Part 1 edits one at a time, verifying the build after 1.6, then Step 1.
