---
name: upstream-new-version
description: Sync the shiroikuma-termux-x11 fork onto the newest termux/termux-x11 master — fast-forward the master mirror, rebase custom, update the submodules, build the next +NNN (BUILD_NUMBER is NOT reset). Use when 白い熊 says upstream has new commits, asks to check/update/sync to upstream, or to rebase custom onto the latest Termux:X11. ALWAYS present the proceed-gated upstream-changes table BEFORE rebasing.
---

# Sync shiroikuma-termux-x11 onto new upstream Termux:X11 commits

This fork tracks [termux/termux-x11](https://github.com/termux/termux-x11) — the Termux X server
add-on. `master` mirrors **`upstream/master`** (fast-forward only); `custom` carries our patches and
is rebased onto it.

**We follow the branch tip, not tags** (`Upstream tracking: git`). Termux:X11 has no releases: its
only tag, `nightly`, is moved by CI onto every `master` commit, and its version literal (`1.03.01`,
`versionCode 15`) stands still for months. A sync therefore happens whenever **`upstream/master` has
moved past our `master`**, and the fork version records which upstream commit we sit on through the
`+<date>.<HH-MM>.g<sha8>` pin (global `git-versioning` skill) — not through a reset counter.

> **Never `git push` or `git commit` unprompted.** After the rebase + build you stop and let 白い熊
> test; you push only on their explicit **"Push"** (`custom` needs `--force-with-lease` after a
> rebase; `master` fast-forwards with a plain push).

## Branch / remote model

| Branch | Role | Update mode |
| --- | --- | --- |
| `master` | Mirrors `upstream/master`. No fork work here. | `git merge --ff-only upstream/master` |
| `custom` | Our patches; the working/dev branch and the GitHub default branch. | rebased onto `master` each sync |

`origin` = `git@github.com:ShiroiKuma0/shiroikuma-termux-x11.git` (push). `upstream` =
`https://github.com/termux/termux-x11.git` (fetch only; push URL `DISABLED`). Sixteen submodules
under `lorie/src/main/cpp/` follow upstream's gitlinks — we never carry a submodule change.

## Steps

1. **Check for new upstream commits:**
   ```bash
   git fetch upstream
   if git merge-base --is-ancestor upstream/master master; then echo "already current"; fi
   old=$(git rev-parse master)                       # capture BEFORE anything moves
   git rev-list --count master..upstream/master      # how many new upstream commits
   git show master:lorie/version.gradle          | grep 'def version'      # old literal
   git show upstream/master:lorie/version.gradle | grep 'def version'      # new literal
   git show master:lorie-app/build.gradle          | grep versionCode        # old code
   git show upstream/master:lorie-app/build.gradle | grep versionCode        # new code
   git diff --stat master upstream/master -- .gitmodules lorie/src/main/cpp | tail -3   # submodules moved?
   ```
   The trigger is `upstream/master` **not** being an ancestor of `master`. If it is, stop and report
   "already current" with the current pin. Report old/new literal and code (they almost never
   change) and the commit count. If a gitlink moved, say so: the next build recompiles that
   submodule's part of the X server.

2. **⛔ PROCEED GATE — present the upstream changes as a table, then STOP.** 白い熊's standing
   requirement: **before** anything is rebased, show what the new upstream commits actually bring.

   Gather the material from all of these — they complement each other:
   ```bash
   git log --oneline --no-merges "$old"..upstream/master          # what really landed
   git log --merges --format='%s' "$old"..upstream/master         # which PRs were merged
   git log --format='%n### %h  %s%n%b' "$old"..upstream/master    # full bodies, to judge relevance
   git diff --stat "$old"..upstream/master                        # where the weight is
   gh release view nightly -R termux/termux-x11 2>/dev/null | head -5   # only "Based on <sha>"; no notes
   ```
   Dependabot bumps (Gradle wrapper, AGP, Kotlin stdlib, actions) are folded into **one** row, never
   listed individually; the weekly CI-cleanup and wrapper-update commits likewise.

   Present a **descriptive markdown table** — one row per feature/change, in plain language, not raw
   commit subjects:

   | Area | Change | What it means for us |
   | --- | --- | --- |
   | X server / native | … | … |
   | Input (touch, mouse, keyboard, extra keys) | … | … |
   | Preferences / UI | … | … |
   | Loader / companion package | … | … |
   | Build / deps / CI | … | … |

   Cover features, fixes, native-stack changes (moved submodules, new patches under
   `lorie/src/main/cpp/patches/`), and anything touching files our patches own — **flag those rows**,
   they are the likely conflict sites: `lorie-app/build.gradle` (our one `apply from` line at the
   end), `gradle.properties`, `.gitignore`, `lorie/version.gradle` (read by regex — a changed shape
   of the `def version = "…"` line breaks the pin), `shell-loader/build.gradle` (the SIGNATURE source
   — must keep reading `signingConfigs.debug`), `shell-loader/companion-package.gradle` (the `.deb`
   name `buildFork` globs for), `lorie/src/main/res/values/strings.xml` (the `TERMUX_X11_APP_NAME`
   ENTITY), the launcher mipmaps, `MainActivity.java`'s help link, and the README. Also flag a
   `versionCode` or `targetSdkVersion` change in `lorie-app/build.gradle`.

   Also state the stack size (`git rev-list --count master..custom`) and the plan.

   **Then stop and wait for 白い熊's explicit go-ahead.** Do not move `master`, do not rebase, do not
   build until they say proceed. If they decline, nothing has been touched.

3. **Fast-forward `master`** (mirror; no fork work lives here) and park a safety branch:
   ```bash
   git status --short                       # must be clean (unsandboxed — the sandbox invents dotfiles)
   git checkout master
   git merge --ff-only upstream/master
   git branch custom-pre-$(date +%Y-%m-%d) custom    # the untouched stack, in case the rebase goes wrong
   ```
   (`master` is pushed only at "Push" time, together with `custom`.)

4. **Rebase `custom`:**
   ```bash
   git checkout custom
   git rebase master
   git submodule update --init --recursive     # gitlinks may have moved with the base
   ```
   Resolve conflicts so **all** our customizations survive (table below). Reconcile, don't drop: if
   upstream restructured a file we patch, port our change to the new structure rather than forcing
   the old diff. Keep **upstream's** version literals — our script reads them, never edits them.
   Never resolve a conflict by touching a submodule or its gitlink. **If conflicts are significant,
   stop and plan with 白い熊** before continuing. If the rebase goes irrecoverable:
   `git rebase --abort`, then `git reset --hard custom-pre-<date>` puts `custom` back.

5. **Do NOT reset `BUILD_NUMBER`.** It keeps counting across syncs: the new pin lands in the
   versionName by itself, and an installer compares only `versionCode = 15 * 10000 + N`. Reset it to
   `1` **only** if upstream's `versionCode 15` itself moved (then `LAST_BUILT_VERSION_CODE` also
   still guards against going backwards — a moved code times 10000 always clears it).

6. **Verify our customizations are intact after the rebase:**

   | What | Expected | Where |
   | --- | --- | --- |
   | Installed app id | `com.termux.x11` (upstream's, untouched) | `lorie-app/build.gradle` → `defaultConfig.applicationId` |
   | Code namespaces | `com.termux.x11.app` / `com.termux.x11` (unchanged) | `lorie-app/build.gradle`, `lorie/build.gradle` |
   | Fork hook | `apply from: 'shiroikuma.gradle'` is still the **last** line | `lorie-app/build.gradle` |
   | Fork script | version pin, `signingConfigs.debug` + `release` from `keystore.properties`, release R8 block, `buildFork` | `lorie-app/shiroikuma.gradle` |
   | Version literal shape | `def version = "…"` still matches the regex in `shiroikuma.gradle` | `lorie/version.gradle` |
   | SIGNATURE source | still `project(':lorie-app').android.signingConfigs.debug` | `shell-loader/build.gradle` |
   | Companion name | `buildCompanionPackage` still emits `termux-x11-nightly-<ver>-all.deb` | `shell-loader/companion-package.gradle` |
   | Build tail | `BUILD_NUMBER` **unchanged**, `LAST_BUILT_VERSION_CODE` present | `gradle.properties` |
   | App label | `白い熊 Termux X11` via the `TERMUX_X11_APP_NAME` ENTITY (once Phase 3 has landed) | `lorie/src/main/res/values/strings.xml` |
   | Black-yellow icon | yellow line-art foreground + black background, all densities (once Phase 2 has landed) | `lorie/src/main/res/mipmap-*/lorie_ic_launcher*` |
   | De-branding | our name + our GitHub links in every user-visible string, the help button, the loader's error text, root docs (once Phase 3 has landed) | `strings.xml`, `MainActivity.java`, `shell-loader/build.gradle`, `README.md` |
   | Committed agent files | `CLAUDE.md`, `.claude/skills/` tracked; only `.claude/settings.local.json` ignored | `.gitignore` |

   Watch for **new upstream strings that reintroduce "Termux:X11" or upstream's links** — grep after
   the rebase and re-de-brand what Phase 3 owns:
   ```bash
   grep -rn "Termux:X11\|termux/termux-x11" lorie/src/main/res/values/strings.xml lorie/src/main/java shell-loader/build.gradle README.md | head -40
   ```
   Sanity check the scripts still evaluate and the pin moved:
   `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew -q help --console=plain | grep '>>>'`
   — the printed `g<sha8>` must be the first 8 chars of `git merge-base HEAD master`.

7. **Build the next `+NNN`** via the **build-apk** skill
   (`JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew buildFork --console=plain < /dev/null`),
   then deliver **both** artefacts via the global **`/after-build`** skill (no transfer prompt). This
   is the first build on the new upstream base — same counter line, new pin.

8. **Stop.** Let 白い熊 test. Commit/push only on their explicit **"Push"**:
   ```bash
   git push origin master                        # fast-forward
   git push --force-with-lease origin custom     # rebased history
   git branch -D custom-pre-<date>               # only once the pushed stack is confirmed good
   ```

## Notes

- Keep our changes a **small, legible layer** on top of upstream — prefer rebasing (linear history)
  over merging, so the customization set stays easy to audit and replay. Everything Gradle-side
  lives in `lorie-app/shiroikuma.gradle`; if a sync ever forces a change to an upstream build file,
  say so explicitly in the handover — it is a new conflict point for every future sync.
- A moved submodule gitlink means CMake re-runs that recipe on the next build; nothing to do by
  hand beyond `git submodule update --init --recursive`. If upstream adds a **new** submodule, the
  same command fetches it — a "does not contain a CMakeLists.txt" configure error is the sign it was
  forgotten.
- Upstream bumps Gradle/AGP through Dependabot. If a bump breaks `shiroikuma.gradle` (a removed
  DSL property, `providers.exec` semantics), fix the script — never pin the wrapper back on `custom`.
- The companion `.deb` keeps upstream's internal version `1.03.01-0` on purpose (it must replace the
  stock `termux-x11-nightly` in place); only the copied file name carries our version.

---

**Commit convention — no Claude attribution.** Never add a `Co-Authored-By: Claude …` /
"Generated with Claude" trailer to commit messages or PR bodies; end the message at the last line of
the body. This overrides the harness default. (Global rule: `~/.claude/CLAUDE.md`.)
