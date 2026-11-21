package app.xiaobuswipe;

import android.view.View;

/** 小布输入法 (com.oplus.keyboard) 键盘视图识别，替代 WeType 的 selfdraw 基类匹配。 */
final class KeyboardCompat {
    private static final String BODY_PREFIX = "com.oplus.keyboard.input.view.body.";

    // 1.8.35.17-mkt 已核实: body.r = QWERTY 主键盘自绘视图;
    // T9View / QwertySeparateView / T9SeparateView 为九宫格与分离/悬浮变体。
    private static final String KEYBOARD_VIEW_CLASS =
            "com.oplus.keyboard.input.view.body.r";
    private static final String T9_VIEW_CLASS =
            "com.oplus.keyboard.input.view.body.T9View";
    private static final String QWERTY_SEPARATE_VIEW_CLASS =
            "com.oplus.keyboard.input.view.body.QwertySeparateView";
    private static final String T9_SEPARATE_VIEW_CLASS =
            "com.oplus.keyboard.input.view.body.T9SeparateView";

    private KeyboardCompat() {}

    static Class<?> findKeyboardBase(Class<?> type) {
        for (int i = 0; type != null && i < 12; i++, type = type.getSuperclass()) {
            String name = type.getName();
            if (KEYBOARD_VIEW_CLASS.equals(name) || T9_VIEW_CLASS.equals(name)
                    || QWERTY_SEPARATE_VIEW_CLASS.equals(name)
                    || T9_SEPARATE_VIEW_CLASS.equals(name)) {
                return type;
            }
            // 结构兜底: body 包下带 getSoftKeyboard 的 View 子类（版本演进防漂移）
            if (name.startsWith(BODY_PREFIX)
                    && View.class.isAssignableFrom(type)
                    && declaresNoArg(type, "getSoftKeyboard")) {
                return type;
            }
        }
        return null;
    }

    static boolean isHandwritingContext(View keyboard) {
        View current = keyboard;
        for (int depth = 0; current != null && depth < 16; depth++) {
            if (KeyboardModeGuard.isHandwritingClassName(current.getClass().getName())) return true;
            Object parent = current.getParent();
            current = parent instanceof View ? (View) parent : null;
        }
        return false;
    }

    private static boolean declaresNoArg(Class<?> type, String name) {
        if (type == null || name == null) return false;
        try {
            type.getDeclaredMethod(name);
            return true;
        } catch (NoSuchMethodException ignored) {
            return false;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
