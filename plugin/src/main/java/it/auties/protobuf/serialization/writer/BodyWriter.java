package it.auties.protobuf.serialization.writer;

import java.io.Writer;

public abstract class BodyWriter extends JavaWriter implements AutoCloseable {
    BodyWriter(Writer out) {
        super(out);
    }

    public String printVariableDeclaration(String fieldName, String fieldValue) {
        printVariableDeclaration(null, fieldName, fieldValue);
        return fieldName;
    }

    public String printVariableDeclaration(String fieldType, String fieldName, String fieldValue) {
        printf("%s %s = %s;%n", fieldType == null ? "var" : fieldType, fieldName, fieldValue);
        return fieldName;
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

    public void printReturn() {
        println("return;");
    }

    public ForEachWriter printForEachStatement(String localVariableName, String accessorCall) {
        printf("for (var %s : %s) { %n", localVariableName, accessorCall);
        return new ForEachWriter(this);
    }

    @Override
    public void close() {
        if (level > 0) {
            level--;
        }

        println("}");
    }
}
