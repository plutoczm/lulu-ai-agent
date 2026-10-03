# Tools

Local third-party executables used by development workflows live under this directory.

## Cloudflared

`tools/cloudflared/` is the expected local location for Cloudflare Tunnel binaries used by
`scripts/dev/start-public-demo.ps1`.

The binaries themselves are intentionally Git-ignored because they are large third-party artifacts.
Install or copy the appropriate executable locally:

```text
tools/cloudflared/cloudflared.exe           # Windows
tools/cloudflared/cloudflared-linux-amd64   # Linux, when needed
```

Application source code must not be added to `tools/`.
