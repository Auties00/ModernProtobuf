import it.auties.protobuf.parser.ProtobufParser;
import it.auties.protobuf.parser.ProtobufParserException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.Objects;

public class UnexpectedTokensTest {
    @Test
    public void testIllegalTokenAfterMessageDefinition() throws URISyntaxException, IOException {
        var proto2Source = ClassLoader.getSystemClassLoader().getResource("unexpected_tokens.proto");
        Objects.requireNonNull(proto2Source);
        Assertions.assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(Path.of(proto2Source.toURI())));
    }
}
