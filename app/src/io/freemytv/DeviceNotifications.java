package io.freemytv;

import android.app.Notification;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.provider.Settings;
import android.service.notification.StatusBarNotification;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * A small, local-only cache for notifications that arrived while the launcher was not visible.  Android controls
 * access to the listener; this class neither requests a runtime permission nor changes any secure setting.
 */
final class DeviceNotifications {
    private static final String PREFS = "device-notifications", KEY = "recent";
    private static final int MAX = 20;

    private DeviceNotifications() {}

    static final class Entry {
        final String key, app, title, body;
        final long at;

        Entry(String key, String app, String title, String body, long at) {
            this.key = key;
            this.app = app;
            this.title = title;
            this.body = body;
            this.at = at;
        }
    }

    static boolean enabled(Context c) {
        String enabled = Settings.Secure.getString(c.getContentResolver(), "enabled_notification_listeners");
        String component = new ComponentName(c, DeviceNotificationService.class).flattenToString();
        return enabled != null && enabled.contains(component);
    }

    static void record(Context c, StatusBarNotification sbn) {
        if (sbn == null || c.getPackageName().equals(sbn.getPackageName())) return;
        Notification n = sbn.getNotification();
        Bundle extras = n == null ? null : n.extras;
        if (extras == null) return;
        String title = clean(extras.getCharSequence(Notification.EXTRA_TITLE));
        String body = clean(extras.getCharSequence(Notification.EXTRA_BIG_TEXT));
        if (body.isEmpty()) body = clean(extras.getCharSequence(Notification.EXTRA_TEXT));
        if (title.isEmpty() && body.isEmpty()) return;
        String app = appLabel(c, sbn.getPackageName());
        if (title.isEmpty()) title = app;
        if (body.length() > 500) body = body.substring(0, 497) + "…";
        Entry entry = new Entry(sbn.getKey(), app, title, body, sbn.getPostTime());

        JSONArray out = new JSONArray();
        try {
            out.put(json(entry));
            String old = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "[]");
            JSONArray previous = new JSONArray(old);
            for (int i = 0; i < previous.length() && out.length() < MAX; i++) {
                JSONObject item = previous.optJSONObject(i);
                if (item != null && !entry.key.equals(item.optString("key"))) out.put(item);
            }
        } catch (JSONException ignored) {}
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, out.toString()).apply();
    }

    static List<Entry> recent(Context c) {
        List<Entry> out = new ArrayList<>();
        long cutoff = System.currentTimeMillis() - 24 * 3600_000L;
        try {
            String raw = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "[]");
            JSONArray stored = new JSONArray(raw);
            for (int i = 0; i < stored.length() && out.size() < 4; i++) {
                JSONObject item = stored.optJSONObject(i);
                if (item == null || item.optLong("at") < cutoff) continue;
                String title = item.optString("title", item.optString("text"));
                if (!title.isEmpty()) out.add(new Entry(item.optString("key"), item.optString("app"), title,
                        item.optString("body"), item.optLong("at")));
            }
        } catch (JSONException ignored) {}
        return out;
    }

    static boolean open(String key) {
        return !key.isEmpty() && DeviceNotificationService.open(key);
    }

    static boolean dismiss(String key) {
        return !key.isEmpty() && DeviceNotificationService.dismiss(key);
    }

    static void remove(Context c, String key) {
        if (key.isEmpty()) return;
        JSONArray kept = new JSONArray();
        try {
            String raw = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "[]");
            JSONArray stored = new JSONArray(raw);
            for (int i = 0; i < stored.length(); i++) {
                JSONObject item = stored.optJSONObject(i);
                if (item != null && !key.equals(item.optString("key"))) kept.put(item);
            }
        } catch (JSONException ignored) {}
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, kept.toString()).apply();
    }

    private static JSONObject json(Entry entry) throws JSONException {
        return new JSONObject().put("key", entry.key).put("app", entry.app).put("title", entry.title)
                .put("body", entry.body).put("at", entry.at);
    }

    private static String appLabel(Context c, String pkg) {
        try {
            PackageManager pm = c.getPackageManager();
            ApplicationInfo info = pm.getApplicationInfo(pkg, 0);
            return String.valueOf(pm.getApplicationLabel(info));
        } catch (PackageManager.NameNotFoundException ignored) {
            return pkg;
        }
    }

    private static String clean(CharSequence value) {
        return value == null ? "" : TextUtils.join(" ", value.toString().trim().split("\\s+"));
    }
}
