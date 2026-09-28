# Sydney Loader Research (legacy 3.0.0, MC 1.21.4)

Reverse-engineering notes and tooling for the **legacy Sydney client loader**
(`sydney-loader.jar`, Minecraft 1.21.4 / Fabric 0.16.14 era, client version 3.0.0).

> [!WARNING]
> The legacy backend (`/api/v1/loader/request`) has been **shut down (HTTP 404)** and the
> old domain `sydneyclient.xyz` no longer resolves. Even with a fully valid authorization
> key, the 3.0.0 client can no longer be downloaded. This repository documents the
> protection scheme for research purposes only.

## What was accomplished

- [x] Full deobfuscation of the loader's protection scheme (string pools, `D.i` dispatcher)
- [x] Complete break of the local authorization scheme (`.sydney\authorization`)
- [x] Working key generator — the loader accepts generated keys
- [x] HWID fingerprint scheme and anti-VM / anti-debug checks documented
- [x] Network protocol reconstructed (2-layer encryption, ChaCha20 + AES)
- [x] Runtime instrumentation tooling (javassist offline patcher + call hooks)

## Repository layout

```
├── README.md / README.ru.md      # this file (EN / RU)
├── docs/
│   ├── en/REPORT.md              # full technical report (English)
│   └── ru/REPORT.md              # полный технический отчёт (русский)
└── tools/
    ├── keygen.ps1                # authorization key generator (EN)
    ├── keygen.ru.ps1             # генератор ключей (RU)
    └── src/                      # research tooling sources (see docs)
```

**No vendor binaries are distributed here.** The patched loader/fabric-loader JARs and
trace logs must be produced locally from files you legally possess — see the report and
`.gitignore`.

## Quick facts

| Item | Value |
|---|---|
| Key file | `%USERPROFILE%\.sydney\authorization` |
| Format | `base64url( IV[16] ‖ AES-256-CBC-PKCS5(plaintext, K, IV) )` |
| KDF | PBKDF2-HMAC-SHA256, 100 000 iterations, 256-bit — **hardcoded** password & salt |
| HWID | `SHA-256(os.name, os.arch, os.version, PROCESSOR_*, MAC)` |
| Anti-VM | MAC prefix blacklist: Parallels, VMware, VirtualBox, QEMU |
| Anti-debug | JVM argument inspection (`javaagent`, `Xdebug`, ...) |
| C2 endpoint | `POST /api/v1/loader/request` — **dead (404)** |

## Legal

Distributed under the [MIT license](LICENSE). Provided as-is for interoperability and
security research. No proprietary binaries are included.
