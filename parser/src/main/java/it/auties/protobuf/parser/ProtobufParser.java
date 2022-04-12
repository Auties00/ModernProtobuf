package it.auties.protobuf.parser;

import it.auties.protobuf.parser.exception.ProtobufSyntaxException;
import it.auties.protobuf.parser.model.*;
import lombok.AllArgsConstructor;

import java.io.File;
import java.io.IOException;
import java.io.StreamTokenizer;
import java.io.StringReader;
import java.nio.file.Files;
import java.util.*;

@AllArgsConstructor
public final class ProtobufParser {
    private static final Set<String> IGNORE = Set.of("syntax", "option", "package");
    private static final char STATEMENT_END = ';';
    private static final char OBJECT_START = '{';
    private static final char OBJECT_END = '}';

    private final StreamTokenizer tokenizer;
    private final LinkedList<String> tokensCache;
    private final Deque<ProtobufObject<?>> objectsQueue;
    public ProtobufParser(String input) {
        this(new StreamTokenizer(new StringReader(input)), new LinkedList<>(), new LinkedList<>());
    }

    public ProtobufParser(File input) throws IOException {
        this(Files.readString(input.toPath()));
    }

    public ProtobufDocument tokenizeAndParse() throws IOException {
        tokenizer.wordChars('_', '_');
        tokenizer.wordChars('"', '"');

        var results = new ArrayList<ProtobufObject<?>>();
        var token = -1;
        while ((token = tokenizer.nextToken()) != StreamTokenizer.TT_EOF) {
            if (token == STATEMENT_END) {
                parseField();
                continue;
            }

            if (token == OBJECT_START) {
                parseObjectStart();
                continue;
            }

            if (token == OBJECT_END) {
                parseObjectEnd(results);
                continue;
            }

            parseToken(token);
        }

        return new ProtobufDocument(results);
    }

    private void parseToken(int token) {
        var content = switch (token) {
            case StreamTokenizer.TT_WORD -> tokenizer.sval;
            case StreamTokenizer.TT_NUMBER -> String.valueOf((int) tokenizer.nval);
            default -> String.valueOf((char) token);
        };

        tokensCache.add(content);
    }

    private void parseObjectEnd(List<ProtobufObject<?>> results) {
        ProtobufSyntaxException.validate(!objectsQueue.isEmpty(),
                "Illegal character: cannot close a body that doesn't exist", tokensCache);
        ProtobufSyntaxException.validate(tokensCache.isEmpty(),
                "Illegal character: cannot close object with %s", tokensCache);
        var removed = objectsQueue.removeLast();
        if (objectsQueue.isEmpty()) {
            results.add(removed);
        }

        tokensCache.clear();
    }

    private void parseObjectStart() {
        ProtobufSyntaxException.validate(tokensCache.size() == 2,
                "Illegal object declaration: expected an instruction and a name", tokensCache);
        var instruction = tokensCache.getFirst();
        var name = tokensCache.getLast();
        var statement = switch (instruction) {
            case "message" -> new MessageStatement(name);
            case "oneof" -> new OneOfStatement(name);
            case "enum" -> new EnumStatement(name);
            default -> throw new ProtobufSyntaxException("Illegal object declaration: %s is not a valid instruction", tokensCache, instruction);
        };

        var last = objectsQueue.peekLast();
        if (last == null) {
            objectsQueue.add(statement);
            tokensCache.clear();
            return;
        }

        if (last instanceof MessageStatement messageStatement) {
            objectsQueue.add(statement);
            messageStatement.getStatements().add(statement);
            tokensCache.clear();
            return;
        }

        throw new ProtobufSyntaxException("Illegal object declaration: only messages can be nested", tokensCache);
    }

    private void parseField() {
        var header = tokensCache.peekFirst();
        if (header == null || IGNORE.contains(header)) {
            tokensCache.clear();
            return;
        }

        switch (tokensCache.size()) {
            case 3 -> parseEnumConstant(header);
            case 4, 5, 10 -> parseStandardField(header);
            default -> throw new ProtobufSyntaxException("Illegal field declaration: invalid instruction", tokensCache);
        }

        tokensCache.clear();
    }

    private void parseStandardField(String header) {
        var modifier = FieldModifier.forName(header);
        var offset = modifier.isPresent() ? 1 : 0;
        var operator = tokensCache.get(2 + offset);
        ProtobufSyntaxException.validate(isAssignmentOperator(operator),
                "Illegal field declaration: expected an assignment operator", tokensCache);

        var type = tokensCache.get(offset);
        var name = tokensCache.get(1 + offset);
        ProtobufSyntaxException.validate(isLegalEnumName(name),
                "Illegal field declaration: expected a non-empty name that doesn't start with a number", tokensCache);

        var index = parseIndex(tokensCache.get(3 + offset))
                .orElseThrow(() -> new ProtobufSyntaxException("Illegal field declaration: expected an unsigned index", tokensCache));

        var scope = objectsQueue.peekLast();
        if (scope instanceof MessageStatement messageStatement) {
            ProtobufSyntaxException.validate(modifier.isPresent(),
                    "Illegal field declaration: expected a valid modifier", tokensCache);
            var fieldStatement = new FieldStatement(name, type, index, modifier.get(), isPacked());
            messageStatement.getStatements()
                    .add(fieldStatement);
            return;
        }

        if (scope instanceof OneOfStatement oneOfStatement) {
            var oneOfOption = new FieldStatement(name, type, index, null, false);
            oneOfStatement.getStatements()
                    .add(oneOfOption);
            return;
        }

        throw new ProtobufSyntaxException("Illegal field declaration: invalid scope", tokensCache);
    }

    private void parseEnumConstant(String header) {
        ProtobufSyntaxException.validate(isLegalEnumName(header),
                "Illegal enum constant declaration: expected a non-empty name that doesn't start with a number", tokensCache);

        var operator = tokensCache.get(1);
        ProtobufSyntaxException.validate(isAssignmentOperator(operator),
                "Illegal enum constant declaration: expected an assignment operator", tokensCache);

        var context = objectsQueue.peekLast();
        if (!(context instanceof EnumStatement enumStatement)) {
            throw new ProtobufSyntaxException("Illegal enum constant declaration: invalid scope", tokensCache);
        }

        var index = parseIndex(tokensCache.get(2), true)
                .orElseThrow(() -> new ProtobufSyntaxException("Illegal enum constant declaration: expected an unsigned index", tokensCache));
        var constant = new EnumConstantStatement(header, index);
        enumStatement.getStatements()
                .add(constant);
    }

    private boolean isAssignmentOperator(String operator) {
        return Objects.equals(operator, "=");
    }

    private boolean isLegalEnumName(String instruction) {
        return !instruction.isBlank()
                && !instruction.isEmpty()
                && !Character.isDigit(instruction.charAt(0));
    }

    // Find a better way lol
    private boolean isPacked(){
        if(tokensCache.size() != 10){
            return false;
        }

        var groupStart = tokensCache.get(tokensCache.size() - 5);
        ProtobufSyntaxException.validate(Objects.equals(groupStart, "["),
                "Illegal options declaration: expected array start", tokensCache);

        var modifier = tokensCache.get(tokensCache.size() - 4);
        if(!Objects.equals(modifier, "packed")){
            return false;
        }

        var operator = tokensCache.get(tokensCache.size() - 3);
        if(!Objects.equals(operator, "=")){
            return false;
        }

        var value = tokensCache.get(tokensCache.size() - 2);
        if(!Objects.equals(value, "true")){
            return false;
        }

        var closeGroup = tokensCache.get(tokensCache.size() - 1);
        ProtobufSyntaxException.validate(Objects.equals(closeGroup, "]"),
                "Illegal options declaration: expected array end", tokensCache);
        return true;
    }

    private Optional<Integer> parseIndex(String parse){
        return parseIndex(parse, false);
    }

    private Optional<Integer> parseIndex(String parse, boolean acceptZero) {
        try {
            return Optional.of(Integer.parseUnsignedInt(parse))
                    .filter(value -> acceptZero || value != 0);
        } catch (NumberFormatException ex) {
            return Optional.empty();
        }
    }
}
