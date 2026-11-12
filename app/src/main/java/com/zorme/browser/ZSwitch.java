package com.zorme.browser;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

/**
 * 自绘开关：不依赖系统 Switch 样式，深浅主题下都有明确的开/关对比。
 */
public class ZSwitch extends View {

    public interface OnCheckedChangeListener {
        void onCheckedChanged(ZSwitch view, boolean checked);
    }

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private boolean checked;
    private float progress;
    private OnCheckedChangeListener listener;
    private ValueAnimator animator;

    public ZSwitch(Context c) {
        this(c, null);
    }

    public ZSwitch(Context c, AttributeSet attrs) {
        super(c, attrs);
        setClickable(true);
        setFocusable(true);
    }

    private int dp(float v) {
        return Utils.dp(getContext(), v);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(resolveSize(dp(46), widthMeasureSpec),
                resolveSize(dp(28), heightMeasureSpec));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth();
        float h = getHeight();
        float radius = h / 2f;
        int trackOff = getContext().getColor(R.color.pill);
        int trackOn = getContext().getColor(R.color.accent);
        paint.setColor(blend(trackOff, trackOn, progress));
        canvas.drawRoundRect(0, 0, w, h, radius, radius, paint);

        float pad = dp(3);
        float thumbRadius = radius - pad;
        float travel = w - 2 * radius;
        float cx = radius + travel * progress;
        int thumbOff = getContext().getColor(R.color.text_tertiary);
        int thumbOn = getContext().getColor(R.color.bg);
        paint.setColor(blend(thumbOff, thumbOn, progress));
        canvas.drawCircle(cx, radius, thumbRadius, paint);
    }

    private static int blend(int from, int to, float t) {
        float f = Math.max(0f, Math.min(1f, t));
        return Color.rgb(
                (int) (Color.red(from) + (Color.red(to) - Color.red(from)) * f),
                (int) (Color.green(from) + (Color.green(to) - Color.green(from)) * f),
                (int) (Color.blue(from) + (Color.blue(to) - Color.blue(from)) * f));
    }

    @Override
    public boolean performClick() {
        toggle();
        return true;
    }

    public boolean isChecked() {
        return checked;
    }

    public void toggle() {
        setChecked(!checked);
    }

    public void setChecked(boolean value) {
        if (checked == value) return;
        checked = value;
        if (animator != null) animator.cancel();
        if (isAttachedToWindow()) {
            animator = ValueAnimator.ofFloat(progress, value ? 1f : 0f);
            animator.setDuration(180);
            animator.setInterpolator(new DecelerateInterpolator(1.4f));
            animator.addUpdateListener(a -> {
                progress = (float) a.getAnimatedValue();
                invalidate();
            });
            animator.start();
        } else {
            progress = value ? 1f : 0f;
            invalidate();
        }
        if (listener != null) listener.onCheckedChanged(this, value);
    }

    /** 初始化时使用，不触发回调与动画 */
    public void setCheckedSilently(boolean value) {
        checked = value;
        progress = value ? 1f : 0f;
        invalidate();
    }

    public void setOnCheckedChangeListener(OnCheckedChangeListener l) {
        listener = l;
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (animator != null) {
            animator.cancel();
            animator = null;
        }
    }
}
