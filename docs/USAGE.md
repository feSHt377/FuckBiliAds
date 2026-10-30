# 使用说明

## 安装

1. 选择生效方式：
   - **Root**：安装 [Magisk](https://github.com/topjohnwu/Magisk) + [LSPosed](https://github.com/LSPosed/LSPosed)；
   - **无 Root**：用 [LSPatch](https://github.com/LSPosed/LSPatch) 以「本地 / 嵌入式」模式把本模块补进哔哩哔哩。
2. 从 [Releases](https://github.com/feSHt377/FuckBiliAds/releases) 下载 `FuckBiliAds-*.apk` 并安装，
   或在 LSPosed 管理器的「模块仓库」里搜索 `FuckBiliAds` 在线安装。
3. 在 LSPosed 中启用模块，**作用域勾选哔哩哔哩**（`tv.danmaku.bili`；HD 勾选 `tv.danmaku.bilibilihd`）；
   LSPatch 方式则在打补丁时勾选本模块。
4. 强制停止哔哩哔哩后重新打开。

进入宿主后若看到 Toast `已加载 · 小视频✓ 入口✓ 分享✓`，说明三项核心功能都已挂上；
带 ✗ 的那项表示安装失败，可在设置页「运行诊断 / 日志管理」查看原因。

> **包名**：自 1.0.7 起 ApplicationID 为 `io.github.fesht377.biliads`（旧版 `com.fuck.bili.ads`）。
> 两者是不同的应用，从 1.0.6 及更早版本升级时请先卸载旧版再装新版；
> 卸载旧版不影响哔哩哔哩里的设置开关（开关存在宿主侧）。

环境要求：Android 8.0+（minSdk 26），**Xposed API 102**（LSPosed 2.2.1+）；
低于 API 102 的框架请留在 1.1.2。

## 设置

入口在 **我的 → 更多服务 → FuckBiliAds**，手机版与 HD 位置一致。
长按分享面板上的「标记广告」按钮可以直接复制当前页面诊断。

| 分组 | 内容 |
|---|---|
| 广告拦截 | 开屏、主页信息流、播放页与暂停页、直播间、关注页，可分别关闭；小视频广告为可展开子组 |
| 分享与标记 | 分享面板「标记广告」入口 |
| 启动页 | 启动页配色（跟随系统 / 浅色 / 深色）、隐藏品牌开屏立绘 |
| 模块更新 | 自动检查、测试版更新、版本信息与 Release 页面 |
| 调试工具 | 条目标记、页面快照、详细日志、Toast 与加载状态测试 |
| 日志管理 | 查看、导出（分享文本 + 保存到 Download）、清空日志 |
| 运行诊断 | Hook 安装状态、小视频扫描、广告剔除统计、日志文件状态 |
| 关于模块 | 模块版本、宿主版本、小视频 Hook、日志保存路径 |

## 日志

日志写在宿主私有目录 `files/fuckbiliads/events.log`，单文件最大 100 KB，写满后覆盖旧内容、
只保留最新记录；同时输出到 logcat：

```bash
adb logcat -s FuckBiliAds
```

按前缀分流：`[BOOT]`、`[HOOK INSTALL]`、`[HOOK REGISTRY]`、`[SPLASH]`、`[FILTER]`、`[UPDATE]`、`[HD]`。

手动标记产生的行形如：

```text
10-26 19:40:12.331 [I] [MARK] source=share activity=com.bilibili.video.story.StoryVideoActivity rv=androidx.recyclerview.widget.RecyclerView adapter=com.bilibili.video.story.L children=3 pos=6 visible=1080x2300 item=com.bilibili.video.story.StoryDetail aid=0 cid=0 adType=-1 adInfo=... feedIsAd=true creativeId=3995855299
```

## 更新检查

模块读取本仓库的 GitHub Release 列表（正式版与 pre-release 在同一个接口里），冷启动约 12 秒后
查询一次，6 小时内不重复。命中更新时：

- 弹窗显示渠道（正式版 / 测试版）、发布时间与更新说明节选，可直接「前往下载」；
- 设置页「模块更新」分组常驻高亮横幅，顶栏版本号变成 `· 可更新`；
- 「立即检查更新」可随时手动查询（绕过 6 小时冷却）；
- 只在主进程执行，断网或请求失败只写日志不打扰。

比较规则：

1. 先比版本号数字，`v1.0.9-pre` 视作 `1.0.9`，更高即更新；
2. 版本号相同时比 `versionCode`：CI 会把 `versionCode: N` 追加到 release 说明末尾，
   所以 `1.0.9-pre`（44）在 `1.0.9`（45）正式发版后能正确提示更新；
3. 两者都拿不到时只剩「测试版 → 正式版」这一种可判定变化，其余一律不提示。

「包含测试版」默认**关闭**，需要测试版时在设置里手动开启。

## 已知限制

- **LSPatch 容器下部分广告位可能不生效**：`DexKitHelper` 用 `lpparam.appInfo.sourceDir` 打开 APK 扫描，
  容器包的 `sourceDir` 指向体积很小的 stub，扫不到真实 dex，于是开屏 / Feed / 直播间 / 播放页等
  「按类名定位」的广告位会静默跳过；小视频跳过、设置入口、分享标记不依赖 DexKit，不受影响。
- **宿主被二次修改**（加固、改包、非官方渠道）时类名与字段可能再次变化，需要重新适配。
- 模块只作用于作用域内的包名，勾选其它应用不会生效。

## 模块仓库

模块已收录到 [Xposed 模块仓库](https://modules.lsposed.org/module/io.github.fesht377.biliads/)：
**[modules.lsposed.org/module/io.github.fesht377.biliads](https://modules.lsposed.org/module/io.github.fesht377.biliads/)**
（搜索 `FuckBiliAds` 或包名）。LSPosed 管理器的「模块仓库」直接读这个索引，可在线安装与更新。

索引仓库为 [`Xposed-Modules-Repo/io.github.fesht377.biliads`](https://github.com/Xposed-Modules-Repo/io.github.fesht377.biliads)，
只放 `SUMMARY`、`README.md` 与 release 产物，源码以本仓库为准。
