package io.github.chaohua.checkin;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public final class OpenReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (!(WeiboHook.MODULE + ".OPEN").equals(intent.getAction())) return;
        Object origin;
        try {
            origin = intent.getParcelableExtra("origin");
        } catch (RuntimeException invalid) {
            return;
        }
        if (!(origin instanceof PendingIntent)
                || !WeiboHook.HOST.equals(((PendingIntent) origin).getCreatorPackage())) return;
        CheckinJob.prefs(context).edit().putLong("host", System.currentTimeMillis()).apply();
        CheckinJob.enqueue(context, false);
    }
}
