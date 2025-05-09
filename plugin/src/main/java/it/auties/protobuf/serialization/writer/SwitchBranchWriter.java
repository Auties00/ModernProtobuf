package it.auties.protobuf.serialization.writer;

public class SwitchBranchWriter extends BodyWriter implements AutoCloseable {
    SwitchBranchWriter(JavaWriter out) {
        super(out);
        this.level = out.level + 1;
    }
}
