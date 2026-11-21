package app.xiaobuswipe;

import java.util.Arrays;
import java.util.Locale;

final class Config {
    static final String ACTION_CONFIG_CHANGED = "app.xiaobuswipe.CONFIG_CHANGED";
    static final String PREFS = "gesture_config";
    static final String TARGET_CACHE_PREFS = "xiaobu_swipe_module_cache_v1";
    static final String EXTRA_SNAPSHOT = "config_snapshot";

    static final String KEY_SELECT_ALL = "select_all";
    static final String KEY_CUT = "cut";
    static final String KEY_COPY = "copy";
    static final String KEY_PASTE = "paste";
    static final String KEY_COPY_ALL = "copy_all";
    static final String KEY_CUT_ALL = "cut_all";
    static final String KEY_PARAGRAPH_START = "paragraph_start";
    static final String KEY_PARAGRAPH_END = "paragraph_end";
    static final String KEY_SELECT_TO_PARAGRAPH_START = "select_to_paragraph_start";
    static final String KEY_SELECT_TO_PARAGRAPH_END = "select_to_paragraph_end";
    static final String KEY_DOCUMENT_START = "document_start";
    static final String KEY_DOCUMENT_END = "document_end";
    static final String KEY_SELECT_TO_DOCUMENT_START = "select_to_document_start";
    static final String KEY_SELECT_TO_DOCUMENT_END = "select_to_document_end";
    static final String KEY_OPEN_CLIPBOARD = "open_clipboard";
    static final String KEY_OPEN_QUICK_PHRASE = "open_quick_phrase";
    static final String KEY_UNDO = "undo";
    static final String KEY_REDO = "redo";
    static final String KEY_NEXT_INPUT_METHOD = "next_input_method";
    static final String KEY_PREVIOUS_INPUT_METHOD = "previous_input_method";
    static final String KEY_SHOW_INPUT_METHOD_PICKER = "show_input_method_picker";
    static final String KEY_PASTE_AND_SEND = "paste_and_send";
    static final String KEY_SEND_EXISTING = "send_existing";
    static final String KEY_DISABLED_KEYS = "disabled_keys";
    static final String KEY_THRESHOLD = "threshold";
    static final String KEY_T9_THRESHOLD = "t9_threshold";
    static final String KEY_VIBRATION = "vibration";
    static final String KEY_SHOW_KEY_LABELS = "show_key_labels";
    static final String KEY_SHOW_TRIGGER_HINT = "show_trigger_hint";
    static final String KEY_HIDE_ICON = "hide_icon";
    static final String KEY_REVISION = "revision";
    static final String KEY_QWERTY_LABEL_PREFIX = "qwerty_label_";
    static final String KEY_T9_LABEL_PREFIX = "t9_label_";
    static final String KEY_QWERTY_TEXT_PREFIX = "qwerty_text_";
    static final String KEY_T9_TEXT_PREFIX = "t9_text_";
    static final String LABEL_HIDDEN = "__HIDDEN__";

    static final String KEY_T9_2 = "t9_key_2";
    static final String KEY_T9_3 = "t9_key_3";
    static final String KEY_T9_4 = "t9_key_4";
    static final String KEY_T9_5 = "t9_key_5";
    static final String KEY_T9_6 = "t9_key_6";
    static final String KEY_T9_7 = "t9_key_7";
    static final String KEY_T9_8 = "t9_key_8";
    static final String KEY_T9_9 = "t9_key_9";

    static final int ACTION_NONE = 0;
    static final int ACTION_SELECT_ALL = 1;
    static final int ACTION_CUT = 2;
    static final int ACTION_COPY = 3;
    static final int ACTION_PASTE = 4;
    // Keep 5 stable for backward compatibility with existing saved T9 mappings.
    static final int ACTION_DISABLE = 5;
    static final int ACTION_PARAGRAPH_START = 6;
    static final int ACTION_PARAGRAPH_END = 7;
    static final int ACTION_SELECT_TO_PARAGRAPH_START = 8;
    static final int ACTION_SELECT_TO_PARAGRAPH_END = 9;
    static final int ACTION_OPEN_CLIPBOARD = 10;
    static final int ACTION_OPEN_QUICK_PHRASE = 11;
    static final int ACTION_COPY_ALL = 12;
    static final int ACTION_CUT_ALL = 13;
    static final int ACTION_UNDO = 14;
    static final int ACTION_REDO = 15;
    static final int ACTION_DOCUMENT_START = 16;
    static final int ACTION_DOCUMENT_END = 17;
    static final int ACTION_SELECT_TO_DOCUMENT_START = 18;
    static final int ACTION_SELECT_TO_DOCUMENT_END = 19;
    static final int ACTION_INSERT_TEXT = 20;
    static final int ACTION_NEXT_INPUT_METHOD = 21;
    static final int ACTION_PREVIOUS_INPUT_METHOD = 22;
    static final int ACTION_SHOW_INPUT_METHOD_PICKER = 23;
    // 微信场景快捷发送：① 粘贴剪贴板内容并发送 ② 直接发送输入框已有内容
    static final int ACTION_PASTE_AND_SEND = 24;
    static final int ACTION_SEND_EXISTING = 25;
    /** 大小写模式切换：在大写锁定与小写之间切换（内部仍走 ShiftState 状态机）。 */
    static final int ACTION_TOGGLE_CAPS_LOCK = 26;

    // ---- 滑动方向 ----
    /** 方向索引，顺序固定，序列化后缀见 DIR_SUFFIX。 */
    static final int DIR_DOWN = 0;
    static final int DIR_UP = 1;
    static final int DIR_LEFT = 2;
    static final int DIR_RIGHT = 3;
    static final int DIR_COUNT = 4;
    /** 方向选择列表的展示顺序：上、下、左、右。 */
    static final int[] DIR_DISPLAY_ORDER = {DIR_UP, DIR_DOWN, DIR_LEFT, DIR_RIGHT};
    /** 下滑沿用旧存储键（无后缀）以兼容历史配置。 */
    static final String[] DIR_SUFFIX = {"", "@up", "@left", "@right"};
    static final String[] DIR_NAMES = {"下滑", "上滑", "左滑", "右滑"};
    static final String[] DIR_SHORT = {"↓", "↑", "←", "→"};

    // ---- 特殊键（非字母/数字，小布自带原生手势，需要按方向屏蔽）----
    // keyId 与 KeyResolver 的 keyCode 映射一一对应，顺序即设置界面展示顺序。
    static final String KEY_DEL = "del";
    static final String KEY_ENTER = "enter";
    static final String KEY_SPACE = "space";
    static final String KEY_SHIFT = "shift";
    static final String KEY_SYMBOL = "symbol";
    static final String KEY_NUMBER = "number";
    static final String KEY_COMMA = "comma";
    static final String KEY_PERIOD = "period";
    static final String KEY_LANG = "lang";
    static final String KEY_CLEAR = "clear";
    static final String KEY_SYMBOLS1 = "symbols1";
    static final String KEY_QUESTION = "question";
    static final String KEY_EXCLAMATION = "exclamation";

    static final String[] SPECIAL_KEYS = {
            KEY_DEL, KEY_ENTER, KEY_SPACE, KEY_SHIFT, KEY_SYMBOL, KEY_NUMBER,
            KEY_COMMA, KEY_PERIOD, KEY_LANG, KEY_CLEAR, KEY_SYMBOLS1,
            KEY_QUESTION, KEY_EXCLAMATION
    };
    static final String[] SPECIAL_LABELS = {
            "退格键", "回车键", "空格键", "大小写切换键", "符号键", "123 键",
            "逗号键", "句号键", "拼/英键", "重输键", "@# 键",
            "问号键", "感叹号键"
    };
    /**
     * 键面图标（纯文本），尽量对齐小布键面实际显示：
     * 退格=⌫、回车=「换行」文字、Shift=⇧、收起键盘=▾、表情=☺（非彩色 emoji）。
     */
    static final String[] SPECIAL_GLYPHS = {
            "⌫", "换行", "空格", "⇧", "符号", "123",
            "，", "。", "拼/英", "重输", "@#",
            "？", "！"
    };
    /** 该键出现的键盘模式：0=两种都有，1=仅 26 键，2=仅九宫格。 */
    static final int MODE_BOTH = 0;
    static final int MODE_QWERTY = 1;
    static final int MODE_T9 = 2;
    static final int[] SPECIAL_MODES = {
            MODE_BOTH,   // del
            MODE_BOTH,   // enter
            MODE_BOTH,   // space
            MODE_QWERTY, // shift
            MODE_BOTH,   // symbol
            MODE_BOTH,   // number
            MODE_BOTH,   // comma
            MODE_BOTH,   // period
            MODE_BOTH,   // lang
            MODE_T9,     // clear（重输）
            MODE_T9,     // symbols1（@#）
            MODE_T9,     // question
            MODE_T9,     // exclamation
    };
    static final String SPECIAL_STORAGE_PREFIX = "special_";
    static final String SPECIAL_TEXT_PREFIX = "special_text_";
    static final String DISTANCE_STORAGE_PREFIX = "dist_";

    static boolean specialVisibleInT9(int index) {
        if (index < 0 || index >= SPECIAL_MODES.length) return false;
        return SPECIAL_MODES[index] != MODE_QWERTY;
    }

    static boolean specialVisibleInQwerty(int index) {
        if (index < 0 || index >= SPECIAL_MODES.length) return false;
        return SPECIAL_MODES[index] != MODE_T9;
    }

    static final String[] ACTION_MENU_LABELS = ActionRegistry.menuLabels();

    String selectAll = "z";
    String cut = "x";
    String copy = "c";
    String paste = "v";
    String copyAll = "";
    String cutAll = "";
    String paragraphStart = "";
    String paragraphEnd = "";
    String selectToParagraphStart = "";
    String selectToParagraphEnd = "";
    String documentStart = "";
    String documentEnd = "";
    String selectToDocumentStart = "";
    String selectToDocumentEnd = "";
    String openClipboard = "";
    String openQuickPhrase = "";
    String undo = "";
    String redo = "";
    String nextInputMethod = "";
    String previousInputMethod = "";
    String showInputMethodPicker = "";
    String pasteAndSend = "";
    String sendExisting = "";
    String disabledKeys = "";
    // 触发距离（dp）范围。九宫格上限放宽到 200dp，26 键沿用原范围。
    static final int MIN_THRESHOLD_DP = 6;
    static final int MAX_THRESHOLD_DP = 40;
    static final int DEFAULT_THRESHOLD_DP = 12;
    static final int MIN_T9_THRESHOLD_DP = 10;
    static final int MAX_T9_THRESHOLD_DP = 200;
    static final int DEFAULT_T9_THRESHOLD_DP = 20;

    int thresholdDp = DEFAULT_THRESHOLD_DP;
    int t9ThresholdDp = DEFAULT_T9_THRESHOLD_DP;
    boolean vibration = true;
    boolean showKeyLabels = true;
    boolean showTriggerHint = true;
    int revision = 0;

    final int[] t9Actions = new int[10];
    final String[] qwertyLabels = new String[26];
    final String[] t9Labels = new String[10];
    final String[] qwertyTexts = new String[26];
    final String[] t9Texts = new String[10];

    /** [字母 a..z][方向] → 绑定动作。 */
    final int[][] qwertyDirActions = new int[26][DIR_COUNT];
    /** [数字 0..9][方向] → 绑定动作（仅 2..9 有效）。 */
    final int[][] t9DirActions = new int[10][DIR_COUNT];
    /** [特殊键 del/enter/space][方向] → 绑定动作。 */
    final int[][] specialDirActions = new int[SPECIAL_KEYS.length][DIR_COUNT];
    /** [键][方向] → ACTION_INSERT_TEXT 要插入的文本（各方向独立）。 */
    final String[][] qwertyDirTexts = new String[26][DIR_COUNT];
    final String[][] t9DirTexts = new String[10][DIR_COUNT];
    final String[][] specialDirTexts = new String[SPECIAL_KEYS.length][DIR_COUNT];

    /** [键][方向] → 触发距离(dp)，0 表示回退全局默认。 */
    final int[][] qwertyDirDistances = new int[26][DIR_COUNT];
    final int[][] t9DirDistances = new int[10][DIR_COUNT];
    final int[][] specialDirDistances = new int[SPECIAL_KEYS.length][DIR_COUNT];

    /** 非下滑方向的绑定：key = action * DIR_COUNT + dir，value = 键 id（读配置时的中间态）。 */
    final java.util.HashMap<Integer, String> directionalBindings = new java.util.HashMap<>();

    private boolean hasAnyBinding;

    void rebuildActionMap() {
        for (int i = 0; i < 26; i++) Arrays.fill(qwertyDirActions[i], ACTION_NONE);
        for (int i = 0; i < 10; i++) Arrays.fill(t9DirActions[i], ACTION_NONE);
        for (int i = 0; i < SPECIAL_KEYS.length; i++) {
            Arrays.fill(specialDirActions[i], ACTION_NONE);
        }

        // 1) 旧版单值字段只代表下滑方向
        for (int action : ActionRegistry.qwertyActions()) {
            bindKeyId(ActionRegistry.keyFor(this, action), action, DIR_DOWN);
        }
        // 2) 九宫格旧字段同样落下滑
        for (int digit = 2; digit <= 9; digit++) {
            int action = validAction(t9Actions[digit]);
            if (action != ACTION_NONE) t9DirActions[digit][DIR_DOWN] = action;
        }
        // 3) 其他方向的绑定
        for (java.util.Map.Entry<Integer, String> entry : directionalBindings.entrySet()) {
            int packed = entry.getKey();
            bindKeyId(entry.getValue(), packed / DIR_COUNT, packed % DIR_COUNT);
        }
        // 4) 输入指定文本：每个方向独立判定
        for (int i = 0; i < 26; i++) {
            for (int dir = 0; dir < DIR_COUNT; dir++) {
                if (!normalizeInsertedText(qwertyDirTexts[i][dir]).isEmpty()) {
                    qwertyDirActions[i][dir] = ACTION_INSERT_TEXT;
                }
            }
        }
        for (int i = 0; i < 10; i++) {
            for (int dir = 0; dir < DIR_COUNT; dir++) {
                if (!normalizeInsertedText(t9DirTexts[i][dir]).isEmpty()) {
                    t9DirActions[i][dir] = ACTION_INSERT_TEXT;
                }
            }
        }
        for (int i = 0; i < SPECIAL_KEYS.length; i++) {
            for (int dir = 0; dir < DIR_COUNT; dir++) {
                if (!normalizeInsertedText(specialDirTexts[i][dir]).isEmpty()) {
                    specialDirActions[i][dir] = ACTION_INSERT_TEXT;
                }
            }
        }
        // 5) 禁用键：四个方向全部标记为 DISABLE（同时屏蔽原生手势）
        bindDisabled(disabledKeys);

        hasAnyBinding = false;
        outer:
        for (int[][] table : new int[][][]{qwertyDirActions, t9DirActions, specialDirActions}) {
            for (int[] row : table) {
                for (int action : row) {
                    if (action != ACTION_NONE) {
                        hasAnyBinding = true;
                        break outer;
                    }
                }
            }
        }
    }

    /**
     * 把 keyId + 方向 绑定到动作；keyId 可为字母、数字或 del/enter/space。
     * 支持逗号分隔的多个键（同一功能可被多个按键共用）。
     */
    void bindKeyId(String keyId, int action, int dir) {
        if (keyId == null || dir < 0 || dir >= DIR_COUNT) return;
        for (String token : splitKeyIds(keyId)) {
            bindSingleKey(token, action, dir);
        }
    }

    private void bindSingleKey(String id, int action, int dir) {
        if (id.isEmpty()) return;
        if (id.length() == 1) {
            char c = id.charAt(0);
            if (c >= 'a' && c <= 'z') {
                qwertyDirActions[c - 'a'][dir] = action;
                return;
            }
            if (c >= '2' && c <= '9') {
                t9DirActions[c - '0'][dir] = action;
            }
            return;
        }
        int special = specialIndex(id);
        if (special >= 0) specialDirActions[special][dir] = action;
    }

    /** 拆分逗号（或空白）分隔的键 id 列表。 */
    static String[] splitKeyIds(String raw) {
        if (raw == null || raw.trim().isEmpty()) return new String[0];
        String[] parts = raw.trim().toLowerCase(Locale.ROOT).split("[,，\\s]+");
        java.util.List<String> kept = new java.util.ArrayList<>(parts.length);
        for (String part : parts) {
            if (part != null && !part.isEmpty()) kept.add(part);
        }
        return kept.toArray(new String[0]);
    }

    /** 同一动作 + 方向可挂多个键：读取时把键追加进同一个槽位。 */
    void addDirectionalBinding(int action, int dir, String keyId) {
        if (keyId == null || dir < 0 || dir >= DIR_COUNT) return;
        for (String token : splitKeyIds(keyId)) {
            int packed = action * DIR_COUNT + dir;
            String existing = directionalBindings.get(packed);
            if (existing == null || existing.isEmpty()) {
                directionalBindings.put(packed, token);
            } else if (!("," + existing + ",").contains("," + token + ",")) {
                directionalBindings.put(packed, existing + "," + token);
            }
        }
    }

    private void bindDisabled(String keys) {
        if (keys == null) return;
        String normalized = keys.toLowerCase(Locale.ROOT);
        for (int i = 0; i < normalized.length(); i++) {
            char c = normalized.charAt(i);
            if (c < 'a' || c > 'z') continue;
            int index = c - 'a';
            for (int dir = 0; dir < DIR_COUNT; dir++) {
                if (qwertyDirActions[index][dir] == ACTION_NONE) {
                    qwertyDirActions[index][dir] = ACTION_DISABLE;
                }
            }
        }
    }

    static int specialIndex(String keyId) {
        if (keyId == null) return -1;
        for (int i = 0; i < SPECIAL_KEYS.length; i++) {
            if (SPECIAL_KEYS[i].equals(keyId)) return i;
        }
        return -1;
    }

    static String specialLabel(String keyId) {
        int index = specialIndex(keyId);
        return index >= 0 ? SPECIAL_LABELS[index] : keyId;
    }

    /** 查询某键某方向的绑定动作。 */
    int actionFor(String key, boolean t9, int dir) {
        if (dir < 0 || dir >= DIR_COUNT) dir = DIR_DOWN;
        if (key == null || key.isEmpty()) return ACTION_NONE;
        String id = key.trim().toLowerCase(Locale.ROOT);
        if (id.length() == 1) {
            char c = id.charAt(0);
            if (c >= 'a' && c <= 'z') return qwertyDirActions[c - 'a'][dir];
            if (c >= '2' && c <= '9' && t9) return t9DirActions[c - '0'][dir];
            return ACTION_NONE;
        }
        int special = specialIndex(id);
        return special >= 0 ? specialDirActions[special][dir] : ACTION_NONE;
    }

    int actionFor(String key, boolean t9) {
        return actionFor(key, t9, DIR_DOWN);
    }

    /** 该键是否有任意方向绑定（无绑定时整个触摸序列交还原生）。 */
    boolean hasAnyDirectionBinding(String key, boolean t9) {
        for (int dir = 0; dir < DIR_COUNT; dir++) {
            if (actionFor(key, t9, dir) != ACTION_NONE) return true;
        }
        return false;
    }

    /** 某键某方向的生效触发距离（dp）：优先按键级设置，否则用全局默认。 */
    int distanceFor(String key, boolean t9, int dir) {
        int custom = 0;
        if (key != null && !key.isEmpty()) {
            if (dir < 0 || dir >= DIR_COUNT) dir = DIR_DOWN;
            String id = key.trim().toLowerCase(Locale.ROOT);
            if (id.length() == 1) {
                char c = id.charAt(0);
                if (c >= 'a' && c <= 'z') custom = qwertyDirDistances[c - 'a'][dir];
                else if (c >= '2' && c <= '9') custom = t9DirDistances[c - '0'][dir];
            } else {
                int special = specialIndex(id);
                if (special >= 0) custom = specialDirDistances[special][dir];
            }
        }
        if (custom > 0) return custom;
        return t9 ? t9ThresholdDp : thresholdDp;
    }

    /** 设置某键某方向的按键级距离；dp<=0 表示恢复全局默认。 */
    void setKeyDistance(String key, boolean t9, int dir, int dp) {
        if (key == null || dir < 0 || dir >= DIR_COUNT) return;
        String id = key.trim().toLowerCase(Locale.ROOT);
        int value = Math.max(0, dp);
        if (id.length() == 1) {
            char c = id.charAt(0);
            if (c >= 'a' && c <= 'z') qwertyDirDistances[c - 'a'][dir] = value;
            else if (c >= '2' && c <= '9') t9DirDistances[c - '0'][dir] = value;
            return;
        }
        int special = specialIndex(id);
        if (special >= 0) specialDirDistances[special][dir] = value;
    }

    /** 设置某键某方向的绑定动作（UI 直接改表）。 */
    void setKeyAction(String key, boolean t9, int dir, int action) {
        if (key == null || dir < 0 || dir >= DIR_COUNT) return;
        String id = key.trim().toLowerCase(Locale.ROOT);
        int value = validAction(action);
        if (id.length() == 1) {
            char c = id.charAt(0);
            if (c >= 'a' && c <= 'z') qwertyDirActions[c - 'a'][dir] = value;
            else if (c >= '2' && c <= '9') t9DirActions[c - '0'][dir] = value;
            return;
        }
        int special = specialIndex(id);
        if (special >= 0) specialDirActions[special][dir] = value;
    }

    boolean hasAnyBinding() {
        return hasAnyBinding;
    }

    static int validAction(int action) {
        return ActionRegistry.validAction(action);
    }

    static int[] menuActions() {
        return ActionRegistry.menuActions();
    }

    static int menuPositionForAction(int action) {
        return ActionRegistry.menuPositionForAction(action);
    }

    static int actionForMenuPosition(int position) {
        return ActionRegistry.actionForMenuPosition(position);
    }

    static String qwertyLabelPrefKey(char key) {
        if (key < 'a' || key > 'z') throw new IllegalArgumentException("key must be a..z");
        return KEY_QWERTY_LABEL_PREFIX + key;
    }

    static String qwertyTextPrefKey(char key) {
        if (key < 'a' || key > 'z') throw new IllegalArgumentException("key must be a..z");
        return KEY_QWERTY_TEXT_PREFIX + key;
    }

    static String t9TextPrefKey(int digit) {
        if (digit < 2 || digit > 9) throw new IllegalArgumentException("digit must be 2..9");
        return KEY_T9_TEXT_PREFIX + digit;
    }

    static String t9LabelPrefKey(int digit) {
        if (digit < 2 || digit > 9) throw new IllegalArgumentException("digit must be 2..9");
        return KEY_T9_LABEL_PREFIX + digit;
    }

    static String normalizeLabelValue(String value) {
        if (value == null) return "";
        if (LABEL_HIDDEN.equals(value)) return LABEL_HIDDEN;
        String clean = value.replace('\n', ' ').replace('\r', ' ').trim();
        int count = clean.codePointCount(0, clean.length());
        if (count > 4) clean = clean.substring(0, clean.offsetByCodePoints(0, 4));
        return clean;
    }

    static String normalizeInsertedText(String value) {
        if (value == null) return "";
        String clean = value.replace("\u0000", "");
        int count = clean.codePointCount(0, clean.length());
        if (count > 1000) clean = clean.substring(0, clean.offsetByCodePoints(0, 1000));
        return clean;
    }

    String textFor(String key, boolean t9) {
        return textFor(key, t9, DIR_DOWN);
    }

    /** 读取某键某方向的插入文本。 */
    String textFor(String key, boolean t9, int dir) {
        if (key == null || key.isEmpty()) return "";
        if (dir < 0 || dir >= DIR_COUNT) dir = DIR_DOWN;
        String id = key.trim().toLowerCase(Locale.ROOT);
        if (id.length() == 1) {
            char value = id.charAt(0);
            if (value >= 'a' && value <= 'z') {
                return normalizeInsertedText(qwertyDirTexts[value - 'a'][dir]);
            }
            if (t9 && value >= '2' && value <= '9') {
                return normalizeInsertedText(t9DirTexts[value - '0'][dir]);
            }
            return "";
        }
        int special = specialIndex(id);
        return special >= 0 ? normalizeInsertedText(specialDirTexts[special][dir]) : "";
    }

    /** 写入某键某方向的插入文本（空字符串表示清除）。 */
    void setKeyText(String key, boolean t9, int dir, String text) {
        if (key == null || key.isEmpty()) return;
        if (dir < 0 || dir >= DIR_COUNT) dir = DIR_DOWN;
        String id = key.trim().toLowerCase(Locale.ROOT);
        String value = normalizeInsertedText(text);
        if (id.length() == 1) {
            char c = id.charAt(0);
            if (c >= 'a' && c <= 'z') {
                qwertyDirTexts[c - 'a'][dir] = value;
                if (dir == DIR_DOWN) qwertyTexts[c - 'a'] = value;
                return;
            }
            if (c >= '2' && c <= '9' && t9) {
                t9DirTexts[c - '0'][dir] = value;
                if (dir == DIR_DOWN) t9Texts[c - '0'] = value;
                return;
            }
            return;
        }
        int special = specialIndex(id);
        if (special >= 0) specialDirTexts[special][dir] = value;
    }

    String labelFor(String key, boolean t9, int action) {
        String configured = "";
        if (key != null && key.length() == 1) {
            char value = Character.toLowerCase(key.charAt(0));
            if (t9 && value >= '2' && value <= '9') configured = t9Labels[value - '0'];
            else if (!t9 && value >= 'a' && value <= 'z') configured = qwertyLabels[value - 'a'];
        }
        configured = normalizeLabelValue(configured);
        if (LABEL_HIDDEN.equals(configured)) return "";
        if (!configured.isEmpty()) return configured;
        return shortActionLabel(action);
    }

    static String shortActionLabel(int action) {
        return ActionRegistry.shortLabel(action);
    }

    static String t9PrefKey(int digit) {
        switch (digit) {
            case 2: return KEY_T9_2;
            case 3: return KEY_T9_3;
            case 4: return KEY_T9_4;
            case 5: return KEY_T9_5;
            case 6: return KEY_T9_6;
            case 7: return KEY_T9_7;
            case 8: return KEY_T9_8;
            case 9: return KEY_T9_9;
            default: throw new IllegalArgumentException("digit must be 2..9");
        }
    }

    static String t9Label(int digit) {
        switch (digit) {
            case 2: return "2 / ABC";
            case 3: return "3 / DEF";
            case 4: return "4 / GHI";
            case 5: return "5 / JKL";
            case 6: return "6 / MNO";
            case 7: return "7 / PQRS";
            case 8: return "8 / TUV";
            case 9: return "9 / WXYZ";
            default: return String.valueOf(digit);
        }
    }

    static int menuIdFor(int action) {
        if (action == ACTION_SELECT_ALL) return android.R.id.selectAll;
        if (action == ACTION_CUT) return android.R.id.cut;
        if (action == ACTION_COPY) return android.R.id.copy;
        if (action == ACTION_PASTE) return android.R.id.paste;
        if (action == ACTION_UNDO) return android.R.id.undo;
        if (action == ACTION_REDO) return android.R.id.redo;
        return 0;
    }

    static String actionName(int action) {
        return ActionRegistry.name(action);
    }
}
