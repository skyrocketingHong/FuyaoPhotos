# 动效实现

[English](IMPLEMENTATION.md)

`tokens.json` 是时长、粒子数量和几何参数的权威源。修改后在仓库根目录运行 `python3 scripts/generate-motion-tokens.py`。两端构建脚本检查已提交的常量与源文件是否一致。`particle-frames.tsv` 提供共同的数值契约，由 Kotlin 和 Swift 测试读取。

Apple 的 iOS、iPadOS 和 macOS 共用同一套实现；Android 各页面复用 Compose 组件。绘制保留原生实现：SwiftUI Canvas 和 Compose GraphicsLayer 只获取待删除表单行的一次快照。真实列表立即修改，暂留的行不能操作，同时最多保留三项，滚出屏幕也会按时释放。保存进度对应已处理数量，同时表达当前操作仍在进行。减少动态效果时去掉位移和旋转，离页或进入后台时停止动画。

Android 设置页使用 Navigation Compose 内的 `sharedBounds`，沿用返回手势进度和取消恢复，草稿确认单独处理。外观修改先保存，再揭示新的设置界面；不截取照片工作区。底部保留 Material 3 导航项，使用照片的独立 SDR 副本作为材质背景；API 31 及以上采用 RenderEffect 模糊，API 33 及以上增加 AGSL 边缘折射。选中胶囊通过可中断弹簧移动。旧系统或关闭玻璃选项时使用原生纯色导航。此材质不采样 HDR 主照片，也不声称复现 Apple 系统 Liquid Glass。

## 源码参考

以下仓库固定到对应提交进行研究。本次组件独立实现，没有复制上游源码文件、素材、渲染引擎或依赖。

| 参考 | 提交 | 阅读的实现 |
| --- | --- | --- |
| [Nagram](https://github.com/NextAlone/Nagram) | `b8db62a65e1e4dee34d92bff412548ef628ddb06` | `ThanosEffect.java`、`thanos_vertex.glsl`、`RadialProgressView.java`、`RadialProgress2.java`：内容碎片、依次启动的消散及进度插值。 |
| [Nagram iOS](https://github.com/NextAlone/Nagram-iOS) | `5b72e0fb7dcbc762568f567c823485e42d32e4d7` | `DustEffectLayer.swift`、`DustEffectShaders.metal`、`RadialProgressContentNode.swift`：纹理粒子、有限生命周期与动画释放。 |
| [Telegram Desktop](https://github.com/telegramdesktop/tdesktop) | `d8594c011756265de4385408540bd9f7c787a003` | `thanos_effect_renderer.cpp`、`thanos_init.comp`、`thanos_update.comp`：网格粒子与错时消散。该仓库是包含 macOS 在内的 Qt 桌面客户端。 |
| [FlClash](https://github.com/chen08209/FlClash) | `4b59eca853778d4e7be3de26589252889c899bc5` | `lib/widgets/open_container.dart`：实测起止边界、300 ms 容器变换及可反向的转场。 |
| [AppMarket](https://github.com/YXBwbWFya2V0/AppMarket) | `af34ee9c3b3c8794b27536ae680f4dc3f335f9a9` | `FloatingBottomBar.kt`、`Lens.kt`、`AppNavigation.kt`、`ThemeSettingsScreen.kt`、`App.kt`：背景层分离、选中态移动、导航和外观选项。所读入口使用 `ThemeController(System)`；Fuyao 的显式外观选择与揭示转场是本项目扩展。 |

未采用 AppMarket 对 `ApplicationInfo` 的私有反射。导航使用公开的[共享元素预测性返回 API](https://developer.android.com/develop/ui/compose/animation/shared-elements/navigation)，快照使用公开的 [Compose 图层 API](https://developer.android.com/develop/ui/compose/graphics/draw/modifiers)。Android 专属导航与光学实现不引入 Apple 客户端，Apple 保留原生导航与系统玻璃。

单元测试和构建只验证代码与数值边界。手势观感、快照还原、AGSL 绘制、辅助功能与帧率仍需设备验收。
