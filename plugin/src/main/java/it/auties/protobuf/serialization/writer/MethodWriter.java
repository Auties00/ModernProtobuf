package it.auties.protobuf.serialization.writer;

public class MethodWriter extends BodyWriter implements AutoCloseable {
    MethodWriter(JavaWriter out) {
        super(out);
        this.level = out.level + 1;
    }

    @Override
    public void close() {
        super.close();
        println();
    }
}
