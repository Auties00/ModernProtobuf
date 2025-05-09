package it.auties.protobuf.serialization.model.converter;

import it.auties.protobuf.serialization.model.converter.attributed.ProtobufAttributedConverterElement;
import it.auties.protobuf.serialization.model.converter.unattributed.ProtobufUnattributedConverterElement;

public sealed interface ProtobufConverterElement permits ProtobufAttributedConverterElement, ProtobufUnattributedConverterElement {

}
