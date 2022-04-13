package it.auties.protobuf;

import com.fasterxml.jackson.databind.*;
import it.auties.protobuf.api.jackson.ProtobufMapper;

public interface TestProvider {
    ObjectMapper JACKSON =  new ProtobufMapper();
}
