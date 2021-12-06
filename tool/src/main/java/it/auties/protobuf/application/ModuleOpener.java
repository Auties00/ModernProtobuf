package it.auties.protobuf.application;

import lombok.experimental.UtilityClass;
import sun.misc.Unsafe;

import java.io.OutputStream;
import java.lang.reflect.Method;
import java.util.NoSuchElementException;
import java.util.Set;

@UtilityClass
public class ModuleOpener {
    private final Unsafe unsafe = openUnsafe();
    private final Set<String> MODULES = Set.of("com.sun.tools.javac.api", "com.sun.tools.javac.file", "com.sun.tools.javac.parser", "com.sun.tools.javac.tree", "com.sun.tools.javac.util");
    private final String GOOGLE_MODULE = "com.google.googlejavaformat";

    public void openJavac(){
        try {
            var jdkCompilerModule = findModule("jdk.compiler");

            var addOpensMethod = Module.class.getDeclaredMethod("implAddOpens", String.class, Module.class);
            var addOpensMethodOffset = unsafe.objectFieldOffset(ModulePlaceholder.class.getDeclaredField("first"));
            unsafe.putBooleanVolatile(addOpensMethod, addOpensMethodOffset, true);
            MODULES.forEach(pack -> openPackage(jdkCompilerModule, addOpensMethod, pack));
        }catch (Throwable throwable){
            throw new UnsupportedOperationException("Cannot open Javac Modules", throwable);
        }
    }

    private void openPackage(Module jdkCompilerModule, Method addOpensMethod, String pack) {
        try {
            addOpensMethod.invoke(jdkCompilerModule, pack, findModule(GOOGLE_MODULE));
        }catch (Throwable throwable){
            throw new RuntimeException("Cannot open package", throwable);
        }
    }

    private Module findModule(String moduleName) {
        return ModuleLayer.boot()
                .findModule(moduleName)
                .orElseThrow(() -> new ExceptionInInitializerError("Missing module: %s".formatted(moduleName)));
    }

    private Unsafe openUnsafe() {
        try {
            var unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
            unsafeField.setAccessible(true);
            return (Unsafe) unsafeField.get(null);
        }catch (NoSuchFieldException exception){
            throw new NoSuchElementException("Cannot find unsafe field in wrapper class");
        }catch (IllegalAccessException exception){
            throw new UnsupportedOperationException("Access to the unsafe wrapper has been blocked: the day has come. In this future has the OpenJDK team created a publicly available compiler api that can do something? Probably not", exception);
        }
    }

    @SuppressWarnings("all")
    private static class AccessibleObjectPlaceholder {
        boolean override;
        Object accessCheckCache;
    }

    @SuppressWarnings("all")
    public static class ModulePlaceholder {
        boolean first;
        static final Object staticObj = OutputStream.class;
        volatile Object second;
        private static volatile boolean staticSecond;
        private static volatile boolean staticThird;
    }
}
