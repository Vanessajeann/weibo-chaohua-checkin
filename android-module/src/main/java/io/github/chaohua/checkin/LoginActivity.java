package io.github.chaohua.checkin;

import android.app.Activity;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.CookieManager;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public final class LoginActivity extends Activity {
    private static final Set<String> HOSTS = new HashSet<>(Arrays.asList("m.weibo.cn", "weibo.com",
            "www.weibo.com", "passport.weibo.cn", "passport.weibo.com", "login.sina.com.cn",
            "login.sina.cn", "security.weibo.com"));
    private static final String LOGIN = "https://passport.weibo.cn/signin/login?entry=mweibo&r=https%3A%2F%2Fm.weibo.cn%2F";
    private WebView web;
    private TextView note;
    private Button confirm;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        note = new TextView(this);
        note.setPadding(16, 12, 16, 12);
        layout.addView(note);
        web = new WebView(this);
        layout.addView(web, new LinearLayout.LayoutParams(-1, 0, 1));
        confirm = new Button(this);
        confirm.setText("已在网页登录，确认此账号");
        confirm.setOnClickListener(v -> verify());
        layout.addView(confirm);
        setContentView(layout);
        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        CookieManager.getInstance().setAcceptCookie(true);
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
                if (!req.isForMainFrame()) return false;
                Uri uri = req.getUrl();
                boolean trusted = "https".equalsIgnoreCase(uri.getScheme()) && HOSTS.contains(uri.getHost())
                        && uri.getUserInfo() == null && (uri.getPort() == -1 || uri.getPort() == 443);
                if (!trusted) note.setText("请在微博官方网页内完成登录");
                return !trusted;
            }
            @Override public void onPageFinished(WebView view, String url) {
                note.setText("微博网页登录 · " + Uri.parse(url).getHost());
            }
        });
        if (state == null || web.restoreState(state) == null) web.loadUrl(LOGIN);
    }

    private void verify() {
        confirm.setEnabled(false);
        note.setText("正在向微博确认当前账号…");
        CookieManager.getInstance().flush();
        Checkin client = CheckinJob.client(this);
        CheckinJob.WORK.submit(() -> {
            try {
                String uid = client.account();
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    CheckinJob.prefs(this).edit().putString("uid", uid).putString("status", "账号已确认，可以签到").apply();
                    finish();
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    note.setText(CheckinJob.message(error));
                    confirm.setEnabled(true);
                });
            }
        });
    }

    @Override protected void onSaveInstanceState(Bundle out) { super.onSaveInstanceState(out); web.saveState(out); }
    @Override protected void onDestroy() {
        ((LinearLayout) web.getParent()).removeView(web);
        web.destroy();
        super.onDestroy();
    }
}
