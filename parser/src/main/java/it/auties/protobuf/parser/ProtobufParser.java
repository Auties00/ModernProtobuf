package it.auties.protobuf.parser;

import it.auties.protobuf.model.ProtobufType;
import it.auties.protobuf.model.ProtobufVersion;
import it.auties.protobuf.parser.exception.ProtobufParserException;
import it.auties.protobuf.parser.exception.ProtobufSyntaxException;
import it.auties.protobuf.parser.token.*;
import it.auties.protobuf.parser.tree.*;
import it.auties.protobuf.parser.type.*;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Parses Protocol Buffer definition files (.proto) into an abstract syntax tree (AST).
 * <p>
 * The parser takes the token stream produced by {@link ProtobufLexer} and constructs a structured
 * representation of the Protocol Buffer definitions as a tree of {@link ProtobufTree} nodes.
 * It handles all Protocol Buffer language constructs including:
 * </p>
 * <ul>
 *   <li>Syntax declarations ({@code syntax = "proto2"} or {@code syntax = "proto3"})</li>
 *   <li>Package declarations ({@code package com.example})</li>
 *   <li>Import statements ({@code import "other.proto"})</li>
 *   <li>Message definitions with fields, nested messages, and enums</li>
 *   <li>Enum definitions with constants</li>
 *   <li>Service definitions with RPC methods</li>
 *   <li>Extensions and extension ranges</li>
 *   <li>Reserved field numbers and names</li>
 *   <li>Options at file, message, field, enum, and service levels</li>
 *   <li>Groups (deprecated Protocol Buffers 2 feature)</li>
 *   <li>Oneofs</li>
 *   <li>Maps</li>
 * </ul>
 * <p>
 * The parser provides both single-file and directory parsing capabilities. After parsing,
 * the {@link ProtobufAnalyzer} performs semantic analysis including type resolution,
 * import attribution, and validation.
 * </p>
 * <h2>Usage Example:</h2>
 * <pre>{@code
 * // Parse a single file
 * Path protoFile = Path.of("message.proto");
 * ProtobufDocumentTree tree = ProtobufParser.parseOnly(protoFile);
 *
 * // Parse a directory of files
 * Path protoDirectory = Path.of("proto/");
 * Map<String, ProtobufDocumentTree> trees = ProtobufParser.parse(protoDirectory);
 * }</pre>
 *
 * @see ProtobufLexer
 * @see ProtobufAnalyzer
 * @see ProtobufDocumentTree
 */
public final class ProtobufParser {
    private static final String STATEMENT_END = ";";
    private static final String BODY_START = "{";
    private static final String BODY_END = "}";
    private static final String ASSIGNMENT_OPERATOR = "=";
    private static final String ARRAY_START = "[";
    private static final String ARRAY_END = "]";
    private static final String ARRAY_SEPARATOR = ",";
    private static final String RANGE_OPERATOR = "to";
    private static final String TYPE_PARAMETERS_START = "<";
    private static final String TYPE_PARAMETERS_END = ">";
    private static final String NULL = "null";
    private static final String KEY_VALUE_SEPARATOR = ":";
    private static final String PARENS_START = "(";
    private static final String PARENS_END = ")";
    private static final String STREAM = "stream";
    private static final String MAX = "max";

    private ProtobufParser() {
        throw new UnsupportedOperationException();
    }

    /**
     * Parses all Protocol Buffer files in the specified path and performs full semantic analysis.
     * <p>
     * If the path is a file, parses that single file. If it's a directory, recursively parses all
     * regular files in the directory tree. After parsing, all documents are analyzed together,
     * resolving imports and type references between files.
     * </p>
     *
     * @param path the file or directory path to parse, must not be null
     * @return an unmodifiable map from file names to their parsed document trees
     * @throws IOException if an I/O error occurs while reading the files
     * @throws ProtobufParserException if a parsing or semantic error is encountered
     */
    public static Map<String, ProtobufDocumentTree> parse(Path path) throws IOException {
        Objects.requireNonNull(path, "Path must not be null");
        if (!Files.isDirectory(path)) {
            return Map.of(path.getFileName().toString(), parseOnly(path));
        }

        try(var walker = Files.walk(path)) {
            var files = walker.filter(Files::isRegularFile).toList();
            Map<String, ProtobufDocumentTree> results = HashMap.newHashMap(files.size());
            for(var file : files) {
                var parsed = doParse(file, Files.newBufferedReader(file));
                if(results.put(file.getFileName().toString(), parsed) != null) {
                    throw new ProtobufParserException("Duplicate file: " + file);
                }
            }
            ProtobufAnalyzer.attribute(results.values());
            return Collections.unmodifiableMap(results);
        }
    }

    /**
     * Parses a Protocol Buffer definition from a string and performs semantic analysis.
     * <p>
     * This method is useful for parsing inline Protocol Buffer definitions or testing.
     * The parsed document is analyzed in isolation without any additional context.
     * </p>
     *
     * @param input the Protocol Buffer definition string to parse, must not be null
     * @return the parsed and analyzed document tree
     * @throws ProtobufParserException if a parsing or semantic error is encountered
     */
    public static ProtobufDocumentTree parseOnly(String input) {
        return parseOnly(input, (Collection<ProtobufDocumentTree>) null);
    }

    /**
     * Parses a Protocol Buffer definition from a string with additional context documents.
     * <p>
     * The provided context documents allow type references in the parsed document to be resolved
     * against types defined in other documents. This is useful when parsing documents that import
     * or reference types from other files.
     * </p>
     *
     * @param input the Protocol Buffer definition string to parse, must not be null
     * @param documentTrees additional context documents for type resolution
     * @return the parsed and analyzed document tree
     * @throws ProtobufParserException if a parsing or semantic error is encountered
     */
    public static ProtobufDocumentTree parseOnly(String input, ProtobufDocumentTree... documentTrees) {
        return parseOnly(input, Arrays.asList(documentTrees));
    }

    /**
     * Parses a Protocol Buffer definition from a string with additional context documents.
     *
     * @param input the Protocol Buffer definition string to parse, must not be null
     * @param documents additional context documents for type resolution, may be null
     * @return the parsed and analyzed document tree
     * @throws ProtobufParserException if a parsing or semantic error is encountered
     */
    public static ProtobufDocumentTree parseOnly(String input, Collection<? extends ProtobufDocumentTree> documents) {
        try {
            var results = new ArrayList<ProtobufDocumentTree>();
            if(documents != null) {
                results.addAll(documents);
            }
            var result = doParse(null, new StringReader(input));
            results.add(result);
            ProtobufAnalyzer.attribute(results);
            return result;
        } catch (IOException exception) {
            throw new ProtobufParserException("Unexpected end of input");
        }
    }

    /**
     * Parses a single Protocol Buffer file and performs semantic analysis.
     * <p>
     * The parsed document is analyzed in isolation without any additional context.
     * </p>
     *
     * @param input the file path to parse, must not be null
     * @return the parsed and analyzed document tree
     * @throws IOException if an I/O error occurs while reading the files
     * @throws ProtobufParserException if a parsing or semantic error is encountered
     */
    public static ProtobufDocumentTree parseOnly(Path input) throws IOException {
        return parseOnly(input, (Collection<ProtobufDocumentTree>) null);
    }

    /**
     * Parses a single Protocol Buffer file with additional context documents.
     *
     * @param input the file path to parse, must not be null
     * @param documentTrees additional context documents for type resolution
     * @return the parsed and analyzed document tree
     * @throws IOException if an I/O error occurs while reading the files
     * @throws ProtobufParserException if a parsing or semantic error is encountered
     */
    public static ProtobufDocumentTree parseOnly(Path input, ProtobufDocumentTree... documentTrees) throws IOException {
        return parseOnly(input, Arrays.asList(documentTrees));
    }

    /**
     * Parses a single Protocol Buffer file with additional context documents.
     *
     * @param input the file path to parse, must not be null
     * @param documents additional context documents for type resolution, may be null
     * @return the parsed and analyzed document tree
     * @throws IOException if an I/O error occurs while reading the files
     * @throws ProtobufParserException if a parsing or semantic error is encountered
     */
    public static ProtobufDocumentTree parseOnly(Path input, Collection<? extends ProtobufDocumentTree> documents) throws IOException {
        var results = new ArrayList<ProtobufDocumentTree>();
        if(documents != null) {
            results.addAll(documents);
        }
        var result = doParse(input, Files.newBufferedReader(input));
        results.add(result);
        ProtobufAnalyzer.attribute(results);
        return result;
    }

    private static ProtobufDocumentTree doParse(Path location, Reader input) throws IOException {
        try {
            var document = new ProtobufDocumentTree(location);
            var tokenizer = new ProtobufLexer(input);
            String token;
            while ((token = tokenizer.nextRawToken(false)) != null) {
                switch (token) {
                    case STATEMENT_END -> {
                        var statement = parseEmpty(tokenizer);
                        document.addChild(statement);
                    }
                    case "package" -> {
                        var statement = parsePackage(document, tokenizer);
                        document.addChild(statement);
                    }
                    case "syntax" -> {
                        var statement = parseSyntax(document, tokenizer);
                        document.addChild(statement);
                    }
                    case "option" -> {
                        var statement = parseOption(tokenizer, OptionParser.STATEMENT);
                        document.addChild(statement);
                    }
                    case "message" -> {
                        var statement = parseMessage(document, tokenizer);
                        document.addChild(statement);
                    }
                    case "enum" -> {
                        var statement = parseEnum(tokenizer);
                        document.addChild(statement);
                    }
                    case "service" -> {
                        var statement = parseService(tokenizer);
                        document.addChild(statement);
                    }
                    case "import" -> {
                        var statement = parseImport(tokenizer);
                        document.addChild(statement);
                    }
                    case "extend" -> {
                        var statement = parseExtend(document, tokenizer);
                        document.addChild(statement);
                    }
                    default -> throw new ProtobufSyntaxException("Unexpected token '%s' at top level\n\nExpected one of: 'syntax', 'package', 'import', 'option', 'message', 'enum', 'service', or 'extend'\n\nHelp: Only the keywords listed above are valid at the top level of a .proto file.\n      If you meant to use '%s' as a field name, it must be inside a message definition.".formatted(token, token), tokenizer.line());
                }
            }
            return document;
        } catch (ProtobufParserException syntaxException) {
            var withPath = new ProtobufParserException(syntaxException.getMessage() + " while parsing " + (location == null ? "input" : location.getFileName()));
            withPath.setStackTrace(syntaxException.getStackTrace());
            throw withPath;
        }
    }

    private static ProtobufEmptyStatement parseEmpty(ProtobufLexer tokenizer) {
        return new ProtobufEmptyStatement(tokenizer.line());
    }

    private static ProtobufPackageStatement parsePackage(ProtobufDocumentTree document, ProtobufLexer tokenizer) throws IOException {
        ProtobufSyntaxException.check(document.packageName().isEmpty(),
                "Duplicate package declaration\n\nPackage can only be set once per .proto file, but found multiple 'package' statements.\n\nHelp: Remove all but one 'package' declaration. If you need to organize your types,\n      consider using nested messages or separate .proto files.",
                tokenizer.line());
        var statement = new ProtobufPackageStatement(tokenizer.line());
        var name = tokenizer.nextRawToken();
        ProtobufSyntaxException.check(isValidType(name),
                "Invalid package name '%s'\n\nExpected a valid identifier or qualified name (e.g., 'com.example.api')\n\nHelp: Package names must follow these rules:\n      - Use lowercase letters, numbers, and dots\n      - Start with a letter\n      - No consecutive dots or leading/trailing dots\n      Example: package com.example.myapp;".formatted(name),
                tokenizer.line());
        statement.setName(name);
        var end = tokenizer.nextRawToken();
        ProtobufSyntaxException.check(isStatementEnd(end),
                "Expected ';' after package name but found '%s'\n\nHelp: Package declarations must end with a semicolon.\n      Example: package com.example;".formatted(end),
                tokenizer.line());
        return statement;
    }

    private static ProtobufSyntaxStatement parseSyntax(ProtobufDocumentTree document, ProtobufLexer tokenizer) throws IOException {
        ProtobufSyntaxException.check(document.children().isEmpty(),
                "Syntax declaration must be the first statement\n\nFound 'syntax' keyword after other declarations, but it must appear before any\npackage, import, message, enum, or service definitions.\n\nHelp: Move the 'syntax' declaration to the very first line of your .proto file.\n      Example:\n        syntax = \"proto3\";\n        package com.example;",
                tokenizer.line());
        var statement = new ProtobufSyntaxStatement(tokenizer.line());
        var assignment = tokenizer.nextRawToken();
        ProtobufSyntaxException.check(isAssignmentOperator(assignment),
                "Expected '=' after 'syntax' keyword but found '%s'\n\nHelp: Syntax declarations must use '=' to assign the version.\n      Example: syntax = \"proto3\";".formatted(assignment),
                tokenizer.line());
        var versionCodeToken = tokenizer.nextToken();
        if(!(versionCodeToken instanceof ProtobufLiteralToken(var versionCode, _))) {
            throw new ProtobufSyntaxException("Expected string literal for protobuf version but found '%s'\n\nHelp: The syntax version must be a quoted string, either \"proto2\" or \"proto3\".\n      Example: syntax = \"proto3\";".formatted(versionCodeToken), tokenizer.line());
        }
        var version = ProtobufVersion.of(versionCode)
                .orElseThrow(() -> new ProtobufParserException("Unknown protobuf version: \"%s\"\n\nSupported versions are:\n  - \"proto2\" (Protocol Buffers version 2)\n  - \"proto3\" (Protocol Buffers version 3, recommended)\n\nHelp: Use syntax = \"proto3\"; for new projects (recommended)\n      or syntax = \"proto2\"; for legacy compatibility.".formatted(versionCode)));
        statement.setVersion(version);
        var end = tokenizer.nextRawToken();
        ProtobufSyntaxException.check(isStatementEnd(end),
                "Expected ';' after syntax declaration but found '%s'\n\nHelp: Syntax declarations must end with a semicolon.\n      Example: syntax = \"proto3\";".formatted(end),
                tokenizer.line());
        return statement;
    }

    private static <T> T parseOption(ProtobufLexer tokenizer, OptionParser<T> parser) throws IOException {
        var nameOrParensStart = tokenizer.nextRawToken();

        String name;
        boolean extension;
        if(isParensStart(nameOrParensStart)) {
            name = tokenizer.nextRawToken();
            var parensEnd = tokenizer.nextRawToken();
            ProtobufSyntaxException.check(isParensEnd(parensEnd),
                    "Expected ')' to close custom option name but found '%s'\n\nCustom options must be enclosed in parentheses.\n\nHelp: Format for custom options:\n      option (my_custom_option) = value;\n      option (com.example.my_option) = value;".formatted(parensEnd),
                    tokenizer.line());
            extension = true;
        }else {
           name = nameOrParensStart;
           extension = false;
        }

        List<String> membersAccessed;
        var membersAccessedOrAssignment = tokenizer.nextRawToken();
        if(isAssignmentOperator(membersAccessedOrAssignment)) {
            membersAccessed = List.of();
        }else if(membersAccessedOrAssignment.charAt(0) == '.') {
            var accessed = membersAccessedOrAssignment.substring(1);
            ProtobufSyntaxException.check(isValidType(accessed),
                    "Invalid sub-field access '%s' in option\n\nWhen accessing nested option fields, each part must be a valid identifier.\n\nHelp: Format for accessing nested option fields:\n      option java_package.subfield = value;\n      Example: option (my_option).field = value;".formatted(accessed),
                    tokenizer.line());
            membersAccessed = Arrays.asList(accessed.split("\\."));
            var assignment = tokenizer.nextRawToken();
            ProtobufSyntaxException.check(isAssignmentOperator(assignment),
                    "Expected '=' after option name but found '%s'\n\nHelp: Options use '=' to assign values.\n      Example: option java_package = \"com.example\";".formatted(assignment),
                    tokenizer.line());
        }else{
            throw new ProtobufSyntaxException("Expected '=' or '.' after option name but found '%s'\n\nHelp: Options must be assigned a value with '='.\n      For nested fields, use dot notation: option.field = value;\n      Example: option java_package = \"com.example\";".formatted(membersAccessedOrAssignment), tokenizer.line());
        }
        var optionName = new ProtobufOptionName(name, extension, membersAccessed);
        var optionValue = readExpression(tokenizer);
        return parser.parse(tokenizer, optionName, optionValue);
    }

    private static ProtobufMessageStatement parseMessage(ProtobufDocumentTree document, ProtobufLexer tokenizer) throws IOException {
        var statement = new ProtobufMessageStatement(tokenizer.line());
        var name = tokenizer.nextRawToken();
        ProtobufSyntaxException.check(isValidIdent(name),
                "Invalid message name '%s'\n\nMessage names must be valid identifiers (letters, numbers, and underscores).\nThey should start with a letter and use PascalCase by convention.\n\nHelp: Choose a descriptive name for your message type.\n      Example: message UserProfile { ... }".formatted(name),
                tokenizer.line());
        statement.setName(name);
        var objectStart = tokenizer.nextRawToken();
        ProtobufSyntaxException.check(isBodyStart(objectStart),
                "Expected '{{' to start message body but found '%s'\n\nHelp: Message declarations must have a body enclosed in curly braces.\n      Example:\n        message Person {{\n          string name = 1;\n          int32 age = 2;\n        }}".formatted(objectStart),
                tokenizer.line());
        String token;
        while (!isBodyEnd(token = tokenizer.nextRawToken())) {
            switch (token) {
                case STATEMENT_END -> {
                    var child = parseEmpty(tokenizer);
                    statement.addChild(child);
                }
                case "option" -> {
                    var child = parseOption(tokenizer, OptionParser.STATEMENT);
                    statement.addChild(child);
                }
                case "message" -> {
                    var child = parseMessage(document, tokenizer);
                    statement.addChild(child);
                }
                case "enum" -> {
                    var child = parseEnum(tokenizer);
                    statement.addChild(child);
                }
                case "extend" -> {
                    var child = parseExtend(document, tokenizer);
                    statement.addChild(child);
                }
                case "extensions" -> {
                    var child = parseExtensions(tokenizer);
                    statement.addChild(child);
                }
                case "reserved"  -> {
                    var child = parseReserved(tokenizer);
                    statement.addChild(child);
                }
                case "oneof" -> {
                    var child = parseOneof(document, tokenizer);
                    statement.addChild(child);
                }
                default -> {
                    var child = parseField(document, true, tokenizer, token);
                    statement.addChild(child);
                }
            }
        }
        return statement;
    }

    private static ProtobufExtendStatement parseExtend(ProtobufDocumentTree document, ProtobufLexer tokenizer) throws IOException {
        var statement = new ProtobufExtendStatement(tokenizer.line());
        var qualifiedName = tokenizer.nextRawToken();
        ProtobufSyntaxException.check(isValidType(qualifiedName),
                "Invalid message name '%s' in extend declaration\n\nExpected a valid message type name (optionally fully-qualified).\n\nHelp: Extend declarations target existing message types.\n      Example:\n        extend MyMessage {{ ... }}\n        extend com.example.MyMessage {{ ... }}".formatted(qualifiedName),
                tokenizer.line());

        // In proto3, extends are only allowed for Options messages
        // Validation happens in the attribute phase, not here during parsing
        var reference = new ProtobufUnresolvedObjectTypeReference(qualifiedName);
        statement.setDeclaration(reference);
        var objectStart = tokenizer.nextRawToken();
        ProtobufSyntaxException.check(isBodyStart(objectStart),
                "Expected '{{' to start extend body but found '%s'\n\nHelp: Extend declarations must have a body with fields to add.\n      Example:\n        extend MyMessage {{\n          string extra_field = 100;\n        }}".formatted(objectStart),
                tokenizer.line());
        String token;
        while (!isBodyEnd(token = tokenizer.nextRawToken())) {
            switch (token) {
                case STATEMENT_END -> {
                    var child = parseEmpty(tokenizer);
                    statement.addChild(child);
                }
                case "option" -> {
                    var child = parseOption(tokenizer, OptionParser.STATEMENT);
                    statement.addChild(child);
                }
                case "message" -> {
                    var child = parseMessage(document, tokenizer);
                    statement.addChild(child);
                }
                case "enum" -> {
                    var child = parseEnum(tokenizer);
                    statement.addChild(child);
                }
                case "extend" -> {
                    var child = parseExtend(document, tokenizer);
                    statement.addChild(child);
                }
                case "extensions" -> {
                    var child = parseExtensions(tokenizer);
                    statement.addChild(child);
                }
                case "reserved"  -> {
                    var child = parseReserved(tokenizer);
                    statement.addChild(child);
                }
                case "oneof" -> {
                    var child = parseOneof(document, tokenizer);
                    statement.addChild(child);
                }
                default -> {
                    var child = parseField(document, true, tokenizer, token);
                    statement.addChild(child);
                }
            }
        }
        return statement;
    }

    private static ProtobufFieldStatement parseField(ProtobufDocumentTree document, boolean parseModifier, ProtobufLexer tokenizer, String token) throws IOException {
        ProtobufFieldStatement.Modifier modifier;
        ProtobufTypeReference reference;
        if(parseModifier) {
            modifier = ProtobufFieldStatement.Modifier.of(token)
                    .orElse(ProtobufFieldStatement.Modifier.NONE);
           if(modifier == ProtobufFieldStatement.Modifier.NONE) {
               ProtobufSyntaxException.check(isValidType(token),
                       "Invalid field type '%s'\n\nExpected a scalar type, message type, or enum type.\n\nHelp: Valid scalar types: int32, int64, uint32, uint64, sint32, sint64,\n      fixed32, fixed64, sfixed32, sfixed64, double, float, bool, string, bytes\n      \n      For message/enum types: use the type name (e.g., MyMessage)\n      For qualified types: package.MessageName".formatted(token), tokenizer.line());
               reference = ProtobufTypeReference.of(token);
           }else {
               var type = tokenizer.nextRawToken();
               ProtobufSyntaxException.check(isValidType(type),
                       "Invalid field type '%s' after modifier '%s'\n\nExpected a type name after the field modifier.\n\nHelp: Field declarations: [modifier] type name = number;\n      Example: repeated string tags = 1;".formatted(type, modifier.token()),
                       tokenizer.line());
               reference = ProtobufTypeReference.of(type);
               ProtobufSyntaxException.check(reference.protobufType() != ProtobufType.MAP,
                       "Map fields cannot have a modifier\n\nMap fields are implicitly repeated and cannot use 'repeated', 'optional', or 'required'.\n\nHelp: Remove the modifier from your map field.\n      Correct: map<string, int32> my_map = 1;\n      Wrong: repeated map<string, int32> my_map = 1;",
                       tokenizer.line());
           }
       }else {
           modifier = ProtobufFieldStatement.Modifier.NONE;
           ProtobufSyntaxException.check(isValidType(token),
                   "Invalid field type '%s'\n\nExpected a scalar type, message type, or enum type.\n\nHelp: Valid scalar types: int32, int64, uint32, uint64, sint32, sint64,\n      fixed32, fixed64, sfixed32, sfixed64, double, float, bool, string, bytes\n      \n      For message/enum types: use the type name (e.g., MyMessage)\n      For qualified types: package.MessageName".formatted(token),
                   tokenizer.line());
           reference = ProtobufTypeReference.of(token);
       }

        ProtobufFieldStatement child;
        if(reference.protobufType() == ProtobufType.GROUP) {
            if(document.syntax().isPresent() && document.syntax().get() == ProtobufVersion.PROTOBUF_3) {
                throw new ProtobufSyntaxException("Group fields are not supported in proto3\n\nGroups are a deprecated proto2 feature and cannot be used in proto3.\n\nHelp: Use regular message types instead:\n      message MyGroup {{\n        string field = 1;\n      }}\n      MyGroup my_field = 1;\n\n      Note: Groups had worse performance and compatibility than regular messages.", tokenizer.line());
            }
            child = new ProtobufGroupFieldStatement(tokenizer.line());
        }else {
            child = new ProtobufFieldStatement(tokenizer.line());
            child.setType(reference);
        }
        child.setModifier(modifier);

        var nameOrTypeArgs = tokenizer.nextRawToken();
        if(isTypeParametersStart(nameOrTypeArgs)) {
            if(!(child.type() instanceof ProtobufMapTypeReference mapType) || mapType.isAttributed()) {
                throw new ProtobufSyntaxException("Unexpected '<' after type\n\nOnly map types use angle brackets for type parameters.\n\nHelp: For map fields: map<KeyType, ValueType> field_name = number;\n      For other fields: type field_name = number;", tokenizer.line());
            }

            var keyTypeToken = tokenizer.nextRawToken();
            ProtobufSyntaxException.check(isValidType(keyTypeToken),
                    "Invalid map key type '%s'\n\nExpected a type name for the map key.\n\nHelp: Map syntax: map<KeyType, ValueType> field_name = number;\n      Example: map<string, int32> user_scores = 1;".formatted(keyTypeToken),
                    tokenizer.line());
            var keyType = ProtobufTypeReference.of(keyTypeToken);
            if(keyType.protobufType() == ProtobufType.MAP || keyType.protobufType() == ProtobufType.GROUP) {
                throw new ProtobufSyntaxException("Map key type cannot be '%s'\n\nMap keys cannot be nested maps or groups.\n\nHelp: Valid map key types: int32, int64, uint32, uint64, sint32, sint64,\n      fixed32, fixed64, sfixed32, sfixed64, bool, string".formatted(keyTypeToken), tokenizer.line());
            }
            mapType.setKeyType(keyType);

            var separator = tokenizer.nextRawToken();
            ProtobufSyntaxException.check(isArraySeparator(separator),
                    "Expected ',' between map key and value types but found '%s'\n\nHelp: Map syntax: map<KeyType, ValueType> field_name = number;\n      Example: map<string, int32> user_scores = 1;".formatted(separator),
                    tokenizer.line());

            var valueTypeToken = tokenizer.nextRawToken();
            ProtobufSyntaxException.check(isValidType(valueTypeToken),
                    "Invalid map value type '%s'\n\nExpected a type name for the map value.\n\nHelp: Map syntax: map<KeyType, ValueType> field_name = number;\n      Example: map<string, MyMessage> entities = 1;".formatted(valueTypeToken),
                    tokenizer.line());
            var valueType = ProtobufTypeReference.of(valueTypeToken);
            mapType.setValueType(valueType);

            var end = tokenizer.nextRawToken();
            ProtobufSyntaxException.check(isTypeParametersEnd(end),
                    "Expected '>' to close map type parameters but found '%s'\n\nHelp: Map syntax: map<KeyType, ValueType> field_name = number;\n      Make sure you have matching angle brackets.".formatted(end),
                    tokenizer.line());

            var childName = tokenizer.nextRawToken();
            ProtobufSyntaxException.check(isValidIdent(childName),
                    "Invalid field name '%s'\n\nField names must be valid identifiers.\n\nHelp: Field names should:\n      - Start with a letter or underscore\n      - Contain only letters, numbers, and underscores\n      - Use snake_case by convention\n      Example: map<string, int32> user_scores = 1;".formatted(childName),
                    tokenizer.line());
            child.setName(childName);
        }else if(isValidIdent(nameOrTypeArgs)) {
            child.setName(nameOrTypeArgs);
        }else {
            throw new ProtobufSyntaxException("Invalid field name '%s'\n\nExpected a valid field name after the type.\n\nHelp: Field declaration format:\n      type field_name = field_number;\n      Example: string user_name = 1;".formatted(nameOrTypeArgs), tokenizer.line());
        }

        var operator = tokenizer.nextRawToken();
        ProtobufSyntaxException.check(isAssignmentOperator(operator),
                "Expected '=' after field name but found '%s'\n\nHelp: Field declarations must assign a field number with '='.\n      Example: string name = 1;".formatted(operator),
                tokenizer.line());
        var index = tokenizer.nextToken();
        if(index instanceof ProtobufNumberToken(var number) && number instanceof ProtobufInteger integer) {
            child.setIndex(integer);
        }else {
            throw new ProtobufSyntaxException("Expected field number (integer) but found '%s'\n\nField numbers must be positive integers.\n\nHelp: Field numbers identify fields in the binary format and must be unique.\n      Use 1-15 for frequently set fields (most efficient).\n      Example: string name = 1;".formatted(index), tokenizer.line());
        }

        var bodyStartOrStatementEndToken = parseOptions(tokenizer, tokenizer.nextRawToken(), child);
        if(child instanceof ProtobufGroupFieldStatement groupStatement && isBodyStart(bodyStartOrStatementEndToken)) {
            String groupToken;
            while(!isBodyEnd(groupToken = tokenizer.nextRawToken())) {
                switch (groupToken) {
                    case STATEMENT_END -> {
                        var groupChild = parseEmpty(tokenizer);
                        groupStatement.addChild(groupChild);
                    }

                    case "message" -> {
                        var groupChild = parseMessage(document, tokenizer);
                        groupStatement.addChild(groupChild);
                    }

                    case "enum" -> {
                        var groupChild = parseEnum(tokenizer);
                        groupStatement.addChild(groupChild);
                    }

                    case "extend" -> {
                        var groupChild = parseExtend(document, tokenizer);
                        groupStatement.addChild(groupChild);
                    }

                    case "extensions" -> {
                        var groupChild = parseExtensions(tokenizer);
                        groupStatement.addChild(groupChild);
                    }

                    case "reserved" -> {
                        var groupChild = parseReserved(tokenizer);
                        groupStatement.addChild(groupChild);
                    }

                    case "oneof" -> {
                        var groupChild = parseOneof(document, tokenizer);
                        groupStatement.addChild(groupChild);
                    }

                    default -> {
                        var groupChild = parseField(document, true, tokenizer, groupToken);
                        groupStatement.addChild(groupChild);
                    }
                }
            }
            return child;
        } else if(isStatementEnd(bodyStartOrStatementEndToken)) {
            return child;
        }else {
            throw new ProtobufSyntaxException("Unexpected token " + bodyStartOrStatementEndToken, tokenizer.line());
        }
    }

    private static ProtobufEnumStatement parseEnum(ProtobufLexer tokenizer) throws IOException {
        var statement = new ProtobufEnumStatement(tokenizer.line());
        var name = tokenizer.nextRawToken();
        ProtobufSyntaxException.check(isValidIdent(name),
                "Unexpected token: " + name, tokenizer.line());
        statement.setName(name);
        var objectStart = tokenizer.nextRawToken();
        ProtobufSyntaxException.check(isBodyStart(objectStart),
                "Unexpected token " + objectStart, tokenizer.line());
        String token;
        while (!isBodyEnd(token = tokenizer.nextRawToken())) {
            switch (token) {
                case STATEMENT_END -> {
                    var child = parseEmpty(tokenizer);
                    statement.addChild(child);
                }
                case "option" -> {
                    var child = parseOption(tokenizer, OptionParser.STATEMENT);
                    statement.addChild(child);
                }
                case "extensions" -> {
                    var child = parseExtensions(tokenizer);
                    statement.addChild(child);
                }
                case "reserved"  -> {
                    var child = parseReserved(tokenizer);
                    statement.addChild(child);
                }
                default -> {
                    var child = parseEnumConstant(token, tokenizer);
                    child.setType(new ProtobufEnumTypeReference(statement));
                    statement.addChild(child);
                }
            }
        }
        return statement;
    }

    private static ProtobufEnumConstantStatement parseEnumConstant(String token, ProtobufLexer tokenizer) throws IOException {
        var statement = new ProtobufEnumConstantStatement(tokenizer.line());
        statement.setModifier(ProtobufFieldStatement.Modifier.NONE);
        statement.setName(token);

        var operator = tokenizer.nextRawToken();
        ProtobufSyntaxException.check(isAssignmentOperator(operator),
                "Unexpected token " + operator, tokenizer.line());

        var index = tokenizer.nextToken();
        if(index instanceof ProtobufNumberToken(var number) && number instanceof ProtobufInteger integer) {
            statement.setIndex(integer);
        }else {
            throw new ProtobufSyntaxException("Unexpected token " + index, tokenizer.line());
        }

        var statementEndToken = parseOptions(tokenizer, tokenizer.nextRawToken(), statement);

        ProtobufSyntaxException.check(isStatementEnd(statementEndToken),
                "Unexpected token " + statementEndToken, tokenizer.line());
        return statement;
    }

    private static ProtobufExtensionsStatement parseExtensions(ProtobufLexer tokenizer) throws IOException {
        var statement = new ProtobufExtensionsStatement(tokenizer.line());

        while(true) {
            var valueOrMinToken = tokenizer.nextToken();
            if (!(valueOrMinToken instanceof ProtobufNumberToken(var valueOrMinNumber)) || !(valueOrMinNumber instanceof ProtobufInteger valueOrMinInt)) {
                throw new ProtobufSyntaxException("Unexpected token " + valueOrMinToken, tokenizer.line());
            }

            var operator = tokenizer.nextRawToken();
            if(isRangeOperator(operator)) {
                var maxToken = tokenizer.nextToken();
                var range = switch (maxToken) {
                    case ProtobufNumberToken(var maxNumber) when maxNumber instanceof ProtobufInteger maxInt -> new ProtobufRange.Bounded(valueOrMinInt, maxInt);
                    case ProtobufRawToken(var token) when isMax(token) -> new ProtobufRange.LowerBounded(valueOrMinInt);
                    default -> throw new ProtobufSyntaxException("Unexpected token " + maxToken, tokenizer.line());
                };

                var expression = new ProtobufIntegerRangeExpression(tokenizer.line());
                expression.setValue(range);
                statement.addExpression(expression);

                operator = tokenizer.nextRawToken();
            }else {
                var expression = new ProtobufNumberExpression(tokenizer.line());
                expression.setValue(valueOrMinInt);
                statement.addExpression(expression);
            }

            var statementEndOrSeparator = parseOptions(tokenizer, operator, statement);
            if (isStatementEnd(statementEndOrSeparator)) {
                if(statement.expressions().isEmpty()) {
                    throw new ProtobufSyntaxException("Unexpected token " + statementEndOrSeparator, tokenizer.line());
                }

                break;
            } else if (!isArraySeparator(statementEndOrSeparator)) {
                throw new ProtobufSyntaxException("Unexpected token " + statementEndOrSeparator, tokenizer.line());
            }
        }

        return statement;
    }

    private static ProtobufReservedStatement parseReserved(ProtobufLexer tokenizer) throws IOException {
        var statement = new ProtobufReservedStatement(tokenizer.line());

        bodyLoop: {
            while (true) {
                var valueOrMinToken = tokenizer.nextToken();
                switch (valueOrMinToken) {
                    case ProtobufLiteralToken(var value, _) -> {
                        var expression = new ProtobufLiteralExpression(tokenizer.line());
                        expression.setValue(value);
                        statement.addExpression(expression);
                        var operator = tokenizer.nextRawToken();
                        if(isStatementEnd(operator)) {
                            if(statement.expressions().isEmpty()) {
                                throw new ProtobufSyntaxException("Unexpected token " + operator, tokenizer.line());
                            }

                            break bodyLoop;
                        }else if(!isArraySeparator(operator)) {
                            throw new ProtobufSyntaxException("Unexpected token " + tokenizer.line(), tokenizer.line());
                        }
                    }

                    case ProtobufNumberToken(var number) when number instanceof ProtobufInteger valueOrMinInt -> {
                        var operator = tokenizer.nextRawToken();
                        if(isRangeOperator(operator)) {
                            var maxToken = tokenizer.nextToken();
                            var range = switch (maxToken) {
                                case ProtobufNumberToken(var maxNumber) when maxNumber instanceof ProtobufInteger maxInt -> new ProtobufRange.Bounded(valueOrMinInt, maxInt);
                                case ProtobufRawToken(var token) when isMax(token) -> new ProtobufRange.LowerBounded(valueOrMinInt);
                                default -> throw new ProtobufSyntaxException("Unexpected token " + maxToken, tokenizer.line());
                            };

                            var expression = new ProtobufIntegerRangeExpression(tokenizer.line());
                            expression.setValue(range);
                            statement.addExpression(expression);

                            operator = tokenizer.nextRawToken();
                        }else {
                            var expression = new ProtobufNumberExpression(tokenizer.line());
                            expression.setValue(valueOrMinInt);
                            statement.addExpression(expression);
                        }

                        if(isStatementEnd(operator)) {
                            if(statement.expressions().isEmpty()) {
                                throw new ProtobufSyntaxException("Unexpected token " + operator, tokenizer.line());
                            }

                            break bodyLoop;
                        }else if(!isArraySeparator(operator)) {
                            throw new ProtobufSyntaxException("Unexpected token " + tokenizer.line(), tokenizer.line());
                        }
                    }

                    default -> throw new ProtobufSyntaxException("Unexpected token " + valueOrMinToken, tokenizer.line());
                }
            }
        }

        return statement;
    }

    private static ProtobufServiceStatement parseService(ProtobufLexer tokenizer) throws IOException {
        var statement = new ProtobufServiceStatement(tokenizer.line());
        var name = tokenizer.nextRawToken();
        ProtobufSyntaxException.check(isValidIdent(name),
                "Unexpected token: " + name, tokenizer.line());
        statement.setName(name);
        var objectStart = tokenizer.nextRawToken();
        ProtobufSyntaxException.check(isBodyStart(objectStart),
                "Unexpected token " + objectStart, tokenizer.line());
        String token;
        while (!isBodyEnd(token = tokenizer.nextRawToken())) {
            switch (token) {
                case STATEMENT_END -> {
                    var child = parseEmpty(tokenizer);
                    statement.addChild(child);
                }
                case "option" -> {
                    var child = parseOption(tokenizer, OptionParser.STATEMENT);
                    statement.addChild(child);
                }
                case "rpc" -> {
                    var child = parseMethod(tokenizer);
                    statement.addChild(child);
                }
                default -> throw new ProtobufSyntaxException("Unexpected token " + token, tokenizer.line());
            }
        }
        return statement;
    }

    private static ProtobufMethodStatement parseMethod(ProtobufLexer tokenizer) throws IOException {
        var statement = new ProtobufMethodStatement(tokenizer.line());
        var name = tokenizer.nextRawToken();
        ProtobufSyntaxException.check(isValidIdent(name),
                "Unexpected token: " + name, tokenizer.line());
        statement.setName(name);
        var inputType = parseMethodType(tokenizer);
        statement.setInputType(inputType);
        var returnsToken = tokenizer.nextRawToken();
        ProtobufSyntaxException.check(Objects.equals(returnsToken, "returns"),
                "Unexpected token: " + returnsToken, tokenizer.line());
        var outputType = parseMethodType(tokenizer);
        statement.setOutputType(outputType);
        var objectStartOrStatementEnd = tokenizer.nextRawToken();
        if(isBodyStart(objectStartOrStatementEnd)){
            String token;
            while (!isBodyEnd(token = tokenizer.nextRawToken())) {
                switch (token) {
                    case STATEMENT_END -> {
                        var child = parseEmpty(tokenizer);
                        statement.addChild(child);
                    }
                    case "option" -> {
                        var child = parseOption(tokenizer, OptionParser.STATEMENT);
                        statement.addChild(child);
                    }
                    default -> throw new ProtobufSyntaxException("Unexpected token " + token, tokenizer.line());
                }
            }
        }else if(!isStatementEnd(objectStartOrStatementEnd)) {
            throw new ProtobufSyntaxException("Unexpected token: " + objectStartOrStatementEnd, tokenizer.line());
        }
        return statement;
    }

    private static ProtobufMethodStatement.Type parseMethodType(ProtobufLexer tokenizer) throws IOException {
        var typeStart = tokenizer.nextRawToken();
        ProtobufSyntaxException.check(isParensStart(typeStart),
                "Unexpected token: " + typeStart, tokenizer.line());
        var typeOrModifier = tokenizer.nextRawToken();
        ProtobufTypeReference typeReference;
        boolean stream;
        if(isStreamModifier(typeOrModifier)) {
            var type = tokenizer.nextRawToken();
            ProtobufSyntaxException.check(isValidType(type),
                    "Invalid RPC type '%s'\n\nExpected a message type name.\n\nHelp: RPC method types must be message types.\n      Example: rpc MyMethod(RequestMessage) returns (ResponseMessage);".formatted(type),
                    tokenizer.line());
            typeReference = ProtobufTypeReference.of(type);
            stream = true;
        }else {
            ProtobufSyntaxException.check(isValidType(typeOrModifier),
                    "Invalid RPC type '%s'\n\nExpected a message type name.\n\nHelp: RPC method types must be message types.\n      Example: rpc MyMethod(RequestMessage) returns (ResponseMessage);".formatted(typeOrModifier),
                    tokenizer.line());
            typeReference = ProtobufTypeReference.of(typeOrModifier);
            stream = false;
        }
        ProtobufSyntaxException.check(typeReference instanceof ProtobufUnresolvedObjectTypeReference,
                "Unexpected type, only messages can be used: " + typeReference.name(), tokenizer.line());
        var typeEnd = tokenizer.nextRawToken();
        ProtobufSyntaxException.check(isParensEnd(typeEnd),
                "Unexpected token: " + typeEnd, tokenizer.line());
        return new ProtobufMethodStatement.Type(typeReference, stream);
    }

    private static ProtobufOneofFieldStatement parseOneof(ProtobufDocumentTree document, ProtobufLexer tokenizer) throws IOException {
        var statement = new ProtobufOneofFieldStatement(tokenizer.line());
        var name = tokenizer.nextRawToken();
        ProtobufSyntaxException.check(isValidIdent(name),
                "Unexpected token: " + name, tokenizer.line());
        statement.setName(name);
        var objectStart = tokenizer.nextRawToken();
        ProtobufSyntaxException.check(isBodyStart(objectStart),
                "Unexpected token " + objectStart, tokenizer.line());
        String token;
        while (!isBodyEnd(token = tokenizer.nextRawToken())) {
            switch (token) {
                case STATEMENT_END -> {
                    var child = parseEmpty(tokenizer);
                    statement.addChild(child);
                }
                case "option" -> {
                    var child = parseOption(tokenizer, OptionParser.STATEMENT);
                    statement.addChild(child);
                }
                default -> {
                    var child = parseField(document, false, tokenizer, token);
                    statement.addChild(child);
                }
            }
        }
        return statement;
    }

    private static ProtobufImportStatement parseImport(ProtobufLexer tokenizer) throws IOException {
        var importStatement = new ProtobufImportStatement(tokenizer.line());
        var token = tokenizer.nextToken();
        switch (token) {
            case ProtobufLiteralToken literal -> {
                importStatement.setModifier(ProtobufImportStatement.Modifier.NONE);
                importStatement.setLocation(literal.value());
                var end = tokenizer.nextRawToken();
                ProtobufSyntaxException.check(isStatementEnd(end),
                        "Unexpected token " + end, tokenizer.line());
            }
            case ProtobufRawToken raw -> {
                var modifier = ProtobufImportStatement.Modifier.of(raw.value())
                        .orElseThrow(() -> new ProtobufParserException("Unexpected token " + raw.value(), tokenizer.line()));
                importStatement.setModifier(modifier);
                var locationToken = tokenizer.nextToken();
                if(!(locationToken instanceof ProtobufLiteralToken(var location, _))) {
                    throw new ProtobufSyntaxException("Unexpected token " + locationToken, tokenizer.line());
                }
                importStatement.setLocation(location);
                var end = tokenizer.nextRawToken();
                ProtobufSyntaxException.check(isStatementEnd(end),
                        "Unexpected token " + end, tokenizer.line());
            }
            default -> throw new ProtobufSyntaxException("Unexpected token " + token, tokenizer.line());
        }
        return importStatement;
    }

    private static ProtobufExpression readExpression(ProtobufLexer tokenizer) throws IOException {
        return switch (tokenizer.nextToken()) {
            case ProtobufBoolToken bool -> readBoolExpression(tokenizer, bool);
            case ProtobufNumberToken number -> readNumberExpression(tokenizer, number);
            case ProtobufLiteralToken literal -> readLiteralExpression(tokenizer, literal);
            case ProtobufRawToken raw
                    when isNullExpression(raw.value()) -> readNullExpression(tokenizer);
            case ProtobufRawToken raw
                    when isBodyStart(raw.value()) -> readMessageExpression(tokenizer);
            case ProtobufRawToken raw
                    when isValidIdent(raw.value()) -> readEnumConstantExpression(tokenizer, raw);
            case ProtobufRawToken raw -> throw new ProtobufSyntaxException("Unexpected token " + raw.value(), tokenizer.line());
        };
    }

    private static ProtobufEnumConstantExpression readEnumConstantExpression(ProtobufLexer tokenizer, ProtobufRawToken raw) {
        var expression = new ProtobufEnumConstantExpression(tokenizer.line());
        expression.setName(raw.value());
        return expression;
    }

    private static ProtobufMessageValueExpression readMessageExpression(ProtobufLexer tokenizer) throws IOException {
        var expression = new ProtobufMessageValueExpression(tokenizer.line());
        var keyOrEndToken = tokenizer.nextToken();
        while (true) {
            if (!(keyOrEndToken instanceof ProtobufRawToken(var keyOrEndValue))) {
                throw new ProtobufSyntaxException("Unexpected token " + keyOrEndToken, tokenizer.line());
            }

            if (isBodyEnd(keyOrEndValue)) {
                break;
            }

            var keyValueSeparatorToken = tokenizer.nextRawToken();
            ProtobufSyntaxException.check(isKeyValueSeparatorOperator(keyValueSeparatorToken),
                    "Unexpected token " + keyValueSeparatorToken, tokenizer.line());

            var valueToken = readExpression(tokenizer);
            expression.addData(keyOrEndValue, valueToken);

            var nextToken = tokenizer.nextToken();
            keyOrEndToken = nextToken instanceof ProtobufRawToken(var nextValue) && isArraySeparator(nextValue)
                    ? tokenizer.nextToken()
                    : nextToken;
        }
        return expression;
    }

    private static ProtobufNullExpression readNullExpression(ProtobufLexer tokenizer) {
        return new ProtobufNullExpression(tokenizer.line());
    }

    private static ProtobufLiteralExpression readLiteralExpression(ProtobufLexer tokenizer, ProtobufLiteralToken literal) {
        var expression = new ProtobufLiteralExpression(tokenizer.line());
        expression.setValue(literal.value());
        return expression;
    }

    private static ProtobufNumberExpression readNumberExpression(ProtobufLexer tokenizer, ProtobufNumberToken number) {
        var expression = new ProtobufNumberExpression(tokenizer.line());
        expression.setValue(number.value());
        return expression;
    }

    private static ProtobufBoolExpression readBoolExpression(ProtobufLexer tokenizer, ProtobufBoolToken bool) {
        var expression = new ProtobufBoolExpression(tokenizer.line());
        expression.setValue(bool.value());
        return expression;
    }

    private static String parseOptions(ProtobufLexer tokenizer, String currentToken, ProtobufTree.WithOptions child) throws IOException {
        if(!isArrayStart(currentToken)) {
            return currentToken;
        }

        String optionSeparatorOrOptionEnd;
        do {
            var expression = parseOption(tokenizer, OptionParser.EXPRESSION);
            child.addOption(expression);
        }while (isArraySeparator(optionSeparatorOrOptionEnd = tokenizer.nextRawToken()));

        ProtobufSyntaxException.check(isArrayEnd(optionSeparatorOrOptionEnd),
                "Unexpected token " + optionSeparatorOrOptionEnd, tokenizer.line());

        return tokenizer.nextRawToken();
    }

    private interface OptionParser<T> {
        OptionParser<ProtobufOptionStatement> STATEMENT = (tokenizer, name, value) -> {
            var end = tokenizer.nextRawToken();
            ProtobufSyntaxException.check(isStatementEnd(end),
                    "Expected ';' after option value but found '%s'\n\nHelp: Option declarations must end with a semicolon.\n      Example: option java_package = \"com.example\";".formatted(end),
                    tokenizer.line());
            var statement = new ProtobufOptionStatement(tokenizer.line());
            statement.setName(name);
            statement.setValue(value);
            return statement;
        };

        OptionParser<ProtobufOptionExpression> EXPRESSION = (tokenizer, name, value) -> {
            var expression = new ProtobufOptionExpression(tokenizer.line());
            expression.setName(name);
            expression.setValue(value);
            return expression;
        };

        T parse(ProtobufLexer tokenizer, ProtobufOptionName name, ProtobufExpression value) throws IOException;
    }

    private static boolean isKeyValueSeparatorOperator(String keyValueSeparatorToken) {
        return Objects.equals(keyValueSeparatorToken, KEY_VALUE_SEPARATOR);
    }

    private static boolean isAssignmentOperator(String operator) {
        return Objects.equals(operator, ASSIGNMENT_OPERATOR);
    }

    private static boolean isBodyStart(String operator) {
        return Objects.equals(operator, BODY_START);
    }

    private static boolean isBodyEnd(String operator) {
        return Objects.equals(operator, BODY_END);
    }

    private static boolean isArrayStart(String operator) {
        return Objects.equals(operator, ARRAY_START);
    }

    private static boolean isArrayEnd(String operator) {
        return Objects.equals(operator, ARRAY_END);
    }

    private static boolean isTypeParametersStart(String operator) {
        return Objects.equals(operator, TYPE_PARAMETERS_START);
    }

    private static boolean isTypeParametersEnd(String operator) {
        return Objects.equals(operator, TYPE_PARAMETERS_END);
    }

    private static boolean isStatementEnd(String operator) {
        return Objects.equals(operator, STATEMENT_END);
    }

    private static boolean isArraySeparator(String operator) {
        return Objects.equals(operator, ARRAY_SEPARATOR);
    }

    private static boolean isRangeOperator(String operator) {
        return Objects.equals(operator, RANGE_OPERATOR);
    }

    private static boolean isValidType(String token) {
        var length = token.length();
        if(length == 0) {
            return false;
        }

        if(token.charAt(length - 1) == '.') {
            return false;
        }

        int start;
        if(token.charAt(0) == '.') {
            if(length == 1) {
                return false;
            }
            start = 1;
        }else {
            start = 0;
        }

        for(var end = start + 1; end < length; end++) {
            if (token.charAt(end) != '.') {
                continue;
            }

            if(!isValidIdent(token.substring(start, end))) {
                return false;
            }

            start = end + 1;
        }

        return isValidIdent(token.substring(start, length));
    }

    private static boolean isValidIdent(String token) {
        var length = token.length();
        if(length == 0) {
            return false;
        }

        var head = token.charAt(0);
        if(!Character.isLetter(head) && token.charAt(0) != '_') {
            return false;
        }

        for(var i = 1; i < length; i++) {
            var entry = token.charAt(i);
            if(!Character.isLetterOrDigit(entry) && entry != '_') {
                return false;
            }
        }

        return true;
    }

    private static boolean isNullExpression(String token) {
        return Objects.equals(token, NULL);
    }

    private static boolean isParensStart(String typeStart) {
        return Objects.equals(typeStart, PARENS_START);
    }

    private static boolean isParensEnd(String typeEnd) {
        return Objects.equals(typeEnd, PARENS_END);
    }

    private static boolean isStreamModifier(String typeOrModifier) {
        return Objects.equals(typeOrModifier, STREAM);
    }

    private static boolean isMax(String token) {
        return Objects.equals(token, MAX);
    }
}
