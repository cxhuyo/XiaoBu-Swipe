package app.xiaobuswipe;

import android.app.Application;
import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.res.Configuration;
import android.inputmethodservice.InputMethodService;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.SystemClock;
import android.text.InputType;
import android.util.Log;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.ExtractedText;
import android.view.inputmethod.ExtractedTextRequest;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.view.inputmethod.SurroundingText;
import android.widget.FrameLayout;
import android.widget.TextView;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;

public final class MainHook extends XposedModule {
    private static final String TAG = "XiaoBuSwipe";
    private static final String TARGET = "com.oplus.keyboard";
    /** 预览提示出现的阈值比例：需滑到触发距离的 80% 才提示，避免提示远早于实际触发。 */
    private static final float HINT_PREVIEW_RATIO = 0.80f;
    private static final int PARAGRAPH_CONTEXT_CHARS = 65_536;
    private static final int LABEL_GEOMETRY_CACHE_LIMIT = 256;

    private final GestureTracker tracker = new GestureTracker();
    private final Reflector reflector = new Reflector();
    private final KeyResolver keyResolver = new KeyResolver(reflector);
    private final NativePanelBridge nativePanelBridge = new NativePanelBridge(
            new NativePanelBridge.Logger() {
                @Override public void info(String message) { logInfo(message); }
                @Override public void error(String message, Throwable throwable) { logError(message, throwable); }
            });
    private final ConfigBridge configBridge = new ConfigBridge(TARGET, new ConfigBridge.Listener() {
        @Override public void onConfigChanged(Config config) { labelGeometry.clear(); }
        @Override public void info(String message) { logInfo(message); }
        @Override public void error(String message, Throwable throwable) { logError(message, throwable); }
    });
    private final ConcurrentHashMap<Method, Boolean> keyboardDrawHookedMethods = new ConcurrentHashMap<>();
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private volatile long labelPaintSignature = Long.MIN_VALUE;
    private volatile int lastResolvedNightMode = Configuration.UI_MODE_NIGHT_UNDEFINED;
    private volatile int liveNightMode = Configuration.UI_MODE_NIGHT_UNDEFINED;
    private volatile WeakReference<View> runtimeKeyboardRef = new WeakReference<>(null);
    private volatile KeyboardRuntimeState runtimeKeyboardState;
    private volatile String runtimeKeyboardTypeName = "";
    private final ConcurrentHashMap<Class<?>, Boolean> keyboardDrawPreparedClasses = new ConcurrentHashMap<>();

    // 标签几何缓存: key identity -> {centerX, baseline, keyWidth}
    private final Map<String, float[]> labelGeometry = new HashMap<>();
    private volatile WeakReference<View> labelGeometryViewRef = new WeakReference<>(null);
    private volatile int labelGeometryWidth;
    private volatile int labelGeometryHeight;
    private volatile int labelGeometryKeyCount = -1;
    private volatile boolean labelGeometryBuilding;
    private volatile boolean labelDrawLogged;
    private volatile boolean labelGeometryLogged;
    /** 合成点击（大小写切换）期间置位，使拦截逻辑直接透传，避免递归。 */
    private volatile boolean synthesizing;

    private volatile WeakReference<InputMethodService> imeRef = new WeakReference<>(null);
    private volatile Class<?> keyboardBaseClass;
    private volatile boolean hooksInstalled;
    private volatile int currentSelectionStart = -1;
    private volatile int currentSelectionEnd = -1;
    private volatile Class<?> selectionHookedClass;
    private volatile boolean currentEditorPassword;
    private volatile WeakReference<View> keyboardHintRootRef = new WeakReference<>(null);
    private volatile WeakReference<TextView> keyboardHintViewRef = new WeakReference<>(null);
    private Runnable keyboardHintHideTask;

    @Override public void onModuleLoaded(XposedModuleInterface.ModuleLoadedParam param) {
        logInfo("event=module_loaded Modern API " + getApiVersion()
                + " loaded in " + param.getProcessName());
    }

    @Override public void onPackageReady(XposedModuleInterface.PackageReadyParam param) {
        if (!TARGET.equals(param.getPackageName())) return;
        logInfo("event=package_ready target=" + TARGET);
        try {
            installHooks();
            logInfo("v2.0.0 entered target package " + TARGET);
        } catch (Throwable throwable) {
            logError("initialization failed", throwable);
        }
    }

    private interface ChainAction {
        void run(XposedInterface.Chain chain) throws Throwable;
    }

    private synchronized void installHooks() throws Exception {
        if (hooksInstalled) return;

        hookAfter(Application.class.getDeclaredMethod("attach", Context.class),
                chain -> captureApplication(chain.getThisObject()));
        hookAfter(Application.class.getDeclaredMethod("onCreate"),
                chain -> captureApplication(chain.getThisObject()));
        hookAfter(Application.class.getDeclaredMethod("onConfigurationChanged", Configuration.class),
                chain -> captureConfiguration(chain.getThisObject(), (Configuration) chain.getArg(0)));

        hookAfter(InputMethodService.class.getDeclaredMethod("onCreate"),
                chain -> captureIme(chain.getThisObject()));
        hookAfter(InputMethodService.class.getDeclaredMethod("onConfigurationChanged", Configuration.class),
                chain -> captureConfiguration(chain.getThisObject(), (Configuration) chain.getArg(0)));
        hookBefore(InputMethodService.class.getDeclaredMethod("onStartInput", EditorInfo.class, boolean.class),
                chain -> captureStartInput(chain.getThisObject(), (EditorInfo) chain.getArg(0)));
        hookAfter(InputMethodService.class.getDeclaredMethod("onUpdateSelection",
                        int.class, int.class, int.class, int.class, int.class, int.class),
                this::captureSelection);

        Method dispatchTouchEvent = View.class.getDeclaredMethod("dispatchTouchEvent", MotionEvent.class);
        dispatchTouchEvent.setAccessible(true);
        hook(dispatchTouchEvent)
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(this::interceptDispatchTouch);

        hooksInstalled = true;
        logInfo("[OK] Hook 1/1: View dispatchTouchEvent + IMService lifecycle");
        logInfo("All hooks installed successfully");
    }

    private void hookAfter(Method method, ChainAction action) {
        method.setAccessible(true);
        hook(method).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
            Object result = chain.proceed();
            action.run(chain);
            return result;
        });
    }

    private void hookBefore(Method method, ChainAction action) {
        method.setAccessible(true);
        hook(method).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
            action.run(chain);
            return chain.proceed();
        });
    }

    private void captureApplication(Object object) {
        if (!(object instanceof Application)) return;
        Application app = (Application) object;
        try {
            if (!TARGET.equals(app.getPackageName())) return;
        } catch (Throwable ignored) {
            return;
        }
        ensureConfigSync(app);
        refreshLiveNightMode(app.getResources().getConfiguration().uiMode, null);
    }

    private void captureConfiguration(Object object, Configuration configuration) {
        if (!(object instanceof Context) || configuration == null) return;
        Context context = (Context) object;
        try {
            if (!TARGET.equals(context.getPackageName())) return;
        } catch (Throwable ignored) {
            return;
        }
        refreshLiveNightMode(configuration.uiMode, null);
    }

    private void captureIme(Object object) {
        if (!(object instanceof InputMethodService)) return;
        InputMethodService ime = (InputMethodService) object;
        try {
            if (!TARGET.equals(ime.getPackageName())) return;
        } catch (Throwable ignored) {
            return;
        }

        InputMethodService previousIme = imeRef.get();
        if (previousIme != ime) {
            clearKeyboardHint();
            runtimeKeyboardRef = new WeakReference<>(null);
            runtimeKeyboardState = null;
        }
        imeRef = new WeakReference<>(ime);
        refreshLiveNightMode(ime.getResources().getConfiguration().uiMode, null);
        ensureConfigSync(ime);
        ensureConcreteSelectionHook(ime.getClass());
    }

    private void captureStartInput(Object object, EditorInfo editorInfo) {
        currentSelectionStart = -1;
        currentSelectionEnd = -1;
        currentEditorPassword = editorInfo != null && isPassword(editorInfo.inputType);
        captureIme(object);
    }

    private void captureSelection(XposedInterface.Chain chain) {
        try {
            Object object = chain.getThisObject();
            if (!(object instanceof InputMethodService)) return;
            int start = ((Number) chain.getArg(2)).intValue();
            int end = ((Number) chain.getArg(3)).intValue();
            if (start >= 0 && end >= 0) {
                currentSelectionStart = start;
                currentSelectionEnd = end;
            }
        } catch (Throwable throwable) {
            logError("selection tracking failed", throwable);
        }
    }

    private synchronized void ensureConcreteSelectionHook(Class<?> imeClass) {
        if (imeClass == null || imeClass == InputMethodService.class || selectionHookedClass == imeClass) return;
        try {
            Method method = Reflector.findMethod(imeClass, "onUpdateSelection", new Class<?>[]{
                    int.class, int.class, int.class, int.class, int.class, int.class});
            if (method == null || method.getDeclaringClass() == InputMethodService.class) return;
            hookAfter(method, this::captureSelection);
            selectionHookedClass = imeClass;
            logInfo("selection hook installed for " + imeClass.getName());
        } catch (Throwable throwable) {
            logError("concrete selection hook failed", throwable);
        }
    }

    private void ensureConfigSync(Context context) {
        configBridge.ensureSync(context);
    }

    private Object interceptDispatchTouch(XposedInterface.Chain chain) throws Throwable {
        Object target = chain.getThisObject();
        Object eventObject = chain.getArg(0);
        if (!(target instanceof View) || !(eventObject instanceof MotionEvent)) return chain.proceed();

        View view = (View) target;
        MotionEvent event = (MotionEvent) eventObject;
        int actionMasked = event.getActionMasked();

        Class<?> keyboardBase = keyboardBaseClass;
        if (keyboardBase == null) {
            if (actionMasked != MotionEvent.ACTION_DOWN) return chain.proceed();
            keyboardBase = KeyboardCompat.findKeyboardBase(view.getClass());
            if (keyboardBase == null) return chain.proceed();
            keyboardBaseClass = keyboardBase;
            logInfo("event=keyboard_detected class=" + keyboardBase.getName());
        } else if (!keyboardBase.isInstance(view)) {
            return chain.proceed();
        }

        KeyboardRuntimeState runtime = runtimeStateFor(view);
        if (runtime.handwriting) {
            tracker.clear(view);
            if (actionMasked == MotionEvent.ACTION_DOWN || actionMasked == MotionEvent.ACTION_UP) {
                view.post(() -> hideKeyboardHint(0L));
            }
            return chain.proceed();
        }

        if (actionMasked == MotionEvent.ACTION_DOWN) {
            ensureKeyboardDrawHook(view.getClass(), view);
            if (!configBridge.isLoaded()) ensureConfigSync(view.getContext());
        }
        return interceptKeyboardTouch(chain, view, event);
    }

    private KeyboardRuntimeState runtimeStateFor(View keyboard) {
        View cachedView = runtimeKeyboardRef.get();
        KeyboardRuntimeState cached = runtimeKeyboardState;
        if (cachedView == keyboard && cached != null) return cached;
        String typeName = KeyResolver.keyboardTypeName(keyboard);
        KeyboardRuntimeState resolved = KeyboardRuntimeState.from(keyboard, typeName);
        runtimeKeyboardRef = new WeakReference<>(keyboard);
        runtimeKeyboardState = resolved;
        runtimeKeyboardTypeName = typeName;
        return resolved;
    }

    private void ensureKeyboardDrawHook(Class<?> keyboardClass, View keyboard) {
        if (keyboardClass == null || keyboard == null
                || keyboardDrawPreparedClasses.containsKey(keyboardClass)) return;
        synchronized (this) {
            if (keyboardDrawPreparedClasses.containsKey(keyboardClass)) return;
            Method drawMethod = findKeyboardDrawMethod(keyboardClass, "onDraw");
            if (drawMethod == null) drawMethod = findKeyboardDrawMethod(keyboardClass, "dispatchDraw");
            if (drawMethod == null) {
                keyboardDrawPreparedClasses.put(keyboardClass, Boolean.TRUE);
                return;
            }
            if (keyboardDrawHookedMethods.putIfAbsent(drawMethod, Boolean.TRUE) != null) {
                keyboardDrawPreparedClasses.put(keyboardClass, Boolean.TRUE);
                return;
            }
            try {
                hookAfter(drawMethod, chain -> {
                    Object drawTarget = chain.getThisObject();
                    Object canvas = chain.getArg(0);
                    if (drawTarget instanceof View && canvas instanceof Canvas) {
                        drawKeyboardFunctionLabels((View) drawTarget, (Canvas) canvas);
                    }
                });
                keyboardDrawPreparedClasses.put(keyboardClass, Boolean.TRUE);
                keyboard.postInvalidate();
                logInfo("[OK] Hook 2/2: keyboard function-label draw hook installed for "
                        + drawMethod.getDeclaringClass().getName() + '#' + drawMethod.getName());
            } catch (Throwable throwable) {
                keyboardDrawHookedMethods.remove(drawMethod);
                logError("keyboard function-label draw hook failed", throwable);
            }
        }
    }

    private static Method findKeyboardDrawMethod(Class<?> type, String name) {
        for (Class<?> current = type; current != null && current != View.class;
             current = current.getSuperclass()) {
            try {
                Method method = current.getDeclaredMethod(name, Canvas.class);
                method.setAccessible(true);
                return method;
            } catch (NoSuchMethodException ignored) {
            } catch (Throwable ignored) {
                return null;
            }
        }
        return null;
    }

    private void drawKeyboardFunctionLabels(View keyboard, Canvas canvas) {
        if (keyboard == null || canvas == null || keyboard.getWidth() <= 0 || keyboard.getHeight() <= 0) return;
        KeyboardRuntimeState runtime = runtimeStateFor(keyboard);
        if (runtime.handwriting || currentEditorPassword) return;
        Config config = configBridge.current();
        if (config == null || !config.hasAnyBinding() || !config.showKeyLabels) return;
        try {
            ensureLabelGeometry(keyboard, runtime.mode == KeyboardRuntimeState.Mode.T9);
            if (labelGeometry.isEmpty()) return;
            prepareLabelPaint(keyboard);
            if (!labelGeometryLogged) {
                labelGeometryLogged = true;
                logInfo("event=label_canvas size=" + keyboard.getWidth() + 'x' + keyboard.getHeight()
                        + " textSize=" + labelPaint.getTextSize()
                        + " entries=" + labelGeometry.size());
                int shown = 0;
                for (Map.Entry<String, float[]> probe : labelGeometry.entrySet()) {
                    float[] rect = probe.getValue();
                    logInfo("event=label_geom " + probe.getKey()
                            + " cx=" + Math.round(rect[0])
                            + " bottom=" + Math.round(rect[1])
                            + " w=" + Math.round(rect[2]));
                    if (++shown >= 4) break;
                }
            }
            int drawn = 0;
            for (Map.Entry<String, float[]> entry : labelGeometry.entrySet()) {
                float[] item = entry.getValue();
                if (item == null || item.length < 3) continue;
                String identity = entry.getKey();
                // 前缀为 "t9:"(3字符) 或 "qwerty:"(7字符)，按分隔符定位键名，避免定长截取错误
                int keyStart = identity.indexOf(':') + 1;
                if (keyStart <= 1 || keyStart >= identity.length()) continue;
                boolean t9 = identity.startsWith("t9:");
                String key = identity.substring(keyStart);
                // 键面空间有限：展示"优先级最高的已绑定方向"，下滑优先，其次上/左/右
                int dir = primaryBoundDirection(config, key, t9);
                if (dir < 0) continue;
                int action = config.actionFor(key, t9, dir);
                if (action == Config.ACTION_NONE || action == Config.ACTION_DISABLE) continue;
                String text = Config.DIR_SHORT[dir] + config.labelFor(key, t9, action);
                drawKeyFunctionText(canvas, text,
                        item[0], item[1], item[2], keyboard);
                drawn++;
            }
            if (drawn > 0 && !labelDrawLogged) {
                labelDrawLogged = true;
                logInfo("event=label_drawn keys=" + drawn + " size="
                        + keyboard.getWidth() + 'x' + keyboard.getHeight());
            }
        } catch (Throwable throwable) {
            logError("keyboard function-label drawing failed", throwable);
        }
    }

    /** 键面标签只展示一个方向：下滑优先，其次上滑、左滑、右滑。 */
    private static int primaryBoundDirection(Config config, String key, boolean t9) {
        int[] order = {Config.DIR_DOWN, Config.DIR_UP, Config.DIR_LEFT, Config.DIR_RIGHT};
        for (int dir : order) {
            int action = config.actionFor(key, t9, dir);
            if (action != Config.ACTION_NONE && action != Config.ACTION_DISABLE) return dir;
        }
        return -1;
    }

    private void ensureLabelGeometry(View keyboard, boolean t9Mode) {        if (keyboard == null || keyboard.getWidth() <= 0 || keyboard.getHeight() <= 0) return;
        boolean sameView = labelGeometryViewRef.get() == keyboard;
        int keyCount = softKeyCount(keyboard);
        boolean sameShape = labelGeometryWidth == keyboard.getWidth()
                && labelGeometryHeight == keyboard.getHeight()
                && labelGeometryKeyCount == keyCount;
        if (sameView && sameShape && !labelGeometry.isEmpty()) return;
        if (labelGeometryBuilding) return;
        labelGeometryBuilding = true;
        try {
            Map<String, float[]> result = new HashMap<>();
            keyResolver.collectKeys(keyboard, t9Mode, (info, left, top, right, bottom) -> {
                String identity = (info.t9 ? "t9:" : "qwerty:") + info.key;
                if (result.size() >= LABEL_GEOMETRY_CACHE_LIMIT) return;
                float centerX = (left + right) * 0.5f;
                float width = right - left;
                result.put(identity, new float[]{centerX, bottom, width});
            });
            if (!result.isEmpty()) {
                labelGeometryViewRef = new WeakReference<>(keyboard);
                labelGeometryWidth = keyboard.getWidth();
                labelGeometryHeight = keyboard.getHeight();
                labelGeometryKeyCount = keyCount;
                labelGeometry.clear();
                labelGeometry.putAll(result);
                logInfo("event=label_geometry_ready keys=" + result.size()
                        + " t9=" + t9Mode + " size=" + keyboard.getWidth() + 'x' + keyboard.getHeight());
            }
        } catch (Throwable throwable) {
            logError("label geometry build failed", throwable);
        } finally {
            labelGeometryBuilding = false;
        }
    }

    private int softKeyCount(View keyboard) {
        Object model = null;
        try {
            java.lang.reflect.Method method = Reflector.findMethod(
                    keyboard.getClass(), "getSoftKeyboard", new Class<?>[0]);
            if (method != null) model = method.invoke(keyboard);
        } catch (Throwable ignored) {
        }
        if (model == null) return -1;
        try {
            Field field = model.getClass().getDeclaredField("l");
            field.setAccessible(true);
            Object value = field.get(model);
            return value instanceof List ? ((List<?>) value).size() : -1;
        } catch (Throwable ignored) {
            return -1;
        }
    }

    private int resolveLiveNightMode(View keyboard) {
        int cached = liveNightMode;
        if (cached == Configuration.UI_MODE_NIGHT_NO
                || cached == Configuration.UI_MODE_NIGHT_YES) return cached;
        if (keyboard == null) return Configuration.UI_MODE_NIGHT_UNDEFINED;

        int keyboardUiMode = Configuration.UI_MODE_NIGHT_UNDEFINED;
        int applicationUiMode = Configuration.UI_MODE_NIGHT_UNDEFINED;
        int imeUiMode = Configuration.UI_MODE_NIGHT_UNDEFINED;
        try {
            keyboardUiMode = keyboard.getResources().getConfiguration().uiMode;
            Context application = keyboard.getContext().getApplicationContext();
            if (application != null) applicationUiMode = application.getResources().getConfiguration().uiMode;
        } catch (Throwable ignored) {}
        InputMethodService ime = imeRef.get();
        if (ime != null) {
            try { imeUiMode = ime.getResources().getConfiguration().uiMode; } catch (Throwable ignored) {}
        }
        int resolved = KeyboardThemeState.resolveNightMode(keyboardUiMode, applicationUiMode, imeUiMode);
        refreshLiveNightMode(resolved, keyboard);
        return resolved;
    }

    private void refreshLiveNightMode(int uiMode, View sourceKeyboard) {
        int resolved = KeyboardThemeState.resolveNightMode(
                Configuration.UI_MODE_NIGHT_UNDEFINED, uiMode, Configuration.UI_MODE_NIGHT_UNDEFINED);
        if (resolved != Configuration.UI_MODE_NIGHT_NO
                && resolved != Configuration.UI_MODE_NIGHT_YES) return;
        int previous = liveNightMode;
        if (previous == resolved) return;
        liveNightMode = resolved;
        lastResolvedNightMode = resolved;
        labelPaintSignature = Long.MIN_VALUE;
        View runtime = runtimeKeyboardRef.get();
        if (runtime != null) runtime.postInvalidate();
        if (sourceKeyboard != null && sourceKeyboard != runtime) {
            sourceKeyboard.postInvalidate();
        }
        if (previous != Configuration.UI_MODE_NIGHT_UNDEFINED) {
            logInfo("keyboard label theme refreshed: " + previous + " -> " + resolved);
        }
    }

    private void prepareLabelPaint(View keyboard) {
        float scaledDensity = keyboard.getResources().getDisplayMetrics().scaledDensity;
        float estimatedKeyHeight = keyboard.getHeight() / 4f;
        float keyHeightBased = estimatedKeyHeight * 0.145f;
        float textSize = Math.max(5.8f * scaledDensity, Math.min(8.0f * scaledDensity, keyHeightBased));
        int nightMode = resolveLiveNightMode(keyboard);
        int density = Float.floatToIntBits(scaledDensity);
        long signature = KeyboardThemeState.paintSignature(
                keyboard.getWidth(), keyboard.getHeight(), Math.max(1, keyboard.getHeight() / 4),
                density, nightMode);
        if (signature == labelPaintSignature) return;
        labelPaintSignature = signature;
        boolean night = KeyboardThemeState.isNight(nightMode);
        labelPaint.reset();
        labelPaint.setAntiAlias(true);
        labelPaint.setTextAlign(Paint.Align.CENTER);
        labelPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        labelPaint.setTextSize(textSize);
        if (night) {
            labelPaint.setColor(0xEDFFFFFF);
            labelPaint.setShadowLayer(Math.max(1f, dp(keyboard, 1)), 0f, 0f, 0xD9000000);
        } else {
            labelPaint.setColor(0xC45C626A);
            labelPaint.setShadowLayer(Math.max(1f, dp(keyboard, 1)), 0f, 0f, 0xE6FFFFFF);
        }
    }

    private void drawKeyFunctionText(Canvas canvas, String text, float x, float bottom,
                                     float keyWidth, View keyboard) {
        if (text == null || text.isEmpty()) return;
        float originalSize = labelPaint.getTextSize();
        float scale = text.length() >= 4 ? 0.78f : (text.length() == 3 ? 0.88f : 1f);
        labelPaint.setTextSize(originalSize * scale);
        Paint.FontMetrics metrics = labelPaint.getFontMetrics();
        float visibleBottomPadding = Math.max(dp(keyboard, 1), keyWidth * 0.045f);
        float baseline = bottom - visibleBottomPadding - metrics.descent;
        canvas.drawText(text, x, baseline, labelPaint);
        labelPaint.setTextSize(originalSize);
    }

    private Object interceptKeyboardTouch(XposedInterface.Chain chain, View keyboard, MotionEvent event) throws Throwable {
        if (synthesizing) return chain.proceed();
        final int maskedAction = event.getActionMasked();
        KeyboardRuntimeState runtime = runtimeStateFor(keyboard);
        Config config = configBridge.current();
        if (event.getPointerCount() != 1) {
            if (maskedAction == MotionEvent.ACTION_UP || maskedAction == MotionEvent.ACTION_CANCEL) {
                tracker.clear(keyboard);
                keyboard.post(() -> hideKeyboardHint(0L));
            }
            return chain.proceed();
        }

        if (maskedAction != MotionEvent.ACTION_DOWN
                && !config.hasAnyBinding()
                && !tracker.matches(keyboard)) return chain.proceed();

        if (maskedAction == MotionEvent.ACTION_DOWN) {
            tracker.begin(keyboard, event.getX(), event.getY());
            Object result = chain.proceed();

            KeyInfo keyInfo = keyResolver.keyAfterDown(keyboard, event,
                    runtime.mode == KeyboardRuntimeState.Mode.T9);
            if (keyInfo != null) {
                logInfo("event=key_hit key=" + keyInfo.key + " t9=" + keyInfo.t9
                        + " mode=" + runtime.mode);
            }
            tracker.setBinding(keyboard, keyInfo, config, keyboard);
            if (keyInfo != null && tracker.anyBinding) {
                StringBuilder detail = new StringBuilder();
                for (int d = 0; d < Config.DIR_COUNT; d++) {
                    if (tracker.actions[d] == Config.ACTION_NONE) continue;
                    detail.append(Config.DIR_SHORT[d]).append('=')
                            .append(Config.actionName(tracker.actions[d]))
                            .append('/').append(Math.round(tracker.thresholds[d])).append("px ");
                }
                logInfo("event=swipe_directions key=" + keyInfo.key + " " + detail);
            }
            keyboard.post(() -> hideKeyboardHint(0L));
            return result;
        }

        if (!tracker.matches(keyboard)) return chain.proceed();

        if (tracker.triggered) {
            if (maskedAction == MotionEvent.ACTION_UP || maskedAction == MotionEvent.ACTION_CANCEL) {
                tracker.clear(keyboard);
            }
            return Boolean.TRUE;
        }

        if (maskedAction == MotionEvent.ACTION_MOVE || maskedAction == MotionEvent.ACTION_UP) {
            float deltaX = event.getX() - tracker.downX;
            float deltaY = event.getY() - tracker.downY;

            int dir;
            if (tracker.locked) {
                dir = tracker.dir;
            } else {
                if (!tracker.anyBinding) {
                    // 该键没有任何方向绑定：整个手势完全交还原生
                    return finishOrProceed(chain, keyboard, maskedAction, event);
                }
                if (Math.hypot(deltaX, deltaY) < tracker.slopPx) {
                    // 仍在点击容差内，原生点击/长按照常
                    return finishOrProceed(chain, keyboard, maskedAction, event);
                }
                dir = resolveDirection(deltaX, deltaY);
                if (tracker.actions[dir] == Config.ACTION_NONE) {
                    // 该方向未绑定 → 原生接管（上滑出数字、左右移光标等保持原状）
                    return finishOrProceed(chain, keyboard, maskedAction, event);
                }
                // 该方向有自定义绑定：从此刻起锁定并吞事件，彻底屏蔽原生同方向手势
                tracker.lock(dir);
            }

            if (tracker.actions[dir] == Config.ACTION_NONE) {
                return finishOrProceed(chain, keyboard, maskedAction, event);
            }

            boolean horizontal = dir == Config.DIR_LEFT || dir == Config.DIR_RIGHT;
            float distance = horizontal ? Math.abs(deltaX) : Math.abs(deltaY);
            float threshold = tracker.thresholds[dir];

            if (maskedAction == MotionEvent.ACTION_MOVE) {
                if (distance >= threshold * HINT_PREVIEW_RATIO && distance < threshold) {
                    final String hintLabel = tracker.displayKey;
                    final int hintAction = tracker.actions[dir];
                    keyboard.post(() -> showKeyboardHint(keyboard, hintLabel, hintAction, false));
                } else if (distance < threshold * HINT_PREVIEW_RATIO) {
                    keyboard.post(() -> hideKeyboardHint(0L));
                }
            }

            if (distance >= threshold) {
                final int requestedAction = tracker.actions[dir];
                final String key = tracker.key;
                final int triggerDir = dir;
                final Context context = keyboard.getContext();
                tracker.markTriggered(keyboard);

                proceedWithCancel(chain, event);

                Config latest = configBridge.current();
                if (latest.vibration) {
                    try { keyboard.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP); }
                    catch (Throwable ignored) {}
                }

                keyboard.post(() -> showKeyboardHint(keyboard, null, requestedAction, true));
                if (requestedAction != Config.ACTION_DISABLE) {
                    keyboard.post(() -> performAction(context, keyboard, requestedAction, key, triggerDir));
                }

                if (maskedAction == MotionEvent.ACTION_UP) tracker.clear(keyboard);
                return Boolean.TRUE;
            }

            // 方向已锁定但未达阈值：继续吞掉事件，防止原生手势接管
            if (maskedAction == MotionEvent.ACTION_UP || maskedAction == MotionEvent.ACTION_CANCEL) {
                tracker.clear(keyboard);
                keyboard.post(() -> hideKeyboardHint(120L));
            }
            return Boolean.TRUE;
        }

        return chain.proceed();
    }

    /**
     * 方向判定：横向需明显占优（>1.25 倍）才判为左/右滑，否则一律按纵向处理。
     * 这样下滑时常见的横向抖动不会被误判成左右滑，保持旧版下滑手感。
     */
    private static int resolveDirection(float deltaX, float deltaY) {
        float absX = Math.abs(deltaX);
        float absY = Math.abs(deltaY);
        if (absX > absY * 1.25f) {
            return deltaX > 0 ? Config.DIR_RIGHT : Config.DIR_LEFT;
        }
        return deltaY > 0 ? Config.DIR_DOWN : Config.DIR_UP;
    }

    /** 交还原生并清理手势状态。 */
    private Object finishOrProceed(XposedInterface.Chain chain, View keyboard,
                                   int maskedAction, MotionEvent event) throws Throwable {
        Object result = chain.proceed();
        if (maskedAction == MotionEvent.ACTION_UP || maskedAction == MotionEvent.ACTION_CANCEL) {
            tracker.clear(keyboard);
            keyboard.post(() -> hideKeyboardHint(120L));
        }
        return result;
    }

    /**
     * 大小写模式切换：优先合成真实 Shift 点击，走小布完整链路（键面刷新 + 引擎大写状态同步）。
     * 非锁定态连点两次进入 UPPER_LOCK_CASE，锁定态点一次回 LOWER_CASE。
     * 找不到 Shift 键矩形时回退到直接写状态机（仅影响内部状态，可能不刷新键面）。
     */
    private boolean toggleCapsLock(View keyboard) {
        float[] rect = findKeyRect(keyboard, Config.KEY_SHIFT);
        if (rect == null) return setShiftStateDirectly(keyboard, -1);
        int current = readShiftState(keyboard);
        boolean locked = current == 2;
        float x = (rect[0] + rect[2]) * 0.5f;
        float y = (rect[1] + rect[3]) * 0.5f;
        synthesizing = true;
        try {
            long now = SystemClock.uptimeMillis();
            // 第 1 次点击：临时大写；第 2 次点击：小布据此进入 UPPER_LOCK_CASE（持续锁定）
            sendSyntheticTap(keyboard, x, y, now);
            if (!locked) sendSyntheticTap(keyboard, x, y, now + 150);
            logInfo("event=caps_lock tap from=" + current + " lock=" + (!locked));
            return true;
        } catch (Throwable throwable) {
            logError("caps_lock failed", throwable);
            return false;
        } finally {
            synthesizing = false;
        }
    }

    /**
     * 读取当前 ShiftState。小布的点击路径调用 q() 写 y.T，而状态机另有一套 y.R，
     * 两者可能不一致，故取"是否有任一为锁定(2)"，避免已锁定时又叠加两次点击。
     */
    private int readShiftState(View keyboard) {
        try {
            Class<?> stateClass = keyboard.getClass().getClassLoader()
                    .loadClass("com.oplus.keyboard.input.manager.y");
            int r = readStaticInt(stateClass, "R");
            int t = readStaticInt(stateClass, "T");
            if (r == 2 || t == 2) return 2;
            return Math.max(r, t);
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private static int readStaticInt(Class<?> type, String name) {
        try {
            Field field = type.getDeclaredField(name);
            field.setAccessible(true);
            return field.getInt(null);
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private void sendSyntheticTap(View keyboard, float x, float y, long downTime) {
        MotionEvent down = MotionEvent.obtain(downTime, downTime,
                MotionEvent.ACTION_DOWN, x, y, 0);
        MotionEvent up = MotionEvent.obtain(downTime, downTime + 40,
                MotionEvent.ACTION_UP, x, y, 0);
        try {
            keyboard.dispatchTouchEvent(down);
            keyboard.dispatchTouchEvent(up);
        } finally {
            down.recycle();
            up.recycle();
        }
    }

    /** 在键盘模型的 SoftKey 列表中查找指定 keyId 的矩形。 */
    private float[] findKeyRect(View keyboard, String keyId) {
        final float[][] holder = new float[1][];
        boolean t9 = runtimeStateFor(keyboard).mode == KeyboardRuntimeState.Mode.T9;
        keyResolver.collectKeys(keyboard, t9, (info, left, top, right, bottom) -> {
            if (holder[0] == null && keyId.equals(info.key)) {
                holder[0] = new float[]{left, top, right, bottom};
            }
        });
        return holder[0];
    }

    /**
     * 直接写 ShiftState：同时调用 q(int)（写 y.T/y.U）与 r(int)（写 y.R/y.S 并通知 Kernel），
     * 覆盖两套状态字段，尽量让引擎与键面都感知到大写变化。
     * target < 0 表示按当前状态取反（找不到 Shift 键时的兜底）。
     */
    private boolean setShiftStateDirectly(View keyboard, int target) {
        try {
            ClassLoader loader = keyboard.getClass().getClassLoader();
            Class<?> stateClass = loader.loadClass("com.oplus.keyboard.input.manager.y");
            Field shiftField = stateClass.getDeclaredField("R");
            shiftField.setAccessible(true);
            int current = shiftField.getInt(null);
            int desired = target >= 0 ? target : (current == 2 ? 0 : 2);

            Class<?> manager = loader.loadClass("com.oplus.keyboard.input.manager.r");
            Method setTmp = manager.getDeclaredMethod("q", int.class);   // 显示态
            setTmp.setAccessible(true);
            setTmp.invoke(null, desired);
            Method setState = manager.getDeclaredMethod("r", int.class); // 引擎态 + Kernel
            setState.setAccessible(true);
            setState.invoke(null, desired);
            logInfo("event=caps_state " + current + " -> " + desired);
            return true;
        } catch (Throwable throwable) {
            logError("caps_state failed", throwable);
            return false;
        }
    }

    private void showKeyboardHint(View keyboard, String keyLabel, int action, boolean executed) {
        Config displayConfig = configBridge.current();
        if (displayConfig == null || !displayConfig.showTriggerHint) {
            hideKeyboardHint(0L);
            return;
        }
        if (keyboard == null || action == Config.ACTION_NONE) {
            hideKeyboardHint(0L);
            return;
        }
        try {
            InputMethodService ime = imeRef.get();
            EditorInfo editorInfo = ime == null ? null : ime.getCurrentInputEditorInfo();
            if (editorInfo != null && isPassword(editorInfo.inputType)) {
                hideKeyboardHint(0L);
                return;
            }

            TextView hint = resolveKeyboardHint(keyboard);
            if (hint == null) return;
            if (keyboardHintHideTask != null) hint.removeCallbacks(keyboardHintHideTask);

            String text;
            if (executed) {
                text = action == Config.ACTION_DISABLE
                        ? "✓ 已禁用下滑"
                        : "✓ " + Config.actionName(action);
            } else {
                String display = keyLabel == null || keyLabel.isEmpty() ? "当前键" : keyLabel;
                text = display + " 下滑：" + Config.actionName(action);
            }
            hint.setText(text);
            positionKeyboardHint(keyboard, hint);
            hint.setAlpha(1f);
            hint.setVisibility(View.VISIBLE);
            if (executed) hideKeyboardHint(400L);
        } catch (Throwable throwable) {
            logError("keyboard hint display failed", throwable);
        }
    }

    private TextView resolveKeyboardHint(View keyboard) {
        InputMethodService ime = imeRef.get();
        View keyboardRoot = keyboardWindowRoot(keyboard);
        View imeRoot = imeRootView(ime);
        View root = keyboardRoot;
        try {
            if (imeRoot instanceof FrameLayout
                    && keyboard.getWindowToken() != null
                    && keyboard.getWindowToken() == imeRoot.getWindowToken()) {
                root = imeRoot;
            }
        } catch (Throwable ignored) {}
        if (!(root instanceof FrameLayout)) return null;

        TextView cached = keyboardHintViewRef.get();
        if (keyboardHintRootRef.get() == root && cached != null && cached.getParent() == root) {
            return cached;
        }
        clearKeyboardHint();

        TextView hint = new TextView(keyboard.getContext());
        hint.setTextColor(0xF2FFFFFF);
        hint.setTextSize(11f);
        hint.setGravity(Gravity.CENTER);
        hint.setSingleLine(true);
        hint.setMaxLines(1);
        hint.setEllipsize(android.text.TextUtils.TruncateAt.END);
        hint.setPadding(dp(keyboard, 4), dp(keyboard, 1), dp(keyboard, 4), dp(keyboard, 1));
        hint.setClickable(false);
        hint.setFocusable(false);
        hint.setFocusableInTouchMode(false);
        hint.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        hint.setElevation(dp(keyboard, 10));
        hint.setVisibility(View.GONE);

        hint.setBackground(null);
        hint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        hint.setShadowLayer(dp(keyboard, 2), 0f, dp(keyboard, 1), 0xD0000000);

        FrameLayout host = (FrameLayout) root;
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.START);
        host.addView(hint, params);

        keyboardHintRootRef = new WeakReference<>(root);
        keyboardHintViewRef = new WeakReference<>(hint);
        return hint;
    }

    private void positionKeyboardHint(View keyboard, TextView hint) {
        View root = keyboardHintRootRef.get();
        if (!(root instanceof FrameLayout) || hint == null || keyboard == null) return;

        FrameLayout.LayoutParams params = hint.getLayoutParams() instanceof FrameLayout.LayoutParams
                ? (FrameLayout.LayoutParams) hint.getLayoutParams()
                : new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.START);
        params.gravity = Gravity.TOP | Gravity.START;
        params.bottomMargin = 0;

        try {
            int rootWidth = root.getWidth();
            int rootHeight = root.getHeight();
            if (rootWidth <= 0 || rootHeight <= 0) {
                hint.setVisibility(View.GONE);
                return;
            }

            int[] rootLocation = new int[2];
            int[] keyboardLocation = new int[2];
            root.getLocationInWindow(rootLocation);
            keyboard.getLocationInWindow(keyboardLocation);

            int keyboardLeft = keyboardLocation[0] - rootLocation[0];
            int keyboardTop = keyboardLocation[1] - rootLocation[1];
            int keyboardWidth = Math.max(1, keyboard.getWidth());
            int edge = dp(keyboard, 3);
            int maxWidth = Math.max(dp(keyboard, 92),
                    Math.min(Math.round(rootWidth * 0.52f), Math.round(keyboardWidth * 0.56f)));
            hint.setMaxWidth(maxWidth);
            hint.measure(
                    View.MeasureSpec.makeMeasureSpec(maxWidth, View.MeasureSpec.AT_MOST),
                    View.MeasureSpec.makeMeasureSpec(rootHeight, View.MeasureSpec.AT_MOST));
            int hintWidth = Math.max(dp(keyboard, 64), hint.getMeasuredWidth());
            int hintHeight = Math.max(dp(keyboard, 17), hint.getMeasuredHeight());

            if (keyboardTop < hintHeight + edge * 2) {
                hint.setVisibility(View.GONE);
                return;
            }

            int targetLeft = keyboardLeft + (keyboardWidth - hintWidth) / 2;
            targetLeft = Math.max(edge, Math.min(targetLeft, rootWidth - hintWidth - edge));
            int targetTop = keyboardTop - hintHeight - edge;

            params.leftMargin = targetLeft;
            params.topMargin = Math.max(edge, Math.min(targetTop, rootHeight - hintHeight - edge));
            hint.setLayoutParams(params);
            hint.bringToFront();
        } catch (Throwable throwable) {
            hint.setVisibility(View.GONE);
            logError("candidate hint positioning failed", throwable);
        }
    }

    private void hideKeyboardHint(long delayMillis) {
        TextView hint = keyboardHintViewRef.get();
        if (hint == null) return;
        if (keyboardHintHideTask != null) hint.removeCallbacks(keyboardHintHideTask);
        final TextView target = hint;
        keyboardHintHideTask = () -> {
            if (keyboardHintViewRef.get() == target) target.setVisibility(View.GONE);
        };
        if (delayMillis <= 0L) keyboardHintHideTask.run();
        else hint.postDelayed(keyboardHintHideTask, delayMillis);
    }

    private void clearKeyboardHint() {
        TextView hint = keyboardHintViewRef.get();
        if (hint != null) {
            try {
                if (keyboardHintHideTask != null) hint.removeCallbacks(keyboardHintHideTask);
                if (hint.getParent() instanceof ViewGroup) {
                    ((ViewGroup) hint.getParent()).removeView(hint);
                }
            } catch (Throwable ignored) {}
        }
        keyboardHintHideTask = null;
        keyboardHintRootRef = new WeakReference<>(null);
        keyboardHintViewRef = new WeakReference<>(null);
    }

    private void proceedWithCancel(XposedInterface.Chain chain, MotionEvent source) throws Throwable {
        MotionEvent cancel = MotionEvent.obtain(source);
        try {
            cancel.setAction(MotionEvent.ACTION_CANCEL);
            List<Object> args = chain.getArgs();
            Object[] cancelArgs = args.toArray(new Object[0]);
            cancelArgs[0] = cancel;
            chain.proceed(cancelArgs);
        } finally {
            cancel.recycle();
        }
    }

    private void performAction(Context context, View keyboard, int action, String key, int dir) {
        String actionName = Config.actionName(action);
        try {
            InputMethodService ime = imeRef.get();
            if (ime == null) ime = findIme(context);
            if (ime == null) {
                logError("action failed: no input method service", null);
                return;
            }
            imeRef = new WeakReference<>(ime);

            if (ActionRegistry.isInputMethodAction(action)) {
                hideKeyboardHint(0L);
                if (!performInputMethodAction(ime, action)) {
                    logError(actionName + " failed: system rejected input-method action", null);
                }
                return;
            }

            if (action == Config.ACTION_TOGGLE_CAPS_LOCK) {
                if (!toggleCapsLock(keyboard)) {
                    logError(actionName + " failed: shift key not found", null);
                }
                return;
            }

            EditorInfo editorInfo = ime.getCurrentInputEditorInfo();
            if (editorInfo != null && isPassword(editorInfo.inputType)) return;

            if (ActionRegistry.isNativePanelAction(action)) {
                hideKeyboardHint(0L);
                boolean quickPhrase = action == Config.ACTION_OPEN_QUICK_PHRASE;
                if (!nativePanelBridge.open(keyboard.getClass().getClassLoader(),
                        keyboardWindowRoot(keyboard), quickPhrase)) {
                    logError(actionName + " failed: native entry not found", null);
                }
                return;
            }

            InputConnection connection = ime.getCurrentInputConnection();
            if (connection == null) {
                logError("action failed: no input connection", null);
                return;
            }

            boolean success;
            if (action == Config.ACTION_INSERT_TEXT) {
                boolean t9 = key != null && key.length() == 1 && key.charAt(0) >= '2' && key.charAt(0) <= '9';
                success = performInsertText(connection, configBridge.current().textFor(key, t9, dir));
            } else if (action == Config.ACTION_PASTE) {
                success = performPaste(ime, connection);
            } else if (ActionRegistry.isDocumentAction(action)) {
                success = performDocumentAction(connection, action);
            } else if (ActionRegistry.isParagraphAction(action)) {
                success = performParagraphAction(connection, action);
            } else if (ActionRegistry.isCompoundAction(action)) {
                success = performCompoundAction(ime, keyboard, connection, action);
            } else if (ActionRegistry.isEditorHistoryAction(action)) {
                success = performEditorHistoryAction(ime, connection, action);
            } else if (action == Config.ACTION_PASTE_AND_SEND) {
                success = performPasteAndSend(ime, connection);
            } else if (action == Config.ACTION_SEND_EXISTING) {
                success = performSendExisting(ime, connection);
            } else {
                success = performMenuAction(ime, connection, Config.menuIdFor(action));
            }
            logInfo("event=action_done action=" + actionName + " success=" + success);
            if (!success) logError(actionName + " failed: target editor rejected action", null);
        } catch (Throwable throwable) {
            logError("action failed", throwable);
        }
    }

    private boolean performInputMethodAction(InputMethodService ime, int action) {
        if (ime == null) return false;
        if (action == Config.ACTION_NEXT_INPUT_METHOD) return ime.switchToNextInputMethod(false);
        if (action == Config.ACTION_PREVIOUS_INPUT_METHOD) return ime.switchToPreviousInputMethod();
        if (action == Config.ACTION_SHOW_INPUT_METHOD_PICKER) {
            Object service = ime.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (!(service instanceof InputMethodManager)) return false;
            ((InputMethodManager) service).showInputMethodPicker();
            return true;
        }
        return false;
    }

    /**
     * 粘贴：优先读取小布输入法自身剪贴板历史的最新一条 (Room 表 tab_clipboard_data)。
     * <ul>
     *   <li>文本记录 → commitText 直接注入，绕过部分编辑器吞掉 IME 粘贴的问题；</li>
     *   <li>图片/视频记录 → 不能 commitText（库里存的是 [wtsync:img:xxx] 占位符），
     *       改走 commitContent，失败则把 URI 写回系统剪贴板后触发原生粘贴（与长按粘贴同路径）；</li>
     * </ul>
     * 历史不可用时回退系统路径；系统剪贴板为媒体时同样走原生粘贴而非 coerceToText。
     */
    private boolean performPaste(InputMethodService ime, InputConnection connection) {
        // 系统剪贴板若是图片/视频，直接原生粘贴（与长按粘贴完全同路径）。
        // 实测：小布不会把用户复制的图片写进 tab_clipboard_data，图片只存在于系统剪贴板，
        // 所以必须先探测系统剪贴板，否则会读到库里上一条文本，表现为"只粘出上次的文字"。
        int systemClipboardKind = probeSystemClipboardKind(ime);
        if (systemClipboardKind == CLIP_KIND_MEDIA) {
            boolean success = pasteCurrentSystemClipboard(ime, connection);
            logInfo("event=paste_system_media success=" + success);
            // 媒体粘贴失败时不再降级为文本，避免粘出与用户预期无关的历史文字
            return success;
        }

        ClipEntry entry = readLatestImeClipboardEntry(ime);
        if (entry != null && entry.isMedia()) {
            logInfo("event=paste_media_detected type=" + entry.type
                    + " mime=" + entry.mimeTypes + " uri=" + entry.uri);
            if (pasteMediaEntry(ime, connection, entry)) return true;
            logError("ime media paste failed; falling back to system paste", null);
        } else {
            String historyText = entry == null ? "" : entry.plainText();
            if (!historyText.isEmpty()) {
                try { connection.finishComposingText(); } catch (Throwable ignored) {}
                boolean success = connection.commitText(historyText, 1);
                logInfo("event=paste_via_ime_history success=" + success
                        + " length=" + historyText.length());
                if (success) return true;
                logError("ime history commit rejected; falling back to system paste", null);
            } else {
                logInfo("event=paste_ime_history_empty fallback=system");
            }
        }
        try { connection.finishComposingText(); } catch (Throwable ignored) {}
        return performMenuAction(ime, connection, android.R.id.paste);
    }

    private static final int CLIP_KIND_UNKNOWN = 0;
    private static final int CLIP_KIND_TEXT = 1;
    private static final int CLIP_KIND_MEDIA = 2;

    /**
     * 探测系统剪贴板当前内容类型（媒体 / 文本 / 不可读）。
     * 三重判定，覆盖微信等 App 把图片标记为 text/plain 的情况：
     * 1) ClipDescription.hasMimeType("image|video");
     * 2) 首项 URI 经 ContentResolver.getType 解析出 image/video;
     * 3) 有 URI 但类型解析失败 —— 交给原生粘贴处理，不当作文本。
     */
    private int probeSystemClipboardKind(InputMethodService ime) {
        try {
            ClipboardManager clipboard =
                    (ClipboardManager) ime.getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard == null || !clipboard.hasPrimaryClip()) {
                logInfo("event=system_clipboard_empty");
                return CLIP_KIND_UNKNOWN;
            }
            ClipDescription description = clipboard.getPrimaryClipDescription();
            boolean image = description != null && description.hasMimeType("image/*");
            boolean video = description != null && description.hasMimeType("video/*");
            ClipData data = clipboard.getPrimaryClip();
            Uri uri = (data != null && data.getItemCount() > 0)
                    ? data.getItemAt(0).getUri() : null;
            String resolvedType = null;
            if (uri != null) {
                try { resolvedType = ime.getContentResolver().getType(uri); }
                catch (Throwable ignored) {}
            }
            boolean uriMedia = resolvedType != null
                    && (resolvedType.startsWith("image/") || resolvedType.startsWith("video/"));
            logInfo("event=system_clipboard_probe image=" + image + " video=" + video
                    + " uri=" + uri + " uri_type=" + resolvedType
                    + " mime_count=" + (description == null ? -1 : description.getMimeTypeCount())
                    + " first_mime=" + (description != null && description.getMimeTypeCount() > 0
                    ? description.getMimeType(0) : "none"));
            if (image || video || uriMedia) return CLIP_KIND_MEDIA;
            if (uri != null && resolvedType == null) return CLIP_KIND_MEDIA;
            return CLIP_KIND_TEXT;
        } catch (Throwable throwable) {
            logError("probe system clipboard failed", throwable);
            return CLIP_KIND_UNKNOWN;
        }
    }

    /** 把系统剪贴板里现有的媒体粘贴进输入框（不改写剪贴板）。 */
    private boolean pasteCurrentSystemClipboard(InputMethodService ime, InputConnection connection) {
        try { connection.finishComposingText(); } catch (Throwable ignored) {}
        if (performMenuAction(ime, connection, android.R.id.paste)) return true;
        return sendPasteKeyEvent(connection);
    }

    /**
     * 图片/视频粘贴。顺序按实测成功率排列：
     * 1) 系统剪贴板已是该 URI → 直接原生粘贴（等价长按粘贴，主人已实测可用）；
     * 2) 把 URI 写回系统剪贴板 → 原生粘贴（performContextMenuAction，失败再发 KEYCODE_PASTE）；
     * 3) 以上都失败才尝试 commitContent（多数第三方编辑器会返回 false）。
     */
    private boolean pasteMediaEntry(InputMethodService ime, InputConnection connection, ClipEntry entry) {
        Uri uri = parseClipUri(entry.uri);
        if (uri == null) {
            // 无本地 URI（如跨设备同步的 [wtsync:img:...] 占位符），只能依赖系统剪贴板里现成的媒体。
            logInfo("event=media_paste_no_uri mime=" + entry.mimeTypes);
            try { connection.finishComposingText(); } catch (Throwable ignored) {}
            if (!systemClipboardHasMedia(ime)) return false;
            return performMenuAction(ime, connection, android.R.id.paste);
        }
        boolean alreadyInClipboard = systemClipboardMatches(ime, uri);
        if (alreadyInClipboard || writeUriToSystemClipboard(ime, uri)) {
            try { connection.finishComposingText(); } catch (Throwable ignored) {}
            boolean success = performMenuAction(ime, connection, android.R.id.paste);
            if (!success) success = sendPasteKeyEvent(connection);
            logInfo("event=media_paste_via_native success=" + success
                    + " reused_clipboard=" + alreadyInClipboard + " uri=" + uri);
            if (success) return true;
        }
        if (tryCommitMediaContent(connection, uri, entry.mimeTypes)) {
            logInfo("event=media_paste_via_commit_content uri=" + uri);
            return true;
        }
        return false;
    }

    /** 兜底：直接向编辑器发送 KEYCODE_PASTE（部分 App 不实现 performContextMenuAction）。 */
    private boolean sendPasteKeyEvent(InputConnection connection) {
        try {
            boolean down = connection.sendKeyEvent(new android.view.KeyEvent(
                    android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_PASTE));
            boolean up = connection.sendKeyEvent(new android.view.KeyEvent(
                    android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_PASTE));
            logInfo("event=media_paste_keyevent down=" + down + " up=" + up);
            return down || up;
        } catch (Throwable throwable) {
            logError("paste key event failed", throwable);
            return false;
        }
    }

    /** InputConnection.commitContent 提交媒体（API 25+；微信等编辑器不支持时返回 false）。 */
    private boolean tryCommitMediaContent(InputConnection connection, Uri uri, String mimeTypes) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1) return false;
        try {
            String mime = mimeTypes == null ? "" : mimeTypes.trim();
            if (!mime.isEmpty() && mime.contains(",")) mime = mime.split(",")[0].trim();
            if (mime.isEmpty()) mime = "image/*";
            ClipDescription description = new ClipDescription("clipboard", new String[]{mime});
            android.view.inputmethod.InputContentInfo contentInfo =
                    new android.view.inputmethod.InputContentInfo(uri, description, null);
            boolean committed = connection.commitContent(contentInfo, 0, null);
            logInfo("event=media_commit_content mime=" + mime + " success=" + committed);
            return committed;
        } catch (Throwable throwable) {
            logInfo("event=media_commit_content_unsupported " + throwable);
            return false;
        }
    }

    /** 把媒体 URI 写回系统剪贴板（等价于用户复制的 MediaStore URI）。 */
    private boolean writeUriToSystemClipboard(InputMethodService ime, Uri uri) {
        try {
            ClipboardManager clipboard =
                    (ClipboardManager) ime.getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard == null) return false;
            ClipData clipData = ClipData.newUri(ime.getContentResolver(), "clip", uri);
            clipboard.setPrimaryClip(clipData);
            ClipData current = clipboard.getPrimaryClip();
            boolean success = current != null && current.getItemCount() > 0
                    && uri.equals(current.getItemAt(0).getUri());
            logInfo("event=media_write_system_clipboard success=" + success + " uri=" + uri);
            return success;
        } catch (Throwable throwable) {
            logError("write media uri to system clipboard failed", throwable);
            return false;
        }
    }

    private boolean systemClipboardMatches(InputMethodService ime, Uri uri) {
        try {
            ClipboardManager clipboard =
                    (ClipboardManager) ime.getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard == null || !clipboard.hasPrimaryClip()) return false;
            ClipData data = clipboard.getPrimaryClip();
            if (data == null || data.getItemCount() == 0) return false;
            return uri.equals(data.getItemAt(0).getUri());
        } catch (Throwable ignored) {
            return false;
        }
    }

    private boolean systemClipboardHasMedia(InputMethodService ime) {
        try {
            ClipboardManager clipboard =
                    (ClipboardManager) ime.getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard == null || !clipboard.hasPrimaryClip()) return false;
            ClipDescription description = clipboard.getPrimaryClipDescription();
            if (description == null) return false;
            return description.hasMimeType("image/*") || description.hasMimeType("video/*");
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static Uri parseClipUri(String raw) {
        if (raw == null) return null;
        String value = raw.trim();
        if (!value.startsWith("content://") && !value.startsWith("file://")) return null;
        try {
            return Uri.parse(value);
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** 小布剪贴板历史的一条记录（tab_clipboard_data）。 */
    private static final class ClipEntry {
        static final int TYPE_TEXT = 1;
        static final int TYPE_IMAGE = 2;
        static final int TYPE_VIDEO = 3;

        final int type;
        final String text;
        final String uri;
        final String mimeTypes;

        ClipEntry(int type, String text, String uri, String mimeTypes) {
            this.type = type;
            this.text = text == null ? "" : text;
            this.uri = uri == null ? "" : uri;
            this.mimeTypes = mimeTypes == null ? "" : mimeTypes;
        }

        /** 图片/视频（小布 ClipboardType: 1=TEXT, 2=IMAGE, 3=VIDEO）。 */
        boolean isMedia() {
            return type == TYPE_IMAGE || type == TYPE_VIDEO
                    || mimeTypes.startsWith("image/") || mimeTypes.startsWith("video/");
        }

        /** 可 commitText 的纯文本；媒体占位符与跨设备同步占位符一律返回空。 */
        String plainText() {
            if (isMedia() || text.isEmpty()) return "";
            String value = text.trim();
            if (value.startsWith("[wtsync:")) return "";
            if (uri.startsWith("content://") || uri.startsWith("file://")) return "";
            return text;
        }
    }

    /**
     * 从小布输入法 Room 库直读最新一条剪贴板记录（含类型/URI/MIME）。
     * 实现说明: 该 APK 把 androidx.room/sqlite 全量 R8 混淆（类名/方法名不可反射），
     * 故改用系统框架 SQLiteDatabase 直接打开库文件
     * <dataDir>/databases/KeyBoardDatabase（模块与目标同进程同 uid，无权限问题）。
     * 表结构: clipboard_type(1=TEXT/2=IMAGE/3=VIDEO), clipboard_text, clipboard_uri, clipboard_mime_types。
     * 注意: 图片行的 clipboard_text 是 [wtsync:img:xxx] 占位符，必须按类型分流而不是只看文本。
     */
    private ClipEntry readLatestImeClipboardEntry(InputMethodService ime) {
        android.database.sqlite.SQLiteDatabase database = null;
        android.database.Cursor cursor = null;
        try {
            String dataDir = ime.getApplicationInfo() == null
                    ? null : ime.getApplicationInfo().dataDir;
            if (dataDir == null || dataDir.isEmpty()) return null;
            java.io.File dbFile = new java.io.File(dataDir, "databases/KeyBoardDatabase");
            if (!dbFile.exists()) {
                logInfo("event=paste_ime_history_db_file_missing path=" + dbFile);
                return null;
            }
            database = android.database.sqlite.SQLiteDatabase.openDatabase(
                    dbFile.getAbsolutePath(), null,
                    android.database.sqlite.SQLiteDatabase.OPEN_READWRITE);
            cursor = database.rawQuery(
                    "SELECT clipboard_type, clipboard_text, clipboard_uri, clipboard_mime_types"
                            + " FROM tab_clipboard_data"
                            + " ORDER BY clipboard_time DESC LIMIT 1",
                    (String[]) null);
            if (cursor != null && cursor.moveToFirst()) {
                ClipEntry entry = new ClipEntry(
                        cursor.getInt(0),
                        cursor.isNull(1) ? "" : cursor.getString(1),
                        cursor.isNull(2) ? "" : cursor.getString(2),
                        cursor.isNull(3) ? "" : cursor.getString(3));
                logInfo("event=paste_ime_history_entry type=" + entry.type
                        + " text_len=" + entry.text.length()
                        + " uri=" + entry.uri + " mime=" + entry.mimeTypes);
                return entry;
            }
        } catch (Throwable throwable) {
            logError("ime clipboard history read failed", throwable);
        } finally {
            if (cursor != null) {
                try { cursor.close(); } catch (Throwable ignored) {}
            }
            if (database != null) {
                try { database.close(); } catch (Throwable ignored) {}
            }
        }
        return null;
    }

    private boolean performInsertText(InputConnection connection, String text) {
        String value = Config.normalizeInsertedText(text);
        if (value.isEmpty()) return false;
        try { connection.finishComposingText(); } catch (Throwable ignored) {}
        return connection.commitText(value, 1);
    }

    private boolean performEditorHistoryAction(InputMethodService ime,
                                               InputConnection connection, int action) {
        try { connection.finishComposingText(); } catch (Throwable ignored) {}
        return performMenuAction(ime, connection, Config.menuIdFor(action));
    }

    /** 读取系统剪贴板纯文本；剪贴板是图片/视频时返回空（否则 coerceToText 会把 URI 当文字粘出来）。 */
    private String readSystemClipboardText(InputMethodService ime) {
        try {
            ClipboardManager cm = (ClipboardManager) ime.getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm == null || !cm.hasPrimaryClip()) return "";
            ClipDescription description = cm.getPrimaryClipDescription();
            if (description != null
                    && (description.hasMimeType("image/*") || description.hasMimeType("video/*"))) {
                return "";
            }
            ClipData data = cm.getPrimaryClip();
            if (data == null || data.getItemCount() == 0) return "";
            // 带 URI 的条目属于媒体/富内容，不能当纯文本粘（否则会粘出 content:// 字符串）
            if (data.getItemAt(0).getUri() != null) return "";
            CharSequence text = data.getItemAt(0).coerceToText(ime);
            return text == null ? "" : text.toString();
        } catch (Throwable throwable) {
            logError("system clipboard read failed", throwable);
            return "";
        }
    }

    /** 粘贴并发送: 系统剪贴板/历史最新为媒体时走媒体粘贴再触发发送；文本则 commitText 后发送。 */
    private boolean performPasteAndSend(InputMethodService ime, InputConnection connection) {
        if (probeSystemClipboardKind(ime) == CLIP_KIND_MEDIA) {
            if (!pasteCurrentSystemClipboard(ime, connection)) {
                logInfo("event=paste_and_send_system_media_failed");
                return false;
            }
            logInfo("event=paste_and_send_system_media");
            scheduleEditorSend(ime);
            return true;
        }
        ClipEntry entry = readLatestImeClipboardEntry(ime);
        if (entry != null && entry.isMedia()) {
            if (!pasteMediaEntry(ime, connection, entry)) {
                logInfo("event=paste_and_send_media_failed type=" + entry.type);
                return false;
            }
            logInfo("event=paste_and_send_media type=" + entry.type + " uri=" + entry.uri);
            scheduleEditorSend(ime);
            return true;
        }
        String text = entry == null ? "" : entry.plainText();
        if (text.isEmpty()) text = readSystemClipboardText(ime);
        if (text.isEmpty()) {
            logInfo("event=paste_and_send_empty clipboard=empty");
            return false;
        }
        try { connection.finishComposingText(); } catch (Throwable ignored) {}
        boolean committed = connection.commitText(text, 1);
        logInfo("event=paste_and_send commit=" + committed + " length=" + text.length());
        if (!committed) return false;
        return sendViaEditorAction(ime, connection);
    }

    /** 图片粘贴后编辑器需要时间生成待发送内容，延迟再触发发送。 */
    private void scheduleEditorSend(InputMethodService ime) {
        new android.os.Handler(ime.getMainLooper()).postDelayed(() -> {
            try {
                InputConnection current = ime.getCurrentInputConnection();
                if (current != null) sendViaEditorAction(ime, current);
            } catch (Throwable throwable) {
                logError("delayed editor send failed", throwable);
            }
        }, 350L);
    }

    /** 发送已有内容: 直接触发编辑器发送动作（输入框中已有文本）。 */
    private boolean performSendExisting(InputMethodService ime, InputConnection connection) {
        try { connection.finishComposingText(); } catch (Throwable ignored) {}
        return sendViaEditorAction(ime, connection);
    }

    /** 通过 InputConnection.performEditorAction 触发发送（微信/多数聊天 App 响应 IME_ACTION_SEND）。 */
    private boolean sendViaEditorAction(InputMethodService ime, InputConnection connection) {
        EditorInfo editorInfo = ime.getCurrentInputEditorInfo();
        int imeOptions = editorInfo != null ? editorInfo.imeOptions : EditorInfo.IME_NULL;
        int action = imeOptions & EditorInfo.IME_MASK_ACTION;
        boolean ok = connection.performEditorAction(EditorInfo.IME_ACTION_SEND);
        if (!ok && action != EditorInfo.IME_ACTION_SEND) {
            ok = connection.performEditorAction(action);
        }
        if (!ok) ok = connection.performEditorAction(EditorInfo.IME_NULL);
        logInfo("event=send_via_editor_action ok=" + ok + " imeOptions=0x" + Integer.toHexString(imeOptions));
        return ok;
    }

    private boolean performCompoundAction(InputMethodService ime, View keyboard,
                                          InputConnection connection, int action) {
        try {
            try { connection.finishComposingText(); } catch (Throwable ignored) {}
            final EditorSnapshot original = readEditorSnapshot(connection);
            if (original != null && original.left == original.right
                    && original.before.isEmpty() && original.after.isEmpty()) return false;
            if (!performMenuAction(ime, connection, android.R.id.selectAll)) return false;

            keyboard.postDelayed(() -> {
                InputConnection active = null;
                try { active = ime.getCurrentInputConnection(); } catch (Throwable ignored) {}
                if (active == null) return;
                int target = action == Config.ACTION_COPY_ALL ? android.R.id.copy : android.R.id.cut;
                boolean success = performMenuAction(ime, active, target);
                if (!success) {
                    logError(Config.actionName(action) + " failed: target editor rejected action", null);
                }
                if (original != null && (action == Config.ACTION_COPY_ALL || !success)) {
                    try {
                        if (active.setSelection(original.left, original.right)) {
                            currentSelectionStart = original.left;
                            currentSelectionEnd = original.right;
                        }
                    } catch (Throwable ignored) {}
                }
            }, 32L);
            return true;
        } catch (Throwable throwable) {
            logError(Config.actionName(action) + " failed", throwable);
            return false;
        }
    }

    private static View keyboardWindowRoot(View keyboard) {
        if (keyboard == null) return null;
        try {
            View root = keyboard.getRootView();
            return root == null ? keyboard : root;
        } catch (Throwable ignored) {
            return keyboard;
        }
    }

    private static View imeRootView(InputMethodService ime) {
        try {
            if (ime == null || ime.getWindow() == null || ime.getWindow().getWindow() == null) return null;
            return ime.getWindow().getWindow().getDecorView();
        } catch (Throwable ignored) {
            return null;
        }
    }

    private boolean performDocumentAction(InputConnection connection, int action) {
        try {
            try { connection.finishComposingText(); } catch (Throwable ignored) {}

            EditorSnapshot snapshot = readEditorSnapshot(connection);
            if (snapshot == null) return false;

            int documentEnd = snapshot.right;
            if (action == Config.ACTION_DOCUMENT_END
                    || action == Config.ACTION_SELECT_TO_DOCUMENT_END) {
                documentEnd = readDocumentEnd(connection);
                if (documentEnd < snapshot.right) return false;
            }

            DocumentNavigator.Target target = DocumentNavigator.resolve(
                    action, snapshot.left, snapshot.right, documentEnd);
            if (target == null) return false;

            boolean success = connection.setSelection(target.start, target.end);
            if (success) {
                currentSelectionStart = target.start;
                currentSelectionEnd = target.end;
            }
            return success;
        } catch (Throwable throwable) {
            logError("document action failed", throwable);
            return false;
        }
    }

    private static int readDocumentEnd(InputConnection connection) {
        ExtractedText extracted = getFullText(connection);
        if (extracted == null || extracted.text == null || extracted.startOffset > 0) return -1;
        return extracted.text.length();
    }

    private boolean performParagraphAction(InputConnection connection, int action) {
        try {
            try { connection.finishComposingText(); } catch (Throwable ignored) {}

            EditorSnapshot snapshot = readEditorSnapshot(connection);
            if (snapshot == null) return false;

            int paragraphStart = snapshot.left - ParagraphNavigator.distanceToStart(snapshot.before);
            int paragraphEnd = snapshot.right + ParagraphNavigator.distanceToEnd(snapshot.after);
            int targetStart;
            int targetEnd;

            if (action == Config.ACTION_PARAGRAPH_START) {
                targetStart = paragraphStart;
                targetEnd = paragraphStart;
            } else if (action == Config.ACTION_PARAGRAPH_END) {
                targetStart = paragraphEnd;
                targetEnd = paragraphEnd;
            } else if (action == Config.ACTION_SELECT_TO_PARAGRAPH_START) {
                targetStart = paragraphStart;
                targetEnd = snapshot.right;
            } else if (action == Config.ACTION_SELECT_TO_PARAGRAPH_END) {
                targetStart = snapshot.left;
                targetEnd = paragraphEnd;
            } else {
                return false;
            }

            boolean success = connection.setSelection(targetStart, targetEnd);
            if (success) {
                currentSelectionStart = targetStart;
                currentSelectionEnd = targetEnd;
            }
            return success;
        } catch (Throwable throwable) {
            logError("paragraph action failed", throwable);
            return false;
        }
    }

    private EditorSnapshot readEditorSnapshot(InputConnection connection) {
        if (Build.VERSION.SDK_INT >= 31) {
            try {
                SurroundingText surrounding = connection.getSurroundingText(
                        PARAGRAPH_CONTEXT_CHARS, PARAGRAPH_CONTEXT_CHARS, 0);
                if (surrounding != null && surrounding.getText() != null) {
                    String text = surrounding.getText().toString();
                    int relativeStart = ParagraphNavigator.clampIndex(
                            surrounding.getSelectionStart(), text.length());
                    int relativeEnd = ParagraphNavigator.clampIndex(
                            surrounding.getSelectionEnd(), text.length());
                    int relativeLeft = Math.min(relativeStart, relativeEnd);
                    int relativeRight = Math.max(relativeStart, relativeEnd);
                    int offset = Math.max(0, surrounding.getOffset());
                    return new EditorSnapshot(
                            offset + relativeLeft,
                            offset + relativeRight,
                            text.substring(0, relativeLeft),
                            text.substring(relativeRight),
                            "surrounding");
                }
            } catch (Throwable throwable) {
                logError("surrounding text unavailable", throwable);
            }
        }

        int selectionStart = currentSelectionStart;
        int selectionEnd = currentSelectionEnd;
        if (selectionStart >= 0 && selectionEnd >= 0) {
            try {
                CharSequence before = connection.getTextBeforeCursor(PARAGRAPH_CONTEXT_CHARS, 0);
                CharSequence after = connection.getTextAfterCursor(PARAGRAPH_CONTEXT_CHARS, 0);
                if (before != null && after != null) {
                    return new EditorSnapshot(
                            Math.min(selectionStart, selectionEnd),
                            Math.max(selectionStart, selectionEnd),
                            before.toString(),
                            after.toString(),
                            "cursor-context");
                }
            } catch (Throwable throwable) {
                logError("cursor context unavailable", throwable);
            }
        }

        try {
            ExtractedTextRequest request = new ExtractedTextRequest();
            request.hintMaxChars = PARAGRAPH_CONTEXT_CHARS;
            request.hintMaxLines = 4096;
            ExtractedText extracted = connection.getExtractedText(request, 0);
            if (extracted != null && extracted.text != null) {
                String text = extracted.text.toString();
                int relativeStart = ParagraphNavigator.clampIndex(
                        extracted.selectionStart, text.length());
                int relativeEnd = ParagraphNavigator.clampIndex(
                        extracted.selectionEnd, text.length());
                int relativeLeft = Math.min(relativeStart, relativeEnd);
                int relativeRight = Math.max(relativeStart, relativeEnd);
                int offset = Math.max(0, extracted.startOffset);
                return new EditorSnapshot(
                        offset + relativeLeft,
                        offset + relativeRight,
                        text.substring(0, relativeLeft),
                        text.substring(relativeRight),
                        "extracted-fallback");
            }
        } catch (Throwable throwable) {
            logError("extracted text fallback unavailable", throwable);
        }
        return null;
    }

    private static final class EditorSnapshot {
        final int left;
        final int right;
        final String before;
        final String after;
        final String source;

        EditorSnapshot(int left, int right, String before, String after, String source) {
            this.left = Math.max(0, left);
            this.right = Math.max(this.left, right);
            this.before = before == null ? "" : before;
            this.after = after == null ? "" : after;
            this.source = source;
        }
    }

    private boolean performMenuAction(InputMethodService ime, InputConnection connection, int menuId) {
        if (menuId == 0) return false;
        try {
            if (connection.performContextMenuAction(menuId)) return true;
        } catch (Throwable ignored) {}

        try {
            Method method = Reflector.findMethod(ime.getClass(), "performContextMenuAction", new Class<?>[]{int.class});
            if (method != null) {
                Object result = method.invoke(ime, menuId);
                if (!(result instanceof Boolean) || (Boolean) result) return true;
            }
        } catch (Throwable ignored) {}
        return fallbackMenuAction(ime, connection, menuId);
    }

    private static boolean fallbackMenuAction(InputMethodService ime, InputConnection connection, int menuId) {
        try {
            if (menuId == android.R.id.selectAll) {
                ExtractedText text = getFullText(connection);
                return text != null && text.text != null
                        && connection.setSelection(0, text.startOffset + text.text.length());
            }
            if (menuId == android.R.id.copy || menuId == android.R.id.cut) {
                CharSequence selected = connection.getSelectedText(0);
                if (selected == null || selected.length() == 0) return false;
                ClipboardManager clipboard = (ClipboardManager) ime.getSystemService(Context.CLIPBOARD_SERVICE);
                if (clipboard == null) return false;
                clipboard.setPrimaryClip(ClipData.newPlainText("text", selected));
                return menuId != android.R.id.cut || connection.commitText("", 1);
            }
            if (menuId == android.R.id.paste) {
                ClipboardManager clipboard = (ClipboardManager) ime.getSystemService(Context.CLIPBOARD_SERVICE);
                if (clipboard == null || !clipboard.hasPrimaryClip()
                        || clipboard.getPrimaryClip() == null
                        || clipboard.getPrimaryClip().getItemCount() == 0) return false;
                ClipDescription description = clipboard.getPrimaryClipDescription();
                if (description != null
                        && (description.hasMimeType("image/*")
                        || description.hasMimeType("video/*"))) {
                    // 媒体剪贴板不能用 coerceToText（会把 content:// URI 当文字粘出来），交回上层走其它路径
                    return false;
                }
                CharSequence text = clipboard.getPrimaryClip().getItemAt(0).coerceToText(ime);
                return text != null && connection.commitText(text, 1);
            }
        } catch (Throwable ignored) {}
        return false;
    }

    private static ExtractedText getFullText(InputConnection connection) {
        try {
            ExtractedTextRequest request = new ExtractedTextRequest();
            request.hintMaxChars = 1_000_000;
            request.hintMaxLines = 100_000;
            return connection.getExtractedText(request, 0);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static InputMethodService findIme(Context context) {
        Context current = context;
        for (int i = 0; i < 12 && current != null; i++) {
            if (current instanceof InputMethodService) return (InputMethodService) current;
            if (current instanceof ContextWrapper) {
                Context next = ((ContextWrapper) current).getBaseContext();
                if (next == current) break;
                current = next;
            } else break;
        }
        return null;
    }

    private static boolean isPassword(int inputType) {
        int inputClass = inputType & InputType.TYPE_MASK_CLASS;
        int variation = inputType & InputType.TYPE_MASK_VARIATION;
        if (inputClass == InputType.TYPE_CLASS_TEXT) {
            return variation == InputType.TYPE_TEXT_VARIATION_PASSWORD
                    || variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                    || variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD;
        }
        return inputClass == InputType.TYPE_CLASS_NUMBER
                && variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD;
    }

    private static int dp(View view, int value) {
        if (view == null) return Math.max(1, value * 2);
        return Math.max(1, Math.round(value * view.getResources().getDisplayMetrics().density));
    }

    private void logInfo(String message) {
        try { log(Log.INFO, TAG, message); } catch (Throwable ignored) {}
    }

    private void logError(String message, Throwable throwable) {
        try {
            if (throwable == null) log(Log.ERROR, TAG, message);
            else log(Log.ERROR, TAG, message, throwable);
        } catch (Throwable ignored) {}
    }

    private static final class GestureTracker {
        private WeakReference<View> keyboardRef = new WeakReference<>(null);
        private float downX;
        private float downY;
        /** 四个方向（Config.DIR_*）各自的动作与触发阈值(px)。 */
        final int[] actions = new int[Config.DIR_COUNT];
        final float[] thresholds = new float[Config.DIR_COUNT];
        private float slopPx = 1f;
        private String key;
        private String displayKey;
        private boolean t9;
        /** 该键是否存在任一方向绑定。 */
        boolean anyBinding;
        /** 是否已锁定某个方向（锁定后吞事件以屏蔽原生手势）。 */
        boolean locked;
        int dir = Config.DIR_DOWN;
        private boolean triggered;
        private boolean active;

        void begin(View keyboard, float x, float y) {
            keyboardRef = new WeakReference<>(keyboard);
            downX = x;
            downY = y;
            slopPx = 1f;
            key = null;
            displayKey = null;
            t9 = false;
            anyBinding = false;
            locked = false;
            dir = Config.DIR_DOWN;
            triggered = false;
            active = true;
            for (int d = 0; d < Config.DIR_COUNT; d++) {
                actions[d] = Config.ACTION_NONE;
                thresholds[d] = 1f;
            }
        }

        /** 记录该按键在四个方向上的绑定与阈值。 */
        void setBinding(View keyboard, KeyInfo keyInfo, Config config, View kb) {
            if (!matches(keyboard)) return;
            key = keyInfo == null ? null : keyInfo.key;
            displayKey = keyInfo == null ? null : keyInfo.display;
            t9 = keyInfo != null && keyInfo.t9;
            slopPx = Math.max(dp(kb, 8), 1f);
            anyBinding = false;
            for (int d = 0; d < Config.DIR_COUNT; d++) {
                int action = keyInfo == null
                        ? Config.ACTION_NONE
                        : config.actionFor(keyInfo.key, keyInfo.t9, d);
                actions[d] = action;
                thresholds[d] = keyInfo == null
                        ? 1f
                        : Math.max(dp(kb, config.distanceFor(keyInfo.key, keyInfo.t9, d)), 1f);
                if (action != Config.ACTION_NONE) anyBinding = true;
            }
            if (keyInfo == null) return;
            // 物理约束：纵向阈值不超过键盘高度 60%，横向不超过宽度 40%，
            // 否则手指滑出键盘也到不了，动作永远触发不了。
            int height = kb.getHeight();
            if (height > 0) {
                float cap = height * 0.6f;
                if (thresholds[Config.DIR_UP] > cap) thresholds[Config.DIR_UP] = cap;
                if (thresholds[Config.DIR_DOWN] > cap) thresholds[Config.DIR_DOWN] = cap;
            }
            int width = kb.getWidth();
            if (width > 0) {
                float cap = width * 0.4f;
                if (thresholds[Config.DIR_LEFT] > cap) thresholds[Config.DIR_LEFT] = cap;
                if (thresholds[Config.DIR_RIGHT] > cap) thresholds[Config.DIR_RIGHT] = cap;
            }
        }

        void lock(int direction) {
            locked = true;
            dir = direction;
        }

        void markTriggered(View keyboard) {
            if (matches(keyboard)) triggered = true;
        }

        void clear(View keyboard) {
            if (!matches(keyboard)) return;
            active = false;
            key = null;
            displayKey = null;
            t9 = false;
            anyBinding = false;
            locked = false;
            dir = Config.DIR_DOWN;
            triggered = false;
        }

        boolean matches(View keyboard) {
            return active && keyboardRef.get() == keyboard;
        }
    }
}
