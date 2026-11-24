package io.github.chaohua.checkin;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.os.PersistableBundle;
import android.webkit.CookieManager;
import android.webkit.WebSettings;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public final class CheckinJob extends JobService {
    static final int ID = 31007;
    static final ExecutorService WORK = Executors.newSingleThreadExecutor();
    private static volatile long generation;
    private JobParameters active;
    private Future<?> task;

    static SharedPreferences prefs(Context ctx) { return ctx.getSharedPreferences("checkin", MODE_PRIVATE); }
    static Checkin client(Context ctx) {
        long session = generation;
        return new Checkin(new WebApi(CookieManager.getInstance(), WebSettings.getDefaultUserAgent(ctx),
                () -> generation == session), Thread::sleep);
    }
    static String day() { return LocalDate.now(ZoneId.of("Asia/Shanghai")).toString(); }
    static boolean due(SharedPreferences p) {
        return p.getBoolean("auto", false) && !day().equals(p.getString("complete", ""))
                && System.currentTimeMillis() - p.getLong("attempt", 0) >= 30 * 60_000L;
    }
    static void enqueue(Context ctx, boolean manual) {
        SharedPreferences p = prefs(ctx);
        if (p.getString("uid", "").isEmpty() || (!manual && !due(p))) return;
        JobScheduler scheduler = ctx.getSystemService(JobScheduler.class);
        if (scheduler.getPendingJob(ID) != null) return;
        PersistableBundle args = new PersistableBundle();
        args.putBoolean("manual", manual);
        args.putString("uid", p.getString("uid", ""));
        JobInfo info = new JobInfo.Builder(ID, new ComponentName(ctx, CheckinJob.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setExtras(args).build();
        int scheduled = scheduler.schedule(info);
        p.edit().putString("status", scheduled == JobScheduler.RESULT_SUCCESS
                ? "已排队，等待网络和系统调度" : "系统暂时无法安排签到，请稍后重试").apply();
    }

    static void cancel(Context ctx) {
        generation++;
        ctx.getSystemService(JobScheduler.class).cancel(ID);
    }

    @Override public boolean onStartJob(JobParameters params) {
        SharedPreferences p = prefs(this);
        String uid = params.getExtras().getString("uid");
        boolean manual = params.getExtras().getBoolean("manual");
        if (uid == null || !uid.equals(p.getString("uid", "")) || (!manual && !due(p))) return false;
        String startedDay = day();
        long session = generation;
        active = params;
        p.edit().putLong("attempt", System.currentTimeMillis()).putString("status", "正在读取关注列表并签到…").apply();
        Checkin client = client(this);
        Handler main = new Handler(Looper.getMainLooper());
        task = WORK.submit(() -> {
            String summary;
            boolean complete = false;
            try {
                Checkin.Result result = client.run(uid);
                summary = result.summary();
                complete = result.complete();
            } catch (InterruptedException cancelled) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception error) {
                summary = message(error);
            }
            String finalSummary = summary;
            boolean finalComplete = complete;
            main.post(() -> {
                if (active != params) return;
                if (generation == session && uid.equals(p.getString("uid", ""))) {
                    SharedPreferences.Editor edit = p.edit().putString("status", finalSummary)
                            .putLong("finished", System.currentTimeMillis());
                    if (finalComplete && startedDay.equals(day())) edit.putString("complete", startedDay);
                    edit.apply();
                }
                active = null;
                task = null;
                jobFinished(params, false);
            });
        });
        return true;
    }

    static String message(Exception error) {
        return error instanceof Checkin.Problem ? error.getMessage() : "请求未完成，请检查网络后重试";
    }

    @Override public boolean onStopJob(JobParameters params) {
        if (active != null && active.getJobId() == params.getJobId()) {
            active = null;
            if (task != null) task.cancel(true);
            task = null;
            SharedPreferences p = prefs(this);
            if (!p.getString("uid", "").isEmpty())
                p.edit().putString("status", "签到已中断，下次可继续检查").apply();
        }
        return false;
    }
}
