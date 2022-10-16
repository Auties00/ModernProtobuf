package it.auties.protobuf.tool.command;

import it.auties.protobuf.parser.ProtobufParser;
import it.auties.protobuf.parser.statement.ProtobufDocument;
import it.auties.protobuf.parser.statement.ProtobufObject;
import it.auties.protobuf.tool.schema.ProtobufSchemaCreator;
import it.auties.protobuf.tool.util.AstUtils;
import it.auties.protobuf.tool.util.LogProvider;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.factory.Factory;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.Callable;

@Command(
        name = "update",
        mixinStandardHelpOptions = true,
        version = "update 1.17",
        description = "Updates a protobuf model class using a schema"
)
public class UpdateCommand implements Callable<Integer>, LogProvider {
    @SuppressWarnings("FieldMayBeFinal")
    @Parameters(
            index = "0",
            description = "The protobuf file used to update the java classes"
    )
    private File protobuf = null;

    @SuppressWarnings("FieldMayBeFinal")
    @Parameters(
            index = "1",
            description = "The input classes to update"
    )
    private File input = null;

    @SuppressWarnings("FieldMayBeFinal")
    @Option(
            names = {"-o", "--output"},
            description = "The package where new classes should be put"
    )
    private File output = null;

    private ProtobufSchemaCreator generator;
    private ProtobufDocument document;
    private CtModel model;

    @Override
    public Integer call() {
        try {
            createDocument();
            createSchemaCreator();
            createSpoonModel();
            doUpdate();
            return 0;
        } catch (IOException ex) {
            log.info("Cannot parse Protobuf message");
            log.throwing(ex);
            return -1;
        }
    }

    private void doUpdate() {
        log.info("Starting update...");
        document.statements()
                .forEach(this::update);
        log.info("Finished update successfully");
    }

    private void update(ProtobufObject<?> statement) {
        var javaClass = AstUtils.getProtobufClass(model, statement);
        var file = getFileOrGenerateSource(statement, javaClass);
        if(file.isEmpty()){
            return;
        }

        var creator = new ProtobufSchemaCreator(document);
        creator.update(javaClass, statement, file.get());
    }

    private void createSpoonModel() {
        log.info("Creating AST model from existing Java classes...");
        this.model = createModel();
        log.info("Created AST model from existing Java classes");
    }

    private void createSchemaCreator() {
        log.info("Creating updater class...");
        this.generator = new ProtobufSchemaCreator(document, output);
        log.info("Created updater class");
    }

    private void createDocument() throws IOException {
        log.info("Generating AST for protobuf file...");
        var parser = new ProtobufParser(protobuf);
        this.document = parser.tokenizeAndParse();
        log.info("Generated AST successfully");
    }

    private CtModel createModel() {
        try (var stream = Files.walk(input.toPath())) {
            var launcher = AstUtils.createLauncher();
            stream.filter(Files::isRegularFile)
                    .filter(entry -> entry.toString().endsWith(".java"))
                    .forEach(entry -> launcher.addInputResource(entry.toString()));
            return launcher.buildModel();
        }catch (IOException exception){
            throw new UncheckedIOException("Cannot create model", exception);
        }
    }

    private Optional<Path> getFileOrGenerateSource(ProtobufObject<?> statement, CtClass<?> matched) {
        if(matched == null){
            var matchingFile = output.toPath()
                    .resolve(document.packageName().replaceAll("\\.", "/"))
                    .resolve("%s.java".formatted(statement.name()));
            createNewSource(statement, model.getUnnamedModule().getFactory(), matchingFile);
            return Optional.empty();
        }

        var matchingFile = output.toPath()
                .resolve("%s.java".formatted(matched.getQualifiedName().replaceAll("\\.", "/")));
        if (Files.exists(matchingFile)){
            return Optional.of(matchingFile);
        }

        createNewSource(statement, model.getUnnamedModule().getFactory(), matchingFile);
        return Optional.empty();
    }

    private void createNewSource(ProtobufObject<?> statement, Factory factory, Path path) {
        log.info("Generating model for %s...".formatted(statement.name()));
        generator.generate(statement, factory, path);
        log.info("Generated model successfully");
    }
}
