package com.zorme.browser;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.webkit.WebView;

/**
 * 支持左右边缘滑动导航的 WebView。
 */
public class ZWebView extends WebView {

    public interface EdgeSwipeListener {
        void onEdgeSwipe(boolean forward);
    }

    private EdgeSwipeListener listener;
    private float downX, downY;
    private boolean edgeStart;
    private final int edgeSize;
    private final int threshold;

    public ZWebView(Context context) {
        this(context, null);
    }

    public ZWebView(Context context, AttributeSet attrs) {
        super(context, attrs);
        edgeSize = Utils.dp(context, 26);
        threshold = Utils.dp(context, 70);
    }

    public void setEdgeSwipeListener(EdgeSwipeListener l) {
        this.listener = l;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (listener != null) {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downX = event.getX();
                    downY = event.getY();
                    edgeStart = downX < edgeSize || downX > getWidth() - edgeSize;
                    break;
                case MotionEvent.ACTION_UP:
                    if (edgeStart) {
                        float dx = event.getX() - downX;
                        float dy = Math.abs(event.getY() - downY);
                        if (Math.abs(dx) > threshold && dy < Utils.dp(getContext(), 70)) {
                            listener.onEdgeSwipe(dx < 0);
                        }
                    }
                    edgeStart = false;
                    break;
                case MotionEvent.ACTION_CANCEL:
                    edgeStart = false;
                    break;
                default:
                    break;
            }
        }
        return super.onTouchEvent(event);
    }
}
