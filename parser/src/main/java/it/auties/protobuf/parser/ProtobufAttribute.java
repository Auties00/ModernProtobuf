package it.auties.protobuf.parser;

import it.auties.protobuf.parser.tree.*;
import it.auties.protobuf.parser.type.*;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.*;

public final class ProtobufAttribute {
    private static final Set<ProtobufDocumentTree> BUILT_INS;
    private static final String TYPE_SELECTOR = ".";
    private static final String TYPE_SELECTOR_SPLITTER = "\\.";

    static {
        try {
            var builtInTypesDirectory = ClassLoader.getSystemClassLoader().getResource("google/protobuf/");
            if(builtInTypesDirectory == null) {
                throw new ProtobufParserException("Missing built-in .proto");
            }

            var builtInTypesPath = Path.of(builtInTypesDirectory.toURI());
            BUILT_INS = ProtobufParser.parse(builtInTypesPath);
        }catch (IOException | URISyntaxException exception) {
            throw new ProtobufParserException("Missing built-in .proto");
        }
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
        if(BUILT_INS != null) {
            for(var builtIn : BUILT_INS) {
                canonicalPathToDocumentMap.put(document.qualifiedPath(), builtIn);
            }
        }
        attributeImports(document, canonicalPathToDocumentMap);
    }

    private static void attributeImports(Collection<ProtobufDocumentTree> documents) {
        var canonicalPathToDocumentMap = buildImportsMap(documents);
        if(BUILT_INS != null) {
            for(var document : BUILT_INS) {
                canonicalPathToDocumentMap.put(document.qualifiedPath(), document);
            }
        }
        for(var document : documents) {
            attributeImports(document, canonicalPathToDocumentMap);
        }
    }

    private static Map<String, ProtobufDocumentTree> buildImportsMap(ProtobufDocumentTree document) {
        var mapSize = 1;
        if(BUILT_INS != null) {
            mapSize += BUILT_INS.size();
        }
        Map<String, ProtobufDocumentTree> canonicalPathToDocumentMap = HashMap.newHashMap(mapSize);
        canonicalPathToDocumentMap.put(document.qualifiedPath(), document);
        return canonicalPathToDocumentMap;
    }

    private static Map<String, ProtobufDocumentTree> buildImportsMap(Collection<ProtobufDocumentTree> documents) {
        var mapSize = documents.size();
        if(BUILT_INS != null) {
            mapSize += BUILT_INS.size();
        }
        Map<String, ProtobufDocumentTree> canonicalPathToDocumentMap = HashMap.newHashMap(mapSize);
        for(var document : documents) {
            canonicalPathToDocumentMap.put(document.qualifiedPath(), document);
        }
        return canonicalPathToDocumentMap;
    }

    private static void attributeImports(ProtobufDocumentTree document, Map<String, ProtobufDocumentTree> canonicalPathToDocumentMap) {
        for(var child : document.body().children()) {
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
                case ProtobufTree.WithBody<?> body -> queue.addAll(body.body().children());

                case ProtobufExpression protobufExpression -> {
                    switch (protobufExpression) {
                        case ProtobufBoolExpression protobufBoolExpression -> {

                        }

                        case ProtobufEnumConstantExpression protobufEnumConstantExpression -> {

                        }

                        case ProtobufIntegerExpression protobufIntegerExpression -> {

                        }

                        case ProtobufLiteralExpression protobufLiteralExpression -> {

                        }

                        case ProtobufNullExpression protobufNullExpression -> {

                        }

                        case ProtobufRangeExpression protobufRangeExpression -> {

                        }

                        case ProtobufReservedChild protobufReservedChild -> {

                        }

                        case ProtobufMessageValueExpression protobufMessageValueExpression -> {

                        }
                    }
                }

                case ProtobufStatement protobufStatement -> {
                    switch (protobufStatement) {
                        case ProtobufEmptyStatement ignored -> {
                            // Nothing to check
                        }

                        case ProtobufExtensionsStatement protobufExtension -> {

                        }

                        case ProtobufFieldStatement protobufField -> {
                            attributeType(document, protobufField);
                        }

                        case ProtobufImportStatement protobufImport -> {
                            if(!protobufImport.hasDocument()) {
                                throw new InternalError("Import statement should already be attributed");
                            }
                        }

                        case ProtobufOptionStatement protobufOption -> queue.add(protobufOption.value());

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
            }
        }
    }

    private static void attributeType(ProtobufDocumentTree document, ProtobufFieldStatement typedFieldTree) {
        var typeReferences = new LinkedList<ProtobufTypeReference>();
        typeReferences.add(typedFieldTree.type());
        while (!typeReferences.isEmpty()) {
            switch (typeReferences.removeFirst()) {
                case ProtobufGroupTypeReference protobufGroupType -> {
                    if(!protobufGroupType.isAttributed()) {
                        throw throwUnattributableType(typedFieldTree);
                    }
                }

                case ProtobufMapTypeReference protobufMapType -> {
                    var keyType = protobufMapType.keyType();
                    typeReferences.add(keyType);
                    var valueType = protobufMapType.valueType();
                    typeReferences.add(valueType);
                }

                case ProtobufMessageOrEnumTypeReference fieldType -> {
                    var accessed = fieldType.name();
                    var types = accessed.split(TYPE_SELECTOR_SPLITTER);
                    var parent = typedFieldTree.parent();

                    // Look for the type definition starting from the field's parent
                    // Only the first result should be considered because of shadowing (i.e. if a name is reused in an inner scope, the inner scope should override the outer scope)
                    ProtobufTree.WithBody<?> resolvedType = null;
                    while (parent != null && resolvedType == null) {
                        resolvedType = parent.body()
                                .getDirectChildByNameAndType(types[0], ProtobufTree.WithBody.class)
                                .orElse(null);
                        parent = parent.parent() instanceof ProtobufTree.WithBody<?> validParent ? validParent : null;
                    }

                    if (resolvedType != null) { // Found a match in the parent scope
                        // Try to resolve the type reference in the matched scope
                        for (var index = 1; index < types.length; index++) {
                            resolvedType = resolvedType.body()
                                    .getDirectChildByNameAndType(types[index], ProtobufTree.WithBody.class)
                                    .orElseThrow(() -> throwUnattributableType(typedFieldTree));
                        }
                    } else { // No match found in the parent scope, try to resolve the type reference through imports
                        for (var statement : document.body().children()) {
                            if (!(statement instanceof ProtobufImportStatement importStatement)) {
                                continue;
                            }

                            var imported = importStatement.document();
                            if (imported == null) {
                                continue;
                            }

                            var simpleName = imported.packageName()
                                    .map(packageName -> accessed.startsWith(packageName + TYPE_SELECTOR) ? accessed.substring(packageName.length() + 1) : null)
                                    .orElse(accessed);
                            var simpleImportName = simpleName.split(TYPE_SELECTOR_SPLITTER);
                            resolvedType = imported;
                            for (var i = 0; i < simpleImportName.length && resolvedType != null; i++) {
                                resolvedType = resolvedType.body()
                                        .getDirectChildByNameAndType(simpleImportName[i], ProtobufTree.WithBody.class)
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

                    fieldType.setDeclaration(resolvedType);
                }

                case ProtobufPrimitiveTypeReference ignored -> {
                    // Nothing to do
                }
            }
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
