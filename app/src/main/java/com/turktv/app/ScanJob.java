package com.turktv.app;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Gece kanal taraması: TV kullanılmazken (genelde gece) tüm kanallar sırayla denenir.
 * Açılmayanlar işaretlenir; listede aynı kanalın başka bir adresi varsa yedek olarak kaydedilir.
 */
public class ScanJob extends JobService {

    private static final int PERIODIC_ID = 7001, NOW_ID = 7002;

    interface Done {
        void done(int broken, int withBackup);
    }

    private static volatile Done listener;

    /** Günlük taramayı planla (TV boştayken, internet varken). */
    static void schedule(Context c) {
        try {
            JobScheduler js = (JobScheduler) c.getSystemService(Context.JOB_SCHEDULER_SERVICE);
            if (js == null) return;
            for (JobInfo j : js.getAllPendingJobs()) if (j.getId() == PERIODIC_ID) return;
            js.schedule(new JobInfo.Builder(PERIODIC_ID, new ComponentName(c, ScanJob.class))
                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                    .setRequiresDeviceIdle(true)
                    .setPeriodic(TimeUnit.HOURS.toMillis(24))
                    .setPersisted(false)
                    .build());
        } catch (Exception ignored) {
        }
    }

    /** Şimdi tara (Ayarlar'dan). */
    static void runNow(Context c, Done d) {
        listener = d;
        try {
            JobScheduler js = (JobScheduler) c.getSystemService(Context.JOB_SCHEDULER_SERVICE);
            if (js == null) return;
            js.schedule(new JobInfo.Builder(NOW_ID, new ComponentName(c, ScanJob.class))
                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                    .setOverrideDeadline(0)
                    .build());
        } catch (Exception ignored) {
        }
    }

    private Thread worker;

    @Override
    public boolean onStartJob(final JobParameters params) {
        worker = new Thread(() -> {
            int[] res = scan(getApplicationContext());
            final Done d = listener;
            listener = null;
            if (d != null) new Handler(Looper.getMainLooper()).post(() -> d.done(res[0], res[1]));
            jobFinished(params, false);
        }, "kanal-taramasi");
        worker.start();
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        if (worker != null) worker.interrupt();
        return true;
    }

    /** {bozuk kanal sayısı, yedeği bulunan} */
    static int[] scan(Context c) {
        final List<Channel> all = Channel.loadAll(c);
        final Map<String, Boolean> ok = Collections.synchronizedMap(new LinkedHashMap<>());
        ExecutorService pool = Executors.newFixedThreadPool(4);
        for (final Channel ch : all) {
            if (!ch.youtube.isEmpty()) continue; // resmi YouTube yayını olan kanallar ayrıca açılır
            pool.execute(() -> ok.put(ch.url, Health.probe(ch.url, ch.ua, ch.referer)));
        }
        pool.shutdown();
        try {
            pool.awaitTermination(15, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            return new int[]{0, 0};
        }
        List<String> broken = new ArrayList<>();
        Map<String, String> alt = new LinkedHashMap<>();
        for (Channel ch : all) {
            Boolean r = ok.get(ch.url);
            if (r == null || r) continue;
            broken.add(ch.url);
            String key = Channel.key(ch.name);
            for (Channel o : all) {
                if (o == ch || o.url.equals(ch.url)) continue;
                boolean same = (!ch.tvgId.isEmpty() && ch.tvgId.equals(o.tvgId)) || Channel.key(o.name).equals(key);
                if (same && Boolean.TRUE.equals(ok.get(o.url))) {
                    alt.put(ch.url, o.url);
                    break;
                }
            }
        }
        Prefs.scanResult(c, broken, alt);
        Health.init(c);
        return new int[]{broken.size(), alt.size()};
    }
}
