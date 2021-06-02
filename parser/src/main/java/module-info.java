module protoc.parser {
    requires static lombok;
    requires java.compiler;

    exports it.auties.protobuf.parser;
    exports it.auties.protobuf.ast;
}