import it.auties.protobuf.parser.ProtobufParser;
import it.auties.protobuf.parser.exception.ProtobufTypeException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.Objects;

public class IllegalScopeTest {
    @Test
    public void test() throws URISyntaxException, IOException {
        var proto2Source = ClassLoader.getSystemClassLoader().getResource("illegal_scope.proto");
        Objects.requireNonNull(proto2Source);

        var parser = new ProtobufParser(Path.of(proto2Source.toURI()));
        Assertions.assertThrows(ProtobufTypeException.class, parser::parse);
    }
}
