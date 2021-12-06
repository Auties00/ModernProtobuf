package it.auties.protobuf.commands;

import it.auties.protobuf.decoder.ProtobufDecoder;
import it.auties.protobuf.utils.RawProtobufConverter;
import lombok.extern.log4j.Log4j2;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.Callable;

@Log4j2
@Command(name = "decode", mixinStandardHelpOptions = true, version = "generate 1.0", description = "Decodes a protobuf message encoded as binary data")
public class DecodeCommand implements Callable<Integer> {
    @SuppressWarnings("FieldMayBeFinal")
    @Parameters(index = "0", converter = RawProtobufConverter.class, description = "The protobuf message to decode, can be an array of bytes or an hex string")
    private byte[] protobuf = null;

    @Override
    public Integer call() {
        try {
            var result = ProtobufDecoder.forType(Map.class).decodeAsJson(protobuf);
            log.info(result);
            return 0;
        }catch (IOException ex){
            log.error("An uncaught exception was thrown, report this incident on github if you believe this to be a bug");
            log.throwing(ex);
            return -1;
        }
    }
}
