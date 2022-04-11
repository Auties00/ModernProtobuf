package it.auties.protobuf.command;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.auties.protobuf.utils.RawProtobufConverter;
import lombok.extern.log4j.Log4j2;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.Callable;

@Log4j2
@Command(
        name = "decode",
        mixinStandardHelpOptions = true,
        version = "decode 1.0",
        description = "Decodes a protobuf message encoded as binary data"
)
public class DecodeCommand implements Callable<Integer> {
    private static final ObjectMapper JACKSON = new ObjectMapper()
            .registerModule(new ProtobufModule());

    @SuppressWarnings("FieldMayBeFinal")
    @Parameters(
            index = "0",
            converter = RawProtobufConverter.class,
            description = "The protobuf message to decode, can be an array of bytes or an hex string"
    )
    private byte[] protobuf = null;

    @Override
    public Integer call() {
        try {
            var result = JACKSON.readValue(protobuf,
                    new TypeReference<Map<Integer, Object>>() {});
            log.info(result);
            return 0;
        } catch (IOException ex) {
            log.info("Cannot parse Protobuf message");
            log.throwing(ex);
            return -1;
        }
    }
}
