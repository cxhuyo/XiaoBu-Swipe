package app.xiaobuswipe;

import android.view.MotionEvent;
import android.view.View;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Locale;

/**
 * 将小布输入法的 SoftKey 模型 (com.oplus.keyboard.base.entity.e) 解析为稳定的 QWERTY/T9 键值。
 *
 * <p>1.8.35.17-mkt 已核实字段口径 (toString 反编译证据):
 * int a = keyCode (KeyCode.getValue(), 字母 A-Z=29..54, 数字 K0-K9=7..16);
 * int b/c = keyLeftX/keyRightX; int d/e = keyTopY/keyBottomY; String s = keyText。
 * 键盘模型 base.entity.f 的 public List l 保存当前布局全部 SoftKey。
 */
final class KeyResolver {
    private static final int KEYCODE_DIGIT_BASE = 7;   // K0..K9 -> 7..16 -> '0'..'9'
    private static final int KEYCODE_LETTER_BASE = 29; // A..Z   -> 29..54 -> 'a'..'z'
    private static final String KEYBOARD_TYPE_T9_PREFIX = "T9";

    private final Reflector reflector;

    KeyResolver(Reflector reflector) {
        this.reflector = reflector;
    }

    /** ACTION_DOWN 后解析当前按住的键: 对 getSoftKeyboard().l 做坐标命中测试。 */
    KeyInfo keyAfterDown(Object keyboard, MotionEvent event, boolean t9Mode) {
        return keyAtPoint(keyboard, event.getX(), event.getY(), t9Mode);
    }

    /** 给定键盘视图坐标解析按键；命中失败返回 null。数字键是否按九宫格处理由 t9Mode 决定。 */
    KeyInfo keyAtPoint(Object keyboard, float x, float y, boolean t9Mode) {
        Object model = keyboardModel(keyboard);
        if (model == null) return null;
        List<?> keys = softKeyList(model);
        if (keys == null || keys.isEmpty()) return null;
        int ix = Math.round(x);
        int iy = Math.round(y);
        for (Object key : keys) {
            if (key == null) continue;
            int left = intField(key, "b", -1);
            int right = intField(key, "c", -1);
            int top = intField(key, "d", -1);
            int bottom = intField(key, "e", -1);
            if (right <= left || bottom <= top) continue;
            if (ix < left || ix > right || iy < top || iy > bottom) continue;
            KeyInfo info = keyFromSoftKey(key, t9Mode);
            if (info != null) return info;
        }
        return null;
    }

    /** SoftKey -> KeyInfo； keyCode 优先，keyText 兜底。数字键仅 t9Mode 下产生绑定身份。 */
    KeyInfo keyFromSoftKey(Object softKey, boolean t9Mode) {
        if (softKey == null) return null;
        int keyCode = intField(softKey, "a", Integer.MIN_VALUE);
        KeyInfo byCode = keyFromKeyCode(keyCode, t9Mode);
        if (byCode != null) return byCode;
        Object text = field(softKey, "s");
        return keyFromText(text);
    }

    static KeyInfo keyFromKeyCode(int keyCode, boolean t9Mode) {
        String special = specialKeyId(keyCode);
        if (special != null) {
            return KeyInfo.special(special, Config.specialLabel(special));
        }
        if (keyCode >= KEYCODE_DIGIT_BASE && keyCode <= KEYCODE_DIGIT_BASE + 9) {
            String digit = String.valueOf((char) ('0' + keyCode - KEYCODE_DIGIT_BASE));
            return t9Mode && digit.charAt(0) >= '2' ? KeyInfo.t9(digit) : null;
        }
        if (keyCode >= KEYCODE_LETTER_BASE && keyCode <= KEYCODE_LETTER_BASE + 25) {
            return KeyInfo.alpha(String.valueOf(
                    (char) ('a' + keyCode - KEYCODE_LETTER_BASE)));
        }
        return null;
    }

    /**
     * 小布 KeyCode 枚举的 value → 稳定 keyId。
     * 正数取自 1.9.18.17-mkt 的 KeyCode 常量，负数为其内置功能键编码。
     */
    private static String specialKeyId(int keyCode) {
        switch (keyCode) {
            case 67: return Config.KEY_DEL;          // KeyCode.DEL
            case 66: return Config.KEY_ENTER;        // KeyCode.ENTER
            case 62: return Config.KEY_SPACE;        // KeyCode.SPACE
            case 28: return Config.KEY_CLEAR;        // KeyCode.CLEAR（重输）
            case 303: return Config.KEY_SYMBOLS1;    // KeyCode.SYMBOLS1（@# 面板）
            case -1: return Config.KEY_SHIFT;        // KeyCode.SHIFT
            case -2: return Config.KEY_SYMBOL;       // KeyCode.SYMBOL（符号）
            case -3: return Config.KEY_NUMBER;       // KeyCode.NUMBER（123）
            case -4: return Config.KEY_COMMA;        // KeyCode.COMMA
            case -5: return Config.KEY_PERIOD;       // KeyCode.PERIOD
            case -6: return Config.KEY_LANG;         // KeyCode.LANG（拼/英）
            case -11: return Config.KEY_QUESTION;    // KeyCode.QUESTION
            case -12: return Config.KEY_EXCLAMATION; // KeyCode.EXCLAMATION
            default: return null;
        }
    }

    /** 供标签绘制: 每次布局取全部可识别按键及其矩形。 */
    void collectKeys(Object keyboard, boolean t9Mode, KeySink sink) {
        Object model = keyboardModel(keyboard);
        if (model == null) return;
        List<?> keys = softKeyList(model);
        if (keys == null) return;
        for (Object key : keys) {
            if (key == null) continue;
            int left = intField(key, "b", -1);
            int right = intField(key, "c", -1);
            int top = intField(key, "d", -1);
            int bottom = intField(key, "e", -1);
            KeyInfo info = keyFromSoftKey(key, t9Mode);
            if (info == null || right <= left || bottom <= top) continue;
            sink.accept(info, left, top, right, bottom);
        }
    }

    interface KeySink {
        void accept(KeyInfo info, int left, int top, int right, int bottom);
    }

    /** 键盘模型 base.entity.f: getSoftKeyboard() 命名方法优先，防御式探测。 */
    private Object keyboardModel(Object keyboard) {
        if (keyboard == null) return null;
        String[] methodNames = {"getSoftKeyboard", "getNormalSoftKeyboard"};
        for (String name : methodNames) {
            Object value = reflector.invoke(keyboard, name);
            if (value != null) return value;
        }
        return null;
    }

    private static List<?> softKeyList(Object model) {
        Object value = readField(model, "l");
        return value instanceof List ? (List<?>) value : null;
    }

    private static Object readField(Object object, String name) {
        for (Class<?> type = object.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(object);
            } catch (NoSuchFieldException ignored) {
            } catch (Throwable ignored) {
                return null;
            }
        }
        return null;
    }

    private static Object field(Object object, String name) {
        return readField(object, name);
    }

    private static int intField(Object object, String name, int fallback) {
        Object value = readField(object, name);
        return value instanceof Number ? ((Number) value).intValue() : fallback;
    }

    /** keyText 兜底: 'a'-'z' 或九宫格数字。 */
    static KeyInfo keyFromText(Object value) {
        if (value == null) return null;
        String text = String.valueOf(value).trim().toLowerCase(Locale.ROOT);
        if (text.length() == 1) {
            char c = text.charAt(0);
            if (c >= 'a' && c <= 'z') return KeyInfo.alpha(String.valueOf(c));
            if (c >= '2' && c <= '9') return KeyInfo.t9(String.valueOf(c));
        }
        return null;
    }

    /** 键盘类型名 (base.entity.f.a = KeyboardType 枚举) 用于 T9 判定与日志。 */
    static String keyboardTypeName(Object keyboard) {
        if (!(keyboard instanceof View)) return "";
        Object model = new KeyResolver(new Reflector()).keyboardModel(keyboard);
        if (model == null) return "";
        Object type = readField(model, "a");
        return type == null ? "" : String.valueOf(type);
    }

    static boolean isT9TypeName(String typeName) {
        return typeName != null && typeName.toUpperCase(Locale.ROOT)
                .startsWith(KEYBOARD_TYPE_T9_PREFIX);
    }

    static boolean isHandwritingTypeName(String typeName) {
        return typeName != null && typeName.toUpperCase(Locale.ROOT).contains("HANDWRITING");
    }
}
