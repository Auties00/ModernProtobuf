package it.auties.protobuf.serialization.writer;

import java.util.List;

public class ClassWriter extends BodyWriter implements AutoCloseable {
    ClassWriter(JavaWriter out) {
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

    public MethodWriter printStaticBlock() {
        printf("static {%n");
        return new MethodWriter(this);
    }
}
