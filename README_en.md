<div align="center">

# Stellar
A deeply customized fork of Shizuku that lets apps access system-level APIs via ADB or Root

[![GitHub Stars](https://img.shields.io/github/stars/roro2239/Stellar?style=flat-square&logo=github&logoColor=white&color=181717&cacheSeconds=0)](https://github.com/roro2239/Stellar/stargazers)
[![GitHub Forks](https://img.shields.io/github/forks/roro2239/Stellar?style=flat-square&logo=github&logoColor=white&color=181717)](https://github.com/roro2239/Stellar/forks)
[![GitHub Issues](https://img.shields.io/github/issues/roro2239/Stellar?style=flat-square&logo=github&logoColor=white&color=e74c3c)](https://github.com/roro2239/Stellar/issues)
[![GitHub Release](https://img.shields.io/github/v/release/roro2239/Stellar?style=flat-square&logo=github&logoColor=white&color=28a745)](https://github.com/roro2239/Stellar/releases)

---
Official Community:
[![QQ Group](https://img.shields.io/badge/QQ-Group%201-12B7F5?style=flat-square&logo=qq&logoColor=white)](https://qm.qq.com/cgi-bin/qm/qr?k=bIpIHQX12Kajh951zELULlF5FN6zeN0y&jump_from=webapi&authKey=Kf6RnfWG1o7whQIi20Uz+X6/dzf/D6/TzED25Pyb0N5td/eVClgysJXgPYnbZhr5)(A Telegram group may be provided in the future)

Language: English | [中文](README.md)

</div>

## Overview

Stellar is a deeply customized fork of [Shizuku](https://github.com/RikkaApps/Shizuku), designed to give developers a more flexible and powerful privileged API framework. Once the service is started via ADB wireless debugging or Root, apps can call APIs that require system-level permissions — without needing Root themselves.

## Key Features

Stellar introduces the following core improvements over the original Shizuku:

### Enhanced Permission System

- **Brand-new permission architecture** — ditches the single-permission model in favor of a granular, multi-dimensional permission management system
- **Tiered permission control**:
  - `stellar` — core API access, grants the ability to call basic service functions
  - `follow_stellar_startup` — startup-follow permission, binds an app's lifecycle to the Stellar service
- **Smart permission callbacks** — enhanced callback mechanism lets clients know exactly what kind of grant they received (permanent vs. one-time)
- **Complete permission management API** — full set of query, request, and revoke interfaces for complex business scenarios

### Startup & Service Optimizations

- **Boot startup** — apps can attempt to auto-start at boot via boot broadcasts, accessibility service, or Root; supports pre-warming for offline startup
- **Follow-startup** — apps can register as companion processes of the Stellar service, waking up automatically when the service starts
- **Dual-process watchdog** — an optional daemon process lets Stellar and the daemon monitor each other and restart on abnormal termination

### Architecture Refactoring

- **Service layer rebuilt** — core service architecture redesigned with optimized inter-module communication for better performance and responsiveness
- **UserService rewritten** — user service layer fully refactored for better maintainability and extensibility
- **Shizuku compatibility fixes** — known issues inherited from upstream Shizuku have been patched for greater stability

### UI/UX Improvements

Stellar's interface has been completely overhauled for a more modern, intuitive experience:

- **New authorization management UI** — built with Material Design 3, offering a clean and elegant permission control center
- **Refreshed authorization flow** — redesigned interaction makes granting permissions smoother and more natural
- **Visual permission breakdown** — the authorization screen clearly displays each individual permission
- **Improved app list** — authorized apps at a glance with permission status
- **Upgraded onboarding** — redesigned setup guide helps new users get started faster

## Key Differences from Shizuku

### Removed Features
- **rish** — the built-in root shell tool from Shizuku has been removed
- **Sui** — API support for Zygisk-Sui has been dropped

### New Features
- **Follow-startup mechanism** — apps can auto-launch alongside the Stellar service
- **Granular permission system** — fine-grained control over multiple permission types
- **Enhanced permission callbacks** — one-time grant awareness
- **Privilege downgrade** — after starting via Root, Stellar can drop to the Shell user for improved security
- **MediaTek support** — fixes a critical bug in upstream v13.6.0 that prevented Shizuku from running on MediaTek devices
- **In-app updates** — fetch update details from GitHub and update Stellar without leaving the app
- **TCP capability** — start or pre-warm Stellar while connected to Wi-Fi, enabling Wi-Fi-free startup on the next boot before Wi-Fi connects

### Re-enabled Features

Stellar brings back features that were deprecated in recent Shizuku releases:

- **`newProcess()` API** — the direct privileged process creation method that Shizuku deprecated but Stellar continues to support
- **Runtime permission grant/revoke** — grant or revoke Android runtime permissions for other apps via `grantRuntimePermission()` and `revokeRuntimePermission()`

These remain genuinely useful in certain scenarios, and Stellar keeps them available for a more complete API surface.

### Architecture Optimizations
- 100% Kotlin codebase
- Streamlined module structure
- Standardized naming conventions

## Shizuku Compatibility Layer

Stellar ships with a built-in Shizuku compatibility layer, so apps written against the Shizuku API work with Stellar without any code changes.

### How It Works

The compatibility layer achieves seamless compatibility through:

1. **Client-side compatibility** — `ShizukuProvider` receives the Binder from the Stellar service and manages connection state via `ShizukuCompat`
2. **Server-side interception** — `ShizukuServiceIntercept` implements the full `IShizukuService` interface, forwarding Shizuku API calls to the Stellar service
3. **Permission mapping** — Shizuku permission requests are automatically mapped to Stellar's `shizuku` permission

### Supported APIs

The compatibility layer covers Shizuku's core APIs:

- `pingBinder()` / `getVersion()` / `getUid()` — service status queries
- `checkSelfPermission()` / `requestPermission()` — permission management
- `newProcess()` — privileged process creation
- `addUserService()` / `removeUserService()` — user service management
- `transactRemote()` — Binder transaction forwarding

### Enable / Disable

The Shizuku compatibility layer is enabled by default. You can turn it off in the Stellar Manager's settings page under "Shizuku Compatibility Layer."

### Notes

- The compatibility layer automatically rejects requests from Shizuku Manager to avoid conflicts
- Apps using the Shizuku API need `ShizukuProvider` configured in their `AndroidManifest.xml`

## Privilege Downgrade

The privilege downgrade feature lets Stellar start with Root, then automatically drop to the Shell user (uid=2000) for improved security.

### How to Enable

Toggle on "Privilege Downgrade" in the Stellar Manager's settings page.

### How It Works

With privilege downgrade enabled, the startup flow is:

```
su (root) → libchid.so 2000 → libstellar.so --apk=...
```

1. Execute `libchid.so` with Root
2. `libchid.so` switches the process identity to uid=2000 (Shell user)
3. `libstellar.so` starts the service as Shell

### Notes

- Privilege downgrade only takes effect in Root startup mode
- ADB startup mode already runs as uid=2000, so no downgrade is needed
- After downgrading, the service loses Root-only capabilities (e.g., writing system properties, accessing protected directories)

## Quick Start

### Integrating Stellar into Your App

See the full integration guide and API documentation:

- **[API Integration Guide](INTEGRATION_GUIDE.md)** — complete integration steps, API reference, and code examples
- **[Migrating from Shizuku](INTEGRATION_GUIDE.md#migrating-from-shizuku)** — detailed migration steps and API comparison

### Basic Usage

1. Add the JitPack dependency: `com.github.roro2239:Stellar-API:latest.release`
2. Configure `StellarProvider` in your AndroidManifest
3. Initialize Stellar and request permissions
4. Use Stellar APIs to perform privileged operations

> See the [API Integration Guide](INTEGRATION_GUIDE.md) for detailed steps

## Credits & License

### Credits

This project is based on [Shizuku](https://github.com/RikkaApps/Shizuku), developed by [RikkaApps](https://github.com/RikkaApps). Huge thanks to the original authors for their outstanding work.

### License

The modifications in this project are licensed under the [Mozilla Public License 2.0](LICENSE).

The original Shizuku code retains its Apache License 2.0.

| Component | License |
|-----------|---------|
| Stellar modifications | Mozilla Public License 2.0 |
| [Shizuku](https://github.com/RikkaApps/Shizuku) original code | Apache License 2.0 |

## Contributing

Issues and pull requests are welcome. Before submitting code, please ensure:

- Code style follows project conventions (Kotlin)
- Necessary comments and documentation are included
- All features pass testing

## Contact

- GitHub Issues: [Submit an issue](https://github.com/RORO2239/Stellar/issues)
- Project homepage: [RORO2239/Stellar](https://github.com/RORO2239/Stellar)

## Related Links

- [Full API Documentation](INTEGRATION_GUIDE.md)
- [Original Shizuku](https://github.com/RikkaApps/Shizuku)

<a href="https://www.star-history.com/?repos=roro2239%2FStellar&type=date&legend=top-left">
 <picture>
   <source media="(prefers-color-scheme: dark)" srcset="https://api.star-history.com/chart?repos=roro2239/Stellar&type=date&theme=dark&legend=top-left&sealed_token=DOGFkMNyKuECKihlTE4gifDbvly4k4Wr5IjBhG6w407ZVASud6bVrZlbDfNkY6rKv8GpKgpOYQ8uYyfFmwMEx6uLVcustg1MI-tvtusH3twxsJFOlmpY-g" />
   <source media="(prefers-color-scheme: light)" srcset="https://api.star-history.com/chart?repos=roro2239/Stellar&type=date&legend=top-left&sealed_token=DOGFkMNyKuECKihlTE4gifDbvly4k4Wr5IjBhG6w407ZVASud6bVrZlbDfNkY6rKv8GpKgpOYQ8uYyfFmwMEx6uLVcustg1MI-tvtusH3twxsJFOlmpY-g" />
   <img alt="Star History Chart" src="https://api.star-history.com/chart?repos=roro2239/Stellar&type=date&legend=top-left&sealed_token=DOGFkMNyKuECKihlTE4gifDbvly4k4Wr5IjBhG6w407ZVASud6bVrZlbDfNkY6rKv8GpKgpOYQ8uYyfFmwMEx6uLVcustg1MI-tvtusH3twxsJFOlmpY-g" />
 </picture>
</a>
