# Records & Logs Bopper

[English](README.md) | **[中文](README.zh-CN.md)**

一个 [Jingle](https://github.com/DuncanRuns/Jingle) 插件，用于清理 SpeedrunIGT 模组在 `%USERPROFILE%\speedrunigt\records` 堆积的速通记录，**同时**清理 MultiMC / Prism Launcher 实例中的 Minecraft 日志文件（`.minecraft/logs`）。

> **本插件支持 Jingle 1.1.0 及以上版本（已验证至 v2.0.0+pre5），需要 Java 17 或更高版本。**
> MultiMC 或 Prism Launcher 仅 MC 日志清理功能需要；速通记录清理可独立使用。

## 功能

速通记录清理与 MC 日志清理，用户自行添加的文件和正在写入的文件不受影响。

### 速通记录清理

删除 SpeedrunIGT 在 `%USERPROFILE%\speedrunigt\records` 产生的记录。

- 通过按钮手动清理
- 文件夹大小超过阈值（默认 50 MB）自动清理，默认关
- 可选择保留最近若干条记录

### MC 日志清理（仅限 MultiMC / Prism 启动器）

一次删除所有实例的日志。

- 自动检测 MultiMC / Prism Launcher 文件夹，也可手动指定
- 通过按钮手动清理
- 默认保留每个实例最近 5 个日志，数量可自定义
- 文件夹大小超过阈值（默认 50 MB）自动清理，默认关

### 快捷方式

Jingle 主窗口提供 `Clean Records` 与 `Clean Logs` 按钮快捷清除，也可以设置快捷键。

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
