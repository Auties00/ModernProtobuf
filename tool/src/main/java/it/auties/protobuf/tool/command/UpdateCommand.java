package it.auties.protobuf.tool.command;

import it.auties.protobuf.parser.ProtobufParser;
import it.auties.protobuf.parser.object.ProtobufDocument;
import it.auties.protobuf.parser.object.ProtobufObject;
import it.auties.protobuf.parser.statement.EnumConstantStatement;
import it.auties.protobuf.parser.statement.FieldStatement;
import it.auties.protobuf.parser.statement.ProtobufStatement;
import it.auties.protobuf.tool.util.LoggerUtils;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.Callable;

@Log4j2
@Command(
        name = "update",
        mixinStandardHelpOptions = true,
        version = "update 1.0",
        description = "Searches for updates to apply to model classes from a protobuf schema"
)
public class UpdateCommand implements Callable<Integer> {
    @SuppressWarnings("FieldMayBeFinal")
    @Parameters(
            index = "0",
            description = "The protobuf file used to update the model classes"
    )
    private File protobuf = null;

    @SuppressWarnings("FieldMayBeFinal")
    @Parameters(
            index = "1",
            description = "The directory where the model classes are contained"
    )
    private File input = null;

    @SuppressWarnings("FieldMayBeFinal")
    @Option(
            names = {"-p", "--print"},
            description = "Print the names of missing files"
    )
    private boolean printNewFiles = false;

    @Override
    public Integer call() {
        if (!input.exists()) {
            log.error("The input directory %s doesn't exist".formatted(input));
            return -1;
        }

        try {
            LoggerUtils.suppressIllegalAccessWarning();
            var ast = generateAST();
            checkObject(ast);
            return 0;
        } catch (Throwable ex) {
            log.error("An uncaught exception was thrown, report this incident on github if you believe this to be a bug");
            log.throwing(ex);
            return -1;
        }
    }

    @SneakyThrows
    private void checkObject(ProtobufObject<?> object) {
        if (object instanceof ProtobufDocument protobufDocument) {
            protobufDocument.getStatements().forEach(this::checkObject);
            return;
        }

        var file = findMatchingFile(object.getName());
        if (file.isEmpty()) {
            if (printNewFiles) {
                log.info("Detected new object: %s".formatted(object.getName()));
            }

            return;
        }

        var source = Files.readString(file.get());
        object.getStatements().forEach(fieldStatement -> checkStatement(object, fieldStatement, source));
    }

    private void checkStatement(ProtobufObject<?> object, ProtobufStatement statement, String source) {
        if (statement instanceof ProtobufObject<?> protobufObject) {
            checkObject(protobufObject);
            return;
        }

        if (statement instanceof EnumConstantStatement enumConstant) {
            if (source.contains("%s(%s)".formatted(enumConstant.getName(), enumConstant.getIndex()))) {
                return;
            }

            log.info("Detected new constant at %s: %s(%s)".formatted(object.getName(), enumConstant.getName(), enumConstant.getIndex()));
            return;
        }

        if (statement instanceof FieldStatement fieldStatement) {
            if (source.contains("%s %s".formatted(fieldStatement.getJavaType(), fieldStatement.getName()))) {
                return;
            }

            log.info("Detected new field at %s: %s(%s)".formatted(object.getName(), fieldStatement.getName(), fieldStatement.getIndex()));
            return;
        }

        throw new IllegalArgumentException("Cannot parse instruction: %s".formatted(statement.getClass().getName()));
    }

    private Optional<Path> findMatchingFile(String name) {
        try (var walker = Files.walk(input.toPath())) {
            return walker.filter(entry -> !Files.isDirectory(entry) && entry.getFileName().toString().equalsIgnoreCase(name + ".java"))
                    .findFirst();
        } catch (IOException exception) {
            throw new UncheckedIOException("Cannot search for matching model class", exception);
        }
    }

    private ProtobufDocument generateAST() throws IOException {
        log.info("Generating AST for protobuf file...");
        var parser = new ProtobufParser(protobuf);
        var document = parser.tokenizeAndParse();
        log.info("Generated AST successfully");
        return document;
    }
}
