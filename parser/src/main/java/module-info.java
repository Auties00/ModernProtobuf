module it.auties.protoc.parser {
    requires static lombok;

    exports it.auties.protobuf.parser;
    exports it.auties.protobuf.parser.statement;
    exports it.auties.protobuf.parser.object;
    exports it.auties.protobuf.parser.model;
}