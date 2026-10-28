package com.xl.gpstool;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.location.LocationManager;
import android.os.Build;
import android.os.IBinder;
import android.os.SystemClock;

public class MockService extends Service {
    private boolean isMocking = false;
    private Thread mockThread;
    private LocationManager locationManager;
    private double targetLat = 0;
    private double targetLng = 0;
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
        // 核心：创建常驻通知栏，告诉系统“我在执行重要任务，不要杀我！”
        createNotificationChannel();
    }
    @Override
    public int onStartCommand(android.content.Intent intent, int flags, int startId) {
        if (intent != null) {
            stopSweepEngine(); // 每次收到新命令，先熄火旧的自动驾驶

            boolean isSweepMode = intent.getBooleanExtra("isSweepMode", false);

            if (isSweepMode) {
                // 🌟 收到【扫街模式】命令，启动火箭引擎！
                double startLat = intent.getDoubleExtra("lat", 0);
                double startLon = intent.getDoubleExtra("lon", 0);
                double finalTargetLat = intent.getDoubleExtra("targetLat", 0);
                double finalTargetLon = intent.getDoubleExtra("targetLon", 0);

                startSweepEngine(startLat, startLon, finalTargetLat, finalTargetLon); // 传给引擎

                if (!isMocking) startMocking(); // 确保底层履带在转

            } else {
                // 🚀 收到【普通瞬移】命令
                targetLat = intent.getDoubleExtra("lat", 0);
                targetLng = intent.getDoubleExtra("lon", 0);

                if (!isMocking) startMocking();
            }
        }
        return START_STICKY;
    }
    @android.annotation.SuppressLint("WrongConstant")
    private void startMocking() {
        isMocking = true;

        // 🛡️ 核心修复 1：强力清道夫！先强制拆除残留通道，防止系统报错卡死！
        String[] providers = {LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, "fused"};
        for (String provider : providers) {
            try {
                locationManager.removeTestProvider(provider);
            } catch (Exception e) {} // 忽略清理错误

            try {
                locationManager.addTestProvider(provider, false, false, false, false, true, true, true, 1, 1);
                locationManager.setTestProviderEnabled(provider, true);
            } catch (Exception e) {
                e.printStackTrace(); // 🛡️ 核心修复 2：就算报错也绝不 return，硬着头皮往下冲！
            }
        }

        mockThread = new Thread(() -> {
            while (isMocking) {
                try {
                    // 灵魂抖动算法
                    double offsetLat = (Math.random() - 0.5) * 0.00004;
                    double offsetLng = (Math.random() - 0.5) * 0.00004;
                    double realLat = targetLat + offsetLat;
                    double realLng = targetLng + offsetLng;

                    long time = System.currentTimeMillis();
                    long elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos();

                    // 批量火力覆盖：同时向 GPS、网络、融合通道疯狂发送坐标
                    for (String provider : providers) {
                        try {
                            Location mockLocation = new Location(provider);
                            mockLocation.setLatitude(realLat);
                            mockLocation.setLongitude(realLng);
                            mockLocation.setAltitude(30.0);
                            mockLocation.setAccuracy(1.0f); // 极高精度骗过系统
                            mockLocation.setSpeed(0.5f);    // 🛡️ 核心修复 3：伪装成有轻微移动（速度）
                            mockLocation.setBearing(90.0f); // 🛡️ 核心修复 4：伪装方向，防企业软件机器查杀
                            mockLocation.setTime(time);
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                                mockLocation.setElapsedRealtimeNanos(elapsedRealtimeNanos);
                            }
                            locationManager.setTestProviderLocation(provider, mockLocation);
                        } catch (Exception e) {
                            // 失败了也继续，绝不让引擎停转！
                        }
                    }

                    Thread.sleep(1000); // 每秒发送一次
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
        mockThread.start();
    }

    @Override
    public void onDestroy() {
        stopSweepEngine(); // 🌟 加在这里！关闭服务时，强制让扫街引擎熄火刹车
        super.onDestroy();
        isMocking = false;
        if (mockThread != null) {
            mockThread.interrupt();
            mockThread = null;
        }
        try {
            locationManager.removeTestProvider(LocationManager.GPS_PROVIDER);
            locationManager.removeTestProvider(LocationManager.NETWORK_PROVIDER);
            locationManager.removeTestProvider("fused");
        } catch (Exception e) {
            e.printStackTrace();
        }
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
                    .setContentText("后台不死鸟引擎已激活")
                    .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                    .build();
            // 绑定前台服务
            startForeground(1, notification);
        }
    }
    // --- 🌟 核心发动机逻辑 ---
    private void startSweepEngine(double startLat, double startLon, double endLat, double endLon) {
        currentSweepLat = startLat;
        currentSweepLon = startLon;

        // 1. 读取仪表盘参数
        android.content.SharedPreferences prefs = getSharedPreferences("MockSettings", MODE_PRIVATE);
        int speed = prefs.getInt("speed", 5);
        int wobble = prefs.getInt("wobble", 2);

        // 2. 算距离 (粗略的几何估算)
        double dLat = endLat - startLat;
        double dLon = endLon - startLon;
        double distanceInMeters = Math.sqrt(dLat * dLat + dLon * dLon) * 111000;

        // 3. 算步数
        remainingSteps = (int) (distanceInMeters / speed);

        if (remainingSteps <= 0) {
            targetLat = endLat;
            targetLng = endLon;
            return;
        }

        stepLat = dLat / remainingSteps;
        stepLon = dLon / remainingSteps;

        // 4. 启动心跳定时器
        sweepTimer = new java.util.Timer();
        sweepTimer.scheduleAtFixedRate(new java.util.TimerTask() {
            @Override
            public void run() {
                if (remainingSteps > 0) {
                    currentSweepLat += stepLat;
                    currentSweepLon += stepLon;

                    // 🛡️ 防封号装甲：加入真实摇摆误差
                    double randomWobbleLat = (random.nextDouble() - 0.5) * (wobble / 111000.0);
                    double randomWobbleLon = (random.nextDouble() - 0.5) * (wobble / 111000.0);

                    // 🌟 黑科技：直接修改底层履带正在读取的全局变量
                    targetLat = currentSweepLat + randomWobbleLat;
                    targetLng = currentSweepLon + randomWobbleLon;

                    remainingSteps--;
                } else {
                    targetLat = endLat;
                    targetLng = endLon;
                    stopSweepEngine(); // 抵达目的地，熄火
                }
            }
        }, 0, 1000); // 每秒跑一次
    }

    private void stopSweepEngine() {
        if (sweepTimer != null) {
            sweepTimer.cancel();
            sweepTimer = null;
        }
    }
}