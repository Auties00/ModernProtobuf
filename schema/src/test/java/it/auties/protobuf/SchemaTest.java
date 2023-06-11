package it.auties.protobuf;

import it.auties.protobuf.tool.command.BaseCommand;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

public class SchemaTest {
    @Test
    public void testSchemaGeneration() {
        Assertions.assertDoesNotThrow(() -> {
            var source = ClassLoader.getSystemClassLoader().getResource("whatsapp.proto");
            Objects.requireNonNull(source);
            var folder = Files.createTempDirectory("output");
            new CommandLine(new BaseCommand()).execute("generate", Path.of(source.toURI()).toString(), "--output", folder.toString());
        });
    }
}
