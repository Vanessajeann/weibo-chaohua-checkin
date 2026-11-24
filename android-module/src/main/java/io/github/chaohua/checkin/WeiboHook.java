package io.github.chaohua.checkin;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Intent;
import android.os.SystemClock;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public final class WeiboHook implements IXposedHookLoadPackage {
    static final String HOST = "com.sina.weibo";
    static final String MODULE = "io.github.chaohua.checkin";
    private long nextSignal;

    @Override public void handleLoadPackage(XC_LoadPackage.LoadPackageParam pkg) {
        if (!HOST.equals(pkg.packageName) || !HOST.equals(pkg.processName)) return;
        XposedHelpers.findAndHookMethod(Activity.class, "onResume", new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam hook) {
                Activity activity = (Activity) hook.thisObject;
                if (!HOST.equals(activity.getPackageName())) return;
                long now = SystemClock.elapsedRealtime();
                if (now < nextSignal) return;
                nextSignal = now + 60_000;
                Intent inert = new Intent(MODULE + ".ORIGIN").setPackage(HOST);
                PendingIntent origin = PendingIntent.getBroadcast(activity, 0, inert, PendingIntent.FLAG_IMMUTABLE);
                Intent open = new Intent(MODULE + ".OPEN").setClassName(MODULE, MODULE + ".OpenReceiver");
                activity.sendBroadcast(open.putExtra("origin", origin));
            }
        });
    }
}
