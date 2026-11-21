package app.xiaobuswipe;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.WindowInsets;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * 独立的「关于」页面。
 * 单独成 Activity 是为了让系统手势返回（含 Android 13+ 的预测性返回动画）
 * 走系统默认路径，而不是在设置页里做视图切换。
 */
public final class AboutActivity extends Activity {
    private static final int COLOR_PAGE = Color.rgb(245, 247, 250);
    private static final int COLOR_CARD = Color.WHITE;
    private static final int COLOR_TEXT = Color.rgb(32, 36, 43);
    private static final int COLOR_SECONDARY = Color.rgb(101, 109, 122);
    private static final int COLOR_DIVIDER = Color.rgb(232, 235, 240);
    private static final int COLOR_ACCENT = Color.rgb(36, 103, 214);

    private static final String SOURCE_URL =
            "https://github.com/waoui/WeType-Swipe/issues";
    private static final String COOLAPK_USER_URL = "https://www.coolapk.com/u/28792620";
    private static final String COOLAPK_USER_LINK = "coolmarket://u/28792620";
    private static final String COOLAPK_FEED_URL =
            "https://www.coolapk.com/feed/74524760?s=NmU5YjFiOWMxYjc1NzJjZzZhZjg0OTc5ega1670";
    private static final String COOLAPK_FEED_LINK = "coolmarket://feed/74524760";

    static Intent intent(Activity from) {
        return new Intent(from, AboutActivity.class);
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        setTitle("关于");
        configureWindow();
        setContentView(buildPage());
    }

    private void configureWindow() {
        getWindow().setStatusBarColor(Color.WHITE);
        getWindow().setNavigationBarColor(Color.WHITE);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
    }

    private View buildPage() {
        LinearLayout page = vertical();
        page.setBackgroundColor(COLOR_PAGE);
        applySystemBarInsets(page);

        page.addView(buildTopBar(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);

        LinearLayout content = vertical();
        content.setPadding(dp(12), dp(12), dp(12), dp(22));
        scroll.addView(content);

        content.addView(buildIntroCard());
        content.addView(buildOriginCard());
        // 赞赏支持放在联系与反馈之前（按主人要求调换）
        content.addView(buildDonateCard());
        content.addView(buildContactCard());

        TextView footer = text(versionLabel() + " · 感谢使用", 12, COLOR_SECONDARY);
        footer.setGravity(Gravity.CENTER);
        footer.setPadding(0, dp(2), 0, dp(18));
        content.addView(footer);

        page.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        scroll.post(() -> scroll.scrollTo(0, 0));
        return page;
    }

    private View buildTopBar() {
        LinearLayout bar = horizontal();
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(8), dp(6), dp(16), dp(6));
        bar.setBackgroundColor(Color.WHITE);
        bar.setElevation(dp(1));

        TextView back = text("‹", 30, COLOR_TEXT);
        back.setGravity(Gravity.CENTER);
        back.setMinWidth(dp(44));
        back.setMinHeight(dp(44));
        back.setClickable(true);
        back.setFocusable(true);
        back.setContentDescription("返回");
        back.setOnClickListener(v -> finish());
        bar.addView(back);

        TextView title = text("关于", 19, COLOR_TEXT);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams titleParams = wrap();
        titleParams.leftMargin = dp(4);
        bar.addView(title, titleParams);
        return bar;
    }

    private View buildIntroCard() {
        LinearLayout card = createCard("关于本模块",
                "小布输入法下滑快捷键 · LSPosed 模块");
        TextView declaration = text("模块完全免费，禁止倒卖。", 15, COLOR_TEXT);
        declaration.setTypeface(Typeface.DEFAULT_BOLD);
        declaration.setPadding(dp(16), dp(2), dp(16), dp(6));
        card.addView(declaration);

        TextView note = text("如果你是通过付费渠道获取的，说明遇到了倒卖，请直接使用本免费版本。",
                12, COLOR_SECONDARY);
        note.setPadding(dp(16), 0, dp(16), dp(16));
        card.addView(note);
        return card;
    }

    private View buildOriginCard() {
        LinearLayout card = createCard("开源声明", null);
        TextView note = text("本模块改自下面这个开源项目，感谢原作者的探索与分享。",
                13, COLOR_SECONDARY);
        note.setPadding(dp(16), 0, dp(16), dp(6));
        card.addView(note);
        card.addView(linkRow("WeType-Swipe", "github.com/waoui/WeType-Swipe",
                () -> openUrl(SOURCE_URL)));
        return card;
    }

    private View buildDonateCard() {
        LinearLayout card = createCard("赞赏支持", null);
        ImageView code = new ImageView(this);
        code.setImageResource(R.drawable.donate_code);
        code.setScaleType(ImageView.ScaleType.FIT_CENTER);
        code.setContentDescription("赞赏码");
        code.setClipToOutline(true);
        code.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), dp(12));
            }
        });
        LinearLayout.LayoutParams codeParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(190));
        codeParams.setMargins(dp(16), dp(2), dp(16), dp(10));
        card.addView(code, codeParams);

        TextView thanks = text("您的支持将是维护模块的最大动力₍ᐢ..ᐢ₎♡谢谢！！", 14, COLOR_TEXT);
        thanks.setTypeface(Typeface.DEFAULT_BOLD);
        thanks.setGravity(Gravity.CENTER);
        thanks.setPadding(dp(16), dp(2), dp(16), dp(6));
        card.addView(thanks);

        TextView saveTip = text("长按图片可保存到相册，再用支付宝扫一扫。", 12, COLOR_SECONDARY);
        saveTip.setGravity(Gravity.CENTER);
        saveTip.setPadding(dp(16), 0, dp(16), dp(16));
        card.addView(saveTip);
        return card;
    }

    private View buildContactCard() {
        LinearLayout card = createCard("联系与反馈", "点击条目直接跳转到酷安");
        card.addView(linkRow("我的酷安主页", "coolapk.com/u/28792620",
                () -> openCoolapk(COOLAPK_USER_LINK, COOLAPK_USER_URL)));
        card.addView(divider());
        card.addView(linkRow("模块发布动态", "coolapk.com/feed/74524760",
                () -> openCoolapk(COOLAPK_FEED_LINK, COOLAPK_FEED_URL)));
        return card;
    }

    private View linkRow(String title, String subtitle, Runnable action) {
        LinearLayout row = horizontal();
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(13), dp(14), dp(13));
        int ripple = selectableItemBackground();
        if (ripple != 0) row.setBackgroundResource(ripple);

        LinearLayout texts = vertical();
        TextView name = text(title, 15, COLOR_ACCENT);
        name.setTypeface(Typeface.DEFAULT_BOLD);
        texts.addView(name);
        if (subtitle != null && !subtitle.isEmpty()) {
            TextView sub = text(subtitle, 12, COLOR_SECONDARY);
            LinearLayout.LayoutParams subParams = wrap();
            subParams.topMargin = dp(2);
            texts.addView(sub, subParams);
        }
        row.addView(texts, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(text("›", 20, COLOR_SECONDARY));

        row.setClickable(true);
        // 不设为可聚焦：否则 Activity 启动时 ScrollView 会自动滚到焦点行，页面不在顶部
        row.setFocusable(false);
        row.setOnClickListener(v -> action.run());
        return row;
    }

    private int selectableItemBackground() {
        TypedValue value = new TypedValue();
        if (getTheme().resolveAttribute(android.R.attr.selectableItemBackground, value, true)
                && value.resourceId != 0) {
            return value.resourceId;
        }
        return 0;
    }

    /** 优先用酷安 App 的 scheme 直达对应页面，未安装时退回浏览器。 */
    private void openCoolapk(String deepLink, String webUrl) {
        try {
            Intent direct = new Intent(Intent.ACTION_VIEW, Uri.parse(deepLink));
            if (getPackageManager().resolveActivity(direct, 0) != null) {
                startActivity(direct);
                return;
            }
        } catch (Throwable ignored) {
            // scheme 不被支持时继续走网页兜底
        }
        openUrl(webUrl);
    }

    private void openUrl(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Throwable throwable) {
            Toast.makeText(this, "没有可以打开该链接的应用", Toast.LENGTH_SHORT).show();
        }
    }

    private String versionLabel() {
        try {
            String name = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            return "v" + name;
        } catch (PackageManager.NameNotFoundException ignored) {
            return "当前安装版本";
        }
    }

    private void applySystemBarInsets(View root) {
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            int top;
            int bottom;
            if (Build.VERSION.SDK_INT >= 30) {
                Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
                top = bars.top;
                bottom = bars.bottom;
            } else {
                top = insets.getSystemWindowInsetTop();
                bottom = insets.getSystemWindowInsetBottom();
            }
            view.setPadding(0, top, 0, bottom);
            return insets;
        });
        root.requestApplyInsets();
    }

    private LinearLayout createCard(String title, String subtitle) {
        LinearLayout card = vertical();
        card.setBackground(rounded(COLOR_CARD, 16));
        card.setElevation(dp(1));
        card.setClipToOutline(true);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardParams.bottomMargin = dp(12);
        card.setLayoutParams(cardParams);

        LinearLayout header = vertical();
        header.setPadding(dp(16), dp(14), dp(16), subtitle == null ? dp(10) : dp(11));
        TextView titleView = text(title, 18, COLOR_TEXT);
        titleView.setTypeface(Typeface.DEFAULT_BOLD);
        header.addView(titleView);
        if (subtitle != null && !subtitle.isEmpty()) {
            TextView subtitleView = text(subtitle, 12, COLOR_SECONDARY);
            LinearLayout.LayoutParams subtitleParams = wrap();
            subtitleParams.topMargin = dp(4);
            header.addView(subtitleView, subtitleParams);
        }
        card.addView(header);
        return card;
    }

    private View divider() {
        View line = new View(this);
        line.setBackgroundColor(COLOR_DIVIDER);
        line.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(1))));
        return line;
    }

    private GradientDrawable rounded(int fillColor, int radiusDp) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(fillColor);
        background.setCornerRadius(dp(radiusDp));
        return background;
    }

    private TextView text(String value, int sp, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(color);
        return view;
    }

    private LinearLayout vertical() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    private LinearLayout horizontal() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        return layout;
    }

    private LinearLayout.LayoutParams wrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
