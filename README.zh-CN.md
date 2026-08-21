# Records & Logs Bopper

[English](README.md) | **[中文](README.zh-CN.md)**

一个 [Jingle](https://github.com/DuncanRuns/Jingle) 插件，用于清理 SpeedrunIGT 模组在 `%USERPROFILE%\speedrunigt\records` 堆积的速通记录，**同时**清理 MultiMC / Prism Launcher 实例中的 Minecraft 日志文件（`.minecraft/logs`）。

> **本插件仅支持 Jingle 1.3.0，需要 Java 17–21**（推荐 Java 21）。
> MultiMC 或 Prism Launcher 仅 MC 日志清理功能需要；速通记录清理可独立使用。

## 功能

### 通用

- **单标签页** — 两个清理功能都在 Jingle 插件菜单的 `Records & Logs Bopper` 标签页内。
- **删除确认** — 每次手动删除前都会弹出警告对话框，需要明确确认才会执行。
- **快捷按钮** — Jingle 主窗口上的 `Clean Records` 和 `Clean Logs` 快捷按钮。
- **热键动作** — 可在 Jingle 的 Hotkeys 页面将 `Clean Records` 和 `Clean Logs` 动作绑定到任意快捷键。
- **设置持久化** — 所有设置保存在 `~/.config/Jingle/records-and-logs-bopper.properties`。
- **占用文件保护** — 被运行中游戏占用的文件无法删除，会计入失败并提示，不会导致崩溃；关闭游戏后重新清理即可。

### 速通记录清理

- **只删验证过的记录** — 清理 `%USERPROFILE%\speedrunigt\records`，仅删除经过验证的 SpeedrunIGT 记录文件（UUID 文件名 + `final_igt` JSON 字段）。用户自行放入的文件不会被删除。
- **手动清理** — "立即清理记录"按钮，带确认对话框；结果弹窗显示删除数量、释放空间和跳过的非记录文件。
- **保留最近记录（数量可自定义）** — 清理时可选择保留最近 N 个记录（默认 10；手动与自动清理均默认关闭；数量可在标签页中修改）。
- **启动时自动清理** — Jingle 启动时，若记录文件夹超过大小阈值（默认 50 MB，可自定义），则自动清理。**默认关闭**，与日志自动清理相互独立。
- **文件夹工具** — "打开 records 文件夹"按钮，实时状态行（文件夹大小、文件数、当前阈值）。

### MC 日志清理

- **一次清理所有实例** — 同时清理找到的每个 MultiMC / Prism 实例的 `.minecraft/logs`。
- **只删游戏生成的日志** — 仅删除 Minecraft 滚动日志归档（如 `2026-08-21-1.log.gz`、`debug-1.log.gz`）。`latest.log` 和 `debug.log` 永不删除；用户自行放入的文件和文件夹不会被触碰。
- **手动清理** — "立即清理旧日志"按钮，带确认对话框；可选择保留每个实例最近 N 个日志（默认 5，默认开启，可在标签页中切换）。
- **启动时自动清理** — Jingle 启动时，若日志总大小超过阈值（默认 50 MB，可自定义），自动清理旧日志，始终保留每个实例最近 N 个日志（默认 5）。**默认关闭**，与记录自动清理相互独立。
- **MultiMC 自动检测** — 通过扫描常见路径和查询运行中进程自动找到 MultiMC / Prism Launcher；也支持手动 Browse 选择。
- **分实例状态** — 显示每个实例的名称、日志大小和文件数。

### 输入校验

- 数量框只接受非负整数（0 或正整数）；阈值框只接受 ≥ 0 MB 的数字。
- 负数、小数（数量框）、字母、表达式、`NaN` 以及超出可处理范围的数字全部拒绝。
- 非法输入在输入时即以红色内联提示标记，提交时报错误对话框（随后恢复为上次合法值）——任何错误输入都不会导致插件崩溃。
- 输入新的保留数量时，勾选框文字实时更新。

## 安装

1. 前往 [Releases](../../releases) 页面，下载 `records-and-logs-bopper-1.0.0.jar`。
2. 将 jar 文件放入 Jingle 的插件文件夹：`%USERPROFILE%\.config\Jingle\plugins\`（不存在则创建）。
3. 完全重启 Jingle（不是最小化到托盘后恢复）。

> **无需克隆仓库或使用命令行。** Releases 页面提供的 jar 文件就是安装所需的全部内容。

## 安全说明

- 速通记录清理仅作用于 `%USERPROFILE%\speedrunigt\records`，且仅删除经过验证的 SpeedrunIGT 记录文件（UUID 文件名 + `final_igt` 内容），不会碰其他任何文件。
- 日志清理仅作用于 MultiMC / Prism 实例内 `.minecraft/logs` 中由游戏生成的日志归档。`latest.log` 和 `debug.log` 永不删除；用户自行放入的文件和文件夹不会被触碰。
- 每次手动删除前都会弹出警告对话框，需要明确确认。
- 被运行中游戏占用的文件无法删除；会提示信息，关闭游戏后重新清理即可。
- 卸载 = 删除 jar 文件。

## 许可

MIT

## AI 声明

本仓库所有代码均由 AI（大语言模型）生成。
