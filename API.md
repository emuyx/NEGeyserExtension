# 给其他 Geyser Extension 的 API

NEGeyserExtension 暴露了一个只读 API，其他 Geyser 扩展可以用它判断玩家是否来自网易客户端，并读取网易账号信息。

API 入口：

```java
io.github.negeyser.api.NEGeyserApi
io.github.negeyser.api.NeteasePlayerInfo
```

## 1. 在 extension.yml 中声明依赖

消费者扩展必须依赖 `negeyser`，这样 Geyser 会先加载 NEGeyserExtension，并让两个扩展共享同一份 API 类：

```yaml
id: myextension
name: MyExtension
main: com.example.MyExtension
api: 2.12.0
version: 1.0.0

dependencies:
  negeyser:
    required: true
```

默认就是 `required: true`；保持默认加载顺序即可。`api` 至少要是 `2.9.0`（Geyser 的扩展依赖机制要求）。

## 2. 编译依赖

### 方式 A：GitHub Release jar（推荐）

把 Release 里的 `NEGeyserExtension-<version>.jar` 放到消费者项目的 `libs/`：

```kotlin
dependencies {
    compileOnly(files("libs/NEGeyserExtension-2.0.0.jar"))
}
```

### 方式 B：JitPack（如果仓库能被 JitPack 构建）

```kotlin
repositories {
    maven("https://jitpack.io")
}

dependencies {
    compileOnly("com.github.emuyx:NEGeyserExtension:v2.0.0")
}
```

### 方式 C：本地 Maven

```bash
mvn install:install-file \
  -Dfile=NEGeyserExtension-2.0.0.jar \
  -DgroupId=io.github.negeyser \
  -DartifactId=NEGeyserExtension \
  -Dversion=2.0.0 \
  -Dpackaging=jar
```

```kotlin
dependencies {
    compileOnly("io.github.negeyser:NEGeyserExtension:2.0.0")
}
```

> **必须使用 `compileOnly` / `provided`，不要 `implementation`，也不要 shade/bundle `io.github.negeyser.api`。**  
> API 实例是一个静态单例，类必须由 NEGeyserExtension 的扩展 classloader 加载；如果消费者打包了自己的副本，会得到另一个静态实例，`isAvailable()` 永远为 false。

## 3. 代码示例

```java
import io.github.negeyser.api.NEGeyserApi;
import io.github.negeyser.api.NeteasePlayerInfo;
import org.geysermc.event.subscribe.Subscribe;
import org.geysermc.geyser.api.event.bedrock.SessionJoinEvent;

public final class MyExtension implements Extension {

    @Subscribe
    public void onSessionJoin(SessionJoinEvent event) {
        if (!NEGeyserApi.isAvailable()) {
            return; // NEGeyserExtension 未安装或启动失败
        }

        NeteasePlayerInfo info = NEGeyserApi.get().getPlayerInfo(event.connection());
        if (info == null) {
            return; // 普通基岩版玩家，或网易认证尚未完成
        }

        System.out.println("NetEase player: " + info.neteaseName()
            + " uid=" + info.neteaseUid()
            + " protocol=" + info.protocolVersion()
            + " singleChain=" + info.singleChain());
    }
}
```

`SessionJoinEvent` 发生在网易认证完成之后，因此此时 `getPlayerInfo` 已经可用。不要在 `SessionInitializeEvent` 里读取，那时网易认证可能还没完成。

## 4. API 方法

```java
boolean available = NEGeyserApi.isAvailable();
NEGeyserApi api = NEGeyserApi.get(); // 未安装时抛 IllegalStateException

boolean netease = api.isNetease(geyserSession);
boolean netease2 = api.isNetease(geyserConnection);

NeteasePlayerInfo info = api.getPlayerInfo(geyserSession);
NeteasePlayerInfo info2 = api.getPlayerInfo(geyserConnection);
NeteasePlayerInfo info3 = api.getPlayerInfo(uuid); // Java/Geyser UUID 或网易 identity UUID

Collection<NeteasePlayerInfo> online = api.getOnlinePlayers();
```

非网易玩家、认证尚未完成、或 UUID 不存在时返回 `null`。`getOnlinePlayers()` 返回不可变快照。

## 5. `NeteasePlayerInfo` 字段

| 方法 | 说明 |
|---|---|
| `uuid()` | Geyser/Java 使用的玩家 UUID；可能为 `null`（极早期） |
| `neteaseIdentity()` | 网易证书链中的 identity UUID |
| `name()` | Geyser/Java 看到的玩家名；开启 `translate-netease-names` 时为拼音名 |
| `neteaseName()` | 网易原始显示名 |
| `neteaseUid()` | 网易账号数字 UID |
| `xuid()` | 网易登录链中的 XUID，可能为空字符串 |
| `environment()` | 网易登录链 environment 字段 |
| `protocolVersion()` | 网易客户端协议号（819 / 860 / 898） |
| `singleChain()` | 是否使用单段（relay）证书链登录 |
| `issuedAt()` | 网易登录链签发时间 |

这些都是认证成功时生成的不可变快照。会话断开后会自动从在线列表移除。

## 6. 生命周期与线程安全

- API 在 NEGeyserExtension 启动/重载时安装；如果没有安装，`isAvailable()` 返回 `false`，`get()` 抛 `IllegalStateException`。
- 认证成功后才注册玩家；断开时自动移除。
- `NeteasePlayerInfo` 不可变，可以安全跨线程读取。
- 其他扩展不需要手动清理缓存；按 `GeyserSession`/UUID 查询即可。

## 7. 版本兼容

- `NEGeyserApi.API_VERSION` 是二进制 API 版本，当前为 `1`。
- API 变更会写入 `CHANGELOG.md`；不兼容变更会增加 `API_VERSION`。
- NEGeyserExtension 本身要求 **Geyser 2.12.1+**，消费者扩展的 `api` 建议写 `2.12.0`。
