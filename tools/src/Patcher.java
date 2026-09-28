import javassist.*;
import javassist.expr.ExprEditor;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.jar.*;
import java.util.zip.*;

public class Patcher {
    public static void main(String[] args) throws Exception {
        String srcJar = args[0];
        String dstJar = args[1];
        String hookClass = args[2];

        ClassPool pool = ClassPool.getDefault();
        int patched = 0, total = 0;

        try (JarInputStream jin = new JarInputStream(new BufferedInputStream(new FileInputStream(srcJar)))) {
            Manifest mf = jin.getManifest();
            try (JarOutputStream jout = mf != null ? new JarOutputStream(new BufferedOutputStream(new FileOutputStream(dstJar)), mf)
                                                   : new JarOutputStream(new BufferedOutputStream(new FileOutputStream(dstJar)))) {
                JarEntry e;
                byte[] buf = new byte[65536];
                while ((e = jin.getNextJarEntry()) != null) {
                    String name = e.getName();
                    if (name.endsWith("/")) { jout.putNextEntry(new JarEntry(name)); jout.closeEntry(); continue; }
                    ByteArrayOutputStream bos = new ByteArrayOutputStream();
                    int n; while ((n = jin.read(buf)) > 0) bos.write(buf, 0, n);
                    byte[] data = bos.toByteArray();
                    total++;
                    String cn = name.endsWith(".class") ? name.substring(0, name.length() - 6).replace('/', '.') : null;
                    if (cn != null && (cn.startsWith("net.sydneyclient.loader") || cn.startsWith("D."))) {
                        try {
                            pool.insertClassPath(new ByteArrayClassPath(cn, data));
                            CtClass cc = pool.get(cn);
                            int w = 0;
                            for (CtMethod m : cc.getDeclaredMethods()) {
                                String desc = m.getMethodInfo2().getDescriptor();
                                boolean stat = javassist.Modifier.isStatic(m.getModifiers());
                                String in = m.getName();

                                // string pool decryptor: static String a(int,int)
                                if (stat && in.equals("a") && desc.equals("(II)Ljava/lang/String;")) {
                                    String newName = "a$orig" + (++Uid.n);
                                    m.setName(newName);
                                    cc.addMethod(CtNewMethod.make(
                                        "public static String a(int n, int n2){" +
                                        "String r=" + newName + "(n,n2);" +
                                        hookClass + ".logStr(\"" + cn + "\",r);return r;}", cc));
                                    w++;
                                }
                                // D.i final dispatch: a(Lookup,MutableCallSite,String,MethodType,Object[]) -> Object
                                if (cn.equals("D.i") && stat && in.equals("a")
                                        && desc.startsWith("(Ljava/lang/invoke/MethodHandles$Lookup;")
                                        && desc.endsWith("[Ljava/lang/Object;)Ljava/lang/Object;")) {
                                    String newName = "disp$orig" + (++Uid.n);
                                    m.setName(newName);
                                    cc.addMethod(CtNewMethod.make(
                                        "public static Object a(java.lang.invoke.MethodHandles$Lookup l, java.lang.invoke.MutableCallSite cs, String s, java.lang.invoke.MethodType mt, Object[] a) {" +
                                        "Object r = " + newName + "(l, cs, s, mt, a);" +
                                        "try{" + hookClass + ".logDispatch(\"" + cn + "\", s, a, r);}catch(Throwable t){}" +
                                        "return r;}", cc));
                                    w++;
                                }
                                // D.i second-stage: a(long)->int, b(long)->Class, c(long)->Field, d(long)->Method
                                else if (cn.equals("D.i") && stat) {
                                    String newName = "x$orig" + (++Uid.n);
                                    if (in.equals("a") && desc.equals("(J)I")) {
                                        m.setName(newName);
                                        cc.addMethod(CtNewMethod.make(
                                            "public static int a(long l){" +
                                            "int r=" + newName + "(l);" +
                                            "try{" + hookClass + ".logRef(l,b[r]);}catch(Throwable t){}" +
                                            "return r;}", cc));
                                        w++;
                                    } else if (in.equals("b") && desc.equals("(J)Ljava/lang/Class;")) {
                                        m.setName(newName);
                                        cc.addMethod(CtNewMethod.make(
                                            "public static Class b(long l){" +
                                            "Class r=" + newName + "(l);" +
                                            "try{" + hookClass + ".logCls(l,r);}catch(Throwable t){}" +
                                            "return r;}", cc));
                                        w++;
                                    } else if (in.equals("c") && desc.equals("(J)Ljava/lang/reflect/Field;")) {
                                        m.setName(newName);
                                        cc.addMethod(CtNewMethod.make(
                                            "public static java.lang.reflect.Field c(long l){" +
                                            "java.lang.reflect.Field r=" + newName + "(l);" +
                                            "try{" + hookClass + ".logFld(l,r);}catch(Throwable t){}" +
                                            "return r;}", cc));
                                        w++;
                                    } else if (in.equals("d") && desc.equals("(J)Ljava/lang/reflect/Method;")) {
                                        m.setName(newName);
                                        cc.addMethod(CtNewMethod.make(
                                            "public static java.lang.reflect.Method d(long l){" +
                                            "java.lang.reflect.Method r=" + newName + "(l);" +
                                            "try{" + hookClass + ".logMth(l,r);}catch(Throwable t){}" +
                                            "return r;}", cc));
                                        w++;
                                    }
                                }
                                // any other static String-returning method (final plaintext strings)
                                else if (stat && !desc.equals("(II)Ljava/lang/String;") && desc.endsWith("Ljava/lang/String;")) {
                                    String newName = "s$orig" + (++Uid.n);
                                    m.setName(newName);
                                    CtClass[] ps = m.getParameterTypes();
                                    StringBuilder sig = new StringBuilder(), call = new StringBuilder();
                                    for (int pi = 0; pi < ps.length; pi++) {
                                        if (pi > 0) { sig.append(","); call.append(","); }
                                        String tn = ps[pi].getName();
                                        sig.append(tn).append(" p").append(pi);
                                        call.append("p").append(pi);
                                    }
                                    cc.addMethod(CtNewMethod.make(
                                        "public static String " + in + "(" + sig + "){" +
                                        "String r=" + newName + "(" + call + ");" +
                                        "try{" + hookClass + ".logS(\"" + cn + "." + in + "\", $args, r);}catch(Throwable t){}" +
                                        "return r;}", cc));
                                    w++;
                                }
                                // static byte[]-returning methods (crypto boundary)
                                else if (stat && desc.endsWith("[B")) {
                                    String newName = "b$orig" + (++Uid.n);
                                    m.setName(newName);
                                    CtClass[] ps = m.getParameterTypes();
                                    StringBuilder sig = new StringBuilder(), call = new StringBuilder();
                                    for (int pi = 0; pi < ps.length; pi++) {
                                        if (pi > 0) { sig.append(","); call.append(","); }
                                        String tn = ps[pi].getName();
                                        sig.append(tn).append(" p").append(pi);
                                        call.append("p").append(pi);
                                    }
                                    cc.addMethod(CtNewMethod.make(
                                        "public static byte[] " + in + "(" + sig + "){" +
                                        "byte[] r=" + newName + "(" + call + ");" +
                                        "try{" + hookClass + ".logB(\"" + cn + "." + in + "\", $args, r);}catch(Throwable t){}" +
                                        "return r;}", cc));
                                    w++;
                                }
                                // SecretKey-returning (capture AES key material)
                                else if (stat && desc.endsWith("Ljavax/crypto/SecretKey;")) {
                                    String newName = "k$orig" + (++Uid.n);
                                    m.setName(newName);
                                    CtClass[] ps = m.getParameterTypes();
                                    StringBuilder sig = new StringBuilder(), call = new StringBuilder();
                                    for (int pi = 0; pi < ps.length; pi++) {
                                        if (pi > 0) { sig.append(","); call.append(","); }
                                        String tn = ps[pi].getName();
                                        sig.append(tn).append(" p").append(pi);
                                        call.append("p").append(pi);
                                    }
                                    cc.addMethod(CtNewMethod.make(
                                        "public static javax.crypto.SecretKey " + in + "(" + sig + "){" +
                                        "javax.crypto.SecretKey r=" + newName + "(" + call + ");" +
                                        "try{" + hookClass + ".logK(\"" + cn + "." + in + "\", r);}catch(Throwable t){}" +
                                        "return r;}", cc));
                                    w++;
                                }
                                // static Path-returning methods
                                else if (stat && desc.endsWith("Ljava/nio/file/Path;")) {
                                    String newName = "p$orig" + (++Uid.n);
                                    m.setName(newName);
                                    CtClass[] ps = m.getParameterTypes();
                                    StringBuilder sig = new StringBuilder(), call = new StringBuilder();
                                    for (int pi = 0; pi < ps.length; pi++) {
                                        if (pi > 0) { sig.append(","); call.append(","); }
                                        String tn = ps[pi].getName();
                                        sig.append(tn).append(" p").append(pi);
                                        call.append("p").append(pi);
                                    }
                                    cc.addMethod(CtNewMethod.make(
                                        "public static java.nio.file.Path " + in + "(" + sig + "){" +
                                        "java.nio.file.Path r=" + newName + "(" + call + ");" +
                                        "try{" + hookClass + ".logP(\"" + cn + "." + in + "\", $args, r);}catch(Throwable t){}" +
                                        "return r;}", cc));
                                    w++;
                                }
                            }
                            if (w > 0) {
                                data = cc.toBytecode();
                                patched++;
                                System.out.println("[PATCHED] " + cn + " (" + w + ")");
                            }
                        } catch (Throwable t) {
                            System.out.println("[SKIP] " + cn + " : " + t);
                        }
                    }
                    JarEntry out = new JarEntry(name);
                    jout.putNextEntry(out);
                    jout.write(data);
                    jout.closeEntry();
                }
                // add HookRuntime
                try (InputStream hi = Patcher.class.getResourceAsStream("/HookRuntime.class")) {
                    ByteArrayOutputStream hb = new ByteArrayOutputStream();
                    byte[] hb2 = new byte[8192]; int r;
                    while ((r = hi.read(hb2)) > 0) hb.write(hb2, 0, r);
                    jout.putNextEntry(new JarEntry("HookRuntime.class"));
                    jout.write(hb.toByteArray());
                    jout.closeEntry();
                }
            }
        }
        System.out.println("done. patched " + patched + "/" + total + " entries");
    }
}

class Uid { static int n = 0; }
