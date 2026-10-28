package com.xl.gpstool;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;
import java.util.Random;

public class CodeRainView extends View {
    private Paint textPaint, bgPaint;
    private int width, height;
    private int textSize = 45; // 字体大小
    private int[] drops;
    private Random random = new Random();
    // 漫天飘落的字符库，你可以随便加你想加的字
    private char[] chars = "010101010101011010101ABCDEF幻迹 GPS".toCharArray();

    public CodeRainView(Context context, AttributeSet attrs) {
        super(context, attrs);
        // 绿色代码画笔
        textPaint = new Paint();
        textPaint.setColor(Color.parseColor("#00FF00")); // 纯正黑客绿
        textPaint.setTextSize(textSize);
        textPaint.setAntiAlias(true);

        // 黑色拖影画笔 (利用半透明制造尾迹效果)
        bgPaint = new Paint();
        bgPaint.setColor(Color.BLACK);
        bgPaint.setAlpha(20);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        width = w;
        height = h;
        // 计算屏幕能排下多少列
        int columns = width / textSize;
        drops = new int[columns];
        for (int i = 0; i < columns; i++) {
            drops[i] = random.nextInt(height / textSize); // 随机下落的初始高度
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        // 画一层半透明的黑色遮罩，覆盖上一帧，形成残影
        canvas.drawRect(0, 0, width, height, bgPaint);

        for (int i = 0; i < drops.length; i++) {
            // 随机抓取一个字符
            char c = chars[random.nextInt(chars.length)];
            // 绘制字符
            canvas.drawText(String.valueOf(c), i * textSize, drops[i] * textSize, textPaint);

            // 如果代码滴落到底部，并且满足一个随机概率，就让它回到顶部重新掉落
            if (drops[i] * textSize > height && random.nextFloat() > 0.95f) {
                drops[i] = 0;
            }
            drops[i]++; // 继续往下落
        }
        // 每隔 40 毫秒刷新一次 (大约 25 FPS 的帧率，刚刚好)
        postInvalidateDelayed(40);
    }
}