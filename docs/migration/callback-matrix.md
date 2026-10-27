# libxposed API 102 回调矩阵

> 基线：`0106d30`（1.1.2）
> 迁移分支：`feat/libxposed-native`（未提交）
> 目标：`io.github.libxposed:api:102.0.0`
> 说明：本表按**注册 owner** 汇总；`hookAllMethods` 会展开为多个实际成员，逐成员清单见
> `docs/migration/current-hooks.txt`（最终 Release 实测 99 个句柄 / 92 个唯一成员；Debug 额外
> 注册 PacketSniffer 的 fromJson 重载）。

## 形态约定

| 形态 | 含义 | `proceed` 次数 |
|---|---|---:|
| before | 仅执行前置逻辑，随后走原实现 | 1 |
| after | 先走原实现，再基于返回值处理 | 1 |
| around | 前后都执行，且观测异常后原样重抛 | 1 |
| replace | 不调用原实现，直接返回替代值 | 0 |
| arg-rewrite | 复制参数数组、替换其中一项后继续 | 1 |

## 逐注册点矩阵

| # | 文件 | 类#方法 | 原形态 | 新形态 | proceed | 备注 |
|---:|---|---|---|---|---:|---|
| 1 | BiliAdSniper | VideoRelateAdViewFactory#createViewHolder | replace | replace | 0 | 返回 null |
| 2 | BiliBottomNavSniper | BaseMainFrameFragment#onCreate | before+after | around | 1 | 前后清理 tab 列表 |
| 3 | BiliBottomNavSniper | BaseMainFrameFragment#onViewCreated | before+after | around | 1 | 同上 |
| 4 | BiliBottomNavSniper | BaseMainFrameFragment#onActivityCreated | before+after | around | 1 | 同上 |
| 5 | BiliCardAdSniper | SearchAdapter#getItemCount | before | before | 1 | 原地移除广告项 |
| 6 | BiliCardAdSniper | HomeAdapter#(List) | before | before | 1 | 原地移除 Banner 广告 |
| 7 | BiliGsonLobotomy | Pegasus 模型所有 String 方法 | after + setResult | after | 1 | 仅替换广告标识；Adapter 子类结构性跳过 |
| 8 | BiliMentionedSniper | VideoMentioned#createViewEntry | replace | replace | 0 | 返回 Space 包装 |
| 9 | BiliMentionedSniper | VideoMentioned#bindToView | replace | replace | 0 | 返回 Unit.INSTANCE |
| 10 | BiliMentionedSniper | VideoMentioned#其余方法 | before+after | after（含异常日志） | 1 | 仅日志 |
| 11 | BiliSplashSniper | OkHttpClient$Builder#build | before | before | 1 | 注入 splash 拦截器 |
| 12 | BiliSplashSniper | Fragment#performViewCreated | after | after | 1 | 品牌开屏隐藏 |
| 13 | BiliSplashSniper | Fragment#performResume | after | after | 1 | 品牌开屏隐藏 |
| 14 | BiliSplashSniper | Fragment#onViewCreated | after | after | 1 | 品牌开屏隐藏 |
| 15 | BiliSplashSniper | Activity#onCreate（探针） | after | after | 1 | 只记日志 |
| 16 | BiliSplashSniper | Activity#onCreate（主题） | after | after | 1 | 替换启动页底色 |
| 17 | BiliSplashSniper | BaseSplash#onSplashReady | after | after | 1 | 调用宿主 skip |
| 18 | DanmuSenderLookup | Application#attach | after | after | 1 | WebView 调试 + 上下文 |
| 19 | DanmuSenderLookup | Activity#onResume（SpaceProbe） | after | after | 1 | 只启用 WebView 调试 |
| 20 | DanmuSenderLookup | 国际版 Chronos#invoke | before | before | 1 | 动态 hook handler |
| 21 | DanmuSenderLookup | 国际版 handler#invoke | before | before | 1 | 解析 RPC 事件 |
| 22 | DanmuSenderLookup | ChronosRpc#invoke（新版） | before | before | 1 | 解析 RPC 事件 |
| 23 | DanmuSenderLookup | ChronosMessageHandler#invoke（旧版） | before | before | 1 | 解析 RPC 事件 |
| 24 | DanmuSenderLookup | ClipboardManager#setPrimaryClip | after | after | 1 | 复制后匹配 dmid |
| 25 | DanmuSenderLookup | Activity#onResume（发送者） | after | after | 1 | 记录前台 Activity |
| 26 | FollowingListAdSniper | Application#attach | after | after | 1 | 初始化过滤配置 |
| 27 | FollowingListAdSniper | DynAllReply#getDynamicList | after | after | 1 | 返回值过滤 |
| 28 | FollowingListAdSniper | DynAllReply#parseFrom（全部重载） | after | after | 1 | Builder 重建返回值 |
| 29 | FollowingTopLeftButton | ExhibitionFragment#onCreateView | after | after | 1 | 注入设置按钮 |
| 30 | FreeDataToastSniper | NetworkShowToastService 方法 | before+setResult | replace | 0 | 返回 Unit.INSTANCE |
| 31 | GlobalAdSniperEngine | PegasusGsonParser#g | before + args 写 + after | arg-rewrite + after | 1 | 读 JSON、净化、替换 body |
| 32 | GlobalAdSniperEngine | Gson#fromJson（全部重载） | after | after | 1 | 清空 SplashListResponse |
| 33 | LiveRoomAdSniper | NativeWebView$d#shouldInterceptRequest | before+setResult | replace | 0 | 返回空 WebResourceResponse |
| 34 | MainHook | DescriptionTextView#<init> | after | after | 1 | 文本可选/长按 |
| 35 | MainHook | DescriptionTextView#onTouchEvent | replace | replace | 0 | 自绘长按选择 |
| 36 | MainHook | ViewBindings#findChildViewById | after | after | 1 | 隐藏播放器下方广告位 |
| 37 | MainHook | AdPausedPagePanel#onCreateView | before+setResult | replace | 0 | 返回空 View |
| 38 | MainHook | DetailAdService#showPanel | replace | replace | 0 | 返回 null |
| 39 | MineServiceEntry | HomeUserCenterFragment#onViewCreated | after | after | 1 | 延迟注入入口 |
| 40 | MineServiceEntry | HomeUserCenterFragment#onResume | after | after | 1 | 延迟注入入口 |
| 41 | MineServiceEntry | 我的页 Adapter#onBindViewHolder | before+after | around | 1 | bind 前补齐、bind 后挂点击 |
| 42 | MineServiceEntry | 我的页 Holder#onClick | before+setResult | before（命中返回 null） | 1/0 | 仅本模块条目跳过原逻辑 |
| 43 | PacketSniffer | Gson#fromJson（Debug only） | before + args 写 | arg-rewrite | 1 | Reader 重新包装 |
| 44 | StoryAdFilter | StoryPagerPlayer#T0/t1/V1 | before + args 写 | arg-rewrite | 1 | 批量剔除广告卡 |
| 45 | StoryAdSniper | StoryVideoActivity 生命周期 | after | after | 1 | 仅 Story 页生效 |
| 46 | StoryAdSniper | Activity 框架生命周期 | after | after | 1 | 仅 Story 页生效 |
| 47 | StoryAdSniper | RecyclerView#onAttachedToWindow | after | after | 1 | 补启会话 |
| 48 | StoryShareMark | StoryActionHelper 分享构建 | after | after | 1 | 显示标记按钮 |
| 49 | StoryShareMark | Story action controller#n | after | after | 1 | 显示标记按钮 |
| 50 | StoryShareMark | ShareSessionCoordinator#b | after | after | 1 | 关闭时隐藏按钮 |
| 51 | StoryShareMark | Activity#onPause | after | after | 1 | 隐藏按钮 |
| 52 | VIPPredict | 预测模型 score（Debug） | before+setResult | replace | 0 | 返回 0.95f |
| 53 | VIPPredict | 请求 onResponse（Release） | before | before | 1 | 置空广告响应体 |
| 54 | BiliSplashSniper | SplashListResponse 间隔（Debug only） | after+setResult | after | 1 | 缩短调试间隔 |
| 55 | StoryAdFilter / 其它动态注册 | 迭代 `getDeclaredMethods()` | hookAllMethods | 逐成员 hook | 1 | 实际成员见 registry |

## 语义核对

- `before + setResult` 全部改成 **replace（不 proceed）**，不存在“先执行原实现再覆盖”的误写。
- `after + getResult` 全部改成 **`Object r = chain.proceed();` 后处理**。
- 原 `hasThrowable()` 的 7 处均改为 `try { proceed() } catch (Throwable t) { log; throw t; }`，异常不会被吞。
- 原 `param.args[i] = v` 的 2 处均改为复制数组后 `chain.proceed(args)`，不在 proceed 之后修改数组。
- 没有 `Chain` 被保存到字段或跨线程使用。
- `hookAllMethods` 逐成员注册，统一经 `NativeHooks` 登记到 `HookRegistry`。

## G1.7 说明

`catch (Throwable)` 的静默捕获已清理到业务层；仍保留少量**基础设施**静默捕获，原因是：

- `FbaLog.close()` / `FbaRuntime.context()` / `Host.currentApplication()`：日志系统自身不可用或递归风险；
- `BiliSplashSniper` 中的 UI 美术兜底：不能因启动页绘制失败打断宿主启动。

这些点已在源码中写明原因，不再扩展新的静默捕获。
