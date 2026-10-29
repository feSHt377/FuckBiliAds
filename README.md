<div align="center">

<img src="docs/icon.png" width="128" alt="FuckBiliAds" />

# FuckBiliAds

**哔哩哔哩 Xposed 去广告模块 · 小视频广告自动跳过**

![version](https://img.shields.io/badge/version-1.2.6--pre--release-ff2d55)
[![xposed repo](https://img.shields.io/badge/Xposed%20Repo-listed-00aeec)](https://modules.lsposed.org/module/io.github.fesht377.biliads/)
![host](https://img.shields.io/badge/host-9.17.0%20tested-00aeec)
![platform](https://img.shields.io/badge/platform-Android%208.0%2B-3ddc84)
![framework](https://img.shields.io/badge/LSPosed%20%2F%20LSPatch-API%20102-3ddc84)

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

> **已适配哔哩哔哩 `9.17.0`（versionCode 9170300）**，在 LSPatch 1.2 下完成小视频、自定义剔除、设置入口与分享标记验证；LSPosed 2.2.1 已在 9.16.1 验证。
> **HD 适配**：1.2.6 起支持 `tv.danmaku.bilibilihd`（HD 2.0.1 / 2001100）的开屏、信息流、播放页推广、动态过滤、底部导航、设置入口与日志更新检查；HD 不含手机版竖屏 Story，不提供小视频剔除与分享标记。静态逆向适配，尚未真机验证。
> 更早的 `9.15.0` 同样可用，其它版本的兼容情况见 [兼容性](#兼容性)。

## 功能

### 开屏

| 功能 | 说明 |
|---|---|
| 开屏广告拦截 | 在 OkHttp 拦截器里给 `/x/v2/splash/list`、`/x/v2/splash/show` 返回空响应，广告不进展示层 |
| 品牌开屏隐藏 | 「22娘/33娘」本地立绘来自 `BrandSplashStorage`，不走广告接口；**默认关闭**，需要隐藏时在设置里打开 |
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
| 更新检查 | 冷启动后查询 GitHub Release，有新版弹窗并提供「前往下载」；默认仅检查正式版，测试版需手动开启 |
| 启动性能 | 非主进程不再扫 dex；缓存按「模块版本 + 宿主指纹」失效；缓存缺失时后台扫描，不阻塞启动 |

## 安装

1. 选择生效方式：
   - **Root**：安装 [Magisk](https://github.com/topjohnwu/Magisk) + [LSPosed](https://github.com/LSPosed/LSPosed)；
   - **无 Root**：用 [LSPatch](https://github.com/LSPosed/LSPatch) 以「本地 / 嵌入式」模式把本模块补进哔哩哔哩；
2. 从 [Releases](../../releases) 下载 `FuckBiliAds-*.apk` 并安装；
3. 在 LSPosed 中启用模块，**作用域勾选哔哩哔哩**（`tv.danmaku.bili`；HD 用户勾选 `tv.danmaku.bilibilihd`）；LSPatch 方式则在打补丁时勾选本模块；
4. 强制停止哔哩哔哩后重新打开。

> **包名**：自 1.0.7 起模块的 ApplicationID 为 `io.github.fesht377.biliads`（旧版为 `com.fuck.bili.ads`）。
> 两者是不同的应用，从 1.0.6 及更早版本升级时请先卸载旧版、再装新版，否则会同时存在两个模块；
> 卸载旧版不会影响哔哩哔哩里的设置开关（开关存在宿主侧）。

要求：Android 8.0+（minSdk 26），**Xposed API 102**（LSPosed 2.2.1+；低于 API 102 的框架请留在 1.1.2）。

## 兼容性

### 宿主版本

| 哔哩哔哩版本 | versionCode | 状态 |
|---|---|---|
| **HD 2.0.1** | 2001100 | 🧪 1.2.6 静态适配：开屏 / 信息流 / 播放页 / 动态 / 设置入口，未真机验证 |
| **9.17.0** | 9170300 | ✅ **已适配**，LSPatch 实测核心功能 |
| 9.16.1 | 9161100 | ✅ LSPosed / LSPatch 实测核心功能 |
| 9.15.0 | 9150300 | ✅ 小视频跳过 / 设置入口 / 分享标记均可用 |
| 9.14 及更早 | — | ⚠️ 未实测，其余广告位按 9.15 的类名定位，可能失效 |
| 9.18 及更新 | — | ❓ 未验证，宿主混淆重排后部分功能可能失效 |

小视频跳过、设置入口、分享标记三项已改为**按结构匹配**（方法签名 / 字段组合 / 适配器内容），
不依赖混淆后的类名，因此在 9.15 → 9.17.0 之间保持可用；其余广告位仍由 DexKit 按类名特征定位，
宿主大版本更新后可能需要重新适配。

### 运行环境

| 项 | 要求 / 实测 |
|---|---|
| Android | 8.0+（minSdk 26），targetSdk 36；实测 Android 15（SDK 35） |
| 框架 | **Xposed API 102**；实测 **LSPosed 2.2.1（API 102）**。1.2.0 起不再使用 legacy API 82，低于 102 的框架请留在 1.1.2 |
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
带 ✗ 的那一项表示安装失败，可在设置页「运行诊断 / 日志管理」查看原因。

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
| 小视频 | 自动跳过、跳过提示、列表剔除（实验）、可展开的自定义剔除内容、剔除提示与位置 |
| 分享与标记 | 分享面板「标记广告」入口 |
| 启动页 | 启动页配色、隐藏品牌开屏立绘 |
| 模块更新 | 自动检查、测试版更新、版本信息与 Release 页面 |
| 调试工具 | 条目标记、页面快照、详细日志、Toast 与加载状态测试 |
| 日志管理 | 查看、导出（分享文本 + 保存到 Download）、清空日志 |
| 运行诊断 | Hook 安装状态、小视频扫描、广告剔除统计、日志文件状态 |
| 关于模块 | 模块版本、宿主版本、小视频 Hook、日志保存路径 |

日志落在宿主私有目录 `files/fuckbiliads/events.log`（最大 100 KB，写满后覆盖旧内容，仅保留最新日志），同时输出到 logcat：

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
- 设置页「模块更新」分组常驻高亮横幅 + **前往下载** 按钮，顶栏版本号变成 `· 可更新`，点开即是同一个弹窗；
- 「立即检查更新」可随时手动查询（绕过 6 小时冷却），结果用 Toast / 弹窗给出；
- 只在主进程执行，断网或请求失败只写日志不打扰（手动检查会把原因显示出来）。

比较规则：

1. 先比版本号数字，`v1.0.9-pre` 视作 `1.0.9`，更高即更新；
2. 版本号相同时比 `versionCode`：CI 会把 `versionCode: N` 追加到 release 说明末尾，
   所以 `1.0.9-pre`（44）在 `1.0.9`（45）正式发版后能正确提示更新；
3. 两者都拿不到时只剩「测试版 → 正式版」这一种可判定变化，其余一律不提示，
   避免把同一个版本的自编译包误报成新版本。

设置页「模块更新」分组：

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

## 更新日志

完整历史见 [GitHub Releases](https://github.com/feSHt377/FuckBiliAds/releases)；这里只留当前版本要点。

**1.2.6（测试版）**

- 新增 **哔哩哔哩 HD**（`tv.danmaku.bilibilihd`）支持：独立开屏页工厂与展示兜底、HD 我的页设置入口、播放页关联推广拦截。
- 复用 Pegasus 信息流、关注页动态过滤、底部导航、启动页外观、日志和更新检查；Pegasus 方法按结构匹配。
- HD 不含手机版竖屏 Story，不加载小视频剔除与分享标记，设置页隐藏这些选项。
- **自动更新默认只获取正式版**，需要 pre-release 时在「模块更新」手动开启。

## 1.2.5 变更

- 修复更新后第一次启动可能闪出开屏广告：在缓存商业开屏页创建前拦截，直接走宿主无广告退出流程，不再等待广告渲染完成后才跳过。
- 请求拦截与展示兜底继续保留；`[SPLASH]` 日志可区分缓存页拦截与实际请求命中。

## 1.2.4 变更

- 标注已适配 **哔哩哔哩 9.17.0**（LSPatch 实测核心功能）。
- 合并剔除总开关与多选：广告默认勾选，可选直播、影视/番剧、课程、充电/预览视频；关闭总开关后不执行任何列表剔除。
- 播报改为「已剔除 N 个内容」；可开启详细播报，显示「广告 *1，直播 *2」等分类数量。
- 日志文件最大 **100 KB**，写满覆盖旧内容；补充 `[SPLASH]` 开屏请求与展示兜底诊断。

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
- release 正文 = **tag 注释** + `**Full Changelog**: …` + `versionCode: N`：
  tag 注释用 `git tag -a v1.2.0 -F notes.md` 写好，页面直接用它当说明；
  末尾那行 `versionCode` 是应用内更新检查区分**同版本号** pre / 正式版的依据，不要手动删；
- 不要靠删 tag 重发版本：GitHub 会把关联 release 转成 **Draft**，重推同一 tag 时工作流会显式
  `draft: false` 救回来，但期间发布页会闪一下 Draft，用户也可能看到；
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
├── MainHook.java            # 入口（XposedModule#onPackageLoaded），按功能分派 Hook
├── core/                    # modern API 基础设施
│   ├── Reflect.java         # 反射匹配 / 装箱 / 父类接口查找
│   ├── NativeHooks.java     # Hook 注册与异常模式
│   ├── HookRegistry.java    # 句柄对账与落盘
│   └── Host.java            # Application / processName
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
