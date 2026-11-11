"""Android App Links verification.

Google fetches `https://oru.freebuff.app/.well-known/assetlinks.json` at install
time and compares the fingerprint with the certificate the installed APK was
signed with. Only a matching pair lets `https://oru.freebuff.app/song/12` open
the app directly instead of the browser.

The static host drops files inside hidden directories, so the same document is
served by the API (rewritten from the .well-known path) and also kept at
`public/.well-known/assetlinks.json` for any host that does serve dot-paths.
Both must carry the fingerprint of `android/keystore/oru-release.jks`; changing
the signing key means changing both, and every existing install has to be
reinstalled once.
"""

ANDROID_PACKAGE = "com.oru.app"

# SHA-256 of the ORU release certificate (android/keystore/oru-release.jks).
ANDROID_CERT_SHA256 = (
    "73:BD:EF:58:CB:4B:0D:16:FB:BF:19:6B:19:97:42:FA:"
    "99:B0:C8:81:F0:32:24:0D:E4:0C:8B:8E:A4:4D:79:64"
)

ASSETLINKS = [
    {
        "relation": ["delegate_permission/common.handle_all_urls"],
        "target": {
            "namespace": "android_app",
            "package_name": ANDROID_PACKAGE,
            "sha256_cert_fingerprints": [ANDROID_CERT_SHA256],
        },
    }
]
