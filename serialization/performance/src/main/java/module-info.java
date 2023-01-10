open module it.auties.protobuf.serializer.jackson {
    requires static lombok;
    requires static java.logging;
    requires it.auties.protobuf.base;
    requires it.auties.protobuf.serializer.base;
    requires jdk.unsupported;

    exports it.auties.protobuf.serialization.performance;
}