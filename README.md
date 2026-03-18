# Auto Torch Enhanced (Fabric Client Mod)

This is a heavily improved and modernized fork of the original [AutoTorch by necessary129](https://github.com/necessary129/minecraft-autotorch). 

**Fabric API and ModMenu are required for this mod to function.**

## 🌟 What's new in this Enhanced version?
The original mod was great, but it lacked compatibility with modern server anti-cheats and required torches to be specifically in the offhand. This version fixes all of that!

* **Hotbar Support:** You no longer need torches in your offhand! The mod will automatically find torches in your hotbar, switch to them in a millisecond, place the torch, and switch back to your weapon.
* **Server-Friendly / Anti-Cheat Bypass:**
  * Added a randomized "Humanized Delay" between torch placements.
  * Added an artificial hand-swing animation to prevent `NoSwing` kicks.
  * Added a Line-of-Sight (FOV) check so you don't place torches behind your back (prevents `Scaffold/KillAura` flags).
* **Smart Placement:** Raycasting prevents placing torches through walls.
* **Adjustable Radius:** Scan for dark spots horizontally and vertically before you even step on them!
* **Block Blacklist:** Prevent ruining your builds! Look at any block in-game and press a key to blacklist it so torches are never placed there.
* **Anti-Torch Zones (WorldEdit Style):** Use an empty hand to left/right click and define 3D exclusion zones. These are saved in your config and can be viewed/deleted via ModMenu. Perfect for big builds!
* **Advanced Configuration:** Fully customizable cooldowns, update ticks, and random variances directly from the ModMenu screen.

## ⚙️ Usage
* Simply have torches in your offhand OR anywhere in your first 9 hotbar slots.
* Press **Left Alt** (default) to toggle the mod on or off.
* Press **B** (default) while looking at a block in the world to add or remove it from the **Blacklist**.
* Press **Z** (default) to toggle the **Anti-Torch Zone Selector**. With an empty hand, Left Click sets Pos1 and Right Click sets Pos2. Press **Shift+Z** to clear all zones.
* Go to the ModMenu configuration screen to adjust the radiuses, light levels, server-friendly options, and advanced delay settings.

## 📜 Credits & License
* Original concept and base code by [noteness (necessary129)](https://github.com/necessary129/minecraft-autotorch).
* Updated to 1.21.11, completely rewritten logic, and anti-cheat modules by [VeritasShield].
* Licensed under GNU LGPL-3.0.