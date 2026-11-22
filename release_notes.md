# Auto Torch Enhanced v2.2.16 (Minecraft 26.3)

Major upgrade and stabilization release bringing **Auto Torch Enhanced** to **Minecraft 26.3** and **Java 25** (consolidated changelog covering all changes since `v2.2.13`).

## Platform Upgrade & Broadened Compatibility (`Minecraft 26.3` / `Java 25`)
* **Minecraft `26.3` & Java `25`:** Ported cleanly to Minecraft `26.3`'s native unobfuscated Mojang Mappings (`net.fabricmc.fabric-loom 1.19.4`, Gradle `9.8.2`) and updated Swing, Screen (`client.gui.screen()`), and `InputConstants.Type.KEYBOARD` bindings.
* **Broadened Fabric API Support (`"fabric-api": "*"` on `minecraft ~26.3`):** Resolves the Fabric Loader `HARD_DEP_NO_CANDIDATE` launch error on earlier `26.3` builds such as `0.165.0+26.3`, supporting every `26.3` Fabric API release (`0.153.1+26.3` through `0.166.2+26.3+`).
* **Verified Fabric Loader Floor (`"fabricloader": ">=0.18.4"`):** Set to the verified earliest `26.3` Fabric Loader supporting `JAVA_25` SpongePowered Mixin compatibility (compiled against `0.20.1`), while strictly enforcing `"minecraft": "~26.3"` and `"java": ">=25"`.
* **Companion Mods & Clean Packaging:** Updated bundled **Cloth Config API (`26.3.159`)** via Jar-in-Jar (no external install required), widened optional **ModMenu** compatibility (`>=21.0.0`, compiled against `21.0.1`), and standardized release artifact names (`autotorch-enhanced-mc26.3-<version>`).

## Anti-Cheat Packet Synchronization & Placement Hardening
* **Single-Movement-Packet-Per-Tick Sync (`LocalPlayerMixin`):** Hooks `LocalPlayer.sendChanges()` (`HEAD`/`RETURN`) so look-angle spoofing piggybacks directly on Minecraft `26.3`'s single per-tick `sendPosition()` movement packet without emitting duplicate rotation packets or corrupting collision flags.
* **Passenger & Camera Safety (`RotationSpoofController`):** Skips rotation spoofing while mounted (`player.isPassenger()`), respects manual camera movement on the next tick, and guarantees fail-safe camera restoration (`try/finally` + end-of-tick fallback).
* **Face-Aware Raycast Verification (`RaycastUtils`):** Raycasts directly against the intended support block face with front half-plane validation and opposite-face verification to prevent through-wall or behind-the-back placements.

## Inventory Safety, Zones, Keybinds & Code Quality
* **Server-Authoritative Inventory Swap (`InventorySwapController`):** Guards `ContainerInput.SWAP` strictly to idle player inventory states (`containerMenu == inventoryMenu && client.gui.screen() == null`), classifies swap outcomes (`SYNCED`, `REJECTED_NO_OP`, `PARTIAL_DESYNC`) without overwriting server slots on desync, and verifies displaced stacks before reverting.
* **Exact Weapon Identifier Token Matching (`TorchInventoryClassifier`):** Uses tokenized registry path matching paired with static Item Tags (`#c:swords`, `#c:axes`, `#c:bows`), fixing false-positive combat pauses on items such as `waxed_copper_block`, `waxed_*`, `*_pickaxe`, `bowl`, and `rainbow_*` while protecting genuine vanilla and modded weapons.
* **Dimension-Scoped Anti-Torch Zones & Configurable Keybinds (`ZoneManager`, `KeybindManager`):** Stores exclusion zones per dimension (`<dimension>@x1,y1,z1|x2,y2,z2`) with legacy entry compatibility, adds dedicated configurable `Delete Current Zone` and `Clear All Zones` keybinds, caps particle rendering density (`SelectionRenderer`), removes `[AutoTorch Log]` debug spam introduced in `v2.2.13`, and backs core modules with `28 / 28` JUnit 5 tests (including `ModMetadataTest` SemVer predicate and Mixin target verification).

---

# Auto Torch Enhanced v2.2.15 (Minecraft 26.3)

Upgrade release targeting **Minecraft 26.3** (Java 25, native unobfuscated Mojang Mappings), modularizing the placement engine and hardening Anti-Cheat packet cadence, inventory synchronization, and weapon classification.

## Platform & Toolchain Upgrade
* **Minecraft `26.3` & Java `25`:** Built on Gradle `9.8.2`, Fabric Loom `1.19.4`, Fabric Loader `0.20.1`, and Fabric API `0.166.2+26.3`.
* **Companion Dependencies:** Updated Cloth Config API (`26.3.159`, bundled via Jar-in-Jar) and ModMenu (`21.0.1`).
* **Clean Artifact Naming:** Standardized base archive names (`autotorch-enhanced-mc26.3-<version>`).

## Anti-Cheat & Placement Hardening
* **Single-Movement-Packet-Per-Tick Sync (`LocalPlayerMixin`):** Hooks `LocalPlayer.sendChanges()` (`HEAD`/`RETURN`) so look-angle spoofing piggybacks directly on Minecraft `26.3`'s single per-tick `sendPosition()` movement packet without emitting duplicate look packets, while skipping passenger/vehicle edge cases and guaranteeing fail-safe camera restoration (`try/finally` + end-of-tick fallback).
* **Face-Aware Raycast Verification (`RaycastUtils`):** Raycasts directly against the intended support block face with front half-plane validation and directional surface matching to avoid through-wall or behind-the-back placements.
* **Server-Authoritative Inventory Swap (`InventorySwapController`):** Guards `ContainerInput.SWAP` operations strictly to idle player inventory states (`containerMenu == inventoryMenu && client.gui.screen() == null`), classifies post-swap states (`SYNCED`, `REJECTED_NO_OP`, `PARTIAL_DESYNC`) without overwriting slots on partial desync, and verifies displaced stacks before reverting.
* **Exact Weapon Identifier Token Matching (`TorchInventoryClassifier`):** Uses tokenized registry path matching paired with static Item Tags (`#c:swords`, `#c:axes`, `#c:bows`), eliminating false-positive weapon pauses on items such as `waxed_copper_block`, `iron_pickaxe`, `bowl`, and `rainbow_*` blocks/items while protecting genuine vanilla and modded weapons.
* **Dimension-Scoped Anti-Torch Zones (`ZoneManager`):** Supports `<dimension>@x1,y1,z1|x2,y2,z2` exclusion zones with legacy entry backward compatibility and per-world cache isolation.
* **Configurable Keybinds (`KeybindManager`) & Automated Tests:** Five configurable `InputConstants.Type.KEYBOARD` bindings and JUnit 5 regression test suites covering raycasting, rotation spoofing, zone parsing/isolation, swap state machine transitions, Mixin descriptor verification, and weapon classification.

---

# Auto Torch Enhanced v2.2.14

This is a massive overhaul of the original mod, introducing **Server-Friendly** anti-cheat bypasses, smart environment scanning, and full UI customization!

## 🌟 Major Features & Additions
* **Hotbar Support:** Torches no longer have to be in your offhand! The mod automatically finds torches in your hotbar, switches to them in a millisecond, places the torch, and switches back to your previous item.
* **Smart Pauses:** The mod will intelligently pause placing torches if you are Sneaking, Sprinting, or holding a weapon (Swords, Axes, Bows, Tridents), preventing accidental placements during combat or precision movement.
* **Darkness ESP (Optional):** Render a small flame particle in the center of dark blocks around you where mobs can spawn, allowing you to easily identify unlit areas.
* **Block Blacklist & Whitelist Mode:** Prevent ruining your builds! Look at any block in-game and press a key to blacklist it so torches are never placed there. Alternatively, toggle the list in the options to act as a Whitelist so torches are ONLY placed on those blocks.
* **Anti-Torch Zones (WorldEdit Style):** Use an empty hand to left/right click and define 3D exclusion zones. These are saved in your config and can be viewed/deleted via ModMenu. Perfect for big builds!
* **Raycast Wall Prevention:** Smart raycasting prevents the mod from trying to place torches through walls or around corners.
* **Line-Of-Sight (FOV) Checking:** Prevents you from placing torches behind your back, preventing kicks for `Scaffold` or `KillAura` behavior.
* **Humanized Delays & Packet Spoofing:** Adds a randomized delay between torch placements and spoofs your player's rotation mathematically and naturally so Anti-Cheats won't ban you.
* **Offhand Support Detection:** Seamlessly supports placing torches directly from the offhand if you prefer playing that way.
* **Smart Spacing:** Configurable radius check to prevent placing torches too close to each other, stopping unnecessary spamming in small areas.

## 🛠️ Fixes & Improvements
* **HOTFIX:** Removed development log spam (`[AutoTorch Log]`) that was accidentally left enabled in v2.2.13.
* Fixed an issue where the inventory slot could get visually desynced, leaving "ghost" torches in your inventory.
* Fixed an infinite loop placement bug when attempting to place torches out of reach.
* Weapon detection logic uses Registry Names and Fabric Tags (`#c:swords`, `#c:axes`) to ensure compatibility with 100% of vanilla and modded weapons, even in obfuscated production environments.
* Complete translation support for Spanish (`es_es.json`) and English (`en_us.json`).
