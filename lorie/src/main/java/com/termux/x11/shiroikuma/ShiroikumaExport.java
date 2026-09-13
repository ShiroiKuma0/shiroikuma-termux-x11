package com.termux.x11.shiroikuma;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Environment;
import android.provider.DocumentsContract;

import androidx.documentfile.provider.DocumentFile;

import com.termux.x11.BuildConfig;
import com.termux.x11.R;
import com.termux.x11.shiroikuma.automation.AutomationAuth;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * shiroikuma-termux-x11 fork (Phase 4): the Export / Import core — the one engine behind the
 * 白い熊 Termux X11 UI page's panel, the headless {@code EXPORT_STATE} receiver and the automation
 * data door, in the family's shared archive shape (Kōjiki / kxkb / raikidoban).
 *
 * <p>The archive: {@code manifest.json} FIRST ({@code format, version, app, appVersion, createdTs,
 * categories[]}), then {@code settings.json} — a type-tagged dump of the app's SharedPreferences
 * files, keyed by file name:
 * <pre>{"com.termux.x11_preferences":{"displayScale":{"t":"int","v":100}, …}, "secondary":{…}}</pre>
 * with {@code t} one of {@code int|long|float|bool|string|set}. The files are whatever sits under
 * {@code shared_prefs/} at export time (today upstream's default file and its {@code secondary}
 * display file, plus our own {@link #UI_PREFS}), <b>except</b> the two device-local ones that must
 * never travel: the automation switch/token ({@link AutomationAuth#PREFS_FILE}) and the export
 * directory ({@link #EXIMPORT_PREFS}).
 *
 * <p>Import is a per-key <b>merge</b> with {@code commit()} — never a clear — restricted to the
 * categories the archive actually carries. The synchronous write matters: 応用管理 force-stops this
 * app the instant it hears {@code OK} (contract v2 §2a), and a {@code SIGKILL} is fatal to an
 * {@code apply()} still in flight.
 *
 * <p>Only one category exists — {@code settings} — because this app keeps no user data in files:
 * everything it owns lives in those preferences; the X server's own state is Termux's.
 */
public final class ShiroikumaExport {

    /** The archive's {@code format} tag, and what {@link #categoriesIn} recognises. */
    public static final String FORMAT = "shiroikuma-termux-x11";
    /** The archive format version — bumped only when an older build could no longer read it. */
    public static final int VERSION = 1;

    /**
     * Family-wide backup-name convention (白い熊, 2026-07-25): every sister app writes
     * {@code <english-dash-separated-app-name>_<yyyy-MM-dd_HH-mm-ss>.zip} — no version, no infix, no
     * suffix — so all apps' backups sort and read uniformly in one directory.
     */
    public static final String EXPORT_PREFIX = "shiroikuma-termux-x11_";

    /** Device-local prefs holding the export-directory tree URI; deliberately never exported. */
    public static final String EXIMPORT_PREFS = "shiroikuma_eximport";
    public static final String KEY_DIR_URI = "export_dir_uri";

    /**
     * Our own UI-page prefs file. Exported inside the {@code settings} category like every other
     * file, and the one file the page's Reset row clears. Empty today (no appearance section yet);
     * it exists so later theming has a home that is already backed up.
     */
    public static final String UI_PREFS = "shiroikuma_ui";

    private static final String MANIFEST_ENTRY = "manifest.json";
    private static final String SETTINGS_ENTRY = "settings.json";

    /** The selectable categories; {@code id} doubles as the id accepted in the automation {@code items}. */
    public enum Cat {
        SETTINGS("settings", R.string.shiroikuma_eim_cat_settings, null, true);

        public final String id;
        public final int labelRes;
        /** The parent's id for a sub-option, null for a top-level category. */
        public final String parentId;
        /**
         * Whether the item starts ticked in a picker — the panel here and, through the fourth
         * {@code LIST_CATEGORIES} field, 保存復元's item editor. Preferences are authored and not
         * re-creatable, so the one category is {@code on}.
         */
        public final boolean defaultSelected;

        Cat(String id, int labelRes, String parentId, boolean defaultSelected) {
            this.id = id;
            this.labelRes = labelRes;
            this.parentId = parentId;
            this.defaultSelected = defaultSelected;
        }

        public static Cat byId(String id) {
            for (Cat c : values())
                if (c.id.equals(id))
                    return c;
            return null;
        }

        public static Set<Cat> all() {
            return new LinkedHashSet<>(Arrays.asList(values()));
        }

        /** The default set — what {@code items} absent means, and what LIST_CATEGORIES calls {@code on}. */
        public static Set<Cat> defaults() {
            Set<Cat> out = new LinkedHashSet<>();
            for (Cat c : values())
                if (c.defaultSelected)
                    out.add(c);
            return out;
        }
    }

    /** Category-walk progress: {@code done} is the POSITION of the category being written (1-based). */
    public interface Progress {
        void onProgress(int done, int total, String categoryLabel);
    }

    /** Polled at entry boundaries — never mid-write, so a cancelled archive is never half a file. */
    public interface Cancel {
        boolean requested();
    }

    /** Thrown out of {@link #export} once a cancel has been seen; the caller deletes its partial file. */
    public static final class CancelledException extends IOException {
        public CancelledException() {
            super("cancelled");
        }
    }

    /**
     * One export at a time, process-wide. Process-local and never persisted, deliberately: a
     * persisted "running" flag survives the crash that stranded it and wedges the app for good.
     */
    private static final AtomicBoolean RUNNING = new AtomicBoolean(false);

    private ShiroikumaExport() {
    }

    // ---- context ---------------------------------------------------------------------------------

    /**
     * A Context that identifies as THIS app. In the sharedUid flavour every component runs inside
     * Termux's process, and a platform-supplied Context can identify as the host package there (the
     * same guard upstream's {@code LoriePreferences.PrefsProto} carries) — the prefs files must be
     * ours, never Termux's.
     */
    public static Context appContext(Context ctx) {
        Context app = ctx.getApplicationContext() != null ? ctx.getApplicationContext() : ctx;
        if (BuildConfig.APPLICATION_ID.equals(app.getPackageName()))
            return app;
        try {
            return app.createPackageContext(BuildConfig.APPLICATION_ID, 0);
        } catch (PackageManager.NameNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    // ---- export ----------------------------------------------------------------------------------

    public static String exportFileName() {
        return EXPORT_PREFIX + new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.ROOT).format(new Date()) + ".zip";
    }

    public static boolean isExportRunning() {
        return RUNNING.get();
    }

    /**
     * Streams the archive for {@code cats} to {@code out}. The caller owns the stream and MUST delete
     * its target on any throwable — a cancelled or failed export leaves the directory as it found it.
     */
    public static void export(Context ctx, Collection<Cat> cats, OutputStream out, Progress progress, Cancel cancel)
            throws Exception {
        if (cats.isEmpty())
            throw new IllegalArgumentException("no categories selected");
        if (!RUNNING.compareAndSet(false, true))
            throw new IllegalStateException("export already running");
        try {
            Context app = appContext(ctx);
            List<Cat> ordered = new ArrayList<>();
            for (Cat c : Cat.values())
                if (cats.contains(c))
                    ordered.add(c);

            // Closing the zip closes `out` too; every caller closes its own stream again afterwards,
            // which is harmless, and none of them may rely on this one staying open.
            try (ZipOutputStream zip = new ZipOutputStream(out)) {
                JSONArray ids = new JSONArray();
                for (Cat c : ordered)
                    ids.put(c.id);
                JSONObject manifest = new JSONObject()
                        .put("format", FORMAT)
                        .put("version", VERSION)
                        .put("app", app.getPackageName())
                        .put("appVersion", BuildConfig.VERSION_NAME)
                        .put("createdTs", System.currentTimeMillis())
                        .put("categories", ids);
                writeEntry(zip, MANIFEST_ENTRY, manifest.toString(2));

                int total = ordered.size();
                int n = 0;
                for (Cat cat : ordered) {
                    throwIfCancelled(cancel);
                    n++;
                    if (progress != null)
                        progress.onProgress(n, total, app.getString(cat.labelRes));
                    if (cat == Cat.SETTINGS)
                        writeEntry(zip, SETTINGS_ENTRY, dumpPrefs(app).toString(2));
                }
                throwIfCancelled(cancel);
                zip.finish();
                zip.flush();
            }
        } finally {
            RUNNING.set(false);
        }
    }

    private static void throwIfCancelled(Cancel cancel) throws CancelledException {
        if (cancel != null && cancel.requested())
            throw new CancelledException();
    }

    private static void writeEntry(ZipOutputStream zip, String name, String content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    /** The prefs files the {@code settings} category carries: everything under shared_prefs/ but the two device-local ones. */
    public static List<String> prefsFiles(Context ctx) {
        Context app = appContext(ctx);
        Set<String> names = new LinkedHashSet<>();
        File dir = new File(app.getApplicationInfo().dataDir, "shared_prefs");
        File[] files = dir.listFiles();
        if (files != null) {
            Arrays.sort(files);
            for (File f : files) {
                String name = f.getName();
                if (f.isFile() && name.endsWith(".xml"))
                    names.add(name.substring(0, name.length() - 4));
            }
        }
        // The default file is where nearly everything lives; list it even before it has been written.
        names.add(app.getPackageName() + "_preferences");
        names.remove(AutomationAuth.PREFS_FILE);
        names.remove(EXIMPORT_PREFS);
        return new ArrayList<>(names);
    }

    /** {@code {"<file>":{"<key>":{"t":"int|long|float|bool|string|set","v":…}}}} — every key of every exportable file. */
    static JSONObject dumpPrefs(Context app) throws Exception {
        JSONObject out = new JSONObject();
        for (String file : prefsFiles(app)) {
            JSONObject entries = new JSONObject();
            SharedPreferences sp = app.getSharedPreferences(file, Context.MODE_PRIVATE);
            for (Map.Entry<String, ?> e : sp.getAll().entrySet()) {
                Object v = e.getValue();
                JSONObject tagged = new JSONObject();
                if (v instanceof Boolean)
                    tagged.put("t", "bool").put("v", v);
                else if (v instanceof Integer)
                    tagged.put("t", "int").put("v", v);
                else if (v instanceof Long)
                    tagged.put("t", "long").put("v", v);
                else if (v instanceof Float)
                    tagged.put("t", "float").put("v", ((Float) v).doubleValue());
                else if (v instanceof String)
                    tagged.put("t", "string").put("v", v);
                else if (v instanceof Set) {
                    JSONArray a = new JSONArray();
                    for (Object s : (Set<?>) v)
                        a.put(String.valueOf(s));
                    tagged.put("t", "set").put("v", a);
                } else
                    continue;
                entries.put(e.getKey(), tagged);
            }
            out.put(file, entries);
        }
        return out;
    }

    // ---- inspect ---------------------------------------------------------------------------------

    /** The categories an archive carries, in declaration order; empty when it is not one of ours. */
    public static List<Cat> categoriesIn(ZipFile zip) {
        List<Cat> out = new ArrayList<>();
        try {
            String manifest = readEntry(zip, MANIFEST_ENTRY);
            if (manifest == null)
                return out;
            JSONObject m = new JSONObject(manifest);
            if (!FORMAT.equals(m.optString("format")))
                return out;
            if (zip.getEntry(SETTINGS_ENTRY) != null)
                out.add(Cat.SETTINGS);
        } catch (Exception ignored) {
            // not ours, or unreadable — an empty answer is the honest one
        }
        return out;
    }

    private static String readEntry(ZipFile zip, String name) throws IOException {
        ZipEntry entry = zip.getEntry(name);
        if (entry == null)
            return null;
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (InputStream in = zip.getInputStream(entry)) {
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0)
                bos.write(buf, 0, n);
        }
        return new String(bos.toByteArray(), StandardCharsets.UTF_8);
    }

    // ---- import ----------------------------------------------------------------------------------

    /** The per-category counts of a finished import, and the one-line summary the dialogs show. */
    public static final class ImportResult {
        public final List<Cat> restored = new ArrayList<>();
        public int keys;
        public int files;
        public String summary = "";
    }

    /**
     * Merges the selected categories out of {@code zipFile}; categories the archive lacks are skipped.
     * Returns {@code null} when the file is not one of ours.
     */
    @SuppressLint("ApplySharedPref")
    public static ImportResult importZip(Context ctx, File zipFile, Collection<Cat> cats) throws Exception {
        Context app = appContext(ctx);
        try (ZipFile zip = new ZipFile(zipFile)) {
            List<Cat> present = categoriesIn(zip);
            if (present.isEmpty())
                return null;
            ImportResult result = new ImportResult();
            StringBuilder summary = new StringBuilder();
            for (Cat cat : present) {
                if (!cats.contains(cat))
                    continue;
                if (cat == Cat.SETTINGS) {
                    String json = readEntry(zip, SETTINGS_ENTRY);
                    if (json == null)
                        continue;
                    int[] counts = importPrefs(app, new JSONObject(json));
                    result.keys += counts[0];
                    result.files += counts[1];
                    if (summary.length() > 0)
                        summary.append('\n');
                    summary.append(app.getString(cat.labelRes)).append(": ")
                            .append(app.getString(R.string.shiroikuma_eim_settings_result, counts[0], counts[1]));
                }
                result.restored.add(cat);
            }
            result.summary = summary.toString();
            return result;
        }
    }

    /** Per-file, per-key merge with {@code commit()}. Returns {keys applied, files touched}. */
    @SuppressLint("ApplySharedPref")
    static int[] importPrefs(Context app, JSONObject settings) {
        int keys = 0;
        int files = 0;
        for (Iterator<String> it = settings.keys(); it.hasNext(); ) {
            String file = it.next();
            // The two device-local files never travel — and a hand-edited archive that names them
            // is not allowed to smuggle a token or a directory grant in either.
            if (AutomationAuth.PREFS_FILE.equals(file) || EXIMPORT_PREFS.equals(file))
                continue;
            // A file this install has never seen is still restored: it IS this app's, or the archive
            // is not ours at all (categoriesIn already vetted the format tag).
            JSONObject entries = settings.optJSONObject(file);
            if (entries == null)
                continue;
            SharedPreferences.Editor ed = app.getSharedPreferences(file, Context.MODE_PRIVATE).edit();
            int applied = 0;
            for (Iterator<String> keysIt = entries.keys(); keysIt.hasNext(); ) {
                String key = keysIt.next();
                JSONObject e = entries.optJSONObject(key);
                if (e == null)
                    continue;
                switch (e.optString("t")) {
                    case "bool": case "b":
                        ed.putBoolean(key, e.optBoolean("v"));
                        break;
                    case "int": case "i":
                        ed.putInt(key, e.optInt("v"));
                        break;
                    case "long": case "l":
                        ed.putLong(key, e.optLong("v"));
                        break;
                    case "float": case "f":
                        ed.putFloat(key, (float) e.optDouble("v"));
                        break;
                    case "string": case "s":
                        ed.putString(key, e.optString("v"));
                        break;
                    case "set": case "ss": {
                        JSONArray a = e.optJSONArray("v");
                        Set<String> set = new HashSet<>();
                        if (a != null)
                            for (int i = 0; i < a.length(); i++)
                                set.add(a.optString(i));
                        ed.putStringSet(key, set);
                        break;
                    }
                    default:
                        continue;
                }
                applied++;
            }
            // commit(), NOT apply(): the restore must be on disk before anything answers OK.
            ed.commit();
            keys += applied;
            files++;
        }
        return new int[]{keys, files};
    }

    // ---- the export directory ------------------------------------------------------------------

    private static SharedPreferences eximportPrefs(Context ctx) {
        return appContext(ctx).getSharedPreferences(EXIMPORT_PREFS, Context.MODE_PRIVATE);
    }

    public static Uri exportDirUri(Context ctx) {
        String raw = eximportPrefs(ctx).getString(KEY_DIR_URI, null);
        if (raw == null || raw.isEmpty())
            return null;
        try {
            return Uri.parse(raw);
        } catch (Exception e) {
            return null;
        }
    }

    @SuppressLint("ApplySharedPref")
    public static void setExportDirUri(Context ctx, Uri uri) {
        eximportPrefs(ctx).edit().putString(KEY_DIR_URI, uri == null ? null : uri.toString()).commit();
    }

    /**
     * Persist a folder picked with ACTION_OPEN_DOCUMENT_TREE as the export directory, taking a
     * persistable read/write grant so it survives reboots.
     */
    public static void storeDir(Context ctx, Uri uri) {
        if (uri == null)
            return;
        try {
            ctx.getContentResolver().takePersistableUriPermission(uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                            | android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        } catch (Exception ignored) {
            // A provider that grants nothing persistable still works for this session.
        }
        setExportDirUri(ctx, uri);
    }

    /** The configured directory when it is usable, null when unset or when the grant is gone. */
    public static DocumentFile exportDir(Context ctx) {
        Uri uri = exportDirUri(ctx);
        if (uri == null)
            return null;
        try {
            DocumentFile dir = DocumentFile.fromTreeUri(ctx, uri);
            return (dir != null && dir.isDirectory()) ? dir : null;
        } catch (Exception e) {
            return null;
        }
    }

    /** Every export of ours in the directory, newest first. */
    public static List<DocumentFile> listExports(Context ctx) {
        List<DocumentFile> out = new ArrayList<>();
        DocumentFile dir = exportDir(ctx);
        if (dir == null)
            return out;
        try {
            for (DocumentFile f : dir.listFiles()) {
                String name = f.getName();
                if (f.isFile() && name != null && name.startsWith(EXPORT_PREFIX) && name.endsWith(".zip"))
                    out.add(f);
            }
        } catch (Exception ignored) {
            // an unreadable directory lists as empty
        }
        Collections.sort(out, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        return out;
    }

    /** The newest export in the configured directory, or null. */
    public static DocumentFile newestExport(Context ctx) {
        List<DocumentFile> all = listExports(ctx);
        return all.isEmpty() ? null : all.get(0);
    }

    /** Best-effort absolute path of a SAF tree document, {@code child} appended when given; null when not derivable. */
    public static String absolutePathOf(DocumentFile dir, String child) {
        return dir == null ? null : absolutePathOf(dir.getUri(), child);
    }

    public static String absolutePathOf(Uri uri, String child) {
        try {
            if (uri == null || !"com.android.externalstorage.documents".equals(uri.getAuthority()))
                return null;
            // content://…/tree/<id>            → a tree (the directory itself)
            // content://…/tree/<id>/document/<id> or content://…/document/<id> → a document in it
            List<String> segments = uri.getPathSegments();
            boolean isDocument = segments.size() >= 4 && "document".equals(segments.get(2))
                    || segments.size() >= 2 && "document".equals(segments.get(0));
            String docId = isDocument ? DocumentsContract.getDocumentId(uri) : DocumentsContract.getTreeDocumentId(uri);
            String[] split = docId.split(":", 2);
            if (split.length != 2)
                return null;
            String root = "primary".equalsIgnoreCase(split[0])
                    ? Environment.getExternalStorageDirectory().getAbsolutePath()
                    : "/storage/" + split[0];
            String path = split[1].isEmpty() ? root : root + "/" + split[1];
            return child == null ? path : path + "/" + child;
        } catch (Exception e) {
            return null;
        }
    }

    /** {@code 4.6 MB}, {@code 1.20 GB} — the caller cannot stat the file, so the display form is ours too. */
    public static String humanSize(long bytes) {
        if (bytes >= 1024L * 1024L * 1024L)
            return String.format(Locale.US, "%.2f GB", bytes / (double) (1024L * 1024L * 1024L));
        if (bytes >= 1024L * 1024L)
            return String.format(Locale.US, "%.1f MB", bytes / (double) (1024L * 1024L));
        if (bytes >= 1024L)
            return String.format(Locale.US, "%.1f KB", bytes / (double) 1024L);
        return bytes + " B";
    }

    /** Copies a stream to a file, cancellable between chunks; returns the byte count. */
    public static long copy(InputStream in, OutputStream out, Cancel cancel) throws IOException {
        byte[] buf = new byte[64 * 1024];
        long total = 0;
        int n;
        while ((n = in.read(buf)) != -1) {
            throwIfCancelled(cancel);
            out.write(buf, 0, n);
            total += n;
        }
        return total;
    }
}
