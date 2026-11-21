# 小布输入法下滑快捷键（XiaoBu-Swipe）

![Android](https://img.shields.io/badge/Android-9.0%2B-3DDC84?logo=android&logoColor=white) ![LSPosed](https://img.shields.io/badge/LSPosed-libxposed%20API%20102-1E88E5) ![Version](https://img.shields.io/badge/version-2.0.0-2E7D32) ![License](https://img.shields.io/badge/license-Apache--2.0-blue)

为 OPPO / 一加 / realme 的**小布输入法（Breeno Keyboard）**增加可自定义的按键滑动快捷操作。

支持 26 键、九宫格与 13 个特殊键的**上 / 下 / 左 / 右**四方向独立绑定，共 25 种动作，并可按「按键 + 方向」单独设置触发距离。绑定自定义动作后，对应方向的输入法原生手势（上滑出数字、左右移光标、退格上下滑等）会被自动屏蔽。

> 模块完全免费，禁止倒卖。

---

## 功能特性

| 能力      | 说明                                                                                                            |
| ------- | ------------------------------------------------------------------------------------------------------------- |
| 四方向滑动   | 上 / 下 / 左 / 右四个方向独立绑定动作，互不影响                                                                                  |
| 全按键覆盖   | 26 字母键、九宫格 2–9、13 个特殊键（退格、回车、空格、大小写切换、符号、123、逗号、句号、拼/英、重输、@#、问号、感叹号）                                          |
| 25 种动作  | 全选、剪切、复制、粘贴、复制全部、剪切全部、段首/段尾、选至段首/段尾、文首/文末、选至文首/文末、剪贴板面板、常用语、撤销、重做、大小写模式切换、输入指定内容、上/下一个输入法、输入法选择器、粘贴并发送、发送已有内容 |
| 按方向设距离  | 26 键 6–40dp、九宫格 10–200dp；不单独设置时跟随全局默认                                                                         |
| 原生手势屏蔽  | 某方向有自定义绑定即接管该方向的原生手势，未绑定方向完全保留原生行为                                                                            |
| 四方向文本   | 「输入指定内容」可为同一按键的四个方向分别保存不同文本                                                                                   |
| 多键共用    | 同一功能可同时绑定在多个按键上，互不顶替                                                                                          |
| 一键解绑    | 方向弹窗左下角一次清空该键四个方向的动作、距离与文本                                                                                    |
| 动作搜索    | 动作选择器为双列网格，支持关键字实时过滤                                                                                          |
| 键面标签    | 键帽底部显示当前绑定，已绑定的按键高亮                                                                                           |
| 大小写模式切换 | 一次触发进入大写锁定，再次触发回到小写，弥补小布无独立 Caps Lock 键                                                                       |
| 关于页     | 独立页面：免费声明、开源出处、酷安链接、赞赏码                                                                                       |

### 手势冲突规则

1. **自定义绑定优先**：某键某方向一旦绑定动作，该方向的原生手势即被屏蔽。
2. **未绑定即原生**：没绑定的方向完全保留输入法原有行为（上滑符号、左右移光标、退格上下滑等）。
3. **逐键逐方向生效**：不做整键禁用。
4. **斜向滑动按主轴判定**，一次只命中一个方向。
5. **位移小于 8dp 视为点击**，交还原生点击/长按。
6. 同一个按键的同方向只能绑定一个动作；同一动作可以绑定到多个按键。

---

## 兼容性

| 项    | 要求                                                                 |
| ---- | ------------------------------------------------------------------ |
| 系统   | Android 9.0（API 28）及以上                                             |
| 框架   | LSPosed（libxposed API 101 / 102）                                   |
| 目标应用 | 小布输入法 / Breeno Keyboard（`com.oplus.keyboard`），已在 1.9.18.17-mkt 上验证 |
| 构建环境 | JDK 17+、Android SDK Platform 37、Gradle 8.10.2                      |

> 目标应用升级后若 R8 重新混淆，反射坐标可能失效，届时需要按新版本重新适配（见 `NativePanelBridge` 的多版本坐标表）。

---

## 安装

1. 从仓库 Releases 页面下载 APK 并安装。
2. 在 LSPosed 管理器中启用「小布输入法下滑快捷键」。
3. 作用域勾选**小布输入法**（`com.oplus.keyboard`）。
4. 强制停止小布输入法（或重启设备），重新弹出键盘即可生效。

模块设置入口：

- 桌面图标（可在「通用设置」中隐藏，隐藏后仍可从 LSPosed 模块设置进入）
- LSPosed 管理器 → 模块 → 小布输入法下滑快捷键

> 所有配置保存在模块私有目录，模块不联网、不上传输入内容或配置。

---

## 使用说明

### 配置一个手势

1. 打开模块，选择「26 键」或「九宫格」页。
2. 点击任意按键 → 选择方向（上 / 下 / 左 / 右）。
3. 在动作列表中选择要执行的功能（可搜索）。
4. 点击底部「保存并应用配置」。

**长按方向项**可为该方向单独设置触发距离；**长按按键**可设置键帽显示的自定义标签。

### 关于保存与生效

- 保存时模块会写入配置并广播给输入法进程。
- 如果保存时输入法进程未运行，键盘侧缓存不会立即刷新；保持键盘弹出状态再保存一次即可。
- 保存后无需重启设备，下次弹出键盘即生效。

---

## 从源码构建

### 环境准备

```bash
# JDK 17 或更高
java -version

# Android SDK：需安装 Platform 37（预览版）与 Build-Tools
export ANDROID_HOME=/path/to/Android/Sdk
```

### 构建

```bash
git clone https://github.com/<your-name>/XiaoBu-Swipe.git
cd XiaoBu-Swipe

# Windows
gradlew.bat assembleRelease

# macOS / Linux
./gradlew assembleRelease
```

产物：`app/build/outputs/apk/release/app-release.apk`

### 调试签名与正式签名

- 默认使用 **debug 签名**打包 release，便于直接 `adb install`。
- 如需正式签名，在仓库根目录放置 `keystore.properties`：

```properties
storeFile=release.jks
storePassword=******
keyAlias=xiaobu
keyPassword=******
```

构建时会自动使用该证书；该文件已在 `.gitignore` 中排除，切勿提交。

### 版本号

版本号统一由根目录 `gradle.properties` 管理：

```properties
VERSION_CODE=51
VERSION_NAME=2.0.0
```

---

## 项目结构

> 仓库根目录即 Gradle 工程根，clone 后无需进入任何子目录。

```
XiaoBu-Swipe/
├─ app/
│  ├─ build.gradle.kts
│  ├─ libs/api-102.0.0.aar            # libxposed API 102（本地依赖，离线可构建）
│  └─ src/main/
│     ├─ AndroidManifest.xml
│     ├─ java/app/xiaobuswipe/        # 21 个源文件
│     │  ├─ MainHook.java             # 手势拦截与动作执行
│     │  ├─ MainActivity.java         # 模块设置界面
│     │  ├─ AboutActivity.java        # 关于页（独立 Activity）
│     │  ├─ Config.java               # 配置模型
│     │  ├─ ConfigCodec.java          # 配置序列化（兼容旧格式）
│     │  ├─ ActionRegistry.java       # 动作注册表
│     │  ├─ KeyResolver.java          # 按键识别
│     │  └─ NativePanelBridge.java    # 原生剪贴板/常用语面板桥接
│     ├─ res/                         # 图标、赞赏码、文案
│     └─ resources/META-INF/xposed/   # module.prop / scope.list / java_init.list
├─ docs/
│  └─ CHANGELOG.txt                   # 纯文本更新日志（便于发布到社区）
├─ .github/ISSUE_TEMPLATE/            # Bug / 功能建议表单
├─ gradle/wrapper/                    # Gradle Wrapper 8.10.2
├─ build.gradle.kts
├─ settings.gradle
├─ gradle.properties                  # 版本号与构建参数
├─ CHANGELOG.md
├─ LICENSE
├─ NOTICE
└─ README.md
```

---

## 更新日志

完整变更见 [CHANGELOG.md](CHANGELOG.md)（纯文本版：[docs/CHANGELOG.txt](docs/CHANGELOG.txt)）。

当前版本 **2.0.0（versionCode 51）** 的主要变化：四方向滑动、全按键覆盖、每键每方向触发距离、原生手势屏蔽、大小写模式切换、多键共用同一功能、动作搜索、关于页等。

---

## 常见问题

**Q：设置保存了但没生效？**  
A：确认保存时输入法正在运行；若仍不生效，可强制停止小布输入法后重新弹出键盘。

**Q：为什么「大小写模式切换」会有两次震动？**  
A：该动作走输入法自身的 Shift 双击逻辑才能进入持续大写锁定，两次震动属于输入法原生反馈。

**Q：升级后「关于」按钮出现红点？**  
A：红点表示当前版本还未查看过关于页，进入一次即消失；每次版本更新后会重新出现。

**Q：更新模块会丢失配置吗？**  
A：不会，配置保存在模块私有目录，覆盖安装自动保留，旧格式会自动兼容。

---

## 反馈

- 酷安主页：<https://www.coolapk.com/u/28792620>
- 发布动态：<https://www.coolapk.com/feed/74524760>
- 问题反馈请附：模块版本、小布输入法版本、系统版本、复现步骤与 LSPosed 日志。

---

## 致谢与许可

本模块**改自开源项目 [WeType-Swipe](https://github.com/waoui/WeType-Swipe)**（作者 waoui，Apache License 2.0），感谢原作者的探索与分享。

本项目同样以 **Apache License 2.0** 发布，详见 [LICENSE](LICENSE) 与 [NOTICE](NOTICE)。

模块完全免费，禁止任何形式的倒卖。若你是通过付费渠道获取的，说明遇到了倒卖，请直接使用本免费版本。

## 免责声明

本项目仅用于个人学习与输入效率提升，使用前请自行评估风险。因使用本模块导致的任何设备异常、数据丢失或输入法行为变化，作者不承担责任。请勿将本模块用于任何违反当地法律法规的场景。
