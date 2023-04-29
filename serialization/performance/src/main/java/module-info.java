open module it.auties.protobuf.serializer.performance {
    requires static lombok;
    requires static java.logging;
    requires it.auties.protobuf.base;
    requires it.auties.protobuf.serializer.base;
    requires jdk.unsupported;
    requires java.compiler;

    exports it.auties.protobuf.serialization.performance;
}