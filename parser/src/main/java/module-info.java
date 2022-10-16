module it.auties.protobuf.parser {
    requires static lombok;
    requires it.auties.protobuf.base;
    requires java.compiler;

    exports it.auties.protobuf.parser;
    exports it.auties.protobuf.parser.statement;
    exports it.auties.protobuf.parser.exception;
    exports it.auties.protobuf.parser.type;
}