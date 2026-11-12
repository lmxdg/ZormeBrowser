package com.zorme.browser;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.View;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowLooper;

import java.io.File;
import java.io.FileOutputStream;

/**
 * 用 Robolectric 的原生 Skia 渲染真实截图，用于视觉回归检查。
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, qualifiers = "w360dp-h740dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class ScreenshotTest {

    private static final int W = 1080;
    private static final int H = 2220;

    @Before
    public void setUp() {
        try {
            TabManager.get().destroyAll();
        } catch (Exception ignored) {
        }
        Prefs.setTheme(org.robolectric.RuntimeEnvironment.getApplication(), Prefs.THEME_DARK);
        org.robolectric.shadows.ShadowLooper.idleMainLooper();
        new File("/tmp/shots").mkdirs();
    }

    @After
    public void tearDown() {
        try {
            TabManager.get().destroyAll();
        } catch (Exception ignored) {
        }
    }

    @Test
    public void captureScreens() throws Exception {
        MainActivity a = Robolectric.buildActivity(MainActivity.class).setup().get();
        ShadowLooper.idleMainLooper();
        layout(a);
        save(a, "/tmp/shots/01_home.png");

        a.findViewById(R.id.btn_menu).performClick();
        View overlay = a.findViewById(R.id.menu_overlay);
        overlay.setAlpha(1f);
        View card = a.findViewById(R.id.menu_card);
        card.setAlpha(1f);
        card.setTranslationY(0);
        layout(a);
        save(a, "/tmp/shots/02_menu.png");
        a.findViewById(R.id.menu_close).performClick();

        a.findViewById(R.id.btn_tabs).performClick();
        View tabs = a.findViewById(R.id.tabs_overlay);
        tabs.setAlpha(1f);
        layout(a);
        ShadowLooper.idleMainLooper();
        layout(a);
        save(a, "/tmp/shots/03_tabs.png");
        a.findViewById(R.id.tabs_new).performClick();
        layout(a);

        DbHelper.get(a).addHistory("Android Developers", "https://developer.android.com/");
        DbHelper.get(a).addHistory("GitHub · Where software is built", "https://github.com/");
        DbHelper.get(a).addHistory("知乎 - 有问题，就会有答案", "https://www.zhihu.com/");
        DbHelper.get(a).addBookmark("Zorme 项目主页", "https://github.com/zorme");
        a.findViewById(R.id.address_pill).performClick();
        a.findViewById(R.id.address_overlay).setAlpha(1f);
        layout(a);
        ShadowLooper.idleMainLooper();
        layout(a);
        save(a, "/tmp/shots/04_address.png");
        a.findViewById(R.id.address_cancel).performClick();

        SettingsActivity s = Robolectric.buildActivity(SettingsActivity.class).setup().get();
        ShadowLooper.idleMainLooper();
        layoutActivity(s);
        saveActivity(s, "/tmp/shots/05_settings.png");

        ListActivity l = Robolectric.buildActivity(ListActivity.class,
                new android.content.Intent(org.robolectric.RuntimeEnvironment.getApplication(), ListActivity.class)
                        .putExtra("mode", ListActivity.MODE_HISTORY)).setup().get();
        ShadowLooper.idleMainLooper();
        layoutActivity(l);
        saveActivity(l, "/tmp/shots/06_history.png");
    }

    private void layout(MainActivity a) {
        View decor = a.getWindow().getDecorView();
        decor.measure(View.MeasureSpec.makeMeasureSpec(W, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(H, View.MeasureSpec.EXACTLY));
        decor.layout(0, 0, W, H);
    }

    private void layoutActivity(android.app.Activity a) {
        View decor = a.getWindow().getDecorView();
        decor.measure(View.MeasureSpec.makeMeasureSpec(W, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(H, View.MeasureSpec.EXACTLY));
        decor.layout(0, 0, W, H);
    }

    private void save(MainActivity a, String path) throws Exception {
        saveView(a.getWindow().getDecorView(), path);
    }

    private void saveActivity(android.app.Activity a, String path) throws Exception {
        saveView(a.getWindow().getDecorView(), path);
    }

    private void saveView(View decor, String path) throws Exception {
        Bitmap bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        decor.draw(canvas);
        FileOutputStream out = new FileOutputStream(path);
        bmp.compress(Bitmap.CompressFormat.PNG, 100, out);
        out.close();
        bmp.recycle();
    }
}
