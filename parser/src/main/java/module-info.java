module it.auties.protoc.parser {
    requires static lombok;
    requires com.google.common;

    exports it.auties.protobuf.model;
    exports it.auties.protobuf.parser;
}