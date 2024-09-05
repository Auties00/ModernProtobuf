module it.auties.protobuf.parser {
    requires it.auties.protobuf.base;
    requires java.compiler;

    exports it.auties.protobuf.parser;
    exports it.auties.protobuf.parser.type;
    exports it.auties.protobuf.parser.tree;
    exports it.auties.protobuf.parser.tree.nested.option;
    exports it.auties.protobuf.parser.tree.nested.field;
    exports it.auties.protobuf.parser.tree.body;
    exports it.auties.protobuf.parser.tree.body.document;
    exports it.auties.protobuf.parser.tree.body.oneof;
    exports it.auties.protobuf.parser.tree.body.object;
    exports it.auties.protobuf.parser.tree.nested;
    exports it.auties.protobuf.parser.tree.nested.imports;
}