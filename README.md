<div align="center">

<img src="docs/icon.png" width="128" alt="FuckBiliAds" />

# FuckBiliAds

**哔哩哔哩 Xposed 去广告模块 · 小视频广告自动跳过**

![version](https://img.shields.io/badge/version-1.0.1-ff2d55)
![platform](https://img.shields.io/badge/platform-Android%208.0%2B-3ddc84)
![framework](https://img.shields.io/badge/LSPosed-%E2%89%A5%2054-00aeec)

</div>

---

## 这是什么

**FuckBiliAds** 是一个面向哔哩哔哩 Android 客户端的 Xposed 模块，用来清理各类广告位，
并新增了「竖屏小视频广告自动跳过」：

> 刷小视频时一旦检测到广告，立即滑到下一条，并用 B 站自己的底部 Toast 提示
> **`检测到广告，已跳过！FUCK BILI ADS！`**

本项目基于 [fredoseep/biliDanmuCheck](https://github.com/fredoseep/biliDanmuCheck) 二次开发，
包名、应用名、图标与发布流程均已重做，后续由本仓库独立维护。

## 功能

| 功能 | 说明 |
|---|---|
| 开屏广告 | 从请求处拦截，不再展示 |
| 主页 / 搜索 / 动态 Feed 广告 | 卡片级过滤，支持关键词与正则 |
| 关注页广告与抽奖 | 单独开关，可自定义过滤规则 |
| 直播间广告 | 直播间内广告位清理 |
| 播放页广告 | 播放器下方推广位、暂停页广告 |
| **小视频广告自动跳过** | 检测到 `feedIsAd` 即跳下一条，并弹 B 站 Toast |
| 弹幕发送者查询 | 点击弹幕「复制」即可跳转到发送者主页 |
| 播放器替换 | 旧版本提示播放器不兼容时可替换目标版本播放器 |

## 安装

1. 手机已 **Root**，并安装 [Magisk](https://github.com/topjohnwu/Magisk) 与 [LSPosed](https://github.com/LSPosed/LSPosed)；
2. 从 [Releases](../../releases) 下载 `FuckBiliAds-*.apk` 并安装；
3. 在 LSPosed 中启用模块，**作用域勾选哔哩哔哩**（`tv.danmaku.bili`）；
4. 强制停止哔哩哔哩后重新打开。

要求：Android 8.0+（minSdk 26），Xposed API 54+。

## 小视频自动跳过是怎么做到

- 小视频页的广告与普通视频是**同一个数据类**，唯一可靠信号是 `StoryDetail.adInfo.feedIsAd == true`；
- `adType` 恒为 `-1`、`natureAd` 随机翻转，都不可用；
- 列表会预加载多条，所以判定只看**当前可见面积最大的那条**；
- 命中后调用宿主自己的 `RecyclerView` 滚动 API 翻一页，不模拟手势、不改数据；
- 同一广告只跳一次，带冷却与次数上限，避免与手动滑动打架。

完整调研、探测数据与实现细节见 [STORY_AD_SKIP.md](STORY_AD_SKIP.md)。

## 构建

```bash
# JDK 17+（CI 使用 21）
./gradlew :app:assembleRelease
```

签名通过环境变量注入，未提供时产物为未签名 APK：

```bash
export KEYSTORE_FILE=/path/to/release.jks
export KEYSTORE_PASSWORD=...
export KEY_ALIAS=fuckbiliads
export KEY_PASSWORD=...
```

## 发布

- 推送 `v*` 标签即触发 [`.github/workflows/release.yml`](.github/workflows/release.yml)：
  构建 → 签名 → 校验签名 → 上传 APK 并创建 Release；
- 也可在 Actions 页面手动 `workflow_dispatch`，只产出构建工件。

## 目录

```text
app/src/main/java/com/fuck/bili/ads/
├── MainHook.java            # 入口，按功能分派 Hook
├── story/                   # 小视频广告自动跳过
│   ├── StoryAdSniper.java   # 生命周期 + 可见条目轮询 + 判定
│   ├── StoryView.java       # RecyclerView / 条目 / 字段读取
│   ├── StoryNext.java       # 下一条执行器
│   └── HostToast.java       # 复用宿主 ToastHelper
├── chronos/                 # 弹幕跳转
├── settings/                # 关注页过滤设置
└── *Sniper.java             # 各类广告位
```

## 致谢

- 上游项目：[fredoseep/biliDanmuCheck](https://github.com/fredoseep/biliDanmuCheck)
- 弹幕 hash 解密算法：[esterTion/BiliBili_crc2mid](https://github.com/esterTion/BiliBili_crc2mid)
- 动态分析：[DexKit](https://github.com/LuckyPray/DexKit)

## 免责声明

本项目仅供**个人学习与技术研究**使用，请勿用于商业用途或二次分发。
使用本模块可能违反哔哩哔哩用户协议，由此产生的一切后果由使用者自行承担。
