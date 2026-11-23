# DarkToggle

Android 16 加入了扩展深色主题（Expanded dark theme，底层是 `FORCE_INVERT_COLOR_DARK`），会把没适配深色模式的应用也强制深色，哪怕应用自己声明了 opt-out 也拦不住。到 Android 17，系统又补了一个按应用排除的列表，遇到反色后显示错乱的应用可以单独关掉。

在 Pixel 上，这个设置在「设置 → 显示与触控 → 深色主题 → 扩展（点旁边的齿轮）」里。但像荣耀 MagicOS 这类系统，底层 framework 保留了相关配置项，系统设置里把入口藏起来了。这个小工具就是用来在没入口的机器上直接读写这份排除列表的（目前在 MagicOS 11 上测试通过）。

注意不要和 MagicOS 设置里的「更多深色模式设置」（`com.android.settings/.display.darkmode.AppDarkModeActivity`）搞混。那个页面走的是荣耀自己的一套白名单（`hn_forcedark_*` + `persist.sys.hn.forcedark_policy`），列表里只显示荣耀预设过的应用，没法自己加别的 App。

## 原理与限制

应用读写的是以下几个系统设置项：

| 键名 | 命名空间 | 说明 |
| --- | --- | --- |
| `accessibility_force_invert_color_enabled` | `Settings.Secure` | 扩展深色主题总开关 |
| `accessibility_force_invert_color_override_packages_to_disable` | `Settings.System` | 排除列表（不强制反色的应用包名） |
| `accessibility_force_invert_color_override_packages_to_enable` | `Settings.System` | 强制启用列表（目前没用到） |

几个实现上的细节：

- **`targetSdk` 固定为 22，不要改**：从 Android 6 开始，`SettingsProvider` 对 `PRIVATE_SETTINGS` 的写入会按 targetSdk 拦截，22 及以下只会打警告日志，不会抛异常崩溃。同时因为 targetSdk 是 22，写 `Settings.System` 所需的 `WRITE_SETTINGS` 权限在安装时会自动给，改排除列表不需要连 adb。
- **总开关需要 adb 授权一次**：总开关在 `Settings.Secure` 下，必须用 adb 授一次 `WRITE_SECURE_SETTINGS` 才能动。
- **状态回显靠本地缓存**：Android 12+ 限制了隐藏设置项的读取（`@Readable` 注解拦截），应用没法直接读回系统当前的值，所以界面上展示的状态其实是应用本地存的「上次写入记录」，但写进去的值本身是生效的。

## 安装与授权

因为 `targetSdk < 23`，Android 14 及以上直接点安装包会被系统拦下，需要用 adb 带参数安装，并授权开关需要的权限：

```bash
adb install -r --bypass-low-target-sdk-block darktoggle-release.apk
adb shell pm grant com.github.sxcccccccc.darktoggle android.permission.WRITE_SECURE_SETTINGS
```

首次打开系统会弹窗提示「此应用专为旧版 Android 系统打造」，点确定就行。

## 构建

环境需要 JDK 17+ 和 Android SDK 36。

```powershell
.\scripts\build-release.ps1
```

打好的包在 `out/darktoggle-release.apk`。签名配置从 `keystore.properties` 读（仓库里不带密钥）。应用图标是用 `scripts/generate-icons.ps1` 现画的黑底白字。

## AI 声明与开源协议

本项目的代码、图标生成脚本和文档在 Claude Code（Anthropic） 辅助下完成；需求、设计取舍、真机验证与发布由作者负责。

[MIT](LICENSE)
