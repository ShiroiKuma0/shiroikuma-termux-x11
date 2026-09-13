package com.termux.x11.shiroikuma;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.documentfile.provider.DocumentFile;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SwitchPreferenceCompat;

import com.termux.x11.R;
import com.termux.x11.shiroikuma.automation.AutomationAuth;

/**
 * The 白い熊 Termux X11 UI page (ported from arcanechat's {@code ShiroikumaUiPreferenceFragment}):
 * one {@link PreferenceFragmentCompat} over {@code preferences_shiroikuma_ui.xml}, wiring every row
 * by key.
 *
 * <p>Sections: <b>Export / Import</b> — 「Export / Import…」 (the panel), 「Export directory」 (red
 * "not set" until chosen, tap → SAF tree picker), 「Last export」 (queried on resume on a background
 * thread), then the 保存復元 automation rows exactly here: the master switch (ON), 「Use
 * authorization token?」 (OFF), and the token row shown only while the token is asked for —
 * and <b>Reset</b>, which clears our own {@code shiroikuma_ui} prefs file after a confirm dialog.
 */
public class ShiroikumaUiFragment extends PreferenceFragmentCompat {

    private ExportImportPanel mPanel;
    private final ActivityResultLauncher<Uri> mPickExportDir =
            registerForActivityResult(new ActivityResultContracts.OpenDocumentTree(), this::onExportDirPicked);
    private final ActivityResultLauncher<String[]> mPickImportFile =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), this::onImportFilePicked);

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.preferences_shiroikuma_ui, rootKey);
        setDivider(null);
        setDividerHeight(0);

        Preference entry = findPreference("shiroikuma_eim_entry");
        if (entry != null)
            entry.setOnPreferenceClickListener(p -> {
                openPanel();
                return true;
            });

        Preference dir = findPreference("shiroikuma_eim_dir");
        if (dir != null)
            dir.setOnPreferenceClickListener(p -> {
                mPickExportDir.launch(ShiroikumaExport.exportDirUri(requireContext()));
                return true;
            });

        initAutomationRows();

        Preference reset = findPreference("shiroikuma_ui_reset");
        if (reset != null)
            reset.setOnPreferenceClickListener(p -> {
                confirmReset();
                return true;
            });
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshDirRow();
        refreshLastExportRow();
    }

    // ---- Export / Import rows --------------------------------------------------------------------

    private void refreshDirRow() {
        Preference dir = findPreference("shiroikuma_eim_dir");
        if (dir == null || getContext() == null)
            return;
        String label = ExportImportPanel.dirLabel(requireContext());
        if (label != null) {
            dir.setSummary(label);
            return;
        }
        String remembered = ExportImportPanel.rememberedDirLabel(requireContext());
        dir.setSummary(warn(remembered != null
                ? remembered + "\n" + getString(R.string.shiroikuma_eim_dir_regrant)
                : getString(R.string.shiroikuma_eim_dir_unset)));
    }

    /** The directory is listed on a background thread — a SAF query is IPC to the documents provider. */
    private void refreshLastExportRow() {
        final Context app = getContext() == null ? null : requireContext().getApplicationContext();
        if (app == null)
            return;
        new Thread(() -> {
            final CharSequence value;
            if (ShiroikumaExport.exportDir(app) == null) {
                value = warn(app.getString(R.string.shiroikuma_eim_last_nodir));
            } else {
                DocumentFile newest = ShiroikumaExport.newestExport(app);
                value = newest == null
                        ? warn(app.getString(R.string.shiroikuma_eim_last_none))
                        : app.getString(R.string.shiroikuma_eim_last_value,
                        ExportImportPanel.formatTs(app, newest.lastModified()),
                        ShiroikumaExport.humanSize(newest.length()));
            }
            Activity activity = getActivity();
            if (activity == null)
                return;
            activity.runOnUiThread(() -> {
                if (!isAdded())
                    return;
                Preference last = findPreference("shiroikuma_eim_last");
                if (last != null)
                    last.setSummary(value);
            });
        }, "shiroikuma-last-export").start();
    }

    private static CharSequence warn(String s) {
        SpannableString span = new SpannableString(s);
        span.setSpan(new ForegroundColorSpan(ShiroikumaDialogs.WARN), 0, s.length(), 0);
        return span;
    }

    private void openPanel() {
        final Activity activity = getActivity();
        if (activity == null)
            return;
        mPanel = new ExportImportPanel(activity, new ExportImportPanel.Host() {
            @Override
            public void pickExportDir(Uri initial) {
                mPickExportDir.launch(initial);
            }

            @Override
            public void pickImportFile() {
                mPickImportFile.launch(new String[]{"application/zip", "application/octet-stream", "*/*"});
            }

            @Override
            public void onChainFinished() {
                // A finished export/import closes the whole chain: info dialog → panel → this page.
                activity.finish();
            }
        });
        mPanel.show();
    }

    private void onExportDirPicked(Uri uri) {
        if (uri == null)
            return;
        if (mPanel != null && mPanel.isShowing())
            mPanel.onDirPicked(uri);
        else
            ShiroikumaExport.storeDir(requireContext(), uri);
        refreshDirRow();
        refreshLastExportRow();
    }

    private void onImportFilePicked(Uri uri) {
        if (uri != null && mPanel != null && mPanel.isShowing())
            mPanel.onImportFilePicked(uri);
    }

    // ---- 保存復元 automation (contract v2 §2) ------------------------------------------------------

    /**
     * Three rows, in the order every sister app shows them, inside the Export / Import section. The
     * master switch ships ON and the token is opt-in; the token row is drawn only while it is being
     * asked for — a 48-character secret under an off switch invites 白い熊 to paste it somewhere it
     * will do nothing.
     */
    private void initAutomationRows() {
        final Context ctx = requireContext();
        SwitchPreferenceCompat enabled = findPreference("shiroikuma_auto_enabled");
        if (enabled != null) {
            enabled.setChecked(AutomationAuth.isEnabled(ctx));
            enabled.setOnPreferenceChangeListener((p, value) -> {
                AutomationAuth.setEnabled(ctx, Boolean.TRUE.equals(value));
                return true;
            });
        }

        final AutomationTokenPreference token = findPreference("shiroikuma_auto_token");
        SwitchPreferenceCompat requireToken = findPreference("shiroikuma_auto_require_token");
        if (requireToken != null) {
            boolean required = AutomationAuth.isTokenRequired(ctx);
            requireToken.setChecked(required);
            if (token != null)
                token.setVisible(required);
            requireToken.setOnPreferenceChangeListener((p, value) -> {
                boolean now = Boolean.TRUE.equals(value);
                AutomationAuth.setTokenRequired(ctx, now);
                if (token != null)
                    token.setVisible(now);
                return true;
            });
        }

        if (token == null)
            return;
        updateTokenRow(token);
        token.setOnPreferenceClickListener(p -> {
            ClipboardManager cb = (ClipboardManager) ctx.getSystemService(Context.CLIPBOARD_SERVICE);
            if (cb != null)
                cb.setPrimaryClip(ClipData.newPlainText("automation_token", AutomationAuth.token(ctx)));
            Toast.makeText(ctx, R.string.shiroikuma_auto_token_copied, Toast.LENGTH_SHORT).show();
            return true;
        });
        token.setOnRegenerateListener(() -> {
            Activity activity = getActivity();
            if (activity == null)
                return;
            new ShiroikumaDialogs(activity).confirm(
                    getString(R.string.shiroikuma_auto_token_regen_title),
                    getString(R.string.shiroikuma_auto_token_regen_msg),
                    getString(R.string.shiroikuma_auto_regenerate),
                    () -> {
                        AutomationAuth.regenerateToken(ctx);
                        updateTokenRow(token);
                        Toast.makeText(ctx, R.string.shiroikuma_auto_token_regenerated, Toast.LENGTH_SHORT).show();
                    });
        });
    }

    private void updateTokenRow(AutomationTokenPreference token) {
        token.setSummary(AutomationAuth.abbreviate(AutomationAuth.token(requireContext()))
                + "\n" + getString(R.string.shiroikuma_auto_token_desc));
    }

    // ---- Reset -----------------------------------------------------------------------------------

    @SuppressLint("ApplySharedPref")
    private void confirmReset() {
        Activity activity = getActivity();
        if (activity == null)
            return;
        new ShiroikumaDialogs(activity).confirm(
                getString(R.string.shiroikuma_ui_reset_confirm_title),
                getString(R.string.shiroikuma_ui_reset_confirm_msg),
                getString(android.R.string.ok),
                () -> {
                    ShiroikumaExport.appContext(activity)
                            .getSharedPreferences(ShiroikumaExport.UI_PREFS, Context.MODE_PRIVATE)
                            .edit().clear().commit();
                    Toast.makeText(activity, R.string.shiroikuma_ui_reset_done, Toast.LENGTH_SHORT).show();
                });
    }
}
