package autotorch.autotorch.client;

import autotorch.autotorch.client.mixin.LocalPlayerMixin;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.metadata.version.VersionPredicate;
import net.minecraft.client.player.LocalPlayer;
import org.junit.jupiter.api.Test;
import org.spongepowered.asm.mixin.MixinEnvironment;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModMetadataTest {

    private static JsonObject readJsonResource(String path) throws Exception {
        try (InputStream stream = ModMetadataTest.class.getResourceAsStream(path)) {
            assertNotNull(stream, path + " must be present on classpath");
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    private static void assertPredicate(String rawPredicate, List<String> accepted, List<String> rejected) throws Exception {
        VersionPredicate predicate = VersionPredicate.parse(rawPredicate);
        for (String ver : accepted) {
            assertTrue(predicate.test(Version.parse(ver)), rawPredicate + " should accept " + ver);
        }
        for (String ver : rejected) {
            assertFalse(predicate.test(Version.parse(ver)), rawPredicate + " should reject " + ver);
        }
    }

    @Test
    void mixinConfigAndTargetMethodAreValidForMc26_3() throws Exception {
        JsonObject json = readJsonResource("/autotorch.mixins.json");
        assertTrue(json.get("required").getAsBoolean());
        assertEquals("autotorch.autotorch.client.mixin", json.get("package").getAsString());
        assertEquals("JAVA_25", json.get("compatibilityLevel").getAsString());
        assertEquals("LocalPlayerMixin", json.getAsJsonArray("client").get(0).getAsString());

        assertNotNull(MixinEnvironment.CompatibilityLevel.valueOf("JAVA_25"));
        assertEquals("autotorch.autotorch.client.mixin.LocalPlayerMixin", LocalPlayerMixin.class.getName());

        Method sendChanges = LocalPlayer.class.getDeclaredMethod("sendChanges");
        assertEquals(void.class, sendChanges.getReturnType());
        assertEquals(0, sendChanges.getParameterCount());
    }

    @Test
    void fabricModJsonSemVerPredicatesAcceptMc26_3AndFabricApi0_165_0() throws Exception {
        JsonObject modJson = readJsonResource("/fabric.mod.json");
        JsonObject depends = modJson.getAsJsonObject("depends");
        JsonObject suggests = modJson.getAsJsonObject("suggests");
        assertNotNull(depends);
        assertNotNull(suggests);

        assertPredicate(
                depends.get("minecraft").getAsString(),
                List.of("26.3", "26.3.1"),
                List.of("26.2", "26.4", "1.21.4")
        );
        assertPredicate(
                depends.get("fabric-api").getAsString(),
                List.of("0.153.1+26.3", "0.160.5+26.3", "0.165.0+26.3", "0.166.2+26.3"),
                List.of()
        );
        assertPredicate(
                depends.get("fabricloader").getAsString(),
                List.of("0.18.4", "0.19.2", "0.19.3", "0.20.1"),
                List.of("0.15.10", "0.18.0")
        );
        assertPredicate(
                depends.get("java").getAsString(),
                List.of("25", "26"),
                List.of("17", "21")
        );
        assertPredicate(
                suggests.get("modmenu").getAsString(),
                List.of("21.0.0", "21.0.1"),
                List.of("20.0.0")
        );

        JsonArray mixins = modJson.getAsJsonArray("mixins");
        assertNotNull(mixins);
        assertEquals(1, mixins.size());
        JsonObject clientMixin = mixins.get(0).getAsJsonObject();
        assertEquals("autotorch.mixins.json", clientMixin.get("config").getAsString());
        assertEquals("client", clientMixin.get("environment").getAsString());
    }
}
