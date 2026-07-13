package autotorch.autotorch.client;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import net.minecraft.client.gui.screens.Screen;

import java.lang.reflect.Method;
import java.util.function.Supplier;

public class ModMenuIntegration implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> {
            try {
                Class<?> autoConfigClass = Class.forName("me.shedaniel.autoconfig.AutoConfig");
                Method getConfigScreen = autoConfigClass.getMethod("getConfigScreen", Class.class, Screen.class);
                @SuppressWarnings("unchecked")
                Supplier<Screen> supplier = (Supplier<Screen>) getConfigScreen.invoke(null, ModConfig.class, parent);
                return supplier.get();
            } catch (Exception e) {
                e.printStackTrace();
                return parent;
            }
        };
    }
}
