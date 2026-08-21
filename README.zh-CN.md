# Records & Logs Bopper

[English](README.md) | **[中文](README.zh-CN.md)**

一个 [Jingle](https://github.com/DuncanRuns/Jingle) 插件，用于清理 SpeedrunIGT 模组在 `%USERPROFILE%\speedrunigt\records` 堆积的速通记录，**同时**清理 MultiMC / Prism Launcher 实例中的 Minecraft 日志文件（`.minecraft/logs`）。

> **本插件仅支持 Jingle 1.3.0，需要 Java 17–21**（推荐 Java 21）。
> MultiMC 或 Prism Launcher 仅 MC 日志清理功能需要；速通记录清理可独立使用。

## 功能

插件提供两个清理器：一个清理速通记录，一个清理 MC 日志。

### 速通记录清理

SpeedrunIGT 每跑一次就会往 `%USERPROFILE%\speedrunigt\records` 里存一个文件，攒得很快。清理器只删真正的 SpeedrunIGT 记录——文件名格式和文件内容都会检查——你自己放进文件夹的东西不会被碰。

点"立即清理记录"手动清理（会先让你确认），也可以让它在 Jingle 启动时自动清理：文件夹超过大小阈值（默认 50 MB）才会动手，默认关闭。两种方式都可以选择保留最近的记录而不是全部删掉，保留几个随你定，默认 10 个。标签页会显示文件夹当前大小和文件数，还有一键打开文件夹的按钮。

### MC 日志清理

每个 MultiMC / Prism 实例的 `.minecraft/logs` 里，旧日志会越攒越多。这个清理器一次处理所有实例，只删游戏自己生成的日志归档（比如 `2026-08-21-1.log.gz`、`debug-1.log.gz`）。`latest.log` 和 `debug.log` 永远保留，你自己放进去的文件也不会被碰。

手动清理默认保留每个实例最近 5 个日志（数量可以改，取消勾选就全删）。启动时自动清理则始终保留最近日志，日志总大小超过独立的阈值（同样默认 50 MB，同样默认关闭）才触发。插件会自动找你的 MultiMC / Prism Launcher 文件夹；找错了就点 Browse 手动选。每个实例的日志大小和文件数都显示在标签页里。

### 快捷方式

除了标签页里的按钮，Jingle 主窗口上还有 `Clean Records` 和 `Clean Logs` 两个按钮，这两个动作也可以在 Jingle 的 Hotkeys 页面绑定快捷键。

如果文件正被运行中的游戏占用就删不掉——插件会告诉你，关掉游戏后再清一次就行。

## 安装与卸载

**安装**

1. 前往 [Releases](../../releases) 页面，下载 `records-and-logs-bopper-1.0.0.jar`。
2. 将 jar 文件放入 Jingle 的插件文件夹：`%USERPROFILE%\.config\Jingle\plugins\`（不存在则创建）。
3. 完全重启 Jingle（不是最小化到托盘后恢复）。

> **无需克隆仓库或使用命令行。** Releases 页面提供的 jar 文件就是安装所需的全部内容。

**卸载**

1. 完全退出 Jingle。
2. 删除插件文件夹 `%USERPROFILE%\.config\Jingle\plugins\` 中的 `records-and-logs-bopper-1.0.0.jar`。
3. 重启 Jingle，插件即已移除。

如需连设置一起清掉，可以再删除 `%USERPROFILE%\.config\Jingle\records-and-logs-bopper.properties`；除此之外不会留下其他文件。

## 许可

MIT

## AI 声明

本仓库所有代码均由 AI（大语言模型）生成。
