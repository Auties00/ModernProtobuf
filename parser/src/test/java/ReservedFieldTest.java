import it.auties.protobuf.parser.ProtobufParser;
import it.auties.protobuf.parser.exception.ProtobufSyntaxException;
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

        var parser = new ProtobufParser(Path.of(source.toURI()));
        var document = parser.parse();
        System.out.println(document);
    }

    @Test
    public void testInvalid() {
        var source = ClassLoader.getSystemClassLoader().getResource("reserved_duplicate.proto");
        Objects.requireNonNull(source);

        Assertions.assertThrows(ProtobufSyntaxException.class, () -> {
            var parser = new ProtobufParser(Path.of(source.toURI()));
            var document = parser.parse();
            System.out.println(document);
        });
    }

    @Test
    public void testIllegal() {
        var source = ClassLoader.getSystemClassLoader().getResource("reserved_illegal.proto");
        Objects.requireNonNull(source);

        Assertions.assertThrows(ProtobufSyntaxException.class, () -> {
            var parser = new ProtobufParser(Path.of(source.toURI()));
            var document = parser.parse();
            System.out.println(document);
        });
    }
}
