package com.xl.gpstool;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

public class SettingsActivity extends AppCompatActivity {

    private SharedPreferences prefs;
    private Switch switchFloat;
    private Switch switchBattery;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        prefs = getSharedPreferences("MockSettings", MODE_PRIVATE);

        Toolbar toolbar = findViewById(R.id.toolbar_settings);
        if (toolbar != null) {
            toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material);
            toolbar.setNavigationOnClickListener(v -> finish());
        }

        SeekBar seekSpeed = findViewById(R.id.seek_speed);
        TextView tvSpeedLabel = findViewById(R.id.tv_speed_label);
        SeekBar seekWobble = findViewById(R.id.seek_wobble);
        TextView tvWobbleLabel = findViewById(R.id.tv_wobble_label);

        int savedSpeed = prefs.getInt("speed", 5);
        int savedWobble = prefs.getInt("wobble", 2);

        if (seekSpeed != null && tvSpeedLabel != null) {
            seekSpeed.setProgress(savedSpeed);
            tvSpeedLabel.setText("轨迹移动速度：" + savedSpeed + " 米/秒");

            seekSpeed.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (progress < 1) progress = 1;
                    tvSpeedLabel.setText("轨迹移动速度：" + progress + " 米/秒");
                    prefs.edit().putInt("speed", progress).apply();
                }
                @Override public void onStartTrackingTouch(SeekBar seekBar) {}
                @Override public void onStopTrackingTouch(SeekBar seekBar) {}
            });
        }

        if (seekWobble != null && tvWobbleLabel != null) {
            seekWobble.setProgress(savedWobble);
            tvWobbleLabel.setText("防检测摇摆误差：" + savedWobble + " 米");

            seekWobble.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    tvWobbleLabel.setText("防检测摇摆误差：" + progress + " 米");
                    prefs.edit().putInt("wobble", progress).apply();
                }
                @Override public void onStartTrackingTouch(SeekBar seekBar) {}
                @Override public void onStopTrackingTouch(SeekBar seekBar) {}
            });
        }

        // 🌟 动态挂载【开启悬浮窗】与【省电优化】双开关（无需修改 XML）
        if (seekWobble != null && seekWobble.getParent() instanceof LinearLayout) {
            LinearLayout container = (LinearLayout) seekWobble.getParent();
            float density = getResources().getDisplayMetrics().density;
            int pad = (int) (16 * density);

            // 1. 开启悬浮窗开关
            switchFloat = new Switch(this);
            switchFloat.setText("开启保活悬浮窗");
            switchFloat.setTextSize(15f);
            switchFloat.setPadding(0, pad, 0, pad);
            boolean floatEnabled = prefs.getBoolean("enable_float", true) && hasOverlayPermission();
            switchFloat.setChecked(floatEnabled);

            switchFloat.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) {
                    if (!hasOverlayPermission()) {
                        buttonView.setChecked(false);
                        Toast.makeText(this, "请授予【幻迹 GPS】显示悬浮窗权限", Toast.LENGTH_LONG).show();
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:" + getPackageName()));
                            startActivity(intent);
                        }
                        return;
                    }
                    prefs.edit().putBoolean("enable_float", true).apply();
                    Toast.makeText(this, "已开启保活悬浮窗", Toast.LENGTH_SHORT).show();
                } else {
                    prefs.edit().putBoolean("enable_float", false).apply();
                    Toast.makeText(this, "已关闭悬浮窗", Toast.LENGTH_SHORT).show();
                }
            });

            // 2. 省电优化（后台白名单）开关
            switchBattery = new Switch(this);
            switchBattery.setText("省电优化白名单");
            switchBattery.setTextSize(15f);
            switchBattery.setPadding(0, pad / 2, 0, pad);
            switchBattery.setChecked(isIgnoringBatteryOptimizations());

            switchBattery.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked && !isIgnoringBatteryOptimizations()) {
                    requestIgnoreBatteryOptimizations();
                } else if (!isChecked && isIgnoringBatteryOptimizations()) {
                    try {
                        startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
                    } catch (Exception ignored) {}
                }
            });

            container.addView(switchFloat, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            container.addView(switchBattery, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 从系统设置授权返回时，自动刷新开关状态
        if (switchFloat != null) {
            switchFloat.setChecked(prefs.getBoolean("enable_float", true) && hasOverlayPermission());
        }
        if (switchBattery != null) {
            switchBattery.setChecked(isIgnoringBatteryOptimizations());
        }
    }

    private boolean hasOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return Settings.canDrawOverlays(this);
        }
        return true;
    }

    private boolean isIgnoringBatteryOptimizations() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            if (pm != null) {
                return pm.isIgnoringBatteryOptimizations(getPackageName());
            }
        }
        return true;
    }

    private void requestIgnoreBatteryOptimizations() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                intent.setData(Uri.parse("package:" + getPackageName()));
                startActivity(intent);
            } catch (Exception e) {
                try {
                    startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
                } catch (Exception ignored) {}
            }
        }
    }
}