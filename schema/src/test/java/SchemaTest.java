import it.auties.protobuf.schema.command.BaseCommand;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

public class SchemaTest {
    @Test
    public void testSchemaGeneration() throws Exception {
        var source = ClassLoader.getSystemClassLoader().getResource("whatsapp.proto");
        Objects.requireNonNull(source);
        var proto = Path.of(source.toURI()).toAbsolutePath().toString();
        var out = Files.createTempDirectory("protobuf");
        Assertions.assertDoesNotThrow(() -> {
            new CommandLine(new BaseCommand()).execute("generate", proto, "--mutable", "--nullable", "--output", out.toString());
        });
    }
}
