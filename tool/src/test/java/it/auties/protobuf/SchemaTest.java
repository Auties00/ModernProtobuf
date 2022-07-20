package it.auties.protobuf;

import it.auties.protobuf.tool.application.ModernProtocApplication;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

public class SchemaTest {
    @Test
    public void testSchemaGeneration() throws IOException, URISyntaxException {
        var source = ClassLoader.getSystemClassLoader().getResource("whatsapp.proto");
        Objects.requireNonNull(source);

        var folder = Files.createTempDirectory("output");
        ModernProtocApplication.main("generate", Path.of(source.toURI()).toString(),
                "--package", "it.auties.example", "--output", folder.toString());
    }
}
