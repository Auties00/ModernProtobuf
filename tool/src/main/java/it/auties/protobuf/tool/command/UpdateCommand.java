package it.auties.protobuf.tool.command;

import it.auties.protobuf.parser.ProtobufParser;
import it.auties.protobuf.parser.statement.ProtobufDocument;
import it.auties.protobuf.parser.statement.ProtobufObject;
import it.auties.protobuf.tool.schema.ProtobufSchemaCreator;
import it.auties.protobuf.tool.util.AstElements;
import it.auties.protobuf.tool.util.AstUtils;
import it.auties.protobuf.tool.util.LogProvider;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtAnnotation;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtTypeReference;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.Callable;

import static it.auties.protobuf.parser.statement.ProtobufStatementType.ENUM;

@Command(
        name = "update",
        mixinStandardHelpOptions = true,
        version = "update 1.17-SNAPSHOT",
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

    @SuppressWarnings("FieldMayBeFinal")
    @Option(
            names = {"-a", "--accessors"},
            description = "Whether accessors should be generated"
    )
    private boolean accessors;

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
                .forEach(entry -> update(entry, entry.name(), entry.type() == ENUM, false));
        log.info("Finished update successfully");
    }

    @SuppressWarnings("unchecked")
    private void update(ProtobufObject<?> statement, String name, boolean enumType, boolean annotate) {
        log.info("Updating %s".formatted(name));
        var creator = new ProtobufSchemaCreator(document);
        var matched = AstUtils.getProtobufClass(model, name, enumType);
        if (matched != null) {
            var ctClass = getTopClass(matched);
            var ctClassPath = ctClass.getQualifiedName()
                    .replaceAll("\\.", "/");
            var matchingFile = output.toPath()
                    .resolve("%s.java".formatted(ctClassPath));
            if (Files.notExists(matchingFile)) {
                log.warn("Skipping %s because file %s doesn't exist"
                        .formatted(name, matchingFile));
                return;
            }

            if(annotate) {
                var annotation = createMessageAnnotation(matched);
                annotation.setElementValues(Map.of("value", statement.name()));
            }

            creator.update(matched, statement, matchingFile, accessors, annotate);
            return;
        }

        log.info("Schema %s doesn't have a model. Type its name if it already exists, ".formatted(name));
        log.info("Suggested names: %s".formatted(AstUtils.getSuggestedNames(model, name, enumType)));
        log.info("If you want to generate a new model click enter");
        var scanner = new Scanner(System.in);
        var newName = scanner.nextLine();
        if (!newName.isBlank()) {
            update(statement, newName, enumType, true);
            return;
        }

        var matchingFile = output.toPath()
                .resolve(document.packageName() == null ? "." : document.packageName().replaceAll("\\.", "/"))
                .resolve("%s.java".formatted(name));
        createNewSource(statement, model.getUnnamedModule().getFactory(), matchingFile);
    }

    private CtClass<?> getTopClass(CtClass<?> ctClass){
        var parent = ctClass.getParent();
        return parent instanceof CtClass<?> newClass ? getTopClass(newClass) : ctClass;
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

    @SuppressWarnings({"unchecked", "rawtypes"})
    private CtAnnotation createMessageAnnotation(CtClass<?> newJavaClass) {
        CtTypeReference reference = newJavaClass.getFactory()
                .createReference(AstElements.PROTOBUF_MESSAGE_NAME);
        var annotation = newJavaClass.getAnnotation(reference);
        if (annotation != null) {
            return annotation;
        }

        var newAnnotation = newJavaClass.getFactory().createAnnotation(reference);
        newJavaClass.addAnnotation(newAnnotation);
        return newAnnotation;
    }

    private void createNewSource(ProtobufObject<?> statement, Factory factory, Path path) {
        log.info("Generating model for %s...".formatted(statement.name()));
        generator.generate(statement, accessors, factory, path);
        log.info("Generated model successfully");
    }
}
