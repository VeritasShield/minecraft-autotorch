# Auto Torch Enhanced (Fabric Client Mod)

This is a heavily improved and modernized fork of the original [AutoTorch by necessary129](https://github.com/necessary129/minecraft-autotorch).

**Fabric API is required for this mod to function. ModMenu is recommended to configure options in-game.**

## 🌟 What's new in this Enhanced version?
The original mod was great, but it lacked compatibility with modern server anti-cheats and required torches to be specifically in the offhand. This version fixes all of that!

* **Hotbar & Main Inventory Support:** You no longer need torches in your offhand! The mod automatically finds torches in your hotbar (or swaps them from your main inventory into a configurable hotbar slot), places the torch, and safely reverts back.
* **Server-Friendly / Anti-Cheat Bypass:**
  * Randomized "Humanized Delay" between torch placements and slot reversions.
  * Artificial hand-swing animation to prevent `NoSwing` kicks.
  * Line-of-Sight (FOV) check so torches are not placed behind your back (prevents `Scaffold/KillAura` flags).
  * Tick-paced rotation packet spoofing (`advancedPacketSpoofing`) sending at most one rotation packet per tick with accurate collision state.
* **Smart Placement:** Face-aware raycasting prevents placing torches through walls or ledges.
* **Smart Pauses:** Automatically pauses when Sneaking, Sprinting, or holding weapons (`#c:swords`, `#c:axes`, `#c:bows`, tridents), preventing accidental placements during combat.
* **Darkness ESP (Optional):** Renders small flame particles on dark floor blocks around you where mobs can spawn.
* **Adjustable Radius:** Scans for dark spots horizontally and vertically before you step onto them.
* **Block Blacklist & Whitelist:** Look at any block in-game and press **B** (default) to add/remove it from the Blacklist, or switch the list to Whitelist mode in settings.
* **Dimension-Scoped Anti-Torch Zones (WorldEdit Style):** Use an empty hand to left/right click and define 3D exclusion zones scoped per dimension (`dimension@x1,y1,z1|x2,y2,z2`), saved in your config.
* **Advanced Configuration:** Fully customizable cooldowns, scan throttling, update ticks, and random variances via ModMenu (`Cloth Config`).

## ⚙️ Usage
* Keep torches in your offhand, anywhere in your hotbar, or in your main inventory (when **Use Torches from Inventory** is enabled).
* Press **Left Alt** (default) to toggle the mod on or off.
* Press **B** (default) while looking at a block in the world to add or remove it from the **Blacklist**.
* Press **Z** (default) to toggle **Anti-Torch Zone Selector** mode. With an empty hand, Left Click sets Point 1 and Right Click sets Point 2, then press **Z** again to save the zone.
* Bind **Delete Current Zone** and **Clear All Zones** in **Options → Controls → Key Binds → Auto Torch** to remove zones in-game.
* Open the **ModMenu** configuration screen to customize radii, light thresholds, server-friendly options, and delay settings.

## 📜 Credits & License
* Original concept and base code by [noteness (necessary129)](https://github.com/necessary129/minecraft-autotorch).
* Updated to Minecraft 26.3, modularized architecture, and anti-cheat modules by [VeritasShield].
* Licensed under GNU LGPL-3.0.
