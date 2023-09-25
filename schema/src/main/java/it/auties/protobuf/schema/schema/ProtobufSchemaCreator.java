package it.auties.protobuf.schema.schema;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.printer.DefaultPrettyPrinter;
import com.github.javaparser.printer.configuration.DefaultConfigurationOption;
import com.github.javaparser.printer.configuration.DefaultPrinterConfiguration;
import com.github.javaparser.printer.configuration.DefaultPrinterConfiguration.ConfigOption;
import com.github.javaparser.printer.configuration.imports.IntelliJImportOrderingStrategy;
import it.auties.protobuf.parser.statement.*;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public record ProtobufSchemaCreator(ProtobufDocument document, File directory) {
    public void generate(List<CompilationUnit> classPool, boolean mutable, boolean nullable) {
        var results = document.statements()
                .stream()
                .collect(Collectors.toMap(ProtobufStatement::qualifiedCanonicalPath, entry -> generate(entry, mutable, nullable, classPool)));
        results.forEach(this::writeOrThrow);
    }

    public CompilationUnit generate(ProtobufObject<?> object, boolean mutable, boolean nullable, List<CompilationUnit> classPool) {
        Objects.requireNonNull(directory, "Cannot generate files without a target directory");
        if (object instanceof ProtobufMessageStatement msg) {
            var schema = new MessageSchemaCreator(document.packageName(), msg, mutable, nullable, classPool, directory.toPath());
            return schema.generate();
        }

        if (object instanceof ProtobufEnumStatement enm) {
            var schema = new EnumSchemaCreator(document.packageName(), enm, classPool, directory.toPath());
            return schema.generate();
        }

        throw new IllegalArgumentException("Cannot find a schema generator for statement %s(%s)".formatted(object.name(), object.getClass().getName()));
    }

    private void writeOrThrow(String path, CompilationUnit unit) {
        try {
            var output = directory.toPath().resolve(path + ".java");
            var configuration = new DefaultPrinterConfiguration();
            configuration.addOption(new DefaultConfigurationOption(ConfigOption.ORDER_IMPORTS, true));
            configuration.addOption(new DefaultConfigurationOption(ConfigOption.SORT_IMPORTS_STRATEGY, new IntelliJImportOrderingStrategy()));
            configuration.addOption(new DefaultConfigurationOption(ConfigOption.SPACE_AROUND_OPERATORS, true));
            configuration.addOption(new DefaultConfigurationOption(ConfigOption.COLUMN_ALIGN_ARGUMENTS, true));
            configuration.addOption(new DefaultConfigurationOption(ConfigOption.COLUMN_ALIGN_PARAMETERS, true));
            configuration.addOption(new DefaultConfigurationOption(ConfigOption.MAX_ENUM_CONSTANTS_TO_ALIGN_HORIZONTALLY, 0));
            var printer = new DefaultPrettyPrinter(configuration);
            var result = printer.print(unit);
            Files.createDirectories(output.getParent());
            Files.writeString(output, result);
        } catch (IOException exception) {
            throw new UncheckedIOException("Cannot write output", exception);
        }
    }

    public void update(boolean mutable, boolean nullable, List<CompilationUnit> classPool) {
        var results = document.statements()
                .stream()
                .map(entry -> Map.entry(entry.qualifiedCanonicalPath(), update(entry, mutable, nullable, classPool)))
                .flatMap(entry -> entry.getValue().isEmpty() ? Stream.empty() : Stream.of(Map.entry(entry.getKey(), entry.getValue().get())))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        results.forEach(this::writeOrThrow);
    }

    private Optional<CompilationUnit> update(ProtobufObject<?> statement, boolean mutable, boolean nullable, List<CompilationUnit> classPool) {
        if (statement instanceof ProtobufMessageStatement msg) {
            var schema = new MessageSchemaCreator(document.packageName(), msg, mutable, nullable, classPool, directory.toPath());
            return schema.update();
        }

        if (statement instanceof ProtobufEnumStatement enm) {
            var schema = new EnumSchemaCreator(document.packageName(), enm, classPool, directory.toPath());
            return schema.update();
        }

        throw new IllegalArgumentException("Cannot find a schema updater for statement");
    }
}
