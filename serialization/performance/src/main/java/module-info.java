import it.auties.protobuf.serialization.performance.processor.ProtobufPropertyProcessor;

open module it.auties.protobuf.serializer.performance {
    requires static lombok;
    requires static java.logging;
    requires it.auties.protobuf.base;
    requires it.auties.protobuf.serializer.base;
    requires jdk.unsupported;
    requires java.compiler;

    exports it.auties.protobuf.serialization.performance;
    exports it.auties.protobuf.serialization.performance.model;

    provides javax.annotation.processing.Processor with ProtobufPropertyProcessor;
}