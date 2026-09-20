<p align="center"><a href="README.md">English</a> | 简体中文</p>
<h1 align="center">Fuyao Photo Info</h1>
<p align="center">直接嵌入照片的紧凑磨砂拍摄信息卡。</p>
<p align="center">
  <img src="https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android&amp;logoColor=white" alt="Android 8.0+">
  <img src="https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4" alt="Jetpack Compose">
  <img src="https://img.shields.io/badge/License-AGPL--3.0--only-blue" alt="AGPL-3.0-only">
</p>

在本机处理图片、可选调用系统地名服务的 Android 单图编辑器。读取可用 EXIF，将可编辑信息卡叠加在照片内部，不增加底部边框、不改变照片尺寸。实际构建结果与设备验证边界见 [BUILD_STATUS.md](docs/BUILD_STATUS.md)。

## 功能

- 设置页独立保存默认摄影者，EXIF Artist 优先；可显式将默认署名应用到当前照片。
- 使用系统照片选择器或“从文件导入”读取原片，处理包含镜像在内的八种 EXIF 方向。
- 编辑设备、摄影者、地点、镜头、像素数、等效焦距、曝光时间、光圈和 ISO。缺失字段自动隐藏。根据照片 GPS 解析城市与国家，按保存的镜头配置或自定义 1× 焦距识别倍率。设置中可通过 Camera2 扫描系统可见镜头，手动保存机型、名称、物理／等效焦段和倍率端点，无机型硬编码；所有字段仍可手动修改。
- 设备与署名使用暖黄色，拍摄参数使用白色。等宽文字保持不透明，背景为模糊后的照片叠加半透明中性灰。
- 卡片按照片短边统一缩放。长文字沿同一左边界换行；常规署名多换一行时保持参考卡片尺寸，更长内容向上扩展，不缩字、不省略。
- 调整卡片比例、独立字号（80%～180%）、不透明度、模糊、圆角及右侧和底部边距。默认字号保持参考样式的 100%，增大文字时保持卡片宽度，仅在需要时增加高度；支持原图对照、全屏缩放预览。
- 优先使用本地构建附带的 SF Mono Regular，缺失时使用 Android 等宽字体。可导入 TTF/OTF/TTC，并重置回默认字体。
- 通过与预览相同的渲染器导出原尺寸 JPEG（质量参数 97）或 PNG。Android 10+ 保存至 `Pictures/FuyaoPhotoInfo`，Android 8/9 使用系统另存为；导出后支持系统分享。

1527 × 859 基准采用 215 × 168 卡片、右侧 77 px / 底部 35 px 边距、20 px 圆角、19 px 水平内边距、10.5 px 字号、12.5 px 行高和 7 px 分组间距。背景起点为 `#5A5A5A`、60% 不透明度和约 25 px 模糊。这是依据所提供截图的复刻参数，不是 Apple 官方规范，详见 [STYLE_SPEC.md](docs/STYLE_SPEC.md)。

## 环境要求与快速开始

- Android 8.0 / API 26 及以上。
- JDK 17 或兼容的更新 JDK、Android SDK Platform 37；未缓存依赖需要联网下载。
- 工程附带官方 Gradle 9.6.1 Wrapper，并固定分发包校验值。

在项目根目录执行：

```bash
# macOS 可选：复制本项目使用的本机字体。
bash scripts/copy-macos-font.sh

# 单元测试、Debug/Release Lint 和 Debug/Release APK。
bash scripts/build-macos.sh
```

构建脚本兼容 `android-37` 和 `android-37.0` SDK 目录。必要时设置 `JAVA_HOME` 和 `ANDROID_HOME`；脚本不会安装 SDK 或接受许可证。构建失败时返回非零状态，应查看首个具体错误。脚本允许追加 Gradle 参数。

可安装的 Debug 包位于 `app/build/outputs/apk/debug/app-debug.apk`：

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

安装成功输出 `Success`，前提是 `adb devices` 中已有授权设备。卸载 Debug 应用及其私有草稿、设置可执行 `adb uninstall ing.fuyaoskyrocket.photoinfo.debug`，已导出至相册的照片保留。清理生成的构建产物可执行 `./gradlew clean`。

## 构建变体与签名

| 变体 | 应用 ID 后缀 | 签名 |
| --- | --- | --- |
| Debug | `.debug` | 本机调试密钥，可安装 |
| Debug Unsigned | `.debug.unsigned` | 无 |
| Release | 无 | 配置私钥时签名，否则未签名 |
| Release Unsigned | `.unsigned` | 无 |

任务分别为 `assembleDebug`、`assembleDebugUnsigned`、`assembleRelease`、`assembleReleaseUnsigned`。Release 启用 R8 与资源压缩。参照 `signing.properties.example`，在被忽略的 `signing.properties` 中配置私钥。Release 不会静默回退为调试签名，未签名 APK 不能直接安装；工程不附带私有签名密钥。

## 技术栈与项目结构

包名前缀、四构建变体、共享运行配置和双语文档参考 [FuyaoColorPicker](https://github.com/skyrocketingHong/FuyaoColorPicker)，README 结构同时参考 [FuyaoLocale](https://github.com/skyrocketingHong/FuyaoLocale)。未复制其应用代码或图片资源。

AGP 9.2.1 · Gradle 9.6.1 · Compose 编译器 2.4.10 · Compose BOM 2026.06.01 · compile/target SDK 37 · ExifInterface 1.4.2。应用 ID：`ing.fuyaoskyrocket.photoinfo`。

| 目录 | 职责 |
| --- | --- |
| `data/photo`、`data/export` | 私有草稿、EXIF、解码、编码与发布 |
| `domain/model`、`metadata`、`layout`、`render` | 字段、格式化、几何与模糊 |
| `platform` | Android 位图合成与字体加载 |
| `presentation`、`ui` | 编辑状态恢复、操作协调与 Material 3 控件 |
| `scripts`、`.run`、`.github/workflows` | 本机检查、构建与 CI 定义 |
| `references` | 本机参考截图，不纳入版本管理 |

GPS、默认摄影者与镜头档案规则见[元数据识别说明](docs/METADATA.md)。数据流和恢复边界见 [ARCHITECTURE.md](docs/ARCHITECTURE.md)。历史 `install-workspace.py` 脚本用于将解压的源码包导入其他工作区；已在当前项目目录中工作时无需执行。

## 字体、隐私与输出边界

当前本机工程包含从 macOS Terminal 复制的 SF Mono Regular，应用自动加载，基于此目录编译的 APK 会包含该字体。字体二进制及参考截图被 Git 忽略，不适用项目源码许可证；不含字体的源码检出仍可使用 Android 等宽字体构建。运行时导入的字体最大 10 MB，仅存放在应用私有目录。

Manifest 声明联网及照片元数据（`ACCESS_MEDIA_LOCATION`）权限，镜头扫描可选请求相机权限，不打开相机或拍摄，不请求设备当前位置或全盘存储权限。地名查询可能将照片坐标发送给 Android 系统地名服务，照片本身在本机处理；可在设置中关闭自动查询。可选保留的拍摄元数据采用白名单，排除 GPS、序列号、MakerNote、XMP 与缩略图。信息卡编辑不会改写原始拍摄参数；填写的姓名、地点仍会成为导出照片中的可见像素。

输出为 **8 位 sRGB / SDR 静态图片**，不保留 HDR 增益图、Display P3、高位深数据、实况照片、RAW 数据或动画。HEIC 解码取决于设备。JPEG 会重新编码，PNG 仅相对于渲染后的 SDR 位图无损；预览与原尺寸导出的栅格化可能略有差异。

内存不足时明确报错，不静默降低导出分辨率。512 MB / 200 MP 输入限制不代表每台设备均可导出该尺寸。当前版本不包含批量处理、自由拖动卡片或后台导出服务。

## 验证

`bash scripts/build-macos.sh` 执行主机检查与 APK 构建；连接授权设备后，`./gradlew :app:connectedDebugAndroidTest` 可运行方向、卡片像素边界、导出元数据及字体重置测试。`scripts/test-core.sh` 是需要 Kotlin CLI 的可选离线测试入口。实际覆盖与剩余设备检查见 [VALIDATION.md](docs/VALIDATION.md)。GitHub 工作流尚未在远端运行。

## 许可证

原创源码采用 `AGPL-3.0-only`，见 [LICENSE](LICENSE) 与 [NOTICE](NOTICE)。字体、截图及照片保留各自权利；本项目不是 Apple 产品。
