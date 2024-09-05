package it.auties.protobuf.serialization.support;

import it.auties.protobuf.serialization.support.JavaWriter.ClassWriter.ConditionalStatementWriter;
import it.auties.protobuf.serialization.support.JavaWriter.ClassWriter.ForEachWriter;
import it.auties.protobuf.serialization.support.JavaWriter.ClassWriter.ForWriter;
import it.auties.protobuf.serialization.support.JavaWriter.ClassWriter.SwitchStatementWriter;

import java.io.PrintWriter;
import java.io.Writer;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public abstract class JavaWriter extends PrintWriter {
    int level;

    public JavaWriter(Writer out) {
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

    public void printSeparator() {
        println();
    }

    public void printComment(String content) {
        println("// " + content);
    }

    public static final class CompilationUnit extends JavaWriter {
        public CompilationUnit(Writer out) {
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

    public abstract static class BodyWriter extends JavaWriter implements AutoCloseable{
        public BodyWriter(Writer out) {
            super(out);
        }

        public void printVariableDeclaration(String fieldName, String fieldValue) {
            printVariableDeclaration(null, fieldName, fieldValue);
        }

        public void printVariableDeclaration(String fieldType, String fieldName, String fieldValue) {
            printf("%s %s = %s;%n", fieldType == null ? "var" : fieldType, fieldName, fieldValue);
        }

        public void printFieldAssignment(String name, String value) {
            printf("%s = %s;%n", name, value);
        }

        public ConditionalStatementWriter printIfStatement(String condition) {
            printf("if (%s) {%n", condition);
            return new ConditionalStatementWriter(this);
        }

        public ConditionalStatementWriter printWhileStatement(String condition) {
            printf("while (%s) {%n", condition);
            return new ConditionalStatementWriter(this);
        }

        public SwitchStatementWriter printSwitchStatement(String condition) {
            printf("switch (%s) {%n", condition);
            return new SwitchStatementWriter(this);
        }

        public void printReturn(String value) {
            printf("return %s;%n", value);
        }

        public ForWriter printForStatement(String initializer, String condition, String body) {
            printf("for (%s; %s; %s) { %n", initializer, condition, body);
            return new ForWriter(this);
        }

        public ForEachWriter printForEachStatement(String localVariableName, String accessorCall) {
            printf("for (var %s : %s) { %n", localVariableName, accessorCall);
            return new ForEachWriter(this);
        }

        @Override
        public void close() {
            if(level > 0) {
                level--;
            }

            println("}");
        }
    }
    
    public static class ClassWriter extends BodyWriter implements AutoCloseable {
        public ClassWriter(JavaWriter out) {
            super(out);
            this.level = out.level + 1;
        }

        public void printFieldDeclaration(String fieldType, String fieldName) {
            printf("private %s %s;%n", fieldType, fieldName);
        }

        public MethodWriter printConstructorDeclaration(String className, String... parameters) {
            printf("public %s(%s) {%n", className, String.join(" ", parameters));
            return new MethodWriter(this);
        }

        public MethodWriter printMethodDeclaration(String returnType, String methodName, String... parameters) {
            return printMethodDeclaration(List.of("public"), returnType, methodName, parameters);
        }

        public MethodWriter printMethodDeclaration(List<String> modifiers, String returnType, String methodName, String... parameters) {
            printf("%s %s %s(%s) {%n", String.join(" ", modifiers), returnType, methodName, String.join(", ", parameters));
            return new MethodWriter(this);
        }

        public static class ConditionalStatementWriter extends BodyWriter implements AutoCloseable {
            public ConditionalStatementWriter(JavaWriter out) {
                super(out);
                this.level = out.level + 1;
            }
        }

        public static class SwitchStatementWriter extends ConditionalStatementWriter implements AutoCloseable {
            public SwitchStatementWriter(JavaWriter out) {
                super(out);
                this.level = out.level + 1;
            }

            public void printSwitchBranch(String condition, String body) {
                printf("%s -> %s;%n", toCase(condition), body);
            }

            public SwitchBranchWriter printSwitchBranch(String condition) {
                printf("%s -> {%n", toCase(condition));
                return new SwitchBranchWriter(this);
            }

            private String toCase(String condition) {
                return Objects.equals(condition, "default") ? condition : "case " + condition;
            }
        }

        public static class SwitchBranchWriter extends BodyWriter implements AutoCloseable {
            public SwitchBranchWriter(JavaWriter out) {
                super(out);
                this.level = out.level + 1;
            }
        }

        public static class ForEachWriter extends BodyWriter implements AutoCloseable {
            public ForEachWriter(JavaWriter out) {
                super(out);
                this.level = out.level + 1;
            }
        }

        public static class ForWriter extends BodyWriter implements AutoCloseable {
            public ForWriter(JavaWriter out) {
                super(out);
                this.level = out.level + 1;
            }
        }

        public static class MethodWriter extends BodyWriter implements AutoCloseable {
            public MethodWriter(JavaWriter out) {
                super(out);
                this.level = out.level + 1;
            }

            @Override
            public void close() {
                super.close();
                println();
            }
        }
    }
}
