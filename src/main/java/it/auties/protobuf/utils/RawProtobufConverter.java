package it.auties.protobuf.utils;

import org.apache.logging.log4j.core.config.plugins.convert.HexConverter;
import picocli.CommandLine;

public class RawProtobufConverter implements CommandLine.ITypeConverter<byte[]> {
    @Override
    public byte[] convert(String s) {
        return HexConverter.parseHexBinary(s);
    }
}
