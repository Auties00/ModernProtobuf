package it.auties.protobuf.serialization.model;

public record ProtobufPropertyConverter(String serializerName, String serializerDescriptor, String deserializerName,
                                        String deserializerDescriptor) {

}
