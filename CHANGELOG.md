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
