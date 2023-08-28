package it.auties.proto.benchmark;

import com.fasterxml.jackson.annotation.JsonProperty;

@SuppressWarnings("unused")
public class JacksonScalarMessage {
    @JsonProperty("fixed32")
    private int fixed32;

    @JsonProperty("sfixed32")
    private int sfixed32;

    @JsonProperty("int32")
    private int int32;

    @JsonProperty("uint32")
    private int uint32;

    @JsonProperty("fixed64")
    private long fixed64;

    @JsonProperty("sfixed64")
    private long sfixed64;

    @JsonProperty("int64")
    private long int64;

    @JsonProperty("uint64")
    private long uint64;

    @JsonProperty("float")
    private float _float;

    @JsonProperty("double")
    private double _double;

    @JsonProperty("bool")
    private boolean bool;

    @JsonProperty("string")
    private String string;

    @JsonProperty("bytes")
    private byte[] bytes;
}
