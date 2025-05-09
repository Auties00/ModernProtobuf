package it.auties.protobuf.serialization.writer;

public class ConditionalStatementWriter extends BodyWriter implements AutoCloseable {
    ConditionalStatementWriter(JavaWriter out) {
        super(out);
        this.level = out.level + 1;
    }
}
