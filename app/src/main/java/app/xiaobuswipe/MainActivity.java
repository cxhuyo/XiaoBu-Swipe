package app.xiaobuswipe;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.function.IntConsumer;

public final class MainActivity extends Activity {
    private static final String LAUNCHER_ALIAS = "app.xiaobuswipe.LauncherAlias";

    private static final int COLOR_PAGE = Color.rgb(245, 247, 250);
    private static final int COLOR_CARD = Color.WHITE;
    private static final int COLOR_TEXT = Color.rgb(32, 36, 43);
    private static final int COLOR_SECONDARY = Color.rgb(101, 109, 122);
    private static final int COLOR_DIVIDER = Color.rgb(232, 235, 240);
    private static final int COLOR_ACCENT = Color.rgb(36, 103, 214);
    private static final int COLOR_ACCENT_SOFT = Color.rgb(232, 240, 254);
    private static final int COLOR_KEY_IDLE = Color.rgb(247, 249, 252);
    private static final int COLOR_KEY_STROKE = Color.rgb(218, 223, 232);
    private static final int COLOR_DANGER = Color.rgb(190, 55, 55);
    private static final int COLOR_DANGER_SOFT = Color.rgb(255, 237, 237);

    private static final int[] QWERTY_ACTIONS = ActionRegistry.qwertyActions();
    private static final String[] QWERTY_LABELS = ActionRegistry.qwertyLabels();

    private static final String[] QWERTY_ROWS = {
            "qwertyuiop",
            "asdfghjkl",
            "zxcvbnm"
    };

    private static final String[] T9_LETTERS = {
            "", "", "ABC", "DEF", "GHI", "JKL", "MNO", "PQRS", "TUV", "WXYZ"
    };

    private SharedPreferences prefs;

    private final String[] qwertyKeys = new String[QWERTY_ACTIONS.length];
    private final TextView[] qwertyActionViews = new TextView[26];
    private final LinearLayout[] qwertyKeyViews = new LinearLayout[26];
    private final String[] qwertyCustomLabels = new String[26];
    private final String[] qwertyInsertTexts = new String[26];
    private String disabledKeys = "";

    private final int[] t9Actions = new int[10];
    private final TextView[] t9ActionViews = new TextView[10];
    private final LinearLayout[] t9KeyViews = new LinearLayout[10];
    private final String[] t9CustomLabels = new String[10];
    private final String[] t9InsertTexts = new String[10];

    // 方向级绑定与触发距离。下滑仍与旧结构（qwertyKeys / t9Actions）保持同步，
    // 上/左/右为新增能力，互不影响。
    private final int[][] qwertyDirs = new int[26][Config.DIR_COUNT];
    private final int[][] t9Dirs = new int[10][Config.DIR_COUNT];
    private final int[][] specialDirs =
            new int[Config.SPECIAL_KEYS.length][Config.DIR_COUNT];
    private final String[][] qwertyDirTexts = new String[26][Config.DIR_COUNT];
    private final String[][] t9DirTexts = new String[10][Config.DIR_COUNT];
    private final String[][] specialDirTexts =
            new String[Config.SPECIAL_KEYS.length][Config.DIR_COUNT];
    private final int[][] qwertyDist = new int[26][Config.DIR_COUNT];
    private final int[][] t9Dist = new int[10][Config.DIR_COUNT];
    private final int[][] specialDist =
            new int[Config.SPECIAL_KEYS.length][Config.DIR_COUNT];
    /** 特殊键配置单元：[0]=keyId，[1]=摘要 TextView。26 键页与九宫格页各有一份，需一起刷新。 */
    private final java.util.List<Object[]> specialCells = new java.util.ArrayList<>();

    private static final String UI_PREFS = "ui_state";
    /** 记录「已看过关于页」的版本号；升级后版本号变化，红点重新出现。 */
    private static final String KEY_ABOUT_BADGE_VERSION = "about_badge_version";

    private TextView aboutButton;
    private View aboutBadge;

    private SeekBar threshold;
    private SeekBar t9Threshold;
    private TextView thresholdValue;
    private TextView t9ThresholdValue;
    private CheckBox showKeyLabels;
    private CheckBox showTriggerHint;
    private CheckBox vibration;
    private CheckBox hideIcon;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        setTitle("小布输入法下滑快捷键");
        prefs = getSharedPreferences(Config.PREFS, MODE_PRIVATE);
        loadUiState();
        configureWindow();
        setContentView(buildPage());
    }

    @Override protected void onResume() {
        super.onResume();
        if (hideIcon != null) hideIcon.setChecked(isLauncherIconHidden());
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

        page.addView(buildHeader(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);

        LinearLayout content = vertical();
        content.setPadding(dp(12), dp(12), dp(12), dp(22));
        scroll.addView(content);

        LinearLayout qwertyPage = vertical();
        qwertyPage.addView(buildQwertyCard());
        qwertyPage.addView(buildSpecialKeyCard(false));

        LinearLayout t9Page = vertical();
        t9Page.addView(buildT9Card());
        t9Page.addView(buildSpecialKeyCard(true));

        LinearLayout optionsPage = vertical();
        optionsPage.addView(buildGestureCard());
        optionsPage.addView(buildGeneralCard());
        optionsPage.addView(buildSupportCard());

        content.addView(qwertyPage, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        content.addView(t9Page, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        content.addView(optionsPage, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        page.addView(SettingsTabs.create(this, scroll, qwertyPage, t9Page, optionsPage),
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT));
        page.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        page.addView(buildSaveBar(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return page;
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

    private View buildHeader() {
        LinearLayout header = vertical();
        boolean compact = getResources().getConfiguration().screenHeightDp < 460;
        header.setPadding(dp(18), dp(compact ? 8 : 13), dp(18), dp(compact ? 8 : 15));
        header.setBackgroundColor(Color.WHITE);
        header.setClipChildren(false);

        LinearLayout row = horizontal();
        row.setGravity(Gravity.TOP);

        LinearLayout texts = vertical();
        row.addView(texts, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView eyebrow = text("小布输入法 · LSPosed", 12, COLOR_ACCENT);
        eyebrow.setTypeface(Typeface.DEFAULT_BOLD);
        eyebrow.setVisibility(compact ? View.GONE : View.VISIBLE);
        texts.addView(eyebrow);

        TextView title = text("下滑快捷键", compact ? 20 : 24, COLOR_TEXT);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams titleParams = wrap();
        titleParams.topMargin = dp(3);
        texts.addView(title, titleParams);

        String versionName = versionLabel();
        TextView version = text(versionName + " · 修改后点击底部保存", 12, COLOR_SECONDARY);
        version.setVisibility(compact ? View.GONE : View.VISIBLE);
        LinearLayout.LayoutParams versionParams = wrap();
        versionParams.topMargin = dp(4);
        texts.addView(version, versionParams);

        // 右上角「关于」入口：打开独立的关于 Activity
        aboutButton = text("关于", 15, COLOR_ACCENT);
        aboutButton.setTypeface(Typeface.DEFAULT_BOLD);
        aboutButton.setGravity(Gravity.CENTER);
        aboutButton.setPadding(dp(20), dp(10), dp(20), dp(10));
        aboutButton.setBackground(rounded(COLOR_ACCENT_SOFT, 18, 0, 0));
        aboutButton.setClickable(true);
        aboutButton.setFocusable(true);
        aboutButton.setContentDescription("关于本模块");
        aboutButton.setOnClickListener(v -> openAbout());
        // 红点挂在按钮右上角，需要在容器上关闭裁剪
        FrameLayout aboutHolder = new FrameLayout(this);
        aboutHolder.setClipChildren(false);
        aboutHolder.addView(aboutButton, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        aboutBadge = new View(this);
        aboutBadge.setBackground(badgeDot());
        aboutBadge.setContentDescription("有更新提示");
        FrameLayout.LayoutParams dotParams = new FrameLayout.LayoutParams(dp(18), dp(18));
        dotParams.gravity = Gravity.TOP | Gravity.END;
        dotParams.topMargin = -dp(4);
        dotParams.rightMargin = -dp(4);
        aboutHolder.addView(aboutBadge, dotParams);
        aboutBadge.setVisibility(isAboutBadgeVisible() ? View.VISIBLE : View.GONE);

        row.setClipChildren(false);
        LinearLayout.LayoutParams aboutParams = wrap();
        aboutParams.topMargin = dp(compact ? 2 : 5);
        aboutParams.rightMargin = dp(8);
        row.addView(aboutHolder, aboutParams);

        header.addView(row);
        return header;
    }

    private String versionLabel() {
        try {
            String name = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            return "v" + name;
        } catch (PackageManager.NameNotFoundException ignored) {
            // Package metadata can be unavailable during preview; keep the header usable.
            return "当前安装版本";
        }
    }

    private View buildQwertyCard() {
        LinearLayout card = createCard(
                "26 键手势",
                "点击按键 → 选方向 → 选动作；长按方向项可单独设置触发距离，长按按键设置自定义标签。",
                true);

        LinearLayout keyboard = vertical();
        keyboard.setPadding(dp(6), dp(9), dp(6), dp(9));

        for (int rowIndex = 0; rowIndex < QWERTY_ROWS.length; rowIndex++) {
            LinearLayout row = horizontal();
            row.setGravity(Gravity.CENTER);

            if (rowIndex == 1) addKeyboardSpacer(row, 0.45f);
            if (rowIndex == 2) addKeyboardSpacer(row, 1.35f);

            String letters = QWERTY_ROWS[rowIndex];
            for (int i = 0; i < letters.length(); i++) {
                char key = letters.charAt(i);
                LinearLayout keyView = buildQwertyKey(key);
                LinearLayout.LayoutParams keyParams = new LinearLayout.LayoutParams(0, dp(62), 1f);
                keyParams.setMargins(dp(2), dp(3), dp(2), dp(3));
                row.addView(keyView, keyParams);
            }

            if (rowIndex == 1) addKeyboardSpacer(row, 0.45f);
            if (rowIndex == 2) addKeyboardSpacer(row, 1.35f);
            keyboard.addView(row, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        card.addView(keyboard);

        card.addView(divider());
        LinearLayout tools = horizontal();
        tools.setPadding(dp(12), dp(10), dp(12), dp(12));

        TextView defaults = smallAction("恢复默认 Z/X/C/V", false);
        defaults.setOnClickListener(v -> restoreQwertyDefaults());
        tools.addView(defaults, new LinearLayout.LayoutParams(0, dp(48), 1f));

        TextView clear = smallAction("清空 26 键", true);
        LinearLayout.LayoutParams clearParams = new LinearLayout.LayoutParams(0, dp(48), 1f);
        clearParams.leftMargin = dp(8);
        tools.addView(clear, clearParams);
        clear.setOnClickListener(v -> confirmClearQwerty());
        card.addView(tools);
        return card;
    }

    private void addKeyboardSpacer(LinearLayout row, float weight) {
        View spacer = new View(this);
        row.addView(spacer, new LinearLayout.LayoutParams(0, dp(1), weight));
    }

    private LinearLayout buildQwertyKey(char letter) {
        int index = letter - 'a';
        LinearLayout key = vertical();
        key.setGravity(Gravity.CENTER);
        key.setPadding(dp(1), dp(4), dp(1), dp(4));
        key.setClickable(true);
        key.setFocusable(true);

        TextView letterView = text(String.valueOf(Character.toUpperCase(letter)), 15, COLOR_TEXT);
        letterView.setTypeface(Typeface.DEFAULT_BOLD);
        letterView.setGravity(Gravity.CENTER);
        key.addView(letterView);

        TextView actionView = text("—", 8, COLOR_SECONDARY);
        actionView.setGravity(Gravity.CENTER);
        actionView.setSingleLine(true);
        actionView.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        actionParams.topMargin = dp(3);
        key.addView(actionView, actionParams);

        qwertyKeyViews[index] = key;
        qwertyActionViews[index] = actionView;
        updateQwertyKeyView(letter);
        key.setOnClickListener(v -> showDirectionPicker(
                String.valueOf(letter), false, Character.toUpperCase(letter) + " 键"));
        key.setOnLongClickListener(v -> {
            showQwertyLabelDialog(letter);
            return true;
        });
        return key;
    }

    private View buildT9Card() {
        LinearLayout card = createCard(
                "九宫格手势",
                "2–9 可设置。点击按键 → 选方向 → 选动作；长按方向项单独设置触发距离。",
                true);

        LinearLayout keyboard = vertical();
        keyboard.setPadding(dp(12), dp(9), dp(12), dp(9));

        int[][] rows = {
                {1, 2, 3},
                {4, 5, 6},
                {7, 8, 9}
        };
        for (int[] digits : rows) {
            LinearLayout row = horizontal();
            row.setGravity(Gravity.CENTER);
            for (int digit : digits) {
                View key = buildT9Key(digit);
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(72), 1f);
                params.setMargins(dp(4), dp(4), dp(4), dp(4));
                row.addView(key, params);
            }
            keyboard.addView(row, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        card.addView(keyboard);

        card.addView(divider());
        LinearLayout tools = horizontal();
        tools.setPadding(dp(12), dp(10), dp(12), dp(12));
        TextView clear = smallAction("清空九宫格映射", true);
        tools.addView(clear, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));
        clear.setOnClickListener(v -> confirmClearT9());
        card.addView(tools);
        return card;
    }

    private View buildT9Key(int digit) {
        LinearLayout key = vertical();
        key.setGravity(Gravity.CENTER);
        key.setPadding(dp(6), dp(5), dp(6), dp(5));

        if (digit < 0) {
            String symbol = digit == -1 ? "*" : "#";
            TextView symbolView = text(symbol, 20, COLOR_SECONDARY);
            symbolView.setGravity(Gravity.CENTER);
            key.addView(symbolView);
            key.setBackground(rounded(COLOR_KEY_IDLE, 12, 1, COLOR_KEY_STROKE));
            return key;
        }

        TextView digitView = text(String.valueOf(digit), 20, COLOR_TEXT);
        digitView.setTypeface(Typeface.DEFAULT_BOLD);
        digitView.setGravity(Gravity.CENTER);
        key.addView(digitView);

        TextView letters = text(digit >= 2 ? T9_LETTERS[digit] : "", 10, COLOR_SECONDARY);
        letters.setGravity(Gravity.CENTER);
        key.addView(letters);

        if (digit >= 2 && digit <= 9) {
            TextView action = text("—", 9, COLOR_SECONDARY);
            action.setGravity(Gravity.CENTER);
            action.setSingleLine(true);
            action.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            actionParams.topMargin = dp(2);
            key.addView(action, actionParams);
            t9KeyViews[digit] = key;
            t9ActionViews[digit] = action;
            updateT9View(digit);
            key.setClickable(true);
            key.setFocusable(true);
            key.setOnClickListener(v -> showDirectionPicker(
                    String.valueOf(digit), true, "九宫格 " + digit));
            key.setOnLongClickListener(v -> {
                showT9LabelDialog(digit);
                return true;
            });
        } else {
            TextView note = text("普通键", 9, COLOR_SECONDARY);
            note.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams noteParams = wrap();
            noteParams.topMargin = dp(2);
            key.addView(note, noteParams);
            key.setBackground(rounded(COLOR_KEY_IDLE, 12, 1, COLOR_KEY_STROKE));
        }
        return key;
    }

    private View buildSpecialKeyCard(boolean t9Mode) {
        LinearLayout card = createCard(
                t9Mode ? "九宫格其它按键" : "26 键其它按键",
                t9Mode
                        ? "点击按键 → 选方向 → 选动作；配置后该方向原生行为被屏蔽。长按方向项可单独设触发距离。"
                        : "点击按键 → 选方向 → 选动作。",
                true);
        LinearLayout grid = vertical();
        grid.setPadding(dp(10), dp(6), dp(10), dp(10));
        java.util.List<Integer> indexes = new java.util.ArrayList<>();
        for (int i = 0; i < Config.SPECIAL_KEYS.length; i++) {
            boolean visible = t9Mode ? Config.specialVisibleInT9(i) : Config.specialVisibleInQwerty(i);
            if (visible) indexes.add(i);
        }
        for (int i = 0; i < indexes.size(); i += 2) {
            LinearLayout row = horizontal();
            for (int column = 0; column < 2; column++) {
                int position = i + column;
                if (position < indexes.size()) {
                    View cell = buildSpecialKeyCell(indexes.get(position));
                    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                    params.setMargins(dp(4), dp(4), dp(4), dp(4));
                    row.addView(cell, params);
                } else {
                    row.addView(new View(this), new LinearLayout.LayoutParams(
                            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                }
            }
            grid.addView(row);
        }
        card.addView(grid);
        return card;
    }

    private View buildSpecialKeyCell(int index) {
        final String keyId = Config.SPECIAL_KEYS[index];
        LinearLayout cell = horizontal();
        cell.setGravity(Gravity.CENTER_VERTICAL);
        cell.setPadding(dp(10), dp(9), dp(10), dp(9));
        cell.setBackground(rounded(COLOR_KEY_IDLE, 12, 1, COLOR_KEY_STROKE));

        // 文本型图标（中文/数字串）用小字号，单符号用大字号，视觉上接近小布键面
        String glyphText = Config.SPECIAL_GLYPHS[index];
        boolean textual = glyphText.length() > 1 && !glyphText.startsWith("@");
        TextView glyph = text(glyphText, textual ? 12 : 19, COLOR_TEXT);
        glyph.setTypeface(Typeface.DEFAULT_BOLD);
        glyph.setGravity(Gravity.CENTER);
        glyph.setMinWidth(dp(38));
        glyph.setMaxLines(1);
        cell.addView(glyph);

        LinearLayout texts = vertical();
        LinearLayout.LayoutParams textsParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        textsParams.leftMargin = dp(8);
        TextView name = text(Config.SPECIAL_LABELS[index], 13, COLOR_TEXT);
        TextView summary = text(specialSummary(keyId), 11, COLOR_SECONDARY);
        summary.setSingleLine(true);
        summary.setEllipsize(TextUtils.TruncateAt.END);
        texts.addView(name);
        LinearLayout.LayoutParams summaryParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        summaryParams.topMargin = dp(1);
        texts.addView(summary, summaryParams);
        cell.addView(texts, textsParams);
        specialCells.add(new Object[]{keyId, summary, cell, name});
        applySpecialCellStyle(cell, name, keyId);

        cell.setClickable(true);
        cell.setFocusable(true);
        cell.setOnClickListener(v -> showDirectionPicker(
                keyId, Config.specialIndex(keyId) >= 0 && Config.SPECIAL_MODES[index] == Config.MODE_T9,
                Config.SPECIAL_LABELS[index]));
        return cell;
    }

    /** 已绑定则用与 26 键一致的浅蓝高亮，未绑定保持灰白。 */
    private void applySpecialCellStyle(LinearLayout cell, TextView name, String keyId) {
        if (cell == null) return;
        boolean bound = primaryUiDirection(keyId, false) >= 0;
        if (bound) {
            cell.setBackground(rounded(COLOR_ACCENT_SOFT, 12, 1, Color.rgb(190, 210, 245)));
            if (name != null) name.setTextColor(COLOR_ACCENT);
        } else {
            cell.setBackground(rounded(COLOR_KEY_IDLE, 12, 1, COLOR_KEY_STROKE));
            if (name != null) name.setTextColor(COLOR_TEXT);
        }
    }

    private String specialSummary(String keyId) {
        StringBuilder builder = new StringBuilder();
        int shown = 0;
        for (int dir = 0; dir < Config.DIR_COUNT; dir++) {
            int action = dirAction(keyId, false, dir);
            if (action == Config.ACTION_NONE) continue;
            if (shown == 2) {
                builder.append(" …");
                break;
            }
            if (shown > 0) builder.append(' ');
            builder.append(Config.DIR_SHORT[dir])
                    .append(Config.shortActionLabel(action));
            shown++;
        }
        return builder.length() == 0 ? "未绑定" : builder.toString();
    }

    private View buildGestureCard() {
        LinearLayout card = createCard("手势设置", "距离越大越不容易误触。", true);

        int qwertyValue = clamp(prefs.getInt(Config.KEY_THRESHOLD, Config.DEFAULT_THRESHOLD_DP),
                Config.MIN_THRESHOLD_DP, Config.MAX_THRESHOLD_DP, Config.DEFAULT_THRESHOLD_DP);
        int t9Value = clamp(prefs.getInt(Config.KEY_T9_THRESHOLD, Config.DEFAULT_T9_THRESHOLD_DP),
                Config.MIN_T9_THRESHOLD_DP, Config.MAX_T9_THRESHOLD_DP,
                Config.DEFAULT_T9_THRESHOLD_DP);

        LinearLayout qwertyBlock = vertical();
        qwertyBlock.setPadding(dp(16), dp(12), dp(16), dp(8));
        LinearLayout qwertyHeader = horizontal();
        qwertyHeader.setGravity(Gravity.CENTER_VERTICAL);
        qwertyHeader.addView(text("26 键触发距离", 15, COLOR_TEXT),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        thresholdValue = valueBadge(qwertyValue + " dp");
        qwertyHeader.addView(thresholdValue);
        qwertyBlock.addView(qwertyHeader);
        threshold = createThreshold(qwertyValue,
                Config.MIN_THRESHOLD_DP, Config.MAX_THRESHOLD_DP,
                value -> thresholdValue.setText(value + " dp"));
        qwertyBlock.addView(threshold);
        card.addView(qwertyBlock);

        card.addView(divider());

        LinearLayout t9Block = vertical();
        t9Block.setPadding(dp(16), dp(12), dp(16), dp(8));
        LinearLayout t9Header = horizontal();
        t9Header.setGravity(Gravity.CENTER_VERTICAL);
        t9Header.addView(text("九宫格触发距离", 15, COLOR_TEXT),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        t9ThresholdValue = valueBadge(t9Value + " dp");
        t9Header.addView(t9ThresholdValue);
        t9Block.addView(t9Header);
        t9Threshold = createThreshold(t9Value,
                Config.MIN_T9_THRESHOLD_DP, Config.MAX_T9_THRESHOLD_DP,
                value -> t9ThresholdValue.setText(value + " dp"));
        t9Block.addView(t9Threshold);
        card.addView(t9Block);
        return card;
    }

    private View buildGeneralCard() {
        LinearLayout card = createCard("通用设置", null, false);

        showKeyLabels = new CheckBox(this);
        showKeyLabels.setText("显示按键底部功能文字");
        showKeyLabels.setTextSize(15);
        showKeyLabels.setTextColor(COLOR_TEXT);
        showKeyLabels.setPadding(dp(12), dp(6), dp(12), dp(6));
        showKeyLabels.setChecked(prefs.getBoolean(Config.KEY_SHOW_KEY_LABELS, true));
        showKeyLabels.setMinHeight(dp(54));
        card.addView(showKeyLabels, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        card.addView(divider());

        showTriggerHint = new CheckBox(this);
        showTriggerHint.setText("显示下滑触发提示");
        showTriggerHint.setTextSize(15);
        showTriggerHint.setTextColor(COLOR_TEXT);
        showTriggerHint.setPadding(dp(12), dp(6), dp(12), dp(6));
        showTriggerHint.setChecked(prefs.getBoolean(Config.KEY_SHOW_TRIGGER_HINT, true));
        showTriggerHint.setMinHeight(dp(54));
        card.addView(showTriggerHint, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView displayNote = text("关闭后只隐藏提示，不影响下滑功能。", 12, COLOR_SECONDARY);
        displayNote.setPadding(dp(18), 0, dp(18), dp(12));
        card.addView(displayNote);
        card.addView(divider());

        vibration = new CheckBox(this);
        vibration.setText("触发快捷操作时额外震动");
        vibration.setTextSize(15);
        vibration.setTextColor(COLOR_TEXT);
        vibration.setPadding(dp(12), dp(6), dp(12), dp(6));
        vibration.setChecked(prefs.getBoolean(Config.KEY_VIBRATION, true));
        vibration.setMinHeight(dp(54));
        card.addView(vibration, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        card.addView(divider());

        hideIcon = new CheckBox(this);
        hideIcon.setText("隐藏桌面图标");
        hideIcon.setTextSize(15);
        hideIcon.setTextColor(COLOR_TEXT);
        hideIcon.setPadding(dp(12), dp(6), dp(12), dp(6));
        hideIcon.setChecked(isLauncherIconHidden());
        hideIcon.setMinHeight(dp(54));
        card.addView(hideIcon, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView note = text("隐藏后可从 LSPosed 的模块设置页面重新进入。", 12, COLOR_SECONDARY);
        note.setPadding(dp(18), 0, dp(18), dp(14));
        card.addView(note);
        return card;
    }

    // ==================== 关于页面 ====================

    /** 「手势与选项」页底部的支持入口：直接打开独立的关于页。 */
    private View buildSupportCard() {
        LinearLayout card = createCard("关于与支持", null, false);
        card.addView(buildLinkRow("捐赠打赏", "喜欢的话可以请作者喝杯水", this::openAbout));
        return card;
    }

    /** 可点击条目：左侧标题 + 副标题，右侧箭头。 */
    private View buildLinkRow(String title, String subtitle, Runnable action) {
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
        row.setFocusable(true);
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

    private void openAbout() {
        markAboutBadgeSeen();
        startActivity(AboutActivity.intent(this));
    }

    /** 红点是否显示：当前版本号与「已看过」的版本号不一致时显示。 */
    private boolean isAboutBadgeVisible() {
        int seen = getSharedPreferences(UI_PREFS, MODE_PRIVATE)
                .getInt(KEY_ABOUT_BADGE_VERSION, -1);
        return seen != currentVersionCode();
    }

    /** 进入过关于页即记录当前版本号，红点消失；下次更新版本后自动重新出现。 */
    private void markAboutBadgeSeen() {
        getSharedPreferences(UI_PREFS, MODE_PRIVATE)
                .edit()
                .putInt(KEY_ABOUT_BADGE_VERSION, currentVersionCode())
                .apply();
        if (aboutBadge != null) aboutBadge.setVisibility(View.GONE);
    }

    private int currentVersionCode() {
        try {
            return (int) getPackageManager()
                    .getPackageInfo(getPackageName(), 0).getLongVersionCode();
        } catch (PackageManager.NameNotFoundException ignored) {
            return 0;
        }
    }

    /** 小红点：红色圆形 + 白色描边，叠在浅蓝按钮上仍清晰。 */
    private GradientDrawable badgeDot() {
        GradientDrawable dot = new GradientDrawable();
        dot.setShape(GradientDrawable.OVAL);
        dot.setColor(Color.rgb(240, 58, 58));
        dot.setStroke(dp(3), Color.WHITE);
        return dot;
    }

    private View buildSaveBar() {
        LinearLayout bar = vertical();
        bar.setPadding(dp(12), dp(9), dp(12), dp(11));
        bar.setBackgroundColor(Color.WHITE);
        bar.setElevation(dp(10));

        Button save = new Button(this);
        save.setText("保存并应用配置");
        save.setTextSize(16);
        save.setTextColor(Color.WHITE);
        save.setTypeface(Typeface.DEFAULT_BOLD);
        save.setAllCaps(false);
        save.setBackground(rounded(COLOR_ACCENT, 12, 0, 0));
        save.setOnClickListener(v -> save());
        bar.addView(save, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(50)));
        return bar;
    }

    private LinearLayout createCard(String title, String subtitle, boolean showHeaderDivider) {
        LinearLayout card = vertical();
        card.setBackground(rounded(COLOR_CARD, 16, 0, 0));
        card.setElevation(dp(1));
        // 让可点击行的高亮（ripple）被裁到圆角内
        card.setClipToOutline(true);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardParams.bottomMargin = dp(12);
        card.setLayoutParams(cardParams);

        LinearLayout header = vertical();
        header.setPadding(dp(16), dp(14), dp(16), subtitle == null ? dp(12) : dp(11));
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
        if (showHeaderDivider) card.addView(divider());
        return card;
    }

    private interface TextValueChanged { void apply(String value); }

    // ==================== 四方向手势配置 ====================

    /** 点击按键后的入口：先选方向（长按方向项可单独设置该方向的触发距离）。 */
    private void showDirectionPicker(String keyId, boolean t9, String title) {
        String[] items = new String[Config.DIR_COUNT];
        // 按「上、下、左、右」展示；只有单独设过触发距离时才附上 dp，默认值不再显示
        for (int i = 0; i < Config.DIR_COUNT; i++) {
            int dir = Config.DIR_DISPLAY_ORDER[i];
            int action = dirAction(keyId, t9, dir);
            String name;
            if (action == Config.ACTION_NONE) name = "未绑定";
            else if (action == Config.ACTION_DISABLE) name = "禁用";
            else name = Config.shortActionLabel(action);
            int dist = dirDistance(keyId, t9, dir);
            String distLabel = dist > 0 ? " · " + dist + "dp" : "";
            items[i] = Config.DIR_SHORT[dir] + " " + Config.DIR_NAMES[dir]
                    + " · " + name + distLabel;
        }
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(title + " · 选择滑动方向")
                .setItems(items, (d, which) -> showDirectionActionPicker(
                        keyId, t9, Config.DIR_DISPLAY_ORDER[which], title))
                .setNeutralButton("解除绑定", (d, w) -> clearKeyBindings(keyId, t9, title))
                .setNegativeButton("取消", null)
                .create();
        dialog.show();
        // 长按方向项 → 设置该方向独立触发距离
        dialog.getListView().setOnItemLongClickListener((parent, view, position, id) -> {
            dialog.dismiss();
            showDirectionDistanceDialog(keyId, t9, Config.DIR_DISPLAY_ORDER[position], title);
            return true;
        });
    }

    /** 一键解除该按键四个方向的全部绑定与距离，并清掉文本插入/禁用标记。 */
    private void clearKeyBindings(String keyId, boolean t9, String title) {
        for (int dir = 0; dir < Config.DIR_COUNT; dir++) {
            setDirAction(keyId, t9, dir, Config.ACTION_NONE);
            setDirDistance(keyId, t9, dir, 0);
            setDirText(keyId, t9, dir, "");
        }
        if (Config.specialIndex(keyId) < 0 && keyId.length() == 1) {
            int index = Character.toLowerCase(keyId.charAt(0)) - 'a';
            if (index >= 0 && index < qwertyInsertTexts.length) qwertyInsertTexts[index] = "";
            disabledKeys = disabledKeys.replace(keyId, "");
        }
        refreshKeyViews();
        Toast.makeText(this, title + " 已解除绑定", Toast.LENGTH_SHORT).show();
    }

    private void showDirectionActionPicker(String keyId, boolean t9, int dir, String title) {
        int current = dirAction(keyId, t9, dir);
        showActionPickerDialog(title + " · " + Config.DIR_NAMES[dir] + "执行", current, action -> {
            if (action == Config.ACTION_INSERT_TEXT) {
                showInsertTextDialog(
                        describeKey(keyId) + " · " + Config.DIR_NAMES[dir] + "插入内容",
                        dirText(keyId, t9, dir),
                        value -> {
                            setDirText(keyId, t9, dir, value);
                            setDirAction(keyId, t9, dir,
                                    Config.normalizeInsertedText(value).isEmpty()
                                            ? Config.ACTION_NONE
                                            : Config.ACTION_INSERT_TEXT);
                            refreshKeyViews();
                        });
                return;
            }
            // 切到非文本动作时清空该方向残留文本，避免文本优先覆盖动作
            setDirText(keyId, t9, dir, "");
            setDirAction(keyId, t9, dir, action);
            refreshKeyViews();
        });
    }

    /**
     * 动作选择器：上方搜索框 + 双列网格。
     * 功能项较多（20+），单列列表需要反复滚动，双列 + 搜索能明显减少操作步数。
     */
    private void showActionPickerDialog(String title, int currentAction, IntConsumer onPick) {
        int[] actions = Config.menuActions();
        String[] labels = Config.ACTION_MENU_LABELS;

        LinearLayout container = vertical();
        container.setPadding(dp(16), dp(8), dp(16), dp(2));

        EditText search = new EditText(this);
        search.setHint("搜索功能");
        search.setTextSize(15);
        search.setSingleLine(true);
        search.setBackground(rounded(COLOR_KEY_IDLE, 12, 1, COLOR_KEY_STROKE));
        search.setPadding(dp(12), dp(9), dp(12), dp(9));
        container.addView(search, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        GridView grid = new GridView(this);
        grid.setNumColumns(2);
        grid.setVerticalSpacing(dp(6));
        grid.setHorizontalSpacing(dp(6));
        grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        grid.setPadding(0, dp(8), 0, dp(2));
        grid.setClipToPadding(false);
        int gridHeight = Math.min(dp(420),
                Math.round(getResources().getDisplayMetrics().heightPixels * 0.46f));
        container.addView(grid, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, gridHeight));

        TextView empty = text("没有匹配的功能", 13, COLOR_SECONDARY);
        empty.setGravity(Gravity.CENTER);
        empty.setVisibility(View.GONE);
        container.addView(empty);

        ActionAdapter adapter = new ActionAdapter(actions, labels, currentAction);
        grid.setAdapter(adapter);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(title)
                .setView(container)
                .setNegativeButton("取消", null)
                .create();

        grid.setOnItemClickListener((parent, view, position, id) -> {
            int action = adapter.actionAt(position);
            dialog.dismiss();
            onPick.accept(action);
        });

        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence c, int a, int b, int d) { }
            @Override public void onTextChanged(CharSequence c, int a, int b, int d) { }
            @Override public void afterTextChanged(Editable editable) {
                adapter.filter(editable == null ? "" : editable.toString());
                boolean none = adapter.hasNoMatch();
                empty.setVisibility(none ? View.VISIBLE : View.GONE);
                grid.setVisibility(none ? View.GONE : View.VISIBLE);
            }
        });

        dialog.show();
    }

    /** 双列动作网格的适配器，支持按名称过滤。 */
    private final class ActionAdapter extends BaseAdapter {
        private final int[] actions;
        private final String[] labels;
        private final List<Integer> visible = new ArrayList<>();
        private final int currentAction;

        ActionAdapter(int[] actions, String[] labels, int currentAction) {
            this.actions = actions;
            this.labels = labels;
            this.currentAction = currentAction;
            for (int i = 0; i < actions.length; i++) visible.add(i);
        }

        void filter(String query) {
            String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
            visible.clear();
            for (int i = 0; i < actions.length; i++) {
                if (q.isEmpty() || labels[i].toLowerCase(Locale.ROOT).contains(q)) {
                    visible.add(i);
                }
            }
            notifyDataSetChanged();
        }

        boolean hasNoMatch() {
            return visible.isEmpty();
        }

        int actionAt(int position) {
            return actions[visible.get(position)];
        }

        @Override public int getCount() { return visible.size(); }

        @Override public Object getItem(int position) { return labels[visible.get(position)]; }

        @Override public long getItemId(int position) { return visible.get(position); }

        @Override public View getView(int position, View convertView, ViewGroup parent) {
            // 条目最多二十几个，直接新建，避免复用视图时样式残留
            TextView cell = new TextView(MainActivity.this);
            cell.setTextSize(14);
            cell.setGravity(Gravity.CENTER);
            cell.setMinHeight(dp(46));
            cell.setMaxLines(2);
            cell.setPadding(dp(8), dp(8), dp(8), dp(8));
            int index = visible.get(position);
            boolean selected = actions[index] == currentAction;
            cell.setText(labels[index]);
            cell.setTextColor(selected ? COLOR_ACCENT : COLOR_TEXT);
            cell.setTypeface(selected ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
            cell.setBackground(rounded(
                    selected ? COLOR_ACCENT_SOFT : COLOR_KEY_IDLE,
                    12, 1, selected ? Color.rgb(190, 210, 245) : COLOR_KEY_STROKE));
            return cell;
        }
    }

    private void showDirectionDistanceDialog(String keyId, boolean t9, int dir, String title) {
        int current = dirDistance(keyId, t9, dir);
        int globalDp = t9
                ? (t9Threshold.getProgress() + Config.MIN_T9_THRESHOLD_DP)
                : (threshold.getProgress() + Config.MIN_THRESHOLD_DP);
        int min = t9 ? Config.MIN_T9_THRESHOLD_DP : Config.MIN_THRESHOLD_DP;
        int max = t9 ? Config.MAX_T9_THRESHOLD_DP : Config.MAX_THRESHOLD_DP;
        int initial = current > 0 ? current : globalDp;

        LinearLayout box = vertical();
        box.setPadding(dp(20), dp(12), dp(20), dp(4));
        TextView label = text("", 14, COLOR_TEXT);
        SeekBar bar = createThreshold(initial, min, max,
                value -> label.setText(value + " dp"));
        label.setText(initial + " dp" + (current > 0 ? "" : "（当前跟随全局）"));
        box.addView(label);
        box.addView(bar);

        new AlertDialog.Builder(this)
                .setTitle(describeKey(keyId) + " · " + Config.DIR_NAMES[dir] + "触发距离")
                .setView(box)
                .setPositiveButton("保存", (d, w) -> {
                    setDirDistance(keyId, t9, dir, bar.getProgress() + min);
                    refreshKeyViews();
                })
                .setNeutralButton("用全局默认", (d, w) -> {
                    setDirDistance(keyId, t9, dir, 0);
                    refreshKeyViews();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private String describeKey(String keyId) {
        int special = Config.specialIndex(keyId);
        if (special >= 0) return Config.SPECIAL_LABELS[special];
        if (keyId.length() == 1) {
            char c = keyId.charAt(0);
            if (c >= 'a' && c <= 'z') return Character.toUpperCase(c) + " 键";
            return "九宫格 " + c;
        }
        return keyId;
    }

    private int dirAction(String keyId, boolean t9, int dir) {
        if (dir == Config.DIR_DOWN) {
            int special = Config.specialIndex(keyId);
            if (special < 0) {
                if (keyId.length() == 1) {
                    char c = Character.toLowerCase(keyId.charAt(0));
                    if (c >= 'a' && c <= 'z') return actionForQwertyKey(keyId);
                    if (c >= '2' && c <= '9') return Config.validAction(t9Actions[c - '0']);
                }
            }
        }
        int special = Config.specialIndex(keyId);
        if (special >= 0) return specialDirs[special][dir];
        if (keyId.length() == 1) {
            char c = Character.toLowerCase(keyId.charAt(0));
            if (c >= 'a' && c <= 'z') return qwertyDirs[c - 'a'][dir];
            if (c >= '2' && c <= '9') return t9Dirs[c - '0'][dir];
        }
        return Config.ACTION_NONE;
    }

    private void setDirAction(String keyId, boolean t9, int dir, int action) {
        int special = Config.specialIndex(keyId);
        if (special >= 0) {
            specialDirs[special][dir] = action;
            return;
        }
        if (keyId.length() != 1) return;
        char c = Character.toLowerCase(keyId.charAt(0));
        if (c >= 'a' && c <= 'z') {
            int index = c - 'a';
            if (dir == Config.DIR_DOWN) {
                assignQwertyAction(String.valueOf(c), action);
            }
            qwertyDirs[index][dir] = action;
            return;
        }
        if (c >= '2' && c <= '9') {
            int digit = c - '0';
            if (dir == Config.DIR_DOWN) {
                t9Actions[digit] = action;
            }
            t9Dirs[digit][dir] = action;
        }
    }

    private String dirText(String keyId, boolean t9, int dir) {
        if (keyId == null || keyId.isEmpty()) return "";
        if (dir < 0 || dir >= Config.DIR_COUNT) dir = Config.DIR_DOWN;
        int special = Config.specialIndex(keyId);
        if (special >= 0) {
            String value = specialDirTexts[special][dir];
            return value == null ? "" : value;
        }
        if (keyId.length() != 1) return "";
        char c = Character.toLowerCase(keyId.charAt(0));
        String value;
        if (c >= 'a' && c <= 'z') value = qwertyDirTexts[c - 'a'][dir];
        else if (c >= '2' && c <= '9') value = t9DirTexts[c - '0'][dir];
        else return "";
        return value == null ? "" : value;
    }

    private void setDirText(String keyId, boolean t9, int dir, String text) {
        if (keyId == null || keyId.isEmpty()) return;
        if (dir < 0 || dir >= Config.DIR_COUNT) dir = Config.DIR_DOWN;
        String value = Config.normalizeInsertedText(text);
        int special = Config.specialIndex(keyId);
        if (special >= 0) {
            specialDirTexts[special][dir] = value;
            return;
        }
        if (keyId.length() != 1) return;
        char c = Character.toLowerCase(keyId.charAt(0));
        if (c >= 'a' && c <= 'z') {
            qwertyDirTexts[c - 'a'][dir] = value;
            if (dir == Config.DIR_DOWN) qwertyInsertTexts[c - 'a'] = value;
        } else if (c >= '2' && c <= '9') {
            t9DirTexts[c - '0'][dir] = value;
            if (dir == Config.DIR_DOWN) t9InsertTexts[c - '0'] = value;
        }
    }

    private int dirDistance(String keyId, boolean t9, int dir) {
        int special = Config.specialIndex(keyId);
        if (special >= 0) return specialDist[special][dir];
        if (keyId.length() == 1) {
            char c = Character.toLowerCase(keyId.charAt(0));
            if (c >= 'a' && c <= 'z') return qwertyDist[c - 'a'][dir];
            if (c >= '2' && c <= '9') return t9Dist[c - '0'][dir];
        }
        return 0;
    }

    private void setDirDistance(String keyId, boolean t9, int dir, int dpValue) {
        int special = Config.specialIndex(keyId);
        if (special >= 0) {
            specialDist[special][dir] = dpValue;
            return;
        }
        if (keyId.length() != 1) return;
        char c = Character.toLowerCase(keyId.charAt(0));
        if (c >= 'a' && c <= 'z') qwertyDist[c - 'a'][dir] = dpValue;
        else if (c >= '2' && c <= '9') t9Dist[c - '0'][dir] = dpValue;
    }

    /** 方向配置变更后刷新键面标签（下滑方向仍需刷新旧视图）。 */
    private void refreshKeyViews() {
        updateAllQwertyViews();
        for (int digit = 2; digit <= 9; digit++) updateT9View(digit);
        for (Object[] cell : specialCells) {
            String keyId = (String) cell[0];
            TextView summary = (TextView) cell[1];
            if (summary != null) summary.setText(specialSummary(keyId));
            if (cell.length >= 4 && cell[2] instanceof LinearLayout && cell[3] instanceof TextView) {
                applySpecialCellStyle((LinearLayout) cell[2], (TextView) cell[3], keyId);
            }
        }
    }

    private void showInsertTextDialog(String title, String currentValue, TextValueChanged changed) {
        EditText input = new EditText(this);
        input.setText(Config.normalizeInsertedText(currentValue));
        input.setHint("最多 1000 个字符，支持换行、符号和 Emoji");
        input.setSingleLine(false);
        input.setMinLines(3);
        input.setMaxLines(8);
        input.setGravity(Gravity.TOP | Gravity.START);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(1000)});
        LinearLayout host = vertical();
        host.setPadding(dp(20), dp(8), dp(20), 0);
        host.addView(input, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        AlertDialog edit = new AlertDialog.Builder(this)
                .setTitle(title)
                .setView(host)
                .setNegativeButton("取消", null)
                .setPositiveButton("保存", null)
                .create();
        edit.setOnShowListener(ignored -> edit.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String value = Config.normalizeInsertedText(input.getText().toString());
            if (value.isEmpty()) {
                Toast.makeText(this, "输入内容不能为空", Toast.LENGTH_SHORT).show();
                return;
            }
            changed.apply(value);
            edit.dismiss();
        }));
        edit.show();
        input.requestFocus();
        input.setSelection(input.length());
    }

    private interface LabelValueChanged { void apply(String value); }

    private void showQwertyLabelDialog(char letter) {
        int action = actionForQwertyKey(String.valueOf(letter));
        showLabelDialog(Character.toUpperCase(letter) + " 键显示标签",
                qwertyCustomLabels[letter - 'a'], Config.shortActionLabel(action), value -> {
                    qwertyCustomLabels[letter - 'a'] = value;
                    updateQwertyKeyView(letter);
                });
    }

    private void showT9LabelDialog(int digit) {
        int action = t9Actions[digit];
        showLabelDialog(Config.t9Label(digit) + " 显示标签",
                t9CustomLabels[digit], Config.shortActionLabel(action), value -> {
                    t9CustomLabels[digit] = value;
                    updateT9View(digit);
                });
    }

    private void showLabelDialog(String title, String currentValue,
                                 String automaticValue, LabelValueChanged changed) {
        String current = Config.normalizeLabelValue(currentValue);
        int selected = Config.LABEL_HIDDEN.equals(current) ? 2 : (current.isEmpty() ? 0 : 1);
        String autoText = automaticValue == null || automaticValue.isEmpty()
                ? "自动（当前无动作）" : "自动（" + automaticValue + "）";
        String customText = current.isEmpty() || Config.LABEL_HIDDEN.equals(current)
                ? "自定义文字" : "自定义（" + current + "）";
        String[] options = {autoText, customText, "隐藏此按键标签"};
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(title)
                .setSingleChoiceItems(options, selected, null)
                .setNegativeButton("取消", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getListView().setOnItemClickListener(
                (parent, view, position, id) -> {
                    dialog.dismiss();
                    if (position == 0) changed.apply("");
                    else if (position == 2) changed.apply(Config.LABEL_HIDDEN);
                    else showCustomLabelInput(title, current, changed);
                }));
        dialog.show();
    }

    private void showCustomLabelInput(String title, String currentValue, LabelValueChanged changed) {
        EditText input = new EditText(this);
        String current = Config.normalizeLabelValue(currentValue);
        if (!Config.LABEL_HIDDEN.equals(current)) input.setText(current);
        input.setSingleLine(true);
        input.setHint("最多 4 个字符，留空恢复自动");
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(4)});
        input.setSelectAllOnFocus(true);
        int padding = dp(20);
        LinearLayout host = vertical();
        host.setPadding(padding, dp(8), padding, 0);
        host.addView(input, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(title + " · 自定义")
                .setView(host)
                .setNegativeButton("取消", null)
                .setPositiveButton("保存", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    String value = Config.normalizeLabelValue(input.getText().toString());
                    changed.apply(value);
                    dialog.dismiss();
                }));
        dialog.show();
        input.requestFocus();
    }

    private void assignQwertyAction(String key, int action) {
        int previousAction = actionForQwertyKey(key);

        // 一个按键在下滑方向只能挂一个功能：先把该键从所有功能里摘掉
        for (int i = 0; i < qwertyKeys.length; i++) {
            qwertyKeys[i] = removeKeyFromList(qwertyKeys[i], key);
        }
        int textIndex = key.charAt(0) - 'a';
        if (textIndex >= 0 && textIndex < qwertyInsertTexts.length) qwertyInsertTexts[textIndex] = "";
        disabledKeys = disabledKeys.replace(key, "");

        int actionIndex = actionIndexFor(action);
        if (action == Config.ACTION_DISABLE) {
            disabledKeys = normalizedKeys(disabledKeys + key);
        } else if (action != Config.ACTION_NONE && actionIndex >= 0) {
            // 不再清掉该功能已有的键：同一功能可以同时绑在多个按键上
            qwertyKeys[actionIndex] = addKeyToList(qwertyKeys[actionIndex], key);
        }

        updateAllQwertyViews();
        if (action == Config.ACTION_DISABLE) {
            Toast.makeText(this, key.toUpperCase(Locale.ROOT) + " 已禁用下滑", Toast.LENGTH_SHORT).show();
        } else if (action != Config.ACTION_NONE && actionIndex >= 0) {
            String allKeys = formatKeyList(qwertyKeys[actionIndex]);
            Toast.makeText(this,
                    "“" + Config.actionName(action) + "”当前绑定：" + allKeys,
                    Toast.LENGTH_SHORT).show();
        } else if (previousAction != Config.ACTION_NONE) {
            Toast.makeText(this,
                    key.toUpperCase(Locale.ROOT) + " 已取消绑定", Toast.LENGTH_SHORT).show();
        }
    }

    /** 把键加入逗号分隔的键列表（已存在则不重复添加）。 */
    private static String addKeyToList(String list, String key) {
        if (key == null || key.isEmpty()) return list == null ? "" : list;
        if (list == null || list.isEmpty()) return key;
        if (!listContainsKey(list, key)) return list + "," + key;
        return list;
    }

    /** 从逗号分隔的键列表移除某个键。 */
    private static String removeKeyFromList(String list, String key) {
        if (list == null || list.isEmpty()) return "";
        StringBuilder result = new StringBuilder();
        for (String token : Config.splitKeyIds(list)) {
            if (token.equals(key)) continue;
            if (result.length() > 0) result.append(',');
            result.append(token);
        }
        return result.toString();
    }

    private static boolean listContainsKey(String list, String key) {
        if (list == null || list.isEmpty() || key == null) return false;
        for (String token : Config.splitKeyIds(list)) {
            if (token.equals(key)) return true;
        }
        return false;
    }

    /** "v,b" → "V、B"，用于提示文案。 */
    private static String formatKeyList(String list) {
        StringBuilder result = new StringBuilder();
        for (String token : Config.splitKeyIds(list)) {
            if (result.length() > 0) result.append('、');
            for (int i = 0; i < token.length(); i++) {
                char c = token.charAt(i);
                if (i == 0) result.append(Character.toUpperCase(c));
                else result.append(c);
            }
        }
        return result.toString();
    }

    private int actionIndexFor(int action) {
        for (int i = 0; i < QWERTY_ACTIONS.length; i++) {
            if (QWERTY_ACTIONS[i] == action) return i;
        }
        return -1;
    }

    private int actionForQwertyKey(String key) {
        if (key == null || key.length() != 1) return Config.ACTION_NONE;
        int textIndex = key.charAt(0) - 'a';
        if (textIndex >= 0 && textIndex < qwertyInsertTexts.length
                && !Config.normalizeInsertedText(qwertyInsertTexts[textIndex]).isEmpty()) return Config.ACTION_INSERT_TEXT;
        if (disabledKeys.indexOf(key) >= 0) return Config.ACTION_DISABLE;
        for (int i = 0; i < qwertyKeys.length; i++) {
            if (listContainsKey(qwertyKeys[i], key)) return QWERTY_ACTIONS[i];
        }
        return Config.ACTION_NONE;
    }

    private void restoreQwertyDefaults() {
        Arrays.fill(qwertyKeys, "");
        Arrays.fill(qwertyInsertTexts, "");
        qwertyKeys[0] = "z";
        qwertyKeys[1] = "x";
        qwertyKeys[2] = "c";
        qwertyKeys[3] = "v";
        disabledKeys = "";
        updateAllQwertyViews();
        Toast.makeText(this, "已恢复默认映射，点击底部按钮保存", Toast.LENGTH_SHORT).show();
    }

    private void confirmClearQwerty() {
        new AlertDialog.Builder(this)
                .setTitle("清空 26 键映射？")
                .setMessage("将清空全部快捷功能和禁用下滑按键，保存前仍可重新设置。")
                .setNegativeButton("取消", null)
                .setPositiveButton("清空", (dialog, which) -> {
                    Arrays.fill(qwertyKeys, "");
                    Arrays.fill(qwertyInsertTexts, "");
                    disabledKeys = "";
                    updateAllQwertyViews();
                })
                .show();
    }

    private void confirmClearT9() {
        new AlertDialog.Builder(this)
                .setTitle("清空九宫格映射？")
                .setMessage("2–9 将全部恢复为未绑定，保存前仍可重新设置。")
                .setNegativeButton("取消", null)
                .setPositiveButton("清空", (dialog, which) -> {
                    for (int digit = 2; digit <= 9; digit++) {
                        t9Actions[digit] = Config.ACTION_NONE;
                        t9InsertTexts[digit] = "";
                        updateT9View(digit);
                    }
                })
                .show();
    }

    private void loadUiState() {
        Config config = ConfigCodec.fromPreferences(prefs);
        for (int i = 0; i < QWERTY_ACTIONS.length; i++) {
            qwertyKeys[i] = normalizedKeyList(ActionRegistry.keyFor(config, QWERTY_ACTIONS[i]));
        }
        disabledKeys = normalizedKeys(config.disabledKeys);
        System.arraycopy(config.qwertyLabels, 0, qwertyCustomLabels, 0, qwertyCustomLabels.length);
        System.arraycopy(config.qwertyTexts, 0, qwertyInsertTexts, 0, qwertyInsertTexts.length);
        System.arraycopy(config.t9Actions, 0, t9Actions, 0, t9Actions.length);
        System.arraycopy(config.t9Labels, 0, t9CustomLabels, 0, t9CustomLabels.length);
        System.arraycopy(config.t9Texts, 0, t9InsertTexts, 0, t9InsertTexts.length);
        // 方向表：下滑来自旧结构，其余方向直接复制
        for (int i = 0; i < 26; i++) {
            System.arraycopy(config.qwertyDirActions[i], 0,
                    qwertyDirs[i], 0, Config.DIR_COUNT);
            System.arraycopy(config.qwertyDirDistances[i], 0,
                    qwertyDist[i], 0, Config.DIR_COUNT);
        }
        for (int i = 0; i < 10; i++) {
            System.arraycopy(config.t9DirActions[i], 0, t9Dirs[i], 0, Config.DIR_COUNT);
            System.arraycopy(config.t9DirDistances[i], 0, t9Dist[i], 0, Config.DIR_COUNT);
        }
        for (int i = 0; i < 26; i++) {
            System.arraycopy(config.qwertyDirTexts[i], 0,
                    qwertyDirTexts[i], 0, Config.DIR_COUNT);
        }
        for (int i = 0; i < 10; i++) {
            System.arraycopy(config.t9DirTexts[i], 0, t9DirTexts[i], 0, Config.DIR_COUNT);
        }
        for (int i = 0; i < Config.SPECIAL_KEYS.length; i++) {
            System.arraycopy(config.specialDirActions[i], 0,
                    specialDirs[i], 0, Config.DIR_COUNT);
            System.arraycopy(config.specialDirDistances[i], 0,
                    specialDist[i], 0, Config.DIR_COUNT);
            System.arraycopy(config.specialDirTexts[i], 0,
                    specialDirTexts[i], 0, Config.DIR_COUNT);
        }
    }

    private Config configFromUi(int revision) {
        Config config = new Config();
        for (int i = 0; i < QWERTY_ACTIONS.length; i++) {
            ActionRegistry.setKey(config, QWERTY_ACTIONS[i], qwertyKeys[i]);
        }
        config.disabledKeys = disabledKeys;
        config.thresholdDp = threshold.getProgress() + Config.MIN_THRESHOLD_DP;
        config.t9ThresholdDp = t9Threshold.getProgress() + Config.MIN_T9_THRESHOLD_DP;
        config.vibration = vibration.isChecked();
        config.showKeyLabels = showKeyLabels.isChecked();
        config.showTriggerHint = showTriggerHint.isChecked();
        config.revision = revision;
        System.arraycopy(t9Actions, 0, config.t9Actions, 0, t9Actions.length);
        System.arraycopy(qwertyCustomLabels, 0, config.qwertyLabels, 0, qwertyCustomLabels.length);
        System.arraycopy(t9CustomLabels, 0, config.t9Labels, 0, t9CustomLabels.length);
        System.arraycopy(qwertyInsertTexts, 0, config.qwertyTexts, 0, qwertyInsertTexts.length);
        System.arraycopy(t9InsertTexts, 0, config.t9Texts, 0, t9InsertTexts.length);
        config.rebuildActionMap();
        applyDirectionalState(config);
        return config;
    }

    /** 把 UI 的方向绑定与按键级距离写回 Config（下滑方向已由旧结构在 rebuild 时落地）。 */
    private void applyDirectionalState(Config config) {
        for (int i = 0; i < 26; i++) {
            for (int dir = 0; dir < Config.DIR_COUNT; dir++) {
                config.qwertyDirTexts[i][dir] = qwertyDirTexts[i][dir];
                int action = qwertyDirs[i][dir];
                if (!Config.normalizeInsertedText(qwertyDirTexts[i][dir]).isEmpty()) {
                    action = Config.ACTION_INSERT_TEXT;
                }
                if (dir != Config.DIR_DOWN || action == Config.ACTION_INSERT_TEXT) {
                    config.qwertyDirActions[i][dir] = action;
                }
                config.qwertyDirDistances[i][dir] = qwertyDist[i][dir];
            }
        }
        for (int i = 0; i < 10; i++) {
            for (int dir = 0; dir < Config.DIR_COUNT; dir++) {
                config.t9DirTexts[i][dir] = t9DirTexts[i][dir];
                int action = t9Dirs[i][dir];
                if (!Config.normalizeInsertedText(t9DirTexts[i][dir]).isEmpty()) {
                    action = Config.ACTION_INSERT_TEXT;
                }
                if (dir != Config.DIR_DOWN || action == Config.ACTION_INSERT_TEXT) {
                    config.t9DirActions[i][dir] = action;
                }
                config.t9DirDistances[i][dir] = t9Dist[i][dir];
            }
        }
        for (int i = 0; i < Config.SPECIAL_KEYS.length; i++) {
            for (int dir = 0; dir < Config.DIR_COUNT; dir++) {
                config.specialDirTexts[i][dir] = specialDirTexts[i][dir];
                int action = specialDirs[i][dir];
                if (!Config.normalizeInsertedText(specialDirTexts[i][dir]).isEmpty()) {
                    action = Config.ACTION_INSERT_TEXT;
                }
                config.specialDirActions[i][dir] = action;
                config.specialDirDistances[i][dir] = specialDist[i][dir];
            }
        }
    }

    private void save() {
        HashSet<String> used = new HashSet<>();
        for (String list : qwertyKeys) {
            for (String key : Config.splitKeyIds(list)) {
                if (!used.add(key)) {
                    Toast.makeText(this, "同一个字母不能绑定多个功能", Toast.LENGTH_SHORT).show();
                    return;
                }
            }
        }
        for (int i = 0; i < disabledKeys.length(); i++) {
            if (used.contains(String.valueOf(disabledKeys.charAt(i)))) {
                Toast.makeText(this, "禁用下滑按键不能与快捷功能重复", Toast.LENGTH_SHORT).show();
                return;
            }
        }

        boolean shouldHideIcon = hideIcon.isChecked();
        int revision = prefs.getInt(Config.KEY_REVISION, 0) + 1;
        Config config = configFromUi(revision);
        SharedPreferences.Editor editor = prefs.edit();
        ConfigCodec.writeToPreferences(editor, config);
        editor.remove("text_start")
                .remove("text_end")
                .putBoolean(Config.KEY_HIDE_ICON, shouldHideIcon);

        if (!editor.commit()) {
            Toast.makeText(this, "配置保存失败", Toast.LENGTH_SHORT).show();
            return;
        }

        Intent changed = new Intent(Config.ACTION_CONFIG_CHANGED);
        changed.setPackage("com.oplus.keyboard");
        ConfigSnapshot.putInto(changed, config);
        sendBroadcast(changed);

        setLauncherIconHidden(shouldHideIcon);
        Toast.makeText(this,
                shouldHideIcon
                        ? "已保存并隐藏桌面图标，可从 LSPosed 模块设置重新进入"
                        : "配置已保存并同步到小布输入法",
                Toast.LENGTH_LONG).show();
    }

    private void updateAllQwertyViews() {
        for (char key = 'a'; key <= 'z'; key++) updateQwertyKeyView(key);
    }

    private void updateQwertyKeyView(char letter) {
        int index = letter - 'a';
        LinearLayout keyView = qwertyKeyViews[index];
        TextView actionView = qwertyActionViews[index];
        if (keyView == null || actionView == null) return;

        String keyId = String.valueOf(letter);
        int dir = primaryUiDirection(keyId, false);
        int action = dir < 0 ? Config.ACTION_NONE : dirAction(keyId, false, dir);
        // 键位宽度有限（约 147px），标签不再拼方向箭头，否则"↓全复制"会被截断；
        // 方向信息放在无障碍描述与点击后的方向对话框里。
        actionView.setText(previewLabel(qwertyCustomLabels[index], action));
        keyView.setContentDescription(Character.toUpperCase(letter) + " 键，"
                + (dir < 0 ? "未绑定" : Config.DIR_NAMES[dir] + "：" + Config.actionName(action))
                + "。点击修改，长按设置标签");
        if (action == Config.ACTION_DISABLE) {
            actionView.setTextColor(COLOR_DANGER);
            keyView.setBackground(rounded(COLOR_DANGER_SOFT, 9, 1, Color.rgb(245, 190, 190)));
        } else if (action == Config.ACTION_NONE) {
            actionView.setTextColor(COLOR_SECONDARY);
            keyView.setBackground(rounded(COLOR_KEY_IDLE, 9, 1, COLOR_KEY_STROKE));
        } else {
            actionView.setTextColor(COLOR_ACCENT);
            keyView.setBackground(rounded(COLOR_ACCENT_SOFT, 9, 1, Color.rgb(190, 210, 245)));
        }
    }

    private void updateT9View(int digit) {
        if (t9ActionViews[digit] == null || t9KeyViews[digit] == null) return;
        String keyId = String.valueOf(digit);
        int dir = primaryUiDirection(keyId, true);
        int action = dir < 0 ? Config.ACTION_NONE : dirAction(keyId, true, dir);
        t9ActionViews[digit].setText(previewLabel(t9CustomLabels[digit], action));
        t9KeyViews[digit].setContentDescription(digit + " 键，"
                + (dir < 0 ? "未绑定" : Config.DIR_NAMES[dir] + "：" + Config.actionName(action))
                + "。点击修改，长按设置标签");
        if (action == Config.ACTION_DISABLE) {
            t9ActionViews[digit].setTextColor(COLOR_DANGER);
            t9KeyViews[digit].setBackground(
                    rounded(COLOR_DANGER_SOFT, 12, 1, Color.rgb(245, 190, 190)));
        } else if (action == Config.ACTION_NONE) {
            t9ActionViews[digit].setTextColor(COLOR_SECONDARY);
            t9KeyViews[digit].setBackground(rounded(COLOR_KEY_IDLE, 12, 1, COLOR_KEY_STROKE));
        } else {
            t9ActionViews[digit].setTextColor(COLOR_ACCENT);
            t9KeyViews[digit].setBackground(
                    rounded(COLOR_ACCENT_SOFT, 12, 1, Color.rgb(190, 210, 245)));
        }
    }

    /** 键面展示用的主方向：下滑优先，其次上/左/右；-1 表示未绑定。 */
    private int primaryUiDirection(String keyId, boolean t9) {
        for (int dir = 0; dir < Config.DIR_COUNT; dir++) {
            if (dirAction(keyId, t9, dir) != Config.ACTION_NONE) return dir;
        }
        return -1;
    }

    private String previewLabel(String configured, int action) {
        if (action == Config.ACTION_NONE) return "—";
        String normalized = Config.normalizeLabelValue(configured);
        if (Config.LABEL_HIDDEN.equals(normalized)) return "隐藏";
        if (!normalized.isEmpty()) return normalized;
        return shortActionName(action);
    }

    private String shortActionName(int action) {
        if (Config.validAction(action) == Config.ACTION_DISABLE) return "禁用";
        String label = ActionRegistry.shortLabel(action);
        return label.isEmpty() ? "—" : label;
    }

    /** 旧结构里的键列表只保留 a–z，多个键以逗号分隔。 */
    private String normalizedKeyList(String value) {
        if (value == null) return "";
        StringBuilder result = new StringBuilder();
        for (String token : Config.splitKeyIds(value)) {
            if (token.length() != 1) continue;
            char c = token.charAt(0);
            if (c < 'a' || c > 'z') continue;
            if (result.indexOf(token) >= 0) continue;
            if (result.length() > 0) result.append(',');
            result.append(c);
        }
        return result.toString();
    }

    private String normalizedKeys(String raw) {
        if (raw == null) return "";
        boolean[] seen = new boolean[26];
        StringBuilder result = new StringBuilder(26);
        raw = raw.toLowerCase(Locale.ROOT);
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c < 'a' || c > 'z' || seen[c - 'a']) continue;
            seen[c - 'a'] = true;
            result.append(c);
        }
        return result.toString();
    }

    private boolean isLauncherIconHidden() {
        ComponentName component = new ComponentName(this, LAUNCHER_ALIAS);
        int state = getPackageManager().getComponentEnabledSetting(component);
        if (state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                || state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER
                || state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED) return true;
        if (state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED) return false;
        return prefs.getBoolean(Config.KEY_HIDE_ICON, false);
    }

    private void setLauncherIconHidden(boolean hidden) {
        ComponentName component = new ComponentName(this, LAUNCHER_ALIAS);
        int state = hidden
                ? PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                : PackageManager.COMPONENT_ENABLED_STATE_ENABLED;
        getPackageManager().setComponentEnabledSetting(
                component, state, PackageManager.DONT_KILL_APP);
    }

    private interface ThresholdChanged { void onChanged(int value); }

    private SeekBar createThreshold(int value, int min, int max, ThresholdChanged listener) {
        SeekBar seekBar = new SeekBar(this);
        seekBar.setMax(max - min);
        seekBar.setProgress(value - min);
        seekBar.setPadding(0, dp(4), 0, 0);
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                listener.onChanged(progress + min);
            }
            @Override public void onStartTrackingTouch(SeekBar bar) {}
            @Override public void onStopTrackingTouch(SeekBar bar) {}
        });
        return seekBar;
    }

    private TextView valueBadge(String value) {
        TextView view = text(value, 13, COLOR_ACCENT);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(10), dp(4), dp(10), dp(4));
        view.setBackground(rounded(COLOR_ACCENT_SOFT, 12, 0, 0));
        return view;
    }

    private TextView smallAction(String value, boolean danger) {
        TextView view = text(value, 13, danger ? COLOR_DANGER : COLOR_ACCENT);
        view.setGravity(Gravity.CENTER);
        view.setTypeface(Typeface.DEFAULT_BOLD);
        view.setBackground(rounded(danger ? COLOR_DANGER_SOFT : COLOR_ACCENT_SOFT, 10, 0, 0));
        view.setClickable(true);
        return view;
    }

    private View divider() {
        View divider = new View(this);
        divider.setBackgroundColor(COLOR_DIVIDER);
        divider.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));
        return divider;
    }

    private GradientDrawable rounded(int fillColor, int radiusDp, int strokeWidthDp, int strokeColor) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fillColor);
        drawable.setCornerRadius(dp(radiusDp));
        if (strokeWidthDp > 0) drawable.setStroke(dp(strokeWidthDp), strokeColor);
        return drawable;
    }

    private TextView text(String value, int sp, int color) {
        TextView textView = new TextView(this);
        textView.setText(value);
        textView.setTextSize(sp);
        textView.setTextColor(color);
        return textView;
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

    private static int clamp(int value, int min, int max, int fallback) {
        return value < min || value > max ? fallback : value;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
