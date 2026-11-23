# DarkToggle / 扩展深色主题应用例外

Android 16+ 的 **扩展深色主题（Expanded dark theme）** 会对没有适配深色的应用强制深色；底层是 `FORCE_INVERT_COLOR_DARK`，会忽略应用自己的 opt-out。Android 17 又为它增加了按应用例外：某些应用出现显示问题时，可以让它们**忽略扩展选项**。

本应用直接管理这份按应用例外列表，适用于 framework 里有这些 Settings 键、但系统 UI 没有暴露入口的设备（已在 MagicOS 上验证）。

## 官方说法 / 页面

- Google 支持文档称该功能为 **Expanded dark theme**，选项是「深色主题」里的 **扩展（Expanded）**；旁边可以进入按应用设置，关掉某些应用，让它们忽略扩展。
- Pixel 上的路径：设置 → 显示与触控 → 深色主题 → 扩展 → 齿轮图标。
- MagicOS 等系统可能没有暴露这个入口。`com.android.settings/.display.darkmode.AppDarkModeActivity` 是荣耀自己的「更多深色模式设置」页面，**不是** Expanded 的按应用例外页。

## 不要和荣耀的白名单混淆

MagicOS 的「更多深色模式设置」是荣耀自己的一套固定白名单（`hn_forcedark_*` 配置 + `persist.sys.hn.forcedark_policy`）。它只显示荣耀内置名单里已安装的应用，不能添加名单外的应用；和本应用的 `accessibility_force_invert_color_override_packages_to_disable` 不是同一个机制。

## 本应用改的键

| 键 | 命名空间 | 作用 |
| --- | --- | --- |
| `accessibility_force_invert_color_enabled` | `Settings.Secure` | 扩展深色主题总开关 |
| `accessibility_force_invert_color_override_packages_to_disable` | `Settings.System` | 忽略扩展的应用列表（应用例外） |
| `accessibility_force_invert_color_override_packages_to_enable` | `Settings.System` | 强制扩展的应用列表（暂未使用） |

**targetSdk 固定为 22**：Android 6+ 的 `SettingsProvider` 对 `PRIVATE_SETTINGS` 的写入按 targetSdk 分级，22 及以下只告警、不抛异常。不要改。

- 应用例外写入：`WRITE_SETTINGS`，targetSdk 22 安装时自动授予，不需要 adb。
- 总开关：写 `Settings.Secure`，需要 `WRITE_SECURE_SETTINGS`，用 adb 授权一次。
- Android 12+ 会拦截隐藏键的读取（`@Readable`），界面显示的是上一次成功写入的本地镜像；写入仍然生效。

## 构建

需要 JDK 17+ 和 Android SDK 36。

```powershell
.\scripts\build-release.ps1
```

产物：`out/darktoggle-release.apk`。签名信息从 `keystore.properties` 读取；仓库不包含密钥。

## 安装

```bash
adb install -r --bypass-low-target-sdk-block darktoggle-release.apk
adb shell pm grant com.github.sxcccccccc.darktoggle android.permission.WRITE_SECURE_SETTINGS
```

- Android 14+ 默认拒绝 `targetSdk < 23` 的应用，必须带 `--bypass-low-target-sdk-block`。
- 首次启动会提示「此应用专为旧版 Android 系统打造」，属预期。
- `targetSdk < 26` 会被系统限制在 1.86:1；manifest 里的 `android.max_aspect = 2.4` 用于解除长屏底部黑边。

## 其他

- 图标由 `scripts/generate-icons.ps1` 生成（黑底白字 DARK / TOGGLE）。
- 欢迎 issue / PR。

## AI 使用声明

本项目的代码、图标生成脚本和文档在 **Claude Code（Anthropic）** 辅助下完成；需求、设计取舍、真机验证与发布由作者负责。

## License

[MIT](LICENSE)
