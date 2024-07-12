import it.auties.protobuf.parser.ProtobufParser;
import it.auties.protobuf.parser.exception.ProtobufSyntaxException;
import it.auties.protobuf.parser.exception.ProtobufTypeException;
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
        var parser = new ProtobufParser();
        var document = parser.parseOnly(Path.of(proto2Source.toURI()));
        System.out.println(document);
    }

    @Test
    public void testDefaultValueProto2TypeChecking() {
        var parser = new ProtobufParser();
        var malformedSource = ClassLoader.getSystemClassLoader().getResource("default_proto2_malformed.proto");
        Objects.requireNonNull(malformedSource);
        Assertions.assertThrows(ProtobufTypeException.class, () -> parser.parseOnly(Path.of(malformedSource.toURI())));
        var unknownSource = ClassLoader.getSystemClassLoader().getResource("default_proto2_unknown.proto");
        Objects.requireNonNull(unknownSource);
        Assertions.assertThrows(ProtobufTypeException.class, () -> parser.parseOnly(Path.of(unknownSource.toURI())));
    }

    @Test
    public void testDefaultValueProto3() {
        var proto2Source = ClassLoader.getSystemClassLoader().getResource("default_proto3.proto");
        Objects.requireNonNull(proto2Source);
        var parser = new ProtobufParser();
        Assertions.assertThrows(ProtobufSyntaxException.class, () -> parser.parseOnly(Path.of(proto2Source.toURI())));
    }
}
