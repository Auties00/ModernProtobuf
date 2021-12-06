module it.auties.protoc.api {
    requires static lombok;
    requires com.fasterxml.jackson.datatype.jdk8;
    requires com.fasterxml.jackson.databind;
    requires jdk.unsupported;

    exports it.auties.protobuf.decoder;
    exports it.auties.protobuf.encoder;
}