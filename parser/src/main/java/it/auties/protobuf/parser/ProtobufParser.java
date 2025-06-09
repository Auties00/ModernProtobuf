package it.auties.protobuf.parser;

import it.auties.protobuf.model.ProtobufVersion;
import it.auties.protobuf.parser.tree.*;
import it.auties.protobuf.parser.type.ProtobufTypeReference;

import java.io.IOException;
import java.io.Reader;
import java.io.StreamTokenizer;
import java.io.StringReader;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;

public final class ProtobufParser {
    private static final Set<ProtobufDocumentTree> BUILT_INS;
    private static final Set<Character> SYMBOLS = Set.of('@', '!', '(', ')');
    private static final String STATEMENT_END = ";";
    private static final String OBJECT_START = "{";
    private static final String OBJECT_END = "}";
    private static final String ASSIGNMENT_OPERATOR = "=";
    private static final String ARRAY_START = "[";
    private static final String ARRAY_END = "]";
    private static final String LIST_SEPARATOR = ",";
    private static final String RANGE_OPERATOR = "to";
    private static final String MAX_KEYWORD = "max";
    private static final String TYPE_SELECTOR = ".";
    private static final String TYPE_SELECTOR_SPLITTER = "\\.";
    private static final String TYPE_PARAMETERS_START = "<";
    private static final String TYPE_PARAMETERS_END = ">";
    private static final char STRING_LITERAL_DELIMITER = '"';
    private static final String STRING_LITERAL = "\"";
    private static final String STRING_LITERAL_ALIAS_CHAR = "'";
    private static final String MAP_TYPE = "map";
    private static final String DEFAULT_KEYWORD_OPTION = "default";

    static {
        var parser = new ProtobufParser();
        try {
            var builtInTypesDirectory = ClassLoader.getSystemClassLoader().getResource("google/protobuf/");
            if(builtInTypesDirectory == null) {
                throw new ProtobufParserException("Missing built-in .proto");
            }

            var builtInTypesPath = Path.of(builtInTypesDirectory.toURI());
            BUILT_INS = parser.parse(builtInTypesPath);
        }catch (IOException | URISyntaxException exception) {
            throw new ProtobufParserException("Missing built-in .proto");
        }
    }

    private final ReentrantLock parserLock;
    private ProtobufDocumentTree document;
    private ProtobufStatement statement;
    private StreamTokenizer tokenizer;
    private final Set<String> syntacticSugar;

    public ProtobufParser() {
        this.parserLock = new ReentrantLock(true);
        this.syntacticSugar = new HashSet<>();
    }

    public Set<ProtobufDocumentTree> parse(Path path) throws IOException {
        if (!Files.isDirectory(path)) {
            return Set.of(parseOnly(path));
        }

        try(var walker = Files.walk(path)) {
            var files = walker.filter(Files::isRegularFile).toList();
            var results = new HashSet<ProtobufDocumentTree>();
            for(var file : files) {
                var parsed = doParse(file, Files.newBufferedReader(file));
                if(!results.add(parsed)) {
                    throw new ProtobufParserException("Duplicate file: " + file);
                }
            }

            attributeImports(results);
            for (var statement : results) {
                attributeStatement(statement, statement);
            }
            return results;
        }
    }

    public ProtobufDocumentTree parseOnly(String input) {
        var result = doParse(null, new StringReader(input));
        attributeStatement(result, result);
        return result;
    }

    private void attributeStatement(ProtobufDocumentTree document, ProtobufStatement statement) {
        // TODO: Attribute
    }

    public ProtobufDocumentTree parseOnly(Path path) throws IOException {
        if (!Files.isRegularFile(path)) {
            throw new IllegalArgumentException("Expected file");
        }

        var result = doParse(path, Files.newBufferedReader(path));
        attributeImports(List.of(result));
        attributeStatement(result, result);
        return result;
    }

    private void attributeImports(Collection<ProtobufDocumentTree> documents) {

    }

    private ProtobufDocumentTree doParse(Path location, Reader input) {
        try {
            parserLock.lock();
            var document = new ProtobufDocumentTree(location);
            this.document = document;
            this.statement = document;
            this.tokenizer = new StreamTokenizer(input);
            tokenizer.resetSyntax();
            tokenizer.wordChars('a', 'z');
            tokenizer.wordChars('A', 'Z');
            tokenizer.wordChars(128 + 32, 255);
            tokenizer.wordChars('_', '_');
            tokenizer.wordChars('"', '"');
            tokenizer.wordChars('\'', '\'');
            tokenizer.wordChars('.', '.');
            tokenizer.wordChars('-', '-');
            for (int i = '0'; i <= '9'; i++) {
                tokenizer.wordChars(i, i);
            }
            tokenizer.whitespaceChars(0, ' ');
            tokenizer.commentChar('/');
            tokenizer.quoteChar(STRING_LITERAL_DELIMITER);
            String token;
            while ((token = nextToken()) != null) {
                handleToken(token);
            }
            return document;
        } catch (ProtobufParserException syntaxException) {
            throw ProtobufParserException.wrap(syntaxException, location);
        } finally {
            document = null;
            statement = null;
            tokenizer = null;
            syntacticSugar.clear();
            parserLock.unlock();
        }
    }

    private void handleToken(String token) {
        switch (statement) {
            case ProtobufPackageStatement packageStatement -> handlePackageStatement(packageStatement, token);
            case ProtobufSyntaxStatement syntaxStatement -> handleSyntaxStatement(syntaxStatement, token);
            case ProtobufOptionStatement optionStatement -> handleOptionStatement(optionStatement, token);
            case ProtobufFieldStatement fieldStatement -> handleFieldStatement(fieldStatement, token);
            case ProtobufImportStatement importStatement -> handleImportStatement(importStatement, token);
            case ProtobufReservedStatement<?> reservedStatement -> handleReservedStatement(reservedStatement, token);
            case ProtobufExtensionStatement extensionStatement -> handleExtensionStatement(extensionStatement, token);
            case ProtobufBlock<?> protobufBlock -> handleBlockStatement(token, protobufBlock);
            case ProtobufEmptyStatement ignored -> {}
        }
    }

    private void handleBlockStatement(String token, ProtobufBlock<?> protobufBlock) {
        switch (protobufBlock) {
            case ProtobufReservedListStatement reservedListStatement -> handleReservedListStatement(token, reservedListStatement);
            case ProtobufExtensionsListStatement extensionsListStatement -> handleExtensionsListStatement(token, extensionsListStatement);
            case ProtobufNamedBlock<?> protobufNamedBlock -> handleNamedBlock(token, protobufNamedBlock);
            case ProtobufDocumentTree documentTree -> handleDocumentTree(documentTree, token);
        }
    }

    private void handleExtensionsListStatement(String token, ProtobufExtensionsListStatement extensionsStatement) {
        if(isStatementEnd(token)) {
            ProtobufParserException.check(!extensionsStatement.children().isEmpty(),
                    "Unexpected token: " + token, tokenizer.lineno());
        }else {
            var index = parseIndex(token, false, false)
                    .orElseThrow(() -> new ProtobufParserException("Unexpected token: " + token));
            var extensionTree = new ProtobufExtensionStatement.Value(tokenizer.lineno());
            extensionTree.setValue(index);
            extensionsStatement.addChild(extensionTree);
            statement = extensionTree;
        }
    }

    private void handleReservedListStatement(String token, ProtobufReservedListStatement reservedStatement) {
        if(isStatementEnd(token)) {
            ProtobufParserException.check(!reservedStatement.children().isEmpty(),
                    "Unexpected token: " + token, tokenizer.lineno());
        }else {
            var index = parseIndex(token, false, false);
            if(index.isPresent()) {
                var value = new ProtobufReservedStatement.FieldIndex.Value(tokenizer.lineno());
                value.setValue(index.get());
                reservedStatement.addChild(value);
                statement = value;
            } else {
                var literal = parseStringLiteral(token)
                        .orElseThrow(() -> new ProtobufParserException("Unexpected token: " + token));
                var value = new ProtobufReservedStatement.FieldName(tokenizer.lineno());
                value.setValue(literal);
                reservedStatement.addChild(value);
                statement = value;
            }
        }
    }

    private void handleNamedBlock(String token, ProtobufNamedBlock<?> protobufNamedBlock) {
        switch (protobufNamedBlock) {
            case ProtobufEnumTree enumTree -> handleEnumTree(token, enumTree);
            case ProtobufGroupTree groupTree -> handleGroupTree(token, groupTree);
            case ProtobufMessageTree messageTree -> handleMessageTree(token, messageTree);
            case ProtobufOneofTree oneofTree -> handleOneofTree(oneofTree, token);
        }
    }

    private void handleEnumTree(String token, ProtobufEnumTree enumTree) {
        if(!enumTree.hasName()) {
            enumTree.setName(token);
        }else {
            ProtobufParserException.check(isObjectEnd(token),
                    "Unexpected token: " + token, tokenizer.lineno());
        }
    }

    private void handleGroupTree(String token, ProtobufGroupTree groupTree) {
        if(!groupTree.hasName()) {
            groupTree.setName(token);
        }else {
            ProtobufParserException.check(isObjectEnd(token),
                    "Unexpected token: " + token, tokenizer.lineno());
        }
    }

    private void handleMessageTree(String token, ProtobufMessageTree messageTree) {
        if(!messageTree.hasName()) {
            messageTree.setName(token);
        }else if(messageTree.children().isEmpty() && !syntacticSugar.contains(OBJECT_START)) {
            ProtobufParserException.check(isObjectStart(token),
                    "Unexpected token: " + token, tokenizer.lineno());
            syntacticSugar.add(OBJECT_START);
        } else if(isObjectEnd(token)) {
            jumpOutStatement();
        } else {
            this.statement = switch (token) {
                case "option" -> {
                    var optionStatement = new ProtobufOptionStatement(tokenizer.lineno());
                    messageTree.addChild(optionStatement);
                    yield optionStatement;
                }

                case "message" -> {
                    var messageStatement = new ProtobufMessageTree(tokenizer.lineno(), false);
                    messageTree.addChild(messageStatement);
                    yield messageStatement;
                }

                case "enum" -> {
                    var enumTree = new ProtobufEnumTree(tokenizer.lineno());
                    messageTree.addChild(enumTree);
                    yield enumTree;
                }

                case "extend" -> {
                    var extendTree = new ProtobufMessageTree(tokenizer.lineno(), true);
                    messageTree.addChild(extendTree);
                    yield extendTree;
                }

                case "extensions" -> {
                    var extensionsTree = new ProtobufExtensionsListStatement(tokenizer.lineno());
                    messageTree.addChild(extensionsTree);
                    yield extensionsTree;
                }

                case "reserved" -> {
                    var reservedListTree = new ProtobufReservedListStatement(tokenizer.lineno());
                    messageTree.addChild(reservedListTree);
                    yield reservedListTree;
                }

                case STATEMENT_END -> {
                    var emptyStatement = new ProtobufEmptyStatement(tokenizer.lineno());
                    messageTree.addChild(emptyStatement);
                    yield statement; // Ignore emptyStatement
                }

                default -> {
                    var field = new ProtobufFieldStatement(tokenizer.lineno());
                    var modifier = ProtobufFieldModifier.of(token);
                    if(modifier.type() == ProtobufFieldModifier.Type.NOTHING) {
                        ProtobufParserException.check(currentVersion() == ProtobufVersion.PROTOBUF_3,
                                "Unexpected token: " + token, tokenizer.lineno());
                        var reference = ProtobufTypeReference.of(token);
                        field.setType(reference);
                    }
                    field.setModifier(modifier);
                    messageTree.addChild(field);
                    yield field;
                }
            };
        }
    }

    private ProtobufVersion currentVersion() {
        return document.syntax().orElse(ProtobufVersion.defaultVersion());
    }

    private void handleOneofTree(ProtobufOneofTree oneofTree, String token) {
        if(!oneofTree.hasName()) {
            oneofTree.setName(token);
        }else {
            ProtobufParserException.check(isObjectEnd(token),
                    "Unexpected token: " + token, tokenizer.lineno());
        }
    }

    private void handleDocumentTree(ProtobufDocumentTree documentTree, String token) {
        this.statement = switch (token) {
            case "package" -> {
                var packageStatement = new ProtobufPackageStatement(tokenizer.lineno());
                documentTree.addChild(packageStatement);
                yield packageStatement;
            }

            case "syntax" -> {
                var syntaxStatement = new ProtobufSyntaxStatement(tokenizer.lineno());
                documentTree.addChild(syntaxStatement);
                yield syntaxStatement;
            }

            case "option" -> {
                var optionStatement = new ProtobufOptionStatement(tokenizer.lineno());
                documentTree.addChild(optionStatement);
                yield optionStatement;
            }

            case "message" -> {
                var messageStatement = new ProtobufMessageTree(tokenizer.lineno(), false);
                documentTree.addChild(messageStatement);
                yield messageStatement;
            }

            case "enum" -> {
                var enumTree = new ProtobufEnumTree(tokenizer.lineno());
                documentTree.addChild(enumTree);
                yield enumTree;
            }

            case "service" -> {
                throw new UnsupportedOperationException();
            }

            case "import" -> {
                var importTree = new ProtobufImportStatement(tokenizer.lineno());
                documentTree.addChild(importTree);
                yield importTree;
            }

            case "extend" -> {
                var extendTree = new ProtobufMessageTree(tokenizer.lineno(), true);
                documentTree.addChild(extendTree);
                yield extendTree;
            }

            case STATEMENT_END -> {
                var emptyStatement = new ProtobufEmptyStatement(tokenizer.lineno());
                documentTree.addChild(emptyStatement);
                yield statement; // Ignore emptyStatement
            }

            default -> throw new ProtobufParserException("Unexpected token: " + token, tokenizer.lineno());
        };
    }

    private void handleReservedStatement(ProtobufReservedStatement<?> reservedStatement, String token) {
        switch (reservedStatement) {
            case ProtobufReservedStatement.FieldIndex fieldIndex -> handleReservedIndexStatement(fieldIndex, token);
            case ProtobufReservedStatement.FieldName fieldName -> handleReservedNameStatement(fieldName, token);
        }
    }

    private void handleReservedIndexStatement(ProtobufReservedStatement.FieldIndex fieldIndex, String token) {
        switch (fieldIndex) {
            case ProtobufReservedStatement.FieldIndex.Range fieldIndexRange -> handleReservedIndexRangeStatement(fieldIndexRange, token);
            case ProtobufReservedStatement.FieldIndex.Value fieldIndexValue -> handleReservedIndexValueStatement(fieldIndexValue, token);
        }
    }

    private void handleReservedIndexRangeStatement(ProtobufReservedStatement.FieldIndex.Range fieldIndexRange, String token) {
        if(!fieldIndexRange.hasMin()) {
            var index = parseIndex(token, false, false)
                    .orElseThrow(() -> new ProtobufParserException("Invalid field index: " + token, tokenizer.lineno()));
            fieldIndexRange.setMin(index);
        }else if(!fieldIndexRange.hasMax()) {
            var index = parseIndex(token, false, true)
                    .orElseThrow(() -> new ProtobufParserException("Invalid field index: " + token, tokenizer.lineno()));
            fieldIndexRange.setMax(index);
        }else {
            ProtobufParserException.check(isStatementEnd(token),
                    "Unexpected token: " + token, tokenizer.lineno());
            jumpOutStatement();
        }
    }

    private void handleReservedIndexValueStatement(ProtobufReservedStatement.FieldIndex.Value fieldIndexValue, String token) {
        if(!fieldIndexValue.hasValue()) {
            var index = parseIndex(token, false, false)
                    .orElseThrow(() -> new ProtobufParserException("Invalid field index: " + token, tokenizer.lineno()));
            fieldIndexValue.setValue(index);
        } else if(isRangeOperator(token)) {
            var parent = (ProtobufReservedListStatement) fieldIndexValue.parent();
            parent.removeChild();
            var range = new ProtobufReservedStatement.FieldIndex.Range(fieldIndexValue.line());
            range.setMin(fieldIndexValue.value());
            parent.addChild(range);
            statement = range;
        } else {
            ProtobufParserException.check(isStatementEnd(token),
                    "Unexpected token: " + token, tokenizer.lineno());
            jumpOutStatement();
        }
    }

    private void handleReservedNameStatement(ProtobufReservedStatement.FieldName fieldName, String token) {
        if(!fieldName.hasValue()) {
            jumpOutStatement();
        }else {
            ProtobufParserException.check(isStatementEnd(token),
                    "Unexpected token: " + token, tokenizer.lineno());
            fieldName.setValue(token);
        }
    }

    private void handleExtensionStatement(ProtobufExtensionStatement extensionStatement, String token) {
        switch (extensionStatement) {
            case ProtobufExtensionStatement.Range fieldIndexRange -> handleExtensionIndexRangeStatement(token, fieldIndexRange);
            case ProtobufExtensionStatement.Value fieldIndexValues -> handleExtensionIndexValueStatement(token, fieldIndexValues);
        }
    }

    private void handleExtensionIndexRangeStatement(String token, ProtobufExtensionStatement.Range fieldIndexRange) {
        if(!fieldIndexRange.hasMin()) {
            var index = parseIndex(token, false, false)
                    .orElseThrow(() -> new ProtobufParserException("Invalid field index: " + token, tokenizer.lineno()));
            fieldIndexRange.setMin(index);
        }else if(!fieldIndexRange.hasMax()) {
            var index = parseIndex(token, false, true)
                    .orElseThrow(() -> new ProtobufParserException("Invalid field index: " + token, tokenizer.lineno()));
            fieldIndexRange.setMax(index);
        }else {
            ProtobufParserException.check(isStatementEnd(token),
                    "Unexpected token: " + token, tokenizer.lineno());
            jumpOutStatement();
        }
    }


    private void handleExtensionIndexValueStatement(String token, ProtobufExtensionStatement.Value fieldIndexValue) {
        if(isStatementEnd(token)) {
            ProtobufParserException.check(fieldIndexValue.hasValue(),
                    "Unexpected token: " + token, tokenizer.lineno());
            jumpOutStatement();
            jumpOutStatement();
        } else if(isListSeparator(token)) {
            ProtobufParserException.check(fieldIndexValue.hasValue(),
                    "Unexpected token: " + token, tokenizer.lineno());
            jumpOutStatement();
        } else if(isRangeOperator(token)) {
            ProtobufParserException.check(fieldIndexValue.hasValue(),
                    "Unexpected token: " + token, tokenizer.lineno());
            var parent = (ProtobufExtensionsListStatement) fieldIndexValue.parent();
            parent.removeChild();
            var range = new ProtobufExtensionStatement.Range(fieldIndexValue.line());
            range.setMin(fieldIndexValue.value());
            parent.addChild(range);
            statement = range;
        } else {
            var index = parseIndex(token, false, false)
                    .orElseThrow(() -> new ProtobufParserException("Invalid field index: " + token, tokenizer.lineno()));
            fieldIndexValue.setValue(index);
        }
    }

    private void handleImportStatement(ProtobufImportStatement importStatement, String token) {
        if (!importStatement.hasLocation()) {
            var literal = parseStringLiteral(token)
                    .orElseThrow(() -> new ProtobufParserException("Unexpected token: " + token, tokenizer.lineno()));
            importStatement.setLocation(literal);
        } else {
            ProtobufParserException.check(isStatementEnd(token),
                    "Unexpected token: " + token, tokenizer.lineno());
            jumpOutStatement();
        }
    }

    private void handleFieldStatement(ProtobufFieldStatement fieldStatement, String token) {
        if(!fieldStatement.hasModifier()) {
            var modifier = ProtobufFieldModifier.of(token);
            fieldStatement.setModifier(modifier);
        }else if(!fieldStatement.hasType()) {
            fieldStatement.setType(ProtobufTypeReference.of(token));
        } else if (!fieldStatement.hasName()) {
            fieldStatement.setName(token);
        }else if(!syntacticSugar.contains(ASSIGNMENT_OPERATOR)) {
            ProtobufParserException.check(isAssignmentOperator(token),
                    "Unexpected token: " + token, tokenizer.lineno());
            syntacticSugar.add(ASSIGNMENT_OPERATOR);
        } else if(!fieldStatement.hasIndex()) {
            var index = parseIndex(token, false, false)
                    .orElseThrow(() -> new ProtobufParserException("Invalid index " + token, tokenizer.lineno()));
            fieldStatement.setIndex(index);
        }else {
            ProtobufParserException.check(isStatementEnd(token),
                    "Unexpected token: " + token, tokenizer.lineno());
            jumpOutStatement();
        }
    }

    private void handleOptionStatement(ProtobufOptionStatement optionStatement, String token) {
        if(!optionStatement.hasName()) {
            optionStatement.setName(token);
        }else if(!syntacticSugar.contains(ASSIGNMENT_OPERATOR)) {
            ProtobufParserException.check(isAssignmentOperator(token),
                    "Unexpected token: " + token, tokenizer.lineno());
            syntacticSugar.add(ASSIGNMENT_OPERATOR);
        } else if(!optionStatement.hasValue()) {
            var literalValue = parseStringLiteral(token);
            if(literalValue.isPresent()) {
                var literalOption = new ProtobufOptionValue.Literal(literalValue.get());
                optionStatement.setValue(literalOption);
            }else {
                if(isObjectStart(token)) {
                    // raw map
                    throw new UnsupportedOperationException();
                }else {
                    var indexValue = parseIndex(token, true, false);
                    if(indexValue.isPresent()) {
                        var intOption = new ProtobufOptionValue.Int(indexValue.get());
                        optionStatement.setValue(intOption);
                    } else {
                        var boolValue = parseBool(token);
                        if(boolValue.isPresent()) {
                            var boolOption = new ProtobufOptionValue.Bool(boolValue.get());
                            optionStatement.setValue(boolOption);
                        }else {
                            var enumOption = new ProtobufOptionValue.Enum(token);
                            optionStatement.setValue(enumOption);
                        }
                    }
                }
            }
        } else {
            ProtobufParserException.check(isStatementEnd(token),
                    "Unexpected token: " + token, tokenizer.lineno());
            jumpOutStatement();
        }
    }

    private void handleSyntaxStatement(ProtobufSyntaxStatement syntaxStatement, String token) {
        if(!syntacticSugar.contains(ASSIGNMENT_OPERATOR)) {
            ProtobufParserException.check(isAssignmentOperator(token),
                    "Unexpected token: " + token, tokenizer.lineno());
            syntacticSugar.add(ASSIGNMENT_OPERATOR);
        }else if (!syntaxStatement.hasVersion()) {
            var version = ProtobufVersion.of(token)
                            .orElseThrow(() -> new ProtobufParserException("Unknown Protobuf version: " + token, tokenizer.lineno()));
            syntaxStatement.setVersion(version);
        } else {
            ProtobufParserException.check(isStatementEnd(token),
                    "Unexpected token: " + token, tokenizer.lineno());
            jumpOutStatement();
        }
    }

    private void handlePackageStatement(ProtobufPackageStatement packageStatement, String token) {
        if (!packageStatement.hasName()) {
            packageStatement.setName(token);
        } else {
            ProtobufParserException.check(isStatementEnd(token),
                    "Unexpected token: " + token, tokenizer.lineno());
            jumpOutStatement();
        }
    }

    private void jumpOutStatement() {
        this.statement = statement.parent();
        syntacticSugar.clear();
    }

    private Optional<String> parseStringLiteral(String token) {
        if ((token.startsWith(STRING_LITERAL) && token.endsWith(STRING_LITERAL)) || (token.startsWith(STRING_LITERAL_ALIAS_CHAR) && token.endsWith(STRING_LITERAL_ALIAS_CHAR))) {
            return Optional.of(token.substring(1, token.length() - 1));
        } else {
            return Optional.empty();
        }
    }

    private boolean isAssignmentOperator(String operator) {
        return Objects.equals(operator, ASSIGNMENT_OPERATOR);
    }

    private boolean isObjectStart(String operator) {
        return Objects.equals(operator, OBJECT_START);
    }

    private boolean isObjectEnd(String operator) {
        return Objects.equals(operator, OBJECT_END);
    }

    private boolean isStatementEnd(String operator) {
        return Objects.equals(operator, STATEMENT_END);
    }

    private boolean isListSeparator(String operator) {
        return Objects.equals(operator, LIST_SEPARATOR);
    }

    private boolean isRangeOperator(String operator) {
        return Objects.equals(operator, RANGE_OPERATOR);
    }

    private boolean isLegalIdentifier(String instruction) {
        return !instruction.isBlank()
                && !Character.isDigit(instruction.charAt(0))
                && instruction.chars().mapToObj(entry -> (char) entry).noneMatch(SYMBOLS::contains);
    }

    private String nextToken() {
        try {
            var token = tokenizer.nextToken();
            if (token == StreamTokenizer.TT_EOF) {
                return null;
            }

            return switch (token) {
                case StreamTokenizer.TT_WORD -> tokenizer.sval;
                case STRING_LITERAL_DELIMITER -> STRING_LITERAL + tokenizer.sval + STRING_LITERAL;
                case StreamTokenizer.TT_NUMBER -> String.valueOf((int) tokenizer.nval);
                default -> String.valueOf((char) token);
            };
        } catch (IOException exception) {
            return null;
        }
    }

    private Optional<Integer> parseIndex(String input, boolean acceptZero, boolean acceptMax) {
        try {
            if(acceptMax && Objects.equals(input, MAX_KEYWORD)) {
                return Optional.of(Integer.MAX_VALUE);
            }

            return Optional.of(Integer.parseUnsignedInt(input))
                    .filter(value -> acceptZero || value != 0);
        } catch (NumberFormatException ex) {
            return Optional.empty();
        }
    }

    private Optional<Boolean> parseBool(String token) {
        return switch (token) {
            case "true" -> Optional.of(true);
            case "false" -> Optional.of(false);
            default -> Optional.empty();
        };
    }
}
