# AGENTS.md

面向在本仓库工作的 AI/人类协作者：**这只是一个"怎么做"的约定文件**，功能说明看 `README.md`，
迁移验收看 `MIGRATION_LIBXPOSED_102.md`，小视频原理看 `STORY_AD_SKIP.md`。

---

## 1. 项目速览

| 项 | 值 |
|---|---|
| 是什么 | 哔哩哔哩 Xposed 去广告模块（Java，单模块 `app`），含小视频广告自动跳过/剔除 |
| 宿主 | `tv.danmaku.bili`（实测 9.16.1 / `9161100`），别名 `tv.danmaku.bilj`、`com.bilibili.app.in` |
| 模块包名 | `io.github.fesht377.biliads`（Java 包名仍是 `com.fuck.bili.ads`，不要跟着改） |
| Xposed API | `io.github.libxposed:api:102.0.0`，入口 `com.fuck.bili.ads.MainHook extends XposedModule` |
| 模块清单 | `app/src/main/resources/META-INF/xposed/{java_init.list,module.prop,scope.list}` |
| 运行时日志 tag | `FuckBiliAds`（`adb logcat -s FuckBiliAds`） |

新增宿主包时必须同步改 `scope.list`，否则 LSPosed 不会把模块注入该包。

---

## 2. 环境与构建

- 本机 Gradle 必须用 **JDK 21** 跑（AGP 9.0.0 + Gradle 9.1.0）：`JAVA_HOME=E:\AndroidStudio\jbr`。
  直接用默认 JDK 8 会报 `Gradle requires JVM 17 or later`。
- 日常校验（编译 + 单测，**这是提交前的最低门槛**）：

```bash
JAVA_HOME=E:\AndroidStudio\jbr ./gradlew :app:testDebugUnitTest :app:assembleRelease --console=plain
```

- 产物位置：`app/build/outputs/apk/debug/app-debug.apk`、`app/build/outputs/apk/release/app-release-unsigned.apk`。
- 签名只走环境变量（`KEYSTORE_FILE` / `KEYSTORE_PASSWORD` / `KEY_ALIAS` / `KEY_PASSWORD`），
  未提供时产物是未签名包。**密钥库、`work/`、`*.log` 都不入库**（见 `.gitignore`）。
- 临时脚本、备份 APK、验证产物一律放 `work/`（已忽略），不要污染源码树。

### 硬规则：不要自己出发布包

用户明确要求 **发布产物一律由 GitHub Actions 构建**。本地最多 `assembleDebug`/`assembleRelease`
用于验证，不要把本地签名的包当成正式版发出去（签名不一致会导致装不上）。
需要给用户测时，产物命名成 `FuckBiliAds-<版本>-test.apk`，并在交付说明里写清"debug 钥匙临时签名"。

---

## 3. 代码约定

- Java 11 语言级别（`sourceCompatibility/targetCompatibility = 11`），4 空格缩进，不引入 Kotlin。
- **注释与 Javadoc 用英文，面向用户的文案用中文**（Toast、设置项标题、日志里给人看的部分）。
- 新 Hook 必须经 `core/NativeHooks` 注册，不要直接调 `api.hook(...)`：

```java
NativeHooks.method(Foo.class, "bar", ContinuationImpl.class)   // 按签名反射查找
NativeHooks.hook("Owner", executable, chain -> { ... })        // 统一注册（含句柄登记）
NativeHooks.hookAllMethods("Owner", Foo.class, "bar", body)    // 重载全挂
```

  原因：`HookRegistry` 是启动对账（`[HOOK REGISTRY]` → 宿主 `cache/fuckbiliads/current-hooks.txt`）的唯一来源，
  绕过它就等于让回归证据失真。默认异常模式固定为 `ExceptionMode.PROTECTIVE`。
- **禁止任何 legacy Xposed API**：`de.robv.android.xposed.*`、`XposedHelpers`、`XposedBridge`、
  `AndroidAppHelper`、`assets/xposed_init`、manifest 里的 `xposedmodule` meta-data 都不许再出现。
- 宿主类名/方法名是混淆的（`L`、`X`、`k0`…），**只允许按结构匹配**：签名、字段存在性、父类/接口、DexKit 查询。
  不要在代码里硬编码混淆名，也不要把 `adType == -1` 这类不稳定字段当判据（唯一可靠的是 `StoryDetail.adInfo.feedIsAd`）。
- 改宿主对象要极度克制：只改返回值，不要就地改宿主正在渲染的实例（1.1.2 的闪退就是这么来的）。
  尤其别动被 RecyclerView 当 `viewType` 用的复合值（如 `cm_v2:3`）。
- 用户可见开关：`FbaPrefs` 加 `KEY_*` 常量 + getter（默认值写在 getter 里），
  `FbaSettings` 用 `switchRow(...)` 加一行；**新增/噪声大的功能默认关闭**。
- Toast 文案集中在使用处定义为常量，格式保持 `已剔除 %d 条广告！FUCK BILI ADS！` 这种风格，
  附加信息用括号后缀（如 `（后%d条）`），不要另起一句。
- 日志统一走 `FbaLog`，用方括号前缀分流：`[BOOT]`、`[HOOK INSTALL]`、`[HOOK REGISTRY]`、`[FILTER]`、`[UPDATE]`。

### 3.1 跨端维护原则（手机版 / HD）

**同一个面板、同一段功能只能有一份实现；只有"入口长在哪、怎么挂上去"按端分别维护。**

| 层次 | 归属 | 例子 |
|---|---|---|
| 共享（唯一实现） | 与端无关的逻辑 | `FbaSettings`（设置面板）、`FbaPrefs`（开关）、`MineEntryCore`（菜单项构造、适配器取列表、`uri` 匹配、点击挂载）、`FbaLog` / `FbaRuntime` |
| 端特定（可以分开） | 注入点定位、宿主结构差异 | `MineServiceEntry`（手机：`MenuGroup` 卡片）、`HdMineServiceEntry`（HD：左侧平铺列表 + 「更多服务」卡片） |

- 新增端 = 新增一个"入口策略"并注册到 `MineServiceEntry.hook` 的分派里（现在按 `Host.isHd()` 分流），
  **不是**把已有入口类复制一份改类名。
- 判断标准：两处代码有 80% 相同就必须抽到共享层；复制粘贴出来的第二份会各自腐烂——
  HD 入口就是这么来的：整套逻辑复制后，`view instanceof RecyclerView` 这条在模块与宿主
  类身份不同时永远为 false，bug 在复制品里藏了三个版本才被发现。
- 跨类加载器的类型判断**一律按类名/结构**做，禁止 `instanceof` 宿主侧的 androidx/宿主类
  （`View`、`ViewGroup` 这类 boot classpath 的类除外）；现成实现见 `MineEntryCore.isRecycler`。
- 端差异要能被一句话说清（"HD 没有竖屏 Story"），说不清的差异通常是没想清楚，不是真差异。

---

## 4. 测试约定

| 层级 | 什么时候做 |
|---|---|
| JVM 单测 | **必做**，提交门槛：`app/src/test/java/...`，反射匹配、广告标识判定、Toast 文案等纯逻辑。当前 15 项，改动相关逻辑要补 |
| 真机冒烟 | **按需**：仅当用户明确要求，或 `adb devices` 里有设备在线时才装包验证；没有设备就跳过（本机 `adb` 常不在 PATH，用 SDK 里的 `platform-tools/adb`） |
| 用户回归 | 由用户自己测：功能类改动先交付，等用户说"测完了"再提交/发布 |

- **真机验证不是必做项**。没设备就只报"编译 + 单测通过，未真机验证"，不要写成已验证过，
  也不要为了凑一条"已测"去猜结果。
- 有设备时的冒烟口径：装包冷启动一次，看 `[BOOT] hooks done` / `[HOOK REGISTRY]`，确认无崩溃、非主进程跳过；
  句柄数是回归信号——Release 正常 **99 个句柄 / 92 个唯一成员**，Debug 会多出 PacketSniffer 的 `fromJson` 重载，
  数字明显掉了说明某个类的查找失效了。
- 装包注意签名：本地 debug 包与 CI 正式包不能互相覆盖，换装前先卸载；覆盖前把现有包备份到 `work/`
  （例：`work/biliads-1.1.2-release-backup.apk`）。

---

## 5. 提交风格

- 格式：**Conventional 前缀 + 中文摘要**，冒号后一个空格：

```text
feat: 迁移到 libxposed API 102，新增「剔除提示附带位置」

- 入口改为 XposedModule#onPackageLoaded，全部 Hook 改写为 hook(...).intercept(...)
- 修复 BiliGsonLobotomy 改写 cm_v2:3 导致 type:unknown is not exist 崩溃
- 版本号 1.2.0 / versionCode 10200

Release 实测：99 个 Hook 句柄 / 92 个唯一成员，无崩溃；单测 15 项全绿。
```

- 前缀用法：`feat:` 功能、`fix:` 缺陷、`ci:` 流水线、`docs:` 文档、`refactor:` 重构（不改行为）、`chore:` 杂务。
- 摘要只写"做了什么 + 为什么"，**不加句号**，一般 ≤ 50 字；正文用 `-` 列要点，行宽约 70–80 字（中文按 2 列算）。
- 正文里**写实测数据**（单测数量；有设备时再加句柄数、冷启动结果），这是这个项目形成的历史习惯，也是回归依据。
- 一次提交只做一件事；不要混入无关格式化，不要在提交里塞构建产物。
- 早期历史里出现过 `1.1.2：...`、`文档：...` 这类写法，那是旧习惯，**新提交统一用上面的前缀式**。
- 不 `push --force` 到 `main`；不改写已发布的 tag（见第 7 节的 Draft 坑）。

---

## 6. 分支与版本

- `main` 永远可发布；功能/迁移在 `feat/<主题>` 上做，完成后 **fast-forward** 合并进 `main`。
- 版本号规则：`versionName = major.minor.patch`，`versionCode = major*10000 + minor*100 + patch`（`1.2.0` → `10200`）。
  CI 也从 tag 推同一个值，本地默认值（`app/build.gradle` 里的 `10200`/`1.2.0`）要与之一致。
- 跨大版本（API 变更、迁移、不兼容调整）**要跳 minor**，让用户一眼看出区别（例：API 102 迁移 → `1.2.0` 而非 `1.1.3`）。
- tag：正式版 `v1.2.0`；测试版 `v1.2.1-pre`（`-pre` 后缀决定 CI 走 pre-release 分支）。
  tag **必须是 annotated**（`git tag -a v1.2.0 -F notes.md`），注释就是 release 正文。

---

## 7. 部署 / 发布（唯一路径：CI）

推送 `v*` tag 即触发 `.github/workflows/release.yml`：构建 → 正式密钥签名 → `apksigner` 校验 →
上传 artifact → 创建 Release → 同步到 Xposed 模块仓库。

```bash
git checkout main && git pull
git tag -a v1.2.1 -F work/notes.md      # 注释即 release 正文，写人话
git push origin v1.2.1
gh run watch <run-id> --exit-status      # 必须等到 success
```

发布正文由流水线拼成，**不要手改格式**：

```text
<tag 注释>
**Full Changelog**: …
---
versionCode: 10200
```

最后那行是应用内更新检查区分"同版本号 pre / 正式版"的依据（`UpdateChecker.parseVersionCode`），删了就只能退回语义版本比较。

### 发布相关的坑（都踩过）

- **删 tag 会让关联 release 变成 Draft**，重推同一个 tag 不会自动恢复。工作流里显式 `draft: false` 兜住，
  但构建窗口期发布页仍会闪 Draft。**不要靠删 tag 重发版本**，要重发就直接发新版本号。
- `softprops/action-gh-release` 的 `append_body` 只在"更新已有 release"时生效，新建时被 `generate_release_notes` 覆盖，
  所以正文由后面的 `gh api PATCH` 步骤重写（`checkout` 对触发 ref 用 `--no-tags`，tag 注释得从 API 读）。
- Xposed 模块仓库的 release tag 是 `{versionCode}-{versionName}`（如 `10200-1.2.0`），
  需要 `MODULE_REPO_TOKEN`；未配置时该步骤自动跳过（不要当成失败）。
- 发布后自检：

```bash
gh release view v1.2.0 --json isDraft,isPrerelease,assets,body   # 非 draft / 非 pre / 有 APK / 有 versionCode
apksigner verify --print-certs FuckBiliAds-1.2.0.apk             # CN=FuckBiliAds，SHA-256 与上一个正式版一致
aapt2 dump badging FuckBiliAds-1.2.0.apk | head -1               # versionCode / versionName 对不对
```

  签名证书必须与历史正式版一致（`93298523…af21981`），否则老用户无法覆盖升级。

---

## 8. 文档习惯

- `README.md` **只留当前版本的要点**，不放逐版本变更史——历史进 GitHub Releases
  （用户明确要求过："README 不要再写那么多版本历史"）。
- 变更说明写进 tag 注释（= release 正文），不要在两处各写一遍。
- **发布说明用官方口径，只讲结果**：写"新增了什么、修复了什么、调整了什么"，面向用户；
  不要写调查过程、排除了哪些猜测、改了哪个类/方法、加了多少行日志、踩了哪些坑。
  每条一句话说清用户能感知的变化，必要时补一句影响范围（机型 / 版本 / 需要重启）。
- 发布说明模板：

```text
新增：设置页可分别关闭开屏、主页、播放页、直播、关注页的广告拦截
修复：HD 端「我的 → 更多服务」缺少模块入口、点击无法打开设置
修复：HD 端深色模式下页面被强制显示为浅色
修复：冷启动时开屏广告请求未被拦截
```

  预发布（`-pre`）同样按这个口径写，只在标题标注测试版，不把不确定的判断写进去。
- 迁移/验收这类需要证据的工作，结论写进 `MIGRATION_LIBXPOSED_102.md`，
  原始证据（`current-hooks.txt`、`g1-static.txt`、`smoke-log.txt`、`callback-matrix.md`）落 `docs/migration/`。
- 交付前给用户的测试清单放 `docs/migration/USER-TEST.md` 这类文件里，别只写在聊天里。

---

## 9. 协作习惯

- 说中文；结论先行，再给证据（命令输出 / 日志片段 / 句柄数）。
- 功能改动流程：**改代码 → 编译 + 单测 → 用户测试 → 用户确认后才提交 → 用户要求才发布**。
  其中"装真机"是可选的：只在用户点名，或确实有设备连着时才做（见第 4 节）；默认不主动提交、不打 tag。
- 破坏性或不可逆操作（删 tag、卸载旧包、改签名、`push --force`）先说明后果再做。
- 汇报里贴真实路径与数字，不写"应该没问题"；没验证的部分明确写"未验证"。

---

## 10. 禁止清单

- ❌ 引入 legacy Xposed API 或任何兼容层
- ❌ 直接 `api.hook(...)` 绕过 `NativeHooks` / `HookRegistry`
- ❌ 硬编码宿主混淆类名，或用 `adType` / `natureAd` 当广告判据
- ❌ 就地修改宿主正在渲染的对象、改写 `viewType` 之类的复合值
- ❌ 把 APK、密钥库、日志、`work/` 提交进仓库
- ❌ 本地构建并分发正式包（正式包只能来自 CI）
- ❌ 删 tag 重发版本号
- ❌ 在 README 里堆版本历史
- ❌ 未经用户确认就提交/发布
