package com.termux.x11.shiroikuma.automation;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Environment;
import android.util.Log;

import androidx.documentfile.provider.DocumentFile;

import com.termux.x11.BuildConfig;
import com.termux.x11.R;
import com.termux.x11.shiroikuma.ShiroikumaExport;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The sister-app <b>state-export automation contract</b> (保存復元), §1 — the wire shape every 白い熊
 * app exposes so one 自由作業盤 task can back them all up headlessly. Ported from raikidoban's
 * {@code StateExportReceiver.java}.
 *
 * <ul>
 * <li>{@code com.termux.x11.action.EXPORT_STATE}: run the category-ZIP export ({@link ShiroikumaExport})
 * with no UI. Extras (all String): {@code token} (OPTIONAL — checked only while 「Use authorization
 * token?」 is on, ignored otherwise), {@code path} (optional absolute directory), {@code items}
 * (optional comma list of category ids; absent/empty = the default set), {@code progress_action}
 * (optional), plus the reply trio {@code reply_action} / {@code reply_package} / {@code reply_id}.</li>
 * <li>{@code com.termux.x11.action.LIST_CATEGORIES}: gated the same way, instant. One
 * {@code id<TAB>label<TAB>parent<TAB>on|off} line per category.</li>
 * <li>{@code com.termux.x11.action.CANCEL_EXPORT}: stop a running export. Fire-and-forget — never
 * replies; the export answers its ORIGINAL request with {@code ERROR:cancelled} and deletes the
 * partial file. A silent no-op when nothing is running.</li>
 * </ul>
 *
 * <p><b>Where the export runs.</b> This app's whole export is a type-tagged dump of two or three
 * SharedPreferences files — milliseconds, bounded, no media and no rows — which is exactly the case
 * contract §1 allows to stay in the receiver ({@code goAsync()} + a worker thread, as both Java
 * references do). Keeping it out of a foreground service is also what keeps the cold-batch path
 * free of the foreground-start refusal that killed eight sister apps: a receiver needs no allowance.
 * The one thing that CAN take longer here — the SAF write to a slow provider — stays well inside the
 * ~10 s window. Long work exists only behind the data door, in {@link AutomationDataService}.
 *
 * <p><b>ONE ZIP per request, always</b> — {@code shiroikuma-termux-x11_<yyyy-MM-dd_HH-mm-ss>.zip},
 * identical to what the Export / Import panel writes, written as {@code <name>.part} and renamed
 * only when complete.
 *
 * <p>Reply: a FRESH broadcast to {@code reply_package} with action {@code reply_action}, extras
 * {@code reply_id} (echoed verbatim) + {@code result} = {@code OK:<path>|<bytes>|<human size>|<n>
 * categories}, {@code OK:} + the category lines, or {@code ERROR:<reason>}. Exactly one terminal
 * reply, guarded by an {@link AtomicBoolean}. NO binders and NO reliance on the ordered-broadcast
 * result — EMUI severs both between third-party apps; {@link Intent#FLAG_INCLUDE_STOPPED_PACKAGES}
 * so a backgrounded/stopped caller still hears us.
 *
 * <p>Storage: this app declares no {@code MANAGE_EXTERNAL_STORAGE}, so an absolute {@code path} is
 * honoured only when All-Files-Access happens to be held; otherwise the configured SAF directory is
 * used, and with neither the reply is {@code ERROR:no-storage-access} (path given) /
 * {@code ERROR:no-directory} (no path).
 *
 * <p>Security: exported with NO {@code android:permission}. This receiver is deliberately the
 * <b>unauthenticated</b> half of the surface — it only ever writes where it was told to and reports
 * what it did. Everything that moves data through a caller-supplied descriptor lives behind
 * {@link AutomationProvider}, which knows who is calling. The master switch (and the opt-in token)
 * are {@link AutomationAuth}; both live on the 白い熊 Termux X11 UI page under Export / Import.
 */
public class StateExportReceiver extends BroadcastReceiver {

    private static final String TAG = "ShiroikumaStateExport";

    public static final String ACTION_EXPORT_STATE = BuildConfig.APPLICATION_ID + ".action.EXPORT_STATE";
    public static final String ACTION_LIST_CATEGORIES = BuildConfig.APPLICATION_ID + ".action.LIST_CATEGORIES";
    public static final String ACTION_CANCEL_EXPORT = BuildConfig.APPLICATION_ID + ".action.CANCEL_EXPORT";

    // Contract extras — deliberately bare names, shared verbatim by every sister app.
    static final String EXTRA_TOKEN = "token";
    static final String EXTRA_PATH = "path";
    static final String EXTRA_ITEMS = "items";
    static final String EXTRA_PROGRESS_ACTION = "progress_action";
    static final String EXTRA_REPLY_ACTION = "reply_action";
    static final String EXTRA_REPLY_PACKAGE = "reply_package";
    static final String EXTRA_REPLY_ID = "reply_id";
    static final String EXTRA_RESULT = "result";

    /**
     * The exports currently writing, so a CANCEL_EXPORT arriving on a fresh receiver instance can
     * reach them. Process-local, never persisted; it empties itself in the worker's finally.
     */
    private static final List<Run> sRunning = new CopyOnWriteArrayList<>();

    private static final class Run {
        final String replyId;
        final AtomicBoolean cancelled = new AtomicBoolean(false);

        Run(String replyId) {
            this.replyId = replyId;
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null)
            return;
        final Context app = ShiroikumaExport.appContext(context);
        final String action = intent.getAction();
        final String token = intent.getStringExtra(EXTRA_TOKEN);
        final String replyAction = trimmed(intent.getStringExtra(EXTRA_REPLY_ACTION));
        final String replyPackage = trimmed(intent.getStringExtra(EXTRA_REPLY_PACKAGE));
        final String replyId = trimmed(intent.getStringExtra(EXTRA_REPLY_ID));
        final String progressAction = trimmed(intent.getStringExtra(EXTRA_PROGRESS_ACTION));
        final String pathOverride = trimmed(intent.getStringExtra(EXTRA_PATH));
        final String items = trimmed(intent.getStringExtra(EXTRA_ITEMS));

        // Cancel is handled ahead of the replying gate: it never answers anything, and a rejected
        // token is silence too. Safe to send at any time — when nothing matches, nothing happens.
        if (ACTION_CANCEL_EXPORT.equals(action)) {
            if (AutomationAuth.refuse(app, token) == null)
                for (Run run : sRunning)
                    if (replyId.isEmpty() || replyId.equals(run.replyId))
                        run.cancelled.set(true);
            return;
        }

        final AtomicBoolean replied = new AtomicBoolean(false);
        final Replier reply = result -> {
            if (!replied.compareAndSet(false, true))
                return;
            // No reply channel means nobody to answer — never degrade to an implicit broadcast.
            if (replyAction.isEmpty() || replyPackage.isEmpty())
                return;
            Intent out = new Intent(replyAction);
            out.setPackage(replyPackage);
            out.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
            out.putExtra(EXTRA_REPLY_ID, replyId);
            out.putExtra(EXTRA_RESULT, result);
            try {
                app.sendBroadcast(out);
                Log.i(TAG, "replied to " + replyPackage + " [" + replyId + "]: " + result);
            } catch (Exception e) {
                Log.w(TAG, "could not deliver the reply", e);
            }
        };

        // Gate first, in ONE place (§2). The switch is on by default and the token is opt-in.
        String refusal = AutomationAuth.refuse(app, token);
        if (refusal != null) {
            reply.send(refusal);
            return;
        }

        if (ACTION_LIST_CATEGORIES.equals(action)) {
            reply.send(listCategories(app));
            return;
        }

        if (!ACTION_EXPORT_STATE.equals(action)) {
            reply.send("ERROR:unknown action: " + action);
            return;
        }

        final Set<ShiroikumaExport.Cat> cats = resolveItems(items);
        if (cats == null) {
            reply.send("ERROR:unknown category in items: " + items);
            return;
        }

        final String appLabel = app.getString(R.string.lorie_app_name);
        final AutomationProgress progress = new AutomationProgress(app, progressAction, replyPackage,
                replyId, new String[]{EXTRA_REPLY_ID}, appLabel, cats, null);

        final Run run = new Run(replyId);
        sRunning.add(run);
        final PendingResult pending = goAsync();
        new Thread(() -> {
            progress.start();
            try {
                reply.send(runExport(app, cats, pathOverride, progress, run.cancelled::get));
            } catch (ShiroikumaExport.CancelledException e) {
                reply.send("ERROR:cancelled");
            } catch (Throwable t) {
                Log.w(TAG, "headless export failed", t);
                reply.send("ERROR:" + AutomationForeground.reason(t));
            } finally {
                progress.stop();
                sRunning.remove(run);
                pending.finish();
            }
        }, "shiroikuma-state-export").start();
    }

    // ---- LIST_CATEGORIES ----------------------------------------------------------------------

    /** {@code OK:} + {@code id<TAB>label<TAB>parent<TAB>on|off} per category; the parent field stays present but empty for a top-level one. */
    static String listCategories(Context app) {
        StringBuilder sb = new StringBuilder("OK:");
        boolean first = true;
        for (ShiroikumaExport.Cat cat : ShiroikumaExport.Cat.values()) {
            if (!first)
                sb.append('\n');
            first = false;
            sb.append(cat.id).append('\t').append(app.getString(cat.labelRes))
                    .append('\t').append(cat.parentId == null ? "" : cat.parentId)
                    .append('\t').append(cat.defaultSelected ? "on" : "off");
        }
        return sb.toString();
    }

    /** The {@code items} grammar, shared with the data door. {@code null} = an id is not ours. */
    static Set<ShiroikumaExport.Cat> resolveItems(String items) {
        if (items == null || items.trim().isEmpty())
            return ShiroikumaExport.Cat.defaults(); // "your default set" = what LIST_CATEGORIES calls `on`
        Set<ShiroikumaExport.Cat> resolved = new LinkedHashSet<>();
        List<String> unknown = new ArrayList<>();
        for (String raw : items.split(",")) {
            String id = raw.trim();
            if (id.isEmpty())
                continue;
            ShiroikumaExport.Cat cat = ShiroikumaExport.Cat.byId(id);
            if (cat == null)
                unknown.add(id);
            else
                resolved.add(cat);
        }
        if (!unknown.isEmpty())
            return null;
        return resolved.isEmpty() ? ShiroikumaExport.Cat.defaults() : resolved;
    }

    // ---- EXPORT_STATE --------------------------------------------------------------------------

    /**
     * Writes the archive and returns the single terminal line. Directory precedence: {@code path}
     * (when this app may write it) → the configured SAF directory → the keyed refusal. The file is
     * written as {@code <name>.part} and renamed on completion; any throwable takes the partial back
     * out, so the directory is left exactly as it was found.
     */
    static String runExport(Context app, Set<ShiroikumaExport.Cat> cats, String pathOverride,
                            ShiroikumaExport.Progress progress, ShiroikumaExport.Cancel cancel) throws Exception {
        final String fileName = ShiroikumaExport.exportFileName();
        boolean useAbsolute = !pathOverride.isEmpty() && hasAllFilesAccess();
        DocumentFile safDir = ShiroikumaExport.exportDir(app);
        if (!pathOverride.isEmpty() && !useAbsolute && safDir == null)
            return "ERROR:no-storage-access";
        if (!useAbsolute && safDir == null)
            return "ERROR:no-directory";

        if (useAbsolute) {
            File dir = new File(pathOverride);
            //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
            if (!dir.isDirectory())
                throw new IOException("not a directory: " + pathOverride);
            File part = new File(dir, fileName + ".part");
            File file = new File(dir, fileName);
            try {
                try (OutputStream os = new FileOutputStream(part)) {
                    ShiroikumaExport.export(app, cats, os, progress, cancel);
                }
                if (!part.renameTo(file))
                    throw new IOException("cannot rename " + part.getName());
            } catch (Throwable t) {
                //noinspection ResultOfMethodCallIgnored
                part.delete();
                throw t;
            }
            long bytes = file.length();
            return "OK:" + file.getAbsolutePath() + "|" + bytes + "|" + ShiroikumaExport.humanSize(bytes)
                    + "|" + cats.size() + " categories";
        }

        // The .part is created as an octet-stream: the external-storage provider appends the MIME
        // type's extension to a display name whose own extension does not match it, and ".zip.part"
        // is not a zip until the rename says so.
        DocumentFile part = safDir.createFile("application/octet-stream", fileName + ".part");
        if (part == null)
            throw new IOException("cannot create " + fileName + " in the export directory");
        long bytes;
        try {
            OutputStream os = app.getContentResolver().openOutputStream(part.getUri());
            if (os == null)
                throw new IOException("cannot open " + fileName + " for writing");
            CountingOutputStream counting = new CountingOutputStream(os);
            try {
                ShiroikumaExport.export(app, cats, counting, progress, cancel);
            } finally {
                counting.close();
            }
            if (!part.renameTo(fileName))
                throw new IOException("cannot rename " + fileName + ".part");
            long reported = part.length();
            bytes = reported > 0 ? reported : counting.written;
        } catch (Throwable t) {
            try {
                part.delete();
            } catch (Exception ignored) {
                // nothing more can be done about a partial that will not go
            }
            throw t;
        }
        String name = part.getName() == null ? fileName : part.getName();
        String abs = ShiroikumaExport.absolutePathOf(safDir, name);
        String shownPath = abs != null ? abs : safDir.getName() + "/" + name;
        return "OK:" + shownPath + "|" + bytes + "|" + ShiroikumaExport.humanSize(bytes)
                + "|" + cats.size() + " categories";
    }

    /** All-Files-Access — this app does not declare it, so on API 30+ this is false unless granted out of band. */
    static boolean hasAllFilesAccess() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager();
    }

    /** Counts what actually reached the SAF stream, in case the provider cannot stat the file. */
    static final class CountingOutputStream extends OutputStream {
        private final OutputStream delegate;
        long written;

        CountingOutputStream(OutputStream delegate) {
            this.delegate = delegate;
        }

        @Override
        public void write(int b) throws IOException {
            delegate.write(b);
            written++;
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            delegate.write(b, off, len);
            written += len;
        }

        @Override
        public void flush() throws IOException {
            delegate.flush();
        }

        @Override
        public void close() throws IOException {
            delegate.close();
        }
    }

    private static String trimmed(String s) {
        return s == null ? "" : s.trim();
    }

    interface Replier {
        void send(String result);
    }
}
