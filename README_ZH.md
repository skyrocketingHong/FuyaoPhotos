<p align="center"><a href="README.md">English</a> | 简体中文</p>
<p align="center"><img src="assets/readme/app-icon.svg" width="112" height="112" alt="Fuyao Photo Info 应用图标"></p>
<h1 align="center">Fuyao Photo Info</h1>
<p align="center">直接嵌入照片的紧凑磨砂拍摄信息卡。</p>
<p align="center">
  <img src="https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android&amp;logoColor=white" alt="Android 8.0+">
  <img src="https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4" alt="Jetpack Compose">
  <img src="https://img.shields.io/badge/License-AGPL--3.0--only-blue" alt="AGPL-3.0-only">
</p>

## 下载

前往 [Releases](https://github.com/skyrocketingHong/FuyaoPhotoInfo/releases/latest) 下载。不了解设备架构时选择 **universal** 通用安装包。支持 Android 8.0 及以上，HDR 显示需要 Android 14+ 和兼容硬件。

## 效果展示

### 导出样例

香港海滨 · 小米 17 Ultra · 徕卡 75–100mm 长焦。点击查看 **4080 × 3072** 原尺寸导出图片。

<p align="center">
  <a href="assets/readme/sample-hong-kong.jpg"><img src="assets/readme/sample-hong-kong.jpg" width="960" alt="香港海滨照片，右下角圆角信息卡展示小米 17 Ultra、徕卡 75–100mm 长焦镜头及拍摄参数"></a>
</p>

### Apple 发布会参考截图

Apple 发布会截图，展示信息卡的设计参考。三张截图均为 **2560 × 1440**，点击可查看原尺寸版本。

<table>
  <tr>
    <td align="center"><a href="assets/readme/apple-keynote-portrait.png"><img src="assets/readme/apple-keynote-portrait.png" width="300" alt="Apple 发布会参考：人物肖像与右下角信息卡"></a><br>人物肖像</td>
    <td align="center"><a href="assets/readme/apple-keynote-night-sky.png"><img src="assets/readme/apple-keynote-night-sky.png" width="300" alt="Apple 发布会参考：星空与右下角信息卡"></a><br>星空</td>
    <td align="center"><a href="assets/readme/apple-keynote-stairs.png"><img src="assets/readme/apple-keynote-stairs.png" width="300" alt="Apple 发布会参考：楼梯场景与右下角信息卡"></a><br>楼梯场景</td>
  </tr>
</table>

Apple 与画面署名摄影者保留各自的图片权利，详见[素材来源与权利说明](assets/readme/README.md)及 [Apple 相关声明](#apple-相关声明)。

## 功能

- **拍摄信息卡**：以圆角磨砂卡片展示设备、摄影者、地点、镜头及拍摄参数。
- **EXIF 识别**：读取照片元数据，根据照片 GPS 解析地点，支持手动修改全部展示字段。
- **批量编辑**：一次选择最多 50 张照片，横向切换编辑并统一保存，每张照片保留独立的信息和样式。
- **样式调整**：自定义卡片大小、字号、透明度、模糊、圆角和边距，支持导入 TTF/OTF/TTC 字体。
- **保存偏好**：设置默认格式、JPEG 质量、EXIF 参数、定位和拍摄时间保留选项，每次保存均可临时调整。
- **相册衔接**：从相册分享单张或多张照片到应用编辑，保存后可直接打开或分享。
- **署名与镜头配置**：保存默认摄影者，自定义镜头名称、焦段范围和变焦倍率。
- **媒体预览**：通过紧凑图标播放动态照片视频、切换 HDR 显示。
- **预览与导出**：原图对照、原尺寸预览，按原尺寸导出 JPEG/PNG。JPEG 编码质量可在 0～100 之间调整，默认 100。
- **HDR 与动态照片**：编辑封面时保留支持的 JPEG Ultra HDR 增益图及动态照片视频、音频。

## 使用

1. 打开一张照片、一次选择多张，或从相册分享到 **Fuyao Photo Info**。HDR/动态照片建议使用“从文件导入（原片）”。
2. 修改拍摄信息和卡片样式；多图可横向切换编辑。
3. 点击“保存”，选择格式、JPEG 质量及元数据选项，保存本次全部照片。

Android 10+ 保存至 `Pictures/FuyaoPhotoInfo`；Android 8/9 单张选择保存位置，多张选择输出文件夹。原片保持不变，导出后可直接分享。

在“设置 → 默认保存选项”中保存长期偏好。每次保存从全局默认开始，临时选择只对本次保存生效。完成提示提供“打开”（最后一张成功导出图片）和“分享”（全部成功项）。

在“设置”中保存默认摄影者。照片已有的 EXIF Artist 优先，也可将默认署名应用到当前照片。

### 镜头配置

进入“设置 → 镜头配置”，分别填写用于展示的产品名称和用于匹配的原始 EXIF 型号。原生等效焦段、原生倍率与数码最高倍率分开设置。

小米 17 Ultra 配置示例：

| 镜头 | 原生等效焦段 | 原生倍率 | 数码最高倍率（可选） |
| --- | --- | --- | --- |
| 主摄 | 23～23 mm | 1～1× | 3.1× |
| 超广角 | 14～14 mm | 0.6～0.6× | 0.9× |
| 长焦 | 75～100 mm | 3.2～4.3× | 按需要填写已确认的最高总倍率 |

物理焦距填写 Camera2/EXIF 的实际数值，不能使用表中的等效焦距；未知时留空。匹配优先使用物理焦距，其次使用配置的等效焦段和数码范围；存在歧义时留空。

扫描可列出尚未配置的硬件并关联镜头。应用单个镜头的修改后，保存镜头配置列表；重新导入照片即可使用新配置。

## 导出支持

| 输入 | 输出 | 保留内容 |
| --- | --- | --- |
| 普通静态照片 | JPEG / 8 位 PNG | 原始尺寸，可选保留拍摄参数 |
| JPEG Ultra HDR（Android 14+） | JPEG | 增益图与解码颜色空间 |
| 支持的 JPEG 动态照片 / Microvideo | JPEG | 原音视频编码内容及所选元数据 |
| 支持的 JPEG Ultra HDR 动态照片（Android 14+） | JPEG | HDR 与动态数据 |

JPEG 底图和增益图会重新编码，视频和音频不转码。定位与拍摄时间开关同时作用于照片和视频元数据。对于支持的 MP4/MOV，清理不会移动编码样本或改变播放时序，并校验未修改区域；全部保留时视频按原字节复制。HDR/动态照片不能导出为 PNG。请导入完整原片，聊天应用或文件提供程序已移除的数据无法恢复。

暂不支持 HEIC/AVIF 保留导出、分离式 Apple Live Photo、未公开的厂商动态格式、动画及高位深 PNG 导出。未识别或损坏的媒体会停止导出。HDR 显示和动态播放取决于设备及相册应用，尚未完成全部设备的兼容性验证。

单张输入上限为 512 MB / 200 MP，实际可处理大小受设备内存限制；导出不会自动降低分辨率。不支持后台导出或自由拖动卡片。

动态播放使用原视频，HDR 预览开关不会移除增益图或修改导出设置；播放和显示取决于设备编码与屏幕支持。

## 字体

参考样式在字体可用时采用 SF Compact Rounded Medium 与部分 SF Mono Medium 数字混排，属于截图近似方案，未确认 Apple 原始字体。不包含本地字体文件的构建使用 Android 系统字体。导入 TTF/OTF/TTC 后整张卡片使用该字体，文件最大 10 MB。

字体二进制不包含在仓库中。使用 Apple 字体构建前，请参阅[字体配置与许可说明](app/src/main/assets/fonts/README.md)。

## 隐私

- 照片在设备上处理。可选地名查询会将照片坐标发送给 Android 系统地名服务，可在设置中关闭。
- 相机权限仅用于可选的镜头扫描；不请求设备当前位置或全盘存储权限。
- EXIF 拍摄参数、定位和拍摄时间分别控制，定位默认关闭。视频元数据遵循相同选项，无法验证清理结果的封装会停止导出。序列号、MakerNote、原始任意 XMP 和缩略图不复制；卡片上的姓名和地点仍会显示在图片中。
- 关闭拍摄时间会移除原照片和视频中的日期，新文件仍有系统创建时间，相册可能显示保存时间。

## 构建

需要 **JDK 17** 或兼容的更新 JDK，以及 **Android SDK Platform 37**。工程附带 Gradle 9.6.1 Wrapper。按需设置 `JAVA_HOME` 和 `ANDROID_HOME`，未缓存依赖需要联网下载。

```bash
# macOS 可选，使用受字体许可约束。
bash scripts/copy-macos-font.sh

# 单元测试、Debug/Release Lint 和 APK 构建。
bash scripts/build-macos.sh
```

APK 输出至 `app/build/outputs/apk/debug/` 和 `app/build/outputs/apk/release/`。构建失败时返回非零状态，请查看首个错误；`./gradlew clean` 可清理构建产物。

在已授权的 ADB 设备上安装最新通用 Release：

```bash
python3 - <<'PYAPK'
from pathlib import Path
import json, subprocess
metadata = Path('app/build/outputs/apk/release/output-metadata.json')
artifacts = json.loads(metadata.read_text())['elements']
apk = metadata.parent / next(item['outputFile'] for item in artifacts if not item['filters'])
subprocess.run(['adb', 'install', '-r', str(apk)], check=True)
PYAPK
```

安装成功输出 `Success`。若提示签名不匹配，使用原签名密钥重新构建以保留应用数据。

### 构建变体与签名

| 变体 | 应用 ID 后缀 | 签名 |
| --- | --- | --- |
| Debug | `.debug` | 本机调试密钥 |
| Debug Unsigned | `.debug.unsigned` | 无 |
| Release | 无 | 已配置的私钥，或本机调试密钥 |
| Release Unsigned | `.unsigned` | 无 |

参照 [signing.properties.example](signing.properties.example) 配置私有签名。签名变体启用 APK v1/v2，Unsigned 变体需签名后安装。工程不附带私钥，Release 启用 R8 与资源压缩。

### 版本与安装包命名

营销版本 **27.0** 使用 **1A** 构建序列。每次构建递增本地序号，同次构建的所有变体和 ABI 共用该序号。提供 arm64-v8a、armeabi-v7a、x86、x86_64 和 universal APK：

```text
FuyaoPhotoInfo-包名-27.0(1A序号)-ABI-变体.apk
```

### 测试

构建脚本运行 JVM 单元测试及 Android Harmony DOM 兼容性测试。连接授权设备后，可运行 `./gradlew :app:connectedDebugAndroidTest` 执行 Android 渲染与媒体测试，其中 HDR 用例需要 Android 14+。主机测试不替代真机验证。

## 技术栈与项目结构

Kotlin · Jetpack Compose / Material 3 · Camera2 · ExifInterface 1.4.2 · AGP 9.2.1 · compile/target SDK 37。

应用 ID：`ing.fuyaoskyrocket.photoinfo`。应用源码位于 `app/src/main/java/ing/fuyaoskyrocket/photoinfo/`：

| 目录 | 用途 |
| --- | --- |
| `data/` | 照片、导出、地名查询、设置与镜头扫描 |
| `domain/` | 元数据、镜头匹配、卡片布局、字体与媒体格式 |
| `platform/` | Android 渲染与字体加载 |
| `presentation/`、`ui/` | 编辑状态与应用页面 |

构建脚本位于 `scripts/`，测试位于 `app/src/test/` 和 `app/src/androidTest/`。

## 许可证

原创源码采用 `AGPL-3.0-only`，见 [LICENSE](LICENSE) 与 [NOTICE](NOTICE)。字体、截图及照片保留各自权利；本项目不是 Apple 产品。

## Apple 相关声明

Fuyao Photo Info 是独立的第三方项目，与 Apple Inc. 无隶属或合作关系，非由 Apple 开发、赞助或认可。Apple、iPhone、macOS 等商标归 Apple Inc. 所有；其他名称及素材的权利归各自权利人所有。

项目对 Apple 产品、字体及视觉样式的提及仅用于说明参考来源与实现。信息卡的尺寸和排版参数依据参考图片估算，不属于 Apple 官方设计规范、设计资源，也不表示获得 Apple 认证。

SF Mono 等 Apple 字体仍受其适用许可约束，不属于本项目 `AGPL-3.0-only` 的授权范围。macOS 自带字体、本地复制脚本或 Git 忽略规则均不构成字体嵌入或再分发授权。分发包含 Apple 字体的 Android APK 前，应取得覆盖该用途的许可，或改用 Android 系统等宽字体及其他许可允许的字体。本声明本身不授予使用 Apple 素材的权利。

相关依据见 Apple 的[商标使用指南](https://www.apple.com/legal/intellectual-property/guidelinesfor3rdparties.html)、[商标列表](https://www.apple.com/legal/intellectual-property/trademark/appletmlist.html)和[字体信息及许可条款](https://developer.apple.com/fonts/)。具体字体或素材随附的许可仍然适用。

## AI 辅助开发

本项目在开发过程中使用生成式 AI 协助编码。

[![Vibe PR](https://raw.githubusercontent.com/fenxer/llm-things/main/stickers/vibe-pr.svg)](https://github.com/fenxer/llm-things/blob/main/stickers/vibe-pr.svg)
