<p align="center"><a href="README.md">English</a> | 简体中文</p>
<h1 align="center">Fuyao Photo Info</h1>
<p align="center">把拍摄信息留在照片里，而不是另加一圈边框。</p>
<p align="center">Android 8.0+ · Kotlin · Jetpack Compose · Material 3 · 1.0.0 源码首版</p>

**交付状态：代码已编写；53 项纯 Kotlin 核心检查通过。Android 编译、Lint、模拟器与真机测试尚未完成，本包不含 APK。** 当前执行环境不是用户的 Mac，未修改 `/Volumes/Thunderbolt 5 SSD (2TB)/Code/GitHub/FuyaoPhotoInfo`，也未读取该目录的参考图。详细验证记录见 [BUILD_STATUS.md](docs/BUILD_STATUS.md)。

## 功能

通过系统照片选择器导入图片，读取可用 EXIF、应用镜像/旋转方向，允许编辑九项信息。清空字段即隐藏；设备、署名、地点为暖黄，其余拍摄参数为白色。设备并非 iPhone 时不会强行写成 iPhone。地点由用户填写，不发送坐标到地理编码服务；缺失的等效焦距、倍率或镜头类型不会猜测。

右下角圆角信息卡直接覆盖照片内部。以 1527×859 为参考，默认卡片 215×168、右距 77、下距 35、圆角 20、内边距 19、等宽字号 10.5、行高 12.5；灰底 `#5A5A5A`、不透明度 60%、模糊 25。所有图像尺寸相关参数按照片短边缩放。文字换行而非缩小字号或省略；内容超过默认高度时向上扩展，超过整张图片时明确报错。无描边、阴影或装饰线。

编辑区提供卡片缩放、透明度、模糊、右/下边距和圆角调节；支持原图对照、全屏缩放预览、系统等宽字体和自有授权字体导入。预览与导出共用 `CardLayoutEngine` 和 `CardRenderer`，不导出屏幕截图。

导出重新解码原图并生成新 JPEG（质量参数 97）或 PNG。Android 10+ 保存到相册 `Pictures/FuyaoPhotoInfo`，Android 8/9 使用系统“另存为”；不覆盖原片。导出成功后可通过系统分享面板分享。进程重建时通过 `SavedStateHandle` 恢复当前草稿，署名另存为本机默认值。

## 在指定 Mac 目录中安装源码并构建

将源码包解压到临时目录，在**解压后的 `FuyaoPhotoInfo` 根目录**执行：

```bash
python3 scripts/install-workspace.py --build
```

默认目的地就是：

```text
/Volumes/Thunderbolt 5 SSD (2TB)/Code/GitHub/FuyaoPhotoInfo
```

脚本保留已有参考图、`.git`、本机配置与私钥；同名文件内容不同时先列出冲突并停止，不覆盖已有代码。已在目的地时只执行构建。需要 Python 3、JDK 17 与 Android SDK Platform 37；不自动安装 SDK、不隐式接受 SDK 许可。

如已自行放入目标目录，可执行：

```bash
./scripts/build-macos.sh
```

此命令依次执行 `:app:testDebugUnitTest`、`:app:lintDebug`、`:app:assembleDebug`。首次 `gradlew` 是下载引导器：校验官方 Gradle 9.6.1 分发包后，生成并替换为标准 Gradle Wrapper。说明见 [Gradle 引导说明](gradle/wrapper/README.md)。因此本源码首次导入 Android Studio 前，应先在终端完成引导。SDK 不在默认位置时设置 `ANDROID_HOME`；Java 不符合要求时设置 `JAVA_HOME`。

构建成功后 APK 位于：

```text
app/build/outputs/apk/debug/app-debug.apk
```

安装到连接设备：

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## 工程模板与结构

参考本人现有 [FuyaoColorPicker](https://github.com/skyrocketingHong/FuyaoColorPicker) 的包名前缀、职责分层、Material 3 界面、双语文档、签名配置和四类构建变体。没有复制其取色逻辑、历史色表或照片资产。应用 ID 为 `ing.fuyaoskyrocket.photoinfo`；工程并非 Apple 官方产品。

```text
app/src/main/java/ing/fuyaoskyrocket/photoinfo/
├── data/photo/       照片拷贝、EXIF、解码与方向处理
├── data/export/      原尺寸编码、EXIF 白名单、相册/文件输出
├── domain/model/     字段与样式
├── domain/metadata/  参数格式化
├── domain/layout/    短边坐标系、换行、几何排版
├── domain/render/    纯 Kotlin 模糊内核
├── platform/         Bitmap 卡片渲染与私有字体导入
├── presentation/     编辑状态、恢复、预览与导出调度
└── ui/               页面、控件、预览与主题
app/src/test/         与离线脚本共用的核心测试
app/src/androidTest/  方向、像素边界与导出验证（尚未执行）
scripts/              构建、静态检查与安全导入脚本
.run/                 Android Studio 共享运行配置
.github/workflows/    构建工作流定义（尚未在 GitHub 执行）
docs/                 样式、架构、验证和已知边界
references/           参考素材说明，不包含原图
```

## 构建变体与签名

| 变体 | 应用 ID 后缀 | Gradle 任务 | 签名 |
| --- | --- | --- | --- |
| Debug | `.debug` | `:app:assembleDebug` | 本机生成的调试密钥 |
| Debug Unsigned | `.debug.unsigned` | `:app:assembleDebugUnsigned` | 无 |
| Release | 无 | `:app:assembleRelease` | `signing.properties` 配置完整时使用私钥，否则无 |
| Release Unsigned | `.unsigned` | `:app:assembleReleaseUnsigned` | 无 |

将 `signing.properties.example` 复制为 `signing.properties` 后填写签名参数。**Release 不会静默回退到调试密钥。** 无签名的 APK 不能直接安装；本包不提供签名私钥。发布前须完成 Android 编译、设备测试和签名校验。

依赖版本沿用现有 Android 模板：AGP 9.2.1、Gradle 9.6.1、Compose 插件 2.4.10、Compose BOM 2026.06.01、compile/target SDK 37；新增 AndroidX ExifInterface 1.4.2。版本锁定不等于已验证兼容，完整依赖解析和 Android 编译尚待本机执行。

## 字体与素材

没有复制、嵌入或分发 macOS 字体文件。默认使用 Android `Typeface.MONOSPACE`；在“样式→导入字体”选择具有相应使用权、不超过 10 MB 的 TTF、OTF 或 TTC。导入文件仅保存在应用私有目录，不进入源码仓库。标准 Apple 字体许可不可直接当作 Android 嵌入授权；请按自己的具体许可判断用途，参见 [Apple 字体页面](https://developer.apple.com/fonts/)。

`references/` 中只有说明。当前样式参数来自用户提供的文字测量，尚未与本机三张参考截图进行像素级核对；不得把此状态称为“Apple 官方规范”或“完全一致”。

## 输出边界与隐私

首版输出 **8 位 sRGB / SDR 静态图**。不承诺保留 Display P3、10/16 位、Ultra HDR 增益图、Live Photo、RAW 工作流或动画；HEIC 等格式能否导入取决于系统解码器。JPEG 是重新编码而非无损封装，PNG 的无损只针对此次 SDR 渲染结果。预览使用采样图，全尺寸渲染共享排版参数，但模糊采样和像素栅格化可能存在细微差异。

应用不声明联网、位置、相机或广泛存储权限，不上传照片。导出可选择保留来源中的拍摄参数白名单，不复制 GPS、序列号、MakerNote、XMP 或缩略图。编辑信息卡不会重写拍摄参数白名单中的原始值；手动填入卡片的地点和署名仍会直接出现在图像像素中。

导出前检查内存，不足时失败并提示，不自动降低分辨率。导入限制为 512 MB / 200 MP；这些上限不是保证可导出的尺寸。当前仅支持单张图片，暂无批量、水印预设库、拖拽自由定位或后台导出服务。

## 验证

离线域模型与算法测试：`./scripts/test-core.sh`（需要 Kotlin CLI 1.9+）。Kotlin 语法、XML 和资源对应检查：`./scripts/check-syntax.sh`。完整 Android 验证：`./scripts/build-macos.sh`；设备测试：`./gradlew :app:connectedDebugAndroidTest`。

已执行结果与尚未执行事项严格分开记录于 [BUILD_STATUS.md](docs/BUILD_STATUS.md) 和 [验证清单](docs/VALIDATION.md)。

## 许可证

源代码按 `AGPL-3.0-only` 声明授权，见 [LICENSE](LICENSE) 中的授权声明与官方完整条款链接。[NOTICE](NOTICE) 记录模板来源与第三方边界。任何导入字体、参考图和用户照片均不因本项目的源代码许可而获得额外授权。
