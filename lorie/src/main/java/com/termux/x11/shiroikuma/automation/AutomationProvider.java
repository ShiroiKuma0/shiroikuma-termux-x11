package com.termux.x11.shiroikuma.automation;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import com.termux.x11.shiroikuma.ShiroikumaExport;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * The data door: export this app's own state, and put it back, for a caller we can identify — the
 * v2 half of the sister-app contract (§2a), and what makes a clean-phone restore possible. It sits
 * <i>alongside</i> {@link StateExportReceiver} and replaces nothing. Ported from raikidoban.
 *
 * <h3>Why a provider and not the broadcast receiver next to it</h3>
 *
 * <b>A broadcast cannot tell you who sent it.</b> A provider gets the caller's identity from the
 * framework — see {@link AutomationCallers} for what is checked and why a package-name prefix would
 * have been worse than the token it replaced. <b>And a list needs a synchronous answer</b>: 応用管理
 * draws a row per installed app before any export exists.
 *
 * <h3>What does NOT happen here</h3>
 *
 * The payload. {@link #call} validates, starts a foreground service and returns; the bytes go
 * through a file descriptor the caller opened, and the terminal answer comes back on the broadcast
 * the family already proved on EMUI. {@code import} exists ONLY here — never as a broadcast action,
 * because the §1 receiver is exported with no permission and an import there would let any app on
 * the phone overwrite this one's preferences.
 *
 * <p>{@code describe} answers from things that exist before {@code Application.onCreate} — the
 * package info, a plain enum, SharedPreferences read directly — because a provider call is what
 * starts the process on a clean phone.
 */
public class AutomationProvider extends ContentProvider {

    private static final String TAG = "ShiroikumaAutomation";

    public static final String METHOD_DESCRIBE = "describe";
    public static final String METHOD_EXPORT = "export";
    public static final String METHOD_IMPORT = "import";
    public static final String METHOD_CANCEL = "cancel";

    public static final String KEY_RESULT = "result";
    public static final String KEY_FD = "fd";
    public static final String KEY_TOKEN = "token";
    public static final String KEY_JOB_ID = "job_id";
    /** The broadcast door's correlation extra; the provider door mirrors its job_id into it too. */
    public static final String KEY_REPLY_ID = "reply_id";
    public static final String KEY_ITEMS = "items";
    public static final String KEY_REPLY_ACTION = "reply_action";
    public static final String KEY_REPLY_PACKAGE = "reply_package";
    public static final String KEY_PROGRESS_ACTION = "progress_action";

    /** This app's archive format; bumped when an older build could no longer read what we write. */
    public static final int FORMAT = ShiroikumaExport.VERSION;
    /** The oldest archive this build can still read — what lets a restore be refused at discovery time. */
    public static final int MIN_FORMAT_READABLE = 1;

    @Override
    public boolean onCreate() {
        return true;
    }

    /**
     * Every method answers a {@link Bundle} with {@link #KEY_RESULT} — {@code OK…} or {@code ERROR:…},
     * the same vocabulary the broadcast contract uses. <b>A refusal is returned, never thrown</b>: an
     * exception across a binder reaches the caller as a stack trace that tells 白い熊 nothing.
     */
    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        Context ctx = getContext();
        if (ctx == null)
            return result("ERROR:not ready");
        ctx = ShiroikumaExport.appContext(ctx);

        // WHO, before WHAT. A caller we cannot identify gets the same answer whatever it asked for.
        String refusedCaller = AutomationCallers.verify(ctx, getCallingPackage());
        if (refusedCaller != null)
            return result(refusedCaller);
        // Then this app's own switches — a token is ignored unless this app asks for one (§2).
        String refused = AutomationAuth.refuse(ctx, extras == null ? null : extras.getString(KEY_TOKEN));
        if (refused != null)
            return result(refused);

        try {
            if (METHOD_DESCRIBE.equals(method))
                return result(describe(ctx));
            if (METHOD_EXPORT.equals(method))
                return start(ctx, extras, false);
            if (METHOD_IMPORT.equals(method))
                return start(ctx, extras, true);
            if (METHOD_CANCEL.equals(method)) {
                AutomationJobs.cancel(extras == null ? null : extras.getString(KEY_JOB_ID));
                return result("OK:cancelled");
            }
            return result("ERROR:unknown method: " + method);
        } catch (Throwable t) {
            Log.w(TAG, "automation call failed", t);
            return result("ERROR:" + AutomationForeground.reason(t));
        }
    }

    /**
     * What this app would export, answered without exporting anything — returned from the call
     * rather than written into the archive, so 応用管理 can draw a row before an export exists and
     * judge compatibility before streaming anything.
     *
     * <p>{@code requires_launch_first} is false: an import merges into SharedPreferences files with
     * {@code commit()}, which needs no Activity to have run. {@code requires_permissions} is empty:
     * the restore writes only this app's own files, never a permission-guarded system provider.
     */
    private static String describe(Context ctx) throws Exception {
        long code = 0;
        String name = "";
        try {
            PackageInfo info = ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0);
            @SuppressWarnings("deprecation")
            int legacy = info.versionCode;
            code = legacy;
            name = info.versionName == null ? "" : info.versionName;
        } catch (Exception e) {
            // a header without a version is still a usable header
        }
        JSONArray contains = new JSONArray();
        for (ShiroikumaExport.Cat cat : ShiroikumaExport.Cat.defaults())
            if (cat.parentId == null)
                contains.put(ctx.getString(cat.labelRes));
        JSONObject header = new JSONObject()
                .put("app_id", ctx.getPackageName())
                .put("version_code", code)
                .put("version_name", name)
                .put("format", FORMAT)
                .put("min_format_readable", MIN_FORMAT_READABLE)
                .put("requires_launch_first", false)
                .put("requires_permissions", new JSONArray())
                .put("contains", contains);
        return "OK:" + header;
    }

    /**
     * Hand the descriptor to the foreground service and get out of the way.
     *
     * <p>The descriptor is <b>duplicated</b> before it leaves this method: the one in {@code extras}
     * belongs to the binder transaction and is closed when {@code call()} returns. The service start
     * is a background start (API 31+ can refuse it outright); on refusal the dup is closed, the job
     * dropped, and the refusal RETURNED — no {@code OK:<job_id>} is ever handed out for a job that
     * will not run, so there is nothing to double-answer.
     */
    private static Bundle start(Context ctx, Bundle extras, boolean importing) {
        if (extras == null)
            return result("ERROR:no descriptor");
        @SuppressWarnings("deprecation")
        ParcelFileDescriptor fd = extras.getParcelable(KEY_FD);
        if (fd == null)
            return result("ERROR:no descriptor");
        ParcelFileDescriptor dup;
        try {
            dup = fd.dup();
        } catch (Exception e) {
            return result("ERROR:descriptor unusable");
        }
        String jobId = AutomationJobs.begin();
        try {
            AutomationDataService.start(ctx, jobId, dup, importing, extras);
        } catch (Throwable t) {
            AutomationJobs.finish(jobId);
            closeQuietly(dup);
            Log.w(TAG, "could not start the automation data service", t);
            return result(AutomationForeground.refusal(ctx, t));
        }
        return result("OK:" + jobId);
    }

    private static void closeQuietly(ParcelFileDescriptor fd) {
        try {
            fd.close();
        } catch (Exception ignored) {
            // an already-closed descriptor is the normal case here
        }
    }

    private static Bundle result(String result) {
        Bundle b = new Bundle();
        b.putString(KEY_RESULT, result);
        return b;
    }

    // A provider that is only ever call()ed still has to answer these. Refusing loudly beats
    // returning an empty cursor, which reads downstream as "there is no data" rather than "wrong door".

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] args, String order) {
        throw new UnsupportedOperationException("automation is call() only");
    }

    @Override
    public String getType(Uri uri) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException("automation is call() only");
    }

    @Override
    public int delete(Uri uri, String selection, String[] args) {
        throw new UnsupportedOperationException("automation is call() only");
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] args) {
        throw new UnsupportedOperationException("automation is call() only");
    }
}
