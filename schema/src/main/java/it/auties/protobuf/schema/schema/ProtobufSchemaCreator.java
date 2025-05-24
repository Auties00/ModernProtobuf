package it.auties.protobuf.schema.schema;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.nodeTypes.NodeWithName;
import com.github.javaparser.printer.DefaultPrettyPrinter;
import com.github.javaparser.printer.configuration.DefaultConfigurationOption;
import com.github.javaparser.printer.configuration.DefaultPrinterConfiguration;
import com.github.javaparser.printer.configuration.DefaultPrinterConfiguration.ConfigOption;
import com.github.javaparser.printer.configuration.imports.IntelliJImportOrderingStrategy;
import it.auties.protobuf.parser.tree.ProtobufTree;
import it.auties.protobuf.parser.tree.ProtobufDocumentTree;
import it.auties.protobuf.parser.tree.ProtobufEnumTree;
import it.auties.protobuf.parser.tree.ProtobufMessageTree;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public record ProtobufSchemaCreator(ProtobufDocumentTree document, File directory) {
    public void generate(List<CompilationUnit> classPool, boolean mutable, boolean nullable) {
        var results = document.children()
                .stream()
                .map(entry -> generate(entry, mutable, nullable, classPool))
                .toList();
        results.forEach(this::writeOrThrow);
    }

    public CompilationUnit generate(ProtobufTree object, boolean mutable, boolean nullable, List<CompilationUnit> classPool) {
        Objects.requireNonNull(directory, "Cannot generate files without a target directory");
        if (object instanceof ProtobufMessageTree msg) {
            var schema = new MessageSchemaCreator(document.packageName().orElse(null), msg, mutable, nullable, classPool, directory.toPath());
            return schema.generate();
        }

        if (object instanceof ProtobufEnumTree enm) {
            var schema = new EnumSchemaCreator(document.packageName().orElse(null), enm, classPool, directory.toPath());
            return schema.generate();
        }

        throw new IllegalArgumentException("Cannot find a schema generator for statement with type " + object.getClass().getName());
    }

    private void writeOrThrow(CompilationUnit unit) {
        try {
            var packageName = unit.getPackageDeclaration()
                    .map(NodeWithName::getNameAsString)
                    .orElseGet(() -> document.packageName().orElse(""))
                    .replaceAll("\\.", File.separator);
            var className = unit.getType(0).getNameAsString();
            var qualifiedCanonicalName = packageName.isEmpty() ? className : packageName + "/" + className;
            var outputParts = Stream.of(directory.getPath().split(Matcher.quoteReplacement(File.separator)), qualifiedCanonicalName.split(Matcher.quoteReplacement(File.separator)))
                    .flatMap(Arrays::stream)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            var output = Path.of(String.join(File.separator, outputParts) + ".java");
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
        var results = document.children()
                .stream()
                .map(entry -> update(entry, mutable, nullable, classPool))
                .flatMap(Optional::stream)
                .toList();
        results.forEach(this::writeOrThrow);
    }

    private Optional<CompilationUnit> update(ProtobufTree statement, boolean mutable, boolean nullable, List<CompilationUnit> classPool) {
        if (statement instanceof ProtobufMessageTree msg) {
            var schema = new MessageSchemaCreator(document.packageName().orElse(null), msg, mutable, nullable, classPool, directory.toPath());
            return schema.update();
        }

        if (statement instanceof ProtobufEnumTree enm) {
            var schema = new EnumSchemaCreator(document.packageName().orElse(null), enm, classPool, directory.toPath());
            return schema.update();
        }

        throw new IllegalArgumentException("Cannot find a schema updater for statement");
    }
}
