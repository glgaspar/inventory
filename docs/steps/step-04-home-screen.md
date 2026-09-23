# Step 4 — Home screen (`MainActivity`)

Goal: the first screen with behavior. Three buttons: **Continue session (N items)** (only when a
session exists), **New session**, **Inventory**. The two target screens do not exist yet, so this
step also creates them as empty placeholders, which lets navigation work and gives Steps 6 and 9
somewhere to grow.

Done when the app runs (emulator or CN51), the Continue button appears only when a session file
exists, and each button opens its screen.

Deviation from PLAN.md: `MainActivity` stays in the root package `com.pessimaideia.inventory`
instead of moving to `ui/`. Moving it is churn for no gain. New screens go under `ui/`.

## Part A — somewhere to run it

Steps 1–3 were pure Java; from here on you need a screen. Two options; set up at least the first.

### A.1 Emulator (API 17, x86)

Already on disk: `~/Android/Sdk/system-images/android-17/{default,google_apis}/x86` and the
emulator binary. Missing: a virtual device (AVD) and KVM permission.

1. KVM permission (once, then log out and back in, or reboot):
   ```sh
   sudo usermod -aG kvm $USER
   ```
   Check after re-login: `groups | grep kvm`. Without it the x86 emulator refuses to boot.
2. Create the AVD in Android Studio: **Tools → Device Manager → Create Virtual Device**.
   - Phone → *Pixel 2* or any small phone (the CN51 is 480×800, 4"). Not a tablet.
   - System image → **x86 Images** tab → API 17 (Jelly Bean 4.2). Choose `default` (no Google
     APIs); both are installed, either works.
   - Name it `cn51-api17`. Under *Advanced*: Graphics = Software, RAM = 1024 MB. Old images are
     unhappy with hardware GL on some machines.
3. Start it from Device Manager (▶) or from a terminal:
   ```sh
   ~/Android/Sdk/emulator/emulator -avd cn51-api17 &
   adb devices        # shows emulator-5554  device
   ```
   First boot of a 4.2 image takes a minute or two.

The emulator has no barcode scanner, but you can type a barcode + Enter with the keyboard (Step 5
is designed for exactly that).

### A.2 CN51 over USB

See checklist item 1.7. When `adb devices` shows the device, everything below is identical.

### A.3 Install & launch (either target)

```sh
./gradlew installDebug
adb shell am start -n com.pessimaideia.inventory/.MainActivity
adb logcat -s Inventory AndroidRuntime      # our logs + crashes
```

`AndroidRuntime` is where uncaught exceptions print. Keep that logcat open in a second terminal
while testing; a crash shows a Java stack trace pointing at your file and line.

## Concepts you meet in this step

| Concept | One-liner |
|---|---|
| Activity lifecycle | `onCreate` runs once when the screen is built; `onResume` every time it comes to the front (including coming back from another screen). Refresh state in `onResume`. |
| Layout XML | Screens are declared in `res/layout/*.xml`; `setContentView` inflates one. `@+id/name` creates an id; `findViewById(R.id.name)` fetches the view in Java. |
| Listener | `button.setOnClickListener(v -> ...)`: a lambda that runs when tapped. The `v` parameter is the tapped view; you usually ignore it. |
| `Intent` | A message to the system: "start this Activity". `startActivity(new Intent(this, X.class))`. |
| `AlertDialog` | Modal yes/no dialog. Built with a `Builder`, chained calls, `.show()` at the end. |
| Visibility | `view.setVisibility(View.VISIBLE)` / `View.GONE`. GONE also removes the space it took. |
| Plurals | `res/values/strings.xml` can define `<plurals>` so "1 item" / "3 items" (and Portuguese rules) are handled by the system. |
| Manifest registration | Every Activity must be declared in the manifest or `startActivity` throws `ActivityNotFoundException`. |
| `parentActivityName` | Tells Android which screen the ◀ (Up) arrow in the action bar returns to. |

## Files

### `res/values/strings.xml` — add

```xml
    <string name="home_new_session">New session</string>
    <string name="home_inventory">Inventory</string>
    <plurals name="home_continue_session">
        <item quantity="one">Continue session (%d item)</item>
        <item quantity="other">Continue session (%d items)</item>
    </plurals>

    <string name="discard_session_title">Discard current session?</string>
    <string name="discard_session_message">The items already scanned will be lost.</string>
    <string name="discard">Discard</string>
    <string name="cancel">Cancel</string>

    <string name="title_session">Scanning</string>
    <string name="title_inventory">Inventory</string>
    <string name="placeholder_coming_soon">Coming soon</string>
```

### `res/values-pt-rBR/strings.xml` — add

```xml
    <string name="home_new_session">Nova entrada</string>
    <string name="home_inventory">Estoque</string>
    <plurals name="home_continue_session">
        <item quantity="one">Continuar entrada (%d item)</item>
        <item quantity="other">Continuar entrada (%d itens)</item>
    </plurals>

    <string name="discard_session_title">Descartar a entrada atual?</string>
    <string name="discard_session_message">Os itens já lidos serão perdidos.</string>
    <string name="discard">Descartar</string>
    <string name="cancel">Cancelar</string>

    <string name="title_session">Leitura</string>
    <string name="title_inventory">Estoque</string>
    <string name="placeholder_coming_soon">Em breve</string>
```

### `res/layout/activity_main.xml` — replace

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical"
    android:gravity="center_vertical"
    android:padding="24dp">

    <Button
        android:id="@+id/btn_continue"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:minHeight="56dp"
        android:textSize="18sp"
        android:visibility="gone"
        android:layout_marginBottom="16dp" />

    <Button
        android:id="@+id/btn_new_session"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:minHeight="56dp"
        android:textSize="18sp"
        android:layout_marginBottom="16dp"
        android:text="@string/home_new_session" />

    <Button
        android:id="@+id/btn_inventory"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:minHeight="56dp"
        android:textSize="18sp"
        android:text="@string/home_inventory" />

</LinearLayout>
```

Units: `dp` = density-independent pixels (layout sizes), `sp` = scaled pixels (text, follows the
user's font size). 56 dp buttons are easy to hit on a small resistive-feeling screen.
`btn_continue` has no `android:text`: its label comes from a `<plurals>` resource, which XML
cannot reference (`@string/home_continue_session` fails with "resource string/... not found").
The text is set in Java in `onResume` via `getQuantityString`.

### `res/layout/activity_placeholder.xml` — new (used by both placeholder screens)

```xml
<?xml version="1.0" encoding="utf-8"?>
<TextView xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:gravity="center"
    android:textSize="20sp"
    android:text="@string/placeholder_coming_soon" />
```

### `ui/session/SessionActivity.java` — placeholder

Folder `app/src/main/java/com/pessimaideia/inventory/ui/session/`.

```java
package com.pessimaideia.inventory.ui.session;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.pessimaideia.inventory.R;

public class SessionActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_placeholder);
        setTitle(R.string.title_session);
    }
}
```

### `ui/inventory/InventoryActivity.java` — placeholder

Same file with package `com.pessimaideia.inventory.ui.inventory`, class `InventoryActivity`,
title `R.string.title_inventory`.

### `AndroidManifest.xml` — register both, inside `<application>` after `MainActivity`

```xml
        <activity
            android:name=".ui.session.SessionActivity"
            android:parentActivityName=".MainActivity" />
        <activity
            android:name=".ui.inventory.InventoryActivity"
            android:parentActivityName=".MainActivity" />
```

Also add `android:screenOrientation="portrait"` to all three `<activity>` tags. The CN51 is used
one-handed in portrait, and rotation would recreate the Activity mid-session (a whole topic we
sidestep this way).

### `MainActivity.java` — replace

```java
package com.pessimaideia.inventory;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.pessimaideia.inventory.data.SessionStore;
import com.pessimaideia.inventory.ui.inventory.InventoryActivity;
import com.pessimaideia.inventory.ui.session.SessionActivity;

public class MainActivity extends AppCompatActivity {

    private SessionStore sessionStore;
    private Button continueButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        sessionStore = App.from(this).sessionStore();
        continueButton = findViewById(R.id.btn_continue);

        continueButton.setOnClickListener(v -> openSession());
        findViewById(R.id.btn_new_session).setOnClickListener(v -> startNewSession());
        findViewById(R.id.btn_inventory).setOnClickListener(v ->
                startActivity(new Intent(this, InventoryActivity.class)));
    }

    /** Called every time this screen comes to the front, so the button reflects the file on disk. */
    @Override
    protected void onResume() {
        super.onResume();
        int count = sessionStore.load().size();
        if (count > 0) {
            continueButton.setText(getResources()
                    .getQuantityString(R.plurals.home_continue_session, count, count));
            continueButton.setVisibility(View.VISIBLE);
        } else {
            continueButton.setVisibility(View.GONE);
        }
    }

    private void startNewSession() {
        if (!sessionStore.hasSession()) {
            openSession();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.discard_session_title)
                .setMessage(R.string.discard_session_message)
                .setPositiveButton(R.string.discard, (dialog, which) -> {
                    sessionStore.clear();
                    openSession();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void openSession() {
        startActivity(new Intent(this, SessionActivity.class));
    }
}
```

Reading notes:
- `findViewById(R.id.btn_continue)` returns the right type automatically because of the field's
  declared type (`Button`). For the other two we never store the view, so the generic `View`
  is enough to attach a listener.
- `v -> openSession()`: a lambda with one parameter and an expression body.
  `(dialog, which) -> { ... }`: two parameters and a block body.
- `getQuantityString(id, count, count)`: the first `count` picks the plural form, the second fills
  `%d`. Yes, you pass it twice.
- `AlertDialog` from `androidx.appcompat.app`, not `android.app`: the AppCompat one is themed
  consistently on old devices.
- `private` methods and fields: only this class can see them. Default to `private`; open up only
  when something outside needs access.
- `import` order is a convention, not a rule: `android.*`, `androidx.*`, third-party, `java.*`.

## Verify

1. `./gradlew installDebug`, launch. Home shows two buttons (no session file yet).
2. Tap **Inventory** → "Coming soon" with title *Inventory*; ◀ Up arrow returns home.
3. Tap **New session** → "Coming soon" with title *Scanning*. Back.
4. Fake a session to test the Continue button (there is no UI to create one yet). In a terminal:
   ```sh
   echo '[{"product":{"id":1,"barcode":"7896004000015","name":"Arroz 5kg","category":"kitchen","unit":"kg","packageSize":5},"packages":2}]' > /tmp/session.json
   adb push /tmp/session.json /data/local/tmp/session.json
   adb shell "run-as com.pessimaideia.inventory sh -c 'mkdir -p files && cat /data/local/tmp/session.json > files/session.json'"
   ```
   If `run-as` is not available on your build, skip this and verify the button in Step 6 instead.
   Leave the app and come back (Home key, then reopen): **Continue session (1 item)** appears.
5. Tap **New session** → discard dialog → *Discard* → the placeholder opens; go back home and the
   Continue button is gone.
6. Switch the device language to Português (Brasil) in Settings and confirm the buttons and dialog
   are translated, then switch back (or leave it).
7. `adb logcat -s Inventory AndroidRuntime` shows no stack traces.

| Symptom | Cause |
|---|---|
| `ActivityNotFoundException: Unable to find explicit activity class` | The Activity is not in the manifest, or the `android:name` path is wrong. |
| `ClassCastException ... cannot be cast to ...App` | `android:name=".App"` missing on `<application>`. |
| `NullPointerException` at `setOnClickListener` | The `R.id` in Java does not match the `@+id` in XML, or `setContentView` used the wrong layout. |
| `VerifyError: com/google/gson/internal/reflect/ReflectionHelper` once a session exists | Gson 2.10+ uses `ReflectiveOperationException` (API 19). Use Gson 2.8.9. |
| Text shows `%d` literally | `getQuantityString` called with only two arguments. |
| Emulator: `KVM is required` / `/dev/kvm permission denied` | Not in the `kvm` group yet, or you did not log out after `usermod`. |

## Checklist

- [ ] A.1 emulator boots (or A.2 device connected) and `adb devices` lists it
- [ ] strings (both languages)
- [ ] `activity_main.xml`, `activity_placeholder.xml`
- [ ] `ui/session/SessionActivity.java`, `ui/inventory/InventoryActivity.java`
- [ ] manifest: two activities registered, portrait on all three
- [ ] `MainActivity.java`
- [ ] Verify 1–7
- [ ] commit: `git add -A && git commit -m "Step 4: home screen with session continue/new and placeholders"`
