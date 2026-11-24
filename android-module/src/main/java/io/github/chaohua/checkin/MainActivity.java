package io.github.chaohua.checkin;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.WebStorage;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

import java.text.DateFormat;
import java.util.Date;

public final class MainActivity extends Activity implements SharedPreferences.OnSharedPreferenceChangeListener {
    private SharedPreferences prefs;
    private TextView account, status, host, finished;
    private Switch automatic;
    private Button sign, logout;
    private boolean refreshing;

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        prefs = CheckinJob.prefs(this);
        ScrollView scroll = new ScrollView(this);
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        int pad = Math.round(20 * getResources().getDisplayMetrics().density);
        form.setPadding(pad, pad, pad, pad);
        scroll.addView(form);
        setContentView(scroll);
        label(form, "微博超话签到", 26);
        account = label(form, "", 17);
        button(form, "登录 / 切换微博账号", v -> {
            CheckinJob.cancel(this);
            prefs.edit().putBoolean("auto", false).remove("uid").remove("complete").remove("attempt")
                    .remove("finished").putString("status", "等待确认微博账号").apply();
            startActivity(new Intent(this, LoginActivity.class));
        });
        automatic = new Switch(this);
        automatic.setText("打开微博时自动签到");
        form.addView(automatic);
        automatic.setOnCheckedChangeListener((v, on) -> {
            if (refreshing) return;
            prefs.edit().putBoolean("auto", on).apply();
            if (!on) {
                CheckinJob.cancel(this);
                prefs.edit().putString("status", "已关闭自动签到").apply();
            }
        });
        sign = button(form, "立即签到", v -> CheckinJob.enqueue(this, true));
        label(form, "签到状态", 19);
        status = label(form, "", 16);
        finished = label(form, "", 14);
        host = label(form, "", 14);
        label(form, "在 LSPosed 中勾选微博（com.sina.weibo），强行停止并重新打开微博后，可在此查看触发时间。", 15);
        button(form, "打开微博，检查触发", v -> {
            Intent launch = getPackageManager().getLaunchIntentForPackage(WeiboHook.HOST);
            if (launch != null) startActivity(launch);
            else new AlertDialog.Builder(this).setMessage("未找到微博标准版（com.sina.weibo）")
                    .setPositiveButton("确定", null).show();
        });
        logout = button(form, "退出模块账号", v -> new AlertDialog.Builder(this)
                .setMessage("清除模块内的微博登录并关闭自动签到？").setNegativeButton("取消", null)
                .setPositiveButton("退出", (d, w) -> {
                    CheckinJob.cancel(this);
                    prefs.edit().clear().apply();
                    CookieManager cookies = CookieManager.getInstance();
                    cookies.removeAllCookies(removed -> cookies.flush());
                    WebStorage.getInstance().deleteAllData();
                }).show());
    }

    private TextView label(LinearLayout form, String value, int size) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setPadding(0, 12, 0, 12);
        form.addView(view);
        return view;
    }
    private Button button(LinearLayout form, String text, View.OnClickListener click) {
        Button view = new Button(this);
        view.setText(text);
        view.setOnClickListener(click);
        form.addView(view);
        return view;
    }
    private String timestamp(String key) {
        long time = prefs.getLong(key, 0);
        return time == 0 ? "暂无记录" : DateFormat.getDateTimeInstance().format(new Date(time));
    }
    private void refresh() {
        refreshing = true;
        String uid = prefs.getString("uid", "");
        account.setText(uid.isEmpty() ? "请先登录模块内的微博官方网页" : "当前微博账号 UID " + uid);
        automatic.setEnabled(!uid.isEmpty());
        automatic.setChecked(prefs.getBoolean("auto", false));
        sign.setEnabled(!uid.isEmpty());
        logout.setEnabled(!uid.isEmpty() || CookieManager.getInstance().hasCookies());
        status.setText(prefs.getString("status", "等待首次签到"));
        host.setText("最近收到微博触发：" + timestamp("host"));
        finished.setText("最近完成检查：" + timestamp("finished"));
        refreshing = false;
    }
    @Override public void onSharedPreferenceChanged(SharedPreferences p, String key) { refresh(); }
    @Override protected void onResume() { super.onResume(); prefs.registerOnSharedPreferenceChangeListener(this); refresh(); }
    @Override protected void onPause() { prefs.unregisterOnSharedPreferenceChangeListener(this); super.onPause(); }
}
