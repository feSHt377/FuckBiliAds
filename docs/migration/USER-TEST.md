# FuckBiliAds 1.2.0 用户测试清单

> 设备当前已安装：`FuckBiliAds-1.2.0-test.apk`（Release 代码，使用本机 debug key 临时签名）
> 宿主：哔哩哔哩 9.16.1（9161100）· Android 15 · LSPosed 2.2.1（API 102）
> 本包**不能用于正式发布**；正式包必须用 CI 的 release keystore 重新签名。

## 安装与回滚

- 当前包已覆盖安装，包名仍是 `io.github.fesht377.biliads`，版本 `1.2.0 (10200)`。
- 回滚到 1.1.2：先卸载当前模块，再安装备份 `work/biliads-1.1.2-release-backup.apk`。
- 以后安装 CI 正式包前也要先卸载当前包（签名不同，无法直接覆盖）。

## 建议测试顺序

### 1. 启动与基础稳定性

- 强制停止哔哩哔哩后冷启动 3 次，确认不闪退。
- 进入首页、搜索、动态、关注、我的、直播间、普通播放页各停留 5 秒。
- 切后台再回前台，确认不闪退、设置入口还在。
- 日志应出现：

```text
[BOOT] hooks done: 剔除✓ 小视频✓ 设置入口✓ 分享标记✓, registered=99
[HOOK REGISTRY] written .../current-hooks.txt, handles=99, unique=92
```

### 2. 小视频广告自动跳过（重点）

- 设置页新增 **“剔除提示附带位置”**，默认关闭。
  - 关闭：仍显示 `已剔除 N 条广告！FUCK BILI ADS！`
  - 开启：显示 `已剔除 N 条广告（后x条）！FUCK BILI ADS！`，`x` 表示该广告距当前视频的位置。
  - 设置页「测试剔除提示」可直接用固定 `后3条` 验证文案。
- 进入竖屏小视频，连续刷至少 100 条，尽量遇到 ≥3 条广告。
- 命中广告后应：
  - 自动滑到下一条；
  - 弹出 `检测到广告，已跳过！FUCK BILI ADS！`；
  - 同一条广告不重复跳；
  - 连续广告能连续跳，不死循环；
  - 普通视频不被误跳。
- 日志关键字：

```text
story: ad detected at pos ...
story ad skipped at pos ...
```

- 如果只看到 `story scan ... ad=false` 而没有广告，说明这批流没有广告，不是失效。

### 3. 开屏 / 信息流 / 关注页 / 直播 / 播放页

- 冷启动不应出现开屏广告；设置里“品牌开屏隐藏”默认关闭，属正常。
- 首页/搜索/动态广告卡片应被过滤。
- 关注页广告/抽奖按设置规则过滤。
- 直播间广告位、播放页下方广告、暂停页广告应消失。

### 4. 设置与分享

- 我的 → 更多服务 → `FuckBiliAds` 能打开。
- 设置项可切换，日志可查看/导出/清空。
- 小视频分享面板出现“标记广告”，点击能写入日志。
- 更新检查能返回结果（或安静失败，不崩溃）。

## 抓日志

```powershell
adb logcat -c
adb logcat -s FuckBiliAds
```

设备端文件：`/data/user/0/tv.danmaku.bili/files/fuckbiliads/events.log`
Hook 清单：`/data/user/0/tv.danmaku.bili/cache/fuckbiliads/current-hooks.txt`

## 已知未完成项（不是代码阻塞）

- 未生成旧 1.1.2 的 `baseline-hooks.txt`，因此 G2 的“两份清单 diff”没有做。
- 未做性能 P50 / gfxinfo / meminfo 对比。
- 未做 8 小时挂机。
- 未验证 LSPatch 路径；本次只验证 LSPosed 2.2.1。
- 小视频跳过依赖真实广告样本，本次冒烟没有遇到广告；需要你实际刷到广告确认。

## 反馈时请提供

1. 宿主版本号；
2. 复现步骤；
3. `adb logcat -s FuckBiliAds` 对应时间段；
4. 是否只在某个页面出现；
5. 是否可以稳定复现。
