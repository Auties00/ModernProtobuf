open module it.auties.protoc.api {
    requires static lombok;
    requires static java.logging;
    requires it.auties.reflection;
    requires com.fasterxml.jackson.datatype.jdk8;
    requires com.fasterxml.jackson.databind;
    requires jdk.unsupported;
    requires io.github.classgraph;

    exports it.auties.protobuf.model;
    exports it.auties.protobuf.exception;
    exports it.auties.protobuf.jackson;
}