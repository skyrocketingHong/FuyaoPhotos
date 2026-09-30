# Fuyao Photos exchange formats

These formats are shared by the Apple and Android clients. Version 1 is supported. Unsupported versions are rejected; readers must not silently drop resources.

## Photo package

Extension: `.fuyaophotos`. MIME: `application/vnd.fuyaophotos`. Apple UTI: `ing.fuyaoskyrocket.photos.package`, conforming to `public.data`.

The file contains, in order:

1. Sixteen magic bytes: ASCII `FUYAOPHOTOS`, then `00 01 0D 0A 1A`.
2. A four-byte unsigned big-endian JSON byte count, from 1 to 32,768.
3. A UTF-8 JSON manifest.
4. The exact photo bytes, then the exact MOV bytes. No padding or trailing data.

Manifest fields: `format` = `fuyaophotos.live-photo`, `version` = `1`, `assetIdentifier` = a canonical UUID string, `stillImageTimeUs` = a nonnegative microsecond timestamp up to 60,000,000, and `photo` / `movie` resource objects. Each resource has `fileExtension`, `bytes`, and a lowercase, 64-character `sha256`. Photo extensions are `jpg` or `heic`; the movie extension is `mov`. Each resource is limited to 512 MiB. Hashes detect transfer corruption, not authenticity.

There are exactly two uncompressed resources. The format contains no extraction paths, directories, links, executable content, or arbitrary resource names. Readers choose their own temporary directory and filenames, enforce all lengths before writing, verify hashes while streaming, and clean up partial output on failure.

The image must contain Apple MakerNote tag 17. The MOV must carry the same `com.apple.quicktime.content.identifier` plus a recognized timed `com.apple.quicktime.still-image-time` track. The global identifier is UTF-8 without a trailing NUL. A `mebx` key table contains indexed key boxes; it is not the count-prefixed global `mdta` key table. Apple validates the actual pair before adding the photo and paired video to Photos. Native style resources require compatible video style data.

## Lens configuration

A compact, single-line UTF-8 `.json` file describes one EXIF camera model. Maximum size: 128 KiB; maximum lenses: 64. See [the shared fixture](../fixtures/lenses-v1.json).

Root fields: `format` = `fuyaophotos.lenses`, `version` = `1`, `device` (display product name), `exifModel` (source EXIF matching value), and `lenses`.

Each lens has `name`, `facing` (`unspecified`, `back`, `front`, `external`), `equivalentMin`, and `equivalentMax`. Optional endpoint pairs are `physicalMin` / `physicalMax` and `zoomMin` / `zoomMax`; an optional `digitalZoomMax` is separate from optical endpoints. Numeric values must be finite and valid under both clients' LensProfile constraints. Missing values remain absent; they are never estimated during import.

Local profile UUIDs and Camera2/hardware bindings are not exported. Import replaces only the matching normalized EXIF model in a draft, requires an in-app replacement confirmation when needed, and persists only after Save. Camera metadata itself is unchanged.
