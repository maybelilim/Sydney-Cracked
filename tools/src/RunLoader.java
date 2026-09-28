public class RunLoader {
    public static void main(String[] args) throws Exception {
        HookRuntime.log("=== harness start ===");
        try {
            Class<?> ep = Class.forName("net.sydneyclient.loader.entrypoints.FabricEntryPoint", true, RunLoader.class.getClassLoader());
            HookRuntime.log("entrypoint class loaded: " + ep);
            Object inst = ep.getDeclaredConstructor().newInstance();
            HookRuntime.log("instance: " + inst);
            try {
                ep.getMethod("onPreLaunch").invoke(inst);
                HookRuntime.log("onPreLaunch returned normally");
            } catch (Throwable t) {
                HookRuntime.log("onPreLaunch threw: " + t + "\n" + stack(t));
            }
        } catch (Throwable t) {
            HookRuntime.log("init failed: " + t + "\n" + stack(t));
        }
        HookRuntime.log("=== harness end ===");
    }

    static String stack(Throwable t) {
        StringBuilder sb = new StringBuilder();
        for (StackTraceElement e : t.getStackTrace()) sb.append("    at ").append(e).append("\n");
        if (t.getCause() != null) sb.append("caused by ").append(t.getCause()).append("\n").append(stack(t.getCause()));
        return sb.toString();
    }
}
