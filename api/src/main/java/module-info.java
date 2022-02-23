open module it.auties.protoc.api {
    requires static lombok;
    requires static java.logging;
    requires com.fasterxml.jackson.datatype.jdk8;
    requires com.fasterxml.jackson.databind;
    requires jdk.unsupported;
    requires io.github.classgraph;

    exports it.auties.protobuf.decoder;
    exports it.auties.protobuf.encoder;
    exports it.auties.protobuf.annotation;
}