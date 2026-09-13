package com.termux.x11.shiroikuma.automation;

import android.content.Context;
import android.os.Build;
import android.os.PowerManager;

/**
 * What to answer when a foreground-service start is refused — the one place that decides it, so the
 * two sites in this app (the provider's {@code startForegroundService} and the data service's own
 * {@code startForeground}) cannot drift apart.
 *
 * <p>{@code ERROR:no-foreground-start} is a RESERVED KEY: 保存中核 matches it exactly and puts a
 * 「電池最適化を除外」 button on the failed row. So it is emitted ONLY when that button actually
 * repairs the fault — the throwable is the platform's {@code ForegroundServiceStartNotAllowedException}
 * <b>and</b> the app is not already battery-exempt. If the exemption is held and the start was still
 * refused, the cause is something the button cannot touch (on this phone, アプリ起動管理 on 自動管理),
 * and a descriptive line is the honest answer.
 *
 * <p>The refusal is matched by class NAME, never {@code instanceof}: the class is API 31 and this
 * module's {@code minSdk} is 24, so loading it to compare against would fail on an older device.
 */
final class AutomationForeground {

    static final String NO_FOREGROUND_START = "ERROR:no-foreground-start";

    private AutomationForeground() {
    }

    static String refusal(Context context, Throwable t) {
        if ("android.app.ForegroundServiceStartNotAllowedException".equals(t.getClass().getName())
                && !isBatteryExempt(context))
            return NO_FOREGROUND_START;
        return "ERROR:cannot start export service: " + t.getClass().getSimpleName();
    }

    private static boolean isBatteryExempt(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M)
            return true; // no exemption to grant below M, so the button could not help either
        try {
            PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            return pm != null && pm.isIgnoringBatteryOptimizations(context.getPackageName());
        } catch (Exception e) {
            return true; // unknown ⇒ do not promise a repair we cannot vouch for
        }
    }

    /** One short line, whatever the throwable carried — a reply is a single line by contract. */
    static String reason(Throwable t) {
        String message = t.getMessage();
        if (message == null || message.trim().isEmpty())
            message = t.getClass().getSimpleName();
        message = message.replace('\n', ' ').replace('\r', ' ').trim();
        return message.length() > 160 ? message.substring(0, 160) : message;
    }
}
