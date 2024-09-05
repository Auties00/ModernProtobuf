import it.auties.protobuf.parser.ProtobufParser;
import it.auties.protobuf.parser.ProtobufParserException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.Objects;

public class GroupTest {
    @Test
    public void test() throws URISyntaxException, IOException {
        var proto2Source = ClassLoader.getSystemClassLoader().getResource("group.proto");
        Objects.requireNonNull(proto2Source);
        var parser = new ProtobufParser();
        var document = parser.parseOnly(Path.of(proto2Source.toURI()));
        System.out.println(document);
    }

    @Test
    public void testIllegalName() {
        var proto2Source = ClassLoader.getSystemClassLoader().getResource("illegal_group_name.proto");
        Objects.requireNonNull(proto2Source);
        var parser = new ProtobufParser();
        Assertions.assertThrows(ProtobufParserException.class, () -> parser.parseOnly(Path.of(proto2Source.toURI())));
    }

    @Test
    public void testIllegalDeclarationBody() {
        var proto2Source = ClassLoader.getSystemClassLoader().getResource("illegal_group_declaration_no_body.proto");
        Objects.requireNonNull(proto2Source);
        var parser = new ProtobufParser();
        Assertions.assertThrows(ProtobufParserException.class, () -> parser.parseOnly(Path.of(proto2Source.toURI())));
    }

    @Test
    public void testIllegalDeclarationTerminator() {
        var proto2Source = ClassLoader.getSystemClassLoader().getResource("illegal_group_declaration_no_terminator.proto");
        Objects.requireNonNull(proto2Source);
        var parser = new ProtobufParser();
        Assertions.assertThrows(ProtobufParserException.class, () -> System.out.println(parser.parseOnly(Path.of(proto2Source.toURI()))));
    }
}
