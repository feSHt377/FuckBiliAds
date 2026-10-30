# 构建与发布

面向维护者。日常使用只需要看 [USAGE.md](USAGE.md)。

## 环境

- JDK 21（AGP 9.0.0 + Gradle 9.1.0；JDK 8 会直接报 `Gradle requires JVM 17 or later`）
- Android SDK（compileSdk 36）

## 本地构建

```bash
JAVA_HOME=/path/to/jdk21 ./gradlew :app:testDebugUnitTest :app:assembleRelease --console=plain
```

产物：

| 路径 | 说明 |
|---|---|
| `app/build/outputs/apk/release/app-release-unsigned.apk` | 未签名 release |
| `app/build/outputs/apk/debug/app-debug.apk` | debug |

签名通过环境变量注入，未提供时产物未签名：

```bash
export KEYSTORE_FILE=/path/to/release.jks
export KEYSTORE_PASSWORD=...
export KEY_ALIAS=fuckbiliads
export KEY_PASSWORD=...
```

> **发布产物一律由 GitHub Actions 构建**，本地包只用于验证。
> 需要给用户测时命名为 `FuckBiliAds-<版本>-test.apk`，并注明是 debug 钥匙临时签名。

## 发布

推送 `v*` 标签即触发 [`.github/workflows/release.yml`](../.github/workflows/release.yml)：
构建 → 签名 → 校验签名 → 上传 APK 并创建 Release → 同步到模块仓库。

```bash
git tag -a v1.3.1 -F work/notes-1.3.1.md   # 注释即 release 正文
git push origin v1.3.1
gh run watch <run-id> --exit-status
```

- tag 带 `-pre` 后缀（如 `v1.3.1-pre`）时为 pre-release，且**不同步**到模块仓库；
- versionCode 由语义版本算出：`major*10000 + minor*100 + patch`（`v1.3.1` → `10301`），
  保证 CI 产物一定比历史构建新，本地默认值用同一套规则；
- release 正文 = tag 注释 + `**Full Changelog**` + `versionCode: N`。
  末尾那行 `versionCode` 是应用内更新检查区分**同版本号** pre / 正式版的依据，不要手动删；
- 不要靠删 tag 重发版本：GitHub 会把关联 release 转成 Draft，重推同一 tag 时工作流虽会
  `draft: false` 救回来，但期间发布页会闪一下 Draft。

### 发布说明口径

只讲结果，面向用户：新增了什么、修复了什么、调整了什么。
不要写调查过程、排除了哪些猜测、改了哪个类/方法、加了多少行日志。

```text
新增：设置页可分别关闭开屏、主页、播放页、直播、关注页的广告拦截
修复：HD 端「我的 → 更多服务」缺少模块入口、点击无法打开设置
修复：冷启动时开屏广告请求未被拦截
```

### Actions Secrets

| Secret | 内容 |
|---|---|
| `KEYSTORE_BASE64` | 签名密钥库的 Base64 |
| `KEYSTORE_PASSWORD` | 密钥库口令 |
| `KEY_ALIAS` | `fuckbiliads` |
| `KEY_PASSWORD` | 密钥口令 |
| `MODULE_REPO_TOKEN` | 同步到 `Xposed-Modules-Repo/io.github.fesht377.biliads` 的 PAT；未配置时该步骤自动跳过 |

签名密钥不入库，本机备份放在仓库之外。更换密钥会导致已安装版本无法覆盖升级：
正式版证书 SHA-256 固定为 `93298523…af21981`，发布后可用
`apksigner verify --print-certs` 核对。

## 模块仓库

索引仓库 [`Xposed-Modules-Repo/io.github.fesht377.biliads`](https://github.com/Xposed-Modules-Repo/io.github.fesht377.biliads)
只放 `SUMMARY`、`README.md` 与 release 产物；模板在 [`module-repo/`](module-repo/)。

- release tag 必须是 `VersionCode-VersionName`（例如 `10301-1.3.1`），且必须带 APK，否则不进索引；
- 仓库描述 / 名称只能在网页端改（官方限制：无法通过 API 更新）。

## 测试

| 层级 | 何时做 |
|---|---|
| JVM 单测 | **必做**，提交门槛：`./gradlew :app:testDebugUnitTest` |
| 真机冒烟 | 按需：用户点名或 `adb devices` 有设备时才做 |
| 用户回归 | 功能类改动先交付，用户确认后再提交、再发版 |

有设备时的冒烟口径：冷启动一次看 `[BOOT] hooks done` 与 `[HOOK REGISTRY]`，确认无崩溃、非主进程跳过。
句柄数是回归信号：Release 正常 **99 个句柄 / 92 个唯一成员**，明显掉数说明某个类查找失效。

## 目录结构

```text
app/src/main/java/com/fuck/bili/ads/
├── MainHook.java            # 入口（XposedModule#onPackageLoaded），按功能分派 Hook
├── core/                    # modern API 基础设施
│   ├── Reflect.java         # 反射匹配 / 装箱 / 父类接口查找
│   ├── NativeHooks.java     # Hook 注册与异常模式
│   ├── HookRegistry.java    # 句柄对账与落盘
│   └── Host.java            # Application / processName / 包名
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
│   ├── MineEntryCore.java   # 两端共用的菜单入口逻辑
│   ├── MineServiceEntry.java# 手机版入口定位
│   ├── HdMineServiceEntry.java # HD 入口定位
│   ├── UpdateChecker.java   # GitHub Release 查询 / 版本比较 / 更新弹窗
│   └── StoryShareMark.java  # 分享面板「标记广告」按钮
├── settings/                # 关注页过滤设置
└── *Sniper.java             # 各类广告位
```
