package com.xl.gpstool;
import android.app.AppOpsManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {
    private boolean isProMode = false; // 记录当前是否处于 Pro 模式
    private Button btnSmartToggle;
    private boolean isMocking = false;
    private Button btnFavorite;
    private TextView btnViewFavorites;
    private DBHelper dbHelper;
    private boolean isSatelliteMode = false;
    private com.baidu.mapapi.map.MapView mMapView;
    private com.baidu.mapapi.map.BaiduMap mBaiduMap;
    private android.widget.TextView tvSelectedLocation;
    private double currentLat = 0;
    private double deviceRealLat = 0;
    private double deviceRealLon = 0;
    private double currentLon = 0;
    private com.baidu.mapapi.map.Marker currentMarker;

    private boolean isFirstLocate = true;
    private boolean isRouteSweepMode = false;

    private com.baidu.mapapi.search.sug.SuggestionSearch mSugSearch;
    private com.baidu.mapapi.search.geocode.GeoCoder mGeoCoder;
    private android.widget.ListPopupWindow mListPopupWindow;
    private java.util.List<com.baidu.mapapi.search.sug.SuggestionResult.SuggestionInfo> mSuggestionList;
    // 📢 弹窗 1：未检测到环境 (高颜值自定义版)
    private void showShizukuNotDetectedDialog() {
        android.view.View dialogView = android.view.LayoutInflater.from(MainActivity.this).inflate(R.layout.dialog_pro_alert, null);
        android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(MainActivity.this).setView(dialogView).create();

        // 把自带的白色丑背景变透明，让咱们 XML 里的圆角透出来
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
        }

        // 绑定 XML 里的控件
        android.widget.TextView tvTitle = dialogView.findViewById(R.id.tv_dialog_title);
        android.widget.TextView tvMessage = dialogView.findViewById(R.id.tv_dialog_message);
        android.widget.Button btnPositive = dialogView.findViewById(R.id.btn_dialog_positive);
        android.widget.Button btnNegative = dialogView.findViewById(R.id.btn_dialog_negative);

        // 设置文案
        tvTitle.setText("未检测到 Shizuku 服务");
        tvMessage.setText("开启Pro定位模式需要配合 Shizuku 框架使用。\n\n当前未检测到 Shizuku 在后台运行，无法激活该模式。\n\n请先前往应用商店下载 Shizuku，并在激活后重试。");
        btnPositive.setText("我知道了");

        // 只有一个按钮，取消键保持隐藏 (XML里默认 gone)
        btnPositive.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    // 📢 弹窗 2：环境检测成功，指引 (高颜值自定义版)
    private void showProModeInstructionsDialog() {
        android.view.View dialogView = android.view.LayoutInflater.from(MainActivity.this).inflate(R.layout.dialog_pro_alert, null);
        android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(MainActivity.this).setView(dialogView).create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
        }

        android.widget.TextView tvTitle = dialogView.findViewById(R.id.tv_dialog_title);
        android.widget.TextView tvMessage = dialogView.findViewById(R.id.tv_dialog_message);
        android.widget.Button btnPositive = dialogView.findViewById(R.id.btn_dialog_positive);
        android.widget.Button btnNegative = dialogView.findViewById(R.id.btn_dialog_negative);

        // 设置文案
        tvTitle.setText("Pro定位模式已就绪");
        tvMessage.setText("Shizuku 服务已成功连接。\n\n【必要操作指引】\n1. 请前往手机“设置 > 开发者选项”。\n2. 找到“选择模拟位置信息应用”，确保当前状态为【无】。\n3. 返回本软件，点击启动按钮即可生效。\n\n注：稍后若系统弹出 Shizuku 授权请求，请点击【始终允许】。");

        // 这里有两个按钮，把取消键显示出来
        btnNegative.setVisibility(android.view.View.VISIBLE);
        btnNegative.setText("已设置");
        btnPositive.setText("去系统设置");

        // 按钮点击事件
        btnNegative.setOnClickListener(v -> dialog.dismiss());
        btnPositive.setOnClickListener(v -> {
            dialog.dismiss();
            try {
                startActivity(new android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS));
            } catch (Exception e) {
                android.widget.Toast.makeText(MainActivity.this, "无法直接跳转，请手动前往", android.widget.Toast.LENGTH_SHORT).show();
            }
        });

        dialog.show();
    }
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 🌟 动态申请定位权限
        if (androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            androidx.core.app.ActivityCompat.requestPermissions(this,
                    new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.ACCESS_COARSE_LOCATION}, 100);
        }

        // --- 引擎通电代码 ---
        com.baidu.mapapi.SDKInitializer.setAgreePrivacy(this.getApplicationContext(), true);
        com.baidu.mapapi.SDKInitializer.initialize(this.getApplicationContext());

        setContentView(R.layout.activity_main);
        btnSmartToggle = findViewById(R.id.btn_smart_toggle);
        btnFavorite = findViewById(R.id.btn_favorite);
        btnViewFavorites = findViewById(R.id.menu_view_favorites);
// 🌟 侧边栏 Pro 模式开关 (商业级防崩溃护甲版)
        android.widget.Switch drawerSwitchPro = findViewById(R.id.switch_pro_mode);
        if (drawerSwitchPro != null) {
            drawerSwitchPro.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (!isChecked) {
                    isProMode = false;
                    android.widget.Toast.makeText(MainActivity.this, "已关闭 Pro 模式，切回常规伪装", android.widget.Toast.LENGTH_SHORT).show();
                    return;
                }

                try {
                    // 🛡️ 1. 探针：必须先检查底层电缆是否插好！(防崩溃核心)
                    if (!rikka.shizuku.Shizuku.pingBinder()) {
                        android.widget.Toast.makeText(MainActivity.this,
                                "🚨 未连上 Shizuku！\n请先确保 Shizuku 正在运行，然后【彻底划掉后台关闭本软件】，重新打开即可连上！",
                                android.widget.Toast.LENGTH_LONG).show();
                        buttonView.setChecked(false);
                        return;
                    }

                    // 2. 叩门：检查是否给了权限
                    if (rikka.shizuku.Shizuku.checkSelfPermission() != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                        rikka.shizuku.Shizuku.requestPermission(0);
                        android.widget.Toast.makeText(MainActivity.this, "👉 请在弹出的窗口点击【始终允许】，然后再次打开本开关！", android.widget.Toast.LENGTH_LONG).show();
                        buttonView.setChecked(false);
                        return;
                    }

                    // 3. 万事俱备，正式激活！
                    showProModeInstructionsDialog();
                    isProMode = true;

                } catch (Exception e) {
                    android.widget.Toast.makeText(MainActivity.this, "底层环境异常: " + e.getMessage(), android.widget.Toast.LENGTH_LONG).show();
                    android.util.Log.e("XL_DEBUG", "💥 Shizuku 连接失败：", e);
                    buttonView.setChecked(false);
                    isProMode = false;
                }
            });
        }
        // 🌟 1. “收藏当前位置”逻辑
        btnFavorite.setOnClickListener(v -> {
            android.view.View favView = android.view.LayoutInflater.from(MainActivity.this).inflate(R.layout.dialog_favorite, null);
            android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(MainActivity.this)
                    .setView(favView)
                    .setCancelable(false)
                    .create();
            if (dialog.getWindow() != null) {
                dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
            }
            android.widget.TextView tvCoords = favView.findViewById(R.id.tv_dialog_coords);
            if (tvCoords != null) {
                tvCoords.setText("当前坐标：" + String.format("%.6f", currentLat) + ", " + String.format("%.6f", currentLon));
            }
            android.widget.EditText input = favView.findViewById(R.id.et_dialog_name);
            android.view.View btnCancel = favView.findViewById(R.id.btn_dialog_fav_cancel);
            android.view.View btnSave = favView.findViewById(R.id.btn_dialog_fav_save);

            if (btnCancel != null) btnCancel.setOnClickListener(v2 -> dialog.dismiss());
            if (btnSave != null) {
                btnSave.setOnClickListener(v2 -> {
                    String placeName = input != null ? input.getText().toString().trim() : "";
                    if (placeName.isEmpty()) placeName = "未命名坐标";
                    dbHelper.addFavorite(placeName, currentLat, currentLon);
                    android.widget.Toast.makeText(MainActivity.this, "已成功收藏：" + placeName, android.widget.Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                });
            }
            dialog.show();
        });

        // 🌟 2. 专属名片
        android.widget.TextView btnAbout = findViewById(R.id.menu_about);
        btnAbout.setOnClickListener(v -> {
            String versionName = "未知版本";
            long versionCode = 0;
            try {
                android.content.pm.PackageInfo pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
                versionName = pInfo.versionName;
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                    versionCode = pInfo.getLongVersionCode();
                } else {
                    versionCode = pInfo.versionCode;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            android.view.View aboutView = android.view.LayoutInflater.from(MainActivity.this).inflate(R.layout.dialog_about, null);
            android.widget.TextView tvVersion = aboutView.findViewById(R.id.tv_about_version);
            if (tvVersion != null) {
                tvVersion.setText("当前版本：V " + versionName + " (Build " + versionCode + ")");
            }
            android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(MainActivity.this)
                    .setView(aboutView)
                    .create();
            if (dialog.getWindow() != null) {
                dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
            }
            android.view.View btnClose = aboutView.findViewById(R.id.btn_dialog_about_close);
            if (btnClose != null) btnClose.setOnClickListener(v2 -> dialog.dismiss());
            dialog.show();
        });

        // 🌟 3. 时光机 (历史记录)
        android.widget.TextView btnHistory = findViewById(R.id.menu_history);
        btnHistory.setOnClickListener(v -> {
            java.util.ArrayList<java.util.HashMap<String, String>> historyList = dbHelper.getAllHistory();
            if (historyList.isEmpty()) {
                android.widget.Toast.makeText(MainActivity.this, "暂无历史坐标记录", android.widget.Toast.LENGTH_SHORT).show();
                return;
            }
            java.util.List<java.util.Map<String, String>> dataList = new java.util.ArrayList<>();
            for (int i = 0; i < historyList.size(); i++) {
                java.util.Map<String, String> item = new java.util.HashMap<>();
                String latStr = historyList.get(i).get("lat");
                String lonStr = historyList.get(i).get("lon");
                String shortLat = latStr.length() > 7 ? latStr.substring(0, 7) : latStr;
                String shortLon = lonStr.length() > 7 ? lonStr.substring(0, 7) : lonStr;
                item.put("title", "时空足迹: " + historyList.get(i).get("time"));
                item.put("subtitle", "经度: " + shortLon + " | 纬度: " + shortLat);
                dataList.add(item);
            }
            android.view.View listViewLayout = android.view.LayoutInflater.from(MainActivity.this).inflate(R.layout.dialog_list, null);
            android.widget.TextView tvTitle = listViewLayout.findViewById(R.id.tv_dialog_list_title);
            if (tvTitle != null) tvTitle.setText("历史足迹");

            android.widget.ListView listView = listViewLayout.findViewById(R.id.lv_dialog_list);
            android.widget.SimpleAdapter adapter = new android.widget.SimpleAdapter(
                    MainActivity.this, dataList, R.layout.item_location,
                    new String[]{"title", "subtitle"}, new int[]{R.id.tv_item_title, R.id.tv_item_subtitle}
            );
            if (listView != null) listView.setAdapter(adapter);

            android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(MainActivity.this).setView(listViewLayout).create();
            if (dialog.getWindow() != null) dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
            dialog.show();

            android.view.View btnClose = listViewLayout.findViewById(R.id.btn_dialog_list_close);
            if (btnClose != null) btnClose.setOnClickListener(v2 -> dialog.dismiss());

            if (listView != null) {
                listView.setOnItemClickListener((parent, view, position, id) -> {
                    dialog.dismiss();
                    double targetLat = Double.parseDouble(historyList.get(position).get("lat"));
                    double targetLon = Double.parseDouble(historyList.get(position).get("lon"));
                    currentLat = targetLat;
                    currentLon = targetLon;
                    if (tvSelectedLocation != null) tvSelectedLocation.setText("当前准星：经度 " + String.format("%.6f", currentLon) + "，纬度 " + String.format("%.6f", currentLat));
                    mBaiduMap.animateMapStatus(com.baidu.mapapi.map.MapStatusUpdateFactory.newLatLng(new com.baidu.mapapi.model.LatLng(targetLat, targetLon)));
                    android.widget.Toast.makeText(MainActivity.this, "已加载历史坐标", android.widget.Toast.LENGTH_SHORT).show();
                });

                listView.setOnItemLongClickListener((parent, view, position, id) -> {
                    String timeToDelete = historyList.get(position).get("time");
                    String displayStr = dataList.get(position).get("title");
                    android.view.View confirmView = android.view.LayoutInflater.from(MainActivity.this).inflate(R.layout.dialog_confirm, null);
                    android.app.AlertDialog confirmDialog = new android.app.AlertDialog.Builder(MainActivity.this).setView(confirmView).create();
                    if (confirmDialog.getWindow() != null) confirmDialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));

                    android.widget.TextView tvMessage = confirmView.findViewById(R.id.tv_confirm_message);
                    if (tvMessage != null) tvMessage.setText("确定要彻底销毁这条足迹吗？\n" + displayStr);
                    confirmView.findViewById(R.id.btn_confirm_cancel).setOnClickListener(v3 -> confirmDialog.dismiss());
                    confirmView.findViewById(R.id.btn_confirm_delete).setOnClickListener(v3 -> {
                        dbHelper.deleteHistory(timeToDelete);
                        confirmDialog.dismiss();
                        dialog.dismiss();
                        android.widget.Toast.makeText(MainActivity.this, "记录已物理抹除", android.widget.Toast.LENGTH_SHORT).show();
                        btnHistory.performClick();
                    });
                    confirmDialog.show();
                    return true;
                });
            }
        });

        // 🌟 4. 参数设置 (呼叫全新的独立全屏设置页)
        android.widget.TextView btnSettings = findViewById(R.id.menu_settings);
        btnSettings.setOnClickListener(v -> {
            startActivity(new android.content.Intent(MainActivity.this, SettingsActivity.class));
            androidx.drawerlayout.widget.DrawerLayout drawer = findViewById(R.id.drawer_layout);
            drawer.closeDrawers();
        });

        // 🌟 5. 扫街模式
        android.widget.TextView btnRoute = findViewById(R.id.menu_route);
        btnRoute.setOnClickListener(v -> {
            androidx.drawerlayout.widget.DrawerLayout drawer1 = findViewById(R.id.drawer_layout);
            drawer1.closeDrawers();
            isRouteSweepMode = true;
            android.widget.Toast.makeText(MainActivity.this, "轨迹模式就绪，请在地图上【长按】选择你的终点！", android.widget.Toast.LENGTH_LONG).show();
        });

        dbHelper = new DBHelper(this);

        // 🌟 左上角抽屉开关
        androidx.appcompat.widget.Toolbar toolbar = findViewById(R.id.my_toolbar);
        androidx.drawerlayout.widget.DrawerLayout drawer1 = findViewById(R.id.drawer_layout);
        androidx.appcompat.app.ActionBarDrawerToggle toggle = new androidx.appcompat.app.ActionBarDrawerToggle(this, drawer1, toolbar, 0, 0);
        drawer1.addDrawerListener(toggle);
        toggle.syncState();

        // 🌟 地图绑定
        mMapView = findViewById(R.id.bmapView);
        mBaiduMap = mMapView.getMap();
        initRealLocation();

        // 🎯 悬浮复位准星
        android.view.View btnMyLocation = findViewById(R.id.btn_my_location);
        if (btnMyLocation != null) {
            btnMyLocation.setOnClickListener(v -> {
                if (deviceRealLat != 0.0 && deviceRealLon != 0.0) {
                    com.baidu.mapapi.model.LatLng realPoint = new com.baidu.mapapi.model.LatLng(deviceRealLat, deviceRealLon);
                    com.baidu.mapapi.map.MapStatusUpdate update = com.baidu.mapapi.map.MapStatusUpdateFactory.newLatLng(realPoint);
                    mBaiduMap.animateMapStatus(update);
                    android.widget.Toast.makeText(MainActivity.this, "已返回当前真实位置", android.widget.Toast.LENGTH_SHORT).show();
                } else {
                    android.widget.Toast.makeText(MainActivity.this, "卫星正在全力搜星中，请稍后重试...", android.widget.Toast.LENGTH_SHORT).show();
                }
            });
        }

        // 🌟 6. 地图长按（扫街模式锁定终点）
        mBaiduMap.setOnMapLongClickListener(targetLatLng -> {
            if (isRouteSweepMode) {
                isRouteSweepMode = false;
                Intent serviceIntent = new Intent(MainActivity.this, MockService.class);
                serviceIntent.putExtra("lat", currentLat);
                serviceIntent.putExtra("lon", currentLon);
                serviceIntent.putExtra("targetLat", targetLatLng.latitude);
                serviceIntent.putExtra("targetLon", targetLatLng.longitude);
                serviceIntent.putExtra("isSweepMode", true);
                startService(serviceIntent);
                isMocking = true;
                btnSmartToggle.setText("已停止模拟轨迹");
                btnSmartToggle.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#FF5722")));
                android.widget.Toast.makeText(MainActivity.this, "模拟轨迹中，将自动前往目标点...", android.widget.Toast.LENGTH_SHORT).show();
            }
        });

        tvSelectedLocation = findViewById(R.id.tv_selected_location);
        mMapView.showZoomControls(false);
        mMapView.showScaleControl(false);
        for (int i = 0; i < mMapView.getChildCount(); i++) {
            android.view.View child = mMapView.getChildAt(i);
            if (child instanceof android.widget.ImageView) child.setVisibility(android.view.View.INVISIBLE);
        }

        // 🌟 点击即落钉
        mBaiduMap.setOnMapClickListener(new com.baidu.mapapi.map.BaiduMap.OnMapClickListener() {
            @Override
            public void onMapClick(com.baidu.mapapi.model.LatLng latLng) {
                if (currentMarker != null) currentMarker.remove();
                currentLat = latLng.latitude;
                currentLon = latLng.longitude;

                android.graphics.Bitmap bitmap = android.graphics.Bitmap.createBitmap(60, 60, android.graphics.Bitmap.Config.ARGB_8888);
                android.graphics.Canvas canvas = new android.graphics.Canvas(bitmap);
                android.graphics.Paint paint = new android.graphics.Paint();
                paint.setColor(android.graphics.Color.parseColor("#E91E63"));
                canvas.drawCircle(30, 30, 30, paint);
                paint.setColor(android.graphics.Color.WHITE);
                canvas.drawCircle(30, 30, 15, paint);

                com.baidu.mapapi.map.BitmapDescriptor safeMarker = com.baidu.mapapi.map.BitmapDescriptorFactory.fromBitmap(bitmap);
                currentMarker = (com.baidu.mapapi.map.Marker) mBaiduMap.addOverlay(new com.baidu.mapapi.map.MarkerOptions().position(latLng).icon(safeMarker));
                tvSelectedLocation.setText("当前准星：经度 " + String.format("%.6f", currentLon) + "，纬度 " + String.format("%.6f", currentLat));
            }
            @Override
            public void onMapPoiClick(com.baidu.mapapi.map.MapPoi mapPoi) {
                onMapClick(mapPoi.getPosition());
            }
        });

        // 🌟 查看收藏夹
        btnViewFavorites.setOnClickListener(v -> {
            java.util.ArrayList<java.util.HashMap<String, String>> favList = dbHelper.getAllFavorites();
            if (favList.isEmpty()) {
                android.widget.Toast.makeText(MainActivity.this, "收藏空空如也，先去地图上收藏几个点吧！", android.widget.Toast.LENGTH_SHORT).show();
                return;
            }
            java.util.List<java.util.Map<String, String>> dataList = new java.util.ArrayList<>();
            for (int i = 0; i < favList.size(); i++) {
                java.util.Map<String, String> item = new java.util.HashMap<>();
                item.put("title", favList.get(i).get("name"));
                String latStr = favList.get(i).get("lat");
                String lonStr = favList.get(i).get("lon");
                item.put("subtitle", "经度: " + (lonStr.length() > 7 ? lonStr.substring(0, 7) : lonStr) + " | 纬度: " + (latStr.length() > 7 ? latStr.substring(0, 7) : latStr));
                dataList.add(item);
            }
            android.view.View listViewLayout = android.view.LayoutInflater.from(MainActivity.this).inflate(R.layout.dialog_list, null);
            android.widget.TextView tvTitle = listViewLayout.findViewById(R.id.tv_dialog_list_title);
            if (tvTitle != null) tvTitle.setText("选择目的地");

            android.widget.ListView listView = listViewLayout.findViewById(R.id.lv_dialog_list);
            android.widget.SimpleAdapter adapter = new android.widget.SimpleAdapter(
                    MainActivity.this, dataList, R.layout.item_location,
                    new String[]{"title", "subtitle"}, new int[]{R.id.tv_item_title, R.id.tv_item_subtitle}
            );
            if (listView != null) listView.setAdapter(adapter);

            android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(MainActivity.this).setView(listViewLayout).create();
            if (dialog.getWindow() != null) dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
            dialog.show();

            android.view.View btnClose = listViewLayout.findViewById(R.id.btn_dialog_list_close);
            if (btnClose != null) btnClose.setOnClickListener(v2 -> dialog.dismiss());

            if (listView != null) {
                listView.setOnItemClickListener((parent, view, position, id) -> {
                    dialog.dismiss();
                    double lat = Double.parseDouble(favList.get(position).get("lat"));
                    double lon = Double.parseDouble(favList.get(position).get("lon"));
                    currentLat = lat;
                    currentLon = lon;
                    tvSelectedLocation.setText("当前准星：经度 " + String.format("%.6f", currentLon) + "，纬度 " + String.format("%.6f", currentLat));
                    mBaiduMap.animateMapStatus(com.baidu.mapapi.map.MapStatusUpdateFactory.newLatLng(new com.baidu.mapapi.model.LatLng(lat, lon)));
                    android.widget.Toast.makeText(MainActivity.this, "已传送到：" + favList.get(position).get("name"), android.widget.Toast.LENGTH_SHORT).show();
                });
                listView.setOnItemLongClickListener((parent, view, position, id) -> {
                    String nameToDelete = favList.get(position).get("name");
                    android.view.View confirmView = android.view.LayoutInflater.from(MainActivity.this).inflate(R.layout.dialog_confirm, null);
                    android.app.AlertDialog confirmDialog = new android.app.AlertDialog.Builder(MainActivity.this).setView(confirmView).create();
                    if (confirmDialog.getWindow() != null) confirmDialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));

                    android.widget.TextView tvMessage = confirmView.findViewById(R.id.tv_confirm_message);
                    if (tvMessage != null) tvMessage.setText("确定要将 [" + nameToDelete + "] 彻底删除吗？");
                    confirmView.findViewById(R.id.btn_confirm_cancel).setOnClickListener(v3 -> confirmDialog.dismiss());
                    confirmView.findViewById(R.id.btn_confirm_delete).setOnClickListener(v3 -> {
                        dbHelper.deleteFavorite(nameToDelete);
                        confirmDialog.dismiss();
                        dialog.dismiss();
                        android.widget.Toast.makeText(MainActivity.this, "已清理：" + nameToDelete, android.widget.Toast.LENGTH_SHORT).show();
                        btnViewFavorites.performClick();
                    });
                    confirmDialog.show();
                    return true;
                });
            }
        });

// 🌟 智能启动/停止引擎 (专业版文案)
        btnSmartToggle.setOnClickListener(v -> {
            if (!isMocking) {
                if (isProMode) {
                    boolean shizukuSuccess = tryEnableMockLocationViaShizuku();
                    if (!shizukuSuccess) {
                        android.widget.Toast.makeText(MainActivity.this, "环境提权失败，请检查 Shizuku 授权状态", android.widget.Toast.LENGTH_SHORT).show();
                        return;
                    }
                } else {
                    // 轨道 B：常规模式
                    if (!isMockLocationEnabled()) {
                        new android.app.AlertDialog.Builder(MainActivity.this)
                                .setTitle("权限提示")
                                .setMessage("常规模式需要系统权限授权。\n\n请前往“设置 > 开发者选项 > 选择模拟位置信息应用”中，选择本应用。")
                                .setCancelable(false)
                                .setNegativeButton("取消", null)
                                .setPositiveButton("去设置", (dialog, which) -> startActivity(new android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)))
                                .show();
                        return;
                    }
                }

                // 🚀 启动引擎
                isMocking = true;
                android.content.Intent serviceIntent = new android.content.Intent(MainActivity.this, MockService.class);
                serviceIntent.putExtra("lat", currentLat);
                serviceIntent.putExtra("lon", currentLon);
                startService(serviceIntent);

                // 正常的提示文案
                android.widget.Toast.makeText(MainActivity.this, isProMode ? "Pro模拟定位已启动" : "模拟定位已启动", android.widget.Toast.LENGTH_SHORT).show();
                btnSmartToggle.setText("停止并恢复真实定位");
                btnSmartToggle.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#E91E63")));

                java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("MM-dd HH:mm:ss", java.util.Locale.getDefault());
                dbHelper.addHistory(sdf.format(new java.util.Date()), String.valueOf(currentLat), String.valueOf(currentLon));

            } else {
                // 停止引擎
                isMocking = false;
                stopService(new android.content.Intent(MainActivity.this, MockService.class));

                android.widget.Toast.makeText(MainActivity.this, "已停止模拟，恢复真实定位", android.widget.Toast.LENGTH_SHORT).show();
                btnSmartToggle.setText("启动虚拟定位");
                btnSmartToggle.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#4CAF50")));
            }
        });
        // 🛰️ 卫星/普通地图无缝切换逻辑
        android.widget.TextView btnSwitchMap = findViewById(R.id.btn_switch_map);
        if (btnSwitchMap != null) {
            btnSwitchMap.setOnClickListener(v -> {
                if (isSatelliteMode) {
                    mBaiduMap.setMapType(com.baidu.mapapi.map.BaiduMap.MAP_TYPE_NORMAL);
                    btnSwitchMap.setText("切换卫星地图");
                    android.widget.Toast.makeText(MainActivity.this, "已切回标准地图", android.widget.Toast.LENGTH_SHORT).show();
                } else {
                    mBaiduMap.setMapType(com.baidu.mapapi.map.BaiduMap.MAP_TYPE_SATELLITE);
                    btnSwitchMap.setText("切回普通地图");
                    android.widget.Toast.makeText(MainActivity.this, "卫星天眼已开启", android.widget.Toast.LENGTH_SHORT).show();
                }
                isSatelliteMode = !isSatelliteMode;
            });
        }

        // 📖 极客使用教程弹窗
        android.view.View btnHelpGuide = findViewById(R.id.btn_help_guide);
        if (btnHelpGuide != null) {
            btnHelpGuide.setOnClickListener(v -> {
                android.view.View helpView = android.view.LayoutInflater.from(MainActivity.this).inflate(R.layout.dialog_help, null);
                android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(MainActivity.this).setView(helpView).create();
                if (dialog.getWindow() != null) dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
                dialog.show();

                android.view.View btnToDeveloper = helpView.findViewById(R.id.btn_dialog_developer);
                android.view.View btnToLocation = helpView.findViewById(R.id.btn_dialog_location);
                android.view.View btnDialogClose = helpView.findViewById(R.id.btn_dialog_close);

                if (btnToDeveloper != null) {
                    btnToDeveloper.setOnClickListener(v2 -> {
                        dialog.dismiss();
                        try {
                            startActivity(new android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS));
                        } catch (Exception e) {
                            android.widget.Toast.makeText(MainActivity.this, "您的系统隐藏了该入口，请手动前往", android.widget.Toast.LENGTH_SHORT).show();
                        }
                    });
                }
                if (btnToLocation != null) {
                    btnToLocation.setOnClickListener(v2 -> {
                        dialog.dismiss();
                        try {
                            startActivity(new android.content.Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS));
                        } catch (Exception e) {
                            android.widget.Toast.makeText(MainActivity.this, "无法直接跳转", android.widget.Toast.LENGTH_SHORT).show();
                        }
                    });
                }
                if (btnDialogClose != null) btnDialogClose.setOnClickListener(v2 -> dialog.dismiss());
            });
        }

        // ==========================================
        // 🌟 万能 PoiSearch 地标搜索 🌟
        // ==========================================
        com.baidu.mapapi.search.poi.PoiSearch mPoiSearch = com.baidu.mapapi.search.poi.PoiSearch.newInstance();
        mPoiSearch.setOnGetPoiSearchResultListener(new com.baidu.mapapi.search.poi.OnGetPoiSearchResultListener() {
            @Override
            public void onGetPoiResult(com.baidu.mapapi.search.poi.PoiResult result) {
                if (result == null || result.error != com.baidu.mapapi.search.core.SearchResult.ERRORNO.NO_ERROR || result.getAllPoi() == null || result.getAllPoi().isEmpty()) {
                    android.widget.Toast.makeText(MainActivity.this, "万能引擎也搜不到，请尝试换个词", android.widget.Toast.LENGTH_SHORT).show();
                    return;
                }
                com.baidu.mapapi.search.core.PoiInfo firstPoi = result.getAllPoi().get(0);
                if (firstPoi.getLocation() != null) {
                    flyToTarget(firstPoi.getLocation().latitude, firstPoi.getLocation().longitude, firstPoi.getName());
                }
            }
            @Override public void onGetPoiDetailResult(com.baidu.mapapi.search.poi.PoiDetailResult result) {}
            @Override public void onGetPoiDetailResult(com.baidu.mapapi.search.poi.PoiDetailSearchResult result) {}
            @Override public void onGetPoiIndoorResult(com.baidu.mapapi.search.poi.PoiIndoorResult result) {}
        });

        mSugSearch = com.baidu.mapapi.search.sug.SuggestionSearch.newInstance();
        mListPopupWindow = new android.widget.ListPopupWindow(this);
        mSuggestionList = new java.util.ArrayList<>();

        mSugSearch.setOnGetSuggestionResultListener(new com.baidu.mapapi.search.sug.OnGetSuggestionResultListener() {
            @Override
            public void onGetSuggestionResult(com.baidu.mapapi.search.sug.SuggestionResult res) {
                if (res == null || res.getAllSuggestions() == null || res.getAllSuggestions().isEmpty()) {
                    mListPopupWindow.dismiss();
                    return;
                }
                mSuggestionList.clear();
                java.util.List<String> displayList = new java.util.ArrayList<>();

                for (com.baidu.mapapi.search.sug.SuggestionResult.SuggestionInfo info : res.getAllSuggestions()) {
                    if (info.key != null) {
                        mSuggestionList.add(info);
                        String desc = info.key;
                        if (info.city != null && !info.city.isEmpty()) {
                            desc += " (" + info.city + (info.district != null ? info.district : "") + ")";
                        }
                        displayList.add(desc);
                    }
                }
                if (displayList.isEmpty()) {
                    mListPopupWindow.dismiss();
                    return;
                }
                android.widget.ArrayAdapter<String> adapter = new android.widget.ArrayAdapter<>(MainActivity.this, android.R.layout.simple_list_item_1, displayList);
                mListPopupWindow.setAdapter(adapter);
                mListPopupWindow.show();
            }
        });

        androidx.appcompat.widget.Toolbar topToolbar = findViewById(R.id.my_toolbar);
        topToolbar.inflateMenu(R.menu.menu_main);
        android.view.MenuItem searchItem = topToolbar.getMenu().findItem(R.id.action_search);
        androidx.appcompat.widget.SearchView searchView = (androidx.appcompat.widget.SearchView) searchItem.getActionView();
        searchView.setQueryHint("输入并选择地点...");

        android.widget.EditText searchAutoComplete = searchView.findViewById(androidx.appcompat.R.id.search_src_text);
        if (searchAutoComplete != null) {
            searchAutoComplete.setTextColor(android.graphics.Color.WHITE);
            searchAutoComplete.setHintTextColor(android.graphics.Color.parseColor("#99FFFFFF"));
            searchAutoComplete.setBackground(null);
        }
        mListPopupWindow.setAnchorView(topToolbar);
        mListPopupWindow.setWidth(android.view.ViewGroup.LayoutParams.MATCH_PARENT);
        mListPopupWindow.setHeight(android.view.ViewGroup.LayoutParams.WRAP_CONTENT);

        mListPopupWindow.setOnItemClickListener((parent, view, position, id) -> {
            com.baidu.mapapi.search.sug.SuggestionResult.SuggestionInfo clickInfo = mSuggestionList.get(position);
            mListPopupWindow.dismiss();
            searchView.clearFocus();
            if (clickInfo.pt != null) {
                flyToTarget(clickInfo.pt.latitude, clickInfo.pt.longitude, clickInfo.key);
            } else {
                String city = (clickInfo.city != null && !clickInfo.city.isEmpty()) ? clickInfo.city : clickInfo.key;
                mPoiSearch.searchInCity(new com.baidu.mapapi.search.poi.PoiCitySearchOption().city(city).keyword(clickInfo.key).pageCapacity(1));
            }
        });

        searchView.setOnQueryTextListener(new androidx.appcompat.widget.SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                searchView.clearFocus();
                mListPopupWindow.dismiss();
                if (!query.trim().isEmpty()) {
                    android.widget.Toast.makeText(MainActivity.this, "启动万能地标雷达: " + query, android.widget.Toast.LENGTH_SHORT).show();
                    mPoiSearch.searchInCity(new com.baidu.mapapi.search.poi.PoiCitySearchOption().city(query.trim()).keyword(query.trim()).pageCapacity(1));
                }
                return true;
            }
            @Override
            public boolean onQueryTextChange(String newText) {
                if (newText.trim().isEmpty()) {
                    mListPopupWindow.dismiss();
                } else {
                    mSugSearch.requestSuggestion(new com.baidu.mapapi.search.sug.SuggestionSearchOption().city(newText.trim()).keyword(newText.trim()));
                }
                return true;
            }
        });
    }

    // ==========================================
    // 🚀 核心飞行器与定位模块
    // ==========================================
    private void flyToTarget(double lat, double lon, String name) {
        com.baidu.mapapi.model.LatLng latLng = new com.baidu.mapapi.model.LatLng(lat, lon);
        mBaiduMap.animateMapStatus(com.baidu.mapapi.map.MapStatusUpdateFactory.newLatLngZoom(latLng, 15.0f));

        if (currentMarker != null) currentMarker.remove();
        currentLat = lat;
        currentLon = lon;

        android.graphics.Bitmap bitmap = android.graphics.Bitmap.createBitmap(60, 60, android.graphics.Bitmap.Config.ARGB_8888);
        android.graphics.Canvas canvas = new android.graphics.Canvas(bitmap);
        android.graphics.Paint paint = new android.graphics.Paint();
        paint.setColor(android.graphics.Color.parseColor("#E91E63"));
        canvas.drawCircle(30, 30, 30, paint);
        paint.setColor(android.graphics.Color.WHITE);
        canvas.drawCircle(30, 30, 15, paint);
        com.baidu.mapapi.map.BitmapDescriptor safeMarker = com.baidu.mapapi.map.BitmapDescriptorFactory.fromBitmap(bitmap);

        currentMarker = (com.baidu.mapapi.map.Marker) mBaiduMap.addOverlay(new com.baidu.mapapi.map.MarkerOptions().position(latLng).icon(safeMarker));
        tvSelectedLocation.setText("已锁定: " + name + " (经纬度: " + String.format("%.6f, %.6f", lon, lat) + ")");
    }
    // 🌟 魔法流核心：Shizuku 静默提权 (公开发布版 - 适配全机型)
    private boolean tryEnableMockLocationViaShizuku() {
        try {
            // 1. 检查 Shizuku 授权状态
            if (rikka.shizuku.Shizuku.checkSelfPermission() != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                return false;
            }

            // 2. 💣 执行提权命令 (因为 Shizuku 作者隐藏了此方法，必须用反射强行调用！)
            String command = "appops set " + getPackageName() + " android:mock_location allow";
            java.lang.reflect.Method execMethod = rikka.shizuku.Shizuku.class.getDeclaredMethod("newProcess", String[].class, String[].class, String.class);
            execMethod.setAccessible(true); // 强行获取私有方法访问权

            Object process = execMethod.invoke(null, new String[]{"sh", "-c", command}, null, null);

            if (process != null) {
                // 等待命令执行完毕
                process.getClass().getMethod("waitFor").invoke(process);

                // 获取退出码 (0代表成功)
                Object exitValue = process.getClass().getMethod("exitValue").invoke(process);
                if (exitValue instanceof Integer && (Integer) exitValue == 0) {
                    return true; // 完美提权成功！
                }
            }

            // 3. 走到这里说明执行失败（如澎湃OS底层物理拦截）
            android.widget.Toast.makeText(MainActivity.this,
                    " 系统(如澎湃OS)拦截了提权指令！请关闭 Pro 模式，使用常规模式启动。",
                    android.widget.Toast.LENGTH_LONG).show();
            return false;

        } catch (Exception e) {
            android.util.Log.e("XL_DEBUG", "Shizuku 提权被系统强杀: ", e);
            android.widget.Toast.makeText(MainActivity.this,
                    " 系统安全限制过高！请关闭 Pro 模式，直接点击底部【启动虚拟定位】去开发者选项中设置。",
                    android.widget.Toast.LENGTH_LONG).show();
            return false;
        }
    }
    // 🌟 核心修复：解决高版本安卓（包括澎湃OS）“已授权但依然死循环弹窗”的系统级 Bug
    private boolean isMockLocationEnabled() {
        boolean isMockLocation = false;
        try {
            AppOpsManager opsManager = (AppOpsManager) getSystemService(Context.APP_OPS_SERVICE);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // 🚨 安卓 10 及以上（API 29+），必须使用 unsafeCheckOp！
                // 如果还用老旧的 checkOp，系统会永远骗你返回 false，导致死循环弹窗！
                isMockLocation = (opsManager.unsafeCheckOp(AppOpsManager.OPSTR_MOCK_LOCATION, android.os.Process.myUid(), getPackageName()) == AppOpsManager.MODE_ALLOWED);
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                // 安卓 6 到 9，用老方法
                isMockLocation = (opsManager.checkOp(AppOpsManager.OPSTR_MOCK_LOCATION, android.os.Process.myUid(), getPackageName()) == AppOpsManager.MODE_ALLOWED);
            } else {
                // 古董机型
                isMockLocation = !android.provider.Settings.Secure.getString(getContentResolver(), "mock_location").equals("0");
            }
        } catch (Exception e) {
            return false;
        }
        return isMockLocation;
    }
    private void initRealLocation() {
        if (mBaiduMap == null) return;
        mBaiduMap.setMyLocationEnabled(true);

        try {
            android.location.LocationManager locationManager = (android.location.LocationManager) getSystemService(Context.LOCATION_SERVICE);
            if (androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                android.location.Location cachedLoc = locationManager.getLastKnownLocation(android.location.LocationManager.NETWORK_PROVIDER);
                if (cachedLoc == null) cachedLoc = locationManager.getLastKnownLocation(android.location.LocationManager.GPS_PROVIDER);

                if (cachedLoc != null) {
                    double lat = cachedLoc.getLatitude();
                    double lon = cachedLoc.getLongitude();
                    com.baidu.mapapi.map.MyLocationData locData = new com.baidu.mapapi.map.MyLocationData.Builder().latitude(lat).longitude(lon).build();
                    mBaiduMap.setMyLocationData(locData);
                    mBaiduMap.setMapStatus(com.baidu.mapapi.map.MapStatusUpdateFactory.newLatLngZoom(new com.baidu.mapapi.model.LatLng(lat, lon), 17.0f));
                    currentLat = lat;
                    currentLon = lon;
                    isFirstLocate = false;
                    deviceRealLat = lat;
                    deviceRealLon = lon;
                }

                android.location.LocationListener listener = new android.location.LocationListener() {
                    @Override
                    public void onLocationChanged(android.location.Location location) {
                        if (location != null && mBaiduMap != null) {
                            double realLat = location.getLatitude();
                            double realLon = location.getLongitude();
                            deviceRealLat = realLat;
                            deviceRealLon = realLon;
                            com.baidu.mapapi.map.MyLocationData locData = new com.baidu.mapapi.map.MyLocationData.Builder()
                                    .latitude(realLat).longitude(realLon).direction(location.getBearing()).build();
                            mBaiduMap.setMyLocationData(locData);

                            if (isFirstLocate) {
                                isFirstLocate = false;
                                currentLat = realLat;
                                currentLon = realLon;
                                mBaiduMap.animateMapStatus(com.baidu.mapapi.map.MapStatusUpdateFactory.newLatLngZoom(new com.baidu.mapapi.model.LatLng(realLat, realLon), 17.0f));
                            }
                        }
                    }
                    @Override public void onStatusChanged(String p, int s, Bundle e) {}
                    @Override public void onProviderEnabled(String p) {}
                    @Override public void onProviderDisabled(String p) {}
                };

                locationManager.requestLocationUpdates(android.location.LocationManager.NETWORK_PROVIDER, 1000, 1, listener);
                locationManager.requestLocationUpdates(android.location.LocationManager.GPS_PROVIDER, 1000, 1, listener);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}