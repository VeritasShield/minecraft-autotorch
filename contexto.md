# Contexto del Proyecto: Auto Torch Enhanced

## 1. Descripción General
"Auto Torch Enhanced" es un mod Client-Side para Minecraft desarrollado usando el Fabric Mod Loader. Permite a los jugadores colocar antorchas automáticamente en zonas de baja iluminación de forma "Server-Friendly" para minimizar falsos positivos de Anti-Cheats.

## 2. Entorno y Herramientas (Stack Tecnológico)
* **Minecraft Version**: `26.3` (Unobfuscated nativo)
* **Java Version**: Java 25
* **Build Toolchain**: Gradle `9.8.2` + Fabric Loom `1.19.4`
* **API / Mod Loader**: Fabric Loader `0.20.1` y Fabric API `0.166.2+26.3`
* **Sistema de Mapeos**: **MOJANG MAPPINGS**. Desde Minecraft 26.x, Fabric opera sobre jars unobfuscated de forma nativa. **No se utiliza Yarn Mappings.** Todas las clases siguen el estándar oficial de Mojang (ej. `net.minecraft.client.gui.screens.Screen`, `net.minecraft.world.entity.player.Player`).
* **Dependencias Extra**: Cloth Config API (`26.3.159`, incluido vía Jar-in-Jar) y ModMenu (`21.0.1`).

## 3. Arquitectura del Código
El mod está estructurado de forma modular dentro de `src/main/java/autotorch/autotorch/client/` (todos los archivos $\le 350$ LOC) con pruebas JUnit 5 en `src/test/java/autotorch/autotorch/client/`:
* `AutotorchClient`: Punto de entrada (`ClientModInitializer`). Registra ticks del cliente (`END_CLIENT_TICK` para keybinds y partículas), callbacks de interacción (`AttackBlockCallback`, `UseBlockCallback`), desconexión (`ClientPlayConnectionEvents.DISCONNECT`), hooks de `LocalPlayerMixin` (`onBeforePlayerSendChanges` / `onAfterPlayerSendChanges`), y listeners de carga/guardado de AutoConfig. La retroalimentación visual se realiza mediante partículas en el mundo (`SelectionRenderer`) y mensajes en barra de acción (`sendOverlayMessage`).
* `mixin/LocalPlayerMixin`: Inyecta `@At("HEAD")` y `@At("RETURN")` en `LocalPlayer.sendChanges()` (`autotorch.mixins.json`) para alinear la colocación y el spoof de rotación con el único paquete de movimiento enviado por `LocalPlayer.sendPosition()` en cada tick.
* `TorchPlacementEngine`: Orquestador de colocación sincronizado con `LocalPlayer.sendChanges()`. Gestiona cooldowns, limpieza de estado al cambiar de mundo/dimensión, alcance (`player.blockInteractionRange()`), colocación armada (`ReadyPlacement`) y `useItemOn` con `SwingAnimation` (`26.3`).
* `TorchTargetScanner`: Escáner volumétrico 3D unificado. Evalúa candidatos alrededor del jugador y en anillos radiales usando `PlacementCandidate` (record inmutable), calcula puntajes de pared/piso con whitelist/blacklist, valida FOV consistentemente y verifica `smartSpacing` sin asignaciones heap usando `BlockPos.MutableBlockPos`.
* `InventorySwapController`: Controlador atómico de inventario y hotbar. Ejecuta `ContainerInput.SWAP` únicamente cuando el inventario del jugador está activo (`containerMenu == inventoryMenu && client.gui.screen() == null`), clasifica el resultado post-swap (`SYNCED`, `REJECTED_NO_OP`, `PARTIAL_DESYNC` sin sobrescribir slots en desync), y verifica el ítem desplazado antes de revertir.
* `TorchInventoryClassifier`: Clasifica armas y antorchas usando paths del registro y tags estáticos (`#c:swords`, `#c:axes`, `#c:bows`, `#c:torches`) sin depender de `instanceof` frágiles, y localiza fuentes válidas de antorchas en offhand, hotbar o inventario principal.
* `RotationSpoofController`: Controlador de rotación compatible con Anti-Cheats. Envuelve/limita ángulos (`wrapYaw`/`clampPitch`), omite el spoof cuando `player.isPassenger()`, aplica las rotaciones durante `LocalPlayer.sendChanges()` para que `sendPosition()` emita exactamente 1 paquete de movimiento por tick (con `horizontalCollision` real), garantiza la restauración fail-safe de cámara (`forceRestoreIfActive`) y omite la restauración al tick siguiente si el jugador rotó manualmente.
* `ZoneManager`: Administra zonas de exclusión 3D por dimensión (`<dimension>@x1,y1,z1|x2,y2,z2`, con retrocompatibilidad para entradas legadas sin dimensión), caché `AABB` por dimensión sincronizado en carga/guardado de configuración, borrado de zona actual y limpieza al cambiar de mundo.
* `ModConfig`: Esquema de configuración con `me.shedaniel.autoconfig` (`general`, `server_friendly`, `advanced`).
* `KeybindManager`: Registra 5 atajos configurables en `KeyMapping.Category` (`autotorch:options`) usando `InputConstants.Type.KEYBOARD` y constantes `InputConstants.KEY_*` / `InputConstants.UNKNOWN.getValue()`.
* `SelectionRenderer`: Dibuja cajas de partículas delimitadas (tope de partículas por arista y culling por distancia) para selección/zonas excluidas y renderiza Darkness ESP.
* `RaycastUtils`: Utilidades de raycasting (`ClipContext`) dirigidas a la cara de soporte (`computeRaycastEndpoint` con `+0.01` hacia el bloque soporte), verificación de semiplano frontal (`isEyeInFrontOfSupportFace`), validación de cara opuesta (`supportDir.getOpposite()`) y verificación de ángulo FOV (`isWithinFovCone`).
* `ModMenuIntegration`: Implementa `ModMenuApi` devolviendo `AutoConfigClient.getConfigScreen(ModConfig.class, parent).get()`.

## 4. Notas Críticas de Desarrollo y Reglas Técnicas (Verificadas en 26.3)
* **API de Inventario Privada**: La selección de hotbar es privada; siempre usar `player.getInventory().getSelectedSlot()` y `setSelectedSlot(int)`.
* **Sincronización de Inventario (`ContainerInput.SWAP`)**: En `26.3`, `MultiPlayerGameMode.handleContainerInput` invoca sincrónicamente `AbstractContainerMenu.clicked(...)` actualizando `Inventory` y `Slot` en cliente antes de enviar `ServerboundContainerClickPacket`. Siempre comprobar `containerMenu == inventoryMenu && client.gui != null && client.gui.screen() == null`, verificar que la permuta ocurrió correctamente (`ItemStack.matches`) y notificar `slot.setChanged()` sin forzar mutaciones si el menú rechazó la operación.
* **Cambios API en 26.3**: `Minecraft.screen` pasó a `client.gui.screen()`, `InputConstants.Type.KEYSYM` y `org.lwjgl.glfw.GLFW` fueron reemplazados por `InputConstants.Type.KEYBOARD` e `InputConstants.KEY_*`, y `LivingEntity.swing(InteractionHand)` ahora requiere `player.swing(hand, itemStack.getInteractAnimation(), false)`.
* **Mensajes Nativos**: `displayClientMessage` está deprecado/eliminado; usar siempre `player.sendSystemMessage()` (chat) o `player.sendOverlayMessage()` (action bar).
* **Integración Cloth-Config (`AutoConfigClient`)**: Desde 26.2/26.3, Cloth Config provee `me.shedaniel.autoconfig.AutoConfigClient.getConfigScreen(Class, Screen)` compilado nativamente contra Mojang Mappings (`net.minecraft.client.gui.screens.Screen`), eliminando la necesidad del antiguo parche por reflexión.
* **Componentes y Tags de Ítems**: Para comprobar tags de ítems usar `stack.typeHolder().is(TagKey)` con `TagKey<Item>` cacheados como constantes estáticas (no usar `stack.isIn()`).
* **Identificación Segura de Ítems**: Para identificar armas de vanilla y mods, usar `BuiltInRegistries.ITEM.getKey(mainItem).getPath()` y tags (`#c:swords`, `#c:axes`, `#c:bows`) en lugar de `instanceof SwordItem` o `getClass().getSimpleName()`.
