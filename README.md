# NEGeyserExtension

[English](README.en.md)

让 **网易版我的世界（中国版基岩版）** 玩家与国际版基岩版玩家、Java 版玩家一起进入同一个使用
**上游 Geyser** 的服务器。

- 上游 Geyser：`2.12.x`（扩展 API `2.12.0`），不修改或替换 Geyser jar
- 支持的网易客户端：协议 `819` / `860` / `898`（对应基岩版 1.21.93 / 1.21.124 / 1.21.130）
- 兼容原版 **Floodgate**：网易玩家的 XUID、UUID 与客户端数据会像普通基岩玩家一样交给 Floodgate
- 国际版基岩玩家和 Java 玩家继续走 Geyser 原版登录、编解码和注册表路径

> 这是社区第三方扩展，并非 GeyserMC、Mojang、微软或网易官方项目。
>
> 本项目主要由 AI 辅助完成，维护者**不会积极维护**；详见
> [AI 辅助说明](#ai-辅助说明)。

## 安装

1. 准备 JDK 21 和 Geyser 2.12.x，并在 Geyser 中启用 RakNet 传输
   （`transport: raknet` 或 `both`）。
2. 把 `NEGeyserExtension.jar` 放进 Geyser 的 `extensions/` 目录。
3. 启动一次服务器，扩展会生成 `extensions/negeyser/config.yml`。
4. 按需修改配置后重启服务器（或执行 `/geyser reload`）。

```
extensions/
├── NEGeyserExtension.jar
└── negeyser/
    ├── config.yml
    └── packs/
        ├── netease/
        └── international/
```

## 原理

网易客户端仍然使用基岩版协议，但在传输层和若干数据包上与上游不同：

1. **RakNet 版本为 8。** 扩展重新绑定 Geyser 的 RakNet 监听器：RakNet 8 使用网易批处理格式，
   初始不压缩，协商后使用 raw deflate；其它 RakNet 版本继续走 Geyser 原版管线。
2. **登录链不同。** 网易客户端发送由网易根证书签发的三段式 JWT 链，扩展在本地完成校验
   （不依赖微软 discovery），随后进行标准 Bedrock 加密握手。房间/跨服中转可能只转发最后一段
   自签名 JWT，扩展提供默认关闭的显式开关支持这种情况。
3. **部分数据包结构不同。** 扩展为 819/860/898 注册网易版编解码器，覆盖 `PlayerAuthInput`、
   文本、命令请求、动画、资源包栈、容器打开、物品栏选项、生物群系定义和附魔选项，并注册
   网易专有包：`ConfirmSkin`、`NetEaseJson`、`PyRpc`、`StoreBuySuccess`、
   `SetDimensionLocalTime`。
4. **内容投影。** 网易会话内部按 Geyser 最新的 26.x 协议运行，但把内容投影回客户端自己的注册表：
   方块使用持久化哈希，物品注册表按网易表重建，配方/创造栏按客户端版本过滤，未知物品和方块
   使用近似替代。国际版会话不受影响。

扩展还处理了测试中发现的网易客户端差异：

- 网易容器槽位枚举在 `RECIPE_ITEMS` 之后整体偏移一位，请求和响应都做了双向转换；
- 抑制重复的玩家列表 ADD + ConfirmSkin，皮肤变化改为 REMOVE → ADD → Confirm，避免实体被隐藏；
- 把 Geyser 的 `geyser_custom:obsidian` / `crying_obsidian` 数据驱动方块映射回原版方块
  （之前会显示为石头）；
- 定位条跟随 Java 的 `locator_bar` gamerule；
- 可选的按客户端类型分流资源包。

## 前置要求

- JDK 21
- Geyser 2.12.x，且启用 RakNet 传输（`transport: raknet` 或 `both`）
- 网易版客户端 1.21.93、1.21.124 或 1.21.130
- 可选：原版 Floodgate（无需修改）

不支持网易 PC 客户端，也不支持仅启用 NetherNet 的传输。

### 入驻网易

如果计划入驻网易（在网易版平台运营服务器，或使用网易房间/跨服等能力），推荐使用
**布吉岛Geyser**——本扩展开发与测试所使用的网易 Geyser 分支，源码位于
<https://github.com/mc-dreamland/>。

## 配置

```yaml
# 只允许网易客户端。false（默认）= 国际版、网易版、Java 版都可以进。
only-netease-clients: false

# only-netease-clients 为 true 时，向国际版客户端显示的消息。
disconnect-message: "This server only accepts NetEase clients."

# 输出网易登录/数据包调试日志。
debug-mode: false

# 要求网易登录证书的环境标识为 obt。一般保持 false。
require-obt-environment: false

# 接受单段自签名网易证书链（网易房间/跨服中转）。
# 仅在玩家通过受信任的中转服务接入时开启；默认关闭。
allow-single-chain: false

# 允许发送单段链登录的来源 IP，逗号分隔，例如 "10.0.0.5"。
# 为空时接受任意来源并输出警告；此时不要把基岩端口直接暴露公网。
trusted-relay-addresses: ""

# 单段链会话所有资源包分片发送完成后，等待多久再发送 ResourcePackStack（毫秒）。
single-chain-pack-stack-delay-ms: 1000

# 网易客户端资源包分片之间的延迟（毫秒）。设为 0 关闭。
resource-pack-chunk-delay-ms: 200

# 预留给未来的网易 PC 支持，目前无效果。
allow-pc-clients: false

# 过滤掉网易客户端版本中不存在的物品定义和创造栏条目。
filter-unknown-items: true

# false：完整透传物品 NBT（书、染料、潜影盒、纹饰、耐久、附魔等）。
# true：只保留 display + extra-item-nbt 中的键（保守模式）。
strip-item-nbt: false

# strip-item-nbt 为 true 时额外保留的组件/标签，逗号分隔，例如 "Damage,ench"。
extra-item-nbt: ""

# 将网易玩家名中的中文转换为拼音，并裁剪为 Java 用户名上限 16 字符。
translate-netease-names: false
```

### 网易房间跨服（单段证书链）

网易房间/跨服中转登录时，客户端可能只上送一段自签名 JWT，而不是正常的三段链。默认会拒绝；
开启 `allow-single-chain: true` 后，扩展会用该 JWT 自身的 `x5u` 公钥验证签名，跳过网易根证书
检查，并要求 `extraData` 至少包含 `netease_uid` 或 `netease_uuid`。`require-obt-environment`
仍然生效。

该功能只应来自受信任的中转服务：请把中转服务器 IP 写入 `trusted-relay-addresses`；列表为空时
会接受任意来源并输出警告，此时不要把基岩端口直接暴露到公网。客户端数据 JWT 始终会用身份公钥
验签，不会跳过。

三段链仍走原来的逐级验签、网易根证书检查和身份取值逻辑，国际版客户端完全不受影响。

单段链会话还会启用资源包 early-3 处理：当客户端在尚未下载资源包时直接回复
`HAVE_ALL_PACKS`（状态 3），扩展会先发送每个包的 `ResourcePackDataInfo`，等待分片下载完成，
再延迟 `single-chain-pack-stack-delay-ms` 后发送 `ResourcePackStack`。

### 资源包分流

Geyser 自带的 `packs/` 文件夹保持统一：其中的资源包仍然发给所有客户端。扩展额外提供两个
按客户端类型分流的目录（相对于扩展数据目录）：

```
extensions/negeyser/
├── config.yml
└── packs/
    ├── netease/         # 只发给网易版客户端（RakNet v8）
    └── international/   # 只发给国际版基岩客户端
```

- 两个目录在扩展启动时自动创建；放入 `.zip` 或 `.mcpack` 文件即可。
- 分流目录中的包以 `HIGH` 优先级注册，可覆盖 `packs/` 中修改同一资源的统一资源包。
- 添加/替换资源包后需要重启或执行 `/geyser reload`；已连接的会话不受影响。
- 同一个包（相同 manifest UUID）不要同时放在 Geyser 的 `packs/` 和分流目录中。

## Floodgate

不需要替换或修改 Floodgate：

- Geyser 的 `auth-type` 保持 `floodgate`；
- 网易玩家的数字 uid 会写入 `AuthData.xuid`，Floodgate 用它生成 Java 端 UUID；
- 网易登录 JWT 中的客户端数据（游戏版本、设备、语言、皮肤等）会校验后写入 `BedrockClientData`；
- 国际版基岩玩家仍走原版 Geyser + Floodgate 流程。

## 构建

需要 JDK 21：

```bash
./gradlew clean build
```

产物：`build/libs/NEGeyserExtension.jar`。

Geyser、Cloudburst Protocol、Netty、fastutil、jose4j、Gson 均由 Geyser 运行时提供，扩展不打包。
唯一打包的依赖是用于中文名转拼音的可选库 [pinyin4j](https://github.com/belerweb/pinyin4j)（BSD）。

## 排查

把扩展配置中的 `debug-mode` 设为 `true`，日志会输出 `[NETEASE-TRACE]` 管线阶段，以及
`[NETEASE-ITEM]`、`[NETEASE-RECIPE]`、`[NETEASE-REQ]` / `[NETEASE-RESP]`、
`[NETEASE-SKIN]`、`[NETEASE-PACKS]`、`[NETEASE-GAMERULE]`、`[NETEASE-BLOCK]` 等诊断信息。
公开反馈问题时请不要附带令牌、证书、服务器地址或玩家名称。

## 已知限制

- 只支持使用 RakNet 的网易客户端（Windows/Android/iOS）；网易 PC 客户端使用另一套协议。
- 网易客户端版本必须为 1.21.93、1.21.124 或 1.21.130，其它版本会提示版本不匹配。
- 较新的 Java 内容会被降级：未知方块显示为石头，未知物品替换为带原标识符的纸；新实体、
  生物群系和配方可能不完整。
- 书与笔的编辑/签名暂时不能正确保存。
- 实体 metadata 尚未按客户端版本过滤；新实体类型或 metadata 可能表现异常。
- 收纳袋/动态容器（DYNAMIC_CONTAINER）的堆叠请求尚未完全验证。
- 不支持仅 `transport: nethernet`；请使用 `raknet` 或 `both`。
- `/geyser reload` 会重新绑定监听器，出现异常时完整重启更稳妥。
- 扩展通过 `sun.misc.Unsafe` 注入会话上游包装器和方块映射副本；升级 Geyser 大版本后需重新
  编译并验证。
- 未实现网易皮肤审核 HTTP 接口；`ConfirmSkin` 会直接标记皮肤有效，需要严格反作弊时请在
  业务层自行处理。

## AI 辅助说明

本项目部分代码与文档在 AI 编程工具（包括 Claude Code）的辅助下完成。维护者会尽量审查
AI 辅助修改并在真实客户端/服务器上验证，但 AI 输出仍可能存在错误。请在部署前自行验证，
并反馈发现的问题。

由于本项目主要由 AI 辅助完成，维护者**不会积极维护本项目**。Issue 和 PR 可能响应较慢或
无法处理。欢迎自行 fork 并继续开发。

## 许可证与致谢

扩展源码使用 MIT 许可证，详见 [LICENSE](LICENSE) 与
[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。

`src/main/resources/netease/` 中的第三方/协议数据来自 Nukkit-MOT、社区网易 Geyser
分支和网易客户端注册表，受各自条款约束，**不**包含在本项目的 MIT 授权中。

开发与测试所使用的网易 Geyser 分支（布吉岛Geyser）与网易版 Cloudburst Protocol
分支来自 <https://github.com/mc-dreamland/>。参考实现
[LoHJG/GeyserNetease](https://github.com/LoHJG/GeyserNetease) 也提供了帮助。
