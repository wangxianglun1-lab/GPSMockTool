package com.xl.gpstool;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.SeekBar;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

public class SettingsActivity extends AppCompatActivity {

    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        // 初始化 GPS 配置存档
        prefs = getSharedPreferences("MockSettings", MODE_PRIVATE);

        // 绑定 Toolbar 并设置返回按钮
        Toolbar toolbar = findViewById(R.id.toolbar_settings);
        if (toolbar != null) {
            toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material);
            toolbar.setNavigationOnClickListener(v -> finish()); // 点击返回上一页
        }

        // 绑定 GPS 摇摆和速度控件
        SeekBar seekSpeed = findViewById(R.id.seek_speed);
        TextView tvSpeedLabel = findViewById(R.id.tv_speed_label);
        SeekBar seekWobble = findViewById(R.id.seek_wobble);
        TextView tvWobbleLabel = findViewById(R.id.tv_wobble_label);

        // 读取历史 GPS 数据并显示
        int savedSpeed = prefs.getInt("speed", 5);
        int savedWobble = prefs.getInt("wobble", 2);

        if (seekSpeed != null && tvSpeedLabel != null) {
            seekSpeed.setProgress(savedSpeed);
            tvSpeedLabel.setText("轨迹移动速度：" + savedSpeed + " 米/秒");

            // 监听速度条拖动
            seekSpeed.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (progress < 1) progress = 1; // 速度最少得有 1，不能原地踏步
                    tvSpeedLabel.setText("轨迹移动速度：" + progress + " 米/秒");
                    prefs.edit().putInt("speed", progress).apply(); // 实时保存
                }
                @Override public void onStartTrackingTouch(SeekBar seekBar) {}
                @Override public void onStopTrackingTouch(SeekBar seekBar) {}
            });
        }

        if (seekWobble != null && tvWobbleLabel != null) {
            seekWobble.setProgress(savedWobble);
            tvWobbleLabel.setText("防检测摇摆误差：" + savedWobble + " 米");

            // 监听摇摆条拖动
            seekWobble.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    tvWobbleLabel.setText("防检测摇摆误差：" + progress + " 米");
                    prefs.edit().putInt("wobble", progress).apply(); // 实时保存
                }
                @Override public void onStartTrackingTouch(SeekBar seekBar) {}
                @Override public void onStopTrackingTouch(SeekBar seekBar) {}
            });
        }
    }
}