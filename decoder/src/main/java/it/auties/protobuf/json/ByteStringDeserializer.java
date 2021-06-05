package it.auties.protobuf.json;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;

public class ByteStringDeserializer extends JsonDeserializer<byte[]> {
    @Override
    public byte[] deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        return parser.getCodec().readValue(parser, String.class).getBytes();
    }
}
