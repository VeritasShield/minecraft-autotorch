# Auto Torch Enhanced v2.2.11

## 🌟 What's New?
* **Smart Pauses:** The mod will intelligently pause placing torches if you are Sneaking, Sprinting, or holding a weapon (Swords, Axes, Bows, Tridents), preventing accidental placements during combat or precision movement.
* **Darkness ESP (Optional):** Render a small flame particle in the center of dark blocks around you where mobs can spawn, allowing you to easily identify unlit areas.
* **Block Blacklist & Whitelist Mode:** Prevent ruining your builds! Look at any block in-game and press a key to blacklist it so torches are never placed there. Alternatively, toggle the list in the options to act as a Whitelist so torches are ONLY placed on those blocks.
* **Offhand Support Detection:** Seamlessly supports placing torches directly from the offhand (bypassing hotbar swaps) if you prefer playing that way.

## 🛠️ Fixes & Improvements
* Fixed an issue where the inventory slot could get visually desynced, leaving "ghost" torches in your inventory until you clicked them.
* Re-wrote weapon detection logic using Registry Names and Fabric Tags (`#c:swords`, `#c:axes`) to ensure compatibility with 100% of vanilla and modded weapons, even in obfuscated production environments.
* Added missing translations for Spanish (`es_es.json`) and English (`en_us.json`) for all the new features.
