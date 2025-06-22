import it.auties.protobuf.parser.ProtobufParser;
import it.auties.protobuf.parser.ProtobufParserException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Objects;

public class IllegalScopeTest {
    @Test
    public void test(){
        var proto2Source = ClassLoader.getSystemClassLoader().getResource("illegal_scope.proto");
        Objects.requireNonNull(proto2Source);
        Assertions.assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(Path.of(proto2Source.toURI())));
    }
}
