package com.termux.x11.shiroikuma;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.text.format.DateFormat;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.documentfile.provider.DocumentFile;

import com.termux.x11.MainActivity;
import com.termux.x11.R;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The Export / Import window — one black-yellow page that backs up and restores every preference of
 * 白い熊 Termux X11, in the family's shared visual format (ported from raikidoban's
 * {@code ExportImportPanel}): one bordered rounded box carrying a centred title, a dim description, a
 * bordered tappable directory box (red when unset, yellow once set), the last-backup line, a divider,
 * 全選択 + the category checkboxes (sub-options indented under their parent, following its toggle),
 * a divider, and the pill row — Cancel alone on the left, Import + Export grouped on the right.
 *
 * <p>All work goes through {@link ShiroikumaExport}, the same core the headless automation receiver
 * and the data door use. A successful export ends with a bordered info dialog whose OK closes the
 * whole chain (info dialog → this panel → the UI page, via {@link Host#onChainFinished()}); an import
 * ends with 「Later」 (closes the chain) / 「Restart now」; failures leave the panel open.
 *
 * <p>The SAF pickers are registered by the hosting fragment, which forwards their results back
 * through {@link #onDirPicked} / {@link #onImportFilePicked}.
 */
public class ExportImportPanel {

    /** What the host must provide: the two SAF pickers and the "close everything" hook. */
    public interface Host {
        void pickExportDir(Uri initial);

        void pickImportFile();

        void onChainFinished();
    }

    private final Activity mActivity;
    private final Host mHost;
    private final ShiroikumaDialogs mUi;

    /** Seeded from the categories' own defaultSelected flag — the same answer LIST_CATEGORIES gives. */
    private final Set<ShiroikumaExport.Cat> mSelected = new LinkedHashSet<>(ShiroikumaExport.Cat.defaults());

    private AlertDialog mDialog;
    private LinearLayout mBox;

    public ExportImportPanel(Activity activity, Host host) {
        mActivity = activity;
        mHost = host;
        mUi = new ShiroikumaDialogs(activity);
    }

    public boolean isShowing() {
        return mDialog != null && mDialog.isShowing();
    }

    public void show() {
        mBox = new LinearLayout(mActivity);
        mBox.setOrientation(LinearLayout.VERTICAL);
        mBox.setPadding(dp(20), dp(16), dp(20), dp(20));
        mBox.setBackground(mUi.panelBackground());
        mDialog = mUi.boxDialog(mBox, true);
        mDialog.show();
        mUi.transparentWindow(mDialog);
        rebuild();
    }

    public void dismiss() {
        if (mDialog != null) {
            mDialog.dismiss();
            mDialog = null;
        }
    }

    // ---- content ---------------------------------------------------------------------------------

    public void rebuild() {
        if (mBox == null)
            return;
        mBox.removeAllViews();

        TextView title = mUi.text(mActivity.getString(R.string.shiroikuma_eim_title), 18, ShiroikumaDialogs.YELLOW, true);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, dp(2), 0, dp(6));
        mBox.addView(title);

        TextView desc = mUi.text(mActivity.getString(R.string.shiroikuma_eim_desc), 13, ShiroikumaDialogs.YELLOW, false);
        desc.setAlpha(0.85f);
        desc.setPadding(0, 0, 0, dp(10));
        mBox.addView(desc);

        mBox.addView(dirBox());
        mBox.addView(statusLine());
        mBox.addView(mUi.divider(0));

        final CheckBox selectAll = mUi.checkbox(mActivity.getString(R.string.shiroikuma_eim_select_all), true, 0);
        selectAll.setChecked(mSelected.size() == ShiroikumaExport.Cat.values().length);
        selectAll.setOnClickListener(v -> {
            if (selectAll.isChecked())
                mSelected.addAll(ShiroikumaExport.Cat.all());
            else
                mSelected.clear();
            rebuild();
        });
        mBox.addView(selectAll);

        for (ShiroikumaExport.Cat cat : ShiroikumaExport.Cat.values())
            mBox.addView(categoryRow(cat));

        mBox.addView(mUi.divider(8));
        mBox.addView(buttonRow());
    }

    /** The folder box: a bordered, clearly-tappable box — small label over the value, red when unset. */
    private View dirBox() {
        LinearLayout box = new LinearLayout(mActivity);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setClickable(true);
        box.setPadding(dp(12), dp(10), dp(12), dp(10));
        String label = dirLabel(mActivity);
        String remembered = label == null ? rememberedDirLabel(mActivity) : null;
        box.setBackground(mUi.boxBackground(label != null ? ShiroikumaDialogs.YELLOW : ShiroikumaDialogs.WARN));
        box.setOnClickListener(v -> mHost.pickExportDir(ShiroikumaExport.exportDirUri(mActivity)));

        box.addView(mUi.text(mActivity.getString(R.string.shiroikuma_eim_dir_caption), 12, ShiroikumaDialogs.YELLOW, false));
        String shown = label != null ? label
                : remembered != null ? remembered
                : mActivity.getString(R.string.shiroikuma_eim_dir_unset_long);
        box.addView(mUi.text(shown, 15, label != null ? ShiroikumaDialogs.YELLOW : ShiroikumaDialogs.WARN, true));
        if (remembered != null) {
            // Restored from a backup: the folder is known, only the grant is gone.
            TextView hint = mUi.text(mActivity.getString(R.string.shiroikuma_eim_dir_regrant), 12, ShiroikumaDialogs.WARN, false);
            hint.setPadding(0, dp(2), 0, 0);
            box.addView(hint);
        }

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(6);
        lp.bottomMargin = dp(6);
        box.setLayoutParams(lp);
        return box;
    }

    /** The configured directory as an absolute path when resolvable, its name otherwise, or null. */
    public static String dirLabel(Context context) {
        DocumentFile dir = ShiroikumaExport.exportDir(context);
        if (dir == null)
            return null;
        String abs = ShiroikumaExport.absolutePathOf(dir, null);
        return abs != null ? abs : dir.getName();
    }

    /**
     * The folder a restore brought back but the platform no longer grants — a path we can name and
     * open the picker at, never one we can write to. Null when the directory works or was never set.
     */
    public static String rememberedDirLabel(Context context) {
        if (ShiroikumaExport.exportDir(context) != null)
            return null;
        Uri uri = ShiroikumaExport.exportDirUri(context);
        if (uri == null)
            return null;
        String abs = ShiroikumaExport.absolutePathOf(uri, null);
        if (abs != null)
            return abs;
        String last = uri.getLastPathSegment();
        if (last == null)
            return uri.toString();
        int colon = last.lastIndexOf(':');
        return colon >= 0 && colon + 1 < last.length() ? last.substring(colon + 1) : last;
    }

    private View statusLine() {
        String msg;
        boolean warn;
        if (ShiroikumaExport.exportDir(mActivity) == null) {
            msg = mActivity.getString(rememberedDirLabel(mActivity) != null
                    ? R.string.shiroikuma_eim_warn_regrant : R.string.shiroikuma_eim_warn_nodir);
            warn = true;
        } else {
            DocumentFile newest = ShiroikumaExport.newestExport(mActivity);
            if (newest == null) {
                msg = mActivity.getString(R.string.shiroikuma_eim_warn_none);
                warn = true;
            } else {
                msg = mActivity.getString(R.string.shiroikuma_eim_last_line, formatTs(mActivity, newest.lastModified())
                        + " · " + ShiroikumaExport.humanSize(newest.length()));
                warn = false;
            }
        }
        TextView tv = mUi.text(msg, 14, warn ? ShiroikumaDialogs.WARN : ShiroikumaDialogs.YELLOW, false);
        tv.setAlpha(warn ? 1f : 0.8f);
        tv.setPadding(dp(2), 0, 0, dp(8));
        return tv;
    }

    public static String formatTs(Context context, long ts) {
        return DateFormat.getDateFormat(context).format(ts) + " " + DateFormat.getTimeFormat(context).format(ts);
    }

    private View categoryRow(final ShiroikumaExport.Cat cat) {
        boolean isChild = cat.parentId != null;
        CheckBox cb = mUi.checkbox(mActivity.getString(cat.labelRes), false, isChild ? dp(28) : 0);
        boolean parentOn = !isChild || mSelected.contains(ShiroikumaExport.Cat.byId(cat.parentId));
        cb.setChecked(mSelected.contains(cat) && parentOn);
        cb.setEnabled(parentOn);
        cb.setAlpha(parentOn ? 1f : 0.5f);
        cb.setOnClickListener(v -> {
            boolean checked = cb.isChecked();
            if (checked)
                mSelected.add(cat);
            else
                mSelected.remove(cat);
            // A parent drags its children along, so turning a group off never leaves orphaned parts on.
            boolean hasChildren = false;
            for (ShiroikumaExport.Cat other : ShiroikumaExport.Cat.values()) {
                if (cat.id.equals(other.parentId)) {
                    hasChildren = true;
                    if (checked)
                        mSelected.add(other);
                    else
                        mSelected.remove(other);
                }
            }
            if (hasChildren)
                rebuild();
        });
        return cb;
    }

    /** The pill row: Cancel alone on the left, a spacer, Import then Export on the right. */
    private View buttonRow() {
        LinearLayout row = new LinearLayout(mActivity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(14), 0, 0);

        row.addView(mUi.pill(mActivity.getString(android.R.string.cancel), v -> dismiss()));
        View spacer = new View(mActivity);
        row.addView(spacer, new LinearLayout.LayoutParams(0, 0, 1f));
        Button importButton = mUi.pill(mActivity.getString(R.string.shiroikuma_eim_import), v -> onImportClicked());
        ((LinearLayout.LayoutParams) importButton.getLayoutParams()).rightMargin = dp(8);
        row.addView(importButton);
        row.addView(mUi.pill(mActivity.getString(R.string.shiroikuma_eim_export), v -> onExportClicked()));
        return row;
    }

    // ---- export ----------------------------------------------------------------------------------

    private void onExportClicked() {
        if (mSelected.isEmpty()) {
            showInfo(mActivity.getString(R.string.shiroikuma_eim_export_fail_title),
                    mActivity.getString(R.string.shiroikuma_eim_none_selected), false);
            return;
        }
        final DocumentFile dir = ShiroikumaExport.exportDir(mActivity);
        if (dir == null) {
            mHost.pickExportDir(ShiroikumaExport.exportDirUri(mActivity)); // no folder yet: ask instead of failing
            return;
        }
        final Set<ShiroikumaExport.Cat> cats = new LinkedHashSet<>(mSelected);
        final String name = ShiroikumaExport.exportFileName();
        final Context app = mActivity.getApplicationContext();
        new Thread(() -> {
            String path;
            long bytes;
            // Written as <name>.part and renamed only once the archive is complete; any failure takes
            // the partial back out, so the directory is left exactly as it was found.
            DocumentFile part = null;
            try {
                part = dir.createFile("application/octet-stream", name + ".part");
                if (part == null)
                    throw new IOException("cannot create " + name);
                OutputStream os = app.getContentResolver().openOutputStream(part.getUri());
                if (os == null)
                    throw new IOException("cannot open " + name);
                try {
                    ShiroikumaExport.export(app, cats, os, null, null);
                } finally {
                    os.close();
                }
                if (!part.renameTo(name))
                    throw new IOException("cannot rename " + name + ".part");
                bytes = part.length();
                String written = part.getName() == null ? name : part.getName();
                String abs = ShiroikumaExport.absolutePathOf(dir, written);
                path = abs != null ? abs : written;
            } catch (Throwable t) {
                if (part != null) {
                    try {
                        part.delete();
                    } catch (Exception ignored) {
                        // nothing more can be done about a partial that will not go
                    }
                }
                final String message = t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName();
                mActivity.runOnUiThread(() -> showInfo(
                        mActivity.getString(R.string.shiroikuma_eim_export_fail_title),
                        mActivity.getString(R.string.shiroikuma_eim_export_fail, message), false));
                return;
            }
            final String shownPath = path;
            final long size = bytes;
            mActivity.runOnUiThread(() -> showInfo(
                    mActivity.getString(R.string.shiroikuma_eim_export_done_title),
                    mActivity.getString(R.string.shiroikuma_eim_export_done_body,
                            shownPath, ShiroikumaExport.humanSize(size), cats.size()),
                    true));
        }, "shiroikuma-export").start();
    }

    // ---- import ----------------------------------------------------------------------------------

    private void onImportClicked() {
        if (mSelected.isEmpty()) {
            showInfo(mActivity.getString(R.string.shiroikuma_eim_import_fail_title),
                    mActivity.getString(R.string.shiroikuma_eim_none_selected), false);
            return;
        }
        final List<DocumentFile> backups = ShiroikumaExport.listExports(mActivity);
        final List<CharSequence> labels = new ArrayList<>();
        for (DocumentFile f : backups)
            labels.add(f.getName());
        labels.add(mActivity.getString(R.string.shiroikuma_eim_browse));
        mUi.list(mActivity.getString(R.string.shiroikuma_eim_pick_backup), labels, which -> {
            if (which >= backups.size())
                mHost.pickImportFile();
            else
                runImport(backups.get(which).getUri());
        });
    }

    /** Called by the host after the SAF file picker returns. */
    public void onImportFilePicked(Uri uri) {
        if (uri != null)
            runImport(uri);
    }

    private void runImport(final Uri uri) {
        final Set<ShiroikumaExport.Cat> cats = new LinkedHashSet<>(mSelected);
        final Context app = mActivity.getApplicationContext();
        new Thread(() -> {
            ShiroikumaExport.ImportResult result;
            File spool = new File(app.getCacheDir(), "shiroikuma-import-" + System.currentTimeMillis() + ".zip");
            try {
                InputStream is = app.getContentResolver().openInputStream(uri);
                if (is == null)
                    throw new IOException("no input stream");
                try (OutputStream os = new FileOutputStream(spool)) {
                    ShiroikumaExport.copy(is, os, null);
                } finally {
                    is.close();
                }
                result = ShiroikumaExport.importZip(app, spool, cats);
                if (result == null)
                    throw new IOException(mActivity.getString(R.string.shiroikuma_eim_import_none));
                if (result.restored.isEmpty())
                    throw new IOException(mActivity.getString(R.string.shiroikuma_eim_import_nothing));
            } catch (Throwable t) {
                final String message = t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName();
                mActivity.runOnUiThread(() -> showInfo(
                        mActivity.getString(R.string.shiroikuma_eim_import_fail_title),
                        mActivity.getString(R.string.shiroikuma_eim_import_fail, message), false));
                return;
            } finally {
                //noinspection ResultOfMethodCallIgnored
                spool.delete();
            }
            // Same process as MainActivity / LoriePreferences (the sharedUid flavour puts every
            // component in com.termux), so the committed values are already what they read; this
            // is upstream's own "a preference changed from outside" signal so an open window and an
            // open preferences screen redraw from them.
            app.sendBroadcast(new Intent("com.termux.x11.ACTION_PREFERENCES_CHANGED")
                    .putExtra("key", "")
                    .putExtra("fromBroadcast", true)
                    .setPackage(app.getPackageName()));
            final String body = result.summary;
            mActivity.runOnUiThread(() -> showImportResult(body));
        }, "shiroikuma-import").start();
    }

    /**
     * The import result: a persistent bordered dialog with two pills — 「Later」 closes the whole
     * chain, 「Restart now」 relaunches the X window so it comes up on the restored preferences.
     *
     * <p>Deliberately NOT {@code Runtime.exit(0)}: in the sharedUid flavour this page runs inside
     * Termux's process, and exiting it would kill every terminal session on the phone. The prefs are
     * committed and already visible in-process, so a restart of the activity task is all a restart
     * can usefully mean here.
     */
    private void showImportResult(String summary) {
        String body = summary + "\n\n" + mActivity.getString(R.string.shiroikuma_eim_restart_hint);
        LinearLayout box = mUi.infoBox(mActivity.getString(R.string.shiroikuma_eim_import_done_title), body);
        final AlertDialog dialog = mUi.boxDialog(box, false);
        LinearLayout buttons = mUi.buttonRow();
        Button later = mUi.pill(mActivity.getString(R.string.shiroikuma_eim_restart_later), v -> {
            dialog.dismiss();
            dismiss();
            mHost.onChainFinished();
        });
        ((LinearLayout.LayoutParams) later.getLayoutParams()).rightMargin = dp(10);
        buttons.addView(later);
        buttons.addView(mUi.pill(mActivity.getString(R.string.shiroikuma_eim_restart_now), v -> {
            dialog.dismiss();
            dismiss();
            restartMainActivity();
        }));
        box.addView(buttons);
        dialog.show();
        mUi.transparentWindow(dialog);
    }

    private void restartMainActivity() {
        Intent restart = Intent.makeRestartActivityTask(new ComponentName(mActivity, MainActivity.class));
        mActivity.startActivity(restart);
        mHost.onChainFinished();
    }

    // ---- the export directory --------------------------------------------------------------------

    /** Called by the host after the SAF folder picker returns. */
    public void onDirPicked(Uri uri) {
        if (uri == null)
            return;
        ShiroikumaExport.storeDir(mActivity, uri);
        rebuild();
    }

    // ---- info dialogs ----------------------------------------------------------------------------

    /**
     * A bordered black-yellow info dialog with a single OK. When {@code closeChain} is set (a
     * successful export), acknowledging it closes this panel and the UI page too; failures only
     * dismiss the dialog, leaving the panel open to retry.
     */
    private void showInfo(String title, String body, final boolean closeChain) {
        mUi.info(title, body, !closeChain, () -> {
            if (closeChain) {
                dismiss();
                mHost.onChainFinished();
            }
        });
    }

    private int dp(float v) {
        return mUi.dp(v);
    }
}
