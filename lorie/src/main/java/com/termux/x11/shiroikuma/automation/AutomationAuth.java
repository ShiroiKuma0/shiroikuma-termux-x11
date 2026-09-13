package com.termux.x11.shiroikuma.automation;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;

import com.termux.x11.shiroikuma.ShiroikumaExport;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;

/**
 * The gate in front of the external-automation surface — the {@link StateExportReceiver} broadcasts
 * and the {@link AutomationProvider} data door — as the sister-app contract <b>v2</b> defines it
 * (ported from raikidoban's {@code AutomationAuth.java}).
 *
 * <p>Device-local by design: these values live in their OWN SharedPreferences file, which
 * {@link ShiroikumaExport} never reads, so the token never travels inside a backup and never leaves
 * the phone.
 *
 * <h3>v2: a switch that is ON, and a token that is OFF</h3>
 *
 * v1 shipped every app closed — the switch defaulted to false and every request also had to carry a
 * 48-character secret 白い熊 had pasted from here into the caller. That cannot serve the case this
 * family now exists for: 応用管理 restoring apps <i>and their data</i> onto a wiped phone, where
 * nothing has been configured and nobody has pasted anything. So {@code automation_enabled} defaults
 * to <b>true</b>, and the token is opt-in through {@code automation_require_token}, default
 * <b>false</b>. The switch stays because it is the only way to close this app off again.
 *
 * <h3>Idempotent about the token</h3>
 *
 * <b>A token handed to an app that does not require one is IGNORED. It is never an error.</b> Tokens
 * live in task arguments that outlive the setting they were pasted for; refusing one would turn
 * "白い熊 turned a switch off" into "half the batch mysteriously fails". The whole decision lives in
 * {@link #refuse} and nowhere else.
 *
 * <h3>Every write is {@code commit()}</h3>
 *
 * Because this gate fails OPEN: the default is now ON, so a {@code setEnabled(false)} lost to a
 * {@code SIGKILL} (応用管理 force-stops an app the instant it replies to an import) silently reopens
 * the door. Three tiny, infrequent writes — synchronous costs nothing anyone waits on.
 */
@SuppressLint("ApplySharedPref")
public final class AutomationAuth {

    /** The device-local prefs file; {@link ShiroikumaExport} excludes it from every archive. */
    public static final String PREFS_FILE = "shiroikuma_automation";
    private static final String KEY_ENABLED = "automation_enabled";
    private static final String KEY_REQUIRE_TOKEN = "automation_require_token";
    private static final String KEY_TOKEN = "automation_token";

    private AutomationAuth() {
    }

    private static SharedPreferences prefs(Context context) {
        return ShiroikumaExport.appContext(context).getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE);
    }

    /** The master switch. <b>Default ON</b> (v2) — a clean phone has nothing to turn on. */
    public static boolean isEnabled(Context context) {
        return prefs(context).getBoolean(KEY_ENABLED, true);
    }

    public static void setEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).commit();
    }

    /** Whether a caller must also present the token. <b>Default OFF</b> (v2). */
    public static boolean isTokenRequired(Context context) {
        return prefs(context).getBoolean(KEY_REQUIRE_TOKEN, false);
    }

    public static void setTokenRequired(Context context, boolean required) {
        prefs(context).edit().putBoolean(KEY_REQUIRE_TOKEN, required).commit();
    }

    /**
     * The one gate. Returns {@code null} to proceed, otherwise the exact {@code ERROR:} line to
     * answer with — "automation disabled" and "bad token" stay distinct because they debug
     * differently. When the token is not required, {@code candidate} is not even looked at.
     */
    public static String refuse(Context context, String candidate) {
        if (!isEnabled(context))
            return "ERROR:automation disabled";
        if (isTokenRequired(context) && !isTokenValid(context, candidate))
            return "ERROR:bad token";
        return null;
    }

    /** The shared secret — 24 random bytes, hex; generated lazily on first read so the row always shows one. */
    public static String token(Context context) {
        String stored = prefs(context).getString(KEY_TOKEN, null);
        if (stored != null && !stored.isEmpty())
            return stored;
        return regenerateToken(context);
    }

    public static String regenerateToken(Context context) {
        byte[] bytes = new byte[24];
        new SecureRandom().nextBytes(bytes);
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        String token = sb.toString();
        prefs(context).edit().putString(KEY_TOKEN, token).commit();
        return token;
    }

    /** Abbreviated form for the settings row — {@code 80922d8c…4c49a87c}. */
    public static String abbreviate(String token) {
        if (token == null)
            return "";
        if (token.length() <= 20)
            return token;
        return token.substring(0, 8) + "…" + token.substring(token.length() - 8);
    }

    /** Constant-time compare ({@link MessageDigest#isEqual}) — kept for the case where the token IS required. */
    public static boolean isTokenValid(Context context, String candidate) {
        if (candidate == null || candidate.isEmpty())
            return false;
        return MessageDigest.isEqual(
                candidate.getBytes(StandardCharsets.UTF_8),
                token(context).getBytes(StandardCharsets.UTF_8));
    }
}
