# SpeedrunIGT Records Cleaner (Jingle Plugin)

A [Jingle](https://github.com/DuncanRuns/Jingle) plugin that cleans up cached speedrun records left by the [SpeedrunIGT](https://github.com/RedLime/SpeedrunIGT) mod in `%USERPROFILE%\speedrunigt\records`.

SpeedrunIGT 会在 `%USERPROFILE%\speedrunigt\records` 文件夹里堆积大量缓存文件。本插件可以一键清空它们（保留文件夹本身），也可以设置大小阈值，在 Jingle 启动时自动清理超限缓存。

## Features / 功能

- **One-click cleanup** — a `Clean Records` quick-action button on the Jingle main window empties the records folder (the folder itself is kept).
- **Auto cleanup on startup** — when Jingle launches, if the records folder exceeds the configured size threshold (default 500 MB), it is cleaned automatically. Can be toggled off.
- **Settings tab** — a `Records Cleaner` tab under Jingle's Plugins menu shows the current folder size, and lets you run a manual cleanup, open the folder, toggle auto-clean, and change the threshold.
- **Hotkey action** — bind the `Clean SpeedrunIGT Records` action to any hotkey in Jingle's Hotkeys page.
- Settings persist in `~/.config/Jingle/speedrunigt-records-cleaner.properties`.

## Installation / 安装

1. Download the latest `speedrunigt-records-cleaner-*.jar` from [Releases](../../releases).
2. Put the jar into Jingle's plugin folder: `%USERPROFILE%\.config\Jingle\plugins\` (create it if missing).
3. Restart Jingle completely (not just minimized to tray).

把 jar 文件放入 `%USERPROFILE%\.config\Jingle\plugins\` 文件夹，完全重启 Jingle 即可。

## Safety / 安全说明

- The plugin only ever touches the single folder `%USERPROFILE%\speedrunigt\records`. Nothing else.
- Files locked by a running game cannot be deleted; a message is shown and you can run the cleanup again after closing the game.
- Uninstall = delete the jar file.

## Build / 构建

Requires JDK 21. Run:

```bash
bash build.sh
```

The jar is produced at `out/speedrunigt-records-cleaner-1.0.0.jar`. The build uses compile-only API stubs (`stubs/`) so the plugin jar stays small and references Jingle's own classes at runtime.

## License / 许可

MIT
