package it.auties.protobuf.parser;

import it.auties.protobuf.annotation.ProtobufEnum;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufVersion;
import it.auties.protobuf.parser.exception.ProtobufParserException;
import it.auties.protobuf.parser.exception.ProtobufSemanticException;
import it.auties.protobuf.parser.exception.ProtobufSyntaxException;
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
 *       converting {@link ProtobufUnresolvedTypeReference} to concrete type references</li>
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

    private static final BigInteger ENUM_CONSTANT_MIN = BigInteger.valueOf(ProtobufEnum.Constant.MIN_INDEX);
    private static final BigInteger ENUM_CONSTANT_MAX = BigInteger.valueOf(ProtobufEnum.Constant.MAX_INDEX);

    private static final BigInteger RESERVED_RANGE_MIN = BigInteger.valueOf(19_000);
    private static final BigInteger RESERVED_RANGE_MAX = BigInteger.valueOf(19_999);

    private static final BigInteger INT32_MIN = BigInteger.valueOf(Integer.MIN_VALUE);
    private static final BigInteger INT32_MAX = BigInteger.valueOf(Integer.MAX_VALUE);

    private static final BigInteger UINT32_MIN = BigInteger.ZERO;
    private static final BigInteger UINT32_MAX = BigInteger.valueOf(4294967295L);

    private static final BigInteger INT64_MIN = BigInteger.valueOf(Long.MIN_VALUE);
    private static final BigInteger INT64_MAX = BigInteger.valueOf(Long.MAX_VALUE);

    private static final BigInteger UINT64_MIN = BigInteger.ZERO;
    private static final BigInteger UINT64_MAX = new BigInteger("18446744073709551615");

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
            for (var document : BUILT_IN_TYPES) {
                canonicalPathToDocumentMap.put(document.qualifiedPath(), document);
            }
        }

        // Attribute imports for all documents
        for (var document : documents) {
            attributeImports(document, canonicalPathToDocumentMap);
        }

        // Attribute and validate each document
        for (var document : documents) {
            attributeDocument(document);
        }
    }

    private static Map<String, ProtobufDocumentTree> buildImportsMap(Collection<ProtobufDocumentTree> documents) {
        var queue = new LinkedList<>(documents);
        var result = new HashMap<String, ProtobufDocumentTree>();
        while(!queue.isEmpty()) {
            var document = queue.poll();
            if (result.put(document.qualifiedPath(), document) == null) {
                document.getDirectChildrenByType(ProtobufImportStatement.class)
                        .filter(ProtobufImportStatement::hasDocument)
                        .forEachOrdered(importStatement -> queue.add(importStatement.document()));
            }
        }
        return result;
    }

    private static void attributeImports(ProtobufDocumentTree document, Map<String, ProtobufDocumentTree> canonicalPathToDocumentMap) {
        document.getDirectChildrenByType(ProtobufImportStatement.class)
                .filter(importStatement -> !importStatement.isAttributed())
                .forEachOrdered(importStatement -> {
                    var imported = canonicalPathToDocumentMap.get(importStatement.location());
                    ProtobufSemanticException.check(imported != null,
                            "Cannot resolve import '%s'\n\nThe imported file could not be found or loaded.",
                            importStatement.line(), importStatement.location());
                    importStatement.setDocument(imported);
                });
    }

    private static void attributeDocument(ProtobufDocumentTree document) {
        var names = new HashSet<String>();
        var queue = new LinkedList<ProtobufTree>();
        queue.add(document);
        while (!queue.isEmpty()) {
            var tree = queue.removeFirst();

            if(tree instanceof ProtobufTree.WithName withName) {
                ProtobufSyntaxException.check(names.add(withName.name()),
                        "Duplicate type name \"%s\"",
                        withName.line(),
                        withName.name());
            }

            if(tree instanceof ProtobufTree.WithBody<?> withBody) {
                queue.addAll(withBody.children());
            }

            switch (tree) {
                case ProtobufEmptyStatement _ -> {
                    // Nothing to do
                }

                case ProtobufExtendStatement extendStatement -> {
                    validateExtendDeclaration(document, extendStatement);
                }

                case ProtobufExtensionsStatement protobufExtension -> {
                    validateExtensions(document, protobufExtension);
                }

                case ProtobufFieldStatement protobufField -> {
                    attributeType(document, protobufField);
                    validateField(document, protobufField);
                    for (var option : protobufField.options()) {
                        attributeFieldOption(document, protobufField, option);
                    }
                }

                case ProtobufOptionStatement protobufOption -> {
                    validateOption(protobufOption);
                }

                case ProtobufPackageStatement _ -> {
                    // Nothing to do
                }

                case ProtobufReservedStatement protobufReserved -> {
                    validateReserved(protobufReserved);
                }

                case ProtobufEnumStatement enumStatement -> {
                    validateEnum(enumStatement);
                }

                case ProtobufImportStatement _ -> {
                    // Nothing to do
                }

                case ProtobufMessageStatement messageStatement -> {

                }

                case ProtobufMethodStatement methodStatement -> {

                }

                case ProtobufServiceStatement serviceStatement -> {
                    validateService(document, serviceStatement);
                }

                case ProtobufSyntaxStatement _ -> {

                }

                case ProtobufDocumentTree protobufDocumentTree -> {

                }

                case ProtobufExpression protobufExpression -> {

                }
            }
        }
    }

    private static void validateExtendDeclaration(ProtobufDocumentTree document, ProtobufExtendStatement extendStatement) {
        // Resolve the declaration type
        var reference = extendStatement.declaration();
        if (reference instanceof ProtobufUnresolvedTypeReference(var name)) {
            var resolvedType = tryResolveType(document, name, extendStatement);
            ProtobufSemanticException.check(resolvedType != null,
                    """
                            Cannot resolve extended type '%s'
                            
                            The message type being extended could not be found.
                            
                            Help: Make sure:
                                  1. The message is defined in this file or imported
                                  2. The message name is spelled correctly
                                  3. If the message is in another package, use the fully qualified name
                                     Example:
                                           extend com.example.MyMessage { ... }""",
                    extendStatement.line(), name);
            extendStatement.setDeclaration(resolvedType);
            reference = resolvedType;
        }

        var declaration = switch (reference) {
            case ProtobufMessageTypeReference(var messageDeclaration) -> messageDeclaration;
            case ProtobufGroupTypeReference(var groupDeclaration) -> groupDeclaration;
            default -> throw new ProtobufSemanticException("""
                            Extended type '%s' is not a message or a group.
                         
                            Help: Only messages and groups can be extended.""",
                    extendStatement.line(), reference.name());
        };

        var ranges = buildExtensionsLookup(declaration);
        for(var statement : extendStatement.children()) {
            switch (statement) {
                case ProtobufFieldStatement field -> {
                    // Extension fields cannot be required
                    ProtobufSemanticException.check(field.modifier() != ProtobufFieldStatement.Modifier.REQUIRED,
                            "Extension field '%s' cannot be required\n\nExtension fields cannot use the 'required' modifier.\nThis ensures backward compatibility with code that doesn't know about the extension.\n\nHelp: Remove the 'required' modifier or use 'optional' instead:\n      extend MyMessage {{\n        optional string %s = 100;  // OK\n        string %s = 100;           // Also OK (implicitly optional)\n      }}",
                            field.line(), field.name(), field.name(), field.name());

                    // Extension fields cannot be map types
                    ProtobufSemanticException.check(!(field.type() instanceof ProtobufMapTypeReference),
                            "Extension field '%s' cannot be a map type\n\nMap fields are not allowed in extensions.\n\nHelp: If you need a map-like structure, use a repeated message with key-value pairs:\n      message KeyValue {{\n        string key = 1;\n        ValueType value = 2;\n      }}\n      extend MyMessage {{\n        repeated KeyValue %s = 100;\n      }}",
                            field.line(), field.name(), field.name());

                    // Validate field number is within declared extension ranges
                    var entry = ranges.floorEntry(field.index());
                    var extensible = entry != null && field.index().compareTo(entry.getValue()) <= 0;
                    ProtobufSemanticException.check(extensible,
                            "Extension field '%s' with number %s is outside declared extension ranges\n\nThe field number must be within one of the extension ranges declared in message '%s'.\n\nHelp: Choose a field number within the declared extension ranges,\n      or update the message to include a range that covers %s:\n      \n      message %s {{\n        extensions 100 to 199;  // Declared ranges\n      }}",
                            field.line(), field.name(), field.index(), declaration.name(), field.index(), declaration.name());
                }
            }
        }
    }

    private static TreeMap<ProtobufInteger, ProtobufInteger> buildExtensionsLookup(ProtobufTree.WithBody<?> declaration) {
        var ranges = new TreeMap<ProtobufInteger, ProtobufInteger>();
        declaration.getDirectChildrenByType(ProtobufExtensionsStatement.class).forEachOrdered(extensionsStatement -> {
            for(var expression : extensionsStatement.expressions()) {
                switch (expression) {
                    case ProtobufIntegerExpression integerExpression -> ranges.put(integerExpression.value(), integerExpression.value());
                    case ProtobufIntegerRangeExpression rangeExpression -> {
                        switch (rangeExpression.value()) {
                            case ProtobufRange.Bounded bounded -> ranges.put(bounded.min(), bounded.max());
                            case ProtobufRange.LowerBounded lowerBounded -> ranges.put(lowerBounded.min(), new ProtobufInteger(FIELD_NUMBER_MAX));
                        }
                    }
                }
            }
        });
        return ranges;
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

    @SuppressWarnings("SameParameterValue")
    private static ProtobufTree.WithBodyAndName<?> resolveNestedTypes(ProtobufTree.WithBodyAndName<?> current, String[] types, int startIndex) {
        for (var i = startIndex; i < types.length && current != null; i++) {
            current = current.getDirectChildByNameAndType(types[i], ProtobufTree.WithBodyAndName.class)
                    .orElse(null);
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
        // Proto3: Default values are NOT allowed
        var syntax = document.syntax()
                .orElse(ProtobufVersion.defaultVersion());
        ProtobufSemanticException.check(syntax != ProtobufVersion.PROTOBUF_3,
                "Default values are not allowed in proto3", option.line());

        // Proto2: Cannot be used on repeated fields
        ProtobufSemanticException.check(protobufField.modifier() != ProtobufFieldStatement.Modifier.REPEATED,
                "Default values cannot be used on repeated fields", option.line());

        var fieldType = protobufField.type().protobufType();
        var value = option.value();

        switch (fieldType) {
            case UNKNOWN -> {
                // Do not validate unknown types: it's a syntax issue
            }

            case MESSAGE -> {
                throw new ProtobufSemanticException("Default values cannot be used on message-typed fields", option.line());
            }

            case ENUM -> {
                ProtobufSemanticException.check(value instanceof ProtobufEnumConstantExpression,
                        "Default value type mismatch for field \"%s\": expected enum constant",
                        option.line(), protobufField.name());
                var enumExpr = (ProtobufEnumConstantExpression) value;
                switch (protobufField.type()) {
                    case ProtobufUnresolvedTypeReference _ ->
                            throw new ProtobufSemanticException("Default value type error for field \"%s\": unresolved type", option.line(), protobufField.name());
                    case ProtobufMessageTypeReference _ ->
                            throw new ProtobufSemanticException("Default value type error for field \"%s\": expected enum type, got message type", option.line(), protobufField.name());
                    case ProtobufGroupTypeReference _ ->
                            throw new ProtobufSemanticException("Default value type error for field \"%s\": expected enum type, got group type", option.line(), protobufField.name());
                    case ProtobufMapTypeReference _ ->
                            throw new InternalError("Should not be possible to reference a map type here");
                    case ProtobufPrimitiveTypeReference _ ->
                            throw new ProtobufSemanticException("Default value type error for field \"%s\": expected enum type, got primitive type", option.line(), protobufField.name());
                    case ProtobufEnumTypeReference(var declaration) -> {
                        var constant = declaration.getDirectChildByNameAndType(enumExpr.name(), ProtobufEnumConstantStatement.class);
                        ProtobufSemanticException.check(constant.isPresent(),
                                "Default value references non-existent enum constant \"%s\" in enum \"%s\"",
                                option.line(), enumExpr.name(), declaration.name());
                    }
                }
            }

            case GROUP -> {
                throw new ProtobufSemanticException("Default values cannot be used on group-typed fields", option.line());
            }

            case MAP -> {
                throw new ProtobufSemanticException("Default values cannot be used on map-typed fields", option.line());
            }

            case FLOAT, DOUBLE -> {
                // Accepts any numeric value: integers, finite floats, inf, -inf, nan
                ProtobufSemanticException.check(value instanceof ProtobufNumberExpression,
                        "Default value type mismatch for field \"%s\": expected numeric value",
                        option.line(), protobufField.name());
            }

            case BOOL -> {
                ProtobufSemanticException.check(value instanceof ProtobufBoolExpression,
                        "Default value type mismatch for field \"%s\": expected boolean value (true or false)",
                        option.line(), protobufField.name());
            }

            case STRING -> {
                ProtobufSemanticException.check(value instanceof ProtobufLiteralExpression,
                        "Default value type mismatch for field \"%s\": expected string value",
                        option.line(), protobufField.name());
            }

            case BYTES -> {
                ProtobufSemanticException.check(value instanceof ProtobufLiteralExpression,
                        "Default value type mismatch for field \"%s\": expected bytes value",
                        option.line(), protobufField.name());
            }

            case INT32, SINT32, SFIXED32 -> {
                ProtobufSemanticException.check(value instanceof ProtobufIntegerExpression,
                        "Default value type mismatch for field \"%s\": expected integer value",
                        option.line(), protobufField.name());
                var intValue = ((ProtobufIntegerExpression) value).value().value();
                ProtobufSemanticException.check(intValue.compareTo(INT32_MIN) >= 0 && intValue.compareTo(INT32_MAX) <= 0,
                        "Default value %s for field \"%s\" is out of range for %s (valid range: %s to %s)",
                        option.line(), intValue, protobufField.name(), fieldType, INT32_MIN, INT32_MAX);
            }

            case UINT32, FIXED32 -> {
                ProtobufSemanticException.check(value instanceof ProtobufIntegerExpression,
                        "Default value type mismatch for field \"%s\": expected integer value",
                        option.line(), protobufField.name());
                var intValue = ((ProtobufIntegerExpression) value).value().value();
                ProtobufSemanticException.check(intValue.compareTo(UINT32_MIN) >= 0 && intValue.compareTo(UINT32_MAX) <= 0,
                        "Default value %s for field \"%s\" is out of range for %s (valid range: %s to %s)",
                        option.line(), intValue, protobufField.name(), fieldType, UINT32_MIN, UINT32_MAX);
            }

            case INT64, SINT64, SFIXED64 -> {
                ProtobufSemanticException.check(value instanceof ProtobufIntegerExpression,
                        "Default value type mismatch for field \"%s\": expected integer value",
                        option.line(), protobufField.name());
                var intValue = ((ProtobufIntegerExpression) value).value().value();
                ProtobufSemanticException.check(intValue.compareTo(INT64_MIN) >= 0 && intValue.compareTo(INT64_MAX) <= 0,
                        "Default value %s for field \"%s\" is out of range for %s (valid range: %s to %s)",
                        option.line(), intValue, protobufField.name(), fieldType, INT64_MIN, INT64_MAX);
            }

            case UINT64, FIXED64 -> {
                ProtobufSemanticException.check(value instanceof ProtobufIntegerExpression,
                        "Default value type mismatch for field \"%s\": expected integer value",
                        option.line(), protobufField.name());
                var intValue = ((ProtobufIntegerExpression) value).value().value();
                ProtobufSemanticException.check(intValue.compareTo(UINT64_MIN) >= 0 && intValue.compareTo(UINT64_MAX) <= 0,
                        "Default value %s for field \"%s\" is out of range for %s (valid range: %s to %s)",
                        option.line(), intValue, protobufField.name(), fieldType, UINT64_MIN, UINT64_MAX);
            }
        }
    }

    private static void validatePackedOption(ProtobufFieldStatement protobufField, ProtobufOptionExpression option) {
        // Packed option only allowed on repeated fields
        ProtobufSemanticException.check(protobufField.modifier() == ProtobufFieldStatement.Modifier.REPEATED,
                "Packed option can only be used on repeated fields", option.line());

        // Check if the type is packable
        ProtobufSemanticException.check(protobufField.type().protobufType().isPackable(),
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
        if (definition == null && !option.name().extension()) {
            var parentName = switch (protobufField.parent()) {
                case ProtobufTree.WithName withName -> withName.name();
                case ProtobufExtendStatement extendStatement -> "extend " + extendStatement.declaration().name();
                case null, default -> "unknown context";
            };
            throw new ProtobufSemanticException("Invalid option \"%s\" for field \"%s\" inside %s",
                    option.line(), optionName, protobufField.name(), parentName);
        }
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

            if(!(typeReference instanceof ProtobufUnresolvedTypeReference(var originalName))) {
                throw throwUnattributableType(typedFieldTree);
            }

            var resolvedType = tryResolveType(document, originalName, typedFieldTree);
            if(resolvedType == null) {
                throw throwUnattributableType(typedFieldTree);
            }

            typedFieldTree.setType(resolvedType);
        }
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

    private static void attributeMapType(ProtobufDocumentTree document, ProtobufFieldStatement typedFieldTree, ProtobufMapTypeReference mapType) {
        var hasUpdate = false;
        
        var keyType = mapType.keyType();
        if(keyType instanceof ProtobufUnresolvedTypeReference(var keyName)) {
            var resolvedKeyType = tryResolveType(document, keyName, typedFieldTree);
            ProtobufSemanticException.check(resolvedKeyType != null,
                    "Cannot resolve map key type \"%s\" in field \"%s\"",
                    typedFieldTree.line(), keyName, typedFieldTree.name());
            keyType = resolvedKeyType;
            hasUpdate = true;
        }
        
        // Attribute value type
        var valueType = mapType.valueType();
        if(valueType instanceof ProtobufUnresolvedTypeReference(var valueName)) {
            var resolvedValueType = tryResolveType(document, valueName, typedFieldTree);
            ProtobufSemanticException.check(resolvedValueType != null,
                    "Cannot resolve map value type \"%s\" in field \"%s\"",
                    typedFieldTree.line(), valueName, typedFieldTree.name());
            valueType = resolvedValueType;
            hasUpdate = true;
        }
        
        if(hasUpdate) {
            var updatedType = new ProtobufMapTypeReference(keyType, valueType);
            typedFieldTree.setType(updatedType);
        }
    }

    private static List<ProtobufDocumentTree> collectVisibleImports(ProtobufDocumentTree document) {
        var result = new ArrayList<ProtobufDocumentTree>();
        var visited = new HashSet<ProtobufDocumentTree>();

        record Entry(ProtobufDocumentTree document, boolean includeNonPublic) { }
        var stack = new ArrayDeque<Entry>();
        stack.push(new Entry(document, true));

        while (!stack.isEmpty()) {
            var entry = stack.pop();
            if (visited.add(entry.document())) {
                entry.document().getDirectChildrenByType(ProtobufImportStatement.class)
                        .filter(ProtobufImportStatement::hasDocument)
                        .forEachOrdered(importStatement -> {
                            var imported = importStatement.document();
                            var isPublic = importStatement.modifier() == ProtobufImportStatement.Modifier.PUBLIC;

                            if (entry.includeNonPublic() || isPublic) {
                                result.add(imported);
                            }

                            stack.push(new Entry(imported, false));
                        });
            }
        }

        // Always include built-in types (google.protobuf.*)
        if (BUILT_IN_TYPES != null) {
            result.addAll(BUILT_IN_TYPES);
        }

        return result;
    }

    private static void validateExtensions(ProtobufDocumentTree document, ProtobufExtensionsStatement extensionsStatement) {
        // Proto3: extensions only allowed for options
        var syntax = document.syntax()
                .orElse(ProtobufVersion.defaultVersion());
        if(syntax == ProtobufVersion.PROTOBUF_3) {
            throw new ProtobufSemanticException("""
                    Extensions are not allowed in proto3, except for custom options.
                    
                    Help: In proto3, if you need to extend the protocol, use the 'Any' type:
                          import "google/protobuf/any.proto";
                    
                          message MyMessage {
                              google.protobuf.Any extra_data = 1;
                          }
                    
                    Note: Proto2 extensions are generally discouraged in favor of proto3's simpler model.""",
                    extensionsStatement.line());
        }
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

    private static void validateOptionValueType(ProtobufOptionStatement option, ProtobufFieldStatement optionDefinition) {
        var value = option.value();
        var type = optionDefinition.type().protobufType();
        switch (type) {
            case UNKNOWN -> {
                // Do not validate unknown types: it's a syntax issue
            }

            case MESSAGE -> {
                ProtobufSemanticException.check(value instanceof ProtobufMessageValueExpression,
                        "Option value type mismatch for field \"%s\": expected message value",
                        option.line(), optionDefinition.name());

            }

            case ENUM -> {
                ProtobufSemanticException.check(value instanceof ProtobufEnumConstantExpression,
                        "Option value type mismatch for field \"%s\": expected enum constant",
                        option.line(), optionDefinition.name());
                var enumExpr = (ProtobufEnumConstantExpression) value;
                switch (optionDefinition.type()) {
                    case ProtobufUnresolvedTypeReference _ ->
                            throw new ProtobufSemanticException("Option value type error for field \"%s\": unresolved type", option.line(), optionDefinition.name());
                    case ProtobufMessageTypeReference _ ->
                            throw new ProtobufSemanticException("Option value type error for field \"%s\": expected enum type, got message type", option.line(), optionDefinition.name());
                    case ProtobufGroupTypeReference _ ->
                            throw new ProtobufSemanticException("Option value type error for field \"%s\": expected enum type, got group type", option.line(), optionDefinition.name());
                    case ProtobufMapTypeReference _ ->
                            throw new InternalError("Should not be possible to reference a map type here");
                    case ProtobufPrimitiveTypeReference _ ->
                            throw new ProtobufSemanticException("Option value type error for field \"%s\": expected enum type, got primitive type", option.line(), optionDefinition.name());
                    case ProtobufEnumTypeReference(var declaration) -> {
                        var constant = declaration.getDirectChildByNameAndType(enumExpr.name(), ProtobufEnumConstantStatement.class);
                        ProtobufSemanticException.check(constant.isPresent(),
                                "Option value references non-existent enum constant \"%s\" in enum \"%s\"",
                                option.line(), enumExpr.name(), declaration.name());
                    }
                }
            }

            case GROUP -> {
                ProtobufSemanticException.check(value instanceof ProtobufGroupValueExpression,
                        "Option value type mismatch for field \"%s\": expected group value",
                        option.line(), optionDefinition.name());

            }

            case MAP -> {
                throw new ProtobufSemanticException("Default values cannot be used on map-typed fields", option.line());
            }

            case FLOAT, DOUBLE -> {
                // Accepts any numeric value: integers, finite floats, inf, -inf, nan
                ProtobufSemanticException.check(value instanceof ProtobufNumberExpression,
                        "Option value type mismatch for field \"%s\": expected numeric value",
                        option.line(), optionDefinition.name());
            }

            case BOOL -> {
                ProtobufSemanticException.check(value instanceof ProtobufBoolExpression,
                        "Option value type mismatch for field \"%s\": expected boolean value (true or false)",
                        option.line(), optionDefinition.name());
            }

            case STRING -> {
                ProtobufSemanticException.check(value instanceof ProtobufLiteralExpression,
                        "Option value type mismatch for field \"%s\": expected string value",
                        option.line(), optionDefinition.name());
            }

            case BYTES -> {
                ProtobufSemanticException.check(value instanceof ProtobufLiteralExpression,
                        "Option value type mismatch for field \"%s\": expected bytes value",
                        option.line(), optionDefinition.name());
            }

            case INT32, SINT32, SFIXED32 -> {
                ProtobufSemanticException.check(value instanceof ProtobufIntegerExpression,
                        "Option value type mismatch for field \"%s\": expected integer value",
                        option.line(), optionDefinition.name());
                var intValue = ((ProtobufIntegerExpression) value).value().value();
                ProtobufSemanticException.check(intValue.compareTo(INT32_MIN) >= 0 && intValue.compareTo(INT32_MAX) <= 0,
                        "Option value %s for field \"%s\" is out of range for %s (valid range: %s to %s)",
                        option.line(), intValue, optionDefinition.name(), type, INT32_MIN, INT32_MAX);
            }

            case UINT32, FIXED32 -> {
                ProtobufSemanticException.check(value instanceof ProtobufIntegerExpression,
                        "Option value type mismatch for field \"%s\": expected integer value",
                        option.line(), optionDefinition.name());
                var intValue = ((ProtobufIntegerExpression) value).value().value();
                ProtobufSemanticException.check(intValue.compareTo(UINT32_MIN) >= 0 && intValue.compareTo(UINT32_MAX) <= 0,
                        "Option value %s for field \"%s\" is out of range for %s (valid range: %s to %s)",
                        option.line(), intValue, optionDefinition.name(), type, UINT32_MIN, UINT32_MAX);
            }

            case INT64, SINT64, SFIXED64 -> {
                ProtobufSemanticException.check(value instanceof ProtobufIntegerExpression,
                        "Option value type mismatch for field \"%s\": expected integer value",
                        option.line(), optionDefinition.name());
                var intValue = ((ProtobufIntegerExpression) value).value().value();
                ProtobufSemanticException.check(intValue.compareTo(INT64_MIN) >= 0 && intValue.compareTo(INT64_MAX) <= 0,
                        "Option value %s for field \"%s\" is out of range for %s (valid range: %s to %s)",
                        option.line(), intValue, optionDefinition.name(), type, INT64_MIN, INT64_MAX);
            }

            case UINT64, FIXED64 -> {
                ProtobufSemanticException.check(value instanceof ProtobufIntegerExpression,
                        "Option value type mismatch for field \"%s\": expected integer value",
                        option.line(), optionDefinition.name());
                var intValue = ((ProtobufIntegerExpression) value).value().value();
                ProtobufSemanticException.check(intValue.compareTo(UINT64_MIN) >= 0 && intValue.compareTo(UINT64_MAX) <= 0,
                        "Option value %s for field \"%s\" is out of range for %s (valid range: %s to %s)",
                        option.line(), intValue, optionDefinition.name(), type, UINT64_MIN, UINT64_MAX);
            }
        }
    }

    private static void validateReserved(ProtobufReservedStatement reservedStatement) {
        var expressions = reservedStatement.expressions();
        if(expressions.isEmpty()) {
            return;
        }

        var ranges = new TreeMap<BigInteger, BigInteger>();
        var names = new HashSet<String>();

        var minAllowedIndex = reservedStatement.parent() instanceof ProtobufEnumStatement
                ? ENUM_CONSTANT_MIN
                : FIELD_NUMBER_MIN;
        var maxAllowedIndex = reservedStatement.parent() instanceof ProtobufEnumStatement
                ? ENUM_CONSTANT_MAX
                : FIELD_NUMBER_MAX;

        for(var expression : expressions) {
            switch (expression) {
                case ProtobufLiteralExpression literalExpression -> {
                    if(!names.add(literalExpression.value())) {
                        throw new ProtobufSyntaxException("Duplicate reserved field name '%s'", literalExpression.line(), literalExpression.value());
                    }
                }

                case ProtobufIntegerExpression integerExpression -> {
                    var value = integerExpression.value().value();

                    ProtobufSemanticException.check(value.compareTo(minAllowedIndex) > 0,
                            "Reserved number %s is invalid: must be at least %s", reservedStatement.line(), value, minAllowedIndex);

                    ProtobufSemanticException.check(value.compareTo(maxAllowedIndex) <= 0,
                            "Reserved number %s is invalid: must be at most %s", reservedStatement.line(), value, maxAllowedIndex);

                    // Check if value falls within an existing range
                    var floor = ranges.floorEntry(value);
                    ProtobufSemanticException.check(floor == null || value.compareTo(floor.getValue()) > 0,
                            "Reserved ranges overlap", reservedStatement.line());

                    ranges.put(value, value);
                }

                case ProtobufIntegerRangeExpression rangeExpr -> {
                    var range = rangeExpr.value();
                    switch (range) {
                        case ProtobufRange.Bounded(ProtobufInteger(var min), ProtobufInteger(var max)) -> {
                            ProtobufSemanticException.check(min.compareTo(minAllowedIndex) > 0,
                                    "Reserved number %s is invalid: must be at least %s", reservedStatement.line(), min, minAllowedIndex);

                            ProtobufSemanticException.check(max.compareTo(maxAllowedIndex) <= 0,
                                    "Reserved number %s is invalid: must be at most %s", reservedStatement.line(), max, maxAllowedIndex);

                            ProtobufSemanticException.check(min.compareTo(max) <= 0,
                                    "Invalid reserved range %s to %s: start must be <= end",
                                    reservedStatement.line(), min, max);

                            // Check overlap with range starting before min
                            var floor = ranges.floorEntry(min);
                            ProtobufSemanticException.check(floor == null || min.compareTo(floor.getValue()) > 0,
                                    "Reserved ranges overlap", reservedStatement.line());

                            // Check overlap with range starting within [min, max]
                            var ceiling = ranges.ceilingEntry(min);
                            ProtobufSemanticException.check(ceiling == null || ceiling.getKey().compareTo(max) > 0,
                                    "Reserved ranges overlap", reservedStatement.line());

                            ranges.put(min, max);
                        }

                        case ProtobufRange.LowerBounded(ProtobufInteger(var min)) -> {
                            ProtobufSemanticException.check(min.compareTo(minAllowedIndex) > 0,
                                    "Reserved number %s is invalid: must be at least %s", reservedStatement.line(), min, minAllowedIndex);

                            // Check overlap with range starting before min
                            var floor = ranges.floorEntry(min);
                            ProtobufSemanticException.check(floor == null || min.compareTo(floor.getValue()) > 0,
                                    "Reserved ranges overlap", reservedStatement.line());

                            // Any range starting at or after min would overlap with [min, max]
                            var ceiling = ranges.ceilingEntry(min);
                            ProtobufSemanticException.check(ceiling == null,
                                    "Reserved ranges overlap", reservedStatement.line());

                            ranges.put(min, maxAllowedIndex);
                        }
                    }
                }

                default -> {}
            }
        }

        ProtobufSemanticException.check(ranges.isEmpty() || names.isEmpty(),
                "Cannot mix reserved numbers and names in the same statement", reservedStatement.line());
    }

    private static void validateField(ProtobufDocumentTree document, ProtobufFieldStatement field) {
        // Check field specific data
        switch (field) {
            case ProtobufGroupFieldStatement groupField -> validateGroupField(document, groupField);
            case ProtobufOneofFieldStatement oneofField -> validateOneofField(oneofField);
            default -> {}
        }

        validateFieldModifiers(document, field);

        validateFieldType(field);

        validateFieldIndex(field);
    }

    private static void validateFieldModifiers(ProtobufDocumentTree document, ProtobufFieldStatement field) {
        // Proto3: "required" modifier not allowed
        var syntax = document.syntax()
                .orElse(ProtobufVersion.defaultVersion());
        ProtobufSemanticException.check(syntax != ProtobufVersion.PROTOBUF_3 || field.modifier() != ProtobufFieldStatement.Modifier.REQUIRED,
                "Field '%s' cannot use 'required' modifier in proto3\n\nProto3 simplified field presence and removed the 'required' modifier.\nAll singular fields in proto3 are optional by default.\n\nHelp: In proto3:\n      - Remove the 'required' keyword\n      - Fields are optional by default and return default values when not set\n      - Use 'optional' keyword if you need explicit presence detection\n      \n      Example:\n        Proto2: required string name = 1;\n        Proto3: string name = 1;  // Already optional\n        Proto3: optional string name = 1;  // Explicit presence tracking",
                field.line(), field.name());
    }

    private static void validateFieldType(ProtobufFieldStatement field) {
        switch (field.type()) {
            case ProtobufMapTypeReference mapType -> validateMapField(field, mapType);
            default -> {}
        }
    }

    private static void validateMapField(ProtobufFieldStatement field, ProtobufMapTypeReference mapType) {
        // Map fields cannot have modifiers
        ProtobufSemanticException.check(field.modifier() == ProtobufFieldStatement.Modifier.NONE,
                "Map field \"%s\" cannot have modifier '%s'\n\nMap fields are implicitly repeated and cannot have additional modifiers.\nYou specified: %s\n\nHelp: Remove the '%s' modifier from your map field declaration.\n      Maps are already collections and don't need 'repeated'.\n      Example: map<string, int32> my_map = 1;",
                field.line(), field.name(), field.modifier().token(), field.modifier().token(), field.modifier().token());

        // Validate key type restrictions
        var keyType = mapType.keyType();
        if(keyType != null) {
            var keyProtobufType = keyType.protobufType();
            ProtobufSemanticException.check(keyProtobufType.isValidMapKeyType(),
                    "Map field \"%s\" has invalid key type '%s'\n\nMap keys must be integral or string types for hashing and equality comparison.\nYou used: %s\n\nHelp: Valid map key types are:\n      - Integral types: int32, int64, uint32, uint64, sint32, sint64, fixed32, fixed64, sfixed32, sfixed64, bool\n      - String types: string\n\n      Invalid key types: float, double, bytes, message types, enums, and nested maps\n      Example: map<string, MyMessage> users = 1;",
                    field.line(), field.name(), keyType.name(), keyType.name());
        }

        // Validate value type restrictions - cannot be another map
        var valueType = mapType.valueType();
        if(valueType != null) {
            var valueProtobufType = valueType.protobufType();
            ProtobufSemanticException.check(valueProtobufType.isValidMapValueType(),
                    "Map field \"%s\" cannot have another map as its value type\n\nNested maps (map<K, map<K2, V>>) are not supported in Protocol Buffers.\n\nHelp: To achieve a similar structure, use one of these approaches:\n      1. Create a wrapper message:\n         message InnerMap {\n           map<string, int32> values = 1;\n         }\n         map<string, InnerMap> outer_map = 1;\n\n      2. Use a different data structure:\n         - Repeated nested messages with composite keys\n         - Flattened key structure (e.g., \"outer_inner\" as a single key)",
                    field.line(), field.name());
        }
    }

    private static void validateFieldIndex(ProtobufFieldStatement field) {
        if (!field.hasIndex()) {
            return;
        }
        var fieldNumber = field.index().value();

        var minAllowedIndex = field.parent() instanceof ProtobufEnumStatement
                ? ENUM_CONSTANT_MIN
                : FIELD_NUMBER_MIN;
        ProtobufSemanticException.check(fieldNumber.compareTo(minAllowedIndex) >= 0,
                "Field \"%s\" has invalid field number %s\n\nField numbers must be positive integers starting from 1.\nYou used: %s\n\nHelp: Field numbers are used to identify fields in the binary format and must be\n      unique within each message. Use field number 1 for your first field.\n      Example: string name = 1;",
                field.line(), field.name(), fieldNumber, fieldNumber);

        var maxAllowedIndex = field.parent() instanceof ProtobufEnumStatement
                ? ENUM_CONSTANT_MAX
                : FIELD_NUMBER_MAX;
        ProtobufSemanticException.check(fieldNumber.compareTo(maxAllowedIndex) <= 0,
                "Field \"%s\" has invalid field number %s\n\nField numbers must not exceed 536,870,911 (2^29 - 1).\nYou used: %s\n\nHelp: This is the maximum field number allowed by the Protocol Buffers specification.\n      Consider reorganizing your message or splitting it into multiple messages if you\n      need more fields.\n\n      Field number ranges:\n        1-15:          Use for frequently set fields (1 byte encoding)\n        16-2047:       Standard range (2 bytes encoding)\n        2048-536870911: Extended range (more bytes encoding)",
                field.line(), field.name(), fieldNumber, fieldNumber);

        ProtobufSemanticException.check(fieldNumber.compareTo(RESERVED_RANGE_MIN) < 0 || fieldNumber.compareTo(RESERVED_RANGE_MAX) > 0,
                "Field \"%s\" uses field number %s which is reserved\n\nThe range 19000-19999 is reserved for the Protocol Buffers implementation.\nYou cannot use field numbers in this range.\n\nHelp: Choose a different field number outside this range.\n      Recommended ranges:\n        1-15:    For frequently set fields (most efficient)\n        16-2047: For less frequently set fields\n      Avoid: 19000-19999 (reserved for internal use)",
                field.line(), field.name(), fieldNumber);
    }

    private static void validateGroupField(ProtobufDocumentTree document, ProtobufGroupFieldStatement groupField) {
        var syntax = document.syntax()
                .orElse(ProtobufVersion.defaultVersion());
        ProtobufSemanticException.check(syntax != ProtobufVersion.PROTOBUF_3,
                "Group \"%s\" is not allowed in proto3\n\nGroups are deprecated and not supported in proto3.\nThey were a legacy feature from proto2 that combined field declaration with message definition.\n\nHelp: Use a nested message type instead:\n      message %s {\n        message %s {\n          // fields here\n        }\n        %s field_name = %s;\n      }",
                groupField.line(),
                groupField.name(), groupField.parent() instanceof ProtobufTree.WithName parentWithName ? parentWithName.name() : "unknown", groupField.name(), groupField.name(), groupField.index().value().toString());
    }

    private static void validateOneofField(ProtobufOneofFieldStatement field) {
        // Oneof must contain at least one field
        var child = field.getDirectChildByType(ProtobufFieldStatement.class);
        ProtobufSemanticException.check(child.isPresent(),
                "Oneof \"%s\" must contain at least one field", field.line(), field.name());

        // Oneof fields cannot be map types
        ProtobufSemanticException.check(!(field.type() instanceof ProtobufMapTypeReference),
                "Oneof field '%s' cannot be a map type\n\nMap fields cannot be used inside oneof blocks.\n\nHelp: If you need a map-like structure in a oneof, use a wrapper message:\n      message MapWrapper {{\n        map<string, int32> values = 1;\n      }}\n      oneof choice {{\n        MapWrapper my_map = 1;\n        string other_option = 2;\n      }}",
                field.line(), field.name());
    }

    private static void validateEnum(ProtobufEnumStatement enumStmt) {
        var enumConstant = enumStmt.getDirectChildByType(ProtobufEnumConstantStatement.class);
        ProtobufSyntaxException.check(enumConstant.isPresent(),
                "Enum \"%s\" must have at least one value",
                enumStmt.line(), enumStmt.name());
    }

    private static void validateService(ProtobufDocumentTree document, ProtobufServiceStatement service) {
        var methodNames = new HashSet<String>();
        for (var child : service.children()) {
            if (child instanceof ProtobufMethodStatement method) {
                // Check for duplicate method names
                if (!methodNames.add(method.name())) {
                    throw new ProtobufSemanticException(
                            "Duplicate method name \"%s\" in service \"%s\"",
                            method.line(), method.name(), service.name());
                }

                // Validate RPC input/output types are messages (not enums)
                validateRpcTypes(document, service, method);
            }
        }
    }

    private static void validateRpcTypes(ProtobufDocumentTree document, ProtobufServiceStatement service, ProtobufMethodStatement method) {
        var inputTypeWrapper = method.inputType();
        var outputTypeWrapper = method.outputType();

        if (inputTypeWrapper == null || outputTypeWrapper == null) {
            return; // Not fully parsed yet
        }

        var inputType = inputTypeWrapper.value();
        var outputType = outputTypeWrapper.value();

        // Resolve and validate input type
        if (inputType instanceof ProtobufUnresolvedTypeReference(var inputName)) {
            var resolvedInput = tryResolveType(document, inputName, method);
            if (resolvedInput != null) {
                method.setInputType(new ProtobufMethodStatement.Type(resolvedInput, inputTypeWrapper.stream()));
                inputType = resolvedInput;
            }
        }
        if (inputType instanceof ProtobufEnumTypeReference) {
            throw new ProtobufSemanticException(
                    "RPC method \"%s\" in service \"%s\" has enum type as input\n\nRPC methods must use message types for input and output, not enum types.\n\nHelp: Wrap the enum in a message type:\n      message %sRequest {\n        YourEnum value = 1;\n      }",
                    method.line(), method.name(), service.name(), method.name());
        }

        // Resolve and validate output type
        if (outputType instanceof ProtobufUnresolvedTypeReference(var outputName)) {
            var resolvedOutput = tryResolveType(document, outputName, method);
            if (resolvedOutput != null) {
                method.setOutputType(new ProtobufMethodStatement.Type(resolvedOutput, outputTypeWrapper.stream()));
                outputType = resolvedOutput;
            }
        }
        if (outputType instanceof ProtobufEnumTypeReference) {
            throw new ProtobufSemanticException(
                    "RPC method \"%s\" in service \"%s\" has enum type as output\n\nRPC methods must use message types for input and output, not enum types.\n\nHelp: Wrap the enum in a message type:\n      message %sResponse {\n        YourEnum value = 1;\n      }",
                    method.line(), method.name(), service.name(), method.name());
        }
    }

    // Converts a field name to its JSON representation (lowerCamelCase)
    private static String toJsonName(String fieldName) {
        if (fieldName == null || fieldName.isEmpty()) {
            return fieldName;
        }

        var result = new StringBuilder(fieldName.length());
        var capitalizeNext = false;
        for (var i = 0; i < fieldName.length(); i++) {
            var c = fieldName.charAt(i);
            if (c == '_') {
                capitalizeNext = true;
            } else if (capitalizeNext) {
                result.append(Character.toUpperCase(c));
                capitalizeNext = false;
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }
}
