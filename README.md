<div align="center">

<img src="docs/icon.png" width="128" alt="FuckBiliAds" />

# FuckBiliAds

**哔哩哔哩 Xposed 去广告模块 · 手机版与 HD 通用**

[![release](https://img.shields.io/github/v/release/feSHt377/FuckBiliAds?color=ff2d55)](https://github.com/feSHt377/FuckBiliAds/releases/latest)
[![stars](https://img.shields.io/github/stars/feSHt377/FuckBiliAds?label=stars&color=ff2d55)](https://github.com/feSHt377/FuckBiliAds/stargazers)
[![downloads](https://img.shields.io/github/downloads/feSHt377/FuckBiliAds/total?color=00aeec)](https://github.com/feSHt377/FuckBiliAds/releases)
[![xposed repo](https://img.shields.io/badge/Xposed%20Repo-listed-00aeec)](https://modules.lsposed.org/module/io.github.fesht377.biliads/)
![host](https://img.shields.io/badge/host-9.17.0%20%2B%20HD%202.5.1-00aeec)
![platform](https://img.shields.io/badge/platform-Android%208.0%2B-3ddc84)
![framework](https://img.shields.io/badge/LSPosed%20%2F%20LSPatch-API%20102-3ddc84)

</div>

---

## 这是什么

面向哔哩哔哩 Android 客户端的 Xposed 去广告模块。手机版与 HD 共用同一套设置页、日志和更新检查，
开屏、信息流、播放页、直播间、小视频、关注页的广告都收在这一个模块里，不用为不同广告位装好几个。

基于 [fredoseep/biliDanmuCheck](https://github.com/fredoseep/biliDanmuCheck) 二次开发，独立维护。

## 最新支持

| 能力 | 说明 |
|---|---|
| 开屏 | 商业、品牌、热开屏一并拦截；已缓存的素材同样不展示 |
| 主页信息流 | 卡片级过滤，支持关键词与正则 |
| 播放页 | 播放器下方推广位、暂停页广告 |
| 直播间 | 直播间内广告位清理 |
| 小视频 | 命中广告立即滑下一条；可把广告卡从播放列表彻底剔除，支持自定义剔除直播 / 影视 / 课程 / 预览 |
| 关注页 | 广告与抽奖动态 |
| 分项开关 | 开屏 / 信息流 / 播放页 / 直播间 / 关注页可分别关闭，默认全开 |
| 设置入口 | 「我的 → 更多服务 → FuckBiliAds」，手机版与 HD 位置一致 |
| 日志与诊断 | 文件 + logcat 双写，设置页可查看、导出、清空 |

完整变更见 [Releases](https://github.com/feSHt377/FuckBiliAds/releases)。

## 安装

1. 下载 `FuckBiliAds-*.apk` 并安装：从 [Releases](https://github.com/feSHt377/FuckBiliAds/releases) 下载，
   或在 LSPosed 管理器的「模块仓库」里搜索 `FuckBiliAds` 在线安装。
2. 启用模块，二选一：
   - **有 Root**：在 LSPosed 中启用本模块，作用域勾选「哔哩哔哩」（HD 用户勾「哔哩哔哩HD」）；
   - **无 Root**：用 [LSPatch](https://github.com/LSPosed/LSPatch) 把本模块补进哔哩哔哩。
3. 强制停止哔哩哔哩，再重新打开。
4. 进入「我的 → 更多服务 → FuckBiliAds」，按需开关各项拦截。

遇到问题、需要看日志或手动排查时，见 [详细安装与使用](docs/USAGE.md)。

## 兼容性

| 宿主 | 版本 | 状态 |
|---|---|---|
| 哔哩哔哩 | 9.17.0（9170300） | ✅ LSPatch 实测核心功能 |
| 哔哩哔哩 | 9.16.1（9161100）、9.15.0 | ✅ 实测 |
| 哔哩哔哩 HD | **2.5.1** | ✅ 开屏、我的页入口、播放页、信息流 |
| 9.14 及更早 / 9.18 及更新 | — | ⚠️ 未验证 |

- Android 8.0+（minSdk 26），Xposed API 102（LSPosed 2.2.1+ 或 LSPatch）
- 模块包名 `io.github.fesht377.biliads`

## 文档

| 文档 | 内容 |
|---|---|
| [安装与使用](docs/USAGE.md) | 安装步骤、设置分组、日志、更新检查、已知限制 |
| [构建与发布](docs/BUILD.md) | 本地构建、签名、CI 流程、模块仓库同步、目录结构 |
| [小视频跳过原理](docs/STORY_SKIP.md) | 广告判定依据与剔除机制 |
| [Xposed 模块仓库](https://modules.lsposed.org/module/io.github.fesht377.biliads/) | LSPosed 管理器内在线安装与更新 |

## 致谢

- 上游项目：[fredoseep/biliDanmuCheck](https://github.com/fredoseep/biliDanmuCheck)
- 弹幕 hash 解密算法：[esterTion/BiliBili_crc2mid](https://github.com/esterTion/BiliBili_crc2mid)
- 动态分析：[DexKit](https://github.com/LuckyPray/DexKit)

## 免责声明

本项目仅供**个人学习与技术研究**使用，请勿用于商业用途或二次分发。
使用本模块可能违反哔哩哔哩用户协议，由此产生的一切后果由使用者自行承担。
