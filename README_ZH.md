<p align="center"><a href="README.md">English</a> | 简体中文</p>
<h1 align="center">Fuyao Photo Info</h1>
<p align="center">直接嵌入照片的紧凑磨砂拍摄信息卡。</p>
<p align="center">
  <img src="https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android&amp;logoColor=white" alt="Android 8.0+">
  <img src="https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4" alt="Jetpack Compose">
  <img src="https://img.shields.io/badge/License-AGPL--3.0--only-blue" alt="AGPL-3.0-only">
</p>

在本机处理图片、可选调用系统地名服务的 Android 单图编辑器。读取可用 EXIF，将可编辑信息卡叠加在照片内部，不增加底部边框、不改变照片尺寸。本机验证记录保存在被忽略的 `docs/` 目录；设备与格式支持边界见下文。

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

普通 Release 与 Debug 均提供可安装包。文件名包含版本和 ABI，以各目录的 `output-metadata.json` 为准。以下命令安装最新的通用 Release：

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

安装成功输出 `Success`，需要已有授权设备。若提示签名不匹配，应使用原签名重新构建，不要直接卸载以免丢失私有草稿和设置。确需移除 Release 时使用 `adb uninstall ing.fuyaoskyrocket.photoinfo`；已导出相册照片保留。`./gradlew clean` 只清理构建产物，不重置构建序号。

## 构建变体与签名

| 变体 | 应用 ID 后缀 | 签名 |
| --- | --- | --- |
| Debug | `.debug` | 本机调试密钥，可安装 |
| Debug Unsigned | `.debug.unsigned` | 无 |
| Release | 无 | 优先私钥，否则使用本机调试签名 |
| Release Unsigned | `.unsigned` | 无 |

任务分别为 `assembleDebug`、`assembleDebugUnsigned`、`assembleRelease`、`assembleReleaseUnsigned`。Release 启用 R8 与资源压缩。参照 `signing.properties.example`，在被忽略的 `signing.properties` 中配置私钥。没有私钥配置时，Release 明确使用本机调试签名，保持与其他 Fuyao 应用一致的可安装行为。普通签名包启用 APK v1/v2；该回退属于本地构建，不是正式私钥签名。Unsigned 变体不能直接安装；工程不附带私有签名密钥。

## 版本与安装包命名

营销版本为 **27.0**，构建序列为 **1A**。每次真实构建原子递增工作区内的 `.build-counter`；同次调用的所有变体、ABI 共用序号，失败不退号，help、IDE 同步和 dry-run 不计数。新工作区从序号 1 开始。

`versionName` 为 `1A序号`（非 Release 保留变体后缀）；`versionCode` 为营销基数 `270` 拼接至少三位序号，如 `1A4` 对应 `270004`。生成 arm64-v8a、armeabi-v7a、x86、x86_64 与 universal 五类 APK：

```text
FuyaoPhotoInfo-包名-27.0(1A序号)-ABI-变体.apk
```

## 界面与边到边

采用 FuyaoLocale / FuyaoColorPicker 的 Material 3 基线：标准 64dp 顶栏、大字号自适应、动态色、默认 Material 字体层级和统一分组。照片为主、参数为辅助面板；窄屏上下排列，中宽窗口采用两栏。镜头配置为独立编辑页，支持数字键盘、就地验证和删除撤销。

状态栏、导航栏透明，安全区只消费一次；背景延伸至窗口底部，末行及贴底操作局部避让。全屏照片保留在 HDR Activity 内并采用深色系统栏样式，提供缩放按钮与可打断的回位。渲染反馈为覆盖层，不挤动照片；参数输入和原图对照即时响应。导出照片的信息卡版式不受界面主题影响。

设置、镜头列表、镜头编辑和全屏预览共用 Navigation Compose 返回栈，处理预测性返回进度及取消；编辑器根页面交由 Android 执行返回桌面。导出选项使用 Material 3 底部面板、分段格式选择及整行可点击的元数据开关。实际手势效果仍需真机验收。

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

## 配置镜头

进入“设置 → 镜头配置”，扫描系统可见镜头或手动添加。匹配机型填写照片中的设备／型号文字（已导入照片时自动带入原始 EXIF 设备名），再填写镜头名称及等效焦段。定焦镜头两端填同值；变焦镜头可设置倍率端点，范围内线性插值。物理焦段可选，在等效焦距缺失且匹配唯一时用于补全。保存配置页面后持久保存；相机 ID 仅标记本机硬件，不当作照片 EXIF 的镜头标识。

按本次提供的小米 17 Ultra 参数，可手动配置：

| 名称示例 | 等效焦段 | 倍率端点 |
| --- | --- | --- |
| LEICA 23MM MAIN／徕卡1英寸光影大师 | 23～23 mm | 1～1× |
| LEICA 200MP TELEPHOTO／徕卡2亿像素光学变焦 | 75～100 mm | 3.2～4.3× |
| LEICA ULTRA WIDE／徕卡超广角 | 14～14 mm | 可选约 0.6～0.6×（相对 23 mm 的近似值） |

名称可自由修改，运行时没有机型硬编码。有可用物理焦距时进一步消歧，仍重叠的配置不任意选择第一项；无法唯一匹配时保留原字段或只显示焦距。

## HDR 与动态照片保留

- Android 14+ 下，支持的 JPEG Ultra HDR 保留增益图与解码颜色空间，同时修改信息卡覆盖区域对应的增益图；编码前其他增益图像素保留。JPEG 背景填充与卡片绘制全部完成后才挂回增益图，避免后续创建 Canvas 将其清除；发布前重新解码检查增益参数与颜色空间。
- 标准 JPEG 动态照片及兼容的旧版 Microvideo，完整复制原 MP4/MOV 视频载荷，包括音频和视频内部元数据，不转码。导出校验视频段 SHA-256，并保留展示时间戳。
- HDR 动态照片的 GainMap 项排在视频项之前；插入 EXIF/XMP 时修正 MPF 长度和偏移。相册文件名使用 `_MP.jpg` 后缀。
- HDR 或动态照片不可导出为 PNG。遇到未识别的附加数据、损坏容器、不支持的格式或校验失败时停止导出，不静默降级。

建议通过“从文件导入（原片）”读取完整文件；分享软件或提供程序已经剥离的 HDR／视频无法恢复。当前保留路径支持 **JPEG 容器**。HEIC/AVIF 保留导出、分离式 Apple Live Photo、未公开的厂商动态封装、动画及高位深 PNG 暂不支持导出。Android 编码／显示、厂商镜头枚举和相册播放仍需真机验证。JPEG 底图与增益图会重新编码，不属于逐像素无损；视频按原字节保留。分享应用后续仍可能改写或扁平化文件。

## 字体、隐私与输出边界

当前本机工程包含从 macOS Terminal 复制的 SF Mono Regular，应用自动加载，基于此目录编译的 APK 会包含该字体。字体二进制及参考截图被 Git 忽略，不适用项目源码许可证；不含字体的源码检出仍可使用 Android 等宽字体构建。运行时导入的字体最大 10 MB，仅存放在应用私有目录。

Manifest 声明联网及照片元数据（`ACCESS_MEDIA_LOCATION`）权限，镜头扫描可选请求相机权限，不打开相机或拍摄，不请求设备当前位置或全盘存储权限。地名查询可能将照片坐标发送给 Android 系统地名服务，照片本身在本机处理；可在设置中关闭自动查询。可选保留的拍摄元数据采用白名单，排除 GPS、序列号、MakerNote、XMP 与缩略图。信息卡编辑不会改写原始拍摄参数；填写的姓名、地点仍会成为导出照片中的可见像素。

普通静态图支持 JPEG 和 8 位 PNG 导出；Ultra HDR、动态照片按上述路径保留。原片不覆盖。拍摄参数保留会移除静态图 EXIF 中的 GPS；原样复制的视频仍保留其自身元数据，其中可能包含位置。

内存不足时明确报错，不静默降低导出分辨率。512 MB / 200 MP 输入限制不代表每台设备均可导出该尺寸。当前版本不包含批量处理、自由拖动卡片或后台导出服务。

## 验证

`bash scripts/build-macos.sh` 执行主机检查与 APK 构建，其中 `:app:testAndroidDom` 使用 Android Harmony DOM 重跑媒体容器测试，覆盖与桌面 Java 的实现差异。该 Android 运行库仅用于主机测试，不打入 APK；此检查不执行原生 HDR 编解码，也不能替代真机验收。

连接授权设备后，`./gradlew :app:connectedDebugAndroidTest` 可运行方向、卡片边界、导出元数据、设置、字体重置及 Android 14+ HDR/动态封装测试。`scripts/test-core.sh` 是需要 Kotlin CLI 的可选离线测试入口。实际覆盖与剩余设备检查见 [VALIDATION.md](docs/VALIDATION.md)。GitHub 工作流尚未在远端运行。

## 许可证

原创源码采用 `AGPL-3.0-only`，见 [LICENSE](LICENSE) 与 [NOTICE](NOTICE)。字体、截图及照片保留各自权利；本项目不是 Apple 产品。
