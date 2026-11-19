package com.zorme.browser;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * v1.1.5 菜单改为 ViewPager2 分页后，菜单项不再是直接加入布局的子 View。
 * 测试通过反射读取 MainActivity.menuTiles 数据来断言菜单内容与触发动作。
 */
final class MenuTestHelper {

    private MenuTestHelper() {
    }

    /** 当前菜单全部标签（按显示顺序） */
    static List<String> labels(MainActivity a) {
        try {
            Field f = MainActivity.class.getDeclaredField("menuTiles");
            f.setAccessible(true);
            List<?> tiles = (List<?>) f.get(a);
            Field lf = null;
            List<String> out = new ArrayList<>();
            for (Object t : tiles) {
                if (lf == null) {
                    lf = t.getClass().getDeclaredField("label");
                    lf.setAccessible(true);
                }
                out.add((String) lf.get(t));
            }
            return out;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** 按标签找到菜单项动作并触发；找不到返回 false */
    static boolean tap(MainActivity a, String label) {
        try {
            Field f = MainActivity.class.getDeclaredField("menuTiles");
            f.setAccessible(true);
            List<?> tiles = (List<?>) f.get(a);
            Field lf = null, af = null;
            for (Object t : tiles) {
                if (lf == null) {
                    lf = t.getClass().getDeclaredField("label");
                    lf.setAccessible(true);
                    af = t.getClass().getDeclaredField("action");
                    af.setAccessible(true);
                }
                if (label.equals(lf.get(t))) {
                    ((Runnable) af.get(t)).run();
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
