## Auto Torch Enhanced - Release Notes

## v2.2.6 (Minecraft 26.2)
### New Features
- **Smart Wall Placement (FOV-Aware)**: Added a new configuration option to prioritize placing torches on adjacent walls instead of strictly on the floor. When searching for walls, the mod respects your FOV and prioritizes the wall face you are most directly looking at.
- **Auto-Refill (Inventory Swapping)**: The mod will now seamlessly use torches from your main inventory when your hotbar runs out. It temporarily swaps the torch into a configured hotbar slot (default: 9) to place it, and then instantly swaps the original item back, keeping your inventory perfectly organized.

### Fixes
- Re-architected `TorchPlacementEngine` to use dynamic block targets and faces to support complex placements without flagging strict anti-cheats.

## Auto Torch Enhanced v2.2.2 (Minecraft 26.2)

This release brings full compatibility with Minecraft 26.2 and includes massive logic and UX upgrades:

### 🚀 Major Updates
* **Minecraft 26.2 Compatibility**: Fully updated the mod environment to target the latest unobfuscated Minecraft 26.2 update.
* **Java 25 Migration**: Transitioned build tools and dependencies to leverage Java 25.

### 🌟 New Features & Performance
* **Volumetric Scanner Throttling**: Added `scanDelayTicks` to the Advanced Configuration. The mod no longer scans the 3D volume every single tick, saving massive amounts of CPU when using large radiuses!
* **Advanced Packet Spoofing**: Added strict math-based Anti-Cheat bypass in Server Friendly config. Calculates exact `Pitch` and `Yaw` to the target block before placing, rather than just forcing the head down.
* **Improved Zone UX**: Tired of editing strings in ModMenu? Press `Z` to enter Zone Mode, stand in any active zone, and press `Shift + Z` to delete it instantly! Pressing `Ctrl + Z` will wipe all zones.

### 🛠️ Fixes & Improvements
* **ModMenu Integration Fix**: Resolved a critical runtime mappings mismatch with `cloth-config`. The configuration screen is dynamically loaded via Reflection, opening flawlessly in pure Mojang mapped environments.
* **Network & Inventory Refactor**: Updated legacy hotbar access to the new accessor patterns (`getSelectedSlot()`/`setSelectedSlot()`).
* **Item Tags & UI Migration**: Ported item checking logic to use modern component structures (`stack.typeHolder().is(...)`) and updated UI feedback to native `sendSystemMessage` and `sendOverlayMessage`.
* **Exclusion Zones Efficiency**: `ZoneManager` effectively caches defined coordinates into `AABB` geometric bounds, ensuring fast and performant spatial checks.
