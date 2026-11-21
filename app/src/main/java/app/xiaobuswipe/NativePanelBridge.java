package app.xiaobuswipe;

import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * 小布输入法原生剪贴板/常用语面板桥。
 *
 * <p>小布每次大版本升级 R8 会重排混淆名，因此这里不再写死单套类名，而是维护一张
 * 「版本坐标表」：每个 Variant 记录该版本下 单例→总线→投递方法→lambda→面板 的反射坐标，
 * 运行时按顺序尝试，首个走通的即生效。
 *
 * <p>已核实的两套坐标：
 * <ul>
 *   <li>1.8.35.17-mkt：{@code Z.a.a().b().n(b.f.a, new manager.t(16))}，面板 body.H；</li>
 *   <li>1.9.18.17-mkt：{@code j0.f15327c.getValue().b().n(base.manager.g.f11777a,
 *       new input.manager.C0991b(22))}，面板 body.I。</li>
 * </ul>
 *
 * <p>注意：jadx 显示的参数顺序为 (headType, lambda)，但运行时存在合成桥方法
 * (Function0, Object)。故 open 时两种实参顺序都会尝试。
 */
final class NativePanelBridge {

    /** 单个小布版本的反射坐标。 */
    private static final class Variant {
        final String label;
        final String singletonClass;
        /** 非空：kotlin Lazy 静态字段名（新式）；空：走旧式内部类 Companion.a()。 */
        final String singletonLazyField;
        final String paramClass;
        final String paramField;
        final String lambdaClass;
        final int clipboardCase;
        final String panelClass;

        Variant(String label, String singletonClass, String singletonLazyField,
                String paramClass, String paramField, String lambdaClass,
                int clipboardCase, String panelClass) {
            this.label = label;
            this.singletonClass = singletonClass;
            this.singletonLazyField = singletonLazyField;
            this.paramClass = paramClass;
            this.paramField = paramField;
            this.lambdaClass = lambdaClass;
            this.clipboardCase = clipboardCase;
            this.panelClass = panelClass;
        }
    }

    /**
     * 新版本坐标放前面：命中即可，失败自动回退旧版本。
     *
     * <p>⚠️ 这里的名字必须是 **dex 真实名**，不能用 jadx 的重命名显示名：
     * jadx 会把与其它 dex 同名的类/字段重命名并在源码里标注 `renamed from`。
     * 例如 1.9.18 反编译里看到的 {@code C0991b} 实际是
     * {@code com.oplus.keyboard.input.manager.b}，{@code j0.f15327c} 实际是 {@code j0.c}，
     * {@code g.f11777a} 实际是 {@code g.a}。
     */
    private static final Variant[] VARIANTS = {
            new Variant("1.9.18",
                    "com.oplus.keyboard.input.view.j0", "c",
                    "com.oplus.keyboard.base.manager.g", "a",
                    "com.oplus.keyboard.input.manager.b", 22,
                    "com.oplus.keyboard.input.view.body.I"),
            new Variant("1.8.35",
                    "com.oplus.keyboard.input.view.Z", null,
                    "com.oplus.keyboard.base.manager.b$f", "a",
                    "com.oplus.keyboard.input.manager.t", 16,
                    "com.oplus.keyboard.input.view.body.H"),
    };

    interface Logger {
        void info(String message);
        void error(String message, Throwable throwable);
    }

    private final Logger logger;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    NativePanelBridge(Logger logger) {
        this.logger = logger;
    }

    /** 在目标进程内打开原生面板; action 为 ACTION_OPEN_CLIPBOARD / ACTION_OPEN_QUICK_PHRASE。 */
    boolean open(ClassLoader loader, View keyboardRoot, boolean quickPhrase) {
        for (Variant variant : VARIANTS) {
            try {
                if (openWithVariant(loader, keyboardRoot, quickPhrase, variant)) {
                    logger.info("native panel bridge: variant " + variant.label + " used");
                    return true;
                }
            } catch (Throwable throwable) {
                logger.info("native panel bridge: variant " + variant.label
                        + " unavailable: " + throwable);
            }
        }
        logger.error("native panel bridge: no variant matched target build", null);
        return false;
    }

    private boolean openWithVariant(ClassLoader loader, View keyboardRoot,
                                    boolean quickPhrase, Variant variant) throws Exception {
        Object facade = facade(loader, variant);
        if (facade == null) {
            logger.info("native panel bridge: " + variant.label + " singleton unresolved");
            return false;
        }
        Object bus = invokeNoArg(facade, "b");
        if (bus == null) {
            logger.info("native panel bridge: " + variant.label + " event bus unresolved");
            return false;
        }
        Object param = staticField(loader, variant.paramClass, variant.paramField);
        Object lambda = panelLambda(loader, variant.lambdaClass, variant.clipboardCase);
        if (param == null || lambda == null) {
            logger.info("native panel bridge: " + variant.label + " param/lambda unresolved");
            return false;
        }
        List<Method> candidates = collectPostMethods(bus);
        if (candidates.isEmpty()) {
            logger.info("native panel bridge: " + variant.label + " no 2-arg n() on bus");
            return false;
        }
        StringBuilder sig = new StringBuilder();
        for (Method m : candidates) {
            Class<?>[] pt = m.getParameterTypes();
            sig.append(m.getDeclaringClass().getName()).append('.')
                    .append(m.getName()).append('(')
                    .append(pt[0].getName()).append(',').append(pt[1].getName()).append(") ");
        }
        logger.info("native panel candidates=[" + sig + "] variant=" + variant.label
                + " param=" + param.getClass().getName()
                + " lambda=" + lambda.getClass().getName());

        // 运行时可能是 (Function0, Object)，也可能与源码一致为 (headType, Function0)，两种都试。
        Object[][] argOrders = { { lambda, param }, { param, lambda } };
        Throwable lastCause = null;
        for (Method m : candidates) {
            for (Object[] args : argOrders) {
                try {
                    m.invoke(bus, args);
                    logger.info("native panel bridge posted(" + variant.label + ") action="
                            + (quickPhrase ? "phrase" : "clipboard"));
                    if (quickPhrase) {
                        switchToPhrase(keyboardRoot, variant.panelClass);
                    }
                    return true;
                } catch (IllegalArgumentException iae) {
                    lastCause = iae;
                } catch (java.lang.reflect.InvocationTargetException ite) {
                    // 方法被接受但内部抛异常：说明坐标已对上，本次无法继续，直接回退下一套
                    logger.error("native panel bridge: " + variant.label + " target threw",
                            ite.getCause());
                    return false;
                } catch (Throwable throwable) {
                    lastCause = throwable;
                    break;
                }
            }
        }
        logger.info("native panel bridge: " + variant.label + " all overloads rejected args: "
                + lastCause);
        return false;
    }

    /** 解析单例门面：新式为 kotlin Lazy 静态字段，旧式为内部类 Companion.a()。 */
    private static Object facade(ClassLoader loader, Variant variant) throws Exception {
        Class<?> facadeClass = loader.loadClass(variant.singletonClass);
        if (variant.singletonLazyField != null) {
            try {
                Field lazyField = facadeClass.getDeclaredField(variant.singletonLazyField);
                lazyField.setAccessible(true);
                Object lazy = lazyField.get(null);
                if (lazy == null) return null;
                // 不能 loadClass("kotlin.Lazy")：小布的 dex 里该符号不可见（ClassNotFoundException）。
                // 直接对实现类/其接口调用 getValue()。
                Object value = lazyValue(lazy);
                if (value != null) return value;
            } catch (NoSuchFieldException ignored) {
                // 落到旧式 Companion
            }
        }
        for (Class<?> inner : facadeClass.getDeclaredClasses()) {
            if (!"a".equals(inner.getSimpleName())) continue;
            Method companion = inner.getDeclaredMethod("a");
            companion.setAccessible(true);
            return companion.invoke(null);
        }
        return null;
    }

    /** 反射调用 kotlin Lazy 的 getValue()，优先实现类，其次其接口。 */
    private static Object lazyValue(Object lazy) {
        for (Class<?> type = lazy.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Method getValue = type.getDeclaredMethod("getValue");
                getValue.setAccessible(true);
                return getValue.invoke(lazy);
            } catch (NoSuchMethodException ignored) {
            } catch (Throwable ignored) {
                return null;
            }
        }
        for (Class<?> itf : lazy.getClass().getInterfaces()) {
            try {
                Method getValue = itf.getMethod("getValue");
                getValue.setAccessible(true);
                return getValue.invoke(lazy);
            } catch (NoSuchMethodException ignored) {
            } catch (Throwable ignored) {
                return null;
            }
        }
        return null;
    }

    private static Object invokeNoArg(Object target, String name) {
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Method method = type.getDeclaredMethod(name);
                method.setAccessible(true);
                return method.invoke(target);
            } catch (NoSuchMethodException ignored) {
            } catch (Throwable ignored) {
                return null;
            }
        }
        return null;
    }

    private static Object staticField(ClassLoader loader, String className, String fieldName)
            throws Exception {
        Class<?> type = loader.loadClass(className);
        Field field = type.getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(null);
    }

    private static Object panelLambda(ClassLoader loader, String className, int caseId)
            throws Exception {
        Class<?> lambdaClass = loader.loadClass(className);
        Constructor<?> constructor = lambdaClass.getConstructor(int.class);
        return constructor.newInstance(caseId);
    }

    /**
     * 收集事件总线上所有 2 参数的 n() 重载（含父类与 R8 合成桥方法）。
     * 不按签名猜测，逐个尝试实参，首个接受者即入口。
     */
    private static List<Method> collectPostMethods(Object bus) {
        List<Method> candidates = new ArrayList<>();
        for (Class<?> type = bus.getClass(); type != null; type = type.getSuperclass()) {
            for (Method method : type.getDeclaredMethods()) {
                if (!"n".equals(method.getName())) continue;
                if (method.getParameterTypes().length != 2) continue;
                method.setAccessible(true);
                candidates.add(method);
            }
        }
        return candidates;
    }

    /**
     * 常用语（快捷发送）：面板本身是剪贴板面板的第 1 段（段 0=剪贴板，段 1=常用语）。
     * 剪贴板 lambda 已把面板打开并停在段 0，这里在同帧内对面板调用 t(true)，
     * 该方法会把段状态置 1 并 selectSegmentAt(1)，从而直接展示常用语。
     */
    private void switchToPhrase(View root, String panelClass) {
        View panel = findPanelByClass(root, panelClass);
        if (panel != null) {
            switchToPhraseNow(panel);
            return;
        }
        // 极少数情况下面板尚未挂到视图树，短延时兜底重试一次。
        mainHandler.postDelayed(() -> {
            View late = findPanelByClass(root, panelClass);
            if (late != null) switchToPhraseNow(late);
            else logger.info("phrase switch: panel " + panelClass + " not found in tree");
        }, 80L);
    }

    private void switchToPhraseNow(View panel) {
        try {
            Method t = panel.getClass().getMethod("t", boolean.class);
            t.setAccessible(true);
            t.invoke(panel, true);
            logger.info("phrase switch done (" + panel.getClass().getName() + ".t(true))");
        } catch (Throwable throwable) {
            logger.error("phrase switch failed", throwable);
        }
    }

    /** 深度优先查找指定类名的剪贴板/常用语面板实例。 */
    private View findPanelByClass(View view, String panelClass) {
        if (view == null) return null;
        if (panelClass.equals(view.getClass().getName())) return view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int index = 0; index < group.getChildCount(); index++) {
                View found = findPanelByClass(group.getChildAt(index), panelClass);
                if (found != null) return found;
            }
        }
        return null;
    }
}
