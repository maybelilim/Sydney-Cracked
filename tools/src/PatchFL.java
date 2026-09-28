import javassist.*;
import java.io.*;
import java.util.jar.*;
import java.util.zip.*;

public class PatchFL {
    public static void main(String[] args) throws Exception {
        String src = args[0], dst = args[1];
        ClassPool pool = ClassPool.getDefault();
        try (JarInputStream jin = new JarInputStream(new BufferedInputStream(new FileInputStream(src)));
             JarOutputStream jout = new JarOutputStream(new BufferedOutputStream(new FileOutputStream(dst)))) {
            JarEntry e; byte[] buf = new byte[65536];
            while ((e = jin.getNextJarEntry()) != null) {
                String name = e.getName();
                if (name.endsWith("/")) { jout.putNextEntry(new JarEntry(name)); jout.closeEntry(); continue; }
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                int n; while ((n = jin.read(buf)) > 0) bos.write(buf, 0, n);
                byte[] data = bos.toByteArray();
                if (name.equals("net/fabricmc/loader/impl/FabricLoaderImpl.class")) {
                    try {
                        pool.insertClassPath(new ByteArrayClassPath("net.fabricmc.loader.impl.FabricLoaderImpl", data));
                        CtClass cc = pool.get("net.fabricmc.loader.impl.FabricLoaderImpl");
                        for (CtMethod m : cc.getDeclaredMethods()) {
                            if (m.getName().equals("isDevelopmentEnvironment")) {
                                m.setBody("{ return false; }");
                                System.out.println("[PATCHED] FabricLoaderImpl.isDevelopmentEnvironment");
                            }
                        }
                        data = cc.toBytecode();
                    } catch (Throwable t) { System.out.println("[SKIP] " + t); }
                }
                jout.putNextEntry(new JarEntry(name));
                jout.write(data);
                jout.closeEntry();
            }
        }
        System.out.println("done");
    }
}
