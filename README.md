# Auto Torch Enhanced (Fabric Client Mod)

This is a heavily improved and modernized fork of the original [AutoTorch by necessary129](https://github.com/necessary129/minecraft-autotorch).

**Fabric API is required for this mod to function. Cloth Config is bundled inside the JAR, and ModMenu is optional for in-game configuration.**

## Requirements (Minecraft 26.3)

| Dependency | Runtime Requirement | Compile-Time Target | Notes |
| :--- | :--- | :--- | :--- |
| **Minecraft** | `~26.3` (`26.3.x`) | `26.3` | Native unobfuscated Mojang Mappings |
| **Java** | `>= 25` | JDK `25` | Mixin compatibility level `JAVA_25` |
| **Fabric Loader** | `>= 0.18.4` | `0.20.1` | Verified minimum for `JAVA_25` + `26.3` |
| **Fabric API** | Required (`*` on `~26.3`) | `0.166.2+26.3` | Compatible with `0.153.1+26.3`, `0.165.0+26.3`, `0.166.2+26.3+` |
| **Cloth Config API** | Bundled (Jar-in-Jar) | `26.3.159` | No external Cloth Config download needed |
| **ModMenu** | Optional (`>= 21.0.0`) | `21.0.1` | Provides the in-game settings screen |

## 🌟 What's new in this Enhanced version?
The original mod was great, but it lacked compatibility with modern server anti-cheats and required torches to be specifically in the offhand. This version fixes all of that!

* **Hotbar & Main Inventory Support:** You no longer need torches in your offhand! The mod automatically finds torches in your hotbar or swaps them from your main inventory into a configurable hotbar slot using server-authoritative `ContainerInput.SWAP` verification (`InventorySwapController`) before safely reverting.
* **Server-Friendly / Anti-Cheat Bypass:**
  * Randomized "Humanized Delay" between torch placements and slot reversions.
  * Artificial hand-swing animation (`26.3` `SwingAnimation`) to prevent `NoSwing` kicks.
  * Line-of-Sight (FOV) check so torches are not placed behind your back (prevents `Scaffold/KillAura` flags).
  * Single-movement-packet rotation sync via `LocalPlayerMixin` (`LocalPlayer.sendChanges()`), skipping mounted vehicles (`isPassenger()`) and guaranteeing fail-safe camera restoration.
* **Smart Placement:** Face-aware raycasting prevents placing torches through walls or around corners.
* **Smart Pauses:** Automatically pauses when Sneaking, Sprinting, or holding weapons matched via exact identifier tokens and static Item Tags (`#c:swords`, `#c:axes`, `#c:bows`, tridents, maces, etc.), avoiding false positives on `waxed_*`, `pickaxe`, `bowl`, or `rainbow_*` items.
* **Darkness ESP (Optional):** Renders small flame particles on dark floor blocks around you where mobs can spawn.
* **Adjustable Radius:** Scans for dark spots horizontally and vertically before you step onto them.
* **Block Blacklist & Whitelist:** Look at any block in-game and press **B** (default) to add/remove it from the Blacklist, or switch the list to Whitelist mode in settings.
* **Dimension-Scoped Anti-Torch Zones (WorldEdit Style):** Use an empty hand to left/right click and define 3D exclusion zones scoped per dimension (`dimension@x1,y1,z1|x2,y2,z2`), saved in your config.
* **Advanced Configuration:** Fully customizable cooldowns, scan throttling, update ticks, and random variances via ModMenu (`Cloth Config`).

## ⚙️ Usage & Default Keybinds
* Keep torches in your offhand, anywhere in your hotbar, or in your main inventory (when **Use Torches from Inventory** is enabled).
* **Left Alt** *(default)*: Toggle Auto Torch on or off.
* **B** *(default)*: While looking at a block, add or remove it from the **Blacklist**.
* **Z** *(default)*: Toggle **Anti-Torch Zone Selector** mode. With an empty hand, Left Click sets Point 1 and Right Click sets Point 2, then press **Z** again to save the zone.
* **Delete Current Zone** / **Clear All Zones** *(unbound by default)*: Configurable in **Options → Controls → Key Binds → Auto Torch** to remove zones in-game.
* Open **ModMenu → Auto Torch Enhanced** to customize scan radii, light thresholds, server-friendly delays, and block lists.

## 🛠️ Building & Multi-Platform CI Publishing

### Local Build & Dry-Run Verification
```bash
# Run unit tests (JUnit 5) and build the mod JAR on JDK 25
./gradlew test build --no-daemon

# Dry-run Modrinth / CurseForge publishing metadata & changelog extraction
./gradlew publishModrinth -PpublishDryRun=true --no-daemon
```

### Automated Publishing (`.github/workflows/publish.yml`)
Releases are built with JDK 25 and published via [`me.modmuss50.mod-publish-plugin`](build.gradle) using the top release section of [`release_notes.md`](release_notes.md) as the Single Source of Truth changelog:

1. **GitHub Actions Credentials** (**Settings → Secrets and variables → Actions**):
   * `MODRINTH_TOKEN` *(Repository Secret)*: Modrinth Personal Access Token with `CREATE_VERSION` scope (targets project `uGWYEogt`).
   * `CURSEFORGE_TOKEN` *(Repository Secret)*: CurseForge API token.
   * `CURSEFORGE_PROJECT_ID` *(Repository Variable or Secret)*: Numeric CurseForge Project ID for **Verritas-Auto Torch**.
2. **Automatic Release Trigger:** Publishing a GitHub Release runs `.github/workflows/publish.yml` (`release-artifacts`), attaches `build/libs/*` + `LICENSE` once to the GitHub Release, and conditionally publishes to Modrinth and CurseForge whenever their respective credentials are configured.
3. **Manual Publish Dispatch (`workflow_dispatch`):** To publish or republish a release (such as `v2.2.16`) to Modrinth and CurseForge from `main` without creating duplicate GitHub Release JARs, run **Actions → `release-artifacts` → Run workflow** with branch `main`, `ref = main`, and `upload_github_assets = false`.

## 📜 Credits & License
* Original concept and base code by [noteness (necessary129)](https://github.com/necessary129/minecraft-autotorch).
* Updated to Minecraft 26.3, modularized architecture, and anti-cheat modules by [VeritasShield](https://github.com/VeritasShield/minecraft-autotorch).
* Licensed under GNU LGPL-3.0 (`LGPL-3.0-or-later`).
