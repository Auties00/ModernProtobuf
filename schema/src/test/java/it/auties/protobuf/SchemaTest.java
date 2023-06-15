package it.auties.protobuf;

import it.auties.protobuf.tool.command.BaseCommand;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import java.nio.file.Path;
import java.util.Objects;

public class SchemaTest {
    @Test
    public void testSchemaGeneration() throws InterruptedException {
        Assertions.assertDoesNotThrow(() -> {
            var source = ClassLoader.getSystemClassLoader().getResource("whatsapp.proto");
            Objects.requireNonNull(source);
            var folder = "C:\\Users\\alaut\\ProtocCompiler\\schema\\src\\test\\java\\it\\auties\\protobuf\\out";
            new CommandLine(new BaseCommand()).execute("generate", Path.of(source.toURI()).toString(), "--mutable", "--output", folder);
        });

        Thread.sleep(2000L);
        
        Assertions.assertDoesNotThrow(() -> {
            var source = ClassLoader.getSystemClassLoader().getResource("whatsapp.proto");
            Objects.requireNonNull(source);
            var folder = "C:\\Users\\alaut\\ProtocCompiler\\schema\\src\\test\\java\\it\\auties\\protobuf\\out";
            new CommandLine(new BaseCommand()).execute("update", Path.of(source.toURI()).toString(), folder, "--mutable");
        });
    }
}
