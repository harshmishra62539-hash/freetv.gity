package io.freemytv;

import android.app.Notification;
import android.app.PendingIntent;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

/** Receives a copy only after the owner enables Notification access in Android Settings. */
public final class DeviceNotificationService extends NotificationListenerService {
    private static volatile DeviceNotificationService active;

    @Override public void onListenerConnected() {
        super.onListenerConnected();
        active = this;
        StatusBarNotification[] active = getActiveNotifications();
        if (active != null) for (StatusBarNotification notification : active) {
            DeviceNotifications.record(this, notification);
        }
    }

    @Override public void onDestroy() {
        if (active == this) active = null;
        super.onDestroy();
    }

    @Override public void onNotificationPosted(StatusBarNotification notification) {
        DeviceNotifications.record(this, notification);
    }

    @Override public void onNotificationRemoved(StatusBarNotification notification) {
        if (notification != null) DeviceNotifications.remove(this, notification.getKey());
    }

    static boolean open(String key) {
        DeviceNotificationService service = active;
        if (service == null) return false;
        StatusBarNotification[] all = service.getActiveNotifications();
        if (all == null) return false;
        for (StatusBarNotification sbn : all) if (key.equals(sbn.getKey())) {
            Notification n = sbn.getNotification();
            PendingIntent intent = n == null ? null : n.contentIntent;
            if (intent == null) return false;
            try {
                intent.send();
                return true;
            } catch (PendingIntent.CanceledException ignored) {
                return false;
            }
        }
        return false;
    }

    static boolean dismiss(String key) {
        DeviceNotificationService service = active;
        if (service == null) return false;
        try {
            service.cancelNotification(key);
            return true;
        } catch (RuntimeException ignored) {
            return false;
        }
    }
}
