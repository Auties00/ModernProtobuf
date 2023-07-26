import it.auties.protobuf.parser.ProtobufParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.Objects;

public class VeryComplexProtoTest {
    @Test
    public void test() throws URISyntaxException, IOException {
        var proto2Source = ClassLoader.getSystemClassLoader().getResource("whatsapp.proto");
        Objects.requireNonNull(proto2Source);

        var parser = new ProtobufParser(Path.of(proto2Source.toURI()));
        var document = parser.tokenizeAndParse();
        System.out.println(document);
    }
}
