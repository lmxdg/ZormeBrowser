package com.zorme.browser;

import android.app.Activity;

/**
 * 首次启动的用户协议与隐私政策。
 * 文案强调：开源项目、无云服务、数据仅存本地。
 */
public final class Agreement {

    private static final String TITLE = "欢迎使用 Zorme 浏览器";

    private Agreement() {
    }

    public static String title() {
        return TITLE;
    }

    public static String fullText() {
        return "在使用本应用前，请仔细阅读并理解以下协议：\n"
                + "\n"
                + "【用户协议】\n"
                + "1. Zorme 浏览器是一个开源项目（github.com/lmxdg/ZormeBrowser），"
                + "基于 MIT 许可证发布，源代码公开可审计，您可以自由使用、修改与分发。\n"
                + "2. 本应用按\"现状\"提供，不含任何形式的担保；您需自行承担使用风险，"
                + "并遵守所在地区的法律法规，不得用于违法违规用途。\n"
                + "\n"
                + "【隐私政策】\n"
                + "1. 无账号体系：无需注册登录，不收集任何个人信息。\n"
                + "2. 无云服务：书签、历史、设置等全部数据仅保存在您的设备本地，"
                + "我们没有任何服务器，也不会上传您的浏览数据。\n"
                + "3. 不内置任何统计、追踪或广告 SDK。\n"
                + "4. 网络权限仅用于加载网页内容与检查应用更新（访问 GitHub），"
                + "更新下载加速站点由您自行选择。\n"
                + "5. 卸载应用或清除应用数据即可彻底删除本应用产生的全部数据。\n"
                + "\n"
                + "点击\"同意并继续\"即表示您已阅读并同意上述《用户协议》与《隐私政策》。";
    }

    /**
     * 首次启动弹出协议确认（自绘弹窗，不可点遮罩关闭）。
     * agree：用户同意；decline：用户拒绝或以其他方式关闭。
     */
    public static void showIfNeeded(Activity a, Runnable onAgree, Runnable onDecline) {
        if (a == null || a.isFinishing()) return;
        if (Prefs.agreed(a)) {
            if (onAgree != null) onAgree.run();
            return;
        }
        ZDialog.confirmLocked(a, TITLE, fullText(),
                "同意并继续", "不同意并退出", onAgree, onDecline);
    }
}
