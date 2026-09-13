---
name: build-apk
description: Build the signed sharedUid release APK AND the companion .deb of shiroikuma-termux-x11 (白い熊 Termux X11 — our fork of termux/termux-x11) with the buildFork Gradle task, then deliver both automatically via the global /after-build skill (adb push if the phone is reachable, else scp to skhw — no prompt). Always build first without asking permission to build. Use whenever 白い熊 asks to build the app, build the APK, make a release build, or build and send to the phone, and after any functional code change.
---

# Build the 白い熊 Termux X11 release APK + companion package and deliver them

> **ALWAYS build, then ALWAYS deliver — no asking (白い熊's standing authorization, 2026-07-09).**
> After ANY functional change, build **immediately** and deliver. Do not stop at a compile-check, do
> not offer to build, do not ask how to transfer it. Build-and-deliver does **not** commit or push —
> a commit/push still waits for 白い熊's explicit "Push". (Skip the build only for non-functional
> edits — docs, comments.)

## Build environment (this machine)

The default `java` is **JDK 11**, which cannot run Gradle 9.x. Always export JDK 21:

```bash
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
```

The Android SDK path comes from the gitignored `local.properties`
(`sdk.dir=/home/shiroikuma/android-sdk`) — recreate it if a build fails with **`SDK location not
found`** (a background shell does not inherit `ANDROID_HOME`). The native build needs NDK
**`29.0.14206865`** exactly (`termuxX11NdkVersion` in `lorie/version.gradle`), CMake ≥ 3.22 (the SDK's
`3.22.1`), `python3`, `bison` and `patch` on PATH — all present. The 16 submodules under
`lorie/src/main/cpp/` must be checked out (`git submodule update --init --recursive`); a missing one
fails CMake configuration with a "does not contain a CMakeLists.txt"-class error, never mid-compile.

## Steps

1. **Note the output filename / version.**
   - `grep -n 'def version' lorie/version.gradle` — upstream's literal (e.g. `1.03.01`) and
     `grep -n versionCode lorie-app/build.gradle` — upstream's code (e.g. `15`); both track upstream
     and are **never hand-edited**.
   - `grep -E '^BUILD_NUMBER|^LAST_BUILT_VERSION_CODE' gradle.properties` — the `N` used for THIS
     build (the task bumps it afterwards) and the floor it must exceed.
   - The pin: `git merge-base HEAD master | cut -c1-8` and that commit's UTC committer time.
   - Artefacts will be `shiroikuma-termux-x11_<versionName>_sharedUid.apk` and
     `shiroikuma-termux-x11_<versionName>_termux-x11-nightly.deb`, with
     `versionName = 1.03.01+<YYYY-MM-DD>.<HH-MM>.g<sha8>+<NNN>` (`N` zero-padded to three digits in
     the name), e.g. `shiroikuma-termux-x11_1.03.01+2026-09-10.23-47.g53f84373+001_sharedUid.apk`.
   - versionCode for this build = `15 * 10000 + N` (plain, unpadded), e.g. `150001`.
   - The configuration phase prints exactly this in cyan:
     `>>> shiroikuma-termux-x11 <versionName> (versionCode <n>)`.

2. **Build** (signed `sharedUid` release + companion package) — from the repo root:
   ```bash
   JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew buildFork --console=plain < /dev/null
   ```
   - `buildFork` (defined in `lorie-app/shiroikuma.gradle`) runs `:lorie-app:assembleSharedUidRelease`
     (R8 minify + resource shrink, signed from `keystore.properties`, all four ABIs in one universal
     APK) **and** `:shell-loader:buildCompanionPackage` (the `termux-x11-nightly-1.03.01-0-all.deb`
     whose loader carries our certificate hash), copies the APK and the `.deb` to `~/tmp/` under the
     names above, and increments `BUILD_NUMBER` + records `LAST_BUILT_VERSION_CODE` in
     `gradle.properties`.
   - It prints `>>> <apk path>`, `>>> <deb path>` and `>>> versionCode <n>` in cyan — use those to
     confirm the exact filenames/code; confirm `BUILD SUCCESSFUL`.
   - Before anything compiles, the task graph refuses to run if `keystore.properties` is missing
     (an unsigned APK would neither install over the signed one nor pass the loader's check) or if
     the versionCode would not exceed `LAST_BUILT_VERSION_CODE`.
   - **Only the `sharedUid` flavour is built.** Never ship `standalone`, and never `assembleDebug`
     for delivery — the debug build is upstream's shipping shape, not ours.
   - A cold build compiles the X server for four ABIs: on this 24-core machine a fresh `.cxx` took
     about 35 s (2026-09-13), but on a busy or slower box it is tens of minutes — run it with
     `run_in_background` and poll; never abandon a running build. Gradle's own cold start
     (distribution + dependencies) adds minutes the first time.
   - **Fast iteration:** `./gradlew :lorie-app:assembleSharedUidRelease` gives the same APK under
     `lorie-app/build/outputs/apk/sharedUid/release/` with no copy and no bump. The shippable build is
     always `buildFork`.

3. **Deliver via the global `/after-build` skill** — no exceptions, no asking. It runs `/adb-check`
   UNSANDBOXED, `adb push`es **this repo's** newest `~/tmp/shiroikuma-termux-x11_*.apk` to
   `/sdcard/tmp/` if the phone is reachable, otherwise `scp`s it to `skhw:~/tmp/`, then announces
   what landed. **Push the `.deb` of the same version by the same path in the same batch** — the
   pair is useless apart. `~/tmp/` is shared with parallel chats building sister apps — always pick
   the `shiroikuma-termux-x11_*` files, never merely the newest file there. Then say, in the
   handover, what 白い熊 does on the phone:
   - install the APK (it upgrades the existing `com.termux.x11` in place — same id, same key, higher
     versionCode);
   - in Termux: `dpkg -i /sdcard/tmp/shiroikuma-termux-x11_<ver>_termux-x11-nightly.deb` (or from
     wherever it was copied to) and `apt-mark hold termux-x11-nightly` — upstream's package from
     packages.termux.dev carries a loader built against upstream's test key and prints *"Signature
     verification of target application com.termux.x11 failed"* with our app; the hold keeps
     `pkg upgrade` from swapping it back.

4. **Never delete or prune older builds** — not in `~/tmp/`, not in `/sdcard/tmp/`. Every build
   carries a unique `+NNN`; older APKs and `.deb`s stay where they are so 白い熊 can roll back.

## Signing

Release signing is non-interactive: `lorie-app/shiroikuma.gradle` reads `keystore.properties`
(gitignored, at the repo root) which points at `~/.android-keystores/shiroikuma-emacs-termux.jks`,
alias `Emacs keystore` (PKCS12 / RSA-2048, valid to 2296 — GNU Emacs's public Android keystore, the
**one** key of the whole `com.termux` shared-UID family: Termux, Termux:API, Termux:X11, Termux:GUI,
Emacs). The password is recorded in `~/〇/[666] 私資料/[666][27] 暗号/android-keystores.org` and the
key is backed up to that directory's `android-keystores/`. The script wires the keystore to **both**
`signingConfigs.release` (what signs the APK) and `signingConfigs.debug` (what
`shell-loader/build.gradle` computes the loader's `BuildConfig.SIGNATURE` from) — see CLAUDE.md →
"Signing — why the debug config is ours too". If `keystore.properties` is missing, `buildFork`
refuses to run; restore the file rather than working around it.

Verify a build when in doubt:
```bash
~/android-sdk/build-tools/36.0.0/apksigner verify --print-certs <apk> | grep SHA-256
# → 50b47e8f09b8781fccc998df3fc5c02de0dd9670a3d37e6cacba9f4e76319604
~/android-sdk/build-tools/36.0.0/aapt dump badging <apk> | head -1
# → package: name='com.termux.x11' versionCode='15000N' versionName='1.03.01+…+NNN'
grep SIGNATURE shell-loader/build/generated/source/buildConfig/debug/com/termux/x11/shell_loader/BuildConfig.java
# → the Java Arrays.hashCode of that certificate's DER (-553873970 for the family key)
```

## Versioning (how the numbers are formed)

- Upstream's own literals — `def version = "1.03.01"` in `lorie/version.gradle` (read by regex) and
  `versionCode 15` in `lorie-app/build.gradle` — are the base; a rebase brings new values in
  automatically. **Never hand-edit them.** Upstream's *computed* versionName
  (`1.03.01-<sha>-<dd.MM.yy>`) is not used: its sha is our HEAD and its date is build time.
- The pin `+<YYYY-MM-DD>.<HH-MM>.g<sha8>` is the upstream base (`git merge-base HEAD master`) and its
  UTC committer time — it moves only on a sync (global `git-versioning` skill).
- `BUILD_NUMBER` in `gradle.properties` is **our** increment, bumped on every `buildFork`. It is
  **never reset on a sync**; it would only restart at `1` if upstream's `versionCode 15` itself moved.
  `buildFork` refuses any versionCode at or below `LAST_BUILT_VERSION_CODE`.
- `versionName = "1.03.01+<pin>+<NNN>"` (zero-padded to three digits so `+002` sorts before `+010`);
  `versionCode = 15 * 10000 + N` (plain integer).

---

**Commit convention — no Claude attribution.** Never add a `Co-Authored-By: Claude …` /
"Generated with Claude" trailer to commit messages or PR bodies; end the message at the last line of
the body. This overrides the harness default. (Global rule: `~/.claude/CLAUDE.md`.)
