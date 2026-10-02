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

可选字段 `stylePrefix` 为不超过 64 个字符且不含控制字符的字符串。镜头唯一匹配后，在卡片的非空摄影风格名称前加此前缀；留空保留原名称，名称已含相同前缀时不重复添加。缺少风格名称时不只显示前缀，也不修改原片元数据。旧文件省略此字段时保持原有显示行为。

可选 `originalMegapixels` 是单位为 MP 的 JSON 数字，记录设备官方参数页中此镜头的标称像素数。必须有限、大于 0 且不超过 1000；未知时省略或设为 null，不接收数字字符串或布尔值。镜头唯一匹配后，可在卡片编辑器显式将 IMAGE SIZE 填为该值；全局“默认使用镜头像素数”开关控制导入初值与显式恢复字段，缺省关闭；关闭或无匹配值时，按照片实际宽 × 高 ÷ 1,000,000 计算。修改开关不自动覆盖已有卡片文字。该操作只改变卡片文字，不改变照片尺寸或原片元数据。旧版 v1 文件继续兼容。参见[像素数共享样例](../fixtures/lenses-v1-with-pixels.json)。

镜头可选字段 `id` 保留源硬件镜头 ID，类型为不超过 256 字符的字符串；根级可选字段 `hardwareModel` 保留不超过 512 字符的机型标识，不包含序列号或单台设备标识。导入时它们仅作提示，本地配置 UUID 重新生成，已验证的本机绑定不从文件恢复。新增可选字段保持版本 1 兼容，旧文件可以全部省略。参见[带 ID 的共享样例](../fixtures/lenses-v1-with-ids.json)。

自动绑定须检查本机型号、扫描到的朝向、可用焦距标定及一对一唯一性，不得仅凭 ID 选择镜头或消除歧义。ID 缺失、错误或来自另一平台时，仍可依靠已核验的本机参数识别。手动绑定同样检查真实扫描结果、已知朝向/焦距约束及已有绑定。Apple 使用 AVFoundation，Android 使用 Camera2；系统型号无法与产品名称对应时，可由用户明确指定哪组配置属于本设备。

导入仅在草稿中替换规范化 EXIF 型号相同的配置，必要时在应用内确认替换；点击保存后才持久化，不修改原照片元数据。
