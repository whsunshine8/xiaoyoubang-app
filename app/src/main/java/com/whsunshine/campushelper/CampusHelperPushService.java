package com.whsunshine.campushelper;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;

import androidx.core.app.NotificationCompat;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class CampusHelperPushService extends Service {

    private static final String CHANNEL_FOREGROUND = "campushelper_fg_service";
    private static final String CHANNEL_MSG = "campushelper_msg_channel";
    private static final int FG_NOTIFICATION_ID = 10001;

    private Handler handler;
    private Runnable pollRunnable;
    private PowerManager.WakeLock wakeLock;
    private static final long POLL_INTERVAL_MS = 6000; // 每 6 秒轻量检查一次后台新消息

    private int lastBroadcastId = 0;
    private int lastTotalUnread = 0;

    @Override
    public void onCreate() {
        super.onCreate();
        try {
            createNotificationChannels();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(FG_NOTIFICATION_ID, buildForegroundNotification("校友帮消息守护中", "正在后台实时接收新订单、代办留言与系统通知"), android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
            } else {
                startForeground(FG_NOTIFICATION_ID, buildForegroundNotification("校友帮消息守护中", "正在后台实时接收新订单、代办留言与系统通知"));
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }

        try {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (pm != null) {
                wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "CampusHelper:PushServiceWakeLock");
                wakeLock.acquire(10 * 60 * 1000L /* 10 mins */);
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }

        try {
            SharedPreferences sp = getSharedPreferences("xyb_prefs", MODE_PRIVATE);
            lastBroadcastId = sp.getInt("last_broadcast_id", 0);
            lastTotalUnread = sp.getInt("last_total_unread", 0);

            handler = new Handler(Looper.getMainLooper());
            pollRunnable = new Runnable() {
                @Override
                public void run() {
                    checkBackendMessages();
                    if (handler != null) {
                        handler.postDelayed(this, POLL_INTERVAL_MS);
                    }
                }
            };
            handler.postDelayed(pollRunnable, 2000);
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            String token = intent.getStringExtra("token");
            if (token != null && !token.isEmpty()) {
                SharedPreferences sp = getSharedPreferences("xyb_prefs", MODE_PRIVATE);
                sp.edit().putString("token", token).apply();
            }
        }
        return START_STICKY;
    }

    private void checkBackendMessages() {
        new Thread(() -> {
            SharedPreferences sp = getSharedPreferences("xyb_prefs", MODE_PRIVATE);
            String token = sp.getString("token", "");
            if (token == null || token.isEmpty()) {
                return;
            }

            try {
                // 1. 检查最新系统广播通知
                checkBroadcasts();

                // 2. 检查 IM 未读消息总数与各订单详情
                checkUnreadMessages(token);

            } catch (Exception e) {
                // 忽略后台静默网络异常
            }
        }).start();
    }

    private void checkBroadcasts() {
        try {
            URL url = new URL("https://www.cnkiedu.cn/api/system/announcements");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);

            if (conn.getResponseCode() == 200) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
                reader.close();

                JSONObject res = new JSONObject(sb.toString());
                if (res.optBoolean("success")) {
                    JSONArray broadcasts = res.optJSONArray("broadcasts");
                    if (broadcasts != null && broadcasts.length() > 0) {
                        JSONObject latest = broadcasts.getJSONObject(0);
                        int bId = latest.optInt("id");
                        if (bId > lastBroadcastId && lastBroadcastId > 0) {
                            String title = latest.optString("title", "系统重要广播通知");
                            String content = latest.optString("content", "");
                            sendPopNotification(100 + bId, "📢 " + title, content, 0);
                        }
                        lastBroadcastId = bId;
                        getSharedPreferences("xyb_prefs", MODE_PRIVATE).edit().putInt("last_broadcast_id", lastBroadcastId).apply();
                    }
                }
            }
            conn.disconnect();
        } catch (Exception e) {}
    }

    private void checkUnreadMessages(String token) {
        try {
            URL url = new URL("https://www.cnkiedu.cn/api/im/unread-counts");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Authorization", "Bearer " + token);
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);

            if (conn.getResponseCode() == 200) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
                reader.close();

                JSONObject res = new JSONObject(sb.toString());
                if (res.optBoolean("success")) {
                    int total = res.optInt("total", 0);
                    if (total > lastTotalUnread && total > 0) {
                        // 有新增未读消息，弹出即时通知栏
                        JSONArray unreadArr = res.optJSONArray("unread");
                        int targetTaskId = 0;
                        String detailText = "您有 " + total + " 条新的订单沟通消息未查看，点击立即回复";
                        if (unreadArr != null && unreadArr.length() > 0) {
                            JSONObject firstUnread = unreadArr.getJSONObject(0);
                            targetTaskId = firstUnread.optInt("task_id");
                        }
                        sendPopNotification(20001, "💬 校友帮订单即时沟通新消息", detailText, targetTaskId);
                    }
                    lastTotalUnread = total;
                    getSharedPreferences("xyb_prefs", MODE_PRIVATE).edit().putInt("last_total_unread", lastTotalUnread).apply();
                }
            }
            conn.disconnect();
        } catch (Exception e) {}
    }

    private void sendPopNotification(int notifId, String title, String content, int taskId) {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;

        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        if (taskId > 0) {
            intent.putExtra("target_task_id", taskId);
        }

        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                notifId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0)
        );

        Uri soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_MSG)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(content)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(content))
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setDefaults(Notification.DEFAULT_ALL)
                .setSound(soundUri)
                .setVibrate(new long[]{0, 250, 150, 250})
                .setAutoCancel(true)
                .setContentIntent(pendingIntent);

        nm.notify(notifId, builder.build());
    }

    private Notification buildForegroundNotification(String title, String content) {
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0)
        );

        return new NotificationCompat.Builder(this, CHANNEL_FOREGROUND)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(content)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build();
    }

    private void createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) return;

            // 1. 前台守护服务通知通道 (静音低干扰)
            NotificationChannel fgChannel = new NotificationChannel(
                    CHANNEL_FOREGROUND,
                    "校友帮后台消息守护",
                    NotificationManager.IMPORTANCE_LOW
            );
            fgChannel.setDescription("保持校友帮在后台稳定接收代办沟通与系统广播");
            nm.createNotificationChannel(fgChannel);

            // 2. 消息与广播通知通道 (高优先级、震动、响铃)
            NotificationChannel msgChannel = new NotificationChannel(
                    CHANNEL_MSG,
                    "校友帮订单沟通与即时提醒",
                    NotificationManager.IMPORTANCE_HIGH
            );
            msgChannel.setDescription("收到新订单消息、盖章进度及系统重要广播时弹出通知");
            msgChannel.enableLights(true);
            msgChannel.setLightColor(Color.BLUE);
            msgChannel.enableVibration(true);
            msgChannel.setVibrationPattern(new long[]{0, 250, 150, 250});

            AudioAttributes audioAttr = new AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .build();
            Uri soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            msgChannel.setSound(soundUri, audioAttr);

            nm.createNotificationChannel(msgChannel);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        if (handler != null && pollRunnable != null) {
            handler.removeCallbacks(pollRunnable);
        }
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
        super.onDestroy();
    }
}
