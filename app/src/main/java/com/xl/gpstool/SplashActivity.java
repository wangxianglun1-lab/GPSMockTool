package com.xl.gpstool;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import androidx.appcompat.app.AppCompatActivity;

public class SplashActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 隐藏顶部的系统标题栏，实现沉浸式全屏
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }
        setContentView(R.layout.activity_splash);

        // 设定定时器：在这里装逼 2.5 秒，然后自动跳转到 MainActivity
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            Intent intent = new Intent(SplashActivity.this, MainActivity.class);
            startActivity(intent);
            // 跳转后销毁开屏页，防止用户按返回键又回到开屏页
            finish();
        }, 2500); // 2500 毫秒 = 2.5 秒
    }
}