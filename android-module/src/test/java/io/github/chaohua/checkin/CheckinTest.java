package io.github.chaohua.checkin;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.*;

public final class CheckinTest {
    private static final String SIGN = "/api/container/button?id=123";
    private final List<String> calls = new ArrayList<>();
    private JSONObject login(boolean value) throws Exception {
        return new JSONObject().put("data", new JSONObject().put("uid", "1234").put("login", value));
    }
    private JSONObject topic(String name, String button, String path) throws Exception {
        return new JSONObject().put("title_sub", name).put("buttons",
                new JSONArray().put(new JSONObject().put("name", button).put("scheme", path)));
    }
    private JSONObject page(String next, JSONObject... topics) throws Exception {
        JSONArray group = new JSONArray();
        for (JSONObject item : topics) group.put(item);
        return new JSONObject().put("ok", 1).put("data", new JSONObject().put("since_id", next)
                .put("cards", new JSONArray().put(new JSONObject().put("card_group", group))));
    }
    private Checkin service(JSONObject... replies) {
        return new Checkin(path -> { calls.add(path); return replies[calls.size() - 1]; }, millis -> {});
    }
    @Test public void twoPagesSignOnceAndSkipAlreadyDone() throws Exception {
        JSONObject pending = topic("一", "去签到", SIGN);
        Checkin.Result r = service(login(true), page("a+b", pending, topic("二", "明日再来", "")),
                page("", pending), new JSONObject().put("ok", 1)).run("1234");
        assertEquals(Arrays.asList("/api/config", Checkin.LIST, Checkin.LIST + "&since_id=a%2Bb", SIGN), calls);
        assertEquals(2, r.total); assertEquals(1, r.already); assertEquals(1, r.signed); assertTrue(r.complete());
    }
    @Test public void unknownButtonDoesNotMarkDayComplete() throws Exception {
        Checkin.Result r = service(login(true), page("", topic("一", "更多", SIGN))).run("1234");
        assertEquals(1, r.unknown); assertEquals(2, calls.size()); assertFalse(r.complete());
    }
    @Test public void repeatedCursorStopsBeforeWrites() throws Exception {
        Checkin task = service(login(true), page("next", topic("一", "打卡", SIGN)), page("next"));
        assertThrows(Checkin.Problem.class, () -> task.run("1234"));
        assertEquals(3, calls.size());
    }
    @Test public void failedPageStopsBeforeWrites() throws Exception {
        Checkin task = service(login(true), page("next", topic("一", "打卡", SIGN)), new JSONObject().put("ok", 0));
        assertThrows(Checkin.Problem.class, () -> task.run("1234"));
        assertEquals(3, calls.size());
    }
    @Test public void emptyListIsValid() throws Exception {
        Checkin.Result r = service(login(true), page("")).run("1234");
        assertTrue(r.complete()); assertEquals(0, r.total); assertEquals(2, calls.size());
    }
    @Test public void changedAccountStopsBeforeReadingTopics() throws Exception {
        Checkin task = service(login(true));
        assertThrows(Checkin.Problem.class, () -> task.run("5678"));
        assertEquals(1, calls.size());
    }
    @Test public void expiredLoginStopsBeforeReadingTopics() throws Exception {
        Checkin task = service(login(false));
        assertThrows(Checkin.Problem.class, () -> task.run("1234")); assertEquals(1, calls.size());
    }
    @Test public void rejectUnrelatedPathsAndExternalLinks() {
        assertTrue(Checkin.validButton(SIGN));
        for (String path : new String[]{"//example.com" + SIGN, "https://example.com" + SIGN,
                "/api/config?x=1", "/api/container/%62utton?a=1", SIGN + "#redirect"})
            assertFalse(Checkin.validButton(path));
    }
    @Test public void serverRefusalStopsRemainingTopics() throws Exception {
        Checkin.Result r = service(login(true), page("", topic("一", "签到", SIGN),
                topic("二", "签到", SIGN + "0")), new JSONObject().put("ok", 0)).run("1234");
        assertEquals(3, calls.size()); assertEquals(0, r.signed); assertFalse(r.complete());
    }
    @Test public void interruptionAfterSignPreservesConfirmedCount() throws Exception {
        JSONObject list = page("", topic("一", "签到", SIGN), topic("二", "签到", SIGN + "0"));
        Checkin job = new Checkin(path -> {
            if ("/api/config".equals(path)) return login(true);
            if (Checkin.LIST.equals(path)) return list;
            if (SIGN.equals(path)) return new JSONObject().put("ok", 1);
            throw new IOException("simulated timeout");
        }, millis -> {});
        Checkin.Result r = job.run("1234");
        assertFalse(r.complete()); assertEquals(1, r.signed);
    }
}
