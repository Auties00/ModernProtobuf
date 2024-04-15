import it.auties.protobuf.parser.ProtobufParser;
import it.auties.protobuf.parser.exception.ProtobufSyntaxException;
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
        var parser = new ProtobufParser();
        parser.parseOnly(Path.of(proto2Source.toURI()));
    }
}
