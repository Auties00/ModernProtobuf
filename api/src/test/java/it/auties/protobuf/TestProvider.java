package it.auties.protobuf;

import com.fasterxml.jackson.databind.*;
import it.auties.protobuf.api.jackson.ProtobufMapper;

public interface TestProvider {
    ProtobufMapper JACKSON =  new ProtobufMapper();
}
