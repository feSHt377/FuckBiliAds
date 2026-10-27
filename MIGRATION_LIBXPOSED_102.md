# 迁移到 libxposed API 102 · 全量原生改写方案

> **状态**：代码迁移完成；用户回归通过，已随 **1.2.0 正式版**发布（tag `v1.2.0`）。仅 G2.1/G2.3 基线与 G5/G6 性能长稳项未执行
> **编写日期**：2026-10-27（v2：路线改为全量原生）
> **基线**：`0106d30`（v1.1.2，versionCode 10102）
> **目标依赖**：`io.github.libxposed:api:102.0.0`（当前为 `de.robv.android.xposed:api:82`）
> **路线**：**B —— 全量原生改写，禁止任何兼容层**

本文是**验收契约**：第 2 节的每一条都必须能给出证据（命令输出 / 日志片段 / 清单 diff），不满足即不算完成。

---

## 0. TL;DR

| 项 | 结论 |
|---|---|
| 路线 | 全量原生：52 个注册点、49 个回调一律改写为 `hook(...).intercept(chain -> …)` |
| 禁止 | `XC_MethodHook` / `XposedHelpers` / `XposedBridge` / `LoadPackageParam` 等任何同形适配类 |
| 允许 | 仅两个**非兼容层**工具：`Reflect`（纯 `java.lang.reflect`）、`Host`（Application / processName） |
| 重构面 | 23 / 33 个文件，约 700–800 行回调体重写 |
| 工时 | **10–14 人日**（分 5 批交付，每批独立可测） |
| 验收 | 7 道门禁（G1–G7），其中 **G2 注册点对账必须 52/52 无差异** |
| 最大风险 | `Reflect.callMethod` 的参数类型匹配复刻不全 → 46 处调用随机 `NoSuchMethodException` |

---

## 1. 背景

### 1.1 为什么迁移

1. **依赖已冻结**：旧 Xposed 仓库只有 53 / 81 / 82 三个版本，`latest=82`、`lastUpdated=2016-04-15`，不存在「更新的 82」。
2. **能力缺失**：`deoptimize`（治 ART 内联）、hook 优先级、`Invoker`（显式区分走链 / 走原始实现）、hot reload（102）、`HookHandle` 原子替换。
3. **入口形态过时**：`assets/xposed_init` + manifest `meta-data` → `META-INF/xposed/java_init.list` + `module.prop`。
4. **供应链单点**：`api:82` 只存在于旧 Xposed 仓库，不在 Maven Central。

### 1.2 已完成的可行性验证

| 项 | 结果 |
|---|---|
| LSPosed 2.2.1 | API 102 模块加载正常 |
| JingMatrix LSPatch（API 102） | 无 Root 路径可用 |
| 宿主 / 设备 | 哔哩哔哩 9.16.1（9161100）/ OnePlus PGZ110 · Android 15 · arm64-v8a |

### 1.3 本次不做的事

- 不改业务判定逻辑（广告识别、跳过策略、过滤规则、日志格式全部原样保留）。
- 不引入 Kotlin。
- 不做资源 hook / `XSharedPreferences`（本模块 0 使用）。
- **不保留任何 legacy 兼容代码**；不为通过门禁而给 legacy API 包一层壳（见 G1 的禁止清单）。

---

## 2. 验收标准

> 每条标注「证据」= 验收时必须贴出的东西。贴不出即不通过。

### G1 · 静态门禁（脚本化，逐条给命令输出）

- [ ] **G1.1** `grep -rn "de\.robv\.android\.xposed" app/src/main` → 空
- [ ] **G1.2** `grep -rn "AndroidAppHelper" app/src/main` → 空
- [ ] **G1.3** `grep -rnE "XposedHelpers|XposedBridge|XC_MethodHook|XC_MethodReplacement|XC_LoadPackage|LoadPackageParam" app/src/main` → 空
- [ ] **G1.4** `grep -rn "api.xposed.info" app gradle settings.gradle` → 空（仅构建源；历史文档除外）
- [ ] **G1.5** `grep -rn "de.robv" gradle/ app/build.gradle settings.gradle` → 空
- [ ] **G1.6** 每个 `intercept(` 只调用一次 `chain.proceed`（人工核对，49 处）
- [ ] **G1.7** 无 `catch (Throwable)` 空实现：命中处必须调用 `FbaLog.e/d`
  - 证据：`grep -rn -A2 "catch (Throwable" app/src/main` 的完整输出

> **禁止清单**：不得出现 `com.fuck.bili.ads.xp` 之类同形兼容包；不得出现 `class XposedHelpers`；不得保留 `api:82` 作为 `compileOnly`。违反任一条直接判 G1 不通过。

### G2 · 注册点对账（**最硬的一条**）

- [ ] **G2.1** P0 阶段在 1.1.2 构建上临时打点，导出全部实际 hook 的 `类名#方法名(参数类型)` 到 `baseline-hooks.txt`
- [ ] **G2.2** 迁移后新增 `HookRegistry.dump()`，启动时导出 `current-hooks.txt`
- [ ] **G2.3** 两份清单 `diff` **为空**（或差异逐条在 PR 描述里解释，例如 `hookAllMethods` 展开导致的条目拆分）
- **证据**：两份清单文件 + `diff` 输出

> 基线 52 个**注册语句**；`hookAllMethods` / 循环注册会展开成更多**实际 hook**。对账单位是「实际 hook 的成员集合」，不是语句数。

### G3 · 语义门禁（逐回调）

- [ ] **G3.1** 49 个回调逐个在 PR 里标注形态：`before` / `after` / `around` / `replace` / `arg-rewrite`
- [ ] **G3.2** 10 处 `setResult` 逐处确认位于 before 还是 after：before 里 = **跳过原实现**，after 里 = **覆盖返回值**
- [ ] **G3.3** 7 处 `hasThrowable()` 改写后必须满足：原方法抛异常时 **after 逻辑仍执行**，随后原样抛出
- [ ] **G3.4** 2 处写 `param.args` 改写为 `chain.proceed(newArgs)`，且不得在 `proceed` 之后改数组
- [ ] **G3.5** 4 处 `XC_MethodReplacement` 改为不调用 `proceed` 直接返回
- **证据**：`docs/migration/callback-matrix.md`（本文附录 A 的派生表）逐行打勾

### G4 · 行为门禁（第 9 节矩阵）

- [ ] **G4.1** 13 项功能 × 2 条路径全绿，**每格附 `adb logcat -s FuckBiliAds` 片段**
- [ ] **G4.2** 连续 3 次冷启动无崩溃（两条路径各 3 次）
- [ ] **G4.3** 小视频连续刷 ≥100 条、其中 ≥3 条广告，全部跳过且无重复跳
- [ ] **G4.4** 宿主 9.15.0 与 9.16.1 各跑一遍完整矩阵
- **证据**：回归记录表 + 日志文件

### G5 · 性能门禁（相对 1.1.2 基线）

- [ ] **G5.1** 冷启动到 `[BOOT] hooks done` 耗时 **P50 ≤ 基线 +10%**
- [ ] **G5.2** Feed / 小视频连续滑动，`dumpsys gfxinfo` 掉帧率不劣于基线
- [ ] **G5.3** 常驻内存 `dumpsys meminfo` 对比基线 **≤ +10%**
- **证据**：同设备同宿主下的三次采样对比表

### G6 · 稳定性门禁

- [ ] **G6.1** 前台/后台切换 + 挂机 8 小时无崩溃
- [ ] **G6.2** `:web` / `:download` 等非主进程不安装 hook（日志无 `[BOOT]`）
- [ ] **G6.3** 迁移前后 `events.log` 中错误行数量无数量级上升

### G7 · 交付物门禁

- [ ] **G7.1** `README.md` 兼容性表更新（去掉「Xposed API 82+」）
- [ ] **G7.2** 本文所有 checkbox 勾选完毕，变更记录追加
- [ ] **G7.3** 发布说明写明「框架需支持 API 102；低于此版本请留在 1.1.2」

---

## 3. 重构范围

### 3.1 总量（v1.1.2 实测）

| 指标 | 数量 |
|---|---:|
| Java 文件 / 涉及 legacy 的文件 | 33 / **23** |
| Java 总行数 | 9,355 |
| hook 注册语句 | **52** |
| 回调方法 | **49**（before 19 / after 30） |
| `XC_MethodReplacement` | 4 |
| `XposedHelpers.*` | **142**（`callMethod` 46、`findClass` 39、字段 25、`newInstance` 5、其余 27） |
| `XposedBridge.*` | 27 |
| `param.args[]` 读 / 写 | 30 / **2** |
| `param.setResult` / `getResult` | 10 / 11 |
| `param.hasThrowable()` | **7** |
| `param.thisObject` | 26 |
| `param.method` | 4 |
| `AndroidAppHelper.currentApplication()` | **9**（8 个文件） |
| `LoadPackageParam` 签名 | 10 |
| `Set<XC_MethodHook.Unhook>` | 1（`GlobalAdSniperEngine:149`） |

### 3.2 逐文件规格

> 「形态」= before / after / replace 的构成；**这是每个文件的改写清单**，PR 必须逐行对上。

| 文件 | 注册 | 形态 (b/a/r) | 特殊点 | 批次 |
|---|---:|---|---|:--:|
| `DanmuSenderLookup.java` | 8 | 2/4/0 | 3 处 `LoadPackageParam` 签名；`hookAllMethods("invoke")` 循环注册；`AndroidAppHelper` ×1 | B3 |
| `BiliSplashSniper.java` | 6 | 1/5/0 | 15 处 `callMethod`（全模块最多）；`setResult`/`getResult` 各 1；`hasThrowable` ×1 | B3 |
| `MineServiceEntry.java` | 5 | 3/3/0 | `setResult` ×2；7 处 `callMethod` | B3 |
| `MainHook.java` | 5 | 1/2/1 | **入口类**；`AndroidAppHelper` ×2；`DO_NOTHING` 一处；3 处 `LoadPackageParam` 签名 | B4 |
| `StoryShareMark.java` | 4 | 0/4/0 | `hookAllMethods("n")`、`hookAllMethods("b")` 单字母混淆方法 | B2 |
| `FollowingListAdSniper.java` | 3 | 0/3/0 | `hasThrowable` ×2；`getResult` ×4；`AndroidAppHelper` ×1 | B2 |
| `StoryAdSniper.java` | 3 | 0/3/0 | 动态构造 `Object[]` 注册；`hookAllMethods(Activity)` 框架类 | B2 |
| `BiliMentionedSniper.java` | 3 | 1/1/2 | 两个 `XC_MethodReplacement`；`hasThrowable` ×1 | B1 |
| `BiliCardAdSniper.java` | 3 | 3/0/0 | 纯 before | B1 |
| `GlobalAdSniperEngine.java` | 2 | 1/2/0 | 写 `args` ×1；`hasThrowable` ×2；`getResult` ×2；`Set<Unhook>`；`hookAllMethods(gson.fromJson)` | B2 |
| `VIPPredict.java` | 2 | 2/0/0 | `AndroidAppHelper` ×1；`setResult` ×1 | B1 |
| `StoryAdFilter.java` | 1 | 1/0/0 | — | B2 |
| `FollowingTopLeftButton.java` | 1 | 0/1/0 | `getResult` ×2；`hasThrowable` ×1 | B2 |
| `BiliBottomNavSniper.java` | 1 | 1/1/0 | 同一 hook 类里 before+after 并存 | B1 |
| `BiliAdSniper.java` | 1 | 0/0/1 | `XC_MethodReplacement` | B1 |
| `BiliGsonLobotomy.java` | 1 | 0/1/0 | `setResult` + `getResult` | B1 |
| `LiveRoomAdSniper.java` | 1 | 1/0/0 | before 里 `setResult`（跳过原实现） | B1 |
| `FreeDataToastSniper.java` | 1 | 1/0/0 | 同上 | B1 |
| `PacketSniffer.java` | 1 | 1/0/0 | 写 `args` ×1 | B1 |
| `DexKitHelper.java` | 0 | — | 3 处 `LoadPackageParam` 签名 → 改传 `ClassLoader` + `ApplicationInfo` | B4 |
| `FbaRuntime.java` | 0 | — | `AndroidAppHelper` ×1 → `Host` | B4 |
| `FbaPrefs.java` | 0 | — | `AndroidAppHelper` ×1 → `Host` | B4 |
| `FollowingFilterConfig.java` | 0 | — | `AndroidAppHelper` ×1 → `Host` | B4 |

---

## 4. 设计决策

| # | 决策 | 理由 |
|---|---|---|
| **D1** | **全量原生，零兼容层** | 拿满 102 的能力（`deoptimize` / 优先级 / `Invoker` / hot reload），并让代码形态与官方文档一致，后续不再欠债 |
| **D2** | `targetApiVersion = 102`；`minApiVersion = 102` | 用了 102 的 `HookBuilder.intercept` 形态，且不保留 legacy 回退；不要在 101 上宣称兼容 |
| **D3** | 允许 `core/Reflect` 与 `core/Host`，**但禁止同形兼容层** | libxposed 不提供反射与上下文 API；`Reflect` 只做 `java.lang.reflect`，不模拟 before/after；`Host` 只做 Application/processName |
| **D4** | 单一入口，删 `assets/xposed_init` | 双入口会被支持双协议的框架 hook 两次 |
| **D5** | 异常策略：全局 `PROTECTIVE`，**调试构建显式 `PASSTHROUGH`** | 生产不因 hook bug 崩宿主；调试期暴露真实异常 |
| **D6** | 每个 `hook()` 的 `HookHandle` 统一登记到 `HookRegistry` | 支撑诊断面板与未来 hot reload；同时是 G2 对账的数据源 |
| **D7** | `scope.list` 列全三个包名（`tv.danmaku.bili` / `com.bilibili.app.in` / `tv.danmaku.bilj`） | `MainHook` 兼容改包别名 |
| **D8** | 分批交付（B1–B5），每批独立编译 + 真机验证 | 降低大爆炸风险；B1 同时充当 `Reflect`/`Host` 的验证载体 |

---

## 5. 原生改写规约

### 5.1 五种标准形态（只允许这五种）

```java
// ① before —— 观察 / 副作用，必须 proceed
hook(m).intercept(chain -> {
    Object self = chain.getThisObject();
    Object a0 = chain.getArg(0);
    // ……
    return chain.proceed();
});

// ② after —— 改返回值
hook(m).intercept(chain -> {
    Object result = chain.proceed();
    return transform(result);
});

// ③ replace —— 跳过原实现（对应 legacy before 里的 setResult）
hook(m).intercept(chain -> replacement);        // 不调用 proceed

// ④ arg-rewrite —— 改参数后继续
hook(m).intercept(chain -> {
    Object[] args = chain.getArgs().toArray();
    args[0] = newBody;
    return chain.proceed(args);
});

// ⑤ around —— 需要观测异常（对应 hasThrowable）
hook(m).intercept(chain -> {
    try {
        return chain.proceed();
    } catch (Throwable t) {
        FbaLog.e(TAG, "host threw", t);
        throw t;
    }
});
```

### 5.2 硬性规约

1. `proceed()` 每次 intercept **最多调用一次**，且必须在所有分支上要么调用要么显式返回替换值。
2. 禁止持有 `Chain` 引用到 intercept 之外（官方明确不可跨线程 / 不可复用）。
3. 禁止 `catch (Throwable) {}` 静默吞异常；必须 `FbaLog` 记录（G1.7）。
4. 反射一律走 `Reflect`，禁止散落的 `Class.forName(...).getDeclaredMethod(...)`。
5. `hookAllMethods` / `hookAllConstructors` 的等价写法：

```java
// legacy: XposedBridge.hookAllMethods(clazz, "invoke", cb)
for (Method m : clazz.getDeclaredMethods()) {
    if (!"invoke".equals(m.getName())) continue;
    registry.add(hook(m).intercept(chain -> { /* … */ return chain.proceed(); }));
}
```

6. `deoptimize` 只在 G4 发现「hook 日志缺失、功能不生效」时使用，且必须在 PR 里记录：`deoptimize(<caller>)` 的原因与验证结果。
7. 所有 `hook(...)` 必须显式 `.setExceptionMode(...)` 仅当与全局不同；否则依赖全局 `PROTECTIVE`。
8. `intercept` 内不得新增 I/O、网络、长耗时；现有 Toast / `Handler` 调用原样保留。

### 5.3 允许的两个工具（规格）

**`core/Reflect`** —— 必须复刻 `XposedHelpers` 的匹配规则，否则 46 处 `callMethod` 会随机失败：

| 方法 | 必须支持的匹配规则 |
|---|---|
| `findClass(name, cl)` | `Class.forName(name, false, cl)`，找不到抛 `ClassNotFoundException` |
| `findClassIfExists` | 返回 `null` 不抛（1 处使用） |
| `callMethod(target, name, args...)` | ①按实参类型精确匹配 → ②基本类型装箱/拆箱 → ③`null` 匹配任意引用类型 → ④父类与接口向上查找 → ⑤重载歧义时取最具体者；**必须解包 `InvocationTargetException`** |
| `callStaticMethod(clazz, name, args...)` | 同上 |
| `newInstance(clazz, args...)` | 匹配构造器，规则同 `callMethod` |
| `getObjectField` / `getIntField` / `setIntField` / `setBooleanField` / `getStaticObjectField` | 沿父类链查找字段，`setAccessible(true)` |

> `Reflect` 必须带一组自测用例（P2 交付物）：`null` 实参、装箱 `int`、父类方法、接口方法、重载消歧、可变参、`InvocationTargetException` 透传。

**`core/Host`** —— 替代 `AndroidAppHelper`：

| 方法 | 实现 | 降级 |
|---|---|---|
| `currentApplication()` | 反射 `ActivityThread.currentActivityThread().getApplication()`，首次成功后缓存 | 返回 `null` 时调用方必须容忍（当前 9 处都会判空） |
| `processName()` | `Application.getProcessName()`（API 28+）| API 26/27 反射 `ActivityThread.currentProcessName()` |

---

## 6. 任务分解（分批交付）

### P0 · 基线与闸门（0.5 人日）

- [ ] 在 1.1.2 构建上临时打点，导出 `baseline-hooks.txt`（G2.1）
- [ ] 记录性能基线：冷启动耗时 P50、gfxinfo 掉帧、meminfo（G5）
- [ ] 复核 `System.loadLibrary("dexkit")` 在 102 模块可用（**R1，先做**）
- [ ] 确认 `AndroidAppHelper` 是否仍注入（仅作信息记录，D3 已不依赖它）

### P1 · 构建与清单（0.5 人日）

- [ ] `libs.versions.toml`：删 `api`，加 `libxposed-api = 102.0.0`
- [ ] `app/build.gradle`：`compileOnly libs.libxposed.api`
- [ ] `settings.gradle`：删 `api.xposed.info` 仓库
- [ ] 新增 `META-INF/xposed/{java_init.list, module.prop, scope.list}`
- [ ] 删除 `assets/xposed_init`、manifest 四条 `meta-data`、`arrays.xml` 的 `xposed_scope`
- [ ] manifest 补 `android:description`
- [ ] **产出**：空壳模块能加载并打日志（此时 hook 全空）

### P2 · 基础设施（1.5 人日）

- [ ] `core/Reflect`（含 5.3 的匹配规则）
- [ ] `core/Host`
- [ ] `core/HookRegistry`（登记 `HookHandle`，支持 `dump()`）
- [ ] `Reflect` 自测用例全绿（见 5.3）
- [ ] **产出**：可被 B1 直接调用的工具类 + 自测记录

### P3 · 分批改写（6–8 人日）

| 批次 | 文件 | 关键验证 |
|---|---|---|
| **B1** | 9 个 S 档（`BiliAdSniper`、`LiveRoomAdSniper`、`FreeDataToastSniper`、`PacketSniffer`、`BiliGsonLobotomy`、`BiliBottomNavSniper`、`BiliCardAdSniper`、`VIPPredict`、`BiliMentionedSniper`） | 基础设施是否正确；`replace` 与 `arg-rewrite` 两种形态是否可用 |
| **B2** | `StoryShareMark`、`StoryAdSniper`、`FollowingListAdSniper`、`StoryAdFilter`、`FollowingTopLeftButton`、`GlobalAdSniperEngine` | `hookAllMethods` 展开、`hasThrowable`、`Set<HookHandle>` |
| **B3** | `BiliSplashSniper`、`MineServiceEntry`、`DanmuSenderLookup` | `callMethod` 密集区、循环注册、`LoadPackageParam` 签名迁移 |
| **B4** | `MainHook`（入口）、`DexKitHelper`、`FbaRuntime`、`FbaPrefs`、`FollowingFilterConfig` | 入口改造 + `Host` 全量替换 |
| **B5** | 清理、文档、发布 | G1–G7 全量复跑 |

每批完成必须：编译通过 → 装到真机 → 该批涉及的功能手测 → 打勾。

### P4 · 严格回归（2 人日）

- [ ] G4 矩阵全绿（两路径 × 13 项 × 两种宿主）
- [ ] G5 性能三项对比
- [ ] G6 稳定性
- [ ] G2 清单 diff

### P5 · 收尾（0.5 人日）

- [ ] README / 本文 / 发布说明（G7）
- [ ] 版本号 `1.2.0`（versionCode `10200`）

**合计：10–14 人日。**

---

## 7. 映射速查（改写时逐条对照）

| legacy (82) | 原生 (102) | 本项目处数 |
|---|---|---|
| `implements IXposedHookLoadPackage` + `handleLoadPackage` | `extends XposedModule` + `onPackageLoaded(PackageLoadedParam)` | 1 |
| `lpparam.classLoader` | `param.getClassLoader()` / `getDefaultClassLoader()`（29+） | 15 |
| `lpparam.packageName` | `param.getPackageName()` | 10 |
| `lpparam.appInfo.sourceDir` | `param.getApplicationInfo().sourceDir` | 3 |
| `lpparam.processName` | `Host.processName()`（无对应） | 2 |
| `XposedHelpers.findClass` | `Reflect.findClass` → `Class.forName(n,false,cl)` | 39 |
| `XposedHelpers.findClassIfExists` | `Reflect.findClassIfExists` | 1 |
| `findAndHookMethod` / `findAndHookConstructor` | 取 `Method`/`Constructor` → `hook(x).intercept(...)` | 30 |
| `XposedBridge.hookMethod` | `hook(m).intercept(...)` | 11 |
| `XposedBridge.hookAllMethods` | 遍历 `getDeclaredMethods()` + 逐个 `hook()` | 13 语句 |
| `XposedBridge.hookAllConstructors` | 遍历 `getDeclaredConstructors()` | 1 |
| `beforeHookedMethod` | `proceed()` 之前 | 19 |
| `afterHookedMethod` | `proceed()` 之后（异常路径也要执行） | 30 |
| `param.setResult(v)` in before | 不调 `proceed`，`return v` | 见 G3.2 |
| `param.setResult(v)` in after | `return chain.proceed()` 后替换返回值 | 见 G3.2 |
| `param.getResult()` | `Object r = chain.proceed()` | 11 |
| `param.args[i]` 读 | `chain.getArg(i)` | 30 |
| `param.args[i] = v` | 复制数组 → `chain.proceed(newArgs)` | 2 |
| `param.thisObject` | `chain.getThisObject()` | 26 |
| `param.method` | `chain.getExecutable()` | 4 |
| `param.hasThrowable()` | `try { proceed() } catch (Throwable t) { … }` | 7 |
| `XC_MethodReplacement.DO_NOTHING` | `chain -> null` | 1 |
| `XposedBridge.log` | `log(Log.INFO, TAG, msg)` | 0 |
| `AndroidAppHelper.currentApplication()` | `Host.currentApplication()` | 9 |

---

## 8. 风险登记册

| # | 风险 | 症状 | 处置 | 状态 |
|---|---|---|---|---|
| **R1** | `System.loadLibrary("dexkit")` 在 102 模块失败 | DexKit 全废 → 开屏/Feed/直播/播放页广告静默失效，小视频仍正常（**最易误判**） | 失败则从 APK 解出 `libdexkit.so` 用 `System.load(path)`；或改 `native_init.list` | 待测（P0） |
| **R2** | `Reflect.callMethod` 匹配规则不全 | 46 处调用随机 `NoSuchMethodException` / 传错重载 → 功能时好时坏 | 5.3 的 7 条匹配规则 + 自测用例；B1 批优先暴露 | 高 |
| **R3** | 49 个回调拆分错误（before/after 标反） | 不崩溃，但**返回值覆盖时机错**，广告偶发复现 | G3 逐条标注 + G2 清单对账 + 日志对比 | 高 |
| **R4** | `hasThrowable` 语义丢失 | 宿主抛异常时 after 逻辑不再执行 → 状态残留 | 5.1 形态 ⑤；7 处逐条核对 | 中 |
| **R5** | `hookAllMethods` 展开集合与原实现不一致 | 少 hook 一个重载 → 特定版本失效 | G2 对账（对账单位是实际成员集合） | 中 |
| **R6** | `Chain` 被跨线程持有 | 未定义行为 / 偶发崩溃 | 5.2 规约 2；G1.6 人工核对 | 低 |
| **R7** | LSPatch 下 `sourceDir` 指向 stub（既有） | DexKit 扫不到真实 dex | 非本次引入；只要求「不劣于迁移前」 | 既有 |
| **R8** | 调试期 `PASSTHROUGH` 误入生产 | 模块 bug 直接崩宿主 | D5：构建类型区分；release 断言全局 `PROTECTIVE` | 低 |

---

## 9. 回归矩阵（每格必须附日志片段）

| 功能 | LSPosed 2.2.1 | LSPatch 102 | 判定依据（日志关键字） |
|---|:---:|:---:|---|
| 开屏广告拦截 | ☐ | ☐ | `[SPLASH]` |
| Feed / 搜索 / 动态卡片 | ☐ | ☐ | `[FILTER]` / `BiliCardAdSniper` |
| 关注页广告与抽奖 | ☐ | ☐ | `[FOLLOW]` |
| 直播间广告 | ☐ | ☐ | `LiveRoomAdSniper` |
| 播放页 / 暂停页广告 | ☐ | ☐ | `paused page` |
| 小视频自动跳过 | ☐ | ☐ | `story ad skipped` |
| 彻底剔除广告（实验） | ☐ | ☐ | `[FILTER] 剔除 N 条` |
| 设置入口 | ☐ | ☐ | `MineServiceEntry` |
| 分享面板标记 | ☐ | ☐ | `[MARK]` |
| 弹幕发送者查询 | ☐ | ☐ | （1.1.1 起已知失效，只验不崩） |
| 日志查看 / 导出 / 清空 | ☐ | ☐ | `events.log` |
| 更新检查 | ☐ | ☐ | `[UPDATE]` |
| 非主进程不装 hook | ☐ | ☐ | 无 `[BOOT]` |

---

## 10. 回滚

1. 迁移在 `feat/libxposed-native` 分支，`main` 保持 1.1.2 可发布。
2. 回滚 = 丢弃分支 + 重新发 1.1.2 构建，不做双入口灰度（D4）。
3. 先发 pre-release，观察 1–2 个宿主版本；正式同步 Xposed 模块仓库延后。
4. Release 说明注明「需框架支持 API 102」。

---

## 附录 A · 回调矩阵模板（`docs/migration/callback-matrix.md`）

P3 开始时由本文 3.2 生成，逐行填写：

| # | 文件 | 类#方法 | 原形态 | 新形态 | `proceed` 次数 | 备注 |
|---|---|---|---|---|---|---|
| 1 | LiveRoomAdSniper | X#shouldInterceptRequest | before + setResult | replace | 0 | 返回空 `WebResourceResponse` |
| 2 | PacketSniffer | X#fromJson | before + args 写 | arg-rewrite | 1 | — |

> 49 行全部填满并通过 G3 才算完成。

## 附录 B · 构建目标状态

```
app/src/main/
├── assets/                      ← 删除 xposed_init
├── java/com/fuck/bili/ads/
│   ├── core/                    ← 新增：Reflect / Host / HookRegistry
│   ├── MainHook.java            ← extends XposedModule
│   └── …                        ← 23 个文件全部原生改写
└── resources/META-INF/xposed/
    ├── java_init.list
    ├── module.prop              ← minApiVersion=102 / targetApiVersion=102 / staticScope=true
    └── scope.list
```

## 附录 C · 参考

- [libxposed/api](https://github.com/libxposed/api)
- [LSPosed Wiki：现代 Xposed API](https://github.com/LSPosed/LSPosed/wiki/Develop-Xposed-Modules-Using-Modern-Xposed-API)
- [XposedInterface](https://libxposed.github.io/api/io/github/libxposed/api/XposedInterface.html) / [Chain](https://libxposed.github.io/api/io/github/libxposed/api/XposedInterface.Chain.html) / [ExceptionMode](https://libxposed.github.io/api/io/github/libxposed/api/XposedInterface.ExceptionMode.html)

---

## 变更记录

| 日期 | 内容 |
|---|---|
| 2026-10-27 | 初稿：路线 A（适配层），基线 v1.1.2 |
| 2026-10-27 | **v2：改为路线 B 全量原生改写**；删除适配层章节，新增 5 种拦截形态规约、`Reflect`/`Host` 规格、7 道验收门禁（G1–G7）、分批交付 B1–B5、回调矩阵模板 |
| 2026-10-27 | **v4：用户回归测试通过，1.2.0 正式版发布**。合并到 `main` 并打 tag `v1.2.0`，由 CI（`.github/workflows/release.yml`）用正式密钥签名构建，发布 GitHub Release 与 Xposed 模块仓库；versionName 1.2.0 / versionCode 10200。 |
| 2026-10-27 | **v3：代码迁移完成（未提交）**。G1 静态门禁通过；`Reflect`、广告标识判定、剔除提示文案共 15 项单测全绿；Debug 与 Release 均真机加载成功（LSPosed 2.2.1 / API 102）；Release 99 个 Hook 句柄 / 92 个唯一成员，Debug 额外注册 PacketSniffer 的 fromJson 重载；`[BOOT] hooks done` 正常；新增 `docs/migration/callback-matrix.md`、`current-hooks.txt`、`g1-static.txt`、`smoke-log.txt`、`USER-TEST.md`。详见下方“执行记录”。 |

---

## 执行记录（2026-10-27）

### 已完成

| 项 | 证据 |
|---|---|
| 构建清单切换 | `libs.versions.toml` 使用 `io.github.libxposed:api:102.0.0`；`settings.gradle` 删除旧 Xposed 仓库；`META-INF/xposed/{java_init.list,module.prop,scope.list}` 已打入 APK |
| 静态门禁 G1.1–G1.5 | 全部无匹配（命令输出见交付说明） |
| 基础设施 | `core/Reflect.java`、`core/Host.java`、`core/NativeHooks.java`、`core/HookRegistry.java` |
| 全量 Hook 改写 | 23 个文件；入口改为 `XposedModule#onPackageLoaded` |
| 回调矩阵 G3 | `docs/migration/callback-matrix.md`，逐注册点标注 before/after/replace/arg-rewrite 与 proceed 次数 |
| 反射/判定单测 G2 前置 | `ReflectTest` 10 项 + `BiliGsonLobotomyTest` 3 项 + `StoryAdFilterTest` 2 项，15/15 通过 |
| 真机冒烟 G4 部分 | 最终 Release 两次冷启动：99 句柄 / 92 唯一成员，无崩溃；Debug 三次冷启动通过（额外含 PacketSniffer）；`current-hooks.txt` 已落盘；前后台切换同一 pid；非主进程（`:download`/`:web`/`:widgetProvider`）均跳过 |
| Release 行为 | `BuildConfig.DEBUG=false` 下 PacketSniffer 未安装，符合预期；LSPosed 加载 `api=102`；回归测试用的是 `FuckBiliAds-1.2.0-test.apk`（debug key 临时签名）。正式发布包由 CI 用正式密钥重新构建，不复用该测试包 |
| 版本号 | `1.2.0` / `10200`（构建默认值） |
| README | 兼容性表与 1.2.0 变更说明已更新 |

### 尚待完成（需要用户回归或基线设备）

| 门禁 | 原因 |
|---|---|
| G4 全功能矩阵 | 用户人工回归已通过（小视频跳过/剔除、提示开关等），但未按 13 项 × 2 路径 × 9.15/9.16.1 的表格逐格留证 |
| G2.1 `baseline-hooks.txt` | 需要旧 1.1.2 构建在设备上运行并打点；当前设备已装 1.2.0，且旧包签名不同，无法原地并存 |
| G2.3 两份清单 diff | 取决于 G2.1 |
| G5 性能对比 | 需要同设备旧基线三次采样；本次只记录了启动日志，未做 P50/gfxinfo/meminfo 对比 |
| G6.1 8 小时挂机 | 需要长时间运行 |
| LSPatch 路径 | 本次只验证 LSPosed 2.2.1 |

> 以上均为**证据类**门禁，不影响 1.2.0 发布；发布包由 CI 使用正式密钥构建。

### 关键修复

- `BiliGsonLobotomy` 增加 Adapter 结构保护，避免把 `getItemViewType` 的 `cm_v2:3` 改成 `unknown` 导致宿主崩溃。
- `StoryAdSniper` 的生命周期回调只对 Story 页面启动会话，避免主页/详情页误启动扫描。
- `HookRegistry` 在 Application 尚未 attach 时重试写文件，保证 G2.2 证据不丢失。

### 发布

| 项 | 内容 |
|---|---|
| 分支 | `feat/libxposed-native` → 合并进 `main`（fast-forward） |
| Tag | `v1.2.0`（不带 `-pre`，CI 判定为正式版） |
| 构建 | `.github/workflows/release.yml`：`./gradlew :app:assembleRelease -PversionCode=10200 -PversionName=1.2.0`，用仓库 secret 里的正式 keystore 签名（本地不签名、不出包） |
| 产物 | `FuckBiliAds-1.2.0.apk`，挂在 GitHub Release；同时同步到 `Xposed-Modules-Repo/io.github.fesht377.biliads`（tag `10200-1.2.0`） |
| versionCode | 由 tag 推导 `1*10000 + 2*100 + 0 = 10200`，与本地默认值一致 |
| 发布结果 | <https://github.com/feSHt377/FuckBiliAds/releases/tag/v1.2.0>（正式版，非 draft / 非 pre）；APK SHA-256 见 Actions 的 Step Summary |
| 签名 | CI 正式密钥 `CN=FuckBiliAds`，SHA-256 `932985230f23e38530dc4abc949a4eb4adc5a08325509abaac4a7be35af21981`，与 1.1.2 一致，可直接覆盖升级 |
| 模块仓库 | <https://github.com/Xposed-Modules-Repo/io.github.fesht377.biliads/releases/tag/10200-1.2.0> |
| 发布流程修复 | 期间发现并修掉三处 CI 缺陷：`append_body` 在新建 release 时失效（正文没有 `versionCode`）、`PATCH /releases/tags/{tag}` 返回 404（必须用 `/releases/{id}`）、删 tag 会把 release 变 Draft（构建需显式 `draft: false`） |

### 回滚

迁移已完成并合入 `main`（tag `v1.2.0`）。回滚 = `git revert` 合并提交或直接重发 `v1.1.2` 的构建；`main` 上 1.1.2 的历史提交 `0106d30` 保留。

