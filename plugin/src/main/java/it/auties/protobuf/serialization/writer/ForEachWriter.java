package it.auties.protobuf.serialization.writer;

public class ForEachWriter extends BodyWriter implements AutoCloseable {
    ForEachWriter(JavaWriter out) {
        super(out);
        this.level = out.level + 1;
    }
}
