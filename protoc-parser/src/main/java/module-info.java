module protoc.parser {
    requires static lombok;
    requires java.compiler;
    requires com.google.common;

    exports it.auties.protobuf.parser;
    exports it.auties.protobuf.ast;
}