# Card font

Local builds load `SF-Mono-Regular.otf` first, with Android monospace as a fallback.
This checkout contains a byte-for-byte copy from the local macOS Terminal bundle:
`/System/Applications/Utilities/Terminal.app/Contents/Resources/Fonts/SF-Mono-Regular.otf`.

The binary is ignored by Git and is not covered by the source-code license.
A fresh source checkout without this file remains buildable using Android monospace.
The app can import TTF/OTF/TTC into private storage; Reset restores the bundled default.
