package it.auties.protobuf;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.auties.protobuf.jackson.ProtobufMapper;

public interface TestProvider {
    ObjectMapper JACKSON =  new ProtobufMapper();
}
