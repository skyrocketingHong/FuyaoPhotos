# Fuyao Photos 交换格式

Apple 与 Android 共用本格式，目前支持版本 1。不支持的版本明确拒绝，不静默丢弃资源。

## 照片包

扩展名 `.fuyaophotos`，MIME 为 `application/vnd.fuyaophotos`；Apple UTI 为 `ing.fuyaoskyrocket.photos.package`，继承 `public.data`。

文件按以下顺序组成：

1. 16 字节魔数：ASCII `FUYAOPHOTOS`，后接 `00 01 0D 0A 1A`。
2. 4 字节无符号大端整数，表示 JSON 字节数，范围 1～32768。
3. UTF-8 JSON 清单。
4. 原始图片字节、原始 MOV 字节；没有填充或尾随数据。

清单字段：`format` 固定为 `fuyaophotos.live-photo`，`version` 为 `1`；`assetIdentifier` 为规范 UUID 字符串，`stillImageTimeUs` 为 0～60000000 的微秒时间戳；`photo`、`movie` 分别包含 `fileExtension`、`bytes`、64 位小写十六进制 `sha256`。图片仅允许 `jpg` 或 `heic`，视频为 `mov`；每个资源最多 512 MiB。哈希用于发现传输损坏，不是身份签名。

固定两个未压缩资源，不包含解包路径、目录、符号链接、可执行内容或任意资源名。读取方自行生成私有临时目录和文件名，写入前验证长度，流式核对哈希；失败时清理半成品。

图片必须包含 Apple MakerNote tag 17；MOV 的 `com.apple.quicktime.content.identifier` 与其一致，并包含可识别的 `com.apple.quicktime.still-image-time` 定时轨道。全局标识的 UTF-8 数据不附加 NUL。`mebx` 的 keys 内是带编号的 key box，不使用全局 `mdta` keys 的计数结构。Apple 验证配对后，通过 photo 与 pairedVideo 资源写入图库；带原生风格的图片还要求兼容的视频风格数据。

## 镜头配置

每个 `.json` 文件是紧凑的单行 UTF-8 文本，对应一个 EXIF 机型，最多 128 KiB、64 个镜头。参见[共享样例](../fixtures/lenses-v1.json)。

根字段：`format` 固定为 `fuyaophotos.lenses`，`version` 为 `1`；`device` 为产品显示名，`exifModel` 为原片 EXIF 匹配值，`lenses` 为镜头数组。

镜头包含 `name`、`facing`（`unspecified`、`back`、`front`、`external`）、`equivalentMin`、`equivalentMax`。可选成对端点为 `physicalMin` / `physicalMax` 与 `zoomMin` / `zoomMax`；`digitalZoomMax` 单独表示数码覆盖上限。数字必须有限且满足两端 LensProfile 范围约束。缺失值保持缺失，不在导入时估算。

不导出本地 UUID、Camera2 ID 或硬件绑定。导入仅在草稿中替换规范化 EXIF 型号相同的配置，必要时在应用内确认替换；点击保存后才持久化，不修改原照片元数据。
