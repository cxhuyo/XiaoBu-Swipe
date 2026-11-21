package app.xiaobuswipe;

import android.content.Intent;
import android.content.SharedPreferences;

/** Centralized serialization for module prefs, target-process cache and broadcasts. */
final class ConfigCodec {
    interface Reader {
        String getString(String key, String fallback);
        int getInt(String key, int fallback);
        boolean getBoolean(String key, boolean fallback);
    }

    interface Writer {
        void putString(String key, String value);
        void putInt(String key, int value);
        void putBoolean(String key, boolean value);
    }

    private ConfigCodec() {}

    static Config fromPreferences(SharedPreferences preferences) {
        return read(new Reader() {
            @Override public String getString(String key, String fallback) {
                return preferences.getString(key, fallback);
            }
            @Override public int getInt(String key, int fallback) {
                return preferences.getInt(key, fallback);
            }
            @Override public boolean getBoolean(String key, boolean fallback) {
                return preferences.getBoolean(key, fallback);
            }
        });
    }

    static void writeToPreferences(SharedPreferences.Editor editor, Config config) {
        write(new Writer() {
            @Override public void putString(String key, String value) { editor.putString(key, value); }
            @Override public void putInt(String key, int value) { editor.putInt(key, value); }
            @Override public void putBoolean(String key, boolean value) { editor.putBoolean(key, value); }
        }, config);
    }

    static Config fromIntent(Intent intent) {
        return read(new Reader() {
            @Override public String getString(String key, String fallback) {
                String value = intent.getStringExtra(key);
                return value == null ? fallback : value;
            }
            @Override public int getInt(String key, int fallback) {
                return intent.getIntExtra(key, fallback);
            }
            @Override public boolean getBoolean(String key, boolean fallback) {
                return intent.getBooleanExtra(key, fallback);
            }
        });
    }

    static void putIntoIntent(Intent intent, Config config) {
        intent.putExtra(Config.EXTRA_SNAPSHOT, true);
        write(new Writer() {
            @Override public void putString(String key, String value) { intent.putExtra(key, value); }
            @Override public void putInt(String key, int value) { intent.putExtra(key, value); }
            @Override public void putBoolean(String key, boolean value) { intent.putExtra(key, value); }
        }, config);
    }

    static Config read(Reader reader) {
        Config config = new Config();
        // 下滑沿用旧键名，保持历史配置可读；其余方向用 <storageKey>@up|@left|@right。
        for (int action : ActionRegistry.qwertyActions()) {
            String storageKey = ActionRegistry.storageKey(action);
            if (storageKey == null) continue;
            String fallback = ActionRegistry.keyFor(config, action);
            ActionRegistry.setKey(config, action, reader.getString(storageKey, fallback));
            for (int dir = 1; dir < Config.DIR_COUNT; dir++) {
                String value = reader.getString(storageKey + Config.DIR_SUFFIX[dir], "");
                config.addDirectionalBinding(action, dir, value);
            }
        }
        // 特殊键（退格/回车/空格）：结构为 special_<keyId>[@dir] = actionId
        for (int i = 0; i < Config.SPECIAL_KEYS.length; i++) {
            String id = Config.SPECIAL_KEYS[i];
            for (int dir = 0; dir < Config.DIR_COUNT; dir++) {
                int action = reader.getInt(
                        Config.SPECIAL_STORAGE_PREFIX + id + Config.DIR_SUFFIX[dir], 0);
                if (action != 0) {
                    config.addDirectionalBinding(action, dir, id);
                }
            }
        }
        // 按键级触发距离：dist_<keyId>[@dir] = dp（0/缺失 = 跟随全局默认）
        for (char c = 'a'; c <= 'z'; c++) {
            for (int dir = 0; dir < Config.DIR_COUNT; dir++) {
                config.qwertyDirDistances[c - 'a'][dir] = reader.getInt(
                        Config.DISTANCE_STORAGE_PREFIX + c + Config.DIR_SUFFIX[dir], 0);
            }
        }
        for (int digit = 2; digit <= 9; digit++) {
            for (int dir = 0; dir < Config.DIR_COUNT; dir++) {
                config.t9DirDistances[digit][dir] = reader.getInt(
                        Config.DISTANCE_STORAGE_PREFIX + digit + Config.DIR_SUFFIX[dir], 0);
            }
        }
        for (int i = 0; i < Config.SPECIAL_KEYS.length; i++) {
            for (int dir = 0; dir < Config.DIR_COUNT; dir++) {
                config.specialDirDistances[i][dir] = reader.getInt(
                        Config.DISTANCE_STORAGE_PREFIX + Config.SPECIAL_KEYS[i]
                                + Config.DIR_SUFFIX[dir], 0);
            }
        }

        config.disabledKeys = reader.getString(Config.KEY_DISABLED_KEYS, "");
        config.thresholdDp = clamp(reader.getInt(Config.KEY_THRESHOLD, Config.DEFAULT_THRESHOLD_DP),
                Config.MIN_THRESHOLD_DP, Config.MAX_THRESHOLD_DP, Config.DEFAULT_THRESHOLD_DP);
        config.t9ThresholdDp = clamp(reader.getInt(Config.KEY_T9_THRESHOLD, Config.DEFAULT_T9_THRESHOLD_DP),
                Config.MIN_T9_THRESHOLD_DP, Config.MAX_T9_THRESHOLD_DP,
                Config.DEFAULT_T9_THRESHOLD_DP);
        config.vibration = reader.getBoolean(Config.KEY_VIBRATION, true);
        config.showKeyLabels = reader.getBoolean(Config.KEY_SHOW_KEY_LABELS, true);
        config.showTriggerHint = reader.getBoolean(Config.KEY_SHOW_TRIGGER_HINT, true);
        config.revision = reader.getInt(Config.KEY_REVISION, 0);
        for (char key = 'a'; key <= 'z'; key++) {
            int index = key - 'a';
            config.qwertyLabels[index] = Config.normalizeLabelValue(
                    reader.getString(Config.qwertyLabelPrefKey(key), ""));
            String base = Config.qwertyTextPrefKey(key);
            String down = Config.normalizeInsertedText(reader.getString(base, ""));
            config.qwertyTexts[index] = down;
            config.qwertyDirTexts[index][Config.DIR_DOWN] = down;
            for (int dir = 1; dir < Config.DIR_COUNT; dir++) {
                config.qwertyDirTexts[index][dir] = Config.normalizeInsertedText(
                        reader.getString(base + Config.DIR_SUFFIX[dir], ""));
            }
        }
        for (int digit = 2; digit <= 9; digit++) {
            config.t9Actions[digit] = Config.validAction(
                    reader.getInt(Config.t9PrefKey(digit), Config.ACTION_NONE));
            config.t9Labels[digit] = Config.normalizeLabelValue(
                    reader.getString(Config.t9LabelPrefKey(digit), ""));
            String base = Config.t9TextPrefKey(digit);
            String down = Config.normalizeInsertedText(reader.getString(base, ""));
            config.t9Texts[digit] = down;
            config.t9DirTexts[digit][Config.DIR_DOWN] = down;
            for (int dir = 1; dir < Config.DIR_COUNT; dir++) {
                config.t9DirTexts[digit][dir] = Config.normalizeInsertedText(
                        reader.getString(base + Config.DIR_SUFFIX[dir], ""));
            }
        }
        for (int i = 0; i < Config.SPECIAL_KEYS.length; i++) {
            String base = Config.SPECIAL_TEXT_PREFIX + Config.SPECIAL_KEYS[i];
            for (int dir = 0; dir < Config.DIR_COUNT; dir++) {
                config.specialDirTexts[i][dir] = Config.normalizeInsertedText(
                        reader.getString(base + Config.DIR_SUFFIX[dir], ""));
            }
        }
        config.rebuildActionMap();
        return config;
    }

    static void write(Writer writer, Config config) {
        if (config == null) config = new Config();
        // 动作绑定按「方向 + 键」反查后落盘；下滑写旧键名以兼容旧版本读取。
        for (int action : ActionRegistry.qwertyActions()) {
            String storageKey = ActionRegistry.storageKey(action);
            if (storageKey == null) continue;
            writer.putString(storageKey, keyIdFor(config, action, Config.DIR_DOWN));
            for (int dir = 1; dir < Config.DIR_COUNT; dir++) {
                writer.putString(storageKey + Config.DIR_SUFFIX[dir],
                        keyIdFor(config, action, dir));
            }
        }
        for (int i = 0; i < Config.SPECIAL_KEYS.length; i++) {
            String id = Config.SPECIAL_KEYS[i];
            for (int dir = 0; dir < Config.DIR_COUNT; dir++) {
                writer.putInt(Config.SPECIAL_STORAGE_PREFIX + id + Config.DIR_SUFFIX[dir],
                        config.specialDirActions[i][dir]);
            }
        }
        for (char c = 'a'; c <= 'z'; c++) {
            for (int dir = 0; dir < Config.DIR_COUNT; dir++) {
                int dp = config.qwertyDirDistances[c - 'a'][dir];
                if (dp > 0) {
                    writer.putInt(Config.DISTANCE_STORAGE_PREFIX
                            + c + Config.DIR_SUFFIX[dir], dp);
                }
            }
        }
        for (int digit = 2; digit <= 9; digit++) {
            for (int dir = 0; dir < Config.DIR_COUNT; dir++) {
                int dp = config.t9DirDistances[digit][dir];
                if (dp > 0) {
                    writer.putInt(Config.DISTANCE_STORAGE_PREFIX
                            + digit + Config.DIR_SUFFIX[dir], dp);
                }
            }
        }
        for (int i = 0; i < Config.SPECIAL_KEYS.length; i++) {
            for (int dir = 0; dir < Config.DIR_COUNT; dir++) {
                int dp = config.specialDirDistances[i][dir];
                if (dp > 0) {
                    writer.putInt(Config.DISTANCE_STORAGE_PREFIX
                            + Config.SPECIAL_KEYS[i] + Config.DIR_SUFFIX[dir], dp);
                }
            }
        }

        writer.putString(Config.KEY_DISABLED_KEYS, config.disabledKeys == null ? "" : config.disabledKeys);
        writer.putInt(Config.KEY_THRESHOLD, config.thresholdDp);
        writer.putInt(Config.KEY_T9_THRESHOLD, config.t9ThresholdDp);
        writer.putBoolean(Config.KEY_VIBRATION, config.vibration);
        writer.putBoolean(Config.KEY_SHOW_KEY_LABELS, config.showKeyLabels);
        writer.putBoolean(Config.KEY_SHOW_TRIGGER_HINT, config.showTriggerHint);
        writer.putInt(Config.KEY_REVISION, config.revision);
        for (char key = 'a'; key <= 'z'; key++) {
            int index = key - 'a';
            writer.putString(Config.qwertyLabelPrefKey(key),
                    Config.normalizeLabelValue(config.qwertyLabels[index]));
            String base = Config.qwertyTextPrefKey(key);
            writer.putString(base, Config.normalizeInsertedText(
                    config.qwertyDirTexts[index][Config.DIR_DOWN]));
            for (int dir = 1; dir < Config.DIR_COUNT; dir++) {
                writer.putString(base + Config.DIR_SUFFIX[dir], Config.normalizeInsertedText(
                        config.qwertyDirTexts[index][dir]));
            }
        }
        for (int digit = 2; digit <= 9; digit++) {
            writer.putInt(Config.t9PrefKey(digit), config.t9Actions[digit]);
            writer.putString(Config.t9LabelPrefKey(digit),
                    Config.normalizeLabelValue(config.t9Labels[digit]));
            String t9Base = Config.t9TextPrefKey(digit);
            writer.putString(t9Base, Config.normalizeInsertedText(
                    config.t9DirTexts[digit][Config.DIR_DOWN]));
            for (int dir = 1; dir < Config.DIR_COUNT; dir++) {
                writer.putString(t9Base + Config.DIR_SUFFIX[dir], Config.normalizeInsertedText(
                        config.t9DirTexts[digit][dir]));
            }
        }
        for (int i = 0; i < Config.SPECIAL_KEYS.length; i++) {
            String base = Config.SPECIAL_TEXT_PREFIX + Config.SPECIAL_KEYS[i];
            for (int dir = 0; dir < Config.DIR_COUNT; dir++) {
                writer.putString(base + Config.DIR_SUFFIX[dir], Config.normalizeInsertedText(
                        config.specialDirTexts[i][dir]));
            }
        }
    }

    /** 反查某动作在某方向绑定的键 id；DISABLE 由 disabled_keys 单独承载，故返回空。 */
    /** 收集某动作在某方向上的全部按键，多个键用逗号连接（同一功能可绑定多个按键）。 */
    private static String keyIdFor(Config config, int action, int dir) {
        if (action == Config.ACTION_NONE || action == Config.ACTION_DISABLE) return "";
        StringBuilder keys = new StringBuilder();
        for (int i = 0; i < 26; i++) {
            if (config.qwertyDirActions[i][dir] == action) {
                appendKey(keys, String.valueOf((char) ('a' + i)));
            }
        }
        for (int digit = 2; digit <= 9; digit++) {
            if (config.t9DirActions[digit][dir] == action) {
                appendKey(keys, String.valueOf((char) ('0' + digit)));
            }
        }
        for (int i = 0; i < Config.SPECIAL_KEYS.length; i++) {
            if (config.specialDirActions[i][dir] == action) {
                appendKey(keys, Config.SPECIAL_KEYS[i]);
            }
        }
        return keys.toString();
    }

    private static void appendKey(StringBuilder builder, String key) {
        if (builder.length() > 0) builder.append(',');
        builder.append(key);
    }

    static Config copyOf(Config source) {
        Config target = new Config();
        if (source == null) {
            target.rebuildActionMap();
            return target;
        }
        for (int action : ActionRegistry.qwertyActions()) {
            ActionRegistry.setKey(target, action, ActionRegistry.keyFor(source, action));
        }
        target.disabledKeys = source.disabledKeys;
        target.thresholdDp = source.thresholdDp;
        target.t9ThresholdDp = source.t9ThresholdDp;
        target.vibration = source.vibration;
        target.showKeyLabels = source.showKeyLabels;
        target.showTriggerHint = source.showTriggerHint;
        target.revision = source.revision;
        System.arraycopy(source.t9Actions, 0, target.t9Actions, 0, source.t9Actions.length);
        System.arraycopy(source.qwertyLabels, 0, target.qwertyLabels, 0, source.qwertyLabels.length);
        System.arraycopy(source.t9Labels, 0, target.t9Labels, 0, source.t9Labels.length);
        System.arraycopy(source.qwertyTexts, 0, target.qwertyTexts, 0, source.qwertyTexts.length);
        System.arraycopy(source.t9Texts, 0, target.t9Texts, 0, source.t9Texts.length);
        // 方向绑定与非下滑距离必须一并复制，否则快照广播会丢失方向配置
        target.directionalBindings.putAll(source.directionalBindings);
        for (int i = 0; i < 26; i++) {
            System.arraycopy(source.qwertyDirTexts[i], 0, target.qwertyDirTexts[i], 0, Config.DIR_COUNT);
        }
        for (int i = 0; i < 10; i++) {
            System.arraycopy(source.t9DirTexts[i], 0, target.t9DirTexts[i], 0, Config.DIR_COUNT);
        }
        for (int i = 0; i < Config.SPECIAL_KEYS.length; i++) {
            System.arraycopy(source.specialDirTexts[i], 0, target.specialDirTexts[i], 0, Config.DIR_COUNT);
        }
        for (int i = 0; i < 26; i++) {
            System.arraycopy(source.qwertyDirDistances[i], 0,
                    target.qwertyDirDistances[i], 0, Config.DIR_COUNT);
        }
        for (int i = 0; i < 10; i++) {
            System.arraycopy(source.t9DirDistances[i], 0,
                    target.t9DirDistances[i], 0, Config.DIR_COUNT);
        }
        for (int i = 0; i < Config.SPECIAL_KEYS.length; i++) {
            System.arraycopy(source.specialDirDistances[i], 0,
                    target.specialDirDistances[i], 0, Config.DIR_COUNT);
        }
        target.rebuildActionMap();
        return target;
    }

    private static int clamp(int value, int min, int max, int fallback) {
        return value < min || value > max ? fallback : value;
    }
}
