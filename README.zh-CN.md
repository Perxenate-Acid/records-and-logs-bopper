# Records & Logs Bopper

[English](README.md) | **[中文](README.zh-CN.md)**

一个 [Jingle](https://github.com/DuncanRuns/Jingle) 插件，用于清理 SpeedrunIGT 模组在 `%USERPROFILE%\speedrunigt\records` 堆积的速通记录，**同时**清理 MultiMC / Prism Launcher 实例中的 Minecraft 日志文件（`.minecraft/logs`）。

SpeedrunIGT 模组会在 `%USERPROFILE%\speedrunigt\records` 保留速通记录，且这些记录会随使用不断堆积。MultiMC / Prism Launcher 实例的 `.minecraft/logs` 也会随着每次启动游戏而堆积日志压缩包。本插件可以在一个标签页内同时管理这两类清理。

## 功能

- **单标签页** — 两个清理功能都在 Jingle 插件菜单的 `Records & Logs Bopper` 标签页内。
- **速通记录清理** — 清理 `%USERPROFILE%\speedrunigt\records`，仅删除经过验证的 SpeedrunIGT 记录文件（UUID 文件名 + `final_igt` JSON 字段）。用户自行放入的文件不会被删除。
- **MC 日志清理** — 清理所有 MultiMC / Prism 实例的 `.minecraft/logs`。`latest.log` 和 `debug.log` 永不删除，仅删除 `*.log.gz` 归档文件和旧 `*.log` 文件。
- **启动时自动清理** — Jingle 启动时，若速通记录或日志超过各自的大小阈值（默认各 50 MB），则自动清理。**默认关闭**，可分别为速通记录和日志独立开启。
- **保留最近文件** — 清理时可选择保留最近的 N 个速通记录（默认 10 个）或日志（默认 5 个）。
- **删除确认** — 每次手动删除前都会弹出警告对话框，需要明确确认才会执行。
- **快捷按钮** — Jingle 主窗口上的 `Clean Records` 快捷按钮。
- **热键动作** — 可在 Jingle 的 Hotkeys 页面将 `Clean Records` 动作绑定到任意快捷键。
- **MultiMC 自动检测** — 通过扫描常见路径和查询运行中进程自动找到 MultiMC / Prism Launcher。
- 设置保存在 `~/.config/Jingle/records-and-logs-bopper.properties`。

## 安装

1. 从 [Releases](../../releases) 下载最新的 `records-and-logs-bopper-*.jar`。
2. 将 jar 文件放入 Jingle 的插件文件夹：`%USERPROFILE%\.config\Jingle\plugins\`（不存在则创建）。
3. 完全重启 Jingle（不是最小化到托盘后恢复）。

## 安全说明

- 速通记录清理仅作用于 `%USERPROFILE%\speedrunigt\records`，且仅删除经过验证的 SpeedrunIGT 记录文件（UUID 文件名 + `final_igt` 内容），不会碰其他任何文件。
- 日志清理仅作用于 MultiMC / Prism 实例内的 `.minecraft/logs`。`latest.log` 和 `debug.log` 永不删除。
- 每次手动删除前都会弹出警告对话框，需要明确确认。
- 被运行中游戏占用的文件无法删除；会提示信息，关闭游戏后重新清理即可。
- 卸载 = 删除 jar 文件。

## 构建

需要 JDK 17 或更高版本。运行：

```bash
bash build.sh
```

生成的 jar 位于 `out/records-and-logs-bopper-1.0.0.jar`。构建使用仅编译用的 API 桩（`stubs/`），插件 jar 保持精简，运行时引用 Jingle 自身的类。插件使用 `--release 17` 编译，兼容 Java 17–21（在 `jingle.plugin.json` 中声明 `minimumJava: 17`）。

## 环境要求

- Jingle 运行在 **Java 17、18、19、20 或 21** 上（推荐 Java 21）。
- MultiMC 或 Prism Launcher（用于 MC 日志清理；速通记录清理可独立使用）。

## 许可

MIT

## AI 声明

本仓库所有代码均由 AI（大语言模型）生成。
