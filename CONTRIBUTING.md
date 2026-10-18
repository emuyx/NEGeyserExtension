# Contributing

Thanks for your interest in NEGeyserExtension.

## Requirements

- JDK 21
- Network access to `repo.opencollab.dev` and Maven Central (or a Maven mirror)
- A Geyser 2.12.x runtime for manual testing

## Building

```bash
./gradlew clean build
```

The jar is written to `build/libs/NEGeyserExtension.jar`.

## Testing

There is no automated test suite yet. Before opening a pull request:

1. Build with JDK 21.
2. Place the jar in `extensions/` of a Geyser 2.12.x server.
3. Start the server and confirm there are no extension load errors.
4. If the change affects NetEase clients, test with a real NetEase client and include
   the relevant `[NETEASE-*]` debug lines (`debug-mode: true`).

Please do not include tokens, certificates, server addresses, player names or other
personal data in issues, logs or test fixtures.

AI-assisted contributions are welcome. Disclose significant AI-generated changes in
the pull request and verify them yourself before submitting.

> **Maintenance note:** this project is largely AI-assisted and is not actively
> maintained. Issues and pull requests may receive little or no response. Forking the
> repository and continuing development independently is encouraged.

## Code style

- Java 21, 4-space indentation, LF line endings (see `.editorconfig`).
- Keep the extension free of direct `it.unimi.dsi.fastutil` references and of
  Adventure `Key` types; Geyser platforms relocate both. Prefer `java.util`
  collections, reflection or `Unsafe` where Geyser's API exposes relocated types.
- Every new file needs the MIT license header used by the existing sources.

## Pull requests

- Keep changes focused and explain the protocol/client behaviour you observed.
- Mention the NetEase protocol version(s) tested (819/860/898).
- Update `README.md` (Chinese, default) / `README.en.md` when adding configuration
  options.
