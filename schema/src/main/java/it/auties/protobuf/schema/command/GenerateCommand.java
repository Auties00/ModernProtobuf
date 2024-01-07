package it.auties.protobuf.schema.command;

import com.github.javaparser.ast.CompilationUnit;
import it.auties.protobuf.parser.ProtobufParser;
import it.auties.protobuf.parser.tree.ProtobufDocument;
import it.auties.protobuf.schema.schema.ProtobufSchemaCreator;
import it.auties.protobuf.schema.util.AstUtils;
import it.auties.protobuf.schema.util.LogProvider;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.File;
import java.io.IOException;
import java.lang.System.Logger.Level;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;

@Command(
        name = "generate",
        mixinStandardHelpOptions = true,
        version = "generate 3.0.2",
        description = "Generates the java classes for a protobuf file"
)
public class GenerateCommand implements Callable<Integer>, LogProvider {
    @Parameters(
            index = "0",
            description = "The protobuf file used to generate the java classes"
    )
    private File protobuf = null;

    @Option(
            names = {"-o", "--output"},
            description = "The directory where the generated classes should be outputted, by default a directory named proto-out will be created in your working directory"
    )
    private File output = Path.of("proto-out/")
            .toAbsolutePath()
            .toFile();

    @Option(
            names = {"-m", "--mutable"},
            description = "Whether the generated classes should have mutable fields"
    )
    private boolean mutable = false;

    @Option(
            names = {"-n", "--nullable"},
            description = "Whether the generated classes should have nullable fields (by default Optionals are used)"
    )
    private boolean nullable = false;

    @Override
    public Integer call() {
        if (!createOutputDirectory()) {
            return -1;
        }

        try {
            var classPool = AstUtils.createClassPool(null);
            var ast = generateAST();
            generateSchema(classPool, ast);
            return 0;
        } catch (Throwable ex) {
            log.log(Level.ERROR, "An uncaught exception was thrown, report this incident on github if you believe this to be a bug", ex);
            return -1;
        }
    }

    private boolean createOutputDirectory() {
        if (output.exists()) {
            return true;
        }

        log.log(Level.WARNING, "The specified output folder doesn't exist, creating it...");
        if (output.mkdirs()) {
            return true;
        }

        log.log(Level.ERROR, "Cannot create output directory");
        return false;
    }

    private ProtobufDocument generateAST() throws IOException {
        log.log(Level.INFO, "Generating AST for protobuf file...");
        var parser = new ProtobufParser();
        var document = parser.parseOnly(protobuf.toPath());
        log.log(Level.INFO, "Generated AST successfully");
        return document;
    }

    private void generateSchema(List<CompilationUnit> classPool, ProtobufDocument ast) {
        log.log(Level.INFO, "Generating java classes from AST...");
        var generator = new ProtobufSchemaCreator(ast, output);
        generator.generate(classPool, mutable, nullable);
        log.log(Level.INFO, "Generated java classes successfully at %s".formatted(output));
    }
}
