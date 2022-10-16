open module it.auties.protobuf.serializer {
    requires static lombok;
    requires static java.logging;
    requires it.auties.protobuf.base;
    requires it.auties.reflection;
    requires com.fasterxml.jackson.datatype.jdk8;
    requires com.fasterxml.jackson.databind;
    requires jdk.unsupported;

    exports it.auties.protobuf.serializer.exception;
    exports it.auties.protobuf.serializer.jackson;
}