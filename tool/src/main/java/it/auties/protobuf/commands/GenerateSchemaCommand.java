package it.auties.protobuf.commands;

import com.google.googlejavaformat.java.Formatter;
import com.google.googlejavaformat.java.FormatterException;
import it.auties.protobuf.ProtobufDocument;
import it.auties.protobuf.ProtobufParser;
import it.auties.protobuf.schema.ProtobufSchemaCreator;
import lombok.extern.log4j.Log4j2;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Option;
import picocli.CommandLine.Command;
import sun.misc.Unsafe;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.Callable;

@Log4j2
@Command(
        name = "generate",
        mixinStandardHelpOptions = true,
        version = "generate 1.0",
        description = "Generates the java classes for a protobuf file"
)
public class GenerateSchemaCommand implements Callable<Integer> {
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

    @SuppressWarnings("FieldMayBeFinal")
    @Option(
            names = {"-p", "--package"},
            description = "The package of the generated classes, by default none is specified"
    )
    private String pack = "";

    @Override
    public Integer call() {
        if(!createOutputDirectory()){
            return -1;
        }

        try{
            suppressIllegalAccessWarning();
            var ast = generateAST();
            generateSchema(ast);
            return 0;
        }catch (IOException | FormatterException | ClassNotFoundException ex) {
            log.error("An uncaught exception was thrown, report this incident on github if you believe this to be a bug");
            log.throwing(ex);
            return -1;
        }
    }

    private void suppressIllegalAccessWarning() {
        try {
            var unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
            unsafeField.setAccessible(true);
            var unsafe = (Unsafe) unsafeField.get(null);

            var loggerClass = Class.forName("jdk.internal.module.IllegalAccessLogger");
            var logger = loggerClass.getDeclaredField("logger");
            unsafe.putObjectVolatile(loggerClass, unsafe.staticFieldOffset(logger), null);
        }catch (Throwable ignored){

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

    private void generateSchema(ProtobufDocument ast) throws IOException, FormatterException, ClassNotFoundException {
        log.info("Generating java classes from AST...");
        var generator = new ProtobufSchemaCreator(ast, pack, output, new Formatter());
        generator.generateSchema();
        log.info("Generated java classes successfully at %s".formatted(output));
    }
}
