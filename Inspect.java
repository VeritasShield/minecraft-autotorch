import java.lang.reflect.Method;
import java.util.Arrays;

public class Inspect {
    public static void main(String[] args) throws Exception {
        Class<?> cls = Class.forName("net.minecraft.client.multiplayer.MultiPlayerGameMode");
        for (Method m : cls.getDeclaredMethods()) {
            if (m.getName().equals("handleInventoryMouseClick")) {
                System.out.println("handleInventoryMouseClick: " + Arrays.toString(m.getParameterTypes()));
            }
            if (m.getName().equals("handleContainerInput")) {
                System.out.println("handleContainerInput: " + Arrays.toString(m.getParameterTypes()));
            }
        }
        try {
            Class<?> ci = Class.forName("net.minecraft.world.inventory.ClickType");
            System.out.println("ClickType values: " + Arrays.toString(ci.getEnumConstants()));
        } catch(Exception e){}
        try {
            Class<?> ci = Class.forName("net.minecraft.world.inventory.ContainerInput");
            System.out.println("ContainerInput values: " + Arrays.toString(ci.getEnumConstants()));
        } catch(Exception e){}
    }
}
