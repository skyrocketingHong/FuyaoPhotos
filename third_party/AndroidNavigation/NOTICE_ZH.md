# Android 导航与主题

Android 导航实际链接 Kyant 的 `io.github.kyant0:backdrop:1.0.6` 和
`io.github.kyant0:shapes:1.2.0`，许可均为 Apache-2.0。
`ui/components/liquid/` 中的液态标签、交互高光和阻尼拖动代码移植自
AndroidLiquidGlass 官方示例，固定提交为
`896a94a3ade1cc1a940b92365f942a34971fecda`（标签 `1.0.6`）。

来源：https://github.com/Kyant0/AndroidLiquidGlass/tree/1.0.6/catalog/src/main/java/com/kyant/backdrop/catalog
形状库：https://github.com/Kyant0/Shapes

移植调整包括 Material 3 主题色与语义字体、减少动态效果支持、选中项无障碍
语义、禁用状态、取消手势保留选择，以及与 Navigation Compose 的选中项同步。
折射、模糊、色散、高光和多背景合成实际调用上游 Backdrop 库。每个安全页面
图层独立注册来源，不将 HDR 主照片视口纳入背景采样或扁平化。

本次选用与现有 AndroidX Compose 栈相容的 Android 版。2.x 多平台版本会引入
更新的 Compose 依赖族，本次接入无需整体升级 Compose。

主题生成实际链接 Jordon de Hoog 的
`com.materialkolor:material-color-utilities:5.0.1`（MIT）。其中 Google
Material Color Utilities 算法（Copyright 2021-2022 Google LLC）为 Apache-2.0。
所有色彩角色从 HCT 调色板、对比度及所选 2021 或 2025 色彩规范生成；使用无
Compose 依赖的算法模块，避免引入另一套 Compose 运行时。

来源：https://github.com/jordond/MaterialKolor
原始算法：https://github.com/material-foundation/material-color-utilities

主题选项以及导航材质与返回过渡的独立设置参考 IntentX 提交
`5870f2f849c4b17518444518f9bc8a8f7e2972e7`（GPL-3.0）和 AppMarket。
未复制 IntentX 源码；Fuyao Photos 保留 Navigation Compose 的可随手势推进的
过渡机制，不替换为 IntentX 所用的 Miuix 导航实现。

参考：
- https://github.com/wxxsfxyzm/IntentX/tree/5870f2f849c4b17518444518f9bc8a8f7e2972e7
- https://github.com/YXBwbWFya2V0/AppMarket

许可原文同时保存在本目录和 Android APK 的 `assets/licenses/` 中。
