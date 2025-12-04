module it.auties.protobuf.base {
    requires java.compiler;
    requires jdk.incubator.vector;

    exports it.auties.protobuf.exception;
    exports it.auties.protobuf.annotation;
    exports it.auties.protobuf.stream;
    exports it.auties.protobuf.model;
    exports it.auties.protobuf.builtin;
}