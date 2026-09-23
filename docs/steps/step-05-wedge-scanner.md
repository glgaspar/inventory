# Step 5 — Scanner input (`ScannerInput` + `WedgeScannerInput`)

Goal: the Scanning screen receives barcodes. The CN51's Virtual Wedge "types" the barcode as key
presses and ends with Enter, like a very fast keyboard. We catch that with an invisible text
field that always has focus, and hand each finished code to the Activity through a small
interface. For now the Activity just shows a `Toast`; Step 6 does the real lookup.

Done when, on the Scanning screen, typing `7896004000015` + Enter on your PC keyboard (emulator)
or pulling the trigger (CN51) shows **Scanned: 7896004000015**, once per scan, with no on-screen
keyboard popping up.

Why an interface: the wedge is the simplest option and needs no Intermec SDK. If it proves
flaky, a `BroadcastReceiver` for Intermec's Data Intent can implement the same `ScannerInput`,
and the Activities do not change.

No unit test this step. Everything here is Android views and key events, which the desktop JVM
cannot run (`returnDefaultValues = true` would turn them into silent no-ops). The emulator is
the test.

## Concepts you meet in this step

| Concept | One-liner |
|---|---|
| Nested interface | `ScannerInput.Listener` is an interface declared inside another. Used as `ScannerInput.Listener`; it groups the callback with the thing that calls it. |
| `implements` | `class WedgeScannerInput implements ScannerInput` promises to provide every method of the interface. `@Override` on each one makes the compiler check that you did. |
| Program to the interface | `private final ScannerInput scanner = new WedgeScannerInput();` The field's type is the interface, so swapping the implementation later is one line. |
| Method reference | `this::onScan` is shorthand for the lambda `code -> onScan(code)`. Works when the method's parameters match the interface's single method. |
| Views created in code | `new EditText(activity)` + `parent.addView(view, layoutParams)` does what a layout XML tag does, without touching the Activity's layout file. |
| `android.R` vs `R` | `R.id.x` is your app's resources; `android.R.id.content` is the platform's: the frame every Activity's layout is placed into. |
| Focus | Only the focused view receives key presses. In touch mode, tapping a `Button` does **not** take focus away from an `EditText`, which is why a hidden field keeps working. |
| Key events come in pairs | Every key press is an `ACTION_DOWN` then an `ACTION_UP`. React to only one of them, or every scan fires twice. |
| `Toast` | Short message at the bottom of the screen: `Toast.makeText(context, text, Toast.LENGTH_SHORT).show()`. Forgetting `.show()` is the classic bug. |
| Format strings | `<string name="x">Scanned: %1$s</string>` + `getString(R.string.x, value)`. `%1$s` = first argument, as text. The translation can move it anywhere in the sentence. |

## Files

### `res/values/strings.xml` — add

```xml
    <string name="scanned_toast">Scanned: %1$s</string>
```

### `res/values-pt-rBR/strings.xml` — add

```xml
    <string name="scanned_toast">Lido: %1$s</string>
```

### `scanner/ScannerInput.java` — new

New folder `app/src/main/java/com/pessimaideia/inventory/scanner/`.

```java
package com.pessimaideia.inventory.scanner;

import android.app.Activity;

/** Delivers scanned barcodes to a screen, whatever the hardware does underneath. */
public interface ScannerInput {

    interface Listener {
        void onScan(String barcode);
    }

    /** Call once in onCreate, after setContentView. */
    void attach(Activity activity, Listener listener);

    /** Call in onResume and after a dialog closes, so the next scan lands here again. */
    void requestFocus();
}
```

Methods in an interface are automatically `public` and have no body; the implementing class
supplies the body.

### `scanner/WedgeScannerInput.java` — new

```java
package com.pessimaideia.inventory.scanner;

import android.app.Activity;
import android.graphics.Color;
import android.text.InputType;
import android.util.Log;
import android.view.KeyEvent;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;

/**
 * Keyboard-wedge scanner: the scanner types the barcode followed by Enter into whatever has
 * focus. We make that a 1x1 transparent EditText that keeps focus.
 */
public class WedgeScannerInput implements ScannerInput {

    private static final String TAG = "Inventory";

    private EditText field;
    private Listener listener;

    @Override
    public void attach(Activity activity, Listener listener) {
        this.listener = listener;

        field = new EditText(activity);
        field.setSingleLine(true);
        field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        field.setImeOptions(EditorInfo.IME_ACTION_DONE);
        field.setBackgroundColor(Color.TRANSPARENT);
        field.setTextColor(Color.TRANSPARENT);
        field.setCursorVisible(false);
        field.setOnEditorActionListener((view, actionId, event) -> handleAction(actionId, event));

        ViewGroup root = activity.findViewById(android.R.id.content);
        root.addView(field, new ViewGroup.LayoutParams(1, 1));
        requestFocus();
    }

    @Override
    public void requestFocus() {
        if (field != null) {
            field.requestFocus();
        }
    }

    /** Returns true when we handled the key, so the EditText does not also act on it. */
    private boolean handleAction(int actionId, KeyEvent event) {
        if (event != null) {
            // Hardware Enter (scanner or PC keyboard) arrives twice: DOWN, then UP.
            if (!isEnter(event.getKeyCode())) return false;
            if (event.getAction() == KeyEvent.ACTION_DOWN) emit();
            return true;
        }
        // On-screen keyboard's "Done" key has no KeyEvent.
        if (actionId == EditorInfo.IME_ACTION_DONE) {
            emit();
            return true;
        }
        return false;
    }

    private static boolean isEnter(int keyCode) {
        return keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER;
    }

    private void emit() {
        String barcode = field.getText().toString().trim();
        field.setText("");
        if (barcode.isEmpty()) return;
        Log.d(TAG, "Scanned: " + barcode);
        listener.onScan(barcode);
    }
}
```

Line by line, the non-obvious bits:

- `setSingleLine(true)`: without it, Enter inserts a newline into the text instead of triggering
  the editor action listener.
- `TYPE_TEXT_FLAG_NO_SUGGESTIONS`: stops the keyboard from "autocorrecting" a barcode.
- Transparent background + text + no cursor: the field is there and focusable but invisible.
  `setVisibility(View.INVISIBLE)` would not work: invisible views cannot take focus.
- `activity.findViewById(android.R.id.content)`: the `FrameLayout` your layout was placed into.
  Adding the field there means no layout file needs to know about the scanner.
- `new ViewGroup.LayoutParams(1, 1)`: 1×1 pixel. Zero would make some Android versions skip it
  for focus.
- `handleAction` returns `true` for the `ACTION_UP` too, without emitting. That swallows the
  second half of the Enter press so it cannot reach anything else.
- `emit()` clears the field **before** calling the listener, so if the listener is slow or opens
  a dialog, the next scan starts from an empty field.

### `ui/session/SessionActivity.java` — replace

```java
package com.pessimaideia.inventory.ui.session;

import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.pessimaideia.inventory.R;
import com.pessimaideia.inventory.scanner.ScannerInput;
import com.pessimaideia.inventory.scanner.WedgeScannerInput;

public class SessionActivity extends AppCompatActivity {

    private final ScannerInput scanner = new WedgeScannerInput();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_placeholder);
        setTitle(R.string.title_session);

        scanner.attach(this, this::onScan);
    }

    @Override
    protected void onResume() {
        super.onResume();
        scanner.requestFocus();
    }

    private void onScan(String barcode) {
        Toast.makeText(this, getString(R.string.scanned_toast, barcode), Toast.LENGTH_SHORT).show();
    }
}
```

`scanner.attach(...)` must come **after** `setContentView`: `setContentView` removes everything
inside `android.R.id.content` first, which would throw away the hidden field.

### `AndroidManifest.xml` — `SessionActivity` tag

```xml
        <activity
            android:name=".ui.session.SessionActivity"
            android:parentActivityName=".MainActivity"
            android:screenOrientation="portrait"
            android:windowSoftInputMode="stateAlwaysHidden" />
```

`stateAlwaysHidden`: the field has focus, and on a device without a hardware keyboard Android
would pop the on-screen keyboard over half the screen. This keeps it closed; the wedge does not
need it.

`screenOrientation="portrait"` was in Step 4 but is not in the manifest yet. Add it to all three
`<activity>` tags now: rotating recreates the Activity, which would drop the session screen's
state from Step 6 on.

## Verify

1. `./gradlew installDebug`, open the app, tap **New session** → *Scanning* screen. No on-screen
   keyboard appears.
2. Click inside the emulator screen once (so it has your keyboard's attention), then type
   `7896004000015` and press Enter → toast **Scanned: 7896004000015**. Exactly one toast.
3. Type `123` + Enter, then `456` + Enter → the second toast says `456`, not `123456` (the field
   was cleared).
4. Press Enter alone → nothing happens.
5. Tap somewhere on the screen, then type a code + Enter → still works (focus kept).
6. Press Home, reopen the app, type a code + Enter → still works (`onResume` re-focused).
7. Use the **main** Enter key. On Android 4.2 the numpad Enter is not treated as an editor
   action: it types a newline into the field instead, and the next scan comes out as
   `999\n111`. The scanner sends the main Enter, so this only matters when typing by hand.
   (`isEnter` still checks `KEYCODE_NUMPAD_ENTER`; newer Android versions do route it here.)
8. `adb logcat -s Inventory AndroidRuntime` shows `D/Inventory: Scanned: ...` per scan and no
   stack traces.
9. Later, on the CN51 (checklist 1.7): trigger a scan of any product → one toast with the code.

| Symptom | Cause |
|---|---|
| Two toasts per scan | `ACTION_UP` not filtered: `emit()` must only run for `ACTION_DOWN`. |
| Typing does nothing, no log line | Field has no focus. `attach` was called before `setContentView`, or `requestFocus()` is missing in `onResume`. |
| Enter moves nothing, text grows `123456...` | `setSingleLine(true)` missing, so Enter is not an editor action. |
| On-screen keyboard pops up | `windowSoftInputMode` missing in the manifest, or the AVD has *Enable keyboard input* off (Device Manager → Edit → Advanced). |
| Toast shows `Scanned: %1$s` | `getString(R.string.scanned_toast)` called without the `barcode` argument. |
| Nothing shows, but the log line is there | `Toast.makeText(...)` without `.show()`. |
| `incompatible types: View cannot be converted to ViewGroup` | You are compiling against an old SDK; cast: `(ViewGroup) activity.findViewById(...)`. |
| CN51: codes arrive with a trailing character, or never "finish" | Virtual Wedge suffix is not Enter. Set suffix = Enter (`\n` / `<CR>`) in the Intermec settings. |

## Checklist

- [ ] strings (both languages)
- [ ] `scanner/ScannerInput.java`
- [ ] `scanner/WedgeScannerInput.java`
- [ ] `ui/session/SessionActivity.java`
- [ ] manifest: `windowSoftInputMode` on `SessionActivity`, `portrait` on all three
- [ ] Verify 1–8 on the emulator (9 waits for the CN51)
- [ ] commit: `git add -A && git commit -m "Step 5: wedge scanner input on the session screen"`
