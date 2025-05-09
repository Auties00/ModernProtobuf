package it.auties.protobuf.serialization.model.converter.attributed;

import it.auties.protobuf.serialization.model.converter.ProtobufConverterElement;

public sealed interface ProtobufAttributedConverterElement extends ProtobufConverterElement
        permits ProtobufAttributedConverterDeserializer, ProtobufAttributedConverterSerializer {

}
