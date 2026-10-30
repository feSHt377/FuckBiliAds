# HD 端未解决问题记录

面向维护者：这里记录 **哔哩哔哩 HD（`tv.danmaku.bilibilihd`，实测 2.0.1 / `2001100`）**
上两个至今未解决的问题、已经排除过的原因，以及下一步需要的证据。
手机端（`tv.danmaku.bili` 9.16.1 / 9.17.0）功能正常，问题只出现在 HD。

---

## 1. 现象

| # | 现象 | 首次报告 | 现状（1.2.9-pre） |
|---|---|---|---|
| P1 | 我的 → 更多服务 里没有 FuckBiliAds 入口，进不去模块设置 | 1.2.6 | **仍不出现** |
| P2 | 冷启动仍出现商业开屏广告（带跳过/倒计时） | 1.2.9 前提出 | **仍出现** |

两者同时存在，这一点本身就是重要线索：它们分别落在"我的页 UI"和"启动阶段"两条完全不同的链路上，
唯一能同时解释的是 **模块在 HD 进程里根本没有生效**（见 §4 H1）。

---

## 2. 已确认的事实（逆向证据）

以下结论都对着 `dexdump`（`classes*.dex`）核过，不依赖反编译产物的可读名。

### 2.1 混淆形态

- HD 与手机版是同一套 R8：字段被压成单字母。
- HD 的 `HdHomeUserCenterFragment` 保留了 Kotlin `@Metadata`，jadx 会据此把字段**还原成可读名**：

  | 反编译里看到 | 运行时真名 | 类型 |
  |---|---|---|
  | `mAdapter` | `J` | 平铺适配器 |
  | `mSectionListV2` | `K` | `List` |
  | `mAdapterV2` | `L` | 卡片适配器 |
  | `mNewStyleV2` | `N` | 卡片容器 `bz2.a` |
  | `mPageManager` | `R` | 页面路由 |
  | `mHdMineFragment` | `X` | 宿主包装 Fragment |

- 卡片元素 `bz2.a$a` 的字段同样是 `a`（`List<MenuGroup.Item>`）/ `b`（`String title`），
  方法为 `a()` / `b()` / `c(List)` / `d(String)`；`itemList`、`title`、`getTitle()` 都是 jadx 别名。
- 点击回调 `HdHomeUserCenterFragment$h` 持有外部 Fragment 的字段名是 `a`，不是 `this$0`。
- 对照：手机版 `HomeUserCenterFragment` 同一个位置有 106 处改名，但**全部**是
  "collision with root package name"（jadx 没拿到属性名），所以反编译产物一眼就能看出是乱码；
  HD 这 59 处是 "from kotlin metadata"，看起来很像真的——这是本轮踩坑的根源。
- 手机版模块之所以没踩：它的注入点是公共库类 `com.bilibili.lib.homepage.mine.MenuGroup`，
  该类的 `itemList` / `title` 在 dex 里就是真名，两个 APK 完全一致。

### 2.2 我的页结构

```
HdMineFragment
 └─ HdHomeUserCenterFragment
      ├─ onActivityCreated()  → new 页面管理器; Fo(ctx, 缓存列表)     ← 平铺路径
      └─ Wo(AccountMine)      → isStyleV2() && hasDataInStyleV2()
                                   ? Go(ctx, mineInfo)              ← 卡片路径（线上实际走这条）
                                   : Fo(...)
```

- 卡片路径 `Go()` 会：清空卡片容器 → 依次塞入
  `padSectionList` / `recommendSectionList` / **`moreSectionList`（标题 `p0.f242700j6` = `0x7f113910` =「更多服务」）**
  → 补一条「设置」→ 建 `az2.a` 适配器 → `swapAdapter` → `notifyDataSetChanged`。
- 「更多服务」这个字符串全包只有 `Go()` 用，可确认为卡片标题。
- 宿主自己往这张卡片追加「设置」时用的锚点条件是 `uri == "bilibili://main/preference"`，
  所以这个 uri 是判断"哪张是更多服务卡片"的稳定锚点（不依赖中文）。
- 点击链路：`yu2.a.onClick(View)` → `mine.c.a(item)` → `HdHomeUserCenterFragment$h.a` →
  `mPageManager.g(item, listener)`；`g()` 对未注册的 uri **直接 return**，所以只往列表塞条目不接管点击是没用的。

### 2.3 开屏结构

```
MainActivityV2.O7()
  → n8(splash, true)
      → new MainSplashHelper().g(splash, report)      ← dex 0x0000: invoke-static page/x;.a（未内联）
          → x.a(splash)  → FullImageSplash / HalfImageSplash / PortVideoSplash / LandVideoSplash / FullVideoSplash / StorySplash
      → g 返回 null ⇒ n8 直接 F0(null,null,false) + S8()，不会 add "ADSplashFragment"
```

- launcher 是 `tv.danmaku.bili.MainActivityV2`（`aapt2 dump badging` 确认），不是独立开屏 Activity。
- 另有 `HotSplashActivity`（热开屏，独立 Activity）也走 `x.a`，同一闸门可覆盖。
- 缓存路径：`b0.D()` 是**同步读磁盘缓存**拿 `Splash`，所以"只拦网络"对已缓存素材无效。
- HD 一共有四个开屏接口：

  | 端点 | 含义 | 1.2.9 是否拦截 |
  |---|---|---|
  | `/x/v2/splash/list` | 商业开屏列表 | ✅ |
  | `/x/v2/splash/show` | 展示上报 | ✅ |
  | `/x/v2/splash/brand/list` | 品牌开屏（商业品牌图） | ✅（1.2.9 新增） |
  | `/x/v2/splash/event/list2` | 运营活动开屏（拜年纪等） | ❌ 有意保留 |

---

## 3. 已尝试的修复（按版本）

| 版本 | 改动 | 结果 | 排除了什么 |
|---|---|---|---|
| 1.2.6 | 新增 HD 适配：`HdAdSniper`、HD 开屏页工厂拦截、入口写 `mSectionListV2`（按名字反射） | 入口不显示 | — |
| 1.2.7 | 补注入 `mNewStyleV2` 分组（仍按名字）、按 `getTitle()` 挑卡片 | 仍不显示 | 说明"只差分组列表"不成立 |
| 1.2.8 | HD 判定从 `processName()` 改为按注入包名；拦截 `$h.a` 直接开设置 | 仍不显示 | 排除"HD 分支因进程名取不到而未启用" |
| 1.2.9 | 入口全部改结构匹配（字段类型 / 元素类型 / 唯一 String 字段），菜单构建后与绑定前各补一次注入，点击改拦行 `onClick(View)`；开屏补品牌接口 + `MainSplashHelper` 决策层闸门 | **仍不显示，广告仍在** | 排除"字段名错"与"缺品牌链路" |

1.2.9 的结构匹配逻辑有单测覆盖（`HdMineServiceEntryTest`，用复制混淆形状的假模型），
在 JVM 里是能正确解析"卡片 → 条目 → 标题"的；也就是说**如果 Hook 真的在 HD 里跑起来，这条路径应当成立**。

---

## 4. 仍未排除的假设

按可能性排序，**H1 优先级最高**，因为它能一次解释 P1 + P2。

### H1. 模块没有被注入到 HD 进程

可能原因：LSPosed 里没给「哔哩哔哩HD」勾选作用域、作用域勾了但没重启宿主、或多用户/工作资料下未生效。
判定：日志里**完全没有** `module load start: pkg=tv.danmaku.bilibilihd`。

> 仓库里 `META-INF/xposed/scope.list` 已包含 `tv.danmaku.bilibilihd`（打包产物也确认过），
> 但这只决定"可被选中"，实际生效仍取决于用户在 LSPosed 里勾选。

### H2. 注入成功，但 Hook 安装阶段抛错

判定：日志里有 `[HD] 我的页设置入口安装失败` / `[SPLASH][HD] … 安装失败`。

### H3. 注入了、Hook 也装上了，但注入点没被执行

判定：有 `[HD] 我的页设置入口 Hook 已安装，菜单构建方法=N`，但没有
`[HD] 菜单适配器已挂载` 或 `[HD] 设置项写入卡片`。
若 N ≠ 2，说明按签名找菜单构建方法的假设不成立。

### H4. 开屏走的是第四条未知链路

可能性较低：`x.a` 与 `MainSplashHelper.g` 两层闸门已在同一路径上，
除非存在完全绕过这两者的渲染实现（例如另一处直接把 `BaseSplash` 子类 add 到容器）。

---

## 5. 复现与取证步骤

HD 正式包 `debuggable=false`，`run-as` 不可用，优先级如下。

### 5.1 先确认"有没有注入"（最关键的一步）

```bash
adb logcat -c
# 冷启动一次 HD，然后：
adb logcat -d -s FuckBiliAds | head -40
```

期望第一行：

```
module load start: pkg=tv.danmaku.bilibilihd process=tv.danmaku.bilibilihd main=true api=102 ...
```

- **一行都没有** → H1，先解决注入（LSPosed → 模块 → 作用域勾选 HD → 强制停止 HD 再启动）。
- 有这一行、后面紧跟 `[SKIP] non-main process` → 说明启动进程不是主进程，需要看是哪个进程在跑 UI。

### 5.2 我的页入口

```bash
adb logcat -d -s FuckBiliAds | grep "\[HD\]"
```

判定表：

| 日志 | 结论 |
|---|---|
| 只有 `Hook 已安装，菜单构建方法=N` | H3：注入点没执行，或 N≠2 说明签名匹配错了 |
| `菜单适配器已挂载: …` 但没有 `设置项写入卡片` | 适配器找到了，但没解析出卡片/条目 → 需要 dump 实际结构 |
| `设置项写入卡片 i，卡片=0:推荐服务(8) \| 1:更多服务(6)` | 注入成功但界面没刷新 → 换刷新方式或换卡片 |
| 完全没有 `[HD]` 行 | H1/H2 |

### 5.3 开屏

```bash
adb logcat -d -s FuckBiliAds | grep -i splash
```

关键行：`[SPLASH] 请求拦截已安装` / `[SPLASH][HD] 决策层拦截已安装` /
`[SPLASH][HD] 缓存广告页创建拦截已安装` / `[SPLASH][HD] 开屏片段创建已拦截（决策层）`。

另外模块内置了一个探测：任何类名含 `splash` 的 Fragment / Activity 首次出现会打
`[SPLASH] 页面 Fragment: <类名>`（最多 20 条）。这条日志能直接告诉我们"广告到底是哪个实现的"。

---

## 6. 下一步候选方案

1. **先拿日志**。没有 §5 的三段输出，后续任何改动都是猜测——本轮的 4 个版本已经证明了代价。
2. 若确认 H1：属于使用环境问题，不改代码，补一份"如何给 HD 勾选作用域"的说明即可。
3. 若确认 H3：把 `HdHomeUserCenterFragment` 的字段类型 + 运行时值一次性 dump 到日志
   （仅 HD、仅一次），据此重新确定注入点；同时放宽探测，把看到的 Fragment/Adapter 类名都打出来。
4. 若确认 H4：放宽 `[SPLASH] 页面 Fragment` 探测到"所有 Fragment 前 20 个"，定位真实实现后再对症下药。

---

## 7. 相关文件

| 文件 | 作用 |
|---|---|
| `app/src/main/java/com/fuck/bili/ads/ui/HdMineServiceEntry.java` | HD 我的页入口注入（结构匹配） |
| `app/src/main/java/com/fuck/bili/ads/ui/MineServiceEntry.java` | 手机版入口；HD 时转给上面那个 |
| `app/src/main/java/com/fuck/bili/ads/BiliSplashSniper.java` | 开屏请求拦截、页面工厂闸门、决策层闸门 |
| `app/src/main/java/com/fuck/bili/ads/HdAdSniper.java` | HD 播放页关联推广拦截 |
| `app/src/test/java/com/fuck/bili/ads/ui/HdMineServiceEntryTest.java` | 结构解析单测（复制混淆形状的假模型） |
| `docs/migration/USER-TEST.md` | 手机端回归清单 |
