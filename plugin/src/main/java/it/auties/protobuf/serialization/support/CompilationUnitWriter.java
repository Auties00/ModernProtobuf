package it.auties.protobuf.serialization.support;

import java.io.PrintWriter;
import java.io.Writer;
import java.util.List;
import java.util.Locale;

public class CompilationUnitWriter extends PrintWriter {
    int level;

    public CompilationUnitWriter(Writer out) {
        super(out);
    }

    @Override
    public void print(String s) {
        super.print("    ".repeat(level) + s);
    }

    @Override
    public PrintWriter format(@SuppressWarnings("NullableProblems") String format, Object... args) {
        return super.format("    ".repeat(level) + format, args);
    }

    @Override
    public PrintWriter format(Locale l, @SuppressWarnings("NullableProblems") String format, Object... args) {
        return super.format(l, "    ".repeat(level) + format, args);
    }

    public void printPackageDeclaration(String packageName) {
        printf("package %s;%n", packageName);
        printClassSeparator();
    }

    public void printImportDeclaration(String importName) {
        printf("import %s;%n", importName);
        printClassSeparator();
    }

    public NestedClassWriter printClassDeclaration(String className) {
        printf("public class %s {%n", className);
        return new NestedClassWriter(this);
    }

    public void printClassSeparator() {
        println();
    }
    
    public static class NestedClassWriter extends CompilationUnitWriter implements AutoCloseable {
        public NestedClassWriter(CompilationUnitWriter out) {
            super(out);
            this.level = out.level + 1;
        }

        public void printFieldDeclaration(String fieldType, String fieldName) {
            printf("private %s %s;%n", fieldType, fieldName);
        }

        public void printFieldAssignment(String name, String value) {
            printf("%s = %s;%n", name, value);
        }

        public NestedClassWriter printConstructorDeclaration(String className, String... parameters) {
            printf("public %s(%s) {%n", className, String.join(" ", parameters));
            return new NestedClassWriter(this);
        }

        public NestedClassWriter printMethodDeclaration(String returnType, String methodName, String... parameters) {
            return printMethodDeclaration(List.of("public"), returnType, methodName, parameters);
        }

        public NestedClassWriter printMethodDeclaration(List<String> modifiers, String returnType, String methodName, String... parameters) {
            printf("%s %s %s(%s) {%n", String.join(" ", modifiers), returnType, methodName, String.join(" ", parameters));
            return new NestedClassWriter(this);
        }

        public void printReturn(String value) {
            printf("return %s;%n", value);
        }

        @Override
        public void close() {
            if(level > 0) {
                level--;
            }

            println("}");
            println();
        }
    }
}
