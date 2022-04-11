package it.auties.protobuf.application;

import it.auties.protobuf.command.BaseCommand;
import it.auties.reflection.Reflection;
import picocli.CommandLine;

import java.io.OutputStream;
import java.util.Set;

public class ModernProtocApplication {
    private static final Set<String> MODULES = Set.of("com.sun.tools.javac.api", "com.sun.tools.javac.file", "com.sun.tools.javac.parser", "com.sun.tools.javac.tree", "com.sun.tools.javac.util");

    public static void main(String... args) throws NoSuchMethodException, NoSuchFieldException {
        var jdkCompilerModule = findModule("jdk.compiler");
        var googleFormatModule = findModule("com.google.googlejavaformat");

        var addOpensMethod = Module.class.getDeclaredMethod("implAddOpens", String.class, Module.class);
        var addOpensMethodOffset = Reflection.unsafe().objectFieldOffset(ModulePlaceholder.class.getDeclaredField("first"));
        Reflection.unsafe().putBooleanVolatile(addOpensMethod, addOpensMethodOffset, true);

        MODULES.forEach(pack -> {
            try {
                addOpensMethod.invoke(jdkCompilerModule, pack, googleFormatModule);
            }catch (Throwable throwable){
                throw new RuntimeException("Cannot open package", throwable);
            }
        });

        var exitCode = new CommandLine(new BaseCommand()).execute(args);
        System.exit(exitCode);
    }

    private static Module findModule(String moduleName) {
        return ModuleLayer.boot()
                .findModule(moduleName)
                .orElseGet(() -> ClassLoader.getSystemClassLoader().getUnnamedModule());
    }

    @SuppressWarnings("all")
    private static class AccessibleObjectPlaceholder {
        boolean override;
        Object accessCheckCache;
    }

    @SuppressWarnings("all")
    private static class ModulePlaceholder {
        boolean first;
        static final Object staticObj = OutputStream.class;
        volatile Object second;
        private static volatile boolean staticSecond;
        private static volatile boolean staticThird;
    }
}
