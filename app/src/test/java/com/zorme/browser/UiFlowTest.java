package com.zorme.browser;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.view.View;
import android.widget.GridLayout;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class UiFlowTest {

    @Before
    public void setUp() {
        try {
            TabManager.get().destroyAll();
        } catch (Exception ignored) {
        }
    }

    @After
    public void tearDown() {
        try {
            TabManager.get().destroyAll();
        } catch (Exception ignored) {
        }
    }

    @Test
    public void coreInteractions() {
        MainActivity a = Robolectric.buildActivity(MainActivity.class).setup().get();
        ShadowLooper.idleMainLooper();

        // 菜单
        a.findViewById(R.id.btn_menu).performClick();
        assertEquals(View.VISIBLE, a.findViewById(R.id.menu_overlay).getVisibility());
        GridLayout grid = a.findViewById(R.id.menu_grid);
        assertEquals(12, grid.getChildCount());

        // 夜间模式开关
        boolean before = Prefs.night(a);
        grid.getChildAt(5).performClick();
        assertEquals(View.GONE, a.findViewById(R.id.menu_overlay).getVisibility());
        assertEquals(!before, Prefs.night(a));

        a.findViewById(R.id.btn_menu).performClick();
        grid.getChildAt(5).performClick();
        assertEquals(before, Prefs.night(a));

        // 标签页
        a.findViewById(R.id.btn_tabs).performClick();
        assertEquals(View.VISIBLE, a.findViewById(R.id.tabs_overlay).getVisibility());
        int count = TabManager.get().size();
        a.findViewById(R.id.tabs_new).performClick();
        assertEquals(count + 1, TabManager.get().size());
        assertEquals(View.GONE, a.findViewById(R.id.tabs_overlay).getVisibility());

        // 无痕标签
        a.findViewById(R.id.btn_tabs).performClick();
        a.findViewById(R.id.tabs_new_incognito).performClick();
        assertEquals(count + 2, TabManager.get().size());
        assertTrue(TabManager.get().current().incognito);

        // 地址栏编辑
        a.findViewById(R.id.address_pill).performClick();
        assertEquals(View.VISIBLE, a.findViewById(R.id.address_overlay).getVisibility());
        a.findViewById(R.id.address_cancel).performClick();
        assertEquals(View.GONE, a.findViewById(R.id.address_overlay).getVisibility());

        // 主页快捷方式
        GridLayout shortcuts = a.findViewById(R.id.shortcut_grid);
        assertEquals(8, shortcuts.getChildCount());
        shortcuts.getChildAt(0).performClick();
        assertEquals(View.GONE, a.findViewById(R.id.home_view).getVisibility());

        // 回到主页
        a.findViewById(R.id.btn_menu).performClick();
        grid.getChildAt(1).performClick();
        assertEquals(View.VISIBLE, a.findViewById(R.id.home_view).getVisibility());
    }
}
