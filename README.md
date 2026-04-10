# Lantern Android 🔦

A lightweight, zero-dependency mDNS/DNS-SD service discovery library for Android (API 28+), built on raw multicast UDP sockets.

Lantern shines a light on your local network — finding services without the bugs that plague every other Android discovery option.

---

## Why not NsdManager?

`NsdManager` is Android's built-in mDNS API. On paper it's the right tool. In practice it has a fundamental architectural flaw: it assembles resolved `NsdServiceInfo` objects from **separately cached DNS records** — the SRV record (hostname + port), TXT record (metadata), and A record (IP address) are fetched and stored independently by the system mDNS daemon. When multiple devices are on the same network, the daemon can return a **TXT record from device A paired with an A record from device B**, giving you a `ServiceInfo` object that points to the wrong host.

This is not a fringe case. It has been [independently reported](https://github.com/COPELABS-SITI/ndn-opp/issues/2) across multiple projects targeting IoT and peer-to-peer scenarios — exactly the class of use case `NsdManager` was designed for.

Additional `NsdManager` problems:

- `resolveService()` can only resolve **one service at a time**. Concurrent `onServiceFound` callbacks must be queued manually or you get `FAILURE_ALREADY_ACTIVE`.
- `resolveService()` is deprecated at API 34. The replacement (`ServiceInfoCallback`) is not available below API 34, requiring a permanent SDK version branch in your code.
- Callbacks arrive on arbitrary threads with no threading guarantees documented.
- Discovery and resolution are two separate async operations with no atomic pairing, making consistent state management difficult.

---

## Why not the alternatives?

| Library | Problem |
|---|---|
| **RxDNSSD** | Archived. The `Bindable` mode (which delegates to the system daemon) throws `SERVICENOTRUNNING` on Android 12+. The `Embedded` mode works but ships a full NDK-compiled mDNSResponder binary (~1.5 MB), and the project has had no maintenance since 2022. |
| **JmDNS** | Active and pure Java, but known to be slow, has had stability issues on Android, and requires careful `MulticastLock` and interface-binding setup that is easy to get wrong. It also carries significantly more code than most Android use cases need. |
| **mdnsjava** | More RFC-complete than JmDNS, but effectively dormant and untested on modern Android API levels. |
| **coucou_android** | Abandoned, and internally wraps `NsdManager` — the original bug is still present. |

---

## How Lantern solves the problem

The root cause of the `NsdManager` IP/TXT mismatch is the **separation of record caching from record assembly**. Lantern eliminates this entirely.

A well-behaved mDNS advertiser (including Android's own `NsdManager` registration, avahi, and most embedded device stacks) sends PTR, SRV, TXT, and A records together in a **single UDP datagram** — either as an unsolicited announcement or in response to a PTR query. Because Lantern reads raw multicast datagrams and assembles `ServiceInfo` objects only from records found **within the same packet**, there is no cache and no daemon to mix records across devices. A TXT record and an A record in the same datagram are guaranteed to have come from the same physical device.

```
One UDP datagram from a device on the network:
  ├── PTR  _canvas._tcp.local  →  CanvasGW-a1b2._canvas._tcp.local
  ├── SRV  CanvasGW-a1b2...    →  host: gateway-a1b2.local  port: 8080
  ├── TXT  CanvasGW-a1b2...    →  [uuid=..., network_id=...]
  └── A    gateway-a1b2.local  →  192.168.1.42

All four records assembled into one ServiceInfo. No cross-device mixing possible.
```

---

## Features

- **Correct IP resolution** — records assembled per-datagram, not from a shared cache
- **No NDK, no JNI** — pure Kotlin/Java, trivial to audit and vendor
- **No third-party dependencies** — only `kotlinx-coroutines-android`
- **Multi-interface support** — joins the mDNS multicast group on all valid network interfaces
- **Raw TXT records** — byte arrays passed through as-is; you control decoding
- **Configurable query repeat** — send PTR queries on an interval for long-running discovery sessions
- **Clean lifecycle** — `start()` / `stop()`, coroutine-scoped internally, main-thread callbacks

---

## Installation

Add the `:lantern-android` module to your project, then in your app's `build.gradle.kts`:

```kotlin
dependencies {
    implementation(project(":lantern-android"))
}
```

The library's `AndroidManifest.xml` declares the required permissions automatically via manifest merge:

```xml
<uses-permission android:name="android.permission.CHANGE_WIFI_MULTICAST_STATE"/>
<uses-permission android:name="android.permission.ACCESS_WIFI_STATE"/>
```

No runtime permission prompts are required — both are install-time permissions.

---

## Usage

### Basic discovery

```kotlin
val config = LanternConfig(serviceType = "_canvas._tcp.local.")

val lantern = LanternDiscovery(
    context = context,
    config = config,
    listener = object : LanternServiceListener {
        override fun onServiceFound(service: ServiceInfo) {
            val uuid      = service.txtRecords["uuid"]
            val networkId = service.txtRecords["network_id"]
            Log.d("Lantern", "Found: ${service.instanceName} @ ${service.host}:${service.port}")
        }
        override fun onServiceLost(instanceName: String) { /* handle removal */ }
        override fun onError(cause: Exception) { Log.e("Lantern", "Error", cause) }
    }
)

lantern.start()

// Stop when done (e.g. in onDestroy / viewModel.onCleared)
lantern.stop()
```

### Filter by TXT attribute

```kotlin
override fun onServiceFound(service: ServiceInfo) {
    val networkId = service.txtRecords["network_id"] ?: return
    if (networkId != myNetworkId) return
    // this device belongs to our network
}
```

### Repeated queries (long-running discovery)

```kotlin
val config = LanternConfig(
    serviceType = "_canvas._tcp.local.",
    queryIntervalMs = 10_000L,  // re-query every 10 seconds
)
```

### Service type format

The service type must follow the DNS-SD format and **must end with a trailing dot**:

```kotlin
// correct
LanternConfig(serviceType = "_myservice._tcp.local.")
LanternConfig(serviceType = "_abcd._tcp.local.")

// will throw — missing trailing dot
LanternConfig(serviceType = "_myservice._tcp.local")

// will throw — missing .local.
LanternConfig(serviceType = "_myservice._tcp")
```

The library sends the service type verbatim in the PTR query, so whatever is between the underscores (`_myservice`, `_abcd`, etc.) doesn't matter — as long as it matches what the advertiser is broadcasting.

---

### Reading binary TXT values

For the rare case where TXT values are binary rather than UTF-8 text, use `rawTxtRecords` directly:

```kotlin
override fun onServiceFound(service: ServiceInfo) {
    val raw: ByteArray? = service.rawTxtRecords
        .firstOrNull { it.startsWith("my_key=") }
        ?.copyOfRange("my_key=".length, it.size)
}

private fun ByteArray.startsWith(prefix: String): Boolean {
    val p = prefix.toByteArray(Charsets.UTF_8)
    return size >= p.size && p.indices.all { this[it] == p[it] }
}
```

---

## API reference

### `LanternConfig`

| Parameter | Type | Default | Description |
|---|---|---|---|
| `serviceType` | `String` | — | DNS-SD service type, must end with `.` (e.g. `_http._tcp.local.`) |
| `queryIntervalMs` | `Long` | `0` | Repeat PTR query interval in ms. `0` = query once on `start()`. |
| `socketTimeoutMs` | `Int` | `5000` | UDP socket receive timeout. Controls how quickly `stop()` unblocks. |

### `ServiceInfo`

| Property | Description |
|---|---|
| `instanceName: String` | Full DNS-SD instance name |
| `host: String` | IPv4 address |
| `port: Int` | Service port from SRV record |
| `txtRecords: Map<String, String>` | TXT attributes decoded as UTF-8 key=value pairs; the go-to for typical string values |
| `rawTxtRecords: List<ByteArray>` | Raw TXT attribute byte arrays exactly as received; use when values are binary |

### `LanternServiceListener`

```kotlin
interface LanternServiceListener {
    fun onServiceFound(service: ServiceInfo)   // called on main thread
    fun onServiceLost(instanceName: String)    // called on main thread
    fun onError(cause: Exception)              // called on main thread
}
```

### `LanternDiscovery`

```kotlin
fun start()   // acquires MulticastLock, opens socket, sends initial PTR query
fun stop()    // cancels coroutine scope, closes socket, releases MulticastLock
```

---

## Requirements

- Android API 28+
- Wi-Fi connection (mDNS is link-local; cellular networks do not support multicast)
- `kotlinx-coroutines-android`

---

## How it works

```
start()
  │
  ├── Acquire WifiManager.MulticastLock
  │     Without this, Android's kernel silently drops
  │     incoming multicast packets on most devices.
  │
  ├── LanternSocket.open()
  │     MulticastSocket bound to port 5353
  │     Joins 224.0.0.251 on all up+multicast-capable interfaces
  │
  ├── LanternQuerySender.send()
  │     Sends DNS PTR query for <serviceType> to 224.0.0.251:5353
  │     Solicits immediate responses from all matching devices
  │
  └── Receive loop (Dispatchers.IO)
        For each incoming datagram:
          LanternPacketParser.parse(data, len, srcIp)
            ├── Skip non-response packets (QR bit = 0)
            ├── Parse all records in answer + additional sections
            ├── For each PTR matching serviceType:
            │     find SRV with same instance name  ──┐
            │     find TXT with same instance name  ──┤ all from same packet
            │     find A   matching SRV target      ──┘
            └── Emit ServiceInfo if all three found
```

---

## Limitations

- **IPv4 only** — AAAA (IPv6) records are not currently assembled. The datagram source address fallback also yields an IPv4 address. IPv6 support can be added by handling `TYPE_AAAA = 28` in `LanternPacketParser`.
- **No TTL expiry** — `onServiceLost` fires on clean shutdowns via mDNS goodbye packets (TTL=0 on the PTR record). It will **not** fire if a gateway loses power or drops off the network abruptly. To detect unclean loss, re-query periodically with `queryIntervalMs` and reconcile against the previous result set.
- **No service registration** — Lantern is discovery-only. Registration is handled by the advertiser side (the device being discovered).

---

## License

MIT — see [LICENSE](LICENSE)
