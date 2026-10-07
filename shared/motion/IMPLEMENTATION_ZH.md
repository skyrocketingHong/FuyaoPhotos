# 原生动效、导航与外观

[English](IMPLEMENTATION.md)

## 上游实现

| 功能 | 实现与来源 |
| --- | --- |
| Android 粒子 | 使用 [Nagram](https://github.com/NextAlone/Nagram/tree/b8db62a65e1e4dee34d92bff412548ef628ddb06) 原始 `thanos_vertex.glsl`、`thanos_fragment.glsl`，从 `ThanosEffect.java` 移植 TextureView/EGL 宿主。GPU transform feedback 在两个 28 字节布局的缓冲区间更新纹理坐标、位置、速度和寿命，保留原公式、密度缩放、随机寿命与照片模式参数。 |
| Apple 粒子 | 使用 [Nagram-iOS](https://github.com/NextAlone/Nagram-iOS/tree/5b72e0fb7dcbc762568f567c823485e42d32e4d7) 原始 `DustEffectShaders.metal`、`loki.metal`、`loki_header.metal`。iOS、iPadOS、macOS 共用 MTKView 宿主，替换 Telegram 应用专属 MetalEngine 桥接；原计算与实例化四边形 shader 保持不变，精确分派线程防止初始化越界。 |
| Android 液态底栏 | 实际依赖 Kyant Backdrop 1.0.6、Shapes 1.2.0，并移植官方 [AndroidLiquidGlass catalog](https://github.com/Kyant0/AndroidLiquidGlass/tree/1.0.6/catalog/src/main/java/com/kyant/backdrop/catalog) 组件；折射、模糊和背景组合调用上游库。 |
| MD3 模糊与主题 | Material 3 原生底栏独立使用同一 Backdrop 模糊，不依赖液态底栏开关。Material Color Utilities 5.0.1 生成 HCT 颜色角色；独立主题分类包含外观、壁纸或自定义种子、调色板、色彩规范、对比度、纯黑、底栏材质与返回转场。 |
| 设置与返回 | 设置容器变换参考 FlClash 的 [OpenContainer](https://github.com/chen08209/FlClash/blob/4b59eca853778d4e7be3de26589252889c899bc5/lib/widgets/open_container.dart)；主题和材质选项参考 [IntentX](https://github.com/wxxsfxyzm/IntentX/tree/5870f2f849c4b17518444518f9bc8a8f7e2972e7) 与 [AppMarket](https://github.com/YXBwbWFya2V0/AppMarket/tree/af34ee9c3b3c8794b27536ae680f4dc3f335f9a9)。手势进度和取消恢复交给 Navigation Compose，不使用 ApplicationInfo 私有反射。 |

Telegram Desktop 在 `d8594c011756265de4385408540bd9f7c787a003` 的 Qt 粒子实现保留为参考。Apple 各平台共用原 Metal 实现。前轮 Canvas 粗块近似动画和按名称猜色彩空间的逻辑已经移除。

## 照片与状态边界

卡片、元数据、色彩均提供关闭入口，有未保存修改时保留放弃确认。关闭仅清理实际拥有者的私有编辑会话，保留原片和已导出文件；共享照片页面按对应会话拥有者处理。换图先实际解码可用的新预览，再提交替换并发出旧图离场事件；取消或准备失败保留旧会话和元数据草稿。

粒子使用独立 SDR 副本，原生 HDR 显示和导出资源不被扁平化。当前缩放、平移、景深和信息卡覆盖保留；GPU 首帧前仍显示旧副本。真实数据操作不等待动画完成。减少动态效果、后台、离页和到达截止时间时释放视觉资源。Android 旋转重建后由当前界面接收替换事件，迟到的元数据保存不能恢复已丢弃草稿。

背景源按各自实际坐标和层级注册，仅包含 SDR 氛围底图与普通控件、列表。元数据和色值列表可滚到底栏后，末项通过内容留白避让；固定照片、当前像素摘要和选项滚轮保持可见。底栏空间只消费一次。

## 共用参数与验证

`tokens.json` 和 `scripts/generate-motion-tokens.py` 继续维护普通控件、进度时长与快照限制，两端构建脚本检查生成常量。粒子物理由平台原始 shader 决定，不使用公共 UI 参数替代。

源码哈希测试验证原 shader。Apple 使用真实离屏 Metal 测试初始化、更新边界、寿命、图像方向与 Alpha，无 Metal 设备时明确跳过 GPU 用例。Android 源码测试验证 shader 一致性和网格分配，并提供源像素解码与替换安全性的设备回归。自动色值保留源浮点分量，显式空间从同一样本转换，只有 8 位 RGB 和 HEX 裁剪。

粒子许可位于 `LICENSES/`，导航版本、作者、移植边界与许可位于 `third_party/AndroidNavigation/`，Android APK 同时包含相关许可。设备视觉、Android GPU 运行、手势、辅助功能和帧率是独立验收项。
