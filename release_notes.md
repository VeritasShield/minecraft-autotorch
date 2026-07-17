# Auto Torch Enhanced v2.2.12

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
* Fixed an issue where the inventory slot could get visually desynced, leaving "ghost" torches in your inventory.
* Fixed an infinite loop placement bug when attempting to place torches out of reach.
* Weapon detection logic uses Registry Names and Fabric Tags (`#c:swords`, `#c:axes`) to ensure compatibility with 100% of vanilla and modded weapons, even in obfuscated production environments.
* Complete translation support for Spanish (`es_es.json`) and English (`en_us.json`).
