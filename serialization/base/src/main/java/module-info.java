open module it.auties.protobuf.serializer.base {
    requires static lombok;
    requires static java.logging;
    requires jdk.unsupported;

    exports it.auties.protobuf.serialization.stream;
    exports it.auties.protobuf.serialization.exception;
    exports it.auties.protobuf.serialization.model;
}