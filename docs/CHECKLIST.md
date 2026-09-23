# Progress checklist

Companion to [PLAN.md](PLAN.md). Tick items as they are verified on disk / on the device.

## Part 1 — Project & environment

- [x] 1.1 Shell env: `ANDROID_HOME` + `platform-tools` on PATH (`~/.zshrc`)
- [x] 1.2 Root `build.gradle` only declares the plugin
- [x] 1.3 `gradle/libs.versions.toml` pinned to API-17-compatible versions
- [x] 1.4 `app/build.gradle`: minSdk 17, Java 8, dependencies from catalog
- [x] 1.5 Manifest (INTERNET, no backup rules), MaterialComponents theme, plain `MainActivity`, `androidTest` removed
- [x] 1.6 `./gradlew assembleDebug` → BUILD SUCCESSFUL, APK reports `minSdkVersion 17`
- [ ] 1.7 Device connection — **deferred: no USB cable available**
  - [x] udev rule `/etc/udev/rules.d/51-android.rules` for vendor `067e`; user in `plugdev`
  - [ ] CN51: Developer options → USB debugging on; USB mode MTP/PTP (not mass storage)
  - [ ] Accept the RSA prompt on first connection; `adb devices` shows `device`
  - [ ] `./gradlew installDebug` and launch → screen shows "Inventory" (Step 0 on device)
  - [ ] Scanner: Virtual Wedge enabled, suffix = Enter; test in a text field
- [x] 1.8 `git init` + first commit

## Part 4 — App steps

- [x] Step 0 Skeleton compiles (device install pending 1.7)
- [x] Step 1 Models + `Category` enum ([step-01-models.md](steps/step-01-models.md))
- [x] Step 2 `InventoryApi`, `ApiCallback`, `MockInventoryApi` + JUnit test ([step-02-api-mock.md](steps/step-02-api-mock.md))
- [x] Step 3 `App` + `SessionStore` ([step-03-app-and-session-store.md](steps/step-03-app-and-session-store.md)) — log tag is `Inventory`, so use `adb logcat -s Inventory`
- [x] Step 4 `MainActivity` home screen ([step-04-home-screen.md](steps/step-04-home-screen.md)) — verified on emulator `cn51-api17`; Gson downgraded to 2.8.9 (VerifyError on API 17)
- [x] Step 5 `ScannerInput` + `WedgeScannerInput` ([step-05-wedge-scanner.md](steps/step-05-wedge-scanner.md))
- [x] Step 6 `SessionActivity` list ([step-06-session-list.md](steps/step-06-session-list.md))
- [x] Step 7 Register unknown product dialog ([step-07-new-product-dialog.md](steps/step-07-new-product-dialog.md))
- [x] Step 8 Send / Cancel ([step-08-send-cancel.md](steps/step-08-send-cancel.md))
- [x] Step 9 `InventoryActivity` ([step-09-inventory.md](steps/step-09-inventory.md))
- [x] Step 10 `ProductDetailActivity` ([step-10-product-detail.md](steps/step-10-product-detail.md))
- [ ] Step 11 CN51 polish
- [ ] Step 12 `HttpInventoryApi` (after backend exists)


Until 1.7 is done, steps 1–3 are verifiable with `./gradlew test` on the desktop; steps 4+ can be
smoke-tested in an emulator (`API 17` system image) or wait for the device.
