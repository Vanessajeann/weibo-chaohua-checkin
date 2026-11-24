package io.github.chaohua.checkin;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Set;

/** Mobile web flow ported from the upstream weibo.py. */
final class Checkin {
    static final class Problem extends IOException {
        Problem(String message) { super(message); }
    }
    interface Api { JSONObject get(String path) throws Exception; }
    interface Pause { void waitFor(long millis) throws InterruptedException; }
    static final String BASE = "https://m.weibo.cn";
    static final String LIST = "/api/container/getIndex?containerid=100803_-_followsuper";

    static final class Result {
        int already, signed, unknown, total;
        String stopped = "";
        boolean complete() { return stopped.isEmpty() && unknown == 0; }
        String summary() {
            String counts = "已识别条目 " + total + "，原已签 " + already + "，本次签成 " + signed
                    + "，未识别 " + unknown;
            return stopped.isEmpty() ? counts : counts + "。已停止：" + stopped;
        }
    }

    static boolean validButton(String path) {
        try {
            URI uri = URI.create(path);
            return !uri.isAbsolute() && uri.getRawAuthority() == null && uri.getRawFragment() == null
                    && "/api/container/button".equals(uri.getRawPath()) && uri.getRawQuery() != null;
        } catch (IllegalArgumentException invalid) {
            return false;
        }
    }

    private static boolean done(String label) {
        return label.contains("已签") || label.contains("明日再来") || label.contains("已打卡");
    }

    private final Api api;
    private final Pause pause;

    Checkin(Api api, Pause pause) { this.api = api; this.pause = pause; }

    String account() throws Exception {
        JSONObject data = api.get("/api/config").optJSONObject("data");
        if (data == null || !(data.optBoolean("login") || data.optInt("login", 0) == 1))
            throw new Checkin.Problem("请先在模块内完成微博网页登录");
        String uid = data.optString("uid", "");
        if (!uid.matches("[1-9][0-9]*")) throw new Checkin.Problem("微博未返回账号 UID，请重新确认登录");
        return uid;
    }

    Result run(String expectedAccount) throws Exception {
        if (!account().equals(expectedAccount)) throw new Checkin.Problem("登录账号已改变，请重新确认账号");
        Result result = new Result();
        LinkedHashMap<String, String> pending = new LinkedHashMap<>();
        Set<String> topics = new HashSet<>();
        Set<String> cursors = new HashSet<>();
        String cursor = "";
        for (int page = 0; ; page++) {
            String path = LIST + (cursor.isEmpty() ? "" : "&since_id=" + URLEncoder.encode(cursor, "UTF-8"));
            JSONObject reply = api.get(path);
            JSONObject data = reply.optJSONObject("data");
            JSONArray cards = data == null ? null : data.optJSONArray("cards");
            if (reply.optInt("ok") != 1 || cards == null) throw new Checkin.Problem("微博未返回有效的关注列表");
            boolean hasGroups = false;
            for (int c = 0; c < cards.length(); c++) {
                JSONObject card = cards.optJSONObject(c);
                JSONArray group = card == null ? null : card.optJSONArray("card_group");
                if (group == null) continue;
                hasGroups = true;
                for (int i = 0; i < group.length(); i++) {
                    JSONObject item = group.optJSONObject(i);
                    if (item == null) { result.unknown++; result.total++; continue; }
                    String name = item.optString("title_sub", "").trim();
                    if (name.isEmpty()) name = item.optString("title", "").trim();
                    String key = item.optString("scheme", "");
                    if (key.isEmpty()) key = name;
                    if (!key.isEmpty() && !topics.add(key)) continue;
                    result.total++;
                    boolean already = false;
                    String action = "";
                    JSONArray buttons = item.optJSONArray("buttons");
                    for (int b = 0; buttons != null && b < buttons.length(); b++) {
                        JSONObject button = buttons.optJSONObject(b);
                        if (button == null) continue;
                        String label = button.optString("name");
                        if (done(label)) already = true;
                        String link = button.optString("scheme");
                        if ((label.contains("签到") || label.contains("打卡")) && validButton(link)) action = link;
                    }
                    if (already) result.already++;
                    else if (action.isEmpty()) result.unknown++;
                    else if (pending.putIfAbsent(action, name) != null) result.total--;
                }
            }
            if (cards.length() > 0 && !hasGroups) throw new Checkin.Problem("超话列表格式已变化");
            cursor = data.optString("since_id", "");
            if (cursor.isEmpty() || cursor.equals("0") || cursor.equals("null")) break;
            if (!cursors.add(cursor) || page >= 39) throw new Checkin.Problem("分页未完整返回，请稍后重试");
            pause.waitFor(650);
        }

        for (String action : pending.keySet()) {
            pause.waitFor(2200);
            try {
                JSONObject reply = api.get(action);
                if (reply.optInt("ok") != 1) {
                    result.stopped = "微博未确认「" + pending.get(action) + "」签到，可在微博内查看";
                    break;
                }
                result.signed++;
            } catch (IOException network) {
                result.stopped = "「" + pending.get(action) + "」的签到结果未能确认。请稍后重新检查";
                break;
            }
        }
        return result;
    }
}
