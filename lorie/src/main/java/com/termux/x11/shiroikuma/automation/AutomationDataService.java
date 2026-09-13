package com.termux.x11.shiroikuma.automation;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.os.PowerManager;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.termux.x11.R;
import com.termux.x11.shiroikuma.ShiroikumaExport;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Where a data export or import started through {@link AutomationProvider} actually runs — a
 * {@code dataSync} foreground service, because a binder call must not hold the caller and a
 * backgrounded app writing into a caller's pipe can be frozen mid-stream on this phone. Ported from
 * raikidoban / arcanechat, with the contract's <b>three-step</b> start recipe:
 *
 * <ol>
 * <li><b>Read the extras.</b> No early returns here — the reply address must be in hand before the
 * call that can fail.</li>
 * <li><b>Go foreground, guarded.</b> Once {@code startForegroundService()} has been invoked the
 * platform requires {@code startForeground()} whatever this method then decides, and enforces it by
 * killing the process; but the call itself can be refused ({@code ForegroundServiceStartNotAllowedException}
 * on API 31+ when the app is not exempt). By then the provider has already answered {@code OK:<job_id>},
 * so the refusal is answered with the terminal broadcast carrying {@code job_id}, the descriptor is
 * closed, and the service stops.</li>
 * <li><b>Then the early returns</b> — a null intent, an unknown or already-consumed job — which stop
 * SILENTLY: that id's request has had its one terminal reply, and a second would break the
 * single-reply rule.</li>
 * </ol>
 *
 * <p>The descriptor was {@code dup()}ed by the provider and travels through {@link #HANDOVER} rather
 * than the Intent (a descriptor in an Intent extra is duplicated by the system on delivery and its
 * lifetime stops being ours). It has an owner from the moment it arrives: one {@code handedOff} flag
 * covers the whole window between draining the map and the worker taking it, and every other path
 * out closes it.
 */
public class AutomationDataService extends Service {

    private static final String TAG = "ShiroikumaAutomationData";
    private static final String CHANNEL = "shiroikuma_automation_data";
    private static final int NOTIFICATION_ID = 9714;
    private static final String EXTRA_JOB = "job";
    private static final String EXTRA_IMPORTING = "importing";
    /** Long enough for a stalled pipe, short enough not to strand the CPU. */
    private static final long WAKELOCK_TIMEOUT_MS = 5 * 60 * 1000L;

    private static final ConcurrentHashMap<String, ParcelFileDescriptor> HANDOVER = new ConcurrentHashMap<>();

    /** Site 3 of the contract's four: the provider's start. Throws on refusal, never stranding the descriptor. */
    public static void start(Context context, String jobId, ParcelFileDescriptor fd, boolean importing, Bundle extras) {
        HANDOVER.put(jobId, fd);
        Intent intent = new Intent(context, AutomationDataService.class);
        intent.putExtra(EXTRA_JOB, jobId);
        intent.putExtra(EXTRA_IMPORTING, importing);
        if (extras != null) {
            intent.putExtra(AutomationProvider.KEY_ITEMS, extras.getString(AutomationProvider.KEY_ITEMS));
            intent.putExtra(AutomationProvider.KEY_REPLY_ACTION, extras.getString(AutomationProvider.KEY_REPLY_ACTION));
            intent.putExtra(AutomationProvider.KEY_REPLY_PACKAGE, extras.getString(AutomationProvider.KEY_REPLY_PACKAGE));
            intent.putExtra(AutomationProvider.KEY_PROGRESS_ACTION, extras.getString(AutomationProvider.KEY_PROGRESS_ACTION));
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                context.startForegroundService(intent);
            else
                context.startService(intent);
        } catch (Throwable t) {
            HANDOVER.remove(jobId);
            throw t;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, final int startId) {
        // 1. The extras — defensively, and with no early return.
        final String jobId = intent == null ? null : intent.getStringExtra(EXTRA_JOB);
        final boolean importing = intent != null && intent.getBooleanExtra(EXTRA_IMPORTING, false);
        final String items = intent == null ? null : intent.getStringExtra(AutomationProvider.KEY_ITEMS);
        final String replyAction = intent == null ? null : intent.getStringExtra(AutomationProvider.KEY_REPLY_ACTION);
        final String replyPackage = intent == null ? null : intent.getStringExtra(AutomationProvider.KEY_REPLY_PACKAGE);
        final String progressAction = intent == null ? null : intent.getStringExtra(AutomationProvider.KEY_PROGRESS_ACTION);
        final Context app = ShiroikumaExport.appContext(this);

        final AtomicBoolean replied = new AtomicBoolean(false);
        final Replier reply = result -> {
            // Exactly one terminal answer per job, whatever path got here.
            if (!replied.compareAndSet(false, true))
                return;
            AutomationJobs.finish(jobId);
            if (replyAction == null || replyAction.trim().isEmpty()
                    || replyPackage == null || replyPackage.trim().isEmpty()) {
                Log.i(TAG, "job " + jobId + " finished with no reply channel: " + result);
                return;
            }
            Intent out = new Intent(replyAction.trim());
            out.setPackage(replyPackage.trim());
            out.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
            // The same id under both names, so one reader on the caller's side serves both doors.
            out.putExtra(AutomationProvider.KEY_JOB_ID, jobId);
            out.putExtra(AutomationProvider.KEY_REPLY_ID, jobId);
            out.putExtra(AutomationProvider.KEY_RESULT, result);
            try {
                app.sendBroadcast(out);
                Log.i(TAG, "replied to " + replyPackage + " [" + jobId + "]: " + result);
            } catch (Throwable t) {
                Log.w(TAG, "could not deliver the reply", t);
            }
        };

        // 2. Foreground, guarded (site 4). The descriptor is drained INSIDE the same try so a throw
        //    here cannot leave it held open in the map.
        ParcelFileDescriptor fd = null;
        try {
            enterForeground(importing);
            fd = jobId == null ? null : HANDOVER.remove(jobId);
        } catch (Throwable t) {
            Log.w(TAG, "could not enter the foreground", t);
            ParcelFileDescriptor stranded = jobId == null ? null : HANDOVER.remove(jobId);
            closeQuietly(stranded);
            reply.send(AutomationForeground.refusal(app, t));
            return stop(startId);
        }

        // 3. Only now the early returns — silent, since that id has had (or never will have) its reply.
        if (jobId == null || fd == null) {
            AutomationJobs.finish(jobId);
            return stop(startId);
        }

        final ParcelFileDescriptor owned = fd;
        boolean handedOff = false;
        try {
            Thread worker = new Thread(() -> {
                PowerManager.WakeLock wakeLock = null;
                try {
                    PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
                    if (pm != null) {
                        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "shiroikuma-termux-x11:automation-data");
                        wakeLock.acquire(WAKELOCK_TIMEOUT_MS);
                    }
                    if (importing)
                        runImport(app, jobId, owned, items, progressAction, replyPackage, reply);
                    else
                        runExport(app, jobId, owned, items, progressAction, replyPackage, reply);
                } catch (ShiroikumaExport.CancelledException e) {
                    reply.send("ERROR:cancelled");
                } catch (Throwable t) {
                    Log.w(TAG, "automation data job failed", t);
                    reply.send("ERROR:" + AutomationForeground.reason(t));
                } finally {
                    closeQuietly(owned);
                    AutomationJobs.finish(jobId);
                    if (wakeLock != null && wakeLock.isHeld())
                        wakeLock.release();
                    stop(startId);
                }
            }, "shiroikuma-automation-data");
            worker.start();
            handedOff = true;
        } finally {
            if (!handedOff) {
                closeQuietly(owned);
                reply.send("ERROR:could not start the job");
                stop(startId);
            }
        }
        return START_NOT_STICKY;
    }

    // ---- export ---------------------------------------------------------------------------------

    /** Writes the archive straight into the caller's descriptor, counting bytes as they go (the caller's file may be a pipe). */
    private void runExport(Context app, final String jobId, ParcelFileDescriptor fd, String items,
                           String progressAction, String replyPackage, Replier reply) throws Exception {
        Set<ShiroikumaExport.Cat> cats = StateExportReceiver.resolveItems(items);
        if (cats == null) {
            reply.send("ERROR:unknown category in items: " + items);
            return;
        }
        final long[] written = {0};
        AutomationProgress progress = new AutomationProgress(app, progressAction, replyPackage, jobId,
                new String[]{AutomationProvider.KEY_JOB_ID, AutomationProvider.KEY_REPLY_ID},
                app.getString(R.string.lorie_app_name), cats, () -> written[0]);
        progress.start();
        OutputStream raw = new ParcelFileDescriptor.AutoCloseOutputStream(fd);
        try {
            OutputStream counting = new OutputStream() {
                @Override
                public void write(int b) throws IOException {
                    raw.write(b);
                    written[0]++;
                }

                @Override
                public void write(byte[] b, int off, int len) throws IOException {
                    raw.write(b, off, len);
                    written[0] += len;
                }

                @Override
                public void flush() throws IOException {
                    raw.flush();
                }
            };
            ShiroikumaExport.export(app, cats, counting, progress, () -> AutomationJobs.isCancelled(jobId));
            counting.flush();
        } finally {
            progress.stop();
            raw.close();
        }
        if (AutomationJobs.isCancelled(jobId))
            reply.send("ERROR:cancelled");
        else
            reply.send("OK:" + written[0] + "|" + ShiroikumaExport.humanSize(written[0]) + "|" + cats.size() + " categories");
    }

    // ---- import — the half that exists ONLY behind the provider ---------------------------------

    /**
     * Spools the archive to a cache file, validates it there, then applies it: nothing is written
     * until the whole archive has arrived and been checked. The merge commits synchronously
     * ({@link ShiroikumaExport#importZip}), so the force-stop 応用管理 sends the instant we answer
     * OK cannot truncate what it protects.
     */
    private void runImport(Context app, String jobId, ParcelFileDescriptor fd, String items,
                           String progressAction, String replyPackage, Replier reply) throws Exception {
        File spool = new File(app.getCacheDir(), "automation-import-" + jobId + ".zip");
        AutomationProgress progress = new AutomationProgress(app, progressAction, replyPackage, jobId,
                new String[]{AutomationProvider.KEY_JOB_ID, AutomationProvider.KEY_REPLY_ID},
                app.getString(R.string.lorie_app_name), ShiroikumaExport.Cat.defaults(), null);
        progress.start();
        try {
            progress.note(app.getString(R.string.shiroikuma_auto_notif_spooling));
            long total;
            try (InputStream in = new ParcelFileDescriptor.AutoCloseInputStream(fd);
                 OutputStream out = new FileOutputStream(spool)) {
                total = ShiroikumaExport.copy(in, out, null);
                out.flush();
            }
            if (total == 0) {
                reply.send("ERROR:empty archive");
                return;
            }
            Set<ShiroikumaExport.Cat> wanted = StateExportReceiver.resolveItems(items);
            if (wanted == null) {
                reply.send("ERROR:unknown category in items: " + items);
                return;
            }
            List<ShiroikumaExport.Cat> present;
            try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(spool)) {
                present = ShiroikumaExport.categoriesIn(zip);
            }
            if (present.isEmpty()) {
                reply.send("ERROR:archive carries no categories");
                return;
            }
            Set<ShiroikumaExport.Cat> cats = new LinkedHashSet<>();
            for (ShiroikumaExport.Cat c : present)
                if (wanted.contains(c))
                    cats.add(c);
            if (cats.isEmpty()) {
                reply.send("ERROR:archive carries none of the requested categories");
                return;
            }
            progress.setCategories(cats);
            int n = 0;
            for (ShiroikumaExport.Cat c : cats)
                progress.onProgress(++n, cats.size(), app.getString(c.labelRes));
            ShiroikumaExport.ImportResult result = ShiroikumaExport.importZip(app, spool, cats);
            if (result == null) {
                reply.send("ERROR:not a shiroikuma-termux-x11 archive");
                return;
            }
            // Every file the restore touched was committed synchronously in importZip; nothing is
            // left in flight for 応用管理's force-stop to truncate.
            reply.send("OK:" + result.restored.size() + " categories restored");
        } finally {
            progress.stop();
            //noinspection ResultOfMethodCallIgnored
            spool.delete();
        }
    }

    // ---- foreground -----------------------------------------------------------------------------

    /** The typed overload where it exists (API 29+), the plain one below; both may throw, and the caller guards. */
    private void enterForeground(boolean importing) {
        Notification notification = notification(importing);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        else
            startForeground(NOTIFICATION_ID, notification);
    }

    private Notification notification(boolean importing) {
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && manager != null)
            manager.createNotificationChannel(new NotificationChannel(CHANNEL,
                    getString(R.string.shiroikuma_auto_notif_channel), NotificationManager.IMPORTANCE_LOW));
        return new NotificationCompat.Builder(this, CHANNEL)
                .setContentTitle(getString(importing ? R.string.shiroikuma_auto_notif_import : R.string.shiroikuma_auto_notif_export))
                .setSmallIcon(R.drawable.ic_x11_icon)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .setSilent(true)
                .build();
    }

    /** Always paired with {@link #enterForeground}: every return runs after the foreground call. */
    private int stop(int startId) {
        try {
            stopForeground(true);
        } catch (Throwable ignored) {
            // we may never have been foreground at all
        }
        stopSelf(startId);
        return START_NOT_STICKY;
    }

    private static void closeQuietly(ParcelFileDescriptor fd) {
        if (fd == null)
            return;
        try {
            fd.close();
        } catch (Exception ignored) {
            // an already-closed descriptor is the normal case here
        }
    }

    interface Replier {
        void send(String result);
    }
}
