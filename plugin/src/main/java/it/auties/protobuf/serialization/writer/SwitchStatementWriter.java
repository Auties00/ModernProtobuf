package it.auties.protobuf.serialization.writer;

import java.util.Objects;

public class SwitchStatementWriter extends ConditionalStatementWriter implements AutoCloseable {
    SwitchStatementWriter(JavaWriter out) {
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
