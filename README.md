<div align="center">

<img src="docs/icon.png" width="128" alt="FuckBiliAds" />

# FuckBiliAds

**哔哩哔哩 Xposed 去广告模块 · 小视频广告自动跳过**

![version](https://img.shields.io/badge/version-1.1.1-ff2d55)
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

**定位：综合集成型模块。** 开屏、信息流、播放页、直播间、小视频、弹幕这几条链路的去广告能力
都收在同一个包里，共用一套设置页、一套日志和一套更新检查——不用为了不同广告位装好几个模块。

> **最高实测宿主版本：哔哩哔哩 `9.16.1`（versionCode 9161100）**，在 LSPosed 2.2.1 与 LSPatch 1.2 下均已验证。
> 更早的 `9.15.0` 同样可用，其它版本的兼容情况见 [兼容性](#兼容性)。

## 功能

### 开屏

| 功能 | 说明 |
|---|---|
| 开屏广告拦截 | 在 OkHttp 拦截器里给 `/x/v2/splash/list`、`/x/v2/splash/show` 返回空响应，广告不进展示层 |
| 品牌开屏隐藏 | 「22娘/33娘」本地立绘来自 `BrandSplashStorage`，不走广告接口，这里在 Fragment 显示阶段直接隐藏 |
| 启动页配色 | 启动页底色可选 **跟随系统 / 浅色 / 深色**；深色下不再白闪，logo 位置保持不变 |

### 信息流

| 功能 | 说明 |
|---|---|
| 主页 / 搜索 / 动态 Feed | 卡片级过滤，支持关键词与正则 |
| 关注页广告与抽奖 | 独立开关，规则可自定义 |
| 直播间广告 | 直播间内广告位清理 |

### 播放

| 功能 | 说明 |
|---|---|
| 播放页广告 | 播放器下方推广位、暂停页广告 |
| 弹幕发送者查询 | ⚠️ 暂不可用：hash 来源（原弹幕跳转）已移除，待重接 |

### 小视频（重点）

| 功能 | 说明 |
|---|---|
| 广告自动跳过 | 命中 `adInfo.feedIsAd` 立即翻下一条，弹 `检测到广告，已跳过！FUCK BILI ADS！` |
| 彻底剔除（实验） | 在播放列表入口摘掉广告卡，不进列表、不渲染、不预加载；每次剔除用 Toast 报条数（可单独关闭） |
| 手动标记 | 分享面板「标记广告」按钮，把当前条目字段写入日志 |

### 集成体验

| 功能 | 说明 |
|---|---|
| 设置入口 | 「我的 → 更多服务」新增 `FuckBiliAds`，界面沿用 B 站配色与控件 |
| 日志 | 文件 + logcat 双写，设置页可查看 / 导出 / 清空 |
| 更新检查 | 冷启动后查询 GitHub Release，有新版弹窗并提供「前往下载」 |
| 启动性能 | 非主进程不再扫 dex；缓存按「模块版本 + 宿主指纹」失效；缓存缺失时后台扫描，不阻塞启动 |

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
| 功能 | 小视频广告自动跳过、彻底剔除广告（实验）、跳过时提示、剔除时提示、分享面板标记入口、启动页配色 |
| 调试 | 标记当前条目、复制页面诊断、详细日志、测试宿主 Toast |
| 诊断 | 模块状态、小视频最近扫描、日志落盘状态、播报模块状态 |
| 日志 | 查看最近日志、导出日志（分享文本 + 保存到 Download）、清空日志 |
| 更新 | 启动时自动检查、包含测试版、当前 / 最新版本、立即检查、打开 Release 页面 |
| 关于 | 模块版本、宿主版本、Hook 状态、日志路径 |

日志落在宿主私有目录 `files/fuckbiliads/events.log`（512 KB 滚动），同时输出到 logcat：

```bash
adb logcat -s FuckBiliAds
```

手动标记产生的行形如：

```text
10-26 19:40:12.331 [I] [MARK] source=share activity=com.bilibili.video.story.StoryVideoActivity rv=androidx.recyclerview.widget.RecyclerView adapter=com.bilibili.video.story.L children=3 pos=6 visible=1080x2300 item=com.bilibili.video.story.StoryDetail aid=0 cid=0 adType=-1 adInfo=... feedIsAd=true creativeId=3995855299
```

## 更新检查

模块会读取本仓库的 GitHub Release 列表（正式版与 pre-release 在同一个接口里），冷启动约 12 秒后
自动查询一次，6 小时内不重复；命中更新时：

- 弹窗「发现新版本 1.0.9」：显示渠道（正式版 / 测试版）、发布时间与更新说明节选，
  **前往下载** 直接打开该版本的 Release 页面，另有「稍后」「忽略此版本」；
- 设置页「更新」分组常驻高亮横幅 + **前往下载** 按钮，顶栏版本号变成 `· 可更新`，点开即是同一个弹窗；
- 「立即检查更新」可随时手动查询（绕过 6 小时冷却），结果用 Toast / 弹窗给出；
- 只在主进程执行，断网或请求失败只写日志不打扰（手动检查会把原因显示出来）。

比较规则：

1. 先比版本号数字，`v1.0.9-pre` 视作 `1.0.9`，更高即更新；
2. 版本号相同时比 `versionCode`：CI 会把 `versionCode: N` 追加到 release 说明末尾，
   所以 `1.0.9-pre`（44）在 `1.0.9`（45）正式发版后能正确提示更新；
3. 两者都拿不到时只剩「测试版 → 正式版」这一种可判定变化，其余一律不提示，
   避免把同一个版本的自编译包误报成新版本。

设置页「更新」分组：

| 项 | 说明 |
|---|---|
| 启动时自动检查 | 默认开；关闭后只有手动检查会联网 |
| 包含测试版 | 默认开；关掉后只匹配正式版 release |
| 当前版本 / 最新版本 | 本地 `versionName (versionCode)` / 最近一次查询到的版本与发布时间 |
| 立即检查更新 | 手动查询，结果直接以弹窗提示 |
| 打开 Release 页面 | 浏览器打开 `github.com/feSHt377/FuckBiliAds/releases` |

查询过程会写进日志，排查时抓 `[UPDATE]` 前缀即可：

```bash
adb logcat -s FuckBiliAds
# [UPDATE] checking feSHt377/FuckBiliAds
# [UPDATE] newest=v1.0.9-pre pre=true local=1.0.8(40) update=true
```

## 1.1.1 变更（测试版）

- **移除「弹幕引擎替换 / 弹幕跳转」**：上线前遗留的旧功能——把宿主的 Chronos 弹幕包换成模块内置的 `3.12.0` 并给引擎 JS 打补丁，1.0.3 之后再没维护过（1.0.7 只跟着改了包名常量，`isDebugBuild` 里还留着旧 ApplicationID `com.fuck.bili.ads`）。本次整体删除：`chronos/` 三个类、根包里同名的死代码 `DanmuScriptPatcher`、`assets/danmu/`（含 1.08 MB 引擎包），以及关注页设置里的「弹幕引擎 / 恢复原版」按钮；APK 体积因此减少约 1.03 MiB。
- **「弹幕发送者查询」暂时失效**：`DanmuSenderLookup.cacheClientDanmaku()` 原本由被删掉的弹幕跳转喂数据（`dmid → hash`），现在没有 hash 来源，点「复制」只会停在日志 `客户端缓存尚未找到 dmid`。代码按「保留待重接」处理——`DanmuSenderLookup` 与 `GLOBAL_DANMAKU_DICT` 未删，后续用宿主侧 hook 直接抓 `DmSegMobileReply` 的 `dmid/hash` 即可恢复。
- **CI 的 versionCode 改为语义版本**：`major*10000 + minor*100 + patch`（`1.1.1 → 10101`）。之前取 `github.run_number`，会小于本地构建的 versionCode，导致「新包装不上、更新检查反过来认为是旧版」。
- 版本号 `1.1.1`（versionCode 10101）。

## 1.1.0 变更

- **启动页配色可选**：设置页新增「启动页配色」——跟随系统 / 浅色 / 深色。只替换窗口背景的纯色层，logo 位置与尺寸不变；深色下不再出现白闪。
- **品牌开屏直接隐藏**：`22娘/33娘` 立绘来自本地 `BrandSplashStorage`，不走 `/x/v2/splash/*`，因此在 Fragment 视图阶段直接 `GONE`；宿主的计时与跳转不受影响。
- **剔除广告有提示**：每次从播放列表剔除广告卡都弹「已剔除 N 条广告！FUCK BILI ADS！」，同时写日志 `[FILTER] 剔除 N 条（来源），累计 M`；可在设置里单独关闭。
- **启动性能（本版重点）**：
  - 非主进程不再安装 hook、不再扫 dex（之前 main / `:download` / `:web` 各扫一遍 268 MB）；
  - 缓存 key 改为「模块 versionCode + 宿主 APK 指纹」，去掉 LSPosed 下永远不成立的 `currentModuleTime != 0`（之前**每次**启动都全量重扫）；
  - 缓存改为原子写（临时文件 + rename），避免多进程并发写坏；
  - 首次启动（缓存缺失）时把扫描放到后台线程，不阻塞 `handleLoadPackage`，扫完再补装依赖 DexKit 的 hook；
  - `Gson.fromJson` 的调试字符串改成只在命中开屏响应时才构造。
- 版本号 `1.1.0`。

## 1.0.9 变更（测试版）

- **新增「彻底剔除广告（实验）」**：宿主的播放列表只在三个时机刷新——首屏、加载下一页、回滑补数据，三者都经过 `StoryPagerPlayer` 的批次方法。现在在 `T0(List)` / `t1(List)`（实例）与 `V1(player, List, …)`（静态，整表替换）的**入口**把广告卡从参数列表里摘掉，宿主后续的 size 运算、当前索引、播放器切片、曝光上报都只看得到真实视频。
- **存量清理**：钩子安装前已经进列表的广告卡（通常是首批），通过宿主自己的 `StoryPagerPlayer.remove(int)` 移除——它对广告卡有专用分支，会同步播放器与索引；我们从不直接改 adapter 列表。
- **多重兜底**：整批都是广告时保留原列表（否则列表会空、翻不动）；类或方法签名变了就整体退回原来的滚动跳过；开关可随时在设置页关闭。
- 诊断面板新增「广告剔除」一行：入口是否启用 + 本会话剔除/清理条数。
- **本版为 pre-release**：只用于测试，不打正式标签、不同步到 Xposed 模块仓库。

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
- tag 带 `-pre` 后缀（如 `v1.1.1-pre`）时发布为 pre-release，标题追加「· 测试版」，且不同步到模块仓库；
- versionCode 由 tag 的语义版本算出：`major*10000 + minor*100 + patch`（`v1.1.1-pre` → `10101`），
  保证 CI 产物一定比历史构建新；本地 `./gradlew` 的默认值也用同一套规则；
- release 说明末尾会自动追加 `versionCode: N`，应用内更新检查靠这一行区分**同版本号**的 pre 与正式版；
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
├── story/                   # 小视频广告自动跳过与剔除
│   ├── StoryAdSniper.java   # 生命周期 + 可见条目轮询 + 判定
│   ├── StoryAdFilter.java   # 播放列表入口剔除广告卡 + 剔除提示
│   ├── StoryView.java       # RecyclerView / 条目 / 字段读取
│   ├── StoryNext.java       # 下一条执行器
│   ├── StoryDebug.java      # 页面快照 / 手动标记
│   └── HostToast.java       # 复用宿主 ToastHelper
├── ui/                      # 设置页与宿主内集成
│   ├── FbaSettings.java     # 设置 / 调试 / 日志面板（B 站风格）
│   ├── FbaPrefs.java        # 开关存储
│   ├── FbaLog.java          # 事件日志（文件 + logcat）
│   ├── MineServiceEntry.java# 「我的 → 更多服务」入口 + BLRouter 拦截
│   ├── UpdateChecker.java   # GitHub Release 查询 / 版本比较 / 更新弹窗
│   └── StoryShareMark.java  # 分享面板「标记广告」按钮
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
