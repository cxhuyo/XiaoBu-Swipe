package app.xiaobuswipe;

import java.util.Locale;

final class KeyInfo {
    final String key;
    final String display;
    final boolean t9;

    private KeyInfo(String key, String display, boolean t9) {
        this.key = key;
        this.display = display;
        this.t9 = t9;
    }

    static KeyInfo alpha(String key) {
        return new KeyInfo(key, key.toUpperCase(Locale.ROOT), false);
    }

    static KeyInfo t9(String digit) {
        return new KeyInfo(digit, "九宫格 " + Config.t9Label(digit.charAt(0) - '0'), true);
    }

    /** 特殊键：del / enter / space，key 与 Config.SPECIAL_KEYS 保持一致。 */
    static KeyInfo special(String key, String display) {
        return new KeyInfo(key, display, false);
    }

    boolean isSpecial() {
        return Config.specialIndex(key) >= 0;
    }
}
