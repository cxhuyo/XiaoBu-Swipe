package app.xiaobuswipe;

import android.view.View;

/** 当前活动键盘的不可变分类，供触摸与绘制热路径使用。 */
final class KeyboardRuntimeState {
    enum Mode { QWERTY, T9, HANDWRITING, OTHER }

    final Mode mode;
    final boolean handwriting;

    private KeyboardRuntimeState(Mode mode, boolean handwriting) {
        this.mode = mode;
        this.handwriting = handwriting;
    }

    static KeyboardRuntimeState from(View keyboard, String keyboardTypeName) {
        if (keyboard == null) return new KeyboardRuntimeState(Mode.OTHER, false);
        boolean handwriting = KeyboardCompat.isHandwritingContext(keyboard)
                || KeyResolver.isHandwritingTypeName(keyboardTypeName);
        return classify(keyboard.getClass().getName(), keyboardTypeName, handwriting);
    }

    static KeyboardRuntimeState classify(String className, String keyboardTypeName,
                                         boolean handwriting) {
        if (handwriting) return new KeyboardRuntimeState(Mode.HANDWRITING, true);
        if (KeyResolver.isT9TypeName(keyboardTypeName)) {
            return new KeyboardRuntimeState(Mode.T9, false);
        }
        String name = className == null ? "" : className.toLowerCase(java.util.Locale.ROOT);
        if (name.contains("t9") || name.contains("nine")) {
            return new KeyboardRuntimeState(Mode.T9, false);
        }
        // body.r = QWERTY 主键盘视图；类名本身不含 qwerty，按包路径判定
        if (name.startsWith("com.oplus.keyboard.input.view.body.")
                || name.contains("qwerty") || name.contains("wubi") || name.contains("pinyin")) {
            return new KeyboardRuntimeState(Mode.QWERTY, false);
        }
        return new KeyboardRuntimeState(Mode.OTHER, false);
    }
}
