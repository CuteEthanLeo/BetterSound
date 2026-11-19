# BetterSound（更好的声音）

Fabric 客户端模组：用**真实录音**替换方块声音，走在不同方块上会发出清脆、真实的脚步声；另外新增一层会随环境变化的环境音。只需要客户端安装，服务器端不用装，可以在任何服务器上使用。

## 安装
1. 到 `dist/` 里找到和你游戏版本一致的 jar，例如 `BetterSound-1.1.0+mc1.21.1.jar`。**每个 MC 版本只能用对应的那个 jar。**
2. 安装对应版本的 Fabric Loader 和 **Fabric API**。
3. 把 jar 和 Fabric API 都放进 `.minecraft/mods/`。

支持的版本：1.21.1 – 1.21.11、26.1、26.1.1、26.1.2、26.2、26.3

## 内容
| 类别 | 说明 |
|---|---|
| 脚步声 | 木地板（橡木、樱花木、竹子、下界木）、石头/深板岩/砖、草地、沙砾/泥土、沙子、雪、羊毛/地毯、泥巴、金属、梯子、玻璃、树叶。每种 5–12 个变体 |
| 放置/破坏 | 木头、石头、玻璃、金属的真实敲击和破碎声；草方块是带根带土被掀起的草皮声；花、草丛、蕨类、树苗等没有碰撞箱的植物单独用一组轻快的折茎+叶片沙沙声；沙砾、沙子、雪、泥巴用各自材质的厚重碾压声 |
| 门和箱子 | 木门、活板门、栅栏门、箱子、铁门 |
| 天气 | 真实雨声 |
| 动态环境音 | 森林白天鸟鸣、草原鸟鸣、夜晚虫鸣、猫头鹰、风声（越高越大，躲进室内或树林会变小）、雨声（室外是雨打地面，薄屋顶下是闷闷的雨打屋顶，深洞或山体内听不到）、洞穴低鸣和滴水、溪流、海浪。全部根据生物群系、时间、天气、高度、是否露天、头顶遮蔽厚度、附近有没有水实时淡入淡出；同时最多 6 层循环，给音乐和唱片留出声道。各层按感知响度（K 加权）统一母带，雨、水、风等噪声层在约 3 kHz 处挖出一个凹槽，把脚步的颗粒感让出来；多层叠加时整体有总线上限，不会糊成一堵噪音墙 |
| 脚步聚焦 | 你走动时环境音会像电影混音那样自动「让位」：溪流、海浪、雨、风这类噪声层最多下沉约 7 dB，鸟鸣只下沉约 4 dB；停下脚步 2–3 秒后完整的环境音回来。你自己的脚步（2 格内）比原版响 1.5 倍 |
| 盔甲脚步 | 本地玩家穿甲走动时，脚步会叠加轻微的装备声。皮甲是摩擦声，锁链和硬甲是短促碰响。潜行时装备声更轻。 |
| 脚步动态 | 原版潜行时完全没有脚步声，本模组会用所踩方块自己的脚步声补上一组很轻的潜行脚步；疾跑时脚步更重（作用于离你 2 格内的脚步声）；所有脚步声的音调都有细微随机变化，不会听起来像机械重复 |

环境音的音量由游戏里「声音设置 → 环境/氛围」这个滑块控制。

## 配置
第一次启动后会生成 `config/bettersound.json`：

```json
{
  "ambientVolume": 1.0,        // 环境音总音量，建议 0–1；每一层最大只到 1，大于 1 只会抬高较安静的层
  "ambientEnabled": true,
  "birds": true, "nightInsects": true, "wind": true,
  "rain": true, "caves": true, "water": true,
  "footstepDynamics": true,    // 潜行/疾跑脚步动态和音调随机
  "sneakStepVolume": 0.45,     // 潜行脚步相对正常脚步的音量（原版潜行无声）
  "sprintStepVolume": 1.15,
  "stepPitchJitter": 0.04,     // 每步音调随机幅度 ±4%
  "ownStepVolume": 1.5,        // 自己的脚步相对原版的音量（0–3）
  "footstepFocus": 1.0,        // 走动时环境音让位的程度，0 关闭，1 最大
  "armorStepVolume": 1.0,      // 盔甲脚步音量（0–2，0 关闭）
  "plantSounds": true          // 花草植物使用独立的破坏/放置声
}
```
改完后重启游戏生效。

## 从源码构建
使用 JDK 25 启动 Gradle，1.21.x 另外需要 JDK 21 工具链，以及带 numpy、scipy、soundfile 的 Python 3。

```bash
python -I tools/download_sources.py      # 下载 CC0 素材到 raw_sources/
python -I tools/build_assets.py          # 切片、降噪、响度匹配、做循环，生成 sounds.json（以本机 1.21.1 的原版资源索引 17 为参考，可用 BS_VANILLA_INDEX 覆盖）
python -I tools/validate_assets.py       # 校验资源
powershell -File tools/build_all.ps1     # 每个 MC 版本打一个 jar，输出到 dist/
```
单独构建某个版本：在 `legacy/`（1.21.x）或 `modern/`（26.x）目录里运行 `gradlew build dist -Pmc=1.21.4`。`build` 会先跑 `common/test/` 下的单元测试（群系判定、昼夜曲线、潜行步频、环境音引擎）。

## 素材来源
所有声音都是 **CC0 / 公共领域**的真实录音，主要来自 BigSoundBank.com（Joseph Sardin）、Kenney 和 OpenGameArt。每个文件的出处见 [CREDITS.md](CREDITS.md)。代码采用 MIT 许可（见 [LICENSE](LICENSE)）。
