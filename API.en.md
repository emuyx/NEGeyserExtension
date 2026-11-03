# API for other Geyser extensions

NEGeyserExtension exposes a read-only API that lets other Geyser extensions detect NetEase
players and read their NetEase account information.

Entry points:

```java
io.github.negeyser.api.NEGeyserApi
io.github.negeyser.api.NeteasePlayerInfo
```

## 1. Declare the extension dependency

The consuming extension must depend on `negeyser` so Geyser loads NEGeyserExtension first and
both extensions share the same API classes:

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

`required` defaults to `true`; the default load order is already "before".

## 2. Compile-time dependency

### Option A: GitHub release jar (recommended)

Put `NEGeyserExtension-<version>.jar` from the GitHub release into `libs/`:

```kotlin
dependencies {
    compileOnly(files("libs/NEGeyserExtension-2.0.0.jar"))
}
```

### Option B: JitPack (if JitPack can build the repository)

```kotlin
repositories {
    maven("https://jitpack.io")
}

dependencies {
    compileOnly("com.github.emuyx:NEGeyserExtension:v2.0.0")
}
```

### Option C: local Maven

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

> **Use `compileOnly` / `provided`. Do not use `implementation` and do not shade or bundle
> `io.github.negeyser.api`.** The API instance is a static singleton owned by
> NEGeyserExtension's extension class loader. Bundling a second copy creates a separate
> singleton and `isAvailable()` will always be `false`.

## 3. Example

```java
import io.github.negeyser.api.NEGeyserApi;
import io.github.negeyser.api.NeteasePlayerInfo;
import org.geysermc.event.subscribe.Subscribe;
import org.geysermc.geyser.api.event.bedrock.SessionJoinEvent;

public final class MyExtension implements Extension {

    @Subscribe
    public void onSessionJoin(SessionJoinEvent event) {
        if (!NEGeyserApi.isAvailable()) {
            return; // NEGeyserExtension is not installed
        }

        NeteasePlayerInfo info = NEGeyserApi.get().getPlayerInfo(event.connection());
        if (info == null) {
            return; // regular Bedrock player, or NetEase auth not complete
        }

        System.out.println("NetEase player: " + info.neteaseName()
            + " uid=" + info.neteaseUid()
            + " protocol=" + info.protocolVersion()
            + " singleChain=" + info.singleChain());
    }
}
```

`SessionJoinEvent` fires after NetEase authentication, so `getPlayerInfo` is available there.
Do not query in `SessionInitializeEvent`; NetEase authentication may not be complete yet.

## 4. Methods

```java
boolean available = NEGeyserApi.isAvailable();
NEGeyserApi api = NEGeyserApi.get(); // throws if not installed

boolean netease = api.isNetease(geyserSession);
boolean netease2 = api.isNetease(geyserConnection);

NeteasePlayerInfo info = api.getPlayerInfo(geyserSession);
NeteasePlayerInfo info2 = api.getPlayerInfo(geyserConnection);
NeteasePlayerInfo info3 = api.getPlayerInfo(uuid); // Geyser/Java UUID or NetEase identity UUID

Collection<NeteasePlayerInfo> online = api.getOnlinePlayers();
```

Returns `null` for non-NetEase players, before NetEase authentication completes, or for an
unknown UUID. `getOnlinePlayers()` returns an immutable snapshot.

## 5. `NeteasePlayerInfo` fields

| Method | Description |
|---|---|
| `uuid()` | Geyser/Java player UUID; may be `null` very early |
| `neteaseIdentity()` | Identity UUID from the validated NetEase chain |
| `name()` | Player name used by Geyser/Java (transliterated if enabled) |
| `neteaseName()` | Original NetEase display name |
| `neteaseUid()` | Numeric NetEase account UID |
| `xuid()` | NetEase XUID, may be empty |
| `environment()` | NetEase login-chain environment |
| `protocolVersion()` | NetEase protocol version (819 / 860 / 898) |
| `singleChain()` | Whether the session used a single-entry relay chain |
| `issuedAt()` | NetEase login-chain issue timestamp |

## 6. Lifecycle and threading

- The API is installed on startup/reload; if unavailable, `isAvailable()` is `false` and
  `get()` throws `IllegalStateException`.
- Players are registered after successful NetEase authentication and removed on disconnect.
- `NeteasePlayerInfo` is immutable and safe to read from any thread.

## 7. Versioning

- `NEGeyserApi.API_VERSION` is the binary API version (currently `1`).
- API changes are documented in `CHANGELOG.md`; incompatible changes bump `API_VERSION`.
- NEGeyserExtension requires **Geyser 2.12.1+**; consumers should declare `api: 2.12.0`.
