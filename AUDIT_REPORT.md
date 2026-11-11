# BetterSound 1.0.0 审计报告

日期：2026-11-09
对象：`D:\BetterSound`（common/、adapters/、tools/、dist/ 共 16 个 jar）
方法：通读全部 Java 源码、构建脚本、资源生成脚本与两份 sounds.json；解包 `dist/` 中 1.21.1 与 26.3 两个 jar；用本机 Loom 缓存里的 Minecraft 1.21.1 / 1.21.11 / 26.1 / 26.3 客户端 jar 反编译核对 mixin 注入目标与声音引擎行为；运行 `tools/validate_assets.py` 并对 207 个音频文件做采样率、声道、时长、循环接缝统计。未实际启动游戏。

## 总体结论

代码结构清晰，资源完整、引用无缺漏，mixin 目标在四个抽检版本上都存在，构建产物元数据正确。但有 2 个功能性缺陷会让 README 宣传的效果落空（潜行脚步、猫头鹰/鸟叫），以及若干生物群系判定和维度判定的逻辑漏洞。建议修复高、中两档后再对外发布。

## 问题清单

### 高

**H1. 「潜行时脚步更轻」对玩家自己完全无效**
位置：`common/java/com/bettersound/core/StepDynamics.java:15`，README「脚步动态」一节。
原版 `Player.getMovementEmission()` 在玩家站在地面且按住潜行时返回 `NONE`，玩家根本不会发出脚步声（已在 26.1 / 26.3 字节码中确认，该逻辑自 1.17 起未变）。因此 `sneakStepVolume` 从不作用于潜行者本人的脚步。它实际的效果是：玩家按住 Shift 时，2 格内生物或其他玩家的脚步声被压低到 45%，这与文档描述相反。
建议：删掉该选项与文档描述，或改为真正实现（例如在潜行时自行播放一组低音量脚步）。

**H2. 猫头鹰叫声几乎永远听不见，一半鸟叫听不见**
位置：`AmbientEngine.java:124-139`。
Minecraft 定位音的可听半径 = `max(volume, 1) × 16` 格，线性衰减到 0（已在 26.3 的 `Sound.getAttenuationDistance` 中确认）。一次性音效传入的 volume 都小于 1，所以半径固定 16 格。猫头鹰生成在 14–32 格外，只有 14–16 格那一小段能以不到 12% 的音量听到；鸟叫生成在 8–24 格外，超过 16 格的一半全部静音，12 格处也只剩 25%。
建议：把生成距离压到 4–12 格，或把 volume 提到 1 以上并由 `sounds.json` 的 `attenuation_distance` 控制范围。

### 中

**M1. 红树林沼泽被判成寒冷群系**
位置：`Biomes.java:15-17`。
`isCold` 用子串 `"grove"` 匹配山地的 grove，但 `mangrove_swamp` 同样包含 `grove`。结果：红树林里没有鸟鸣、没有夜虫，风声基础值 +0.35，且 `isForest` 因 `isCold` 为真而返回 false。
建议：改为精确匹配或排除 `mangrove`；同理 `"peaks"` 会把温暖的 `stony_peaks` 判为寒冷。

**M2. 一次性环境音没有维度检查，下界会出现洞穴滴水和鸟叫**
位置：`AmbientEngine.java:56`、`117-148`。
`computeTargets` 有 `env.overworld` 检查，但 `oneShots` 没有。下界海平面为 32，滴水条件 `y < seaLevel - 4` 在 y < 28 处成立，挖远古残骸常去的 y=15 会持续听到水滴声。绯红/诡异森林名字含 `forest`，站在基岩顶上时白天会有鸟叫。
建议：在 `tick` 里把 `env.overworld` 加入 `oneShots` 的前置条件。

**M3. 流式声道上限 8，叠加场景下会打满并每 tick 重试**
位置：`AmbientEngine.java:54`、`adapters/*/BetterSoundClient.java:startLoop`。
26.3 的 `Library` 将流式声源数钳制在 2–8。模组 9 个循环全部 `stream: true`，原版音乐与唱片也占流式声道。傍晚（白天与夜晚循环同时淡入淡出）+ 下雨（室外与室内雨声同时存在）+ 河边 + 海滩 + 风，可同时达到 7 个，再加背景音乐就满了。一旦 `play()` 拿不到声道，`isLoopAlive` 为 false，引擎每 tick 新建一个 `LoopSound` 重试，刷 20 次/秒的「Failed to create new sound handle」警告。主音量为 0 或无音频设备时同样每 tick 重试。
建议：对 `startLoop` 加冷却（如失败后 40 tick 内不重试）；把短循环改为非流式，或限制同时活跃的循环数。

**M4. 雨声双重叠加，山体内会播「屋顶雨声」**
位置：`AmbientEngine.java:95-102`，`tools/build_assets.py` 中对 `weather.rain` 的覆盖。
模组既用真实雨滴替换了原版 `weather.rain`/`weather.rain.above`（原版按 tick 随机位置播放），又叠加了自己的 `rain` 循环，两者同时响，总响度高于任一方单独设计值。另外 `env.rain` 取自玩家所在列最高处是否下雨，而 `underground` 只在海平面以下才大于 0，所以在海平面以上的山体洞穴里会以 0.75 音量播放 `rain_indoor`。
建议：二选一（保留循环时把 `weather.rain` 的覆盖音量调低），并把 `underground` 的判定加入「头顶有多少实心方块」而不只看海平面。

### 低

**L1. Mixin 兼容级别与 26.x 的类版本不一致**
位置：`adapters/mojmap/resources/bettersound.mixins.json`。
26.x jar 里的 mixin 类是 class 版本 69（Java 25），配置却声明 `JAVA_21`。Fabric Loader 可能会自动抬高兼容级别，但未经运行验证。建议在 26.x 适配层声明匹配的级别，并至少做一次 `runClient` 冒烟。

**L2. `ambientVolume` 文档说 0–2，实际大于 1 基本无效**
`AmbientEngine.set()` 把目标钳到 0–1，`SoundEngine` 本身也把音量钳到 1。只有少数系数之积小于 0.5 的层才能从 >1 的 master 中受益。建议文档改为 0–1，或说明只是「相对权重」。

**L3. 缺少 LICENSE 文件**
README 与 `fabric.mod.json` 都声明代码 MIT，但仓库根目录没有 LICENSE 文件；`fabric.mod.json` 的 `license` 字段也不是 SPDX 标识符，Modrinth/CurseForge 会按「自定义」处理。

**L4. 资源生成结果取决于本机 .minecraft 状态，不可复现**
`tools/vanilla.py` 取 `%APPDATA%\.minecraft\assets\indexes` 下修改时间最新的索引（本机当前是 `17.json`，即 1.21.x）来决定哪些事件可覆盖、以及响度匹配的参考值。换一台机器或启动过别的版本后，生成出的 `sounds.json` 会不同。建议把参考版本写死在脚本参数里。

**L5. 素材出处的佐证偏弱**
Kenney Impact Sounds 从第三方 GitHub 镜像（Boyquotes）下载而非 kenney.nl 官方；三个 minetest 金属脚步的 CC0 声明是转述 minetest_game 的 README。两者大概率没问题，但建议在 CREDITS 中给出官方原始链接，并保存 BigSoundBank 许可页面的快照日期。

**L6. 疾跑加重与音调抖动的作用范围比文档宽**
`StepDynamics.volume` 的 2 格判定会把玩家疾跑时身边生物的脚步一起加重；音调抖动对所有距离的所有 `*.step` 事件生效（含远处生物）。后者可视为特性，但应写明。`isStep` 的 `endsWith("_step")` 分支在原版事件中没有命中。

**L7. 没有自动化测试，也没有运行记录**
16 个 jar 只经过编译与资源校验，仓库中没有任何 `runClient` 或人工测试记录。至少应对 1.21.1、1.21.11、26.1、26.3 各启动一次确认无 mixin 报错、环境音能起播。

**L8. 杂项**
- `download_sources.py` 的 User-Agent 伪装为 Mozilla，建议改为诚实的客户端名。
- `BetterSoundClient.onTick` 在离开世界时只清空了 `loops` 映射，`AmbientEngine` 的 current 值没有归零，重进世界时循环会从上次音量直接起播（约 3 秒内自然收敛，影响很小）。
- 发行 jar 每个约 19.9 MB，16 个合计 318 MB，其中 17.9 MB 是 9 个 90–150 秒的立体声循环；若要减小体积可缩短到 45–60 秒。

## 已核实无问题的项

- 两套 mixin 的注入目标均存在：1.21.1 与 1.21.11 的中间名 `method_4849`/`method_4853` 对应混淆类中的 `g(…)F`/`h(…)F`；26.1 与 26.3 的 `SoundEngine.calculateVolume(SoundInstance)` 与 `calculatePitch(SoundInstance)` 均为 private float。1.21.x jar 采用静态重映射（manifest `Fabric-Loom-Mixin-Remap-Type: static`），不需要 refmap。
- `assets/minecraft/sounds.json` 共 223 个覆盖事件，全部带 `replace: true`；`assets/bettersound/sounds.json` 12 个事件；207 个 ogg 全部被引用且文件存在，没有孤儿文件。`validate_assets.py` 通过。
- 所有定位音为 44.1 kHz 单声道；9 个循环为立体声且以 `relative + 无衰减` 播放，符合 Minecraft 对立体声的处理方式。循环接缝处的样本跳变均小于整体 RMS 的 1/3，听感上应无爆音。
- `fabric.mod.json` 的 `${version}`/`${mc}`/`${java}` 展开正确，`environment: client`，每个 jar 精确绑定对应 MC 版本。
- 配置读取使用 Gson 宽松模式，README 中带 `//` 注释的示例 JSON 可以被正确解析，不会导致配置被重置。
- 环境采样每 10 tick 做 25 次天空检测、324 次流体查询、81 次方块查询，开销可忽略。
- 没有网络访问、反射或不安全的文件操作；配置文件只在 `config/bettersound.json`。

## 修复记录（2026-11-09，高、中两档）

| 编号 | 处理 | 改动位置 |
|---|---|---|
| H1 | 真正实现潜行脚步：新增 `SneakSteps` 按原版步频（水平位移 × 0.6，每 1 单位一步）累计，在潜行、着地、未骑乘、未入水、未飞行时用所踩方块自己的 step 音效以 `方块音量 × 0.15 × sneakStepVolume` 播放；mixin 不再对潜行做任何处理，避免二次叠乘 | `core/SneakSteps.java`、`core/StepDynamics.java`、两套 `BetterSoundClient.java`、两套 mixin |
| H2 | 鸟叫生成距离改为 6–20 格、猫头鹰 12–28 格并提高到 0.8 音量；`sounds.json` 中 bird 声明 `attenuation_distance: 32`、owl 48，生成脚本同步 | `AmbientEngine.oneShots`、`assets/bettersound/sounds.json`、`tools/build_assets.py` |
| M1 | `isCold` 先排除 `mangrove`、`stony_peaks`、`cherry` 再做子串匹配 | `core/Biomes.java` |
| M2 | `oneShots` 增加 `env.overworld` 前置条件 | `AmbientEngine.tick` |
| M3 | 每层循环启动后 40 tick 内不重试；同时最多 6 层循环，按当前音量从大到小分配，给音乐/唱片留声道 | `AmbientEngine`（`Layer`、`MAX_LOOPS`、`RETRY_TICKS`） |
| M4 | `weather.rain` 覆盖音量降到 0.5、`weather.rain.above` 0.35；新增 `roofBlocks`（头顶 32 格内非空气非树叶方块数），厚度 ≥ 12 视为山体内部，使雨声与室内雨声归零、洞穴环境音与滴水生效 | `core/EnvSnapshot.java`、两套 `BetterSoundClient.sample`、`AmbientEngine.computeTargets`、`assets/minecraft/sounds.json`、`tools/build_assets.py` |

验证：1.21.1（Yarn）与 26.3（官方名）编译通过，新 jar 中 mixin 目标仍为 `method_4849`/`method_4853`，`validate_assets.py` 通过。低档项目未处理。

## 修复记录（2026-11-09，低档）

| 编号 | 处理 | 改动位置 |
|---|---|---|
| L1 | 构建用 Mixin 0.17.5 的枚举包含 JAVA_25，26.x 适配层的兼容级别改为 `JAVA_25`，与 class 版本 69 一致 | `adapters/mojmap/resources/bettersound.mixins.json` |
| L2 | README 把 `ambientVolume` 说明改为「建议 0–1，大于 1 只抬高较安静的层」 | `README.md` |
| L3 | 新增根目录 MIT `LICENSE`（含对 CC0 音频的说明）；`fabric.mod.json` 的 license 改为 SPDX 数组 `["MIT", "CC0-1.0"]` | `LICENSE`、`common/resources/fabric.mod.json` |
| L4 | `vanilla.py` 不再取「最新修改」的索引，固定为资源索引 17（1.21/1.21.1），可用环境变量 `BS_VANILLA_INDEX` 覆盖，缺失时报错并列出可用索引 | `tools/vanilla.py`、`README.md` |
| L5 | 用网页核实 BigSoundBank 许可页：CC0 1.0 Universal，署名非必需；核实日期写入 `sources.json`、两份 `CREDITS.md` 与生成脚本。Kenney Impact 补官方来源链接，三个 minetest 金属脚步补 minetest_game README 链接 | `tools/sources.json`、`tools/build_assets.py`、`CREDITS.md`、`raw_sources/*/LICENSE.txt` |
| L6 | README 写明疾跑加重只作用于 2 格内、音调抖动作用于全部脚步；删除 `isStep` 中无命中的 `_step` 分支 | `README.md`、`core/StepDynamics.java` |
| L7 | 新增 JUnit 5 单元测试 21 条（群系判定、昼夜曲线、潜行步频、环境音引擎：主世界门禁、重试冷却、6 层上限、屋顶厚度），`gradlew build` 会一并运行；26.3 上全部通过 | `shared.gradle`、`common/test/java/com/bettersound/core/*Test.java` |
| L8 | 下载脚本 UA 改为 `Mozilla/5.0 (compatible; BetterSound-asset-fetch/1.0)`；新增 `AmbientEngine.reset()`，离开世界时清零音量与重试计时 | `tools/download_sources.py`、`AmbientEngine`、两套 `BetterSoundClient` |

未处理：缩短循环素材以减小 jar 体积（需重跑音频流水线）；在真实游戏中启动冒烟（本次全部为静态核对与单元测试）。
