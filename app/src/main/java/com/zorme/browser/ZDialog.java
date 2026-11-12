package com.zorme.browser;

import android.app.Activity;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;

import java.util.ArrayList;
import java.util.List;

/**
 * 内置弹窗：完全自绘的底部卡片，不使用系统 AlertDialog / Dialog。
 * 统一 Zorme 的圆角、配色与动效，并支持返回键与遮罩关闭。
 */
public final class ZDialog {

    /** 当前存在的弹窗，保证同一时刻只显示最上面一个 */
    private static final List<ZDialog> OPEN = new ArrayList<>();

    private final Activity activity;
    private final ViewGroup host;
    private final View root;
    private final LinearLayout card;
    private final TextView titleView, messageView, positiveView, negativeView;
    private final EditText inputView;
    private final ScrollView scrollView;
    private final LinearLayout optionsView;
    private final LinearLayout buttonsRow;

    private OnBackPressedCallback backCallback;
    private Runnable onDismiss;
    private Runnable positiveAction;
    private Runnable negativeAction;
    private OnInput inputListener;
    private boolean positiveClicked;
    private boolean dismissed;
    private boolean cancelOnOutside = true;

    private ZDialog(Activity a) {
        activity = a;
        host = a.findViewById(android.R.id.content);
        root = LayoutInflater.from(a).inflate(R.layout.dialog_sheet, host, false);
        card = root.findViewById(R.id.dialog_card);
        titleView = root.findViewById(R.id.dialog_title);
        messageView = root.findViewById(R.id.dialog_message);
        positiveView = root.findViewById(R.id.dialog_positive);
        negativeView = root.findViewById(R.id.dialog_negative);
        inputView = root.findViewById(R.id.dialog_input);
        scrollView = root.findViewById(R.id.dialog_scroll);
        optionsView = root.findViewById(R.id.dialog_options);
        buttonsRow = root.findViewById(R.id.dialog_buttons);
    }

    private static ZDialog make(Activity a) {
        ZDialog d = new ZDialog(a);
        d.root.setOnClickListener(v -> {
            if (d.cancelOnOutside) d.dismiss();
        });
        return d;
    }

    // ------------------------------------------------------------------
    // 对外 API
    // ------------------------------------------------------------------

    /** 单按钮提示 */
    public static void alert(Activity a, String title, CharSequence message) {
        alert(a, title, message, "知道了", null);
    }

    public static void alert(Activity a, String title, CharSequence message, String okText, Runnable after) {
        if (a == null || a.isFinishing()) return;
        ZDialog d = make(a);
        d.titleView.setText(title);
        d.setMessage(message);
        d.positiveView.setText(okText);
        d.positiveView.setOnClickListener(v -> {
            d.positiveClicked = true;
            d.dismiss();
            if (after != null) after.run();
        });
        d.negativeView.setVisibility(View.GONE);
        d.buttonsRow.setVisibility(View.VISIBLE);
        d.show();
    }

    /** 双按钮确认 */
    public static void confirm(Activity a, String title, CharSequence message,
                               String okText, Runnable onOk) {
        confirm(a, title, message, okText, "取消", onOk, null, false);
    }

    public static void confirm(Activity a, String title, CharSequence message,
                               String okText, Runnable onOk, Runnable onCancel) {
        confirm(a, title, message, okText, "取消", onOk, onCancel, false);
    }

    /** 危险操作确认（确定按钮为红色文字） */
    public static void confirmDanger(Activity a, String title, CharSequence message,
                                     String okText, Runnable onOk) {
        confirm(a, title, message, okText, "取消", onOk, null, true);
    }

    public static void confirm(Activity a, String title, CharSequence message, String okText,
                               String cancelText, Runnable onOk, Runnable onCancel, boolean danger) {
        if (a == null || a.isFinishing()) return;
        ZDialog d = make(a);
        d.titleView.setText(title);
        d.setMessage(message);
        d.positiveView.setText(okText);
        d.negativeView.setText(cancelText);
        if (danger) {
            d.positiveView.setTextColor(a.getColor(R.color.danger));
            d.positiveView.setBackgroundResource(R.drawable.bg_dialog_btn);
        }
        d.buttonsRow.setVisibility(View.VISIBLE);
        d.positiveView.setOnClickListener(v -> {
            d.positiveClicked = true;
            d.dismiss();
            if (onOk != null) onOk.run();
        });
        d.negativeView.setOnClickListener(v -> d.dismiss());
        d.onDismiss = () -> {
            if (!d.positiveClicked && onCancel != null) onCancel.run();
        };
        d.show();
    }

    /** 输入框弹窗 */
    public interface OnInput {
        void onInput(String text);
    }

    public static void input(Activity a, String title, String hint, String initial,
                             String okText, OnInput listener) {
        input(a, title, hint, initial, okText, listener, null);
    }

    public static void input(Activity a, String title, String hint, String initial,
                             String okText, OnInput listener, Runnable onCancel) {
        if (a == null || a.isFinishing()) return;
        ZDialog d = make(a);
        d.titleView.setText(title);
        d.inputView.setVisibility(View.VISIBLE);
        d.inputView.setHint(hint == null ? "" : hint);
        d.inputView.setText(initial == null ? "" : initial);
        d.inputView.setSelection(d.inputView.getText().length());
        d.inputListener = listener;
        d.positiveView.setText(okText);
        d.negativeView.setText("取消");
        d.buttonsRow.setVisibility(View.VISIBLE);
        d.positiveView.setOnClickListener(v -> {
            String text = d.inputView.getText().toString();
            d.positiveClicked = true;
            d.dismiss();
            if (d.inputListener != null) d.inputListener.onInput(text);
        });
        d.negativeView.setOnClickListener(v -> d.dismiss());
        d.onDismiss = () -> {
            if (!d.positiveClicked && onCancel != null) onCancel.run();
        };
        d.show();
        d.inputView.postDelayed(() -> {
            d.inputView.requestFocus();
            InputMethodManager imm = (InputMethodManager)
                    a.getSystemService(Activity.INPUT_METHOD_SERVICE);
            if (imm != null) imm.showSoftInput(d.inputView, InputMethodManager.SHOW_IMPLICIT);
        }, 180);
    }

    /** 单选列表（带对勾） */
    public interface OnChoice {
        void onChoice(int index);
    }

    public static void choice(Activity a, String title, String[] items, int checked, OnChoice listener) {
        if (a == null || a.isFinishing() || items == null || items.length == 0) return;
        ZDialog d = make(a);
        d.titleView.setText(title);
        d.negativeView.setVisibility(View.GONE);
        d.buttonsRow.setVisibility(View.GONE);
        d.buildOptions(items, checked, listener);
        d.show();
    }

    /** 普通操作列表 */
    public static void options(Activity a, String title, String[] items, OnChoice listener) {
        options(a, title, items, -1, listener);
    }

    public static void options(Activity a, String title, String[] items, int dangerIndex, OnChoice listener) {
        if (a == null || a.isFinishing() || items == null || items.length == 0) return;
        ZDialog d = make(a);
        d.titleView.setText(title);
        d.buttonsRow.setVisibility(View.GONE);
        d.buildOptions(items, -1, listener);
        if (dangerIndex >= 0 && dangerIndex < d.optionsView.getChildCount()) {
            ((TextView) d.optionsView.getChildAt(dangerIndex).findViewById(R.id.option_text))
                    .setTextColor(a.getColor(R.color.danger));
        }
        d.show();
    }

    private void buildOptions(String[] items, int checked, final OnChoice listener) {
        scrollView.setVisibility(View.VISIBLE);
        LayoutInflater inflater = LayoutInflater.from(activity);
        for (int i = 0; i < items.length; i++) {
            View item = inflater.inflate(R.layout.item_dialog_option, optionsView, false);
            TextView text = item.findViewById(R.id.option_text);
            ImageView check = item.findViewById(R.id.option_check);
            text.setText(items[i]);
            if (i == checked) check.setVisibility(View.VISIBLE);
            final int index = i;
            item.setOnClickListener(v -> {
                positiveClicked = true;
                dismiss();
                if (listener != null) listener.onChoice(index);
            });
            optionsView.addView(item);
        }
        int max = (int) (activity.getResources().getDisplayMetrics().heightPixels * 0.52f);
        scrollView.post(() -> {
            if (scrollView.getHeight() > max) {
                ViewGroup.LayoutParams lp = scrollView.getLayoutParams();
                lp.height = max;
                scrollView.setLayoutParams(lp);
            }
        });
    }

    // ------------------------------------------------------------------
    // 显示与关闭
    // ------------------------------------------------------------------

    private void setMessage(CharSequence message) {
        if (message == null || message.length() == 0) {
            messageView.setVisibility(View.GONE);
        } else {
            messageView.setVisibility(View.VISIBLE);
            messageView.setText(message);
            messageView.setMaxLines(18);
            messageView.setEllipsize(TextUtils.TruncateAt.END);
        }
    }

    private void show() {
        if (host == null) return;
        // 同一界面只保留最新一个弹窗
        for (int i = OPEN.size() - 1; i >= 0; i--) {
            ZDialog d = OPEN.get(i);
            if (d.activity == activity) d.dismissNow();
        }

        card.setTranslationY(activity.getResources().getDisplayMetrics().heightPixels);
        host.addView(root, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        OPEN.add(this);
        root.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View v) {
            }

            @Override
            public void onViewDetachedFromWindow(View v) {
                dismissed = true;
                if (backCallback != null) {
                    backCallback.remove();
                    backCallback = null;
                }
                OPEN.remove(ZDialog.this);
            }
        });

        root.setAlpha(0f);
        root.animate().alpha(1f).setDuration(170).start();
        card.animate().translationY(0).setDuration(260)
                .setInterpolator(new android.view.animation.DecelerateInterpolator(1.5f))
                .start();

        backCallback = new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                dismiss();
            }
        };
        if (activity instanceof androidx.activity.ComponentActivity) {
            androidx.activity.ComponentActivity ca = (androidx.activity.ComponentActivity) activity;
            ca.getOnBackPressedDispatcher().addCallback(ca, backCallback);
        }
    }

    /** 供 Activity 的 onBackPressed 调用，返回是否消费了返回键 */
    public static boolean handleBack() {
        for (int i = OPEN.size() - 1; i >= 0; i--) {
            ZDialog d = OPEN.get(i);
            if (!d.dismissed) {
                d.dismiss();
                return true;
            }
        }
        return false;
    }

    public static boolean isShowing() {
        return !OPEN.isEmpty();
    }

    public void dismiss() {
        if (dismissed) return;
        dismissed = true;
        if (backCallback != null) {
            backCallback.remove();
            backCallback = null;
        }
        if (onDismiss != null) {
            Runnable r = onDismiss;
            onDismiss = null;
            r.run();
        }
        OPEN.remove(this);

        if (!activity.isFinishing() && root.getParent() != null) {
            card.animate().translationY(card.getHeight() + Utils.dp(activity, 24))
                    .setDuration(170).start();
            root.animate().alpha(0f).setDuration(170).withEndAction(this::removeNow).start();
        } else {
            removeNow();
        }
    }

    private void dismissNow() {
        if (dismissed) return;
        dismissed = true;
        if (backCallback != null) {
            backCallback.remove();
            backCallback = null;
        }
        OPEN.remove(this);
        removeNow();
    }

    private void removeNow() {
        try {
            if (root.getParent() instanceof ViewGroup) {
                ((ViewGroup) root.getParent()).removeView(root);
            }
        } catch (Exception ignored) {
        }
    }

    // ------------------------------------------------------------------
    // 内置浮层提示（替代系统 Toast）
    // ------------------------------------------------------------------

    public static void toast(Activity a, CharSequence msg) {
        if (a == null || a.isFinishing() || msg == null || msg.length() == 0) return;
        final ViewGroup host = a.findViewById(android.R.id.content);
        if (host == null) return;
        View old = host.findViewWithTag("zorme_toast");
        if (old != null) host.removeView(old);

        TextView tv = new TextView(a);
        tv.setTag("zorme_toast");
        tv.setText(msg);
        tv.setTextSize(13);
        tv.setTextColor(0xFFFFFFFF);
        tv.setGravity(Gravity.CENTER);
        tv.setMaxLines(3);
        tv.setEllipsize(TextUtils.TruncateAt.END);
        int ph = Utils.dp(a, 20);
        int pv = Utils.dp(a, 11);
        tv.setPadding(ph, pv, ph, pv);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xF21D1D24);
        bg.setCornerRadius(Utils.dp(a, 22));
        tv.setBackground(bg);
        tv.setElevation(Utils.dp(a, 8));
        tv.setAlpha(0f);

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        lp.bottomMargin = Utils.dp(a, 96);
        lp.leftMargin = Utils.dp(a, 32);
        lp.rightMargin = Utils.dp(a, 32);
        try {
            host.addView(tv, lp);
        } catch (Exception e) {
            return;
        }
        tv.animate().alpha(1f).setDuration(150).start();
        tv.postDelayed(() -> {
            if (tv.getParent() == null) return;
            tv.animate().alpha(0f).setDuration(220).withEndAction(() -> {
                if (tv.getParent() instanceof ViewGroup) {
                    ((ViewGroup) tv.getParent()).removeView(tv);
                }
            }).start();
        }, 1800);
    }
}
