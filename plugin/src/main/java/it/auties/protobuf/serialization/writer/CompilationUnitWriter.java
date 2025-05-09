package it.auties.protobuf.serialization.writer;

import java.io.Writer;

public final class CompilationUnitWriter extends JavaWriter {
    public CompilationUnitWriter(Writer out) {
        super(out);
    }

    public void printPackageDeclaration(String packageName) {
        printf("package %s;%n", packageName);
        printSeparator();
    }

    public void printImportDeclaration(String importName) {
        printf("import %s;%n", importName);
    }

    public ClassWriter printClassDeclaration(String className) {
        printf("public class %s {%n", className);
        return new ClassWriter(this);
    }
}
