package it.auties.protobuf.parser;

import it.auties.protobuf.parser.tree.*;
import it.auties.protobuf.parser.type.*;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class ProtobufAttribute {
    private static final String TYPE_SELECTOR = ".";
    private static final String TYPE_SELECTOR_SPLITTER = "\\.";

    private static final Collection<ProtobufDocumentTree> BUILT_IN_TYPES;
    private static final Map<String, Map<String, ProtobufFieldStatement>> BUILT_IN_OPTIONS;

    static {
        try {
            var builtInTypesDirectory = ClassLoader.getSystemClassLoader().getResource("google/protobuf/");
            if(builtInTypesDirectory == null) {
                throw new ProtobufParserException("Parser initialization failed: missing built-in .proto documents");
            }

            var builtInTypesPath = Path.of(builtInTypesDirectory.toURI());
            var resolved = ProtobufParser.parse(builtInTypesPath);
            BUILT_IN_TYPES = resolved.values();

            var descriptorDocument = resolved.get("descriptor.proto");
            if(descriptorDocument == null) {
                throw new ProtobufParserException("Parser initialization failed: missing descriptor.proto");
            }

            BUILT_IN_OPTIONS = getOptionsForDescriptor(descriptorDocument);
        }catch (IOException | URISyntaxException exception) {
            throw new ProtobufParserException("Missing built-in .proto");
        }
    }

    private static Map<String, Map<String, ProtobufFieldStatement>> getOptionsForDescriptor(ProtobufDocumentTree descriptorDocument) {
        return descriptorDocument.children()
                .stream()
                .filter(child -> child instanceof ProtobufMessageStatement messageStatement && messageStatement.name().endsWith("Options"))
                .map(child -> (ProtobufMessageStatement) child)
                .collect(Collectors.toUnmodifiableMap(ProtobufMessageStatement::name, ProtobufAttribute::getOptionsForDescriptor));
    }

    private static Map<String, ProtobufFieldStatement> getOptionsForDescriptor(ProtobufMessageStatement statement) {
        return statement.children()
                .stream()
                .filter(child -> child instanceof ProtobufFieldStatement fieldStatement
                        && !Objects.equals(fieldStatement.name(), "uninterpreted_options"))
                .map(child -> (ProtobufFieldStatement) child)
                .collect(Collectors.toUnmodifiableMap(ProtobufFieldStatement::name, Function.identity()));
    }

    private ProtobufAttribute() {
        throw new UnsupportedOperationException();
    }

    public static void attribute(ProtobufDocumentTree document) {
        attributeImports(document);
        attributeDocument(document);
    }

    public static void attribute(Collection<ProtobufDocumentTree> documents) {
        attributeImports(documents);
        for(var document : documents) {
            attributeDocument(document);
        }
    }

    private static void attributeImports(ProtobufDocumentTree document) {
        var canonicalPathToDocumentMap = buildImportsMap(document);
        if(BUILT_IN_TYPES != null) {
            for(var builtIn : BUILT_IN_TYPES) {
                canonicalPathToDocumentMap.put(document.qualifiedPath(), builtIn);
            }
        }
        attributeImports(document, canonicalPathToDocumentMap);
    }

    private static void attributeImports(Collection<ProtobufDocumentTree> documents) {
        var canonicalPathToDocumentMap = buildImportsMap(documents);
        if(BUILT_IN_TYPES != null) {
            for(var document : BUILT_IN_TYPES) {
                canonicalPathToDocumentMap.put(document.qualifiedPath(), document);
            }
        }
        for(var document : documents) {
            attributeImports(document, canonicalPathToDocumentMap);
        }
    }

    private static Map<String, ProtobufDocumentTree> buildImportsMap(ProtobufDocumentTree document) {
        var mapSize = 1;
        if(BUILT_IN_TYPES != null) {
            mapSize += BUILT_IN_TYPES.size();
        }
        Map<String, ProtobufDocumentTree> canonicalPathToDocumentMap = HashMap.newHashMap(mapSize);
        canonicalPathToDocumentMap.put(document.qualifiedPath(), document);
        return canonicalPathToDocumentMap;
    }

    private static Map<String, ProtobufDocumentTree> buildImportsMap(Collection<ProtobufDocumentTree> documents) {
        var mapSize = documents.size();
        if(BUILT_IN_TYPES != null) {
            mapSize += BUILT_IN_TYPES.size();
        }
        Map<String, ProtobufDocumentTree> canonicalPathToDocumentMap = HashMap.newHashMap(mapSize);
        for(var document : documents) {
            canonicalPathToDocumentMap.put(document.qualifiedPath(), document);
        }
        return canonicalPathToDocumentMap;
    }

    private static void attributeImports(ProtobufDocumentTree document, Map<String, ProtobufDocumentTree> canonicalPathToDocumentMap) {
        for(var child : document.children()) {
            if(child instanceof ProtobufImportStatement importStatement && !importStatement.isAttributed()) {
                var imported = canonicalPathToDocumentMap.get(importStatement.location());
                ProtobufParserException.check(imported != null,
                        "Cannot resolve import %s", importStatement.line(), importStatement.location());
                importStatement.setDocument(imported);
            }
        }
    }

    private static void attributeDocument(ProtobufDocumentTree document) {
        var queue = new LinkedList<ProtobufTree>();
        queue.add(document);
        while (!queue.isEmpty()) {
            var tree = queue.removeFirst();
            switch (tree) {
                case ProtobufTree.WithBody<?> body -> queue.addAll(body.children());
                case ProtobufStatement protobufStatement -> attributeStatement(document, protobufStatement);
                case ProtobufExpression ignored -> throw new InternalError("Expressions should be attributed in-place");
            }
        }
    }

    private static void attributeStatement(ProtobufDocumentTree document, ProtobufStatement protobufStatement) {
        switch (protobufStatement) {
            case ProtobufEmptyStatement emptyStatement -> {
                if(!emptyStatement.isAttributed()) {
                    throw new InternalError("Empty statement should already be attributed");
                }
            }

            case ProtobufExtensionsStatement protobufExtension -> {

            }

            case ProtobufFieldStatement protobufField -> {
                attributeType(document, protobufField);
                for(var option : protobufField.options() ) {
                    attributeFieldOption(protobufField, option);
                }
            }

            case ProtobufImportStatement protobufImport -> {
                if(!protobufImport.isAttributed()) {
                    throw new InternalError("Import statement should already be attributed");
                }
            }

            case ProtobufOptionStatement protobufOption -> {

            }

            case ProtobufPackageStatement ignored -> {
                // Nothing to check
            }

            case ProtobufReservedStatement protobufReserved -> {

            }

            case ProtobufSyntaxStatement protobufSyntax -> {
                if(!protobufSyntax.hasVersion()) {
                    throw new InternalError("Syntax statement should already be attributed");
                }
            }

            default -> throw new IllegalStateException("Unexpected value: " + protobufStatement);
        }
    }

    private static void attributeFieldOption(ProtobufFieldStatement protobufField, ProtobufOptionExpression option) {
        if(BUILT_IN_OPTIONS == null) {
            return; // Options validation cannot happen during the bootstrap phase
        }

        var fieldOptions = BUILT_IN_OPTIONS.get("FieldOptions");
        if(fieldOptions == null) {
            throw new ProtobufParserException("Cannot validate statement options: missing FieldOptions message");
        }

        var optionName = option.name().toString();
        var definition = fieldOptions.get(optionName);
        if(definition == null) {
            throw new ProtobufParserException("Invalid option \"" + optionName
                    + "\" for field \"" + protobufField.name()
                    + "\" inside " + ((ProtobufTree.WithName) protobufField.parent()).name());
        }

        switch (definition.type().protobufType()) {
            case UNKNOWN, MAP, GROUP -> throwOnOption(protobufField, optionName, definition, "unknown option type");
            case MESSAGE -> {

            }
            case ENUM -> {

            }
            case FLOAT -> {
                if(!(option.value() instanceof ProtobufNumberExpression numberExpression)
                        || numberExpression.value().floatValue() != numberExpression.value().doubleValue()) {
                    throwOnOption(protobufField, optionName, definition, "expected float");
                }
            }
            case DOUBLE -> {
                if(!(option.value() instanceof ProtobufNumberExpression)) {
                    throwOnOption(protobufField, optionName, definition, "expected double");
                }
            }
            case BOOL -> {
                if(!(option.value() instanceof ProtobufBoolExpression)) {
                    throwOnOption(protobufField, optionName, definition, "expected bool");
                }
            }
            case STRING -> {
                if(!(option.value() instanceof ProtobufLiteralExpression)) {
                    throwOnOption(protobufField, optionName, definition, "expected string literal");
                }
            }
            case BYTES -> {
                if(!(option.value() instanceof ProtobufLiteralExpression)) {
                    throwOnOption(protobufField, optionName, definition, "expected bytes");
                }
            }
            case INT32, SINT32, FIXED32, SFIXED32 -> {
                if(!(option.value() instanceof ProtobufIntegerExpression numberExpression)
                        || numberExpression.value().intValue() != numberExpression.value()) {
                    throwOnOption(protobufField, optionName, definition, "expected int");
                }
            }
            case INT64, SINT64, FIXED64, SFIXED64 -> {
                if(!(option.value() instanceof ProtobufIntegerExpression)) {
                    throwOnOption(protobufField, optionName, definition, "expected long");
                }
            }
            case UINT32 -> {
                if(!(option.value() instanceof ProtobufIntegerExpression numberExpression)
                        || numberExpression.value().intValue() != numberExpression.value()
                        || numberExpression.value().intValue() < 0) {
                    throwOnOption(protobufField, optionName, definition, "expected unsigned int");
                }
            }
            case UINT64 -> {
                if(!(option.value() instanceof ProtobufIntegerExpression numberExpression)
                        || numberExpression.value() < 0) {
                    throwOnOption(protobufField, optionName, definition, "expected unsigned long");
                }
            }
        }
    }

    private static void throwOnOption(ProtobufFieldStatement optionParent, String optionName, ProtobufFieldStatement optionDefinition, String errorMessage) {
        throw new ProtobufParserException("Invalid option \"" + optionName
            + "\" for field \"" + optionParent.name()
            + "\" inside " + ((ProtobufTree.WithName) optionParent.parent()).name()
            + " defined by " + ((ProtobufTree.WithName) optionDefinition.parent()).name()
            + ": " + errorMessage);
    }

    private static void attributeType(ProtobufDocumentTree document, ProtobufFieldStatement typedFieldTree) {
        var typeReferences = new LinkedList<ProtobufTypeReference>();
        typeReferences.add(typedFieldTree.type());
        while (!typeReferences.isEmpty()) {
            var typeReference = typeReferences.removeFirst();
            if(typeReference.isAttributed()) {
                continue;
            }

            if(!(typeReference instanceof ProtobufUnresolvedObjectTypeReference(String name))) {
                throw throwUnattributableType(typedFieldTree);
            }

            var types = name.split(TYPE_SELECTOR_SPLITTER);
            var parent = typedFieldTree.parent();

            // Look for the type definition starting from the field's parent
            // Only the first result should be considered because of shadowing (i.e. if a name is reused in an inner scope, the inner scope should override the outer scope)
            ProtobufTree.WithBodyAndName<?> resolvedType = null;
            while (parent != null && resolvedType == null) {
                resolvedType = parent.getDirectChildByNameAndType(types[0], ProtobufTree.WithBodyAndName.class)
                        .orElse(null);
                parent = parent.parent() instanceof ProtobufTree.WithBody<?> validParent ? validParent : null;
            }

            if (resolvedType != null) { // Found a match in the parent scope
                // Try to resolve the type reference in the matched scope
                for (var index = 1; index < types.length; index++) {
                    resolvedType = resolvedType.getDirectChildByNameAndType(types[index], ProtobufTree.WithBodyAndName.class)
                            .orElseThrow(() -> throwUnattributableType(typedFieldTree));
                }
            } else { // No match found in the parent scope, try to resolve the type reference through imports
                for (var statement : document.children()) {
                    if (!(statement instanceof ProtobufImportStatement importStatement)) {
                        continue;
                    }

                    var imported = importStatement.document();
                    if (imported == null) {
                        continue;
                    }

                    var importedTypeName = imported.packageName()
                            .map(packageName -> name.startsWith(packageName + TYPE_SELECTOR) ? name.substring(packageName.length() + 1) : null)
                            .orElse(name)
                            .split(TYPE_SELECTOR_SPLITTER);
                    if (importedTypeName.length == 0) {
                        continue;
                    }

                    resolvedType = imported.getDirectChildByNameAndType(importedTypeName[0], ProtobufTree.WithBodyAndName.class)
                            .orElse(null);
                    for (var i = 1; i < importedTypeName.length && resolvedType != null; i++) {
                        resolvedType = resolvedType.getDirectChildByNameAndType(importedTypeName[i], ProtobufTree.WithBodyAndName.class)
                                .orElse(null);
                    }
                    if (resolvedType != null) {
                        break;
                    }
                }

                if (resolvedType == null) {
                    throw throwUnattributableType(typedFieldTree);
                }
            }

            typedFieldTree.setType(ProtobufTypeReference.of(resolvedType));
        }
    }

    private static ProtobufParserException throwUnattributableType(ProtobufFieldStatement typedFieldTree) {
        return new ProtobufParserException(
                "Cannot resolve type \"%s\" in field \"%s\"%s",
                typedFieldTree.line(),
                typedFieldTree.type().name(),
                typedFieldTree.name(),
                typedFieldTree.parent() instanceof ProtobufTree.WithName withName ? " inside \"%s\"".formatted(withName.name())  : ""
        );
    }
}
