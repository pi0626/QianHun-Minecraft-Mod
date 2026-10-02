# 千魂 (Qianhun)

改编自高铭小说《千魂》的 Minecraft Fabric 模组。核心玩法：**说出真名唤醒千魂书 → 印掌纹成为神选者 → 聊天许愿，AI 把愿望翻译成游戏命令并以最高权限执行**。

支持两个版本：

| 版本 | Minecraft | Java | Fabric Loader |
|---|---|---|---|
| `qianhun_1211` | 1.21.1 | 21 | 0.19.5 |
| `qianhun_262` | 26.2 | 25 | 0.19.3 |

---

## 安装

1. 装 [Fabric Loader](https://fabricmc.net/use/installer/) 对应你游戏版本的版本。
2. 装 [Fabric API](https://modrinth.com/mod/fabric-api)（两个版本都在 Modrinth 上有）。
3. 把 `qianhun-1.0.0.jar` 丢进 `.minecraft/mods/`。

单机与服务器都支持。

---

## 第一次玩：四步

### 1. 拿到千魂书

- 创造模式物品栏里搜"千魂"，或
- `/give 你的ID qianhun:qianhun_book`
- 生存：击杀生物有概率在**林地府邸**的箱子里刷出（Loot 表已内置）

### 2. 唤醒它

在聊天框里打出它的真名并回车：

```
千魂书
```

书会从沉睡中醒来。就这一步之后，**你打的所有话都会被它听见**——所以下面第四步才需要前缀。

### 3. 印掌纹，成为神选者

击杀任何生物，25% 概率掉落一滴**心脏温血**（`qianhun:heart_blood`）。打开背包，用鼠标**拿起温血、点到千魂书上**，就像把一个物品叠到另一个物品上。

温血被消耗一滴，你成为神选者。神选者**不给任何属性加成**——它只是一个身份：那些死去的千魂开始护佑你，你可以许愿了。

### 4. 许愿

在聊天框里说：

```
许愿 给我一把钻石剑
许愿 把时间调成白天
```

以 `许愿` 开头的那句话**只有你自己看得见**，不会广播给别人。前缀可以在配置里改，也可以设成空字符串（那样任何一句聊天都算愿望——**多人服别这么干**）。

AI 会把你的话翻译成 Minecraft 命令，服务端以最高权限执行。

---

## 配置：填入你自己的 Base URL 和 API Key

**这个仓库不包含任何密钥，也不预置任何服务地址。** 每个人用自己的。

进游戏后，用聊天框输入（不需要 OP 权限）：

```
/qianhun key base https://你的服务商/v1
/qianhun key sk-你的密钥
```

- 凭据按玩家 UUID 存在**你自己世界的存档目录**下的 `qianhun_players.json` 里，别人看不到也改不了。
- `/qianhun key` 查看自己的填写状态（不回显密钥本体）。
- `/qianhun key clear` 让书忘掉你。

任何 OpenAI 兼容的 `/v1/chat/completions` 接口都能用（Agnes、OpenAI、DeepSeek、Ollama、LM Studio……），模型名在 `config/qianhun.json` 里改。

多人服服主也可以在 `config/qianhun.json` 里填 `aiBaseUrl` / `aiApiKey` 做全服统一配置，玩家自己的填写优先于它。

---

## 命令

| 命令 | 说明 |
|---|---|
| `/qianhun status` | 书的苏醒状态、千魂计数、你的 AI 接入状态、游尸存量 |
| `/qianhun key ...` | 管理自己的 Base URL 与 API Key（见上） |
| `/qianhun wish <愿望>` | 直接许愿，等价于聊天许愿，调试用 |
| `/qianhun spawn <生物ID> [数量]` | 手动刷游尸，测试用 |
| `/qianhun config <键> [值]` | 读/改配置（改需要 2 级权限） |
| `/qianhun reload` | 重新写出配置文件 |

### 常用配置项（`config/qianhun.json`）

| 键 | 默认 | 说明 |
|---|---|---|
| `soulThreshold` | `1000` | 许愿需要的千魂数，`0` = 不限 |
| `chatWishPrefix` | `许愿` | 聊天许愿前缀，空 = 任何聊天都算愿望 |
| `commandBlacklist` | `true` | 是否启用命令黑名单 |
| `youzhiSpawnEnabled` | `true` | 击杀生物后是否在当夜生成游尸 |
| `youzhiBurnInDaylight` | `true` | 游尸是否在日光下消散 |
| `youzhiPerTypeCap` | `8` | 同类型游尸同时存在上限 |
| `heartBloodDropChance` | `0.25` | 温血掉落概率 |
| `aiModel` | `agnes-2.5-flash` | 模型名 |

### 安全

AI 生成的命令默认过一遍黑名单（`/stop` `/op` `/deop` `/ban` `/ban-ip` `/pardon` `/kick` `/whitelist` `/save-off` `/save-all` `/reload` `/shutdown`），被拒的命令会在聊天里明说。想放开就 `commandBlacklist: false`，**风险自负**。

---

## 千魂与游尸

- 任何玩家每杀一个生物，全服千魂计数 +1（不拿书也算），存在世界存档里，重启不丢。
- 满 1000 后才能许愿——对应原著"每次神选之夜前要有一千个人因书而死"。嫌慢就 `/qianhun config soulthreshold 0`。
- 击杀生物后，它会变成**游尸**：照搬那个生物的外形，整体压成暗色，漫无目的地游荡、蹒跚而机械，白日太阳出来就消散。原著第 7202 行。
- 游尸生成在**当夜**，不立即出现。

---

## 构建

仓库自带 Gradle wrapper，不需要预装 Gradle，但需要对应版本的 JDK：

- `qianhun_1211` → **JDK 21**
- `qianhun_262` → **JDK 25**

```bat
cd qianhun_1211
set JAVA_HOME=C:\path\to\jdk-21
gradlew.bat build
```

产物在 `build/libs/qianhun-1.0.0.jar`。另有一个 `-sources.jar`，那是给 IDE 看源码用的，**不要**放进 `mods`。

如果 `gradlew` 报找不到 Java，就取消 `gradle.properties` 里 `org.gradle.java.home` 那行的注释并指向你的 JDK。

首次构建需要联网拉 Minecraft 与 Fabric 依赖（`maven.fabricmc.net` 在部分网络下较慢，可自行换镜像）。

---

## 说明书

完整流程与细节见 `千魂模组使用说明.txt`。

## 许可

源码保留所有权利。小说原著版权归高铭所有，本模组为非商业同人改编。
