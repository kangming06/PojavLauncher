package net.kdt.pojavlaunch.services;

import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Process;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.kdt.mcgui.ProgressLayout;

import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.progresskeeper.ProgressKeeper;
import net.kdt.pojavlaunch.progresskeeper.TaskCountListener;

/**
 * Lazy service which allows the process not to get killed.
 * Can be created from context, can be killed statically
 */
public class ProgressService extends Service implements TaskCountListener {

    private final Handler mainThreadHandler = new Handler(Looper.getMainLooper());
    private NotificationManagerCompat notificationManagerCompat;

    /** Simple wrapper to start the service */
    public static void startService(Context context){
        Intent intent = new Intent(context, ProgressService.class);
        ContextCompat.startForegroundService(context, intent);
    }

    private NotificationCompat.Builder mNotificationBuilder;

    @Override
    public void onCreate() {
        Tools.buildNotificationChannel(getApplicationContext());
        notificationManagerCompat = NotificationManagerCompat.from(getApplicationContext());
        Intent killIntent = new Intent(getApplicationContext(), ProgressService.class);
        killIntent.putExtra("kill", true);
        PendingIntent pendingKillIntent = PendingIntent.getService(this, 0, killIntent, Build.VERSION.SDK_INT >=23 ? PendingIntent.FLAG_IMMUTABLE : 0);
        mNotificationBuilder = new NotificationCompat.Builder(this, "channel_id")
                .setContentTitle(getString(R.string.lazy_service_default_title))
                .addAction(android.R.drawable.ic_menu_close_clear_cancel,  getString(R.string.notification_terminate), pendingKillIntent)
                .setSmallIcon(R.drawable.notif_icon)
                .setNotificationSilent();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if(intent != null) {
            if(intent.getBooleanExtra("kill", false)) {
                stopSelf(); // otherwise Android tries to restart the service since it "crashed"
                Process.killProcess(Process.myPid());
                return START_NOT_STICKY;
            }
        }
        Log.d("ProgressService", "Started!");
        mNotificationBuilder.setContentText(getString(R.string.progresslayout_tasks_in_progress, ProgressKeeper.getTaskCount()));
        startForeground(1, mNotificationBuilder.build());
        if(ProgressKeeper.getTaskCount() < 1) stopSelf();
        else ProgressKeeper.addTaskCountListener(this, false);

        return START_NOT_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        ProgressKeeper.removeTaskCountListener(this);
    }

    @Override
    public void onUpdateTaskCount(int taskCount) {
        mainThreadHandler.post(()->{
            if(taskCount > 0) {
                mNotificationBuilder.setContentText(getString(R.string.progresslayout_tasks_in_progress, taskCount));
                notificationManagerCompat.notify(1, mNotificationBuilder.build());
            }else{
                stopSelf();
            }
        });
    }
}
