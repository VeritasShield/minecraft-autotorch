# Contexto del Proyecto: Auto Torch Enhanced

## 1. Descripción General
"Auto Torch Enhanced" es un mod Client-Side para Minecraft desarrollado usando el Fabric Mod Loader. Permite a los jugadores colocar antorchas automáticamente en zonas de baja iluminación de forma "Server-Friendly" para no ser detectados por Anti-Cheats.

## 2. Entorno y Herramientas (Stack Tecnológico)
* **Minecraft Version**: 26.2 (Equivalente a iteraciones 1.21.2+)
* **Java Version**: Java 25
* **API / Mod Loader**: Fabric Loader y Fabric API
* **Sistema de Mapeos**: **MOJANG MAPPINGS**. En la versión 26.2, Fabric trata el entorno como *unobfuscated* de forma nativa. **No se utiliza Yarn Mappings**. Todas las clases siguen el estándar oficial de Mojang (ej. `net.minecraft.client.gui.screens.Screen` o `net.minecraft.world.entity.player.Player`).
* **Dependencias Extra**: ModMenu y Cloth-Config API (manejado por AutoConfig).

## 3. Arquitectura del Código
El mod está estructurado dentro de `src/main/java/autotorch/autotorch/client/`:
* `AutotorchClient`: Punto de entrada (`ClientModInitializer`). Registra los eventos de Ticks del cliente, HUD, y carga la configuración.
* `TorchPlacementEngine`: El motor lógico principal. Se ejecuta en cada Tick. Realiza un escaneo volumétrico (3D) buscando niveles de luz bajos. Incluye optimizaciones como **Throttling (`scanDelayTicks`)** para reducir el impacto en CPU al evitar escaneos en cada tick, y **Advanced Packet Spoofing** para enviar paquetes matemáticamente precisos de rotación (*Pitch/Yaw*) hacia la cara superior del bloque antes de colocar la antorcha.
* `ZoneManager`: Permite al usuario delimitar zonas espaciales (WorldEdit-style) donde el mod tiene estrictamente prohibido actuar. Guarda las coordenadas en la configuración y las parsea a `AABB` geométricas para validaciones rápidas (`O(N)`). Ahora incluye lógica para borrar directamente el `AABB` actual si el jugador se encuentra dentro.
* `ModConfig`: Esquema de configuración (utiliza `me.shedaniel.autoconfig`). Guarda datos de listas negras, tiempos de espera humanos, radios de acción y niveles de luz.
* `KeybindManager`: Registra los atajos de teclado (`KeyMapping.Category.register`). Controla eventos avanzados como `Shift + Z` (borrar zona actual) o `Ctrl + Z` (borrar todas las zonas).
* `SelectionRenderer`: Dibuja "bounding boxes" visuales (partículas) cuando el usuario está definiendo una `Zone` o cuando las está previsualizando en modo selección (`zoneSelectionMode`).
* `RaycastUtils`: Utilidades de matemáticas de vectores para trazar la línea de visión (Line Of Sight / FOV) del jugador y evitar poner antorchas a través de bloques u obstáculos.

## 4. Notas Críticas de Desarrollo y Problemas Conocidos (Workarounds)
* **API de Inventario Privada**: Desde 26.2, la propiedad de selección de hotbar en el inventario es privada. Siempre usar `player.getInventory().getSelectedSlot()` y `setSelectedSlot(int)`.
* **Sincronización de Inventario**: `handleContainerInput` (SWAP) actualiza el servidor, pero visualmente el UI se puede desincronizar. Siempre respaldar el item (`slot.getItem().copy()`) y forzar la sincronización gráfica con `slot.set()` después de mandar el paquete.
* **Mensajes Nativos**: Se deprecó `displayClientMessage`. Los mensajes al usuario se deben enviar con `player.sendSystemMessage()` (chat) o `player.sendOverlayMessage()` (action bar).
* **Parche de Cloth-Config (Reflection)**: Dado que compilamos con *Mojang Mappings* nativos, la librería pre-compilada `cloth-config-fabric v26.2.155` introduce un fallo de compilación porque exportó sus métodos bajo el mapping obsoleto Yarn (`net.minecraft.client.gui.screen.Screen` en singular, no plural). 
  * **Solución permanente**: En `ModMenuIntegration.java`, la integración de la pantalla de opciones usa *Java Reflection* (`Class.forName("me.shedaniel.autoconfig.AutoConfig").getMethod(...)`) para puentear el compilador. Fabric Loader remapea todo en Runtime, por lo que Reflection funciona perfectamente in-game. Nunca intentes enlazar `AutoConfig.getConfigScreen` directamente en código.
* **Componentes de Items**: Al checar si un item está en un Tag (ej. antorchas), usa la API moderna: `stack.typeHolder().is(TagKey)`. No usar `stack.isIn()`.
* **Identificación de Ítems sin Ofuscación**: En el entorno de producción (jar compilado), las clases cambian de nombre (obfuscation). Para identificar armas, en lugar de usar `mainItem instanceof SwordItem` o `mainItem.getClass().getSimpleName()`, se DEBE usar `BuiltInRegistries.ITEM.getKey(mainItem).getPath()` o chequeos de Tags (`#c:swords`, `#c:axes`) para mantener la compatibilidad independientemente de los mappings de ofuscación.
