# MediaTek FM JNI

Source: AOSP `packages/apps/FMRadio/jni/fmr` (Apache License 2.0), taken from
[LineageOS/android_packages_apps_FMRadio](https://github.com/LineageOS/android_packages_apps_FMRadio/tree/lineage-16.0/jni/fmr)
branch `lineage-16.0`. It drives `/dev/fm` (MediaTek FM driver; MT6631 combo chip on the AC8257).

The ioctl numbers match the ones used by Jancar's `libfmjni.so` on the UJC201 (`FM_IOCTL_POWERUP` 0xf500 …
`FM_IOCTL_SOFT_MUTE_TUNE` 0xf53f, seek done in user space by soft-mute tuning).

LibreHU changes (marked `LibreHU` in the code):
- `fmr.h`: NDK logging (`android/log.h`) instead of `<utils/Log.h>`;
- `fm.h`: `#include <stdint.h>`;
- `fmr_core.cpp`: built-in US/Europe configuration (87.5–108 MHz, 100 kHz) when `libfmcust.so` is missing, chip id
  taken from the driver;
- `libfm_jni.cpp`: natives registered on `org.librehu.fm.FmNative`;
- `fm.h` / `common.cpp`: AC8257 driver (`ro.mediatek.platform=AC8257`, the test of Jancar's `JNI_OnLoad`). Its
  `FM_IOCTL_POWERUP` / `FM_IOCTL_TUNE` take an 8-byte `fm_tune_parm` (deemphasis + reserved byte, frequency at
  offset 6; Jancar's `COM_pwr_up_8257` / `COM_tune_8257`) and tune in 10 kHz units (Jancar's JNI `tune` multiplies
  by 100, `powerUp` by 10). The AOSP 6-byte structure put the frequency where this driver reads the deemphasis.
