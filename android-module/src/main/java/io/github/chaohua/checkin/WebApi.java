package io.github.chaohua.checkin;

import android.webkit.CookieManager;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import javax.net.ssl.HttpsURLConnection;

final class WebApi implements Checkin.Api {
    private final CookieManager cookies;
    private final String agent;
    private final BooleanSupplier sameSession;
    WebApi(CookieManager cookies, String agent, BooleanSupplier sameSession) {
        this.cookies = cookies;
        this.agent = agent;
        this.sameSession = sameSession;
    }

    @Override public JSONObject get(String path) throws Exception {
        if (!sameSession.getAsBoolean() || Thread.currentThread().isInterrupted()) throw new InterruptedException();
        if (!path.equals("/api/config") && !path.equals(Checkin.LIST)
                && !path.startsWith(Checkin.LIST + "&since_id=") && !Checkin.validButton(path))
            throw new Checkin.Problem("接口地址无效");
        String address = Checkin.BASE + path;
        HttpsURLConnection conn = (HttpsURLConnection) new URL(address).openConnection();
        try {
            conn.setInstanceFollowRedirects(false);
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);
            conn.setUseCaches(false);
            conn.setRequestProperty("User-Agent", agent);
            conn.setRequestProperty("Referer", Checkin.BASE + "/");
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("X-Requested-With", "XMLHttpRequest");
            String cookie = cookies.getCookie(address);
            if (cookie != null) conn.setRequestProperty("Cookie", cookie);
            int code = conn.getResponseCode();
            if (!sameSession.getAsBoolean() || Thread.currentThread().isInterrupted()) throw new InterruptedException();
            if (code != 200) throw new Checkin.Problem("微博返回 HTTP " + code + "，请检查网络或重新登录");
            for (Map.Entry<String, List<String>> header : conn.getHeaderFields().entrySet()) {
                if ("Set-Cookie".equalsIgnoreCase(header.getKey()))
                    for (String value : header.getValue()) cookies.setCookie(address, value);
            }
            try (InputStream input = conn.getInputStream(); ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                int n;
                while ((n = input.read(buffer)) != -1) {
                    if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
                    if (bytes.size() + n > 2 * 1024 * 1024) throw new Checkin.Problem("微博响应过大");
                    bytes.write(buffer, 0, n);
                }
                try { return new JSONObject(bytes.toString("UTF-8")); }
                catch (JSONException invalid) { throw new Checkin.Problem("微博返回了非接口页面，请在官方网页检查登录或验证提示"); }
            }
        } finally { conn.disconnect(); }
    }
}
