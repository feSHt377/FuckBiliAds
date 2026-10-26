# 竖屏小视频广告自动跳过：调研、探测与实际实现

> 本文合并了原先的「定位任务」与「探测结果」两份文档，包含从需求、方法到实现与验证的完整链路。
>
> - 宿主：哔哩哔哩 9.15.0（versionCode 9150300，targetSdk 35）
> - 设备：OnePlus PGZ110 / Android 15（SDK 35）/ arm64-v8a
> - 框架：Magisk 30.7 + LSPosed 2.2.1（API 102）
> - 模块：FuckBiliAds（本仓库）

---

# 第一部分 · 任务与目标

## 1.1 目标

为 Xposed 模块增加「竖屏小视频广告自动跳过」：

> 捕获「首次进入、上下滑切换、后台恢复」时的**当前播放条目变化**，提取当前视频特征；确认是广告后，**调用宿主自己的下一条逻辑**跳过，并用 B 站自己的 Toast 提示。

**不是**删除 Feed 数据，**也不是**只监听网络请求。

需要覆盖的场景：

- 从主页、搜索、动态、分享链接等入口进入竖屏播放器；
- 首次加载的视频；
- 上下滑切换视频；
- 命中预加载缓存、没有新网络请求的切换；
- 连续多条广告；
- 从后台恢复。

## 1.2 环境条件

- Xposed API 82，DexKit 2.2.0；
- Android minSdk 26、targetSdk 36；
- AGP 9.0.0、Gradle 9.1.0、Java 11 源码兼容（构建用 JDK 17+）。

## 1.3 探测阶段的原则

1. **只读**：不修改返回值、不跳过宿主方法、无任何权限、无网络；
2. 探针是**独立工程**（仓库外），只 Hook `tv.danmaku.bili`，不碰系统、不写宿主数据；
3. 人工标记 + 日志落盘，用真实样本确认判定式后再写正式代码。

## 1.4 探测方法

```powershell
adb devices -l
adb shell su -c id
adb shell pm path tv.danmaku.bili
adb shell dumpsys package tv.danmaku.bili
adb shell dumpsys activity top
```

探针的 Hook 点：

| Hook | 目的 |
|---|---|
| `Activity.performCreate/performResume/performPause` | 判定小视频页面生命周期 |
| `Fragment.performCreate/performResume` | 确认页面 Fragment |
| `ViewPager` / `ViewPager2.setCurrentItem` | 排除「翻页由 ViewPager 驱动」的假设 |
| `RecyclerView.Adapter.bindViewHolder`（after） | 取位置、条目、`adInfo` |
| `Gson.fromJson`（仅 String 入参，关键词过滤） | 观察广告数据注入时机 |
| `RecyclerView` 的 scroll 系列方法 | 判断宿主翻页实现 |

日志：`logcat` tag `BiliProbe`，经设备端 `probe_sink.sh` 逐行追加写入
`内部共享存储空间\BiliProbe\probe.log`（删除后自动重建，写入失败自动轮转，实测延迟约 0.2 秒）。

---

# 第二部分 · 探测结果

## 2.1 结论速览

1. 小视频页的广告判定**只有一个稳定入口**：`StoryDetail.adInfo`（类型 `com.bilibili.adcommon.data.model.FeedAdInfo`）。
2. 强判定：`adInfo != null && adInfo.feedIsAd == true`，本批数据 100% 命中，无误报。
3. `StoryDetail.adType` 恒为 `-1`，**不能**作为判定依据。
4. 广告与普通条目使用**同一个类** `com.bilibili.video.story.StoryDetail`，不能靠类名区分。
5. 广告在卡片流中插入密度极低（559 次 bind 中仅 4 条广告）。
6. 列表预加载导致「最后 bind 的条目」≠「屏幕上的条目」，判定必须以**当前可见条目**为准。

## 2.2 页面结构

| 角色 | 类名 |
|---|---|
| 页面 | `com.bilibili.video.story.StoryVideoActivity` |
| 页面 Fragment | `com.bilibili.video.story.StoryVideoFragment` |
| 列表 Adapter | `com.bilibili.video.story.L` |
| ViewHolder | `com.bilibili.video.story.M` |
| 条目模型 | `com.bilibili.video.story.StoryDetail` |

小视频页仍会出现 `androidx.viewpager.widget.ViewPager.setCurrentItem` 调用，但主列表是 `RecyclerView`，
翻页判定必须以 RecyclerView 的可见位置为准，**不要**用 ViewPager 翻页。

## 2.3 广告字段（FeedAdInfo）

广告样本实际值：

```text
feedIsAd         = true      // 最强判定，唯一应参与判定的字段
feedIsAdLoc      = true
natureAd         = 0 / 1     // 不稳定，禁止参与判定
feedSrcId        = 4356
feedResource     = 4355
feedCreativeId   = 每条广告唯一（3995855299 / 3997267961 / 4012794909 / 4021568648）
feedCardIndex    = 广告在卡片流中的序号
storyExposedFlag = false
creativeStyle    = 0
ecomAd           = false
```

普通条目：

```text
adInfo = null
adType = -1
```

## 2.4 当前可见条目的判定方法

1. 从当前 Activity 的 DecorView 递归查找 RecyclerView；
2. 要求其 Adapter 类名以 `com.bilibili.video.story` 开头；
3. 遍历子 View，用 `getGlobalVisibleRect()` 计算可见面积，取面积最大者；
4. 用 `RecyclerView.getChildAdapterPosition(child)` 得到位置；
5. 通过 Adapter 的 List 字段反射取 `StoryDetail`，再读 `adInfo`。

## 2.5 验证数据

人工标记 9 次：

| 标记 | 当前 pos | adInfo | feedIsAd | 结论 |
|---|---|---|---|---|
| AD | 6 | Y | true | 命中 |
| NORMAL | 5 | N | null | 正确 |
| AD | 6 | Y | true | 命中 |
| NORMAL | 45 | N | null | 正确 |
| NORMAL | 46 | N | null | 正确 |
| NORMAL | 47 | N | null | 正确 |
| NORMAL | 48 | N | null | 正确 |
| NORMAL | 49 | N | null | 正确 |
| NORMAL | 51 | N | null | 正确 |

3/3 广告全部满足判定式；6/6 普通条目全部不满足。

广告密度：StoryDetail 绑定 559 次，`adInfo` 非空 4 次（4 个不同 creativeId）。
注意这只是 **bind 样本中的比例**，包含预加载、重绑定与多次会话，**不等于曝光频率**。

## 2.6 异步性

本批样本中 `adInfo` 在 `bindViewHolder` 完成时已就绪，未观察到「先展示、后补字段」。
但实现仍应在「条目变为可见」时再取一次 `adInfo`，而不是只在 bind 时判定。

## 2.7 下一条动作

`RecyclerView.smoothScrollToPosition(pos + 1)` 实测有效（pos 6 → 7），宿主会正常切换播放。
备选顺序：

1. `smoothScrollToPosition(target)`；
2. `scrollToPosition(target)`；
3. `smoothScrollBy(0, height)`（兜底一屏）。

实测**不需要**模拟滑动手势。

---

# 第三部分 · 实现

## 3.1 判定代码

```java
Object item = storyDetail;                       // com.bilibili.video.story.StoryDetail
Object ad = readField(item, "adInfo");           // FeedAdInfo
boolean isAd = false;
if (ad != null) {
    isAd = Boolean.TRUE.equals(readField(ad, "feedIsAd"));
}
```

可退化为 `ad != null` 兼容未知变体，但优先 `feedIsAd`，避免把「预留广告位」误判成广告。

## 3.2 模块内的实现

| 类 | 职责 |
|---|---|
| `story.StoryAdSniper` | Hook `StoryVideoActivity` 生命周期，驱动可见条目监控 |
| `story.StoryView` | 找 RecyclerView、可见位置、条目、`adInfo`、`aid/cid` |
| `story.StoryNext` | 下一条执行器（含兜底与结果回报） |
| `story.HostToast` | 调用 `com.bilibili.droid.ToastHelper`，失败回落系统 Toast |

行为：

1. 页面 `performResume`/`onResume` 启动 400ms 轮询，`performPause`/`onPause`/`performDestroy` 立即停止；
2. 每轮取「可见面积最大」的 child，用它的 adapter position 定位条目，读 `adInfo.feedIsAd`；
3. 命中广告且通过去重/冷却 → 等 300ms 再确认「位置没变、仍是广告」，然后调用滚动；
4. 滚动请求发出后才弹 Toast；900ms 后校验位置确实前进，未前进则按
   `smoothScrollToPosition → scrollToPosition → smoothScrollBy(0, height)` 降级重试（最多 2 次）；
5. 全链路静默失败，绝不抛异常打断宿主播放。

Toast 文案：

```text
检测到广告，已跳过！FUCK BILI ADS！
```

## 3.3 去重与防循环

- 条目身份：广告优先 `adInfo.feedCreativeId`（广告的 `aid` 恒为 0，用 aid 会把所有广告
  当成同一条）；普通条目用 `aid`，都取不到时退化为 `pos`；
- 页面身份：Activity 实例（弱引用），页面销毁即失效；
- 同一身份只跳一次，冷却 700ms；只有滚动请求真的发出才登记为「已跳」，一次失败不会把广告永久拉黑；
- 连续广告允许连续跳，但 15 秒内最多 12 次，超出即暂停本轮；
- 跳转前再次确认「位置没变、仍然是广告」，避免与用户手动滑动打架；
- 不修改 Feed 数据、不修改任何返回值。

## 3.4 已知边界

- 广告在 Feed 中随机稀疏，「跳过」成功与否需长期使用观察；
- 宿主改版可能更换类名，实现层已做「类不存在即静默退出」；
- 只对 `tv.danmaku.bili` / `tv.danmaku.bilj` / `com.bilibili.app.in` 生效；
- 竖屏页以外的广告由模块其他模块负责，互不干扰。


---

# 第四部分 · 1.0.2 修复记录（实测「广告视频不跳」的根因）

实测不跳不是判定条件的问题：**可见条目解析在第一步就拿不到位置**，`scan()` 每次都在
`if (pos < 0) return;` 处退出，日志里除了一行 `story ad auto skip ready` 什么都没有。

## 4.1 根因一（致命）：反射按「精确类型」找方法

`StoryView.call()` 用 `args[i].getClass()` 当形参类型去 `getDeclaredMethod`，而
`RecyclerView.getChildAdapterPosition(View)` 的形参是 `View`，实际传入的是
`FrameLayout`、`ViewGroup` 之类的子类：

```java
getDeclaredMethod("getChildAdapterPosition", FrameLayout.class)   // NoSuchMethodException
```

于是永远返回 `null` → `dominantPos()` 恒为 `-1` → 整条链路在第一步就断掉。
最小复现（用 JDK 直接跑同样的解析逻辑）：

```text
getChildAdapterPosition -> null      // 模块里的 call()
real call               -> 7         // 直接调用
```

修复：`call()` 改为按「同名 + 同参数个数 + 形参可由实参赋值」匹配（基本类型自动装箱），
不再比较精确 `Class`。

## 4.2 根因二：广告身份用 `aid` 去重，第二条广告被当成「已跳过」

广告的 `aid` 是 0，而 `identityOf()` 里 `!"-".equals(aid)` 对 `"0"` 也成立，
所有广告 id 都变成 `a0`；第一条广告之后的 120 秒内，其余广告全部命中 `skippedAt`
被静默丢弃。修复：广告优先用 `adInfo.feedCreativeId` 做身份。

## 4.3 根因三：RecyclerView 识别写成精确类名

`"androidx.recyclerview.widget.RecyclerView".equals(view.getClass().getName())`
遇到宿主的 RecyclerView 子类就找不到列表。修复：沿继承链匹配类名；并且在没有
`com.bilibili.video.story.*` 适配器时，退化为「可见面积最大的非 ViewPager2 列表」，
避免评论列表/外层 pager 抢占。

## 4.4 其他加固

| 项 | 修改 |
|---|---|
| 条目读取 | 先读当前可见 child 的 ViewHolder 字段（即探测阶段 `bindViewHolder` 拿到的对象），适配器 List 只作兜底 |
| 适配器 List | 只接受 `com.bilibili.video.story.*` 类型的元素，避免被 `List<Long>` 之类的字段串位 |
| 字段读取 | 支持 getter 兜底（Kotlin 属性编译成 `getAdInfo()`） |
| 生命周期 | `performResume`/`onResume` 起，`performPause`/`onPause`/`performDestroy` 停；页面类改名时按 `com.bilibili.video.story.` 包前缀匹配 |
| Fragment 页面 | 额外挂 `RecyclerView.onAttachedToWindow`：列表挂到窗口且适配器属于 story 包时补启会话 |
| 跳转校验 | 三级降级重试，见 3.2 第 4 条 |

## 4.5 现场排查

```bash
adb logcat -s FuckBiliAds
```

| 日志 | 含义 |
|---|---|
| `story ad auto skip ready (page=...)` | hook 安装成功；`package-match` 表示页面类已改名，靠包前缀工作 |
| `story: story list not found (activity=...)` | 页面起来了但找不到列表（类名或适配器变了） |
| `story: card at N has adInfo but feedIsAd=...` | 已取到条目但判定不成立（字段语义变了） |
| `story: ad detected at pos N id=c...` | 判定命中 |
| `story ad skipped at pos N` | 已下发滚动 |
| `story: skip did not advance, retry N` | 滚动没生效，正在降级重试 |
