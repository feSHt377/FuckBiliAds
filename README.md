<div align="center">

<img src="docs/icon.png" width="128" alt="FuckBiliAds" />

# FuckBiliAds

**哔哩哔哩 Xposed 去广告模块 · 小视频广告自动跳过**

![version](https://img.shields.io/badge/version-1.0.8-ff2d55)
[![xposed repo](https://img.shields.io/badge/Xposed%20Repo-listed-00aeec)](https://modules.lsposed.org/module/io.github.fesht377.biliads/)
![host](https://img.shields.io/badge/host-9.16.1%20tested-00aeec)
![platform](https://img.shields.io/badge/platform-Android%208.0%2B-3ddc84)
![framework](https://img.shields.io/badge/LSPosed%20%2F%20LSPatch-API%2082%2B-3ddc84)

</div>

---

## 这是什么

**FuckBiliAds** 是一个面向哔哩哔哩 Android 客户端的 Xposed 模块，用来清理各类广告位，
并新增了「竖屏小视频广告自动跳过」：

> 刷小视频时一旦检测到广告，立即滑到下一条，并用 B 站自己的底部 Toast 提示
> **`检测到广告，已跳过！FUCK BILI ADS！`**

本项目基于 [fredoseep/biliDanmuCheck](https://github.com/fredoseep/biliDanmuCheck) 二次开发，
包名、应用名、图标与发布流程均已重做，后续由本仓库独立维护。

> **最高实测宿主版本：哔哩哔哩 `9.16.1`（versionCode 9161100）**，在 LSPosed 2.2.1 与 LSPatch 1.2 下均已验证。
> 更早的 `9.15.0` 同样可用，其它版本的兼容情况见 [兼容性](#兼容性)。

## 功能

| 功能 | 说明 |
|---|---|
| 开屏广告 | 从请求处拦截，不再展示 |
| 主页 / 搜索 / 动态 Feed 广告 | 卡片级过滤，支持关键词与正则 |
| 关注页广告与抽奖 | 单独开关，可自定义过滤规则 |
| 直播间广告 | 直播间内广告位清理 |
| 播放页广告 | 播放器下方推广位、暂停页广告 |
| **小视频广告自动跳过** | 检测到 `feedIsAd` 即跳下一条，并弹 B 站 Toast |
| **设置入口** | 「我的 → 更多服务」新增 `FuckBiliAds` 入口，界面沿用 B 站配色与控件 |
| **手动标记** | 小视频分享面板内「标记广告」按钮，把当前条目字段写入日志 |
| **日志查看 / 导出 / 清空** | 设置页内可查看、复制、分享或保存到 `Download/FuckBiliAds` |
| 弹幕发送者查询 | 点击弹幕「复制」即可跳转到发送者主页 |
| 播放器替换 | 旧版本提示播放器不兼容时可替换目标版本播放器 |

## 安装

1. 选择生效方式：
   - **Root**：安装 [Magisk](https://github.com/topjohnwu/Magisk) + [LSPosed](https://github.com/LSPosed/LSPosed)；
   - **无 Root**：用 [LSPatch](https://github.com/LSPosed/LSPatch) 以「本地 / 嵌入式」模式把本模块补进哔哩哔哩；
2. 从 [Releases](../../releases) 下载 `FuckBiliAds-*.apk` 并安装；
3. 在 LSPosed 中启用模块，**作用域勾选哔哩哔哩**（`tv.danmaku.bili`）；LSPatch 方式则在打补丁时勾选本模块；
4. 强制停止哔哩哔哩后重新打开。

> **包名**：自 1.0.7 起模块的 ApplicationID 为 `io.github.fesht377.biliads`（旧版为 `com.fuck.bili.ads`）。
> 两者是不同的应用，从 1.0.6 及更早版本升级时请先卸载旧版、再装新版，否则会同时存在两个模块；
> 卸载旧版不会影响哔哩哔哩里的设置开关（开关存在宿主侧）。

要求：Android 8.0+（minSdk 26），Xposed API 82+（LSPosed 与 LSPatch 均可）。

## 兼容性

### 宿主版本

| 哔哩哔哩版本 | versionCode | 状态 |
|---|---|---|
| **9.16.1** | 9161100 | ✅ **最高实测版本**，当前开发与验证基线 |
| 9.15.0 | 9150300 | ✅ 小视频跳过 / 设置入口 / 分享标记均可用 |
| 9.14 及更早 | — | ⚠️ 未实测，其余广告位按 9.15 的类名定位，可能失效 |
| 9.17 及更新 | — | ❓ 未验证，宿主混淆重排后部分功能可能失效 |

小视频跳过、设置入口、分享标记三项已改为**按结构匹配**（方法签名 / 字段组合 / 适配器内容），
不依赖混淆后的类名，因此在 9.15 → 9.16.1 之间保持可用；其余广告位仍由 DexKit 按类名特征定位，
宿主大版本更新后可能需要重新适配。

### 运行环境

| 项 | 要求 / 实测 |
|---|---|
| Android | 8.0+（minSdk 26），targetSdk 36；实测 Android 15（SDK 35） |
| 框架 | Xposed API 82+；实测 **LSPosed 2.2.1（API 102）** 与 **LSPatch 1.2（API 102）** 均正常 |
| 架构 | 实测 arm64-v8a；模块无 native 依赖，armeabi-v7a / x86_64 理论上同样可用 |
| 安装形态 | Root + LSPosed；或无 Root 用 LSPatch 把模块补进宿主 |
| 模块包名 | `io.github.fesht377.biliads`（1.0.7 起；旧版 `com.fuck.bili.ads` 不再更新） |
| 实测设备 | OnePlus PGZ110 · Android 15 · arm64-v8a · 哔哩哔哩 9.16.1 |

### 已知限制

- **LSPatch 容器下部分广告位可能不生效**：`DexKitHelper` 用 `lpparam.appInfo.sourceDir` 打开 APK 做扫描，
  容器包的 `sourceDir` 指向体积很小的 stub，扫不到真实 dex，于是开屏 / Feed / 直播间 / 播放页等
  「按类名定位」的广告位会静默跳过；小视频跳过、设置入口、分享标记不依赖 DexKit，不受影响。
- **宿主被二次修改**（加固、改包、非官方渠道）时类名与字段可能再次变化，需要重新适配。
- 模块只作用于作用域内的 `tv.danmaku.bili`，勾选其它包名不会生效。

进入宿主后若看到 Toast `已加载 · 小视频✓ 入口✓ 分享✓`，说明三项核心功能均已挂上；
带 ✗ 的那一项表示安装失败，可在设置页「诊断 / 日志」查看原因。

## 小视频自动跳过是怎么做到

- 小视频页的广告与普通视频是**同一个数据类**，唯一可靠信号是 `StoryDetail.adInfo.feedIsAd == true`；
- `adType` 恒为 `-1`、`natureAd` 随机翻转，都不可用；
- 列表会预加载多条，所以判定只看**当前可见面积最大的那条**（先取该 child 的 ViewHolder 字段，适配器 List 只作兜底）；
- 命中后调用宿主自己的 `RecyclerView` 滚动 API 翻一页，不模拟手势、不改数据；
- 滚动请求发出后才弹 Toast，900ms 校验位置确实前进，否则降级重试；
- 同一广告（按 `feedCreativeId`）只跳一次，带冷却与次数上限，避免与手动滑动打架。

排查时抓 `FuckBiliAds` 日志即可看到每一步：

```bash
adb logcat -s FuckBiliAds
# story ad auto skip ready -> 生效
# story: ad detected at pos N id=c... -> 命中
# story ad skipped at pos N -> 已翻页
```

完整调研、探测数据与实现细节见 [STORY_AD_SKIP.md](STORY_AD_SKIP.md)。

## 设置与日志

设置页入口在 **我的 → 更多服务 → FuckBiliAds**，也可以长按分享面板上的「标记广告」按钮直接复制当前页面诊断。

| 分组 | 内容 |
|---|---|
| 功能 | 小视频广告自动跳过、跳过时提示、分享面板标记入口 |
| 调试 | 标记当前条目、复制页面诊断、详细日志、测试宿主 Toast |
| 诊断 | 模块状态、小视频最近扫描、日志落盘状态、播报模块状态 |
| 日志 | 查看最近日志、导出日志（分享文本 + 保存到 Download）、清空日志 |
| 关于 | 模块版本、宿主版本、Hook 状态、日志路径 |

日志落在宿主私有目录 `files/fuckbiliads/events.log`（512 KB 滚动），同时输出到 logcat：

```bash
adb logcat -s FuckBiliAds
```

手动标记产生的行形如：

```text
10-26 19:40:12.331 [I] [MARK] source=share activity=com.bilibili.video.story.StoryVideoActivity rv=androidx.recyclerview.widget.RecyclerView adapter=com.bilibili.video.story.L children=3 pos=6 visible=1080x2300 item=com.bilibili.video.story.StoryDetail aid=0 cid=0 adType=-1 adInfo=... feedIsAd=true creativeId=3995855299
```

## 1.0.8 变更

- 上架 [Xposed 模块仓库](https://modules.lsposed.org/)，首个模块仓库 release 由 CI 自动同步（tag 形如 `9-1.0.8`，versionCode 取自 CI run number）；
- 仓库内 `SUMMARY` / `README.md`（上架版）已推送，包名保持 `io.github.fesht377.biliads`，功能与 1.0.7 一致。

## 1.0.7 变更（包名切换）

- **ApplicationID 改为 `io.github.fesht377.biliads`**：Xposed 模块仓库要求包名是自有域名反写或 `io.github.{用户名}`，原 `com.fuck.bili.ads` 无法通过审核。Java 包名保持 `com.fuck.bili.ads` 不变，只改 ApplicationID，因此代码与 Hook 无改动。
- **`xposedminversion` 从 54 提到 82**，与编译所用的 Xposed API 保持一致，避免旧框架按 54 加载后崩溃。
- 设置页「模块版本」改为读模块自己的 `BuildConfig`，不再依赖硬编码包名。
- 模块同步发布到 [Xposed 模块仓库](https://modules.lsposed.org/)。

## 1.0.4 ~ 1.0.6 变更（9.16.1 适配）

- **启动播报**：进入 B 站后用 Toast 显示 `已加载 · 小视频✓ 入口✓ 分享✓`，加载失败或部分失败会带 ✗；设置页「诊断 → 播报模块状态」可随时重播。
- **按结构匹配，不认混淆名**：9.16 把分享控制器从 `r0` 换成 `k0`、列表适配器从 `L` 换成 `X`。现在分享入口锚在 `StoryActionHelper` 的 `(StoryDetail, StoryShareFrom) -> String` 方法签名上，列表则按「适配器里是否有 `adInfo`+`adType` 的条目对象」来选，不再依赖类名。
- **入口不再消失**：服务端静默刷新会重建 `MenuGroup`，入口改为在适配器每次 bind 前补齐，并保留 10 秒重试窗口。
- **日志补齐**：内存环形缓冲 + 文件双写，设置页同时展示「本次运行」和「历史日志」；每个功能的 Hook 安装结果单独记录，单个失败不会中断其它功能。
- **小视频看门狗**：每 2 秒检查前台 Activity，即使生命周期 Hook 在 LSPatch 上不生效，进入小视频页也会自动开始检测。
- **诊断可见**：设置页「诊断」分组显示模块各功能状态、小视频最近一次扫描（rv/适配器/pos/条目/adInfo）。
- **1.0.5**：分享面板若为独立对话框窗口，「标记广告」按钮只跟随窗口 6 秒，关闭面板后不会残留在页面上。
- **1.0.6**：9.16 的我的页把宫格项交给 `MinePageManager` 处理、**不再走 BLRouter**，未知 uri 点击无反应；改为在条目 ViewHolder 的 `onClick` 上按 `uri` 拦截，点击直接打开设置页。

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

仓库需要的 Actions Secrets（已由 `gh secret set` 配置）：

| Secret | 内容 |
|---|---|
| `KEYSTORE_BASE64` | 签名密钥库的 Base64 |
| `KEYSTORE_PASSWORD` | 密钥库口令 |
| `KEY_ALIAS` | `fuckbiliads` |
| `KEY_PASSWORD` | 密钥口令 |

签名密钥请不要入库；本机备份放在仓库之外，更换密钥会导致已安装版本无法覆盖升级。

## 模块仓库

本模块同时发布到 [Xposed 模块仓库](https://modules.lsposed.org/)，已收录：
**[modules.lsposed.org/module/io.github.fesht377.biliads](https://modules.lsposed.org/module/io.github.fesht377.biliads/)**
（搜索 `FuckBiliAds` 或包名即可）。LSPosed 管理器的「模块仓库」直接读这个索引，可在线安装与更新。

上架规则要求 ApplicationID 为自有域名反写或 `io.github.{用户名}` 前缀，因此 1.0.7 起包名改为
`io.github.fesht377.biliads`；索引仓库为
[`Xposed-Modules-Repo/io.github.fesht377.biliads`](https://github.com/Xposed-Modules-Repo/io.github.fesht377.biliads)，
只放 `SUMMARY`、`README.md` 与 release 产物，源码仍以本仓库为准。

- release tag 必须是 `VersionCode-VersionName`（例如 `9-1.0.7`），且 release 必须带 APK，否则不会进索引；
- 仓库描述 / 名称只能在网页端改（官方限制：无法通过 API 更新）；
- 上架用的 `SUMMARY` 与 `README.md` 模板放在 [`docs/module-repo/`](docs/module-repo/)；
- 打标签后 `.github/workflows/release.yml` 会顺带把同一个 APK 同步发到模块仓库，
  需要先在 Secrets 里配置 `MODULE_REPO_TOKEN`（对 `Xposed-Modules-Repo/...` 有写权限的 PAT）；未配置时该步骤自动跳过。

## 目录

```text
app/src/main/java/com/fuck/bili/ads/
├── MainHook.java            # 入口，按功能分派 Hook
├── story/                   # 小视频广告自动跳过
│   ├── StoryAdSniper.java   # 生命周期 + 可见条目轮询 + 判定
│   ├── StoryView.java       # RecyclerView / 条目 / 字段读取
│   ├── StoryNext.java       # 下一条执行器
│   ├── StoryDebug.java      # 页面快照 / 手动标记
│   └── HostToast.java       # 复用宿主 ToastHelper
├── ui/                      # 设置页与宿主内集成
│   ├── FbaSettings.java     # 设置 / 调试 / 日志面板（B 站风格）
│   ├── FbaPrefs.java        # 开关存储
│   ├── FbaLog.java          # 事件日志（文件 + logcat）
│   ├── MineServiceEntry.java# 「我的 → 更多服务」入口 + BLRouter 拦截
│   └── StoryShareMark.java  # 分享面板「标记广告」按钮
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
