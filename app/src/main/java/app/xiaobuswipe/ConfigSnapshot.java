package app.xiaobuswipe;

import android.content.Intent;

final class ConfigSnapshot {
    private ConfigSnapshot() {}

    static Config copyOf(Config source) {
        return ConfigCodec.copyOf(source);
    }

    static void putInto(Intent intent, Config config) {
        ConfigCodec.putIntoIntent(intent, config);
    }
}
