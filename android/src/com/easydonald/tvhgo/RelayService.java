package com.easydonald.tvhgo;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

/** 前台服务：在播放期间保持中继进程存活 */
public class RelayService extends Service {

    private static final String CHANNEL = "tvhgo_relay";

    @Override
    public void onCreate() {
        super.onCreate();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (nm.getNotificationChannel(CHANNEL) == null) {
                nm.createNotificationChannel(new NotificationChannel(
                        CHANNEL, "TvhGO", NotificationManager.IMPORTANCE_LOW));
            }
        }
        Notification.Builder b = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL)
                : new Notification.Builder(this);
        b.setContentTitle("TvhGO").setContentText(I18n.tr("串流中…")).setSmallIcon(android.R.drawable.stat_sys_download_done);
        startForeground(1001, b.build());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        StreamRelay.get().setIdleCallback(new StreamRelay.Idle() {
            public void onIdle() {
                stopSelf();
            }
        });
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        StreamRelay.get().setIdleCallback(null);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
