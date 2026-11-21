# 贡献指南

## 提 issue 前

1. 确认模块已在 LSPosed 中启用，且作用域勾选了**小布输入法**（`com.oplus.keyboard`）。
2. 确认改完配置后**保持键盘在运行状态**再保存 —— 否则键盘侧缓存不会立即刷新。
3. 到 README 的「常见问题」里对照一遍，很多现象有明确解释。
4. 仍然异常再提 issue，并附：模块版本、小布输入法版本、系统版本、复现步骤、LSPosed 日志。

## 开发环境

- JDK 17 或 21
- Android SDK，需安装 `android-37` 平台（`compileSdk = 37`，libxposed interface 102 的要求）
- 本地 AAR：`app/libs/api-102.0.0.aar`（libxposed API，仓库内已附带，无需额外下载）
- 构建：`./gradlew :app:assembleRelease`

## 代码约定

- 宿主的 `androidx` 被 R8 重名化，模块运行期只能加载自身 dex 中的类。**不要引入任何 `androidx.*`
  运行期依赖**；需要宿主类时用 `classLoader` 反射获取。
- Hook 点集中在 `MainHook`，触摸与按键命中判定在 `KeyboardCompat` / `KeyResolver`。新增 Hook 请写清
  「为什么是这个方法」，并说明对原生手势的影响范围。
- 手势判定阈值、优先级等常量集中在 `Config` 与 `KeyResolver`，不要在业务代码里散落魔法数字。
- 提交前请确保工程能离线构建通过（`--offline`），避免引入新的远程依赖。

## 提交 PR

- 一个 PR 只做一件事，便于审查与回滚。
- 说明改动动机、影响范围、验证方式（真机型号 + 输入法版本 + 验证步骤）。
- 涉及 UI 的改动请附截图。
- 不要提交构建产物、签名文件、`local.properties`。

## 不符合预期的改动

以下方向会被拒绝：

- 引入统计、埋点、联网上报
- 增加需要额外权限的能力
- 破坏与上游 [WeType-Swipe](https://github.com/waoui/WeType-Swipe) 的许可条款（本项目为 Apache-2.0，
  派生作品需保留 `NOTICE` 中的署名）
