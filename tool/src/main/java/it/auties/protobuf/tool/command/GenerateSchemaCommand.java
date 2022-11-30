package it.auties.protobuf.tool.command;

import it.auties.protobuf.parser.ProtobufParser;
import it.auties.protobuf.parser.statement.ProtobufDocument;
import it.auties.protobuf.tool.schema.ProtobufSchemaCreator;
import it.auties.protobuf.tool.util.AstUtils;
import it.auties.protobuf.tool.util.LogProvider;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import spoon.reflect.factory.Factory;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.Callable;

@Command(
        name = "generate",
        mixinStandardHelpOptions = true,
        version = "generate 1.17-SNAPSHOT",
        description = "Generates the java classes for a protobuf file"
)
public class GenerateSchemaCommand implements Callable<Integer>, LogProvider {
    @SuppressWarnings("FieldMayBeFinal")
    @Parameters(
            index = "0",
            description = "The protobuf file used to generate the java classes"
    )
    private File protobuf = null;

    @SuppressWarnings("FieldMayBeFinal")
    @Option(
            names = {"-o", "--output"},
            description = "The directory where the generated classes should be outputted, by default a directory named schemas will be created in the home directory"
    )
    private File output = new File(System.getProperty("user.home"), "/schemas");

    @Override
    public Integer call() {
        if (!createOutputDirectory()) {
            return -1;
        }

        try {
            var launcher = AstUtils.createLauncher(null);
            launcher.buildModel();
            var ast = generateAST();
            generateSchema(ast, launcher.getFactory());
            return 0;
        } catch (Throwable ex) {
            log.error("An uncaught exception was thrown, report this incident on github if you believe this to be a bug");
            log.throwing(ex);
            return -1;
        }
    }

    private boolean createOutputDirectory() {
        if (output.exists()) {
            return true;
        }

        log.warn("The specified output folder doesn't exist, creating it...");
        if (output.mkdirs()) {
            return true;
        }

        log.error("Cannot create output directory");
        return false;
    }

    private ProtobufDocument generateAST() throws IOException {
        log.info("Generating AST for protobuf file...");
        var parser = new ProtobufParser(protobuf);
        var document = parser.tokenizeAndParse();
        log.info("Generated AST successfully");
        return document;
    }

    private void generateSchema(ProtobufDocument ast, Factory factory) {
        log.info("Generating java classes from AST...");
        var generator = new ProtobufSchemaCreator(ast, output);
        generator.generate(factory);
        log.info("Generated java classes successfully at %s".formatted(output));
    }
}
