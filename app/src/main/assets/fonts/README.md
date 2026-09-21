# Card font

Local builds load `SF-Mono-Regular.otf` first, with Android monospace as a fallback.
This checkout contains a byte-for-byte copy from the local macOS Terminal bundle:
`/System/Applications/Utilities/Terminal.app/Contents/Resources/Fonts/SF-Mono-Regular.otf`.

The binary is ignored by Git and is not covered by the source-code license.
A fresh source checkout without this file remains buildable using Android monospace.
The app can import TTF/OTF/TTC into private storage; Reset restores the bundled default.

## Apple font licensing

SF Mono remains subject to its applicable Apple license. A font being installed
on macOS or excluded from Git does not authorize embedding it in an Android APK
or redistributing it. This project’s source license and notices grant no such
rights. Before distribution, obtain a license covering that use or omit the
Apple font and use Android monospace or another appropriately licensed font.

See [Apple font information and license terms](https://developer.apple.com/fonts/)
and the license supplied with the font.
