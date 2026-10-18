# Security policy

## Reporting a vulnerability

Please report security issues privately through GitHub's
"Report a vulnerability" (Security Advisories) feature on this repository. Do not open a
public issue for authentication bypasses, remote crashes, or data exposure.

Useful details to include:

- Geyser version and extension version
- NetEase client version/protocol (819/860/898)
- Relevant `debug-mode` log lines with tokens, certificates and player names removed
- Whether `allow-single-chain` is enabled

## Deployment notes

- `allow-single-chain` is disabled by default. When enabled, restrict it with
  `trusted-relay-addresses`; an empty list accepts any source and only logs a warning.
- Do not expose the Bedrock/RakNet port directly to the public internet when accepting
  single-chain logins from an untrusted network.
- The extension validates the NetEase certificate chain locally but does not perform
  NetEase's skin moderation HTTP check. `ConfirmSkin` marks skins as valid.
- Logs can contain player names and connection metadata. Redact them before sharing.
