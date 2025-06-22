import it.auties.protobuf.parser.ProtobufParser;
import it.auties.protobuf.parser.ProtobufParserException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.Objects;

public class ReservedFieldTest {
    @Test
    public void testValid() throws URISyntaxException, IOException {
        var source = ClassLoader.getSystemClassLoader().getResource("reserved_valid.proto");
        Objects.requireNonNull(source);
        var document = ProtobufParser.parseOnly(Path.of(source.toURI()));
        System.out.println(document);
    }

    @Test
    public void testInvalid() {
        var source = ClassLoader.getSystemClassLoader().getResource("reserved_duplicate.proto");
        Objects.requireNonNull(source);

        Assertions.assertThrows(ProtobufParserException.class, () -> {
            var document = ProtobufParser.parseOnly(Path.of(source.toURI()));
            System.out.println(document);
        });
    }

    @Test
    public void testIllegal() {
        var illegalIndexSource = ClassLoader.getSystemClassLoader().getResource("reserved_illegal_index.proto");
        Objects.requireNonNull(illegalIndexSource);
        Assertions.assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(Path.of(illegalIndexSource.toURI())));
        var illegalNameSource = ClassLoader.getSystemClassLoader().getResource("reserved_illegal_name.proto");
        Objects.requireNonNull(illegalNameSource);
        Assertions.assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(Path.of(illegalNameSource.toURI())));
    }
}
