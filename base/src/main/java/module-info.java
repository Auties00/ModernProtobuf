module it.auties.protobuf.base {
    requires transitive jdk.compiler;
    requires jdk.unsupported;
    requires java.desktop;

    exports it.auties.protobuf.exception;
    exports it.auties.protobuf.annotation;
    exports it.auties.protobuf.stream;
    exports it.auties.protobuf.model;
    exports it.auties.protobuf.builtin;
}