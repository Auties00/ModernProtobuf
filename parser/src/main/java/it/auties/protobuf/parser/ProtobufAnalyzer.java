package it.auties.protobuf.parser;

import it.auties.protobuf.annotation.ProtobufEnumIndex;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufType;
import it.auties.protobuf.model.ProtobufVersion;
import it.auties.protobuf.parser.exception.ProtobufParserException;
import it.auties.protobuf.parser.exception.ProtobufSemanticException;
import it.auties.protobuf.parser.tree.*;
import it.auties.protobuf.parser.type.*;

import java.io.IOException;
import java.math.BigInteger;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Performs semantic analysis and validation of parsed Protocol Buffer documents.
 * <p>
 * The analyzer is responsible for the second phase of Protocol Buffer processing, after the
 * {@link ProtobufParser} has constructed the abstract syntax tree. It performs:
 * </p>
 * <ul>
 *   <li><strong>Type Resolution:</strong> Resolves unattributed type references to their declarations,
 *       converting {@link ProtobufUnresolvedObjectTypeReference} to concrete type references</li>
 *   <li><strong>Import Attribution:</strong> Processes import statements and links imported documents
 *       to the importing document, enabling cross-file type resolution</li>
 *   <li><strong>Semantic Validation:</strong> Validates Protocol Buffer language rules including:
 *       <ul>
 *         <li>Field number uniqueness and range validity</li>
 *         <li>Reserved field number and name enforcement</li>
 *         <li>Enum constant uniqueness and first value zero requirement (proto3)</li>
 *         <li>Oneof field constraints</li>
 *         <li>Map key type restrictions</li>
 *         <li>Extension range validity</li>
 *         <li>Option type checking</li>
 *       </ul>
 *   </li>
 *   <li><strong>Built-in Type Integration:</strong> Automatically includes Google's well-known types
 *       and descriptor.proto for option validation</li>
 * </ul>
 * <p>
 * The analyzer is automatically invoked by {@link ProtobufParser} methods, so direct use is typically
 * not necessary unless implementing custom parsing workflows.
 * </p>
 *
 * @see ProtobufParser
 * @see ProtobufDocumentTree
 */
public final class ProtobufAnalyzer {
    private static final String TYPE_SELECTOR = ".";
    private static final String TYPE_SELECTOR_SPLITTER = "\\.";

    private static final BigInteger FIELD_NUMBER_MIN = BigInteger.valueOf(ProtobufProperty.MIN_INDEX);
    private static final BigInteger FIELD_NUMBER_MAX = BigInteger.valueOf(ProtobufProperty.MAX_INDEX);

    private static final BigInteger ENUM_CONSTANT_MIN = BigInteger.valueOf(ProtobufEnumIndex.MIN_VALUE);
    private static final BigInteger ENUM_CONSTANT_MAX = BigInteger.valueOf(ProtobufEnumIndex.MAX_VALUE);

    private static final BigInteger RESERVED_RANGE_MIN = BigInteger.valueOf(19_000);
    private static final BigInteger RESERVED_RANGE_MAX = BigInteger.valueOf(19_999);

    private static final Map<Class<? extends ProtobufTree>, String> TYPE_OPTIONS_MAP = Map.of(
            ProtobufDocumentTree.class, "FileOptions",
            ProtobufMessageStatement.class, "MessageOptions",
            ProtobufEnumStatement.class, "EnumOptions",
            ProtobufOneofFieldStatement.class, "OneOfOptions",
            ProtobufEnumConstantExpression.class, "EnumValueOptions",
            ProtobufFieldStatement.class, "FieldOptions",
            ProtobufServiceStatement.class, "ServiceOptions",
            ProtobufMethodStatement.class, "MethodOptions",
            ProtobufExtendStatement.class, "ExtensionRangeOptions"
    );

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
                .collect(Collectors.toUnmodifiableMap(ProtobufMessageStatement::name, ProtobufAnalyzer::getOptionsForDescriptor));
    }

    private static Map<String, ProtobufFieldStatement> getOptionsForDescriptor(ProtobufMessageStatement statement) {
        return statement.children()
                .stream()
                .filter(child -> child instanceof ProtobufFieldStatement fieldStatement
                        && !Objects.equals(fieldStatement.name(), "uninterpreted_options"))
                .map(child -> (ProtobufFieldStatement) child)
                .collect(Collectors.toUnmodifiableMap(ProtobufFieldStatement::name, Function.identity()));
    }

    private ProtobufAnalyzer() {
        throw new UnsupportedOperationException();
    }

    /**
     * Performs semantic analysis on a collection of parsed Protocol Buffer documents.
     * <p>
     * This method orchestrates the complete semantic analysis process:
     * </p>
     * <ol>
     *   <li>Builds a map of all documents including built-in Google types</li>
     *   <li>Attributes (resolves) import statements for all documents</li>
     *   <li>Attributes type references and validates each document</li>
     * </ol>
     * <p>
     * Documents can reference types defined in other documents through imports or within the
     * same package. The analyzer ensures all type references are valid and properly resolved.
     * </p>
     *
     * @param documents the collection of parsed documents to analyze, must not be null
     * @throws ProtobufSemanticException if semantic validation fails
     * @throws ProtobufParserException if a fatal error occurs during analysis
     */
    public static void attribute(Collection<ProtobufDocumentTree> documents) {
        var canonicalPathToDocumentMap = buildImportsMap(documents);

        // Add built-in types once
        if(BUILT_IN_TYPES != null) {
            BUILT_IN_TYPES.forEach(document -> canonicalPathToDocumentMap.put(document.qualifiedPath(), document));
        }

        // Attribute imports for all documents
        documents.forEach(document -> attributeImports(document, canonicalPathToDocumentMap));

        // Attribute and validate each document
        documents.forEach(ProtobufAnalyzer::attributeDocument);

        // Validate document-level constraints
        documents.forEach(ProtobufAnalyzer::validateDocumentLevel);
    }

    private static Map<String, ProtobufDocumentTree> buildImportsMap(Collection<ProtobufDocumentTree> documents) {
        var mapSize = documents.size() + (BUILT_IN_TYPES != null ? BUILT_IN_TYPES.size() : 0);
        Map<String, ProtobufDocumentTree> canonicalPathToDocumentMap = HashMap.newHashMap(mapSize);
        var visited = new HashSet<ProtobufDocumentTree>();
        documents.forEach(document -> collectAllReferencedDocuments(document, canonicalPathToDocumentMap, visited));
        return canonicalPathToDocumentMap;
    }

    private static void collectAllReferencedDocuments(ProtobufDocumentTree document, Map<String, ProtobufDocumentTree> map, Set<ProtobufDocumentTree> visited) {
        if (!visited.add(document)) {
            return;
        }

        map.put(document.qualifiedPath(), document);

        // Recursively collect all imported documents
        document.children().stream()
                .filter(ProtobufImportStatement.class::isInstance)
                .map(ProtobufImportStatement.class::cast)
                .filter(importStatement -> importStatement.document() != null)
                .forEach(importStatement -> collectAllReferencedDocuments(importStatement.document(), map, visited));
    }

    private static void attributeImports(ProtobufDocumentTree document, Map<String, ProtobufDocumentTree> canonicalPathToDocumentMap) {
        document.children().stream()
                .filter(ProtobufImportStatement.class::isInstance)
                .map(ProtobufImportStatement.class::cast)
                .filter(importStatement -> !importStatement.isAttributed())
                .forEach(importStatement -> {
                    var imported = canonicalPathToDocumentMap.get(importStatement.location());
                    ProtobufSemanticException.check(imported != null,
                            "Cannot resolve import '%s'\n\nThe imported file could not be found or loaded.",
                            importStatement.line(), importStatement.location());
                    importStatement.setDocument(imported);
                });
    }

    private static void attributeDocument(ProtobufDocumentTree document) {
        var queue = new LinkedList<ProtobufTree>();
        queue.add(document);
        while (!queue.isEmpty()) {
            var tree = queue.removeFirst();
            switch (tree) {
                case ProtobufExtendStatement extendStatement -> {
                    attributeStatement(document, extendStatement);
                    queue.addAll(extendStatement.children());
                }
                case ProtobufOneofFieldStatement oneofStatement -> {
                    attributeStatement(document, oneofStatement);
                    queue.addAll(oneofStatement.children());
                }
                case ProtobufTree.WithBody<?> body -> queue.addAll(body.children());
                case ProtobufStatement protobufStatement -> attributeStatement(document, protobufStatement);
                case ProtobufExpression ignored -> throw new InternalError("Expressions should be attributed in-place");
            }
        }
    }

    private static void attributeStatement(ProtobufDocumentTree document, ProtobufStatement protobufStatement) {
        switch (protobufStatement) {
            case ProtobufEmptyStatement emptyStatement when !emptyStatement.isAttributed() ->
                throw new InternalError("Empty statement should already be attributed");
            case ProtobufExtendStatement extendStatement -> validateExtendDeclaration(document, extendStatement);
            case ProtobufExtensionsStatement protobufExtension -> validateExtensions(document, protobufExtension);
            case ProtobufOneofFieldStatement oneofStatement -> validateOneof(oneofStatement);
            case ProtobufFieldStatement protobufField -> {
                attributeType(document, protobufField);
                validateField(document, protobufField);
                protobufField.options().forEach(option -> attributeFieldOption(document, protobufField, option));
            }
            case ProtobufImportStatement protobufImport when !protobufImport.isAttributed() ->
                throw new InternalError("Import statement should already be attributed");
            case ProtobufOptionStatement protobufOption -> validateOption(protobufOption);
            case ProtobufPackageStatement packageStatement -> validatePackage(packageStatement);
            case ProtobufReservedStatement protobufReserved -> validateReserved(protobufReserved);
            case ProtobufSyntaxStatement protobufSyntax when !protobufSyntax.hasVersion() ->
                throw new InternalError("Syntax statement should already be attributed");
            default -> {}
        }
    }

    private static void validateExtendDeclaration(ProtobufDocumentTree document, ProtobufExtendStatement extendStatement) {
        // Resolve the declaration type
        var declaration = extendStatement.declaration();
        if (declaration instanceof ProtobufUnresolvedObjectTypeReference(var name)) {
            var resolvedType = tryResolveType(document, name, extendStatement);
            ProtobufSemanticException.check(resolvedType != null,
                    "Cannot resolve extended type '%s'\n\nThe message type being extended could not be found.\n\nHelp: Make sure:\n      1. The message is defined in this file or imported\n      2. The message name is spelled correctly\n      3. If the message is in another package, use the fully qualified name\n      \n      Example:\n        extend MyMessage {{ ... }}\n        extend com.example.MyMessage {{ ... }}",
                    extendStatement.line(), name);
            extendStatement.setDeclaration(resolvedType);
            declaration = resolvedType;
        }

        // Validate extend statement
        if (!(declaration instanceof ProtobufMessageTypeReference(var messageDeclaration))) {
            return; // Can only extend messages
        }

        // Check that the target message has declared extension ranges
        var hasExtensionRanges = messageDeclaration.children().stream()
                .anyMatch(ProtobufExtensionsStatement.class::isInstance);

        ProtobufSemanticException.check(hasExtensionRanges,
                "Cannot extend message '%s' - no extension ranges declared\n\nThe message must explicitly declare extension ranges before it can be extended.\n\nHelp: Add an 'extensions' declaration to the message being extended:\n      message %s {{\n        // ... existing fields ...\n        extensions 100 to 199;  // Reserve field numbers for extensions\n      }}\n      \n      Then extend it:\n      extend %s {{\n        string my_extension = 100;\n      }}",
                extendStatement.line(), messageDeclaration.name(), messageDeclaration.name(), messageDeclaration.name());

        // Validate each extension field
        for(var child : extendStatement.children()) {
            switch (child) {
                case ProtobufOneofFieldStatement ignored -> throw new ProtobufSemanticException("Extensions cannot contain oneof fields\n\nOneof fields cannot be used in extend declarations.\n\nHelp: Only regular fields are allowed in extensions.\n      Remove the oneof block and use regular fields instead.", extendStatement.line());
                case ProtobufFieldStatement field -> validateExtensionField(messageDeclaration, field);
                default -> {}
            }
        }
    }

    private static void validateExtensionField(ProtobufMessageStatement targetMessage, ProtobufFieldStatement field) {
        // Extension fields cannot be required
        ProtobufSemanticException.check(field.modifier() != ProtobufFieldStatement.Modifier.REQUIRED,
                "Extension field '%s' cannot be required\n\nExtension fields cannot use the 'required' modifier.\nThis ensures backward compatibility with code that doesn't know about the extension.\n\nHelp: Remove the 'required' modifier or use 'optional' instead:\n      extend MyMessage {{\n        optional string %s = 100;  // OK\n        string %s = 100;           // Also OK (implicitly optional)\n      }}",
                field.line(), field.name(), field.name(), field.name());

        // Extension fields cannot be map types
        ProtobufSemanticException.check(!(field.type() instanceof ProtobufMapTypeReference),
                "Extension field '%s' cannot be a map type\n\nMap fields are not allowed in extensions.\n\nHelp: If you need a map-like structure, use a repeated message with key-value pairs:\n      message KeyValue {{\n        string key = 1;\n        ValueType value = 2;\n      }}\n      extend MyMessage {{\n        repeated KeyValue %s = 100;\n      }}",
                field.line(), field.name(), field.name());

        // Validate field number is within declared extension ranges
        var fieldNumber = field.index();
        if(fieldNumber == null) {
            return;
        }

        var fieldNumberValue = fieldNumber.value();
        var inValidRange = targetMessage.children().stream()
                .filter(ProtobufExtensionsStatement.class::isInstance)
                .map(ProtobufExtensionsStatement.class::cast)
                .flatMap(extensionsStatement -> extensionsStatement.expressions().stream())
                .anyMatch(expression -> isNumberInExpression(fieldNumberValue, expression));

        ProtobufSemanticException.check(inValidRange,
                "Extension field '%s' with number %s is outside declared extension ranges\n\nThe field number must be within one of the extension ranges declared in message '%s'.\n\nHelp: Choose a field number within the declared extension ranges,\n      or update the message to include a range that covers %s:\n      \n      message %s {{\n        extensions 100 to 199;  // Declared ranges\n      }}",
                field.line(), field.name(), fieldNumberValue, targetMessage.name(), fieldNumberValue, targetMessage.name());
    }

    private static boolean isNumberInExpression(BigInteger number, ProtobufExpression expression) {
        return switch (expression) {
            case ProtobufNumberExpression numberExpr
                    when numberExpr.value() instanceof ProtobufInteger(var value) -> number.equals(value);
            case ProtobufIntegerRangeExpression rangeExpr -> switch (rangeExpr.value()) {
                case ProtobufRange.Bounded(var min, var max) ->
                    number.compareTo(min.value()) >= 0 && number.compareTo(max.value()) <= 0;
                case ProtobufRange.LowerBounded(var min) ->
                    number.compareTo(min.value()) >= 0;
            };
            default -> false;
        };
    }

    private static ProtobufTypeReference tryResolveType(ProtobufDocumentTree document, String originalName, ProtobufTree context) {
        var isGlobalScope = originalName.startsWith(TYPE_SELECTOR);
        var name = isGlobalScope ? originalName.substring(1) : originalName;
        var types = name.split(TYPE_SELECTOR_SPLITTER);

        // Look for type in parent scope (if not global scope)
        ProtobufTree.WithBodyAndName<?> resolvedType = null;
        if(!isGlobalScope) {
            resolvedType = findTypeInParentScope(context.parent(), types[0]);
            if(resolvedType != null) {
                resolvedType = resolveNestedTypes(resolvedType, types, 1);
            }
        }

        // If not found in parent scope, try imports and current document
        if(resolvedType == null) {
            var visibleImports = collectVisibleImports(document);
            var documentsToCheck = new ArrayList<ProtobufDocumentTree>(visibleImports.size() + 1);
            documentsToCheck.add(document);
            documentsToCheck.addAll(visibleImports);

            resolvedType = findTypeInDocuments(documentsToCheck, name);
        }

        return switch (resolvedType) {
            case ProtobufEnumStatement enumeration -> new ProtobufEnumTypeReference(enumeration);
            case ProtobufMessageStatement message -> new ProtobufMessageTypeReference(message);
            case ProtobufOneofFieldStatement _ -> throw new IllegalArgumentException("Cannot resolve a type reference for a oneof field");
            case ProtobufServiceStatement _ -> throw new IllegalArgumentException("Cannot resolve a type reference for a service");
            case null -> null;
        };
    }

    private static ProtobufTree.WithBodyAndName<?> findTypeInParentScope(ProtobufTree parent, String typeName) {
        while (parent != null) {
            if (parent instanceof ProtobufTree.WithBody<?> withBody) {
                var found = withBody.getDirectChildByNameAndType(typeName, ProtobufTree.WithBodyAndName.class)
                        .orElse(null);
                if(found != null) {
                    return found;
                }
                parent = parent.parent() instanceof ProtobufTree.WithBody<?> validParent ? validParent : null;
            } else {
                parent = null;
            }
        }
        return null;
    }

    private static ProtobufTree.WithBodyAndName<?> resolveNestedTypes(ProtobufTree.WithBodyAndName<?> current, String[] types, int startIndex) {
        for (var i = startIndex; i < types.length && current != null; i++) {
            current = current.getDirectChildByNameAndType(types[i], ProtobufTree.WithBodyAndName.class).orElse(null);
        }
        return current;
    }

    private static ProtobufTree.WithBodyAndName<?> findTypeInDocuments(List<ProtobufDocumentTree> documents, String name) {
        for (var documentToCheck : documents) {
            var typeName = documentToCheck.packageName()
                    .map(packageName -> name.startsWith(packageName + TYPE_SELECTOR) ? name.substring(packageName.length() + 1) : null)
                    .orElse(name)
                    .split(TYPE_SELECTOR_SPLITTER);

            if (typeName.length == 0) {
                continue;
            }

            var resolvedType = documentToCheck.getDirectChildByNameAndType(typeName[0], ProtobufTree.WithBodyAndName.class).orElse(null);
            resolvedType = resolveNestedTypes(resolvedType, typeName, 1);

            if (resolvedType != null) {
                return resolvedType;
            }
        }
        return null;
    }

    private static void attributeFieldOption(ProtobufDocumentTree document, ProtobufFieldStatement protobufField, ProtobufOptionExpression option) {
        if(BUILT_IN_OPTIONS == null) {
            return; // Options validation cannot happen during the bootstrap phase
        }

        var optionName = option.name().name();
        switch (optionName) {
            case "default" -> validateDefaultOption(document, protobufField, option);
            case "packed" -> validatePackedOption(protobufField, option);
            case "json_name" -> validateJsonNameOption(option);
            default -> validateCustomOption(protobufField, option, optionName);
        }
    }

    private static void validateDefaultOption(ProtobufDocumentTree document, ProtobufFieldStatement protobufField, ProtobufOptionExpression option) {
        var syntax = document.syntax().orElse(null);

        // Proto3: Default values are NOT allowed
        ProtobufSemanticException.check(syntax != ProtobufVersion.PROTOBUF_3,
                "Default values are not allowed in proto3", option.line());

        // Proto2: Cannot be used on repeated fields
        ProtobufSemanticException.check(protobufField.modifier() != ProtobufFieldStatement.Modifier.REPEATED,
                "Default values cannot be used on repeated fields", option.line());

        // Proto2: Cannot be used on required fields
        ProtobufSemanticException.check(protobufField.modifier() != ProtobufFieldStatement.Modifier.REQUIRED,
                "Default values cannot be used on required fields", option.line());

        // Proto2: Cannot be used on message-typed fields
        var fieldType = protobufField.type().protobufType();
        ProtobufSemanticException.check(fieldType != ProtobufType.MESSAGE,
                "Default values cannot be used on message-typed fields", option.line());

        // Validate value matches field type
        var value = option.value();
        var isValidType = switch (fieldType) {
            case INT32, INT64, UINT32, UINT64, SINT32, SINT64, FIXED32, FIXED64, SFIXED32, SFIXED64, FLOAT, DOUBLE ->
                value instanceof ProtobufNumberExpression;
            case STRING, BYTES -> value instanceof ProtobufLiteralExpression;
            case BOOL -> value instanceof ProtobufBoolExpression;
            case ENUM -> value instanceof ProtobufEnumConstantExpression;
            default -> false;
        };

        ProtobufSemanticException.check(isValidType,
                "Default value type mismatch for field \"%s\": expected %s but got %s",
                option.line(), protobufField.name(), fieldType, value.getClass().getSimpleName());

        // Validate numeric range for int32 fields
        if (fieldType == ProtobufType.INT32) {
            var numExpr = (ProtobufNumberExpression) value;
            if (numExpr.value() instanceof ProtobufInteger(var intValue)) {
                var int32Min = BigInteger.valueOf(Integer.MIN_VALUE);
                var int32Max = BigInteger.valueOf(Integer.MAX_VALUE);
                ProtobufSemanticException.check(
                        intValue.compareTo(int32Min) >= 0 && intValue.compareTo(int32Max) <= 0,
                        "Default value %s is out of int32 range (%d to %d) for field \"%s\"",
                        option.line(), intValue, Integer.MIN_VALUE, Integer.MAX_VALUE, protobufField.name());
            }
        }

        // Validate enum constant exists
        if (fieldType == ProtobufType.ENUM) {
            var enumExpr = (ProtobufEnumConstantExpression) value;
            var fieldTypeRef = protobufField.type();
            if (fieldTypeRef instanceof ProtobufEnumTypeReference(var declaration)) {
                var constantName = enumExpr.name();
                var constantExists = declaration.children().stream()
                        .filter(ProtobufEnumConstantStatement.class::isInstance)
                        .map(ProtobufEnumConstantStatement.class::cast)
                        .anyMatch(constant -> constant.name().equals(constantName));
                ProtobufSemanticException.check(constantExists,
                        "Default value references non-existent enum constant \"%s\" in enum \"%s\"",
                        option.line(), constantName, declaration.name());
            }
        }
    }

    private static void validatePackedOption(ProtobufFieldStatement protobufField, ProtobufOptionExpression option) {
        // Packed option only allowed on repeated fields
        ProtobufSemanticException.check(protobufField.modifier() == ProtobufFieldStatement.Modifier.REPEATED,
                "Packed option can only be used on repeated fields", option.line());

        var fieldType = protobufField.type().protobufType();
        // Check if it's a numeric scalar type (not string, bytes, message, map, group)
        var isNumericScalar = switch (fieldType) {
            case STRING, BYTES, MESSAGE, MAP, GROUP, UNKNOWN -> false;
            default -> true;
        };

        ProtobufSemanticException.check(isNumericScalar,
                "Packed option can only be used on repeated numeric scalar fields", option.line());
    }

    private static void validateJsonNameOption(ProtobufOptionExpression option) {
        // json_name value must be a string
        var value = option.value();
        ProtobufSemanticException.check(value instanceof ProtobufLiteralExpression,
                "json_name option must have a string value", option.line());
    }

    private static void validateCustomOption(ProtobufFieldStatement protobufField, ProtobufOptionExpression option, String optionName) {
        // Skip validation for fields inside extend statements - these are custom option definitions
        if(protobufField.parent() instanceof ProtobufExtendStatement) {
            return;
        }

        var fieldOptions = BUILT_IN_OPTIONS.get("FieldOptions");
        if(fieldOptions == null) {
            throw new ProtobufSemanticException("Cannot validate statement options: missing FieldOptions message");
        }

        var definition = fieldOptions.get(optionName);
        ProtobufSemanticException.check(definition != null || option.name().extension(),
                "Invalid option \"%s\" for field \"%s\" inside %s",
                option.line(), optionName, protobufField.name(), getParentContextName(protobufField));
    }

    private static String getParentContextName(ProtobufFieldStatement field) {
        return switch (field.parent()) {
            case ProtobufTree.WithName withName -> withName.name();
            case ProtobufExtendStatement extendStatement -> "extend " + extendStatement.declaration().name();
            case null, default -> "unknown context";
        };
    }

    private static void attributeType(ProtobufDocumentTree document, ProtobufFieldStatement typedFieldTree) {
        var typeReferences = new LinkedList<ProtobufTypeReference>();
        typeReferences.add(typedFieldTree.type());

        while (!typeReferences.isEmpty()) {
            var typeReference = typeReferences.removeFirst();
            if(typeReference.isAttributed()) {
                continue;
            }

            // Handle map types specially - need to attribute key and value types
            if(typeReference instanceof ProtobufMapTypeReference mapType) {
                attributeMapType(document, typedFieldTree, mapType);
                continue;
            }

            if(!(typeReference instanceof ProtobufUnresolvedObjectTypeReference(var originalName))) {
                throw throwUnattributableType(typedFieldTree);
            }

            var resolvedType = tryResolveType(document, originalName, typedFieldTree);
            if(resolvedType == null) {
                throw throwUnattributableType(typedFieldTree);
            }

            typedFieldTree.setType(resolvedType);
        }
    }

    private static void attributeMapType(ProtobufDocumentTree document, ProtobufFieldStatement typedFieldTree, ProtobufMapTypeReference mapType) {
        // Attribute key type
        if(mapType.keyType() instanceof ProtobufUnresolvedObjectTypeReference(var keyName)) {
            var resolvedKey = tryResolveType(document, keyName, typedFieldTree);
            ProtobufSemanticException.check(resolvedKey != null,
                    "Cannot resolve map key type \"%s\" in field \"%s\"",
                    typedFieldTree.line(), keyName, typedFieldTree.name());
            mapType.setKeyType(resolvedKey);
        }

        // Attribute value type
        if(mapType.valueType() instanceof ProtobufUnresolvedObjectTypeReference(var valueName)) {
            var resolvedValue = tryResolveType(document, valueName, typedFieldTree);
            ProtobufSemanticException.check(resolvedValue != null,
                    "Cannot resolve map value type \"%s\" in field \"%s\"",
                    typedFieldTree.line(), valueName, typedFieldTree.name());
            mapType.setValueType(resolvedValue);
        }
    }

    private static List<ProtobufDocumentTree> collectVisibleImports(ProtobufDocumentTree document) {
        var result = new ArrayList<ProtobufDocumentTree>();
        var visited = new HashSet<ProtobufDocumentTree>();
        collectVisibleImportsRecursive(document, result, visited, true);
        // Always include built-in types (google.protobuf.*)
        if(BUILT_IN_TYPES != null) {
            result.addAll(BUILT_IN_TYPES);
        }
        return result;
    }

    private static void collectVisibleImportsRecursive(ProtobufDocumentTree document, List<ProtobufDocumentTree> result, Set<ProtobufDocumentTree> visited, boolean includeNonPublic) {
        if (!visited.add(document)) {
            return;
        }

        document.children().stream()
                .filter(ProtobufImportStatement.class::isInstance)
                .map(ProtobufImportStatement.class::cast)
                .filter(importStatement -> importStatement.document() != null)
                .forEach(importStatement -> {
                    var imported = importStatement.document();
                    var isPublic = importStatement.modifier() == ProtobufImportStatement.Modifier.PUBLIC;

                    // Add this import if it's a direct import OR if it's a public import we're traversing
                    if (includeNonPublic || isPublic) {
                        result.add(imported);
                    }

                    // Always recursively collect public imports from any imported document
                    collectVisibleImportsRecursive(imported, result, visited, false);
                });
    }

    private static ProtobufParserException throwUnattributableType(ProtobufFieldStatement typedFieldTree) {
        var insideContext = typedFieldTree.parent() instanceof ProtobufTree.WithName withName
                ? " inside \"%s\"".formatted(withName.name()) : "";
        return new ProtobufParserException(
                "Cannot resolve type '%s' in field '%s'%s\n\nThe type '%s' is not defined or imported in this .proto file.\n\nHelp: Make sure the type is:\n      1. Defined in this file before it's used\n      2. Imported from another .proto file: import \"path/to/file.proto\";\n      3. A valid scalar type: int32, int64, uint32, uint64, sint32, sint64,\n         fixed32, fixed64, sfixed32, sfixed64, double, float, bool, string, bytes\n      4. Qualified with the correct package: package.MessageName\n\n      If this is a message or enum, check for typos in the type name.",
                typedFieldTree.line(),
                typedFieldTree.type().name(),
                typedFieldTree.name(),
                insideContext,
                typedFieldTree.type().name()
        );
    }

    private static void validateExtensions(ProtobufDocumentTree document, ProtobufExtensionsStatement extensionsStatement) {
        var syntax = document.syntax().orElse(null);
        var parent = extensionsStatement.parent();

        // Proto3: extensions only allowed for options
        if(syntax == ProtobufVersion.PROTOBUF_3 && parent instanceof ProtobufMessageStatement message) {
            var messageName = message.name();
            ProtobufSemanticException.check(messageName != null && messageName.endsWith("Options"),
                    "Extensions are not allowed in proto3 except for custom options\n\nYou're using 'extensions' in proto3, but extensions are only allowed in messages\nwhose names end with 'Options' (for defining custom options).\n\nHelp: In proto3, use one of these alternatives:\n      1. If you need to extend the protocol, use the 'Any' type:\n         import \"google/protobuf/any.proto\";\n         message MyMessage {{\n           google.protobuf.Any extra_data = 1;\n         }}\n\n      2. If you're defining custom options, ensure your message name ends with 'Options'\n\n      3. Use regular message composition instead of extensions\n\n      Note: Proto2 extensions are generally discouraged in favor of proto3's simpler model.",
                    extensionsStatement.line());
        }

        if(!(parent instanceof ProtobufMessageStatement message)) {
            return;
        }

        // Build list of extension ranges
        var extensionRanges = extensionsStatement.expressions().stream()
                .map(ProtobufAnalyzer::toRange)
                .filter(Objects::nonNull)
                .toList();

        // Check for overlaps with regular field numbers and reserved ranges
        validateExtensionRangeOverlaps(message, extensionsStatement, extensionRanges);
    }

    private static ProtobufRange toRange(ProtobufExpression expression) {
        return switch (expression) {
            case ProtobufNumberExpression numberExpr when numberExpr.value() instanceof ProtobufInteger integer ->
                new ProtobufRange.Bounded(integer, integer);
            case ProtobufIntegerRangeExpression rangeExpr -> rangeExpr.value();
            default -> null;
        };
    }

    private static void validateExtensionRangeOverlaps(ProtobufMessageStatement message, ProtobufExtensionsStatement extensionsStatement, List<ProtobufRange> extensionRanges) {
        // Check for overlaps with regular field numbers
        message.children().stream()
                .filter(ProtobufFieldStatement.class::isInstance)
                .map(ProtobufFieldStatement.class::cast)
                .filter(field -> field.index() != null)
                .forEach(field -> {
                    var fieldNumberValue = field.index().value();
                    extensionRanges.stream()
                            .filter(extRange -> isInRange(fieldNumberValue, extRange))
                            .findFirst()
                            .ifPresent(ignored -> {
                                throw new ProtobufSemanticException(
                                        "Extension range overlaps with field \"%s\" (field number %s)",
                                        extensionsStatement.line(), field.name(), fieldNumberValue);
                            });
                });

        // Check for overlaps with reserved ranges
        message.children().stream()
                .filter(ProtobufReservedStatement.class::isInstance)
                .map(ProtobufReservedStatement.class::cast)
                .flatMap(reserved -> reserved.expressions().stream())
                .forEach(expression -> validateReservedExtensionOverlap(extensionsStatement, extensionRanges, expression));
    }

    private static void validateReservedExtensionOverlap(ProtobufExtensionsStatement extensionsStatement, List<ProtobufRange> extensionRanges, ProtobufExpression expression) {
        switch (expression) {
            case ProtobufNumberExpression numberExpr when numberExpr.value() instanceof ProtobufInteger(var value) ->
                    extensionRanges.stream()
                            .filter(extRange -> isInRange(value, extRange))
                            .findFirst()
                            .ifPresent(ignored -> {
                                throw new ProtobufSemanticException(
                                        "Extension range overlaps with reserved number %s",
                                        extensionsStatement.line(), value);
                            });
            case ProtobufIntegerRangeExpression rangeExpr -> {
                var reservedRange = rangeExpr.value();
                extensionRanges.stream()
                        .filter(extRange -> rangesOverlap(extRange, reservedRange))
                        .findFirst()
                        .ifPresent(ignored -> {
                            throw new ProtobufSemanticException(
                                    "Extension range overlaps with reserved range",
                                    extensionsStatement.line());
                        });
            }
            default -> {}
        }
    }

    private static boolean isInRange(BigInteger number, ProtobufRange range) {
        return switch (range) {
            case ProtobufRange.Bounded(var min, var max) ->
                number.compareTo(min.value()) >= 0 && number.compareTo(max.value()) <= 0;
            case ProtobufRange.LowerBounded(var min) ->
                number.compareTo(min.value()) >= 0;
        };
    }

    private static void validateOption(ProtobufOptionStatement optionStatement) {
        if(BUILT_IN_OPTIONS == null) {
            return; // Options validation cannot happen during the bootstrap phase
        }

        // Determine which options map to use based on parent context
        var optionsMapKey = TYPE_OPTIONS_MAP.get(optionStatement.parent().getClass());
        if(optionsMapKey == null) {
            return; // Unknown context
        }

        var optionsMap = BUILT_IN_OPTIONS.get(optionsMapKey);
        if(optionsMap == null) {
            return; // No validation possible
        }

        var optionName = optionStatement.name().name();
        var isExtension = optionStatement.name().extension();
        var definition = optionsMap.get(optionName);

        if(definition != null) {
            // Standard option - must not use parentheses
            ProtobufSemanticException.check(!isExtension,
                    "Standard option \"%s\" should not use parentheses", optionStatement.line(), optionName);

            // Validate option value type matches the definition
            validateOptionValueType(optionStatement, definition);
        } else {
            // Not a standard option - must use parentheses (custom option)
            ProtobufSemanticException.check(isExtension,
                    "Unknown option \"%s\". Custom options must use parentheses, e.g., \"(%s)\"",
                    optionStatement.line(), optionName, optionName);
        }
    }

    private static void validateOptionValueType(ProtobufOptionStatement option, ProtobufFieldStatement definition) {
        var expectedType = definition.type().protobufType();
        var actualValue = option.value();

        var isValidType = switch (expectedType) {
            case INT32, INT64, UINT32, UINT64, SINT32, SINT64, FIXED32, FIXED64, SFIXED32, SFIXED64, FLOAT, DOUBLE ->
                actualValue instanceof ProtobufNumberExpression;
            case STRING, BYTES -> actualValue instanceof ProtobufLiteralExpression;
            case BOOL -> actualValue instanceof ProtobufBoolExpression;
            case ENUM -> actualValue instanceof ProtobufEnumConstantExpression;
            case MESSAGE -> actualValue instanceof ProtobufMessageValueExpression;
            default -> true; // Unknown type, can't validate
        };

        ProtobufSemanticException.check(isValidType,
                "Option \"%s\" has invalid value type: expected %s but got %s",
                option.line(), option.name().name(), expectedType, actualValue.getClass().getSimpleName());
    }

    private static void validateReserved(ProtobufReservedStatement reservedStatement) {
        var expressions = reservedStatement.expressions();
        if(expressions.isEmpty()) {
            return;
        }

        // Check if mixing numbers and names (not allowed) and collect ranges
        boolean hasNumbers = false;
        boolean hasNames = false;
        var ranges = new ArrayList<ProtobufRange>();

        for(var expression : expressions) {
            switch (expression) {
                case ProtobufLiteralExpression ignored -> hasNames = true;
                case ProtobufNumberExpression numberExpr when numberExpr.value() instanceof ProtobufInteger integer -> {
                    hasNumbers = true;
                    validateReservedNumberRange(integer.value(), reservedStatement);
                    ranges.add(new ProtobufRange.Bounded(integer, integer));
                }
                case ProtobufIntegerRangeExpression rangeExpr -> {
                    hasNumbers = true;
                    var range = rangeExpr.value();
                    validateReservedRangeOrder(range, reservedStatement);
                    ranges.add(range);
                }
                default -> {}
            }
        }

        ProtobufSemanticException.check(!(hasNumbers && hasNames),
                "Cannot mix reserved numbers and names in the same statement", reservedStatement.line());

        // Check for overlapping ranges
        if(hasNumbers) {
            checkOverlappingRanges(ranges, reservedStatement);
        }
    }

    private static void validateReservedNumberRange(BigInteger value, ProtobufReservedStatement reservedStatement) {
        ProtobufSemanticException.check(value.compareTo(BigInteger.ZERO) > 0,
                "Reserved number %s is invalid: must be at least 1", reservedStatement.line(), value);

        ProtobufSemanticException.check(value.compareTo(FIELD_NUMBER_MAX) <= 0,
                "Reserved number %s is invalid: must be at most 536870911", reservedStatement.line(), value);
    }

    private static void validateReservedRangeOrder(ProtobufRange range, ProtobufReservedStatement reservedStatement) {
        if(range instanceof ProtobufRange.Bounded(var min, var max)) {
            ProtobufSemanticException.check(min.value().compareTo(max.value()) <= 0,
                    "Invalid reserved range %s to %s: start must be <= end",
                    reservedStatement.line(), min.value(), max.value());
        }
    }

    private static void checkOverlappingRanges(List<ProtobufRange> ranges, ProtobufReservedStatement reservedStatement) {
        for(var i = 0; i < ranges.size(); i++) {
            for(var j = i + 1; j < ranges.size(); j++) {
                ProtobufSemanticException.check(!rangesOverlap(ranges.get(i), ranges.get(j)),
                        "Reserved ranges overlap", reservedStatement.line());
            }
        }
    }

    private static boolean rangesOverlap(ProtobufRange range1, ProtobufRange range2) {
        return switch (range1) {
            case ProtobufRange.Bounded(var min1, var max1) -> switch (range2) {
                case ProtobufRange.Bounded(var min2, var max2) -> {
                    // Check if ranges overlap: [a,b] and [c,d] overlap if max(a,c) <= min(b,d)
                    var max = min1.value().max(min2.value());
                    var min = max1.value().min(max2.value());
                    yield max.compareTo(min) <= 0;
                }
                case ProtobufRange.LowerBounded(var min2) ->
                    // [a,b] overlaps with [c,max] if b >= c
                    max1.value().compareTo(min2.value()) >= 0;
            };
            case ProtobufRange.LowerBounded(var min1) -> switch (range2) {
                case ProtobufRange.Bounded(var min2, var max2) ->
                    // [a,max] overlaps with [b,c] if c >= a
                    max2.value().compareTo(min1.value()) >= 0;
                case ProtobufRange.LowerBounded ignored ->
                    // Both extend to max, always overlap
                    true;
            };
        };
    }

    private static void validatePackage(ProtobufPackageStatement packageStatement) {
        var packageName = packageStatement.name();
        if(packageName == null || packageName.isEmpty()) {
            return;
        }

        ProtobufSemanticException.check(!packageName.startsWith("."),
                "Package name cannot start with a dot", packageStatement.line());

        ProtobufSemanticException.check(!packageName.endsWith("."),
                "Package name cannot end with a dot", packageStatement.line());

        ProtobufSemanticException.check(!packageName.contains(".."),
                "Package name cannot contain consecutive dots", packageStatement.line());
    }

    private static void validateOneof(ProtobufOneofFieldStatement oneofStatement) {
        // Oneof must contain at least one field
        var hasFields = oneofStatement.children().stream()
                .anyMatch(ProtobufFieldStatement.class::isInstance);

        ProtobufSemanticException.check(hasFields,
                "Oneof \"%s\" must contain at least one field", oneofStatement.line(), oneofStatement.name());

        // Oneof cannot contain groups
        for (var child : oneofStatement.children()) {
            if (child instanceof ProtobufGroupFieldStatement group) {
                throw new ProtobufSemanticException(
                        "Oneof \"%s\" cannot contain group \"%s\"\n\nGroups are not allowed inside oneofs.",
                        oneofStatement.line(), oneofStatement.name(), group.name());
            }
        }
    }

    private static void validateField(ProtobufDocumentTree document, ProtobufFieldStatement field) {
        var syntax = document.syntax().orElse(null);
        var parent = field.parent();

        // Determine the enclosing message and validate accordingly
        switch (parent) {
            case ProtobufMessageStatement message -> validateMessageField(field, message, syntax);
            case ProtobufOneofFieldStatement oneof when oneof.parent() instanceof ProtobufMessageStatement message -> {
                validateOneofField(field);
                validateMessageField(field, message, syntax);
            }
            case ProtobufEnumStatement enumStatement ->
                validateEnumConstant(document, enumStatement, (ProtobufEnumConstantStatement) field);
            default -> {} // Not in a validation context
        }
    }

    private static void validateMessageField(ProtobufFieldStatement field, ProtobufMessageStatement enclosingMessage, ProtobufVersion syntax) {
        // Validate field number range
        validateFieldNumber(field, syntax);

        // Proto3: "required" modifier not allowed
        ProtobufSemanticException.check(syntax != ProtobufVersion.PROTOBUF_3 || field.modifier() != ProtobufFieldStatement.Modifier.REQUIRED,
                "Field '%s' cannot use 'required' modifier in proto3\n\nProto3 simplified field presence and removed the 'required' modifier.\nAll singular fields in proto3 are optional by default.\n\nHelp: In proto3:\n      - Remove the 'required' keyword\n      - Fields are optional by default and return default values when not set\n      - Use 'optional' keyword if you need explicit presence detection\n      \n      Example:\n        Proto2: required string name = 1;\n        Proto3: string name = 1;  // Already optional\n        Proto3: optional string name = 1;  // Explicit presence tracking",
                field.line(), field.name());

        // Validate map field restrictions
        if(field.type() instanceof ProtobufMapTypeReference mapType) {
            validateMapField(field, mapType);
        }

        // Check for duplicate field names and numbers within the message
        validateFieldUniqueness(enclosingMessage, field);

        // Check reserved names and numbers
        validateFieldNotReserved(enclosingMessage, field);

        // Check for name conflicts with nested types
        validateFieldNameConflicts(enclosingMessage, field);
    }

    private static void validateFieldNumber(ProtobufFieldStatement field, ProtobufVersion syntax) {
        var index = field.index();
        if(index == null) {
            return;
        }

        var fieldNumber = index.value();

        // Field numbers must be between 1 and 536,870,911
        ProtobufSemanticException.check(fieldNumber.compareTo(FIELD_NUMBER_MIN) >= 0,
                "Field \"%s\" has invalid field number %s\n\nField numbers must be positive integers starting from 1.\nYou used: %s\n\nHelp: Field numbers are used to identify fields in the binary format and must be\n      unique within each message. Use field number 1 for your first field.\n      Example: string name = 1;",
                field.line(), field.name(), fieldNumber, fieldNumber);

        ProtobufSemanticException.check(fieldNumber.compareTo(FIELD_NUMBER_MAX) <= 0,
                "Field \"%s\" has invalid field number %s\n\nField numbers must not exceed 536,870,911 (2^29 - 1).\nYou used: %s\n\nHelp: This is the maximum field number allowed by the Protocol Buffers specification.\n      Consider reorganizing your message or splitting it into multiple messages if you\n      need more fields.\n\n      Field number ranges:\n        1-15:          Use for frequently set fields (1 byte encoding)\n        16-2047:       Standard range (2 bytes encoding)\n        2048-536870911: Extended range (more bytes encoding)",
                field.line(), field.name(), fieldNumber, fieldNumber);

        // Reserved range 19000-19999 for Protobuf implementation
        ProtobufSemanticException.check(fieldNumber.compareTo(RESERVED_RANGE_MIN) < 0 || fieldNumber.compareTo(RESERVED_RANGE_MAX) > 0,
                "Field \"%s\" uses field number %s which is reserved\n\nThe range 19000-19999 is reserved for the Protocol Buffers implementation.\nYou cannot use field numbers in this range.\n\nHelp: Choose a different field number outside this range.\n      Recommended ranges:\n        1-15:    For frequently set fields (most efficient)\n        16-2047: For less frequently set fields\n      Avoid: 19000-19999 (reserved for internal use)",
                field.line(), field.name(), fieldNumber);
    }

    private static void validateMapField(ProtobufFieldStatement field, ProtobufMapTypeReference mapType) {
        // Map fields cannot have modifiers
        ProtobufSemanticException.check(field.modifier() == null || field.modifier() == ProtobufFieldStatement.Modifier.NONE,
                "Map field \"%s\" cannot have modifier '%s'\n\nMap fields are implicitly repeated and cannot have additional modifiers.\nYou specified: %s\n\nHelp: Remove the '%s' modifier from your map field declaration.\n      Maps are already collections and don't need 'repeated'.\n      Example: map<string, int32> my_map = 1;",
                field.line(), field.name(), field.modifier().token(), field.modifier().token(), field.modifier().token());

        // Validate key type restrictions
        var keyType = mapType.keyType();
        if(keyType != null) {
            var keyProtobufType = keyType.protobufType();
            var isValidKeyType = switch (keyProtobufType) {
                case FLOAT, DOUBLE, BYTES, MESSAGE, ENUM, MAP -> false;
                default -> true;
            };

            ProtobufSemanticException.check(isValidKeyType,
                    "Map field \"%s\" has invalid key type '%s'\n\nMap keys must be integral or string types for hashing and equality comparison.\nYou used: %s\n\nHelp: Valid map key types are:\n      - Integral types: int32, int64, uint32, uint64, sint32, sint64, fixed32, fixed64, sfixed32, sfixed64, bool\n      - String types: string\n\n      Invalid key types: float, double, bytes, message types, enums, and nested maps\n      Example: map<string, MyMessage> users = 1;",
                    field.line(), field.name(), keyType.name(), keyType.name());
        }

        // Validate value type restrictions - cannot be another map
        ProtobufSemanticException.check(!(mapType.valueType() instanceof ProtobufMapTypeReference),
                "Map field \"%s\" cannot have another map as its value type\n\nNested maps (map<K, map<K2, V>>) are not supported in Protocol Buffers.\n\nHelp: To achieve a similar structure, use one of these approaches:\n      1. Create a wrapper message:\n         message InnerMap {\n           map<string, int32> values = 1;\n         }\n         map<string, InnerMap> outer_map = 1;\n\n      2. Use a different data structure:\n         - Repeated nested messages with composite keys\n         - Flattened key structure (e.g., \"outer_inner\" as a single key)",
                field.line(), field.name());
    }

    private static void validateOneofField(ProtobufFieldStatement field) {
        // Oneof fields cannot be repeated
        ProtobufSemanticException.check(field.modifier() != ProtobufFieldStatement.Modifier.REPEATED,
                "Oneof field \"%s\" cannot be repeated\n\nFields inside a 'oneof' represent mutually exclusive options and cannot be repeated.\nA oneof can only hold one value at a time.\n\nHelp: Remove the 'repeated' modifier or move this field outside the oneof.\n      If you need a repeated field, define it outside the oneof block.\n      Example:\n        oneof choice {{\n          string name = 1;      // OK\n          int32 age = 2;        // OK\n        }}\n        repeated string tags = 3;  // Repeated field outside oneof",
                field.line(), field.name());

        // Oneof fields cannot be optional
        ProtobufSemanticException.check(field.modifier() != ProtobufFieldStatement.Modifier.OPTIONAL,
                "Oneof field '%s' cannot have 'optional' modifier\n\nFields in a oneof are inherently optional - only one can be set at a time.\nThe 'optional' modifier is redundant and not allowed.\n\nHelp: Remove the 'optional' modifier from this field.\n      Example:\n        oneof choice {{\n          string name = 1;      // Already optional\n          int32 age = 2;        // Already optional\n        }}",
                field.line(), field.name());

        // Oneof fields cannot be map types
        ProtobufSemanticException.check(!(field.type() instanceof ProtobufMapTypeReference),
                "Oneof field '%s' cannot be a map type\n\nMap fields cannot be used inside oneof blocks.\n\nHelp: If you need a map-like structure in a oneof, use a wrapper message:\n      message MapWrapper {{\n        map<string, int32> values = 1;\n      }}\n      oneof choice {{\n        MapWrapper my_map = 1;\n        string other_option = 2;\n      }}",
                field.line(), field.name());
    }

    private static void validateFieldUniqueness(ProtobufMessageStatement message, ProtobufFieldStatement field) {
        var fieldName = field.name();
        var fieldNumber = field.index();
        if(fieldName == null || fieldNumber == null) {
            return;
        }

        var fieldNumberValue = fieldNumber.value();

        // If field is inside a oneof, validate uniqueness within the oneof first
        if(field.parent() instanceof ProtobufOneofFieldStatement oneof) {
            validateOneofUniqueness(oneof, field, fieldName, fieldNumberValue);
        }

        // Collect all fields in the message (including those in oneofs)
        var allFields = new ArrayList<ProtobufFieldStatement>();
        for(var child : message.children()) {
            switch (child) {
                case ProtobufFieldStatement childField when childField != field -> allFields.add(childField);
                case ProtobufOneofFieldStatement oneof ->
                    oneof.children().stream()
                            .filter(ProtobufFieldStatement.class::isInstance)
                            .map(ProtobufFieldStatement.class::cast)
                            .filter(oneofField -> oneofField != field)
                            .forEach(allFields::add);
                default -> {}
            }
        }

        // Check for duplicate field names and numbers across the entire message
        for(var otherField : allFields) {
            ProtobufSemanticException.check(!fieldName.equals(otherField.name()),
                    "Duplicate field name '%s' in message '%s'\n\nEach field in a message must have a unique name.\nField '%s' is already defined elsewhere in this message.\n\nHelp: Choose a different name for this field.\n      Field names must be unique within the same message.\n      Example: Use 'user_id' instead if 'id' is already taken.",
                    field.line(), fieldName, message.name(), fieldName);

            var otherFieldNumber = otherField.index();
            ProtobufSemanticException.check(otherFieldNumber == null || !fieldNumberValue.equals(otherFieldNumber.value()),
                    "Duplicate field number %s in message '%s'\n\nField '%s' uses field number %s, which is already used by field '%s'.\n\nHelp: Each field in a message must have a unique field number.\n      Field numbers are used to identify fields in the binary format and cannot be reused.\n      Choose a different field number for '%s'.\n      \n      Tip: Use field numbers 1-15 for frequently set fields (most efficient encoding).",
                    field.line(), fieldNumberValue, message.name(), fieldName, fieldNumberValue, otherField.name(), fieldName);
        }
    }

    private static void validateOneofUniqueness(ProtobufOneofFieldStatement oneof, ProtobufFieldStatement field, String fieldName, BigInteger fieldNumberValue) {
        oneof.children().stream()
                .filter(ProtobufFieldStatement.class::isInstance)
                .map(ProtobufFieldStatement.class::cast)
                .filter(oneofField -> oneofField != field)
                .forEach(oneofField -> {
                    ProtobufSemanticException.check(!fieldName.equals(oneofField.name()),
                            "Duplicate field name \"%s\" in oneof \"%s\"",
                            field.line(), fieldName, oneof.name());

                    var otherFieldNumber = oneofField.index();
                    ProtobufSemanticException.check(otherFieldNumber == null || !fieldNumberValue.equals(otherFieldNumber.value()),
                            "Duplicate field number %s in oneof \"%s\" (field \"%s\" conflicts with field \"%s\")",
                            field.line(), fieldNumberValue, oneof.name(), fieldName, oneofField.name());
                });
    }

    private static void validateFieldNotReserved(ProtobufMessageStatement message, ProtobufFieldStatement field) {
        var fieldName = field.name();
        var fieldNumber = field.index();
        if(fieldName == null || fieldNumber == null) {
            return;
        }

        var fieldNumberValue = fieldNumber.value();

        // Check all reserved statements in the message
        message.children().stream()
                .filter(ProtobufReservedStatement.class::isInstance)
                .map(ProtobufReservedStatement.class::cast)
                .flatMap(reserved -> reserved.expressions().stream())
                .forEach(expression -> validateNotReservedExpression(field, fieldName, fieldNumberValue, message, expression));
    }

    private static void validateNotReservedExpression(ProtobufFieldStatement field, String fieldName, BigInteger fieldNumberValue, ProtobufMessageStatement message, ProtobufExpression expression) {
        switch (expression) {
            case ProtobufLiteralExpression literalExpr when fieldName.equals(literalExpr.value()) ->
                throw new ProtobufSemanticException(
                        "Field \"%s\" uses reserved name in message \"%s\"",
                        field.line(), fieldName, message.name());
            case ProtobufNumberExpression numberExpr when numberExpr.value() instanceof ProtobufInteger(var value) && fieldNumberValue.equals(value) ->
                throw new ProtobufSemanticException(
                        "Field \"%s\" uses reserved number %s in message \"%s\"",
                        field.line(), fieldName, fieldNumberValue, message.name());
            case ProtobufIntegerRangeExpression rangeExpr when isInRange(fieldNumberValue, rangeExpr.value()) -> {
                var rangeDesc = switch (rangeExpr.value()) {
                    case ProtobufRange.Bounded(var min, var max) ->
                        "in range %s to %s".formatted(min.value(), max.value());
                    case ProtobufRange.LowerBounded(var min) ->
                        "in range %s to max".formatted(min.value());
                };
                throw new ProtobufSemanticException(
                        "Field \"%s\" uses reserved number %s (%s) in message \"%s\"",
                        field.line(), fieldName, fieldNumberValue, rangeDesc, message.name());
            }
            default -> {}
        }
    }

    private static void validateFieldNameConflicts(ProtobufMessageStatement message, ProtobufFieldStatement field) {
        var fieldName = field.name();
        if(fieldName == null) {
            return;
        }

        // Check for conflicts with nested types and enum constants
        for(var child : message.children()) {
            if(child instanceof ProtobufTree.WithName withName && child != field && fieldName.equals(withName.name())) {
                var conflictType = switch (child) {
                    case ProtobufMessageStatement ignored -> "message";
                    case ProtobufEnumStatement ignored -> "enum";
                    case ProtobufOneofFieldStatement ignored -> "oneof";
                    default -> "declaration";
                };
                throw new ProtobufSemanticException(
                        "Field name \"%s\" conflicts with %s name in message \"%s\"",
                        field.line(), fieldName, conflictType, message.name());
            }

            // Check for conflicts with enum constant names in nested enums
            if(child instanceof ProtobufEnumStatement enumStatement) {
                enumStatement.children().stream()
                        .filter(ProtobufEnumConstantStatement.class::isInstance)
                        .map(ProtobufEnumConstantStatement.class::cast)
                        .filter(constant -> fieldName.equals(constant.name()))
                        .findFirst()
                        .ifPresent(ignored -> {
                            throw new ProtobufSemanticException(
                                    "Field name \"%s\" conflicts with enum constant name in enum \"%s\" inside message \"%s\"",
                                    field.line(), fieldName, enumStatement.name(), message.name());
                        });
            }
        }
    }

    private static void validateEnumConstant(ProtobufDocumentTree document, ProtobufEnumStatement enumStatement, ProtobufEnumConstantStatement constant) {
        var syntax = document.syntax().orElse(null);
        var constantName = constant.name();
        var constantValue = constant.index();

        if(constantName == null || constantValue == null) {
            return;
        }

        var value = constantValue.value();

        // Validate enum value fits in int32 range (-2147483648 to 2147483647)
        ProtobufSemanticException.check(value.compareTo(ENUM_CONSTANT_MIN) >= 0 && value.compareTo(ENUM_CONSTANT_MAX) <= 0,
                "Enum value %s is out of valid int32 range (-2147483648 to 2147483647) in enum \"%s\"",
                constant.line(), value, enumStatement.name());

        // Collect all other enum constants in a single pass
        var allConstants = new ArrayList<ProtobufEnumConstantStatement>();
        ProtobufEnumConstantStatement firstConstant = null;
        boolean allowAlias = false;

        for(var child : enumStatement.children()) {
            switch (child) {
                case ProtobufEnumConstantStatement otherConstant -> {
                    if(otherConstant == constant) {
                        if(firstConstant == null) {
                            firstConstant = constant;
                        }
                    } else {
                        if(firstConstant == null) {
                            firstConstant = otherConstant;
                        }
                        allConstants.add(otherConstant);
                    }
                }
                case ProtobufOptionStatement option when "allow_alias".equals(option.name().toString()) -> {
                    var optionValue = option.value();
                    if(optionValue instanceof ProtobufBoolExpression boolExpr && Boolean.TRUE.equals(boolExpr.value())) {
                        allowAlias = true;
                    }
                }
                default -> {}
            }
        }

        // Check for duplicate names
        allConstants.stream()
                .filter(otherConstant -> constantName.equals(otherConstant.name()))
                .findFirst()
                .ifPresent(ignored -> {
                    throw new ProtobufSemanticException(
                            "Duplicate enum constant name \"%s\" in enum \"%s\"",
                            constant.line(), constantName, enumStatement.name());
                });

        // Check for duplicate values (unless allow_alias is true)
        if(!allowAlias) {
            var constantValueInt = constantValue.value();
            allConstants.stream()
                    .filter(otherConstant -> otherConstant.index() != null && constantValueInt.equals(otherConstant.index().value()))
                    .findFirst()
                    .ifPresent(otherConstant -> {
                        throw new ProtobufSemanticException(
                                "Duplicate enum value %s in enum '%s'\n\nEnum constant '%s' has value %s, which is already used by '%s'.\n\nHelp: By default, each enum constant must have a unique value.\n      If you intentionally want multiple constants with the same value (aliases),\n      add this option to your enum:\n      \n      enum %s {{\n        option allow_alias = true;\n        %s = %s;\n        %s = %s;  // Alias for %s\n      }}\n\n      Note: Aliases can be useful for renaming constants while maintaining backward compatibility.",
                                constant.line(), constantValueInt, enumStatement.name(), constantName, constantValueInt, otherConstant.name(),
                                enumStatement.name(), otherConstant.name(), constantValueInt, constantName, constantValueInt, otherConstant.name());
                    });
        }

        // Proto3: first enum value must be 0
        if(syntax == ProtobufVersion.PROTOBUF_3 && firstConstant == constant) {
            ProtobufSemanticException.check(constantValue.value().compareTo(BigInteger.ZERO) == 0,
                    "First enum value in proto3 must be 0\n\nEnum '%s' has first value %s, but proto3 requires the first enum value to be 0.\nThis ensures a default value exists.\n\nHelp: Change the first enum constant's value to 0.\n      Example:\n      enum %s {{\n        UNKNOWN = 0;  // First value must be 0 in proto3\n        %s = 1;\n        // ... other values\n      }}\n\n      Tip: The zero value is used as the default when a field isn't set.\n           Consider naming it UNSPECIFIED or UNKNOWN to indicate this.",
                    constant.line(), enumStatement.name(), constantValue.value(), enumStatement.name(), constantName);
        }

        // Check reserved names and numbers
        validateEnumConstantNotReserved(enumStatement, constant, constantName, constantValue.value());
    }

    private static void validateEnumConstantNotReserved(ProtobufEnumStatement enumStatement, ProtobufEnumConstantStatement constant, String constantName, BigInteger constantValueInt) {
        // Check all reserved statements in the enum
        enumStatement.children().stream()
                .filter(ProtobufReservedStatement.class::isInstance)
                .map(ProtobufReservedStatement.class::cast)
                .flatMap(reserved -> reserved.expressions().stream())
                .forEach(expression -> validateEnumNotReservedExpression(constant, constantName, constantValueInt, enumStatement, expression));
    }

    private static void validateEnumNotReservedExpression(ProtobufEnumConstantStatement constant, String constantName, BigInteger constantValueInt, ProtobufEnumStatement enumStatement, ProtobufExpression expression) {
        switch (expression) {
            case ProtobufLiteralExpression literalExpr when constantName.equals(literalExpr.value()) ->
                throw new ProtobufSemanticException(
                        "Enum constant \"%s\" uses reserved name in enum \"%s\"",
                        constant.line(), constantName, enumStatement.name());
            case ProtobufNumberExpression numberExpr when numberExpr.value() instanceof ProtobufInteger(var value) && constantValueInt.equals(value) ->
                throw new ProtobufSemanticException(
                        "Enum constant \"%s\" uses reserved value %s in enum \"%s\"",
                        constant.line(), constantName, constantValueInt, enumStatement.name());
            case ProtobufIntegerRangeExpression rangeExpr when isInRange(constantValueInt, rangeExpr.value()) -> {
                var rangeDesc = switch (rangeExpr.value()) {
                    case ProtobufRange.Bounded(var min, var max) ->
                        "in range %s to %s".formatted(min.value(), max.value());
                    case ProtobufRange.LowerBounded(var min) ->
                        "in range %s to max".formatted(min.value());
                };
                throw new ProtobufSemanticException(
                        "Enum constant \"%s\" uses reserved value %s (%s) in enum \"%s\"",
                        constant.line(), constantName, constantValueInt, rangeDesc, enumStatement.name());
            }
            default -> {}
        }
    }

    // Document-level validation for global uniqueness constraints
    private static void validateDocumentLevel(ProtobufDocumentTree document) {
        validateUniqueTopLevelNames(document);
        validateMessageLevelConstraints(document);
        validateEnumConstraints(document);
        validateServiceConstraints(document);
    }

    private static void validateUniqueTopLevelNames(ProtobufDocumentTree document) {
        var messageNames = new HashSet<String>();
        var enumNames = new HashSet<String>();
        var serviceNames = new HashSet<String>();

        for (var child : document.children()) {
            switch (child) {
                case ProtobufMessageStatement message -> {
                    if (!messageNames.add(message.name())) {
                        throw new ProtobufSemanticException(
                                "Duplicate message name \"%s\"",
                                message.line(), message.name());
                    }
                }
                case ProtobufEnumStatement enumStmt -> {
                    if (!enumNames.add(enumStmt.name())) {
                        throw new ProtobufSemanticException(
                                "Duplicate enum name \"%s\"",
                                enumStmt.line(), enumStmt.name());
                    }
                }
                case ProtobufServiceStatement service -> {
                    if (!serviceNames.add(service.name())) {
                        throw new ProtobufSemanticException(
                                "Duplicate service name \"%s\"",
                                service.line(), service.name());
                    }
                }
                default -> {}
            }
        }
    }

    private static void validateMessageLevelConstraints(ProtobufDocumentTree document) {
        var queue = new LinkedList<ProtobufTree>();
        queue.add(document);

        while (!queue.isEmpty()) {
            var tree = queue.removeFirst();
            if (tree instanceof ProtobufMessageStatement message) {
                validateDuplicateOneofNames(message);
                validateExtensionRangeNonOverlapping(message);
                validateGroupFieldNumbers(message);
                queue.addAll(message.children());
            } else if (tree instanceof ProtobufTree.WithBody<?> body) {
                queue.addAll(body.children());
            }
        }
    }

    private static void validateDuplicateOneofNames(ProtobufMessageStatement message) {
        var oneofNames = new HashSet<String>();
        for (var child : message.children()) {
            if (child instanceof ProtobufOneofFieldStatement oneof) {
                if (!oneofNames.add(oneof.name())) {
                    throw new ProtobufSemanticException(
                            "Duplicate oneof name \"%s\" in message \"%s\"",
                            oneof.line(), oneof.name(), message.name());
                }
            }
        }
    }

    private static void validateExtensionRangeNonOverlapping(ProtobufMessageStatement message) {
        var extensionRanges = new ArrayList<ProtobufRange>();
        for (var child : message.children()) {
            if (child instanceof ProtobufExtensionsStatement extensions) {
                for (var expr : extensions.expressions()) {
                    if (expr instanceof ProtobufIntegerRangeExpression rangeExpr) {
                        var newRange = rangeExpr.value();
                        for (var existingRange : extensionRanges) {
                            if (rangesOverlap(existingRange, newRange)) {
                                throw new ProtobufSemanticException(
                                        "Extension ranges overlap in message \"%s\"",
                                        extensions.line(), message.name());
                            }
                        }
                        extensionRanges.add(newRange);
                    }
                }
            }
        }
    }

    private static void validateGroupFieldNumbers(ProtobufMessageStatement message) {
        // Collect reserved field numbers
        var reservedNumbers = new HashSet<BigInteger>();
        for (var child : message.children()) {
            if (child instanceof ProtobufReservedStatement reserved) {
                for (var expr : reserved.expressions()) {
                    switch (expr) {
                        case ProtobufNumberExpression numExpr when numExpr.value() instanceof ProtobufInteger(var value) ->
                            reservedNumbers.add(value);
                        case ProtobufIntegerRangeExpression rangeExpr -> {
                            var range = rangeExpr.value();
                            var start = switch (range) {
                                case ProtobufRange.Bounded(var min, var max) -> min.value();
                                case ProtobufRange.LowerBounded(var min) -> min.value();
                            };
                            var end = switch (range) {
                                case ProtobufRange.Bounded(var min, var max) -> max.value();
                                case ProtobufRange.LowerBounded(var min) -> FIELD_NUMBER_MAX;
                            };
                            for (var i = start; i.compareTo(end) <= 0; i = i.add(BigInteger.ONE)) {
                                reservedNumbers.add(i);
                                if (reservedNumbers.size() > 100000) break; // Prevent infinite loop
                            }
                        }
                        default -> {}
                    }
                }
            }
        }

        // Check if any group uses a reserved field number and validate group name capitalization
        for (var child : message.children()) {
            if (child instanceof ProtobufGroupFieldStatement group) {
                // Group names must start with a capital letter
                var groupName = group.name();
                if (groupName != null && !groupName.isEmpty() && !Character.isUpperCase(groupName.charAt(0))) {
                    throw new ProtobufSemanticException(
                            "Group name \"%s\" must start with a capital letter",
                            group.line(), groupName);
                }

                var fieldNumber = group.index().value();
                if (reservedNumbers.contains(fieldNumber)) {
                    throw new ProtobufSemanticException(
                            "Group \"%s\" uses reserved field number %s",
                            group.line(), group.name(), fieldNumber);
                }
            }
        }
    }

    private static void validateEnumConstraints(ProtobufDocumentTree document) {
        var queue = new LinkedList<ProtobufTree>();
        queue.add(document);

        while (!queue.isEmpty()) {
            var tree = queue.removeFirst();
            if (tree instanceof ProtobufEnumStatement enumStmt) {
                validateEnumNotEmpty(enumStmt);
            } else if (tree instanceof ProtobufTree.WithBody<?> body) {
                queue.addAll(body.children());
            }
        }
    }

    private static void validateEnumNotEmpty(ProtobufEnumStatement enumStmt) {
        var hasConstants = enumStmt.children().stream()
                .anyMatch(child -> child instanceof ProtobufEnumConstantStatement);
        if (!hasConstants) {
            throw new ProtobufSemanticException(
                    "Enum \"%s\" must have at least one value",
                    enumStmt.line(), enumStmt.name());
        }
    }

    private static void validateServiceConstraints(ProtobufDocumentTree document) {
        for (var child : document.children()) {
            if (child instanceof ProtobufServiceStatement service) {
                validateServiceMethodNames(service);
            }
        }
    }

    private static void validateServiceMethodNames(ProtobufServiceStatement service) {
        var methodNames = new HashSet<String>();
        for (var child : service.children()) {
            if (child instanceof ProtobufMethodStatement method) {
                if (!methodNames.add(method.name())) {
                    throw new ProtobufSemanticException(
                            "Duplicate method name \"%s\" in service \"%s\"",
                            method.line(), method.name(), service.name());
                }
            }
        }
    }
}
