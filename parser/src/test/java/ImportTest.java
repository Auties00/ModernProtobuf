import it.auties.protobuf.parser.ProtobufParser;
import it.auties.protobuf.parser.exception.ProtobufTypeException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.Objects;

public class ImportTest {
    @Test
    public void testNamedImport() throws URISyntaxException, IOException {
        var importedSource = ClassLoader.getSystemClassLoader().getResource("import");
        Objects.requireNonNull(importedSource);
        var parser = new ProtobufParser();
        var documents = parser.parse(Path.of(importedSource.toURI()));
        for(var document : documents) {
            System.out.println(document);
        }
    }

    @Test
    public void testBuiltinImport() throws URISyntaxException, IOException {
        var proto2Source = ClassLoader.getSystemClassLoader().getResource("builtin.proto");
        Objects.requireNonNull(proto2Source);
        var parser = new ProtobufParser();
        var document = parser.parseOnly(Path.of(proto2Source.toURI()));
        System.out.println(document);
    }

    @Test
    public void testInvalidImport() {
        var proto2Source = ClassLoader.getSystemClassLoader().getResource("invalid_import.proto");
        Objects.requireNonNull(proto2Source);
        var parser = new ProtobufParser();
        Assertions.assertThrows(ProtobufTypeException.class, () -> parser.parseOnly(Path.of(proto2Source.toURI())));
    }
}
