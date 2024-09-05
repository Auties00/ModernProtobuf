import it.auties.protobuf.parser.ProtobufParser;
import it.auties.protobuf.parser.ProtobufParserException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.Objects;

public class OptionsTest {
    @Test
    public void testDocumentOptions() throws URISyntaxException, IOException {
        var proto2Source = ClassLoader.getSystemClassLoader().getResource("document_options.proto");
        Objects.requireNonNull(proto2Source);
        var parser = new ProtobufParser();
        var document = parser.parseOnly(Path.of(proto2Source.toURI()));
        System.out.println(document);
    }

    @Test
    public void testDocumentOptionsUnknown() {
        var proto2Source = ClassLoader.getSystemClassLoader().getResource("document_options_unknown.proto");
        Objects.requireNonNull(proto2Source);
        var parser = new ProtobufParser();
        Assertions.assertThrows(ProtobufParserException.class, () -> parser.parseOnly(Path.of(proto2Source.toURI())));
    }

    @Test
    public void testDocumentOptionsTypeMismatch() {
        var proto2Source = ClassLoader.getSystemClassLoader().getResource("document_options_type_mismatch.proto");
        Objects.requireNonNull(proto2Source);
        var parser = new ProtobufParser();
        Assertions.assertThrows(ProtobufParserException.class, () -> parser.parseOnly(Path.of(proto2Source.toURI())));
    }

    @Test
    public void testMessageOptions() throws URISyntaxException, IOException {
        var proto2Source = ClassLoader.getSystemClassLoader().getResource("message_options.proto");
        Objects.requireNonNull(proto2Source);
        var parser = new ProtobufParser();
        var document = parser.parseOnly(Path.of(proto2Source.toURI()));
        System.out.println(document);
    }

    @Test
    public void testMessageOptionsUnknown() {
        var proto2Source = ClassLoader.getSystemClassLoader().getResource("message_options_unknown.proto");
        Objects.requireNonNull(proto2Source);
        var parser = new ProtobufParser();
        Assertions.assertThrows(ProtobufParserException.class, () -> parser.parseOnly(Path.of(proto2Source.toURI())));
    }

    @Test
    public void testMessageOptionsTypeMismatch() {
        var proto2Source = ClassLoader.getSystemClassLoader().getResource("message_options_type_mismatch.proto");
        Objects.requireNonNull(proto2Source);
        var parser = new ProtobufParser();
        Assertions.assertThrows(ProtobufParserException.class, () -> parser.parseOnly(Path.of(proto2Source.toURI())));
    }

    @Test
    public void testEnumOptions() throws URISyntaxException, IOException {
        var proto2Source = ClassLoader.getSystemClassLoader().getResource("enum_options.proto");
        Objects.requireNonNull(proto2Source);
        var parser = new ProtobufParser();
        var document = parser.parseOnly(Path.of(proto2Source.toURI()));
        System.out.println(document);
    }

    @Test
    public void testEnumOptionsUnknown() {
        var proto2Source = ClassLoader.getSystemClassLoader().getResource("enum_options_unknown.proto");
        Objects.requireNonNull(proto2Source);
        var parser = new ProtobufParser();
        Assertions.assertThrows(ProtobufParserException.class, () -> parser.parseOnly(Path.of(proto2Source.toURI())));
    }

    @Test
    public void testEnumOptionsTypeMismatch() {
        var proto2Source = ClassLoader.getSystemClassLoader().getResource("enum_options_type_mismatch.proto");
        Objects.requireNonNull(proto2Source);
        var parser = new ProtobufParser();
        Assertions.assertThrows(ProtobufParserException.class, () -> parser.parseOnly(Path.of(proto2Source.toURI())));
    }

    @Test
    public void testEnumConstantOptions() throws URISyntaxException, IOException {
        var proto2Source = ClassLoader.getSystemClassLoader().getResource("enum_constant_options.proto");
        Objects.requireNonNull(proto2Source);
        var parser = new ProtobufParser();
        var document = parser.parseOnly(Path.of(proto2Source.toURI()));
        System.out.println(document);
    }

    @Test
    public void testEnumConstantOptionsUnknown() {
        var proto2Source = ClassLoader.getSystemClassLoader().getResource("enum_constant_options_unknown.proto");
        Objects.requireNonNull(proto2Source);
        var parser = new ProtobufParser();
        Assertions.assertThrows(ProtobufParserException.class, () -> parser.parseOnly(Path.of(proto2Source.toURI())));
    }

    @Test
    public void testEnumConstantOptionsTypeMismatch() {
        var proto2Source = ClassLoader.getSystemClassLoader().getResource("enum_constant_options_type_mismatch.proto");
        Objects.requireNonNull(proto2Source);
        var parser = new ProtobufParser();
        Assertions.assertThrows(ProtobufParserException.class, () -> parser.parseOnly(Path.of(proto2Source.toURI())));
    }

    @Test
    public void testMessageFieldOptions() throws URISyntaxException, IOException {
        var proto2Source = ClassLoader.getSystemClassLoader().getResource("message_field_options.proto");
        Objects.requireNonNull(proto2Source);
        var parser = new ProtobufParser();
        var document = parser.parseOnly(Path.of(proto2Source.toURI()));
        System.out.println(document);
    }

    @Test
    public void testMessageFieldOptionsUnknown() {
        var proto2Source = ClassLoader.getSystemClassLoader().getResource("message_field_options_unknown.proto");
        Objects.requireNonNull(proto2Source);
        var parser = new ProtobufParser();
        Assertions.assertThrows(ProtobufParserException.class, () -> parser.parseOnly(Path.of(proto2Source.toURI())));
    }

    @Test
    public void testMessageFieldOptionsTypeMismatch() {
        var proto2Source = ClassLoader.getSystemClassLoader().getResource("message_field_options_type_mismatch.proto");
        Objects.requireNonNull(proto2Source);
        var parser = new ProtobufParser();
        Assertions.assertThrows(ProtobufParserException.class, () -> parser.parseOnly(Path.of(proto2Source.toURI())));
    }
}
