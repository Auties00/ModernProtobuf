package it.auties.protobuf.parser;

import it.auties.protobuf.model.ProtobufVersion;
import it.auties.protobuf.parser.tree.ProtobufTreeBody;
import it.auties.protobuf.parser.tree.*;
import it.auties.protobuf.parser.type.ProtobufObjectType;
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
    private static final Set<ProtobufDocument> BUILT_INS;
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
    private ProtobufDocument document;
    private ProtobufStatement tree;
    private StreamTokenizer tokenizer;

    public ProtobufParser() {
        this.parserLock = new ReentrantLock(true);
    }

    public Set<ProtobufDocument> parse(Path path) throws IOException {
        if (!Files.isDirectory(path)) {
            return Set.of(parseOnly(path));
        }

        try(var walker = Files.walk(path)) {
            var files = walker.filter(Files::isRegularFile).toList();
            var results = new HashSet<ProtobufDocument>();
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

    public ProtobufDocument parseOnly(String input) {
        var result = doParse(null, new StringReader(input));
        attributeStatement(result, result);
        return result;
    }

    private void attributeStatement(ProtobufDocument document, ProtobufTree tree) {
        // TODO: Attribute
    }

    public ProtobufDocument parseOnly(Path path) throws IOException {
        if (!Files.isRegularFile(path)) {
            throw new IllegalArgumentException("Expected file");
        }

        var result = doParse(path, Files.newBufferedReader(path));
        attributeImports(List.of(result));
        attributeStatement(result, result);
        return result;
    }

    private void attributeImports(Collection<ProtobufDocument> documents) {

    }

    private ProtobufDocument doParse(Path location, Reader input) {
        try {
            parserLock.lock();
            var document = new ProtobufDocument(location);
            this.document = document;
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
            tree = null;
            tokenizer = null;
            parserLock.unlock();
        }
    }

    private void handleToken(String token) {
        if(tree == null) {
            handleDocumentTree(document, token);
        }else {
            switch (tree) {
                case ProtobufPackage packageStatement -> handlePackageStatement(packageStatement, token);
                case ProtobufSyntax syntaxStatement -> handleSyntaxStatement(syntaxStatement, token);
                case ProtobufOption optionStatement -> handleOptionStatement(optionStatement, token);
                case ProtobufField fieldStatement -> handleFieldStatement(fieldStatement, token);
                case ProtobufImport importStatement -> handleImportStatement(importStatement, token);
                case ProtobufReserved reservedStatement -> handleReservedStatement(reservedStatement, token);
                case ProtobufExtension extensionStatement -> handleExtensionStatement(extensionStatement, token);
                case ProtobufEmptyStatement ignored -> {}
                case ProtobufEnum enumTree -> handleEnumTree(enumTree, token);
                case ProtobufMessage messageTree -> handleMessageTree(messageTree, token);
                case ProtobufOneof oneofTree -> handleOneofTree(oneofTree, token);
                case ProtobufService serviceTree -> handleServiceTree(serviceTree, token);
                case ProtobufMethod methodStatement -> handleMethodStatement(methodStatement, token);
                case ProtobufReservedList reservedListStatement -> handleReservedListStatement(token, reservedListStatement);
                case ProtobufExtensionsList extensionsListStatement -> handleExtensionsListStatement(token, extensionsListStatement);
            }
        }
    }

    private void handleMethodStatement(ProtobufMethod methodStatement, String token) {

    }

    private void handleExtensionsListStatement(String token, ProtobufExtensionsList extensionsStatement) {
        if(isStatementEnd(token)) {
            throw new ProtobufParserException("Unexpected token: " + token, tokenizer.lineno());
        }

        var index = parseIndex(token, false, false)
                .orElseThrow(() -> new ProtobufParserException("Unexpected token: " + token, tokenizer.lineno()));
        var extension = new ProtobufExtension(tokenizer.lineno(), extensionsStatement);
        var fieldIndex = new ProtobufExtension.Value.FieldIndex(index);
        extension.setValue(fieldIndex);
        tree = extension;
    }

    private void handleReservedListStatement(String token, ProtobufReservedList reservedStatement) {
        if(isStatementEnd(token)) {
            throw new ProtobufParserException("Unexpected token: " + token, tokenizer.lineno());
        }

        var reserved = new ProtobufReserved(tokenizer.lineno(), reservedStatement);
        tree = reserved;
        var index = parseIndex(token, false, false);
        if(index.isPresent()) {
            var value = new ProtobufReserved.Value.FieldIndex(index.get());
            reserved.setValue(value);
            return;
        }

        var literal = parseStringLiteral(token);
        if(literal.isPresent()) {
            var value = new ProtobufReserved.Value.FieldName(literal.get());
            reserved.setValue(value);
            return;
        }

        throw new ProtobufParserException("Unexpected token: " + token, tokenizer.lineno());
    }

    private void handleServiceTree(ProtobufService serviceTree, String token) {
        if(!serviceTree.hasName()) {
            serviceTree.setName(token);
        }else if(!serviceTree.hasBody()) {
            ProtobufParserException.check(isObjectStart(token),
                    "Unexpected token: " + token, tokenizer.lineno());
            var body = new ProtobufTreeBody<ProtobufServiceChild>(tokenizer.lineno(), false, serviceTree);
            serviceTree.setBody(body);
        } else if(isObjectEnd(token)) {
            jumpOutStatement();
        } else {
            this.tree = switch (token) {
                case STATEMENT_END -> new ProtobufEmptyStatement(tokenizer.lineno(), serviceTree);
                case "option" -> new ProtobufOption(tokenizer.lineno(), serviceTree);
                case "rpc" -> new ProtobufMethod(tokenizer.lineno(), serviceTree);
                default -> throw new ProtobufParserException("Unexpected token: " + token, tokenizer.lineno());
            };
        }
    }

    private void handleEnumTree(ProtobufEnum enumTree, String token) {
        if(!enumTree.hasName()) {
            enumTree.setName(token);
        }else if(!enumTree.hasBody()) {
            ProtobufParserException.check(isObjectStart(token),
                    "Unexpected token: " + token, tokenizer.lineno());
            var block = new ProtobufTreeBody<ProtobufEnumChild>(tokenizer.lineno(), false, enumTree);
            enumTree.setBody(block);
        } else if(isObjectEnd(token)) {
            jumpOutStatement();
        } else {
            this.tree = switch (token) {
                case STATEMENT_END -> new ProtobufEmptyStatement(tokenizer.lineno(), enumTree);
                case "extensions" -> new ProtobufExtensionsList(tokenizer.lineno(), enumTree);
                case "option" -> new ProtobufOption(tokenizer.lineno(), enumTree);
                case "reserved" -> new ProtobufReservedList(tokenizer.lineno(), enumTree);
                default -> {
                    var field = new ProtobufEnumConstant(tokenizer.lineno(), enumTree);
                    field.setModifier(ProtobufField.Modifier.nothing());
                    field.setType(ProtobufObjectType.of(enumTree.name(), enumTree));
                    field.setName(token);
                    yield field;
                }
            };
        }
    }

    private void handleMessageTree(ProtobufMessage messageTree, String token) {
        if(!messageTree.hasName()) {
            messageTree.setName(token);
        }else if(!messageTree.hasBody()) {
            ProtobufParserException.check(isObjectStart(token),
                    "Unexpected token: " + token, tokenizer.lineno());
            var block = new ProtobufTreeBody<ProtobufMessageChild>(tokenizer.lineno(), false, messageTree);
            messageTree.setBody(block);
        } else if(isObjectEnd(token)) {
            jumpOutStatement();
        } else {
            this.tree = switch (token) {
                case STATEMENT_END -> new ProtobufEmptyStatement(tokenizer.lineno(), messageTree);
                case "option" -> new ProtobufOption(tokenizer.lineno(), messageTree);
                case "message" -> new ProtobufMessage(tokenizer.lineno(), false, messageTree);
                case "enum" -> new ProtobufEnum(tokenizer.lineno(), messageTree);
                case "extend" -> new ProtobufMessage(tokenizer.lineno(), true, messageTree);
                case "extensions" -> new ProtobufExtensionsList(tokenizer.lineno(), messageTree);
                case "reserved" -> new ProtobufReservedList(tokenizer.lineno(), messageTree);
                default -> {
                    var field = new ProtobufField(tokenizer.lineno(), messageTree);
                    var modifier = ProtobufField.Modifier.of(token);
                    if(modifier.type() == ProtobufField.Modifier.Type.NOTHING) {
                        ProtobufParserException.check(currentVersion() == ProtobufVersion.PROTOBUF_3,
                                "Unexpected token: " + token, tokenizer.lineno());
                        var reference = ProtobufTypeReference.of(token);
                        field.setType(reference);
                    }
                    field.setModifier(modifier);
                    yield field;
                }
            };
        }
    }

    private void handleOneofTree(ProtobufOneof oneofTree, String token) {
        if(!oneofTree.hasName()) {
            oneofTree.setName(token);
        }else {
            ProtobufParserException.check(isObjectEnd(token),
                    "Unexpected token: " + token, tokenizer.lineno());
        }
    }

    private void handleDocumentTree(ProtobufDocument documentTree, String token) {
        this.tree = switch (token) {
            case STATEMENT_END -> new ProtobufEmptyStatement(tokenizer.lineno(), documentTree);
            case "package" -> new ProtobufPackage(tokenizer.lineno(), documentTree);
            case "syntax" -> new ProtobufSyntax(tokenizer.lineno(), documentTree);
            case "option" -> new ProtobufOption(tokenizer.lineno(), documentTree);
            case "message" -> new ProtobufMessage(tokenizer.lineno(), false, documentTree);
            case "enum" -> new ProtobufEnum(tokenizer.lineno(), documentTree);
            case "service" -> new ProtobufService(tokenizer.lineno(), documentTree);
            case "import" -> new ProtobufImport(tokenizer.lineno(), documentTree);
            case "extend" -> new ProtobufMessage(tokenizer.lineno(), true, documentTree);
            default -> throw new ProtobufParserException("Unexpected token: " + token, tokenizer.lineno());
        };
    }

    private void handleReservedStatement(ProtobufReserved reservedStatement, String token) {

    }

    private void handleExtensionStatement(ProtobufExtension extensionStatement, String token) {

    }

    private void handleImportStatement(ProtobufImport importStatement, String token) {
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

    private void handleFieldStatement(ProtobufField fieldStatement, String token) {
        if(!fieldStatement.hasModifier()) {
            var modifier = ProtobufField.Modifier.of(token);
            fieldStatement.setModifier(modifier);
        }else if(!fieldStatement.hasType()) {
            fieldStatement.setType(ProtobufTypeReference.of(token));
        } else if (!fieldStatement.hasName()) {
            fieldStatement.setName(token);
        }else if(!fieldStatement.hasIndex()) {
            ProtobufParserException.check(isAssignmentOperator(token),
                    "Unexpected token: " + token, tokenizer.lineno());
            fieldStatement.setIndex(ProtobufExpression.operator());
        } else if(fieldStatement.index() == ProtobufExpression.operator()) {
            var index = parseIndex(token, false, false)
                    .map(ProtobufExpression::value)
                    .orElseThrow(() -> new ProtobufParserException("Invalid index " + token, tokenizer.lineno()));
            fieldStatement.setIndex(index);
        }else {
            ProtobufParserException.check(isStatementEnd(token),
                    "Unexpected token: " + token, tokenizer.lineno());
            jumpOutStatement();
        }
    }

    private void handleOptionStatement(ProtobufOption optionStatement, String token) {
        if(!optionStatement.hasName()) {
            optionStatement.setName(token);
        }else if(!optionStatement.hasValue()) {
            ProtobufParserException.check(isAssignmentOperator(token),
                    "Unexpected token: " + token, tokenizer.lineno());
            optionStatement.setValue(op);
        } else if(optionStatement.value() == ASSIGNMENT_VALUE) {
            var literalValue = parseStringLiteral(token);
            if(literalValue.isPresent()) {
                var literalOption = new ProtobufOption.Value.Literal(literalValue.get());
                optionStatement.setValue(literalOption);
            }else {
                if(isObjectStart(token)) {
                    // raw map
                    throw new UnsupportedOperationException();
                }else {
                    var indexValue = parseIndex(token, true, false);
                    if(indexValue.isPresent()) {
                        var intOption = new ProtobufOption.Value.Int(indexValue.get());
                        optionStatement.setValue(intOption);
                    } else {
                        var boolValue = parseBool(token);
                        if(boolValue.isPresent()) {
                            var boolOption = new ProtobufOption.Value.Bool(boolValue.get());
                            optionStatement.setValue(boolOption);
                        }else {
                            var enumOption = new ProtobufOption.Value.Enum(token);
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

    private void handleSyntaxStatement(ProtobufSyntax syntaxStatement, String token) {
        if(!syntacticSugar.contains(ASSIGNMENT_OPERATOR)) {
            ProtobufParserException.check(isAssignmentOperator(token),
                    "Unexpected token: " + token, tokenizer.lineno());
            syntacticSugar.add(ASSIGNMENT_OPERATOR);
        }else if (!syntaxStatement.hasVersion()) {
            var literal = parseStringLiteral(token)
                    .orElseThrow(() -> new ProtobufParserException("Unexpected token: " + token, tokenizer.lineno()));
            var version = ProtobufVersion.of(literal)
                            .orElseThrow(() -> new ProtobufParserException("Unknown Protobuf version: " + token, tokenizer.lineno()));
            syntaxStatement.setVersion(version);
        } else {
            ProtobufParserException.check(isStatementEnd(token),
                    "Unexpected token: " + token, tokenizer.lineno());
            jumpOutStatement();
        }
    }

    private void handlePackageStatement(ProtobufPackage packageStatement, String token) {
        if (!packageStatement.hasName()) {
            packageStatement.setName(token);
        } else {
            ProtobufParserException.check(isStatementEnd(token),
                    "Unexpected token: " + token, tokenizer.lineno());
            jumpOutStatement();
        }
    }

    private void jumpOutStatement() {
        this.tree = tree.parent().owner();
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

    private ProtobufVersion currentVersion() {
        return document.syntax().orElse(ProtobufVersion.defaultVersion());
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
