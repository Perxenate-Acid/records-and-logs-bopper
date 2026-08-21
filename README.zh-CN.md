# Records & Logs Bopper

[English](README.md) | **[中文](README.zh-CN.md)**

一个 [Jingle](https://github.com/DuncanRuns/Jingle) 插件，用于清理 SpeedrunIGT 模组在 `%USERPROFILE%\speedrunigt\records` 堆积的速通记录，**同时**清理 MultiMC / Prism Launcher 实例中的 Minecraft 日志文件（`.minecraft/logs`）。

> **本插件仅支持 Jingle 1.3.0，需要 Java 17–21**（推荐 Java 21）。
> MultiMC 或 Prism Launcher 仅 MC 日志清理功能需要；速通记录清理可独立使用。

## 功能

插件提供速通记录清理与 MC 日志清理两项功能。

### 速通记录清理

SpeedrunIGT 每完成一次速通，都会在 `%USERPROFILE%\speedrunigt\records` 中保存一个记录文件，长期使用后数量增长很快。清理器仅删除真正的 SpeedrunIGT 记录：文件名格式与文件内容均会经过校验，用户自行放入该文件夹的文件不受影响。

可通过"立即清理记录"按钮手动清理（执行前会弹出确认对话框），也可启用 Jingle 启动时自动清理：仅当文件夹大小超过阈值（默认 50 MB）时才会执行，默认关闭。两种方式均可选择保留最近若干条记录而非全部删除，保留数量可自定义，默认为 10。标签页会显示文件夹当前大小与文件数量，并提供打开文件夹的按钮。

### MC 日志清理

每个 MultiMC / Prism 实例的 `.minecraft/logs` 文件夹中都会逐渐积累旧日志。清理器可一次处理所有实例，仅删除游戏自身生成的日志归档（如 `2026-08-21-1.log.gz`、`debug-1.log.gz`）；`latest.log` 与 `debug.log` 始终保留，用户自行放入的文件同样不受影响。

手动清理默认保留每个实例最近 5 个日志（数量可自定义，取消勾选则全部删除）。启动时自动清理始终保留最近的日志，仅当日志总大小超过独立阈值（同样默认 50 MB，同样默认关闭）时才执行。插件会自动检测 MultiMC / Prism Launcher 文件夹，若检测结果有误，可通过 Browse 按钮手动指定。每个实例的日志大小与文件数量均显示在标签页中。

### 快捷方式

除标签页内的按钮外，Jingle 主窗口还提供 `Clean Records` 与 `Clean Logs` 两个按钮，这两个动作也可以在 Jingle 的 Hotkeys 页面绑定快捷键。

若文件正被运行中的游戏占用而无法删除，插件会给出提示，关闭游戏后再次执行清理即可。

## 安装与卸载

**安装**

1. 前往 [Releases](../../releases) 页面，下载 `records-and-logs-bopper-1.0.0.jar`。
2. 将 jar 文件放入 Jingle 的插件文件夹：`%USERPROFILE%\.config\Jingle\plugins\`（不存在则创建）。
3. 完全重启 Jingle（不是最小化到托盘后恢复）。

**卸载**

1. 完全退出 Jingle。
2. 删除插件文件夹 `%USERPROFILE%\.config\Jingle\plugins\` 中的 `records-and-logs-bopper-1.0.0.jar`。
3. 重启 Jingle，插件即已移除。

如需删除设置，可再删除 `%USERPROFILE%\.config\Jingle\records-and-logs-bopper.properties`；除此之外不会留下其他文件。

## 许可

MIT

## AI 声明

本仓库所有代码均由 AI（大语言模型）生成。
