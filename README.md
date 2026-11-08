# Most Wanted Offline — Community Edition

A privacy-focused, offline single-player Android build of the **PC version** of *Need for Speed: Most Wanted* (2005).

> This repository is an independent community modification of the upstream project. It is not an original EA product, does not include copyrighted game files, and is not affiliated with or endorsed by Electronic Arts.

## Project status

This is a work-in-progress cleanup branch based on the upstream PC executable recompilation project. The offline changes are being reviewed and must pass Android builds and device tests before an APK can be called stable.

## What this edition is intended to do

- Run the original PC game's single-player content using the owner's legally obtained game files.
- Keep local gameplay features such as Career, Quick Race, Free Roam, police pursuits, controls, graphics settings and local saves where supported by the underlying port.
- Use the Android device's system Vulkan driver only.
- Work without an internet connection after installation and game-file setup.

## Removed or disabled in this edition

- Online server selection and EA Nation/community-server connection paths.
- LAN discovery and multiplayer server components.
- Startup GitHub update checks and remote release-note fetching.
- Automatic crash-report packaging and external issue-page launching.
- Developer-channel promotion and in-game promotional toast.
- Importing arbitrary third-party Vulkan driver ZIPs into the application.

The offline network restrictions are defense-in-depth, not a substitute for Android's application sandbox. This build should not request Internet permission. Local storage access is still required by the current game-file layout.

## Requirements

- Android device with Vulkan 1.1 support and a compatible 64-bit ARM build (recommended).
- A legally obtained PC copy of *Need for Speed: Most Wanted* (2005).
- Game data copied to `Internal storage/nfsmw`, with `speed.exe` (or `Speed.exe`), the original data folders, and `d3dx9_26.dll` in that directory. The 32-bit DirectX runtime DLL can be copied from Windows `SysWOW64`.
- Free storage for game assets and local save data.

No game executable, music, video, car assets, or other copyrighted game data is distributed in this repository.

## Build notes

The Android build depends on the patched `pcrecomp` and `nfsu2-recompiled` sibling checkouts, the Android SDK/NDK, prepared DXVK sources, and generated code created locally from the user's own game files. Generated proprietary game translations and original game files must remain local and must not be committed.

On Windows, after preparing those sibling checkouts and the Android SDK/NDK, run the local build pipeline from PowerShell:

```powershell
Set-ExecutionPolicy -Scope Process Bypass
.\scripts\build_android.ps1
```

The script verifies the selected `speed.exe` SHA-256, creates the main executable, D3DX9 and sound-bank translations locally, then builds an installable debug-signed APK at `MostWantedOffline.apk`. It does not upload game files or generated translations. The APK is a personal test build, not a publicly release-signed package.

Before publishing an APK, maintainers must:
1. Build arm64-v8a from a clean checkout.
2. Inspect the merged manifest and confirm there is no INTERNET permission.
3. Run a static dependency scan for analytics, advertising, tracking and remote code loading.
4. Install and test with airplane mode enabled.
5. Verify game launch, Career, Quick Race, Free Roam, police pursuits, controller/touch input and save/load.
6. Test on more than one GPU family; a successful compile is not proof of device compatibility.

## Security and privacy

- No online service is required for the intended single-player mode.
- No automatic crash-log upload is included in the intended offline edition.
- No custom Vulkan driver package should be imported or loaded by the app.
- Game data and saves are stored locally in the game folder.
- The app still needs broad shared-storage access under the current legacy file layout. This is a known permission limitation and should be replaced with scoped storage or the Storage Access Framework in a future redesign.

## Known limitations

- This branch has not yet been validated by a successful clean Android build or full device test.
- Pixel 6a / Mali-G78 compatibility is not confirmed. Do not assume the upstream test results guarantee stability on that device.
- Android rendering, aspect ratio, audio timing, touch controls and performance can differ from the original PC game.
- A complete campaign playthrough and every race/save scenario have not been independently verified for this branch.

## Credits and notices

This project is based on the work in [codepdbh/nfsmwpc-recompiled](https://github.com/codepdbh/nfsmwpc-recompiled). It also relies on third-party projects and adaptations documented in the upstream repository and its `THIRD_PARTY_NOTICES.md`. Their notices and licenses must be retained.

*Need for Speed* and related game assets are trademarks and property of Electronic Arts. All game assets must be supplied by the user from their own lawful copy. This community project is not affiliated with EA.

## Upstream reference

- [Original recompilation project](https://github.com/codepdbh/nfsmwpc-recompiled)
- [Upstream issues and device reports](https://github.com/codepdbh/nfsmwpc-recompiled/issues)
- [Upstream release history](https://github.com/codepdbh/nfsmwpc-recompiled/releases)
