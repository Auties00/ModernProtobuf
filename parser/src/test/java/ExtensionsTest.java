import it.auties.protobuf.parser.ProtobufParser;
import it.auties.protobuf.parser.ProtobufParserException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.Objects;

public class ExtensionsTest {
    @Test
    public void testValid() throws URISyntaxException, IOException {
        var source = ClassLoader.getSystemClassLoader().getResource("extensions_valid.proto");
        Objects.requireNonNull(source);
        var document = ProtobufParser.parseOnly(Path.of(source.toURI()));
        System.out.println(document);
    }

    @Test
    public void testInvalid() {
        var source = ClassLoader.getSystemClassLoader().getResource("extensions_duplicate.proto");
        Objects.requireNonNull(source);
        Assertions.assertThrows(ProtobufParserException.class, () -> {
            var document = ProtobufParser.parseOnly(Path.of(source.toURI()));
            System.out.println(document);
        });
    }

    @Test
    public void testIllegal() {
        {
            var illegalIndexSource = ClassLoader.getSystemClassLoader().getResource("extensions_illegal_index.proto");
            Objects.requireNonNull(illegalIndexSource);
            Assertions.assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(Path.of(illegalIndexSource.toURI())));
            var illegalNameSource = ClassLoader.getSystemClassLoader().getResource("extensions_illegal_value.proto");
            Objects.requireNonNull(illegalNameSource);
            Assertions.assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(Path.of(illegalNameSource.toURI())));
            var illegalNoMinSource = ClassLoader.getSystemClassLoader().getResource("extensions_illegal_no_min.proto");
            Objects.requireNonNull(illegalNoMinSource);
            Assertions.assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(Path.of(illegalNoMinSource.toURI())));
            var illegalNoMaxSource = ClassLoader.getSystemClassLoader().getResource("extensions_illegal_no_max.proto");
            Objects.requireNonNull(illegalNoMaxSource);
            Assertions.assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(Path.of(illegalNoMaxSource.toURI())));
        }
        {
            var illegalIndexSource = ClassLoader.getSystemClassLoader().getResource("extend_illegal_field.proto");
            Objects.requireNonNull(illegalIndexSource);
            Assertions.assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(Path.of(illegalIndexSource.toURI())));
            var illegalNameSource = ClassLoader.getSystemClassLoader().getResource("extend_illegal_field1.proto");
            Objects.requireNonNull(illegalNameSource);
            Assertions.assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(Path.of(illegalNameSource.toURI())));
            var illegalNoMinSource = ClassLoader.getSystemClassLoader().getResource("extend_illegal_no_fields.proto");
            Objects.requireNonNull(illegalNoMinSource);
            Assertions.assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(Path.of(illegalNoMinSource.toURI())));
        }
    }
}
