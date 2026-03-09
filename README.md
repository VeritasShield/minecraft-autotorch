# Auto Torch Enhanced (Fabric Client Mod)

This is a heavily improved and modernized fork of the original [AutoTorch by necessary129](https://github.com/necessary129/minecraft-autotorch). 

**Fabric API and ModMenu are required for this mod to function.**

## 🌟 What's new in this Enhanced version?
The original mod was great, but it lacked compatibility with modern server anti-cheats and required torches to be specifically in the offhand. This version fixes all of that!

* **Hotbar Support:** You no longer need torches in your offhand! The mod will automatically find torches in your hotbar, switch to them in a millisecond, place the torch, and switch back to your weapon.
* **Server-Friendly / Anti-Cheat Bypass:** * Added a randomized "Humanized Delay" between torch placements.
  * Added an artificial hand-swing animation to prevent `NoSwing` kicks.
  * Added a Line-of-Sight (FOV) check so you don't place torches behind your back (prevents `Scaffold/KillAura` flags).
* **Smart Placement:** Raycasting prevents placing torches through walls.
* **Adjustable Radius:** Scan for dark spots up to 4 blocks away before you even step on them!

## ⚙️ Usage
Simply have torches in your offhand OR anywhere in your first 9 hotbar slots.
Press **Left Alt** (default) to toggle the mod on or off.
Go to the ModMenu configuration screen to adjust the radius, light levels, and server-friendly settings.

## 📜 Credits & License
* Original concept and base code by [noteness (necessary129)](https://github.com/necessary129/minecraft-autotorch).
* Updated to 1.21.11, completely rewritten logic, and anti-cheat modules by [VeritasShield].
* Licensed under GNU LGPL-3.0.