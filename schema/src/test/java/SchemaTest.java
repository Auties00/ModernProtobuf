import it.auties.protobuf.schema.command.BaseCommand;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import java.nio.file.Path;
import java.util.Objects;

public class SchemaTest {
    @Test
    public void testSchemaGeneration() throws Exception {
        var source = ClassLoader.getSystemClassLoader().getResource("whatsapp.proto");
        Objects.requireNonNull(source);
        var proto = Path.of(source.toURI()).toString();
        var out = Path.of("/home/alessandro/CobaltStreamline/src/main/java/it/auties/whatsapp/model").toAbsolutePath().toString();

        Assertions.assertDoesNotThrow(() -> {
            new CommandLine(new BaseCommand()).execute("generate", proto, "--nullable", "--output", out);
        });
    }
}
