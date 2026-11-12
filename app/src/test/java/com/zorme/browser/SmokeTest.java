package com.zorme.browser;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.view.View;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * 冒烟测试：验证主界面、列表页、设置页能真实 inflate 并启动。
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class SmokeTest {

    @Test
    public void mainActivityStarts() {
        MainActivity activity = Robolectric.buildActivity(MainActivity.class).setup().get();
        assertNotNull(activity);
        View bottomBar = activity.findViewById(R.id.bottom_bar);
        assertNotNull(bottomBar);
        View address = activity.findViewById(R.id.address_pill);
        assertNotNull(address);
        View home = activity.findViewById(R.id.home_view);
        assertNotNull(home);
        assertTrue(TabManager.get().size() >= 1);
    }

    @Test
    public void settingsActivityStarts() {
        SettingsActivity activity = Robolectric.buildActivity(SettingsActivity.class).setup().get();
        assertNotNull(activity.findViewById(R.id.settings_container));
    }

    @Test
    public void listActivityStarts() {
        android.content.Intent i = new android.content.Intent(
                org.robolectric.RuntimeEnvironment.getApplication(), ListActivity.class);
        i.putExtra("mode", ListActivity.MODE_HISTORY);
        ListActivity activity = Robolectric.buildActivity(ListActivity.class, i).setup().get();
        assertNotNull(activity.findViewById(R.id.list_recycler));
    }
}
