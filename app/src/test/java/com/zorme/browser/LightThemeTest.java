package com.zorme.browser;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.View;

import androidx.appcompat.app.AppCompatDelegate;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowLooper;

import java.io.FileOutputStream;

/** 浅色主题下的渲染检查 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, qualifiers = "w360dp-h740dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class LightThemeTest {

    private static final int W = 1080;
    private static final int H = 2220;

    @Test
    public void captureLightHome() throws Exception {
        Context c = RuntimeEnvironment.getApplication();
        Prefs.setTheme(c, Prefs.THEME_LIGHT);
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        TabManager.get().destroyAll();

        MainActivity a = Robolectric.buildActivity(MainActivity.class).setup().get();
        ShadowLooper.idleMainLooper();
        View decor = a.getWindow().getDecorView();
        decor.measure(View.MeasureSpec.makeMeasureSpec(W, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(H, View.MeasureSpec.EXACTLY));
        decor.layout(0, 0, W, H);

        Bitmap bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
        decor.draw(new Canvas(bmp));
        FileOutputStream out = new FileOutputStream("/tmp/shots/07_home_light.png");
        bmp.compress(Bitmap.CompressFormat.PNG, 100, out);
        out.close();
        bmp.recycle();

        a.findViewById(R.id.btn_menu).performClick();
        a.findViewById(R.id.menu_overlay).setAlpha(1f);
        View card = a.findViewById(R.id.menu_card);
        card.setAlpha(1f);
        card.setTranslationY(0);
        decor.measure(View.MeasureSpec.makeMeasureSpec(W, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(H, View.MeasureSpec.EXACTLY));
        decor.layout(0, 0, W, H);
        Bitmap bmp2 = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
        decor.draw(new Canvas(bmp2));
        FileOutputStream out2 = new FileOutputStream("/tmp/shots/08_menu_light.png");
        bmp2.compress(Bitmap.CompressFormat.PNG, 100, out2);
        out2.close();
        bmp2.recycle();

        // 浅色主题下的内置弹窗
        a.findViewById(R.id.menu_close).performClick();
        ZDialog.confirmDanger(a, "清除浏览数据",
                "将清除缓存、Cookie、本地存储和历史记录，书签会保留。", "清除", () -> {
                });
        ShadowLooper.idleMainLooper();
        View dRoot = a.findViewById(R.id.dialog_root);
        if (dRoot != null) {
            dRoot.setAlpha(1f);
            View dCard = a.findViewById(R.id.dialog_card);
            if (dCard != null) dCard.setTranslationY(0);
            dRoot.measure(View.MeasureSpec.makeMeasureSpec(W, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(H, View.MeasureSpec.EXACTLY));
            dRoot.layout(0, 0, W, H);
        }
        decor.measure(View.MeasureSpec.makeMeasureSpec(W, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(H, View.MeasureSpec.EXACTLY));
        decor.layout(0, 0, W, H);
        Bitmap bmp3 = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
        decor.draw(new Canvas(bmp3));
        FileOutputStream out3 = new FileOutputStream("/tmp/shots/13_dialog_light.png");
        bmp3.compress(Bitmap.CompressFormat.PNG, 100, out3);
        out3.close();
        bmp3.recycle();
        ZDialog.handleBack();

        SettingsActivity s = Robolectric.buildActivity(SettingsActivity.class).setup().get();
        ShadowLooper.idleMainLooper();
        View sdecor = s.getWindow().getDecorView();
        sdecor.measure(View.MeasureSpec.makeMeasureSpec(W, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(H, View.MeasureSpec.EXACTLY));
        sdecor.layout(0, 0, W, H);
        Bitmap bmp4 = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
        sdecor.draw(new Canvas(bmp4));
        FileOutputStream out4 = new FileOutputStream("/tmp/shots/14_settings_light.png");
        bmp4.compress(Bitmap.CompressFormat.PNG, 100, out4);
        out4.close();
        bmp4.recycle();

        TabManager.get().destroyAll();
    }
}
