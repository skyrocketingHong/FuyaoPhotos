# 原生动效、导航与外观

[English](IMPLEMENTATION.md)

## 上游实现

| 功能 | 实现与来源 |
| --- | --- |
| Android 离场消散 | 换图和镜头行删除保留 [Nagram](https://github.com/NextAlone/Nagram/tree/b8db62a65e1e4dee34d92bff412548ef628ddb06) 原始 `thanos_vertex.glsl`、`thanos_fragment.glsl`，从 `ThanosEffect.java` 移植 TextureView/EGL 宿主。GPU transform feedback 在两个 28 字节布局的缓冲区间更新纹理坐标、位置、速度和寿命，保留原公式、密度缩放、随机寿命与照片模式参数。 |
| Apple 离场消散 | 换图和镜头行删除保留 [Nagram-iOS](https://github.com/NextAlone/Nagram-iOS/tree/5b72e0fb7dcbc762568f567c823485e42d32e4d7) 原始 `DustEffectShaders.metal`、`loki.metal`、`loki_header.metal`。iOS、iPadOS、macOS 共用 MTKView 宿主，替换 Telegram 应用专属 MetalEngine 桥接；原计算与实例化四边形 shader 保持不变，精确分派线程防止初始化越界。 |
| 关闭照片后汇聚 | 独立的 Fuyao 实现让可见照片散开，再汇聚成原生导入卡片的真实捕获内容。Android 使用 OpenGL ES 3 实例化四边形，Apple 使用独立 Metal 四边形管线；新解析式 shader 不替换或修改固定版本的上游消散 shader。 |
| Android 液态底栏 | 实际依赖 Kyant Backdrop 1.0.6、Shapes 1.2.0，并移植官方 [AndroidLiquidGlass catalog](https://github.com/Kyant0/AndroidLiquidGlass/tree/1.0.6/catalog/src/main/java/com/kyant/backdrop/catalog) 组件；折射、模糊和背景组合调用上游库。 |
| MD3 模糊与主题 | Material 3 原生底栏独立使用同一 Backdrop 模糊，不依赖液态底栏开关。Material Color Utilities 5.0.1 生成 HCT 颜色角色；独立主题分类包含外观、壁纸或自定义种子、调色板、色彩规范、对比度、纯黑、底栏材质与返回转场。 |
| 设置与返回 | 设置容器变换参考 FlClash 的 [OpenContainer](https://github.com/chen08209/FlClash/blob/4b59eca853778d4e7be3de26589252889c899bc5/lib/widgets/open_container.dart)；主题和材质选项参考 [IntentX](https://github.com/wxxsfxyzm/IntentX/tree/5870f2f849c4b17518444518f9bc8a8f7e2972e7) 与 [AppMarket](https://github.com/YXBwbWFya2V0/AppMarket/tree/af34ee9c3b3c8794b27536ae680f4dc3f335f9a9)。手势进度和取消恢复交给 Navigation Compose，不使用 ApplicationInfo 私有反射。 |

Telegram Desktop 在 `d8594c011756265de4385408540bd9f7c787a003` 的 Qt 粒子实现保留为参考。Apple 各平台共用原消散渲染器和独立的 Fuyao 关闭汇聚渲染器。前轮 Canvas 粗块近似动画和按名称猜色彩空间的逻辑已经移除。

## 照片与状态边界

卡片、元数据、色彩均提供关闭入口，有未保存修改时保留放弃确认。关闭仅清理实际拥有者的私有编辑会话，保留原片和已导出文件；共享照片页面按对应会话拥有者处理。换图先实际解码可用的新预览，再提交替换并发出旧图离场事件；取消或准备失败保留旧会话和元数据草稿。

Apple 照片离场事件记录捕获拥有者、一次消费票据和单调时钟绝对期限。关闭动作显式传递捕获拥有者，换图事件仍按有效捕获来源选择。仅当前选中且可见的照片页注册捕获并消费自己的事件，根 Tab 切换同步取消视觉事件；返回保留的 Tab 不重播旧事件，同一副本只允许创建一次原生渲染器。多窗口交互仍是独立的设备验收项。

粒子使用独立 SDR 副本，原生 HDR 显示和导出资源不被扁平化。当前缩放、平移、景深和信息卡覆盖保留；GPU 首帧前仍显示旧副本。真实数据操作不等待动画完成。减少动态效果、后台、离页和到达截止时间时释放视觉资源。Android 旋转重建后由当前界面接收替换事件，迟到的元数据保存不能恢复已丢弃草稿。

背景源按各自实际坐标和层级注册，仅包含 SDR 氛围底图与普通控件、列表。元数据和色值列表可滚到底栏后，末项通过内容留白避让；固定照片、当前像素摘要和选项滚轮保持可见。底栏空间只消费一次。

## 关闭照片后汇聚到占位卡片

关闭照片先创建视觉请求，再立即继续真实会话清理。准备目标期间保留源副本；目标使用发起关闭页面的实际卡片、元数据或色彩导入卡，不因共享照片的数据拥有者不同而换成另一页。

- Android 将正常 `PhotoImportCard` 的绘制记录到 `GraphicsLayer`，在可用帧完成后取得真实纹理和根坐标边界。仅在 `busy == false` 时捕获，避免使用清理期间的临时加载状态。先记录卡片，再暂停最终显示，不捕获零透明度或禁用控件状态。元数据在稳定视口注册可见位图，并从布局回调即时更新几何；硬件位图先独立复制再交给软件画布，不改动原图。
- Apple 在隐藏的原生捕获宿主中渲染与实际页面相同的 `PhotoImportForm`。共用外观修饰器只复制公开可写的配色、语言、动态字号、显示缩放及 macOS 控件活跃状态，不覆盖宿主整份 SwiftUI 环境；对比度和减少透明度参与失效检测，并由原生宿主继承系统设置。与原生空 Form 比较，确定实测内容周围的分区背景及其原生内边距和圆角，不使用手绘矩形替代。返回捕获结果时仍须匹配请求、捕获尝试、外观及实际内容边界。隐藏动作保留正常按钮外观，通过动作守卫、快捷键、焦点、命中和辅助功能限制交互。

两端均采用 880 ms 解析式过渡、650 ms 目标捕获期限和 2400 ms 请求总寿命。网格从 3 个逻辑单位的粒子单元开始，最多 24000 个粒子。散开阶段在 `t = 0...0.22` 使用 `cubic-bezier(0.23, 1, 0.32, 1)` 快速达到峰值；汇聚在 `t = 0.22...0.90` 使用 `cubic-bezier(0.77, 0, 0.175, 1)` 加速后柔和落定，散开量同步按 `1 - travel` 收回。源纹理和目标预乘 Alpha 纹理在 `t = 0.35...0.88` 使用同一汇聚曲线混合。单元尺寸跟随源与目标几何，途中乘以 `1 - 0.55 * scatter` 收缩。CPU 每帧反解一次 Bezier 时间坐标，将权重传给 shader。在 `t = 0` 精确还原源图；从 `t = 0.90` 起目标位置、纹理及粒子尺寸完全固定，最后 88 ms 不再移动。端点顶点直接由纹理坐标映射矩形，避免粒子中心相减造成的浮点误差。

交还前隐藏原生目标的输入与辅助功能。Android 的 `focusProperties` 必须位于 `focusGroup` 之前，使 `onEnter` 作用于组边界而非子控件。Android 的 GPU 视图初始隐藏，由同一绘制入口选择源副本、已呈现的 GPU 帧或固定目标桥接图。`SurfaceTexture` 确认终帧 EGL presentation timestamp 后，先绘制桥接图再停止 GPU；实际目标完成正常原生层记录，并核对同一请求、拥有者、边界和外观后，在同一绘制帧取代桥接图，避免半透明像素重复合成。取消时立即隐藏并停止对应 GPU。Apple 在同一主线程无动画事务中移除已完成的 GPU 副本并显示外观未变的原生卡片。两端均不做第二次淡入。键盘收起造成的画布高度变化只更新视口，不直接终止播放；目标真实重排、取消、新导入、后台、减少动态效果、捕获失败、渲染失败或超时均回到原生占位界面。导航和选片、导入入口同步取消，包含权限及照片包导入路径；旧代次和旧捕获尝试不能替换或结束新请求。

## 共用参数与验证

`tokens.json` 和 `scripts/generate-motion-tokens.py` 维护普通控件、进度时长、快照限制及 Fuyao 汇聚契约，两端构建脚本检查生成常量。保留的离场消散路径仍使用各平台原始 shader 物理过程。

源码哈希测试验证原 shader，汇聚测试共用 `reassembly-progress.json` 高精度参考样本，并以密集进度检查覆盖位移单调性、散开边界、加减速、落定速度与固定终态，另覆盖网格上限、旧请求和期限。Apple 测试包含离屏 Metal 初始化、更新边界、寿命、图像方向与 Alpha，无 Metal 设备时明确跳过 GPU 用例。Android 设备测试包含 ES 3 端点回读，核对矩形位置、纹理方向和预乘 Alpha，并覆盖硬件位图快照；编译这些测试不等于实际执行 Android GPU 渲染。既有源像素解码和替换安全性回归继续保留。自动色值保留源浮点分量，显式空间从同一样本转换，只有 8 位 RGB 和 HEX 裁剪。

粒子许可位于 `LICENSES/`，导航版本、作者、移植边界与许可位于 `third_party/AndroidNavigation/`，Android APK 同时包含相关许可。设备视觉、Android GPU 运行、手势、辅助功能和帧率是独立验收项。
