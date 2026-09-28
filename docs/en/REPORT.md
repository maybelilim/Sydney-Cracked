# Sydney Loader (legacy 3.0.0, MC 1.21.4) — Technical Report

Reverse engineering of the legacy Sydney loader (`sydney-loader.jar`).
Date: 2026-09-28. Tooling: javassist offline patching + runtime call hooks.

## 1. Architecture

The loader is a Fabric mod (`preLaunch` entrypoint), pure Java, protected by:

- **Encrypted string pools** — every class carries a private pool of encrypted strings,
  decrypted lazily by a per-class `private static String a(int, int)` rolling-XOR routine
  (two 8-bit keys seeded from the first character, key rotation `x = ((x>>>3 | x<<5) ^ prev)`).
- **`D.i` reflection dispatcher** — all sensitive API calls go through `invokedynamic`
  into `D.i`, which resolves members by encrypted descriptors:
  - `a(long)` → pool index + second-stage XOR with a 6-byte key derived from the long
  - `b(long)` → `Class.forName(...)`
  - `c(long)` → field lookup, `d(long)` → method lookup
  - pool size: 1109 strings
- **Control-flow flattening** — integer state machines with `Random`-derived start states.
- **`Unsafe` tricks** — `D.i` calls `sun.misc.Unsafe::putAddress`; attaching a
  `-javaagent` is detected and leads to an intentional JVM crash
  (`EXCEPTION_ACCESS_VIOLATION` in `jvm.dll`).

### Defeating the agent detection

Do **not** use `-javaagent`. Patch the JAR **offline** instead: rename each
`a(II)Ljava/lang/String;` pool-decryptor to a unique name and add a logging wrapper with
the original name (see `tools/src/Patcher.java`). No instrumentation remains at runtime.

## 2. Authorization — fully broken

### Key file

```
%USERPROFILE%\.sydney\authorization
```

### Format

```
file   = base64url( IV[16] || AES-256-CBC-PKCS5(plaintext, K, IV) )
K      = PBKDF2-HMAC-SHA256(password, salt, 100000, 256)
```

with **hardcoded** constants recovered from the binary:

```
password = "dW0wBAlrg62G0eaJpeDvpJjH2jenDyMKhW6tN5uaHhW3IHvWr5zYnkrwiRH5znjV"
salt     = "JZ1NNWMFKN47jg9mCkAQlIwKDTcNy3Kb"
K        = 05ef8193ceaefb7172bb04e9af2533a987e491dd1cb99f61e908e90e7f06d9f7
```

The loader decrypts the file, checks `!plaintext.isEmpty()` and proceeds. Arbitrary
plaintexts are accepted at this stage. `tools/keygen.ps1` produces valid key files.

### Key derivation details (as observed via instrumentation)

1. read file → UTF-8 string
2. `Base64.getUrlDecoder().decode(...)`
3. `SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")`
4. `PBEKeySpec(password = <hardcoded string>, salt = <hardcoded>, 100000, 256)`
5. `Cipher.getInstance("AES/CBC/PKCS5Padding")`, mode = DECRYPT
6. `IV = decoded[0..15]`, ciphertext = remainder
7. `doFinal(...)` — on `BadPaddingException` the loader returns `""` and shows
   *“Invalid Credentials / You do not have a valid authentication key created.”*

## 3. HWID fingerprint

```
HWID = SHA-256( os.name + os.arch + os.version
              + PROCESSOR_IDENTIFIER + PROCESSOR_ARCHITECTURE
              + PROCESSOR_ARCHITEW6432 + NUMBER_OF_PROCESSORS + MAC )
```

- Anti-VM — MAC prefix blacklist:
  `00:1C:42` (Parallels), `00:0C:29` / `00:05:69` / `00:1C:14` / `00:50:56` (VMware),
  `08:00:27` (VirtualBox), `52:54:00` (QEMU/KVM)
- Anti-debug — JVM input arguments are scanned for:
  `agentlib`, `Xdebug`, `Xnoagent`, `Xrunjdwp`, `javaagent`, `jmxremote`,
  `XBootclasspath`, `verbose`, `DproxySet`, `DproxyHost`, `DproxyPort`,
  `Djavax.net.ssl.trustStore`, `Djavax.net.ssl.trustStorePassword`

## 4. Network protocol

| Property | Value |
|---|---|
| Endpoint | `POST https://www.sydneyclient.net/api/v1/loader/request` |
| Body | `{"authorization":"<blob>"}` |
| Outer cipher | ChaCha20, random 12-byte nonce per request |
| ChaCha20 key | `04df20987d2cab6a2ebb4d71872055cec9a5fc962587f1258cfaa8e1cf52f8a4` |
| Key source | shared secret `SENndfFVaFEuBOiIbPeBkQOLuRnZEd2R4XfvNi2xwoZumH3bVayqN5TDda32ozNL` |
| Inner layer | AES-256-CBC (same K as authorization) over license key + HWID hash |

**Status: dead end.** The endpoint answers `HTTP 404` — the legacy backend has been
removed. The old domain `sydneyclient.xyz` no longer resolves. Consequently the 3.0.0
client cannot be downloaded even with a fully valid key.

## 5. Tooling (see `tools/src/`)

| File | Purpose |
|---|---|
| `Patcher.java` | offline javassist patcher: wraps string-pool decryptors, `D.i` resolvers, all `String`/`byte[]`/`Path`/`SecretKey`-returning statics with logging |
| `HookRuntime.java` | logger (string pools, reflection targets, crypto params incl. PBE passwords/salts, IVs, keys, dispatcher args) |
| `RunLoader.java` | harness that invokes `onPreLaunch` outside Fabric |
| `PatchFL.java` | stubs `FabricLoaderImpl.isDevelopmentEnvironment()` in fabric-loader for headless runs |
| `keygen.ps1` | authorization key generator |

Build & run (Java 21, javassist 3.30):

```powershell
javac --release 21 -cp .;javassist.jar Patcher.java HookRuntime.java -d .
java -cp .;javassist.jar Patcher sydney-loader.jar sydney-loader-hooked.jar HookRuntime
javac --release 21 -cp .;javassist.jar PatchFL.java -d .
java -cp .;javassist.jar PatchFL fabric-loader-0.16.14.jar fabric-loader-patched.jar
java -cp .;sydney-loader-hooked.jar;fabric-loader-patched.jar RunLoader   # hook.log is produced in CWD
```

> Do not commit `hook.log` — it contains your real HWID hash and local paths.

## 6. Reproducing the analysis — lessons learned

1. `-javaagent` triggers an anti-tamper `Unsafe` crash → patch JARs offline.
2. The JVM passes slashed class names to `ClassFileTransformer` — dotted-name prefix
   checks silently match nothing.
3. Pool strings decrypt in two stages; only the final stage yields plaintext.
4. The most informative hooks are the *generic* ones: every static returning
   `String` / `byte[]` / `Path` / `SecretKey`, plus the `D.i` dispatch choke point that
   sees every reflective call with fully materialized arguments.
