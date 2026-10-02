# Changelog

This file carries the history of **白い熊 Termux X11**, 白い熊's fork of
[Termux:X11](https://github.com/termux/termux-x11) — and, beneath it, upstream's own changelog the
day they start shipping one. Termux:X11 keeps no `CHANGELOG.md` in its tree today (its history is the
`nightly` release's "Based on <sha>" line and `git log`), so for now the fork's history is the whole
file. Our block always stays at the very top, so an upstream file arriving later inserts below it and
merges cleanly.

Entries are **per-release deltas** — only the first fork release lists everything the fork adds to
stock. Each entry names the upstream commit the build sits on: the fork tracks upstream's `master`
tip, not tags (Termux:X11 has no releases), and the version string pins that commit —
`<upstream version>+<upstream base date>.<HH-MM>.g<sha8>+<NNN>`, with `versionCode` = upstream code
× 10000 + N. Signed builds live on the
[releases page](https://github.com/ShiroiKuma0/shiroikuma-termux-x11/releases); each release carries
**two artefacts at one version** — the `sharedUid` APK and the companion `termux-x11-nightly` `.deb`.

---

## 白い熊 Termux X11 `1.03.01+2026-10-01.03-18.g0e1ebb4c+007` — 2026-10-02

**Upstream sync.** Built on upstream `termux/termux-x11`, branch `master`, commit [`0e1ebb4c`](https://github.com/termux/termux-x11/commit/0e1ebb4c) (2026-10-01 03:18 UTC — *build(deps): bump gradle/actions from 6.3.0 to 6.4.0*), upstream version literal `1.03.01`, `versionCode 15` — both unchanged, so the build counter simply continues (`versionCode 150007`). Eight new upstream commits; one of them touches `TermuxX11ExtraKeys.java`, the file that carries this fork's long-press hook, but in separate hunks — the fork's eleven commits rebased with **no conflict**. No submodule gitlink moved and no `cpp/patches/*.patch` changed.

### The fork's own layer

No fork source changed in this release; every customization was re-verified after the rebase.

- **The gear key's long press keeps working at the gear's new place.** Upstream's new default
  extra-keys layout moves PREFERENCES from the top-right corner to the bottom-right one. The fork's
  `onExtraKeyButtonLongClick` hook matches the key by name, not by position, so a long press on the
  gear still opens the **白い熊 Termux X11 UI** page wherever the gear sits. In the new default the
  gear is a plain key with no swipe-up popup (ZOOM_RESET moved to the KEYBOARD key with it), so the
  long press no longer shares the key with a popup gesture. A layout you have customized yourself is
  untouched — only the default moved.

### The two artefacts

- **`shiroikuma-termux-x11_1.03.01+2026-10-01.03-18.g0e1ebb4c+007_sharedUid.apk`** — the signed
  release build of the `sharedUid` flavour, one universal APK for all four ABIs, `versionCode 150007`;
  installs over `+006` in place.
- **`shiroikuma-termux-x11_1.03.01+2026-10-01.03-18.g0e1ebb4c+007_termux-x11-nightly.deb`** — the
  companion package (the `termux-x11` command and its loader, built against this fork's certificate).
  Its internal Debian version is still `1.03.01-0`, so `dpkg -i` replaces the installed one in place;
  keep `apt-mark hold termux-x11-nightly` set so `pkg upgrade` cannot swap in upstream's loader, which
  refuses this app's signature.

### Upstream since `9c23bd3b` (8 commits)

- **PASTE sends the clipboard as UTF-8 text** (`deb245e`). The PASTE extra key used to push the
  clipboard through Android's virtual-keyboard `KeyCharacterMap`, turning it into synthetic key
  events — anything that map has no key for (Japanese, most non-Latin text, many symbols) was
  silently dropped. It now hands the string to `LorieView.sendTextEvent()` as UTF-8 bytes, the same
  path the soft keyboard's committed text takes, so pasting any text into an X application arrives
  intact. The text field of the toolbar's second page now calls `sendTextEvent()` directly too,
  instead of wrapping its text in a multi-character `KeyEvent` that ended up there anyway.
- **Menu and Break keys work inside X** (`f782b59`). Android's `KEYCODE_MENU` was mapped to
  `KEY_CONTEXT_MENU` and `KEYCODE_BREAK` to `KEY_BREAK`, both beyond X11's 255-keycode limit, so the
  keys did nothing. They now map to `KEY_COMPOSE` (the Menu key of a PC keyboard) and `KEY_PAUSE`.
- **New default extra-keys layout** (`29f57c0`): KEYBOARD and PREFERENCES swap places — the keyboard
  toggle (with ZOOM_RESET as its swipe-up popup) takes the top-right corner, the gear the
  bottom-right one. See above for the gear's long press.
- **Stroked vector icons** (`483fbc7`) for the keyboard, zoom in / out / reset and exit glyphs,
  matching the start screen's icons of the previous sync.
- **The renderer thread is named for the JVM** (`99a33ac`): it attaches as `LorieRendererThread`
  through `AttachCurrentThread`'s arguments instead of renaming itself afterwards with
  `pthread_setname_np`, so Java-side thread dumps no longer show it as `Thread-N`.
- **Dependencies** (Dependabot): Gradle wrapper 9.7.1 → 9.8.0, `androidx.annotation` 1.10.0 → 1.11.0
  (the shell-loader stub), `gradle/actions` 6.3.0 → 6.4.0 (CI only).

### Build pipeline

- The Gradle 9.8.0 wrapper builds this fork unchanged: `lorie-app/shiroikuma.gradle`,
  `gradle.properties` and every upstream build file needed nothing, and the version pin moved by
  itself with the rebase.
- The native build was incremental: `lorie.h`'s keycode table and `renderer.cpp` recompiled and
  `libXlorie.so` relinked for all four ABIs, the X.Org submodules untouched.
- The loader's build-time overrides were re-verified — `shell-loader/build.gradle` is still
  upstream's byte for byte and still reads `signingConfigs.debug` for the certificate constant.

---

## 白い熊 Termux X11 `1.03.01+2026-09-25.09-49.g9c23bd3b+006` — 2026-09-26

**Upstream sync.** Built on upstream `termux/termux-x11`, branch `master`, commit [`9c23bd3b`](https://github.com/termux/termux-x11/commit/9c23bd3b) (2026-09-25 09:49 UTC — *fix(LorieApp.java): avoid duplicate linkToDeath registrations*), upstream version literal `1.03.01`, `versionCode 15` — both unchanged, so the build counter simply continues (`versionCode 150006`). Twenty-five new upstream commits, and for the first time since the fork began they **restructured a file this fork patches**: the notification and the broadcast receiver were moved out of `MainActivity` into the `Application` class, which was then renamed `LorieApp`. The fork's eight commits rebased with exactly one conflict, resolved by porting our change to its new home rather than by re-applying the old diff — see below. No submodule gitlink moved and no `cpp/patches/*.patch` changed, so only upstream's own `cpp/lorie/` sources recompiled.

### The fork's own layer

Two source lines of ours moved or appeared; no fork feature changed behaviour.

- **The notification title followed upstream's refactor.** Our one-line change — the ongoing
  notification titled from `R.string.lorie_app_name` (the `TERMUX_X11_APP_NAME` entity) instead of
  the literal `"Termux:X11"` — lived in `MainActivity.buildNotification()`, which upstream deleted
  outright when it moved the whole notification into the `Application` class. The conflict was
  resolved by taking upstream's deletion in full (`MainActivity.java` is now byte-identical to
  upstream in that region) and re-applying the change at its new site: the cached base notification
  built once in `LorieApp.onCreate()`. The notification channel's id and name were already read from
  the same string resource by upstream's own code, so the channel needed nothing.
- **A new de-branding site, created by upstream's new feature.** `407fa3b` added a fatal error for a
  second X server trying to take over an active connection, with the message hard-coded as
  *"Termux:X11 already has an active X server connection."* — the first user-visible brand string
  upstream has added since the fork's Phase 3. It now reads the app name from
  `R.string.lorie_app_name` too, so the entity stays the single source of brand truth for every
  string the user can see.
- **Both manifests re-anchored** onto the renamed `.LorieApp$Receiver` — the receiver entry our
  automation block sits after in `lorie/src/main/AndroidManifest.xml`, and the line directly above
  our four `android:process="com.termux"` entries in the `sharedUid` overlay. Our
  `ACTION_PREFERENCES_CHANGED` broadcast is addressed by action, never by class, so it was
  unaffected by the rename.
- **The agent documentation followed the rename** — the three `CLAUDE.md` rows that named
  `LorieBroadcastReceiver` or `TermuxX11Application` now name `LorieApp` and `LorieApp$Receiver`.
- Upstream's `styles.xml` change (below) makes the main activity's theme non-DayNight — which is
  what this fork's own `ShiroikumaUiTheme` has always been, so the UI page and the X window now
  agree on their base theme by construction rather than by coincidence.

### The two artefacts

- **`shiroikuma-termux-x11_1.03.01+2026-09-25.09-49.g9c23bd3b+006_sharedUid.apk`** — the signed
  release build of the `sharedUid` flavour, one universal APK for all four ABIs, `versionCode 150006`;
  installs over `+005` in place.
- **`shiroikuma-termux-x11_1.03.01+2026-09-25.09-49.g9c23bd3b+006_termux-x11-nightly.deb`** — the
  companion package (the `termux-x11` command and its loader, built against this fork's certificate).
  Its internal Debian version is still `1.03.01-0`, so `dpkg -i` replaces the installed one in place;
  keep `apt-mark hold termux-x11-nightly` set so `pkg upgrade` cannot swap in upstream's loader, which
  refuses this app's signature.

### Upstream since `a7ae7819` (25 commits)

The largest batch the fork has carried so far, and the only one to touch the app's own structure.
Nothing in it touches the build, the packaging or the sixteen X.Org submodules.

- **The `Application` class absorbed the notification and the broadcast receiver** (nine commits,
  `1df1205` → `05d98fe`). `LorieBroadcastReceiver` first grew into the single dispatcher for every
  broadcast action, caching a pending connection binder so an `ACTION_START` arriving before any
  activity exists is picked up on `MainActivity`'s first connect attempt instead of being lost, with
  one death recipient clearing the cache and disconnecting the live service. It was then folded into
  the `Application` as a nested `Receiver` — the state it held (`pendingConnection`) belonged there
  anyway — and the manifest entry re-pointed at it. The notification followed: `buildNotification()`,
  the channel, posting and cancelling on activity resume/pause, and the refresh after a preference
  change all moved out of `MainActivity`, with the preference-independent parts of the notification
  (and its channel) now built **once** in `onCreate()` and each post recovered from that cached
  `Notification` instead of reconstructing everything and recreating the channel every time. A
  follow-up (`f4f6fdc`) restores the silent flag by hand, because `NotificationCompat.Builder`'s copy
  constructor does not carry the group-alert-behavior tweak `setSilent()` relies on — without it the
  X window's notification would have started making a sound. `TouchInputHandler`'s three notification
  helpers became static, the `Application` now registers one preference-change listener per prefs
  store, `MainActivity` holds the `Application` reference in a field instead of casting at every call
  site, and the class was finally renamed **`LorieApp`** to match `LorieView` / `LoriePreferences`.
- **A conflicting X server now reports a real fatal error** (`407fa3b`, `bd1cfad`, `9c23bd3`).
  `ICmdEntryInterface` gained `reportFatalError()`, which queues `FatalError()` onto the X server's
  own thread so the loser exits through the normal teardown path instead of a raw `exit()`; a second
  `termux-x11` while one is already connected is told so rather than being silently dropped and left
  orphaned. Two follow-ups keep it from misfiring: the *same* binder retrying `ACTION_START` is no
  longer mistaken for a competing server, and `linkToDeath` is no longer registered twice for one
  connection.
- **Keyboard input on the X server thread** (`2beefbf`, `f2bd19d`, `c861882`, `fa7a9cb`). Key events
  are now processed on the server's own thread and `mieq` is drained after physical keys are queued.
  Two Unicode-keysym fixes go with them: the active keyboard is switched *before* keysyms are
  assigned, and the keycode tracking table keeps one entry per keycode, so a keymap replacement can
  no longer leave a duplicate at the LRU tail that recycles a key which had just been assigned and
  used — the cause of characters arriving as the wrong glyph after a soft-keyboard layout change.
- **Zoom panning is now optional** (`b9b56a7`). A new **“Pan to follow cursor while zoomed in”**
  preference (`zoomFollowsCursor`, default on) — turn it off and a zoomed view stops chasing the
  cursor around; the vertical panning that keeps the bottom of the screen clear of the soft keyboard
  is unaffected either way. Alongside it, the renderer's state-lock setters were deduplicated behind
  a `withStateLock` macro (`dc61bc7`).
- **The start screen wears icons, and the theme stopped following the system** (`912b712`,
  `c74e35e`). Preferences, help and exit are now a settings gear, an info glyph and a logout arrow
  sitting closer together, their labels shortened to `Preferences` / `Help` / `Exit`; `LorieAppTheme`
  extends the non-DayNight AppCompat base, so the activity no longer renders light-theme chrome over
  the hard-coded black background of `main_activity.xml`.
- **The mouse and stylus overlay behaves around the IME and the extra-keys bar** (`6e4158f`,
  `fb282e0`, `f5f05b3`). The overlay's visible rect is clamped against the IME height
  unconditionally and against the bar's thickness only when the bar is not already reserving its own
  space; the IME content inset is derived from the bar's own bottom margin in that case instead of
  being independently reseeded. The bar is kept fully opaque whenever it reserves space, since a
  translucent bar there would show content bleeding through a region meant to be exclusively its
  own. And the overlay's click and position buttons now treat `ACTION_CANCEL` like `ACTION_UP`, so a
  gesture the system intercepts — a back-navigation swipe starting on a button — can no longer leave
  it stuck down with its mouse button held.
- **One preference stopped being per-display** (`77b661b`). `enableAccessibilityServiceAutomatically`
  now always reads and writes the built-in store, like `storeSecondaryDisplayPreferencesSeparately`
  already did, instead of whichever store the current display happens to use.
- **Docs** (`051f2e1`): the README's kill recipe is `pkill -f termux-x11`, since the bare form does
  not match the full process name.

### Build pipeline

- Nothing changed in `lorie-app/shiroikuma.gradle`, `gradle.properties` or any upstream build file;
  the version pin moved by itself with the rebase. Upstream landed no Gradle, AGP, wrapper or CI
  commits in this range, so there was no Gradle-side conflict to resolve at all.
- The native build was incremental: `renderer.cpp`, `cmdentrypoint.cpp` and `InputXKB.c` recompiled
  and `libXlorie.so` relinked for all four ABIs, the X.Org submodules untouched.
- The loader's build-time overrides were re-verified after the rebase — `shell-loader/build.gradle`
  is still upstream's byte for byte, still reads `signingConfigs.debug` for the certificate constant,
  and its generated `BuildConfig` still carries this fork's name and `/releases` link.

---

## 白い熊 Termux X11 `1.03.01+2026-09-16.05-25.ga7ae7819+005` — 2026-09-16

**Upstream sync.** Built on upstream `termux/termux-x11`, branch `master`, commit
[`a7ae7819`](https://github.com/termux/termux-x11/commit/a7ae7819) (2026-09-16 05:25 UTC — *fix(InputEventSender.java): track touch IDs and send historical coordinates*), upstream version literal `1.03.01`, `versionCode 15` — both unchanged, so the build counter simply continues (`versionCode 150005`). The fork's six commits were rebased onto the three new upstream commits without a conflict: upstream touched only `activity.cpp`, `LorieView.java` and `InputEventSender.java`, none of which this fork patches, so the file sets did not intersect at all. No submodule gitlink moved, and **no fork feature changed** in this release — it exists to carry upstream's input fixes below.

### The two artefacts

- **`shiroikuma-termux-x11_1.03.01+2026-09-16.05-25.ga7ae7819+005_sharedUid.apk`** — the signed
  release build of the `sharedUid` flavour, one universal APK for all four ABIs, `versionCode 150005`;
  installs over `+004` in place.
- **`shiroikuma-termux-x11_1.03.01+2026-09-16.05-25.ga7ae7819+005_termux-x11-nightly.deb`** — the
  companion package (the `termux-x11` command and its loader, built against this fork's certificate).
  Its internal Debian version is still `1.03.01-0`, so `dpkg -i` replaces the installed one in place;
  keep `apt-mark hold termux-x11-nightly` set so `pkg upgrade` cannot swap in upstream's loader, which
  refuses this app's signature.

### Upstream since `9df6ca26` (3 commits)

All three are fixes by the upstream maintainer, landed the same morning; the batch is entirely
input- and connection-side, with nothing touching the build, the packaging or the X.Org submodules.

- **Touch input — full-resolution drags** (`InputEventSender.java`): an `ACTION_MOVE` now replays
  every *historical* coordinate sample Android batched into the `MotionEvent` before sending the
  current one, instead of sending only the latest point — so a drag or a stroke reaches the X client
  at the digitiser's sampling rate rather than at the frame rate. Alongside it, the stuck-pointer
  workaround was rewritten: the pointer table grew from 10 slots to 32, and a new
  `releaseMissingPointers()` ends only the touch IDs that have genuinely disappeared from the event,
  where the old code blindly sent `XI_TouchEnd` to all ten slots on *every* move. `ACTION_CANCEL`
  now releases every pointer and returns, and `pointers[id]` is kept in step on touch down and up.
- **X server — disconnect ordering** (`cpp/lorie/activity.cpp`): when the X client's socket reports
  error or hang-up, the Java callback `clientConnectedStateChanged` is fired *after* the fd is
  removed from the looper, the connection closed and the renderer's shared state and buffers
  cleared, rather than before — so the Java side no longer observes a half-torn-down connection as
  still live.
- **IME text — surrogate pairs preserved** (`LorieView.java`): appending composing text sends the
  whole newly-added substring as one UTF-8 event instead of one `char` at a time, which had split
  every surrogate pair — emoji and the rarer CJK planes — into two lone halves that are not valid
  UTF-8 on their own.

### Build pipeline

- Nothing changed in `lorie-app/shiroikuma.gradle`; the version pin moved by itself with the rebase.
- No `cpp/patches/*.patch` changed in this sync, so the submodule-reset dance recorded under `+004`
  was not needed: only upstream's own `cpp/lorie/activity.cpp` recompiled, across the four ABIs.

---

## 白い熊 Termux X11 `1.03.01+2026-09-15.13-25.g9df6ca26+004` — 2026-09-15

**Upstream sync.** Built on upstream `termux/termux-x11`, branch `master`, commit
[`9df6ca26`](https://github.com/termux/termux-x11/commit/9df6ca26) (2026-09-15 13:25 UTC — *fix(InitOutput.c): compare Present pixmap height with screen height*), upstream version literal `1.03.01`, `versionCode 15` — both unchanged, so the build counter simply continues (`versionCode 150004`). The fork's four commits were rebased onto the twelve new upstream commits without a conflict, and **no fork feature changed** in this release: it exists to carry upstream's fixes below. (`+003` was built from submodule trees still holding the previous `xserver.patch` — see *Build pipeline* — and was neither delivered nor released.)

### The two artefacts

- **`shiroikuma-termux-x11_1.03.01+2026-09-15.13-25.g9df6ca26+004_sharedUid.apk`** — the signed
  release build of the `sharedUid` flavour, one universal APK for all four ABIs, `versionCode 150004`;
  installs over `+002` in place.
- **`shiroikuma-termux-x11_1.03.01+2026-09-15.13-25.g9df6ca26+004_termux-x11-nightly.deb`** — the
  companion package (the `termux-x11` command and its loader, built against this fork's certificate).
  Its internal Debian version is still `1.03.01-0`, so `dpkg -i` replaces the installed one in place;
  keep `apt-mark hold termux-x11-nightly` set so `pkg upgrade` cannot swap in upstream's loader, which
  refuses this app's signature.

### Upstream since `53f84373` (12 commits)

- **X server — Present:** the wait-fence callback now disarms itself before calling `re_execute()`,
  so `miSyncTriggerFence()`'s restart-scan can no longer fire the same trigger over and over
  (`cpp/patches/xserver.patch`); `InitOutput.c` compares the Present pixmap's height with the screen
  *height* — it had been comparing it with the width.
- **X server — clipboard hardening** (`clipboard.c`, `activity.cpp`, six commits): UTF-8 validation
  made strict per RFC 3629, with the continuation-byte reads bounds-checked (a truncated sequence
  could read past the end of the X property buffer); standalone carriage returns preserved as line
  feeds; requests from clients that have since disconnected discarded; text buffers moved from the
  stack to the heap; and the Android side reads exactly `count` bytes of a clipboard payload instead
  of `count + 1`, which had stolen the first byte of the next protocol message off the socket.
- **`termux-x11` connection knock** (`cmdentrypoint.cpp`): the listener waits at most 200 ms for a
  client's knock (`poll` + non-blocking `recv`), so a stalled client can no longer hang it.
- **Preferences plumbing:** a new `TermuxX11Application` owns the built-in and secondary-display
  `Prefs` and attaches them once at process start (with a `createPackageContext` detour for
  `sharedUid` builds, where a platform-supplied context can identify as the host package);
  `MainActivity.prefs` became an instance field, the static `getPrefs()` is gone, `Prefs` gained a
  no-arg constructor plus `attach()`, and `LoriePreferences`, `LorieView`, `TouchInputHandler`,
  `KeyInterceptor` and the extra-keys code all read through the singleton. The fork's Export / Import
  dumps and merges the same two SharedPreferences files as before, and its post-import
  `ACTION_PREFERENCES_CHANGED` still makes an open window re-read them.
- **`sharedUid` flavour:** the `KeyInterceptor` accessibility service is now pinned to
  `android:process="com.termux"` like every other component — it had been landing in its own default
  process. Directly relevant here, since this fork ships only that flavour.
- **Dependencies:** `kotlin-stdlib-jdk8` 2.4.10 → 2.4.20.

### Build pipeline

- Nothing changed in `lorie-app/shiroikuma.gradle`; the version pin moved by itself with the rebase.
- Recorded for the next sync: upstream applies `cpp/patches/*.patch` **in place** into the submodule
  working trees at CMake configure time with `patch -N`, which cannot upgrade a tree that already
  carries the previous version of a patch — GNU `patch` sees the later hunks as "previously applied"
  and skips the whole file, the new hunk included. After a sync that changes a patch, the submodule
  trees must be reset to their gitlinks (`git submodule foreach 'git checkout -- .'`) and
  `lorie/.cxx` removed before building. `+004` was built that way; `+003`, built before the reset,
  lacked the Present wait-fence fix and was set aside.

---

## 白い熊 Termux X11 `1.03.01+2026-09-10.23-47.g53f84373+002` — 2026-09-13

**First public release.** Built on upstream `termux/termux-x11`, branch `master`, commit
[`53f84373`](https://github.com/termux/termux-x11/commit/53f84373) (2026-09-10 23:47 UTC — *fix(TouchInputHandler): preserve batched relative mouse movement*), upstream version literal `1.03.01`, `versionCode 15`. Everything below is added on top of stock. (`+001` was the build of the identity layer alone — icon, name, links — and was superseded on the phone before publishing; it was never released.)

### The two artefacts, and why the `.deb` is not optional

- **`shiroikuma-termux-x11_1.03.01+2026-09-10.23-47.g53f84373+002_sharedUid.apk`** — the signed
  release build of the `sharedUid` flavour, one universal APK for all four ABIs (`arm64-v8a`,
  `armeabi-v7a`, `x86_64`, `x86`), `versionCode 150002`. Installs **over** the stock Termux:X11
  (app id `com.termux.x11` kept); a stock install signed with upstream's key must be uninstalled
  first.
- **`shiroikuma-termux-x11_1.03.01+2026-09-10.23-47.g53f84373+002_termux-x11-nightly.deb`** — the
  companion package for the Termux prefix (the `termux-x11` command, its loader and the
  `xkeyboard-config` dependency). **It is required:** the `termux-x11` loader verifies the installed
  app's signing certificate (`Signature.hashCode()` against a `BuildConfig.SIGNATURE` computed at
  build time) before it will load it, so the stock `termux-x11-nightly` package from
  `packages.termux.dev` — built against upstream's test key — refuses this app with *"Signature
  verification of target application com.termux.x11 failed"*. Install the fork's `.deb` inside
  Termux and pin it so `pkg upgrade` cannot swap the loader back:
  `dpkg -i shiroikuma-termux-x11_<version>_termux-x11-nightly.deb && apt-mark hold termux-x11-nightly`.
  Its internal Debian version stays upstream's `1.03.01-0`, so it replaces the stock package in
  place; its `Homepage:` names this repository.

### Major features

- **`sharedUid` flavour only, as a signed release build.** Upstream's nightly ships both flavours as
  debug builds; this fork builds and ships exclusively `sharedUid` (`sharedUserId="com.termux"`,
  `android:process="com.termux"` on every component, `targetSdkVersion 28`). The X window, the
  preferences screen and every receiver run inside Termux's own process, so while a graphical app is
  in front Android sees Termux as the foreground app and never moves the shell, its compilers or its
  X clients into the background cpuset. The `standalone` flavour is never built.
- **The 白い熊 Termux X11 UI page** (`ShiroikumaUiActivity` + `ShiroikumaUiFragment`, a
  `PreferenceFragmentCompat` over `preferences_shiroikuma_ui.xml`): black toolbar titled
  「白い熊 Termux X11 UI」, two sections — *Export / Import* (with the automation rows inside it) and
  *Reset*. Four entry points:
  - **long-press on the PREFERENCES (gear) key of the extra-keys bar** — a new generic
    `IExtraKeysView.onExtraKeyButtonLongClick` default hook in `ExtraKeysView` (a `mLongClickRunnable`
    posted after `mLongPressTimeout` for keys that neither repeat nor lock; a consumed long press is
    counted like a repeat so the release does not also click the key), implemented for `PREFERENCES`
    in `TermuxX11ExtraKeys`;
  - **long-press on the Preferences button of the not-connected screen** (`MainActivity`);
  - a **static launcher shortcut** `白い熊 Termux X11 UI` (second `<shortcut>` in upstream's
    `shortcuts.xml` template, carrying the launcher icon);
  - the **first row of upstream's Preferences screen** (`<Preference app:key="shiroikuma_ui">` with an
    `<intent>`; `generatePrefs` ignores it, so no `Prefs.java` field is generated).
  - The notification's *Preferences* action cannot be long-pressed, so there is no entry there.
- **Export / Import of every preference** — see *Backup & automation*.
- **The sister-app backup-automation contract v2** — see *Backup & automation*.
- **The version row of the Preferences screen shows the fork version** (`1.03.01+…+NNN`): `:lorie`'s
  `BuildConfig.VERSION_NAME` is overridden through AGP's variant API from `lorie-app/shiroikuma.gradle`,
  so the screen and the APK always agree and the row names the exact upstream commit in use.

### UI & theming

- **House look for the page** (`ShiroikumaUiTheme`, `shiroikuma_styles.xml`): black ground, yellow
  `#FFFF00` ink, dim `#C8C800` summaries, warning red `#FFFF5252`; yellow-tinted switches and
  checkboxes; black status and navigation bars. Section headings 20 sp bold with a text-wide 2.5 dp
  underline and a 1 px hairline above every section but the first
  (`preference_category_shiroikuma{,_first}.xml`); rows at 72 dp with 5 dp vertical padding and no
  dividers (`preference_shiroikuma_indent1/indent2/subheader.xml`); a `Regenerate` pill widget layout
  (`preference_widget_shiroikuma_regenerate.xml`); the `shiroikuma_pill` drawable.
- **House dialogs** (`ShiroikumaDialogs`): a transparent window whose content is one bordered rounded
  black box (2 dp yellow stroke, 16 dp radius), yellow text, pill buttons (black fill, 1.5 dp yellow
  stroke, 50 dp radius, yellow ripple, no all-caps); confirm dialogs for *Regenerate* and *Reset*;
  chain-closing on success (info dialog → panel → page).
- **The Reset section**: 「Reset the 白い熊 UI preferences」 clears only the page's own
  `shiroikuma_ui` preferences file after a confirm dialog; the app's preferences, the export
  directory and the automation switch are left alone.
- **The activity** is `excludeFromRecents`, shares upstream's `.LoriePreferences` task affinity,
  is resizeable and never enters picture-in-picture.
- No appearance sections and no re-theming of the X window or of upstream's preference screen —
  deliberately; deeper theming only on request.

### Backup & automation

- **Export / Import panel** (`ExportImportPanel`, a port of raikidoban's): a bordered box with a
  centred title and dim description; a tappable **export-directory box** — red while unset, yellow
  once a SAF folder is chosen, with a "chosen again" prompt when the persisted grant was lost with a
  reinstall; the last-export line; 全選択 plus the category checkboxes; the pill row — Cancel alone
  on the left, Import + Export grouped on the right. A successful export ends in an info dialog whose
  OK closes the whole chain; an import ends with 「Later」 / 「Restart now」; failures leave the panel
  open.
- **The archive** (`ShiroikumaExport`): `shiroikuma-termux-x11_<yyyy-MM-dd_HH-mm-ss>.zip`, written as
  `<name>.part` and renamed only when complete. `manifest.json` first (`format`
  `shiroikuma-termux-x11`, `version` 1, `app`, `appVersion`, `createdTs`, `categories[]`), then
  `settings.json` — a type-tagged dump of every SharedPreferences file under `shared_prefs/`
  (`{"<file>":{"<key>":{"t":"int|long|float|bool|string|set","v":…}}}`): today upstream's default
  file `com.termux.x11_preferences` (every `Prefs.java` key — display, pointer, keyboard, extra keys),
  its `secondary` display copy, and our `shiroikuma_ui`. One category, `settings` (on by default) —
  this app keeps no user data in files, the X server's state is Termux's, so there is no `data`
  category.
- **Never exported:** the export directory (`shiroikuma_eximport`) and the automation switch/token
  (`shiroikuma_automation`) are device-local, skipped on export and refused on import even if an
  archive names them.
- **Import** is a per-key **merge** with `commit()` — never a clear — restricted to the categories
  the archive carries; upstream's `ACTION_PREFERENCES_CHANGED` is sent afterwards so an open X window
  redraws with the restored values.
- **「Restart now」 relaunches the X window alone** (`makeRestartActivityTask(MainActivity)`) and
  never calls `Runtime.exit` — in the `sharedUid` flavour the page runs inside Termux's process and an
  exit would kill every Termux session.
- **UI rows:** 「Export / Import…」, 「Export directory」 (SAF tree picker, red "not set"), 「Last
  export」 (probed on resume on a background thread — the newest `shiroikuma-termux-x11_*.zip` in the
  directory, with its size), 「Automation export」 (switch, **ON** by default), 「Use authorization
  token?」 (switch, **OFF**), 「Automation token」 (shown only while the previous switch is on; tap
  copies the token to the clipboard, the *Regenerate* pill asks first and warns that pasted copies
  must be updated).
- **Contract v2 §1 — the headless receiver** (`StateExportReceiver`, exported, no permission):
  `com.termux.x11.action.EXPORT_STATE` (extras `token` — checked only while the token switch is on,
  otherwise ignored, never an error — `path`, `items`, `progress_action`, `reply_action`,
  `reply_package`, `reply_id`), `LIST_CATEGORIES` (one `id<TAB>label<TAB>parent<TAB>on|off` line per
  category), `CANCEL_EXPORT` (fire-and-forget; the running export answers its original request with
  `ERROR:cancelled` and deletes the `.part`). The export runs in the receiver itself (`goAsync()` +
  worker thread — a millisecond preferences dump is the case contract §1 allows there, and it keeps
  the cold-batch path free of foreground-start refusals). Exactly one terminal reply per request,
  guarded by an `AtomicBoolean`: a fresh broadcast to `reply_package` with `FLAG_INCLUDE_STOPPED_PACKAGES`,
  `result` = `OK:<path>|<bytes>|<human size>|<n> categories` or `ERROR:<reason>` — no binders, no
  ordered-broadcast result (EMUI severs both between third-party apps).
- **Storage rule:** the app declares no `MANAGE_EXTERNAL_STORAGE`, so an absolute `path` is honoured
  only when All-Files-Access happens to be held; otherwise the configured SAF directory is used, and
  with neither the reply is `ERROR:no-storage-access` (path given) / `ERROR:no-directory` (no path).
- **Contract v2 §2 — the gate** (`AutomationAuth`, own prefs file `shiroikuma_automation`):
  `automation_enabled` defaults **ON** (so 応用管理 can restore onto a wiped phone where nothing has
  been configured), `automation_require_token` defaults **OFF**, `automation_token` generated lazily;
  every write is `commit()` because the gate fails open and a `SIGKILL` after an `apply()` would
  silently reopen the door.
- **Contract v2 §2a — the data door** (`AutomationProvider`, authority `com.termux.x11.automation`,
  exported with no permission by design): `describe` (answers from package info and prefs alone —
  a provider call is what starts the process on a clean phone), `export`, `import` (exists **only**
  here, never as a broadcast action), `cancel`. Callers are identified by `AutomationCallers`: an
  **exact package name** (`shiroikuma.oyokanri`, `shiroikuma.jiyusagyoban` — never a prefix), the
  **kernel uid** confirmed through `getPackagesForUid`, and a **pinned SHA-256 signing certificate**.
  The bytes travel through a descriptor the caller opened, `dup()`ed by the provider and handed to the
  service through an in-process map, never through an Intent extra.
- **`AutomationDataService`** — a `dataSync` foreground service (new permission
  `FOREGROUND_SERVICE_DATA_SYNC`, notification channel *Automation data* with "Writing preferences
  out…" / "Restoring preferences…" / "Reading the backup…") running the contract's three-step start
  recipe: read the extras, then a guarded `startForeground` (a refusal is answered with the terminal
  broadcast carrying `job_id`, the descriptor closed, the service stopped), then the early returns,
  which stop silently so the single-reply rule holds. `AutomationForeground` decides the answer to a
  refused start: `ERROR:no-foreground-start` (the reserved key 保存中核 turns into a
  「電池最適化を除外」 button) only when the throwable is `ForegroundServiceStartNotAllowedException`
  *and* the app is not already battery-exempt, matched by class name because the class is API 31 and
  `minSdk` is 24. `AutomationJobs` maps every handed-out job id to its cancellation flag,
  process-local and never persisted.
- **Contract v2 §3 — progress** (`AutomationProgress`, one sender for both doors): the correlation id
  written into every requested extra name (`reply_id` for the broadcast door, `job_id` for the
  provider door), a 500 ms throttle, and a **20 s heartbeat** that re-sends the last true line even
  when the numbers have not moved, because 自由作業盤 presumes an app silent for two minutes dead and a
  write into a caller's pipe can block for as long as 応用管理 is slow to drain it. Inert without a
  `progress_action` and a reply package.
- **Manifest:** the activity, receiver, provider and service; `<meta-data
  shiroikuma.automation.{contract=2,format=1,min_format=1}>` for capability discovery without waking
  the app; `<queries>` for `shiroikuma.oyokanri` and `shiroikuma.jiyusagyoban` (without them the
  reply's `setPackage()` fails silently on Android 11+). The `sharedUid` overlay lists all four
  components with `android:process="com.termux"`, so an import's committed values are the very
  SharedPreferences instances `MainActivity` and `LoriePreferences` hold.
- **Shared-process consequence, documented rather than fought:** a force-stop of `com.termux.x11` by
  応用管理 after an automated restore stops Termux's process too — inherent to upstream's `sharedUid`
  design.

### Identity & packaging

- **App name `白い熊 Termux X11`**: the `TERMUX_X11_APP_NAME` ENTITY in `strings.xml` (launcher label,
  notification channel name *and id*, notification title — `buildNotification` now reads
  `R.string.lorie_app_name` instead of a literal); the unused `TERMUX_APP_NAME` ENTITY says
  `白い熊 Termux` for the day upstream references it; the accessibility service is
  `白い熊 Termux X11 KeyInterceptor`.
- **Launcher icon**: black/yellow line tracing — the chevron, the X box with its orbit, the
  underscore — `#FFFF00` strokes on black, sources in `design/shiroikuma-termux-x11-icon.svg`,
  regenerated by `tools/icon/emit_launcher.py`. Upstream's PNGs replaced in place under their own
  names in all five densities (`lorie_ic_launcher`, `_round`, `_foreground`, `_background`,
  `_monochrome`) plus `lorie/src/main/ic_launcher-web.png`, so the `mipmap-anydpi-v26` adaptive-icon
  wrappers are upstream's untouched.
- **Links point at the fork**: the HELP button of the not-connected screen opens
  `ShiroiKuma0/shiroikuma-termux-x11/blob/custom/README.md#running-graphical-applications`; the
  loader's `packageNotInstalledErrorText` names 白い熊 Termux X11, this repo's releases page and the
  `shiroikuma-termux-x11_<version>_sharedUid.apk` file name, and `packageSignatureMismatchErrorText`
  names the fork — both overridden at build time through the variant API, `shell-loader/build.gradle`
  untouched; the companion `.deb`'s `Homepage:` is this repository.
- **App id, namespaces and shared UID unchanged** (`com.termux.x11`, `com.termux.x11.app` /
  `com.termux.x11`, `com.termux`): the `termux-x11` CLI, the loader and Termux's package tooling all
  hardcode the id, so the fork installs over stock rather than beside it.
- **One key for the family**: the release is signed with the keystore shared by 白い熊 Termux,
  Termux API, Termux X11, Termux GUI and 白い熊 GNU Emacs (a shared UID demands one certificate; SHA-256
  `50b47e8f…9604`). `keystore.properties` and every `*.jks` are gitignored (upstream's tracked
  `testkey_untrusted.jks` explicitly exempted); `keystore.properties_sample` documents the four keys.
- **README**: the fork's story on top; upstream's manual kept verbatim beneath it, so the in-app HELP
  anchor keeps resolving.

### Build pipeline

- **`lorie-app/shiroikuma.gradle`**, applied by the single added last line of `lorie-app/build.gradle`
  — everything of ours in the build lives there, so upstream's Gradle files stay byte-identical apart
  from that one `apply` line and a rebase never conflicts in Gradle:
  - **version pin**: `versionName = "<literal>+<base date>.<HH-MM>.g<sha8>+<NNN>"`, the literal read by
    regex from upstream's `lorie/version.gradle` (never edited), the pin = `git merge-base HEAD master`
    (the upstream commit our patches sit on — not our HEAD, not `master`'s tip) with that commit's
    committer time in UTC, resolved through `providers.exec` so the configuration cache re-resolves it
    when the base moves and a clone without `master` degrades to an empty pin instead of failing;
    `versionCode = upstream code × 10000 + BUILD_NUMBER`;
  - **`BUILD_NUMBER` / `LAST_BUILT_VERSION_CODE`** in `gradle.properties`: the counter is zero-padded
    to three digits in the name only, runs **monotonically across syncs** (reset only if upstream's own
    `versionCode` ever moves — an installer compares the code alone), and `buildFork` refuses any
    `versionCode` at or below the last built one;
  - **`forkOverrideBuildConfig`**: AGP variant-API overrides of `:lorie`'s `VERSION_NAME` and
    `:shell-loader`'s two loader messages, reached through the extension's classloader so the script
    needs no AGP compile classpath;
  - **signing**: `signingConfigs.release` created from `keystore.properties` and wired to the release
    build type (upstream leaves it unsigned) — and **`signingConfigs.debug` re-pointed at the same
    keystore**, because `shell-loader/build.gradle` computes the loader's `BuildConfig.SIGNATURE` from
    the *debug* config; every APK this tree can produce therefore carries the one certificate the
    companion package expects, with upstream's file untouched (evaluation order guaranteed by
    upstream's own `evaluationDependsOn`);
  - **release build type**: R8 minify + resource shrink with upstream's `proguard-rules.pro` plus
    `shiroikuma-proguard.pro` (keeps the `Preference` subclasses the page inflates by name),
    `debuggable false` — mirroring the treatment upstream ships on debug;
  - **`androidx.documentfile:documentfile:1.0.1`** added to `:lorie` from here
    (`pluginManager.withPlugin` → `dependencies.add`), so `lorie/build.gradle` stays byte-identical;
  - **`buildFork`** = `:lorie-app:assembleSharedUidRelease` + `:shell-loader:buildCompanionPackage`;
    copies the APK and the `-all.deb` to `~/tmp/` as `shiroikuma-termux-x11_<versionName>_sharedUid.apk`
    and `…_termux-x11-nightly.deb`, then bumps `BUILD_NUMBER` and records `LAST_BUILT_VERSION_CODE`;
    task-graph guards (before the four-ABI native build starts) refuse a missing `keystore.properties`
    and a non-increasing `versionCode`; a cyan banner names the version at configuration time.
- **`.gitignore`**: `/keystore.properties`, `*.jks` (with the `testkey_untrusted.jks` exemption),
  `/.claude/settings.local.json`, `/.scratch/`.
- **Agent documentation**: `CLAUDE.md` (the fork's map, hard rules, the rebase grep guard),
  `.claude/skills/build-apk` (build + delivery of the APK *and* the `.deb`) and
  `.claude/skills/upstream-new-version` (sync with the proceed-gated upstream-changes table; `master`
  fast-forwards, `custom` rebases, submodules follow, `BUILD_NUMBER` is never reset).
- **Icon generator** `tools/icon/emit_launcher.py` — emits the SVG source and the whole mipmap set
  from one geometry description.

### Upstream files touched (the whole list)

`lorie-app/build.gradle` (one `apply` line) · `lorie-app/src/sharedUid/AndroidManifest.xml` (four
components) · `lorie/src/main/AndroidManifest.xml` (label, permission, components, meta-data,
queries) · `lorie/src/main/res/values/strings.xml` (two ENTITY lines) ·
`lorie/src/main/res/xml/preferences.xml` (one row) · `lorie/src/main/templates/xml/shortcuts.xml` (one
shortcut) · `lorie/src/main/java/com/termux/x11/MainActivity.java` (HELP link, long-press,
notification title) · `extrakeys/ExtraKeysView.java` (the long-click hook) ·
`utils/TermuxX11ExtraKeys.java` (its implementation) · `shell-loader/companion-package.gradle`
(`Homepage:`) · the launcher PNGs · `README.md` (fork header above upstream's body) · `.gitignore`.
Every other addition is a new file under `lorie/src/main/java/com/termux/x11/shiroikuma/`,
`lorie/src/main/res/*/…shiroikuma…`, `lorie-app/shiroikuma*`, `design/`, `tools/` and `.claude/`.
The sixteen X.Org submodules carry no fork change.
