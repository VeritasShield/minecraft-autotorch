import java.lang.reflect.Method;

public class DumpGameMode {
    public static void main(String[] args) throws Exception {
        Class<?> cls = Class.forName("net.minecraft.client.multiplayer.MultiPlayerGameMode");
        for (Method m : cls.getDeclaredMethods()) {
            System.out.println(m.getName() + " : " + java.util.Arrays.toString(m.getParameterTypes()));
        }
    }
}
