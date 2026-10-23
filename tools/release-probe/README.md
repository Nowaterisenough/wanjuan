# Normal Release Probe

This platform-only instrumentation runner verifies provider constructors against the actual R8 APK. With `-e checkUpdate true`, it also enables Cronet temporarily, opens About without accepting or changing onboarding consent, clicks Check for Updates and verifies the normal up-to-date toast. It restores the Cronet preference afterward. The UI check assumes the installed local release is at least as new as the public release.

Compile `ReleaseProbe.java` against the Android SDK `android.jar`, dex the classes with D8, link this manifest using AAPT2, add `classes.dex`, zipalign, and sign with the same release key as the installed app. Keep generated files outside this directory. The probe is non-debuggable and contains no Kotlin/AndroidX/desugared-library copies, which would otherwise conflict with an obfuscated target.

After installing the signed probe APK:

```shell
adb shell am instrument -w -e checkUpdate true io.wanjuan.app.releaseprobe/.ReleaseProbe
adb pull /sdcard/Android/data/io.wanjuan.app/cache/update-release-verified.png
adb uninstall io.wanjuan.app.releaseprobe
```

Success reports `providers_verified=3`, the up-to-date toast, `PASS`, and instrumentation code `-1`. Do not run the network/UI check if the phone must stay offline or if a newer public release should instead open the update dialog.
