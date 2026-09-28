import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

public class HookRuntime {
    static final Path LOG = Paths.get("hook.log");
    static final Object LOCK = new Object();

    public static void log(String s) {
        synchronized (LOCK) {
            try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(LOG, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND))) {
                w.println(s);
            } catch (Exception ignored) { }
        }
    }

    public static void logStr(String cls, Object res) {
        log("[STR] " + cls + " -> " + safe(res));
    }

    public static void logDispatch(String cls, String op, Object[] args, Object result) {
        StringBuilder sb = new StringBuilder("[DISPATCH] op=").append(op).append(" args=[");
        for (int i = 0; i < args.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append(inspect(args[i]));
        }
        sb.append("] -> ").append(inspect(result));
        log(sb.toString());
    }

    static String inspect(Object o) {
        if (o == null) return "null";
        if (o instanceof byte[]) return hex((byte[]) o) + "(" + ((byte[]) o).length + "B)";
        if (o instanceof javax.crypto.spec.IvParameterSpec) return "IV:" + hex(((javax.crypto.spec.IvParameterSpec) o).getIV());
        if (o instanceof javax.crypto.SecretKey) {
            javax.crypto.SecretKey k = (javax.crypto.SecretKey) o;
            byte[] e = k.getEncoded();
            return "KEY:" + k.getAlgorithm() + ":" + (e == null ? "?" : hex(e));
        }
        if (o instanceof java.security.spec.KeySpec) {
            java.security.spec.KeySpec ks = (java.security.spec.KeySpec) o;
            try {
                java.lang.reflect.Method gp = ks.getClass().getMethod("getPassword");
                gp.setAccessible(true);
                char[] pw = (char[]) gp.invoke(ks);
                String p = pw == null ? "null" : new String(pw);
                String salt = "?"; int iters = -1; int klen = -1;
                try { java.lang.reflect.Method m = ks.getClass().getMethod("getSalt"); byte[] s = (byte[]) m.invoke(ks); salt = s == null ? "null" : hex(s); } catch (Throwable ignored) { }
                try { java.lang.reflect.Method m = ks.getClass().getMethod("getIterationCount"); iters = (Integer) m.invoke(ks); } catch (Throwable ignored) { }
                try { java.lang.reflect.Method m = ks.getClass().getMethod("getKeyLength"); klen = (Integer) m.invoke(ks); } catch (Throwable ignored) { }
                return "PBE:{pw=" + p + ", salt=" + salt + ", iters=" + iters + ", keylen=" + klen + "}";
            } catch (Throwable t) { return "KeySpec:" + ks.getClass().getName(); }
        }
        if (o instanceof java.security.Key) {
            java.security.Key k = (java.security.Key) o;
            byte[] e = k.getEncoded();
            return "KEY:" + k.getAlgorithm() + ":" + (e == null ? "?" : hex(e));
        }
        if (o instanceof Object[]) {
            Object[] a = (Object[]) o;
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < a.length; i++) { if (i > 0) sb.append(", "); sb.append(inspect(a[i])); }
            return sb.append("]").toString();
        }
        if (o instanceof java.lang.invoke.MethodType) return o.toString();
        if (o instanceof java.lang.invoke.MethodHandles.Lookup) return "Lookup";
        if (o instanceof java.lang.invoke.MutableCallSite) return "CallSite@" + Integer.toHexString(o.hashCode());
        String s = String.valueOf(o);
        if (s.length() > 200) s = s.substring(0, 200) + "...";
        StringBuilder sb2 = new StringBuilder();
        for (char c : s.toCharArray()) sb2.append(c >= 32 && c < 127 ? c : (c == 10 || c == 13 ? ' ' : '.'));
        String cls = o.getClass().getSimpleName();
        return cls.equals("String") || cls.equals("Integer") || cls.equals("Long") || cls.equals("Boolean") ? sb2.toString() : cls + "(" + sb2 + ")";
    }

    public static void logS(String where, Object[] args, Object res) {
        log("[S] " + where + "(" + briefArgs(args) + ") -> " + safe(res));
    }

    public static void logP(String where, Object[] args, Object res) {
        log("[P] " + where + "(" + briefArgs(args) + ") -> " + safe(res));
    }

    static String briefArgs(Object[] a) {
        if (a == null) return "";
        StringBuilder sb = new StringBuilder();
        for (Object o : a) {
            if (sb.length() > 0) sb.append(", ");
            String s = String.valueOf(o);
            sb.append(s.length() > 60 ? s.substring(0, 60) + "..." : s);
        }
        return sb.toString();
    }

    public static void logB(String where, Object[] args, byte[] res) {
        log("[B] " + where + "(" + briefArgs(args) + ") -> " + (res == null ? "null" : res.length + "B: " + hex(res)));
    }

    public static void logK(String where, javax.crypto.SecretKey k) {
        byte[] e = k == null ? null : k.getEncoded();
        log("[K] " + where + " alg=" + (k == null ? "?" : k.getAlgorithm()) + " -> " + (e == null ? "null-encoded" : e.length + "B: " + hex(e)));
    }

    static String hex(byte[] b) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < b.length && i < 128; i++) sb.append(String.format("%02x", b[i]));
        return sb.toString();
    }

    static String safe(Object o) {
        if (o == null) return "null";
        String s = String.valueOf(o);
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            sb.append(c >= 32 && c < 127 ? c : '.');
        }
        return sb.toString();
    }

    public static void logRef(long key, Object res) {
        log("[REF] " + Long.toHexString(key) + " -> " + safe(res));
    }

    public static void logCls(long key, Class<?> c) {
        if (c != null) log("[CLS] " + Long.toHexString(key) + " -> " + c.getName());
    }

    public static void logFld(long key, Field f) {
        if (f != null) log("[FLD] " + Long.toHexString(key) + " -> " + f.getDeclaringClass().getName() + "." + f.getName() + " : " + f.getType().getName());
    }

    public static void logMth(long key, Method m) {
        if (m != null) log("[MTH] " + Long.toHexString(key) + " -> " + m.getDeclaringClass().getName() + "." + m.getName() + java.util.Arrays.toString(m.getParameterTypes()) + " : " + m.getReturnType().getName());
    }
}
