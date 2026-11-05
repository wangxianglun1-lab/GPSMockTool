package com.xl.gpstool;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.location.Location;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

public class MockService extends Service {
    private volatile boolean isMocking = false;
    private Thread mockThread;
    private LocationManager locationManager;
    private PowerManager.WakeLock wakeLock;

    private volatile double targetLat = 0;
    private volatile double targetLng = 0;

    // --- 🌟 悬浮窗组件（防系统冻结核心外衣） ---
    private WindowManager windowManager;
    private View floatingView;

    // --- 🌟 扫街模式专属引擎部件 ---
    private java.util.Timer sweepTimer; // 自动驾驶心跳定时器
    private double currentSweepLat;     // 当前插值纬度
    private double currentSweepLon;     // 当前插值经度
    private double targetSweepLat;      // 目标终点纬度
    private double targetSweepLon;      // 目标终点经度
    private double stepLat;             // 每次心跳纬度该走多少
    private double stepLon;             // 每次心跳经度该走多少
    private int remainingSteps;         // 距离终点还有多少步
    private java.util.Random random = new java.util.Random(); // 摇摆发生器
    @Override
    public void onCreate() {
        super.onCreate();
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        createNotificationChannel();
        acquireWakeLock();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            stopSweepEngine();

            // 检查是否开启悬浮窗保活（默认开启）
            SharedPreferences prefs = getSharedPreferences("MockSettings", MODE_PRIVATE);
            boolean enableFloat = prefs.getBoolean("enable_float", true);
            if (enableFloat) {
                showFloatingWindow();
            } else {
                removeFloatingWindow();
            }

            boolean isSweepMode = intent.getBooleanExtra("isSweepMode", false);

            if (isSweepMode) {
                double startLat = intent.getDoubleExtra("lat", 0);
                double startLon = intent.getDoubleExtra("lon", 0);
                double finalTargetLat = intent.getDoubleExtra("targetLat", 0);
                double finalTargetLon = intent.getDoubleExtra("targetLon", 0);

                startSweepEngine(startLat, startLon, finalTargetLat, finalTargetLon);
                if (!isMocking) startMocking();
            } else {
                targetLat = intent.getDoubleExtra("lat", 0);
                targetLng = intent.getDoubleExtra("lon", 0);
                if (!isMocking) startMocking();
            }
        }
        return START_STICKY;
    }

    // 🌟 挂载纯代码高颜值可拖拽悬浮窗（无需新建 XML）
    @SuppressLint("ClickableViewAccessibility")
    private void showFloatingWindow() {
        if (floatingView != null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            return; // 未授权悬浮窗则跳过
        }

        try {
            windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
            TextView pillView = new TextView(this);
            pillView.setText("幻迹保活中");
            pillView.setTextColor(Color.WHITE);
            pillView.setTextSize(12f);
            pillView.setPadding(28, 14, 28, 14);

            // 绘制圆角半透明深色胶囊背景
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(Color.parseColor("#CC1E293B"));
            bg.setCornerRadius(50f);
            bg.setStroke(2, Color.parseColor("#4CAF50"));
            pillView.setBackground(bg);

            int layoutType = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    : WindowManager.LayoutParams.TYPE_PHONE;

            WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    layoutType,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT
            );
            params.gravity = Gravity.TOP | Gravity.START;
            params.x = 30;
            params.y = 300;

            // 支持手指随意拖动悬浮窗位置
            pillView.setOnTouchListener(new View.OnTouchListener() {
                private int initialX, initialY;
                private float initialTouchX, initialTouchY;

                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    switch (event.getAction()) {
                        case MotionEvent.ACTION_DOWN:
                            initialX = params.x;
                            initialY = params.y;
                            initialTouchX = event.getRawX();
                            initialTouchY = event.getRawY();
                            return true;
                        case MotionEvent.ACTION_MOVE:
                            params.x = initialX + (int) (event.getRawX() - initialTouchX);
                            params.y = initialY + (int) (event.getRawY() - initialTouchY);
                            if (windowManager != null && floatingView != null) {
                                windowManager.updateViewLayout(floatingView, params);
                            }
                            return true;
                    }
                    return false;
                }
            });

            floatingView = pillView;
            windowManager.addView(floatingView, params);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void removeFloatingWindow() {
        if (windowManager != null && floatingView != null) {
            try {
                windowManager.removeView(floatingView);
            } catch (Exception ignored) {}
            floatingView = null;
        }
    }

    @SuppressLint("WrongConstant")
    private void startMocking() {
        isMocking = true;
        final String[] providers = {LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, "fused"};

        for (String provider : providers) {
            try {
                locationManager.removeTestProvider(provider);
            } catch (Exception ignored) {}
            registerProvider(provider);
        }

        mockThread = new Thread(() -> {
            while (isMocking) {
                try {
                    // 1. 将百度地图选点的 BD-09 坐标纠偏转换为底层 GPS 标准 WGS-84 坐标（防止高德/学习通偏移500米）
                    double[] wgs84 = bd09ToWgs84(targetLat, targetLng);

                    // 2. 微米级呼吸抖动（约 1~2 米生理级微动，防止被判定为死坐标）
                    double offsetLat = (Math.random() - 0.5) * 0.000015;
                    double offsetLng = (Math.random() - 0.5) * 0.000015;
                    double finalLat = wgs84[0] + offsetLat;
                    double finalLng = wgs84[1] + offsetLng;

                    long nowTime = System.currentTimeMillis();
                    long elapsedNanos = SystemClock.elapsedRealtimeNanos();

                    // 3. 补齐虚拟卫星数量（骗过学习通/高德 SDK 卫星信噪比检查）
                    Bundle extras = new Bundle();
                    extras.putInt("satellites", 12);
                    extras.putInt("maxCn0", 42);
                    extras.putInt("meanCn0", 35);

                    for (String provider : providers) {
                        try {
                            Location mockLocation = new Location(provider);
                            mockLocation.setLatitude(finalLat);
                            mockLocation.setLongitude(finalLng);
                            mockLocation.setAltitude(35.0);
                            mockLocation.setAccuracy(2.5f);
                            mockLocation.setSpeed(0.08f);
                            mockLocation.setBearing(90.0f);
                            mockLocation.setTime(nowTime);
                            mockLocation.setElapsedRealtimeNanos(elapsedNanos);
                            mockLocation.setExtras(extras);

                            // 补齐 Android 8.0+ 硬件传感器精度校验字段（高德 SDK 必查项）
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                mockLocation.setVerticalAccuracyMeters(2.0f);
                                mockLocation.setSpeedAccuracyMetersPerSecond(0.1f);
                                mockLocation.setBearingAccuracyDegrees(2.0f);
                            }

                            locationManager.setTestProviderLocation(provider, mockLocation);
                        } catch (Exception e) {
                            // 如果系统重置了通道，立刻自动重连注册
                            registerProvider(provider);
                        }
                    }

                    // ⚡ 核心提速：从 1000ms 提升到 150ms（每秒轰炸 6~7 次，彻底压制真实 GPS/Wi-Fi 抢答）
                    Thread.sleep(150);
                } catch (InterruptedException e) {
                    break;
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
        mockThread.setPriority(Thread.MAX_PRIORITY);
        mockThread.start();
    }

    @SuppressLint("WrongConstant")
    private void registerProvider(String provider) {
        try {
            locationManager.addTestProvider(provider, false, false, false, false, true, true, true, 1, 1);
            locationManager.setTestProviderEnabled(provider, true);
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                locationManager.setTestProviderStatus(provider, LocationProvider.AVAILABLE, null, System.currentTimeMillis());
            }
        } catch (Exception ignored) {}
    }

    // --- 🧭 坐标纠偏算法：百度 BD-09 -> 国测局 GCJ-02 -> 国际卫星 WGS-84 ---
    private double[] bd09ToWgs84(double bdLat, double bdLon) {
        double x = bdLon - 0.0065, y = bdLat - 0.006;
        double z = Math.sqrt(x * x + y * y) - 0.00002 * Math.sin(y * Math.PI * 3000.0 / 180.0);
        double theta = Math.atan2(y, x) - 0.000003 * Math.cos(x * Math.PI * 3000.0 / 180.0);
        double gcjLon = z * Math.cos(theta);
        double gcjLat = z * Math.sin(theta);

        double dLat = transformLat(gcjLon - 105.0, gcjLat - 35.0);
        double dLon = transformLon(gcjLon - 105.0, gcjLat - 35.0);
        double radLat = gcjLat / 180.0 * Math.PI;
        double magic = Math.sin(radLat);
        magic = 1 - 0.00669342162296594323 * magic * magic;
        double sqrtMagic = Math.sqrt(magic);
        dLat = (dLat * 180.0) / ((6378245.0 * (1 - 0.00669342162296594323)) / (magic * sqrtMagic) * Math.PI);
        dLon = (dLon * 180.0) / (6378245.0 / sqrtMagic * Math.cos(radLat) * Math.PI);
        return new double[]{gcjLat - dLat, gcjLon - dLon};
    }

    private double transformLat(double x, double y) {
        double ret = -100.0 + 2.0 * x + 3.0 * y + 0.2 * y * y + 0.1 * x * y + 0.2 * Math.sqrt(Math.abs(x));
        ret += (20.0 * Math.sin(6.0 * x * Math.PI) + 20.0 * Math.sin(2.0 * x * Math.PI)) * 2.0 / 3.0;
        ret += (20.0 * Math.sin(y * Math.PI) + 40.0 * Math.sin(y / 3.0 * Math.PI)) * 2.0 / 3.0;
        ret += (160.0 * Math.sin(y / 12.0 * Math.PI) + 320 * Math.sin(y * Math.PI / 30.0)) * 2.0 / 3.0;
        return ret;
    }

    private double transformLon(double x, double y) {
        double ret = 300.0 + x + 2.0 * y + 0.1 * x * x + 0.1 * x * y + 0.1 * Math.sqrt(Math.abs(x));
        ret += (20.0 * Math.sin(6.0 * x * Math.PI) + 20.0 * Math.sin(2.0 * x * Math.PI)) * 2.0 / 3.0;
        ret += (20.0 * Math.sin(x * Math.PI) + 40.0 * Math.sin(x / 3.0 * Math.PI)) * 2.0 / 3.0;
        ret += (150.0 * Math.sin(x / 12.0 * Math.PI) + 300.0 * Math.sin(x / 30.0 * Math.PI)) * 2.0 / 3.0;
        return ret;
    }

    @SuppressLint("InvalidWakeLockTag")
    private void acquireWakeLock() {
        try {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (pm != null && wakeLock == null) {
                wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "HuanJiGPS:MockWakeLock");
                wakeLock.acquire();
            }
        } catch (Exception ignored) {}
    }

    @Override
    public void onDestroy() {
        stopSweepEngine();
        removeFloatingWindow();
        isMocking = false;
        if (mockThread != null) {
            mockThread.interrupt();
            mockThread = null;
        }
        if (wakeLock != null && wakeLock.isHeld()) {
            try { wakeLock.release(); } catch (Exception ignored) {}
        }
        try {
            locationManager.removeTestProvider(LocationManager.GPS_PROVIDER);
            locationManager.removeTestProvider(LocationManager.NETWORK_PROVIDER);
            locationManager.removeTestProvider("fused");
        } catch (Exception ignored) {}
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    "mock_service_channel",
                    "模拟定位运行中",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
            Notification notification = new Notification.Builder(this, "mock_service_channel")
                    .setContentTitle("定位伪装守护中")
                    .setContentText("后台不死鸟引擎与悬浮保活已激活")
                    .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                    .build();
            startForeground(1, notification);
        }
    }

    private void startSweepEngine(double startLat, double startLon, double endLat, double endLon) {
        currentSweepLat = startLat;
        currentSweepLon = startLon;

        SharedPreferences prefs = getSharedPreferences("MockSettings", MODE_PRIVATE);
        int speed = prefs.getInt("speed", 5);
        int wobble = prefs.getInt("wobble", 2);

        double dLat = endLat - startLat;
        double dLon = endLon - startLon;
        double distanceInMeters = Math.sqrt(dLat * dLat + dLon * dLon) * 111000;

        remainingSteps = (int) (distanceInMeters / speed);

        if (remainingSteps <= 0) {
            targetLat = endLat;
            targetLng = endLon;
            return;
        }

        stepLat = dLat / remainingSteps;
        stepLon = dLon / remainingSteps;

        sweepTimer = new java.util.Timer();
        sweepTimer.scheduleAtFixedRate(new java.util.TimerTask() {
            @Override
            public void run() {
                if (remainingSteps > 0) {
                    currentSweepLat += stepLat;
                    currentSweepLon += stepLon;
                    double randomWobbleLat = (random.nextDouble() - 0.5) * (wobble / 111000.0);
                    double randomWobbleLon = (random.nextDouble() - 0.5) * (wobble / 111000.0);
                    targetLat = currentSweepLat + randomWobbleLat;
                    targetLng = currentSweepLon + randomWobbleLon;
                    remainingSteps--;
                } else {
                    targetLat = endLat;
                    targetLng = endLon;
                    stopSweepEngine();
                }
            }
        }, 0, 1000);
    }

    private void stopSweepEngine() {
        if (sweepTimer != null) {
            sweepTimer.cancel();
            sweepTimer = null;
        }
    }
}