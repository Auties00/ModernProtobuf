open module it.auties.protoc.api {
    requires static lombok;
    requires static java.logging;
    requires it.auties.reflection;
    requires com.fasterxml.jackson.datatype.jdk8;
    requires com.fasterxml.jackson.databind;
    requires jdk.unsupported;

    exports it.auties.protobuf.api.model;
    exports it.auties.protobuf.api.exception;
    exports it.auties.protobuf.api.jackson;
}