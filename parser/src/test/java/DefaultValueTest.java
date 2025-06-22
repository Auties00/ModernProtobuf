import it.auties.protobuf.parser.ProtobufParser;
import it.auties.protobuf.parser.ProtobufParserException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.Objects;

public class DefaultValueTest {
    @Test
    public void testDefaultValueProto2() throws URISyntaxException, IOException {
        var proto2Source = ClassLoader.getSystemClassLoader().getResource("default_proto2.proto");
        Objects.requireNonNull(proto2Source);
        var document = ProtobufParser.parseOnly(Path.of(proto2Source.toURI()));
        System.out.println(document);
    }

    @Test
    public void testDefaultValueProto2TypeChecking() {
        var malformedSource = ClassLoader.getSystemClassLoader().getResource("default_proto2_malformed.proto");
        Objects.requireNonNull(malformedSource);
        Assertions.assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(Path.of(malformedSource.toURI())));
        var unknownSource = ClassLoader.getSystemClassLoader().getResource("default_proto2_unknown.proto");
        Objects.requireNonNull(unknownSource);
        Assertions.assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(Path.of(unknownSource.toURI())));
    }

    @Test
    public void testDefaultValueProto3() {
        var proto2Source = ClassLoader.getSystemClassLoader().getResource("default_proto3.proto");
        Objects.requireNonNull(proto2Source);
        Assertions.assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(Path.of(proto2Source.toURI())));
    }
}
