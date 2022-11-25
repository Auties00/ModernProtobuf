package it.auties.protobuf.tool.command;

import it.auties.protobuf.parser.ProtobufParser;
import it.auties.protobuf.parser.statement.ProtobufDocument;
import it.auties.protobuf.parser.statement.ProtobufEnumStatement;
import it.auties.protobuf.parser.statement.ProtobufMessageStatement;
import it.auties.protobuf.parser.statement.ProtobufObject;
import it.auties.protobuf.tool.schema.ProtobufSchemaCreator;
import it.auties.protobuf.tool.util.*;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtAnnotation;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtType;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

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
            AccessorsSettings.accessors(accessors);
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

    @SuppressWarnings("unchecked")
    private void update(ProtobufObject<?> statement) {
        var creator = new ProtobufSchemaCreator(document);
        var matched = AstUtils.getProtobufClass(model, statement);
        if (matched != null) {
            var matchingFile = output.toPath()
                    .resolve("%s.java".formatted(matched.getQualifiedName().replaceAll("\\.", "/")));
            if (Files.exists(matchingFile)) {
                creator.update(matched, statement, matchingFile);
                return;
            }
        }

        log.info("Schema %s doesn't have a model. Type its name or click enter to generate it:".formatted(statement.name()));
        var suggestedNames = model.getElements(new TypeFilter<>(CtClass.class))
                .stream()
                .map(CtType::getSimpleName)
                .filter(simpleName -> StringUtils.similarity(statement.name(), simpleName) > 0.5)
                .collect(Collectors.joining(", "));
        log.info("Suggested names: %s".formatted(suggestedNames));

        var scanner = new Scanner(System.in);
        var newName = scanner.nextLine();
        if (newName.isBlank()) {
            var matchingFile = output.toPath()
                    .resolve(document.packageName().replaceAll("\\.", "/"))
                    .resolve("%s.java".formatted(statement.name()));
            createNewSource(statement, model.getUnnamedModule().getFactory(), matchingFile);
            return;
        }

        var dummyStatement = statement instanceof ProtobufEnumStatement
                ? new ProtobufEnumStatement(newName, statement.packageName(), statement.parent())
                : new ProtobufMessageStatement(newName, statement.packageName(), statement.parent());
        var newJavaClass = AstUtils.getProtobufClass(model, dummyStatement);
        if (newJavaClass != null) {
            var matchingFile = output.toPath()
                    .resolve("%s.java".formatted(newJavaClass.getQualifiedName().replaceAll("\\.", "/")));
            if (Files.exists(matchingFile)) {
                var annotation = createMessageAnnotation(newJavaClass);
                annotation.setElementValues(Map.of("value", newName));
                creator.update(newJavaClass, statement, matchingFile);
                return;
            }
        }

        log.info("Model %s doesn't exist, try again".formatted(newName));
        update(statement);
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
        generator.generate(statement, factory, path);
        log.info("Generated model successfully");
    }
}
