package com.lumen.tvlauncher;

import android.app.Notification;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.List;

/** Mirrors the user-facing notifications so the home screen can show a badge and a list. */
public final class NotificationService extends NotificationListenerService {
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final int SKIP_FLAGS = Notification.FLAG_ONGOING_EVENT
            | Notification.FLAG_FOREGROUND_SERVICE | Notification.FLAG_GROUP_SUMMARY;

    private static volatile NotificationService instance;
    private static Runnable listener;

    static void setListener(Runnable r) {
        listener = r;
    }

    static boolean isConnected() {
        return instance != null;
    }

    /** Newest first; skips ongoing/background-service noise and our own. */
    static List<StatusBarNotification> active() {
        List<StatusBarNotification> out = new ArrayList<>();
        NotificationService s = instance;
        if (s == null) return out;
        StatusBarNotification[] all;
        try {
            all = s.getActiveNotifications();
        } catch (RuntimeException e) {
            return out;
        }
        if (all == null) return out;
        for (StatusBarNotification sbn : all) {
            if (isUseful(s, sbn)) out.add(sbn);
        }
        out.sort((a, b) -> Long.compare(b.getPostTime(), a.getPostTime()));
        return out;
    }

    private static boolean isUseful(Context ctx, StatusBarNotification sbn) {
        Notification n = sbn.getNotification();
        if (sbn.getPackageName().equals(ctx.getPackageName())) return false;
        if ((n.flags & SKIP_FLAGS) != 0) return false;
        return !TextUtils.isEmpty(n.extras.getCharSequence(Notification.EXTRA_TITLE));
    }

    static void cancel(String key) {
        NotificationService s = instance;
        if (s == null) return;
        try {
            s.cancelNotification(key);
        } catch (RuntimeException ignored) {
        }
    }

    static void clearAll() {
        NotificationService s = instance;
        if (s == null) return;
        try {
            s.cancelAllNotifications();
        } catch (RuntimeException ignored) {
        }
    }

    @Override
    public void onListenerConnected() {
        instance = this;
        changed();
    }

    @Override
    public void onListenerDisconnected() {
        instance = null;
        changed();
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        changed();
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        changed();
    }

    private static void changed() {
        MAIN.post(() -> {
            Runnable l = listener;
            if (l != null) l.run();
        });
    }
}
