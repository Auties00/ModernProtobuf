import it.auties.protobuf.parser.ProtobufParser;
import it.auties.protobuf.parser.ProtobufParserException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Objects;

public class IllegalNameTest {
    @Test
    public void testIllegalSymbol(){
        var proto2Source = ClassLoader.getSystemClassLoader().getResource("illegal_name_symbol.proto");
        Objects.requireNonNull(proto2Source);
        Assertions.assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(Path.of(proto2Source.toURI())));
    }

    @Test
    public void testIllegalSymbolVariant(){
        var proto2Source = ClassLoader.getSystemClassLoader().getResource("illegal_name_symbol_variant.proto");
        Objects.requireNonNull(proto2Source);
        Assertions.assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(Path.of(proto2Source.toURI())));
    }

    @Test
    public void testIllegalNumber(){
        var proto2Source = ClassLoader.getSystemClassLoader().getResource("illegal_name_number.proto");
        Objects.requireNonNull(proto2Source);
        Assertions.assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(Path.of(proto2Source.toURI())));
    }

    @Test
    public void testLegalName(){
        var proto2Source = ClassLoader.getSystemClassLoader().getResource("legal_name_number.proto");
        Objects.requireNonNull(proto2Source);
        Assertions.assertDoesNotThrow(() -> ProtobufParser.parseOnly(Path.of(proto2Source.toURI())));
    }
}
