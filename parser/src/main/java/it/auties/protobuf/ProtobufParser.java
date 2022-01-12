package it.auties.protobuf;

import it.auties.protobuf.*;
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
    
    private final StreamTokenizer tokenizer;
    private final List<String> tokensCache;
    private final Deque<ProtobufObject<?>> objectsQueue;
    public ProtobufParser(String input) {
        this(new StreamTokenizer(new StringReader(input)), new ArrayList<>(), new LinkedList<>());
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
            if (token == ';') {
                parseField();
                continue;
            }

            if (token == '{') {
                parseObjectStart();
                continue;
            }

            if (token == '}') {
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
        var last = objectsQueue.peekLast();
        if (last == null) {
            throw new IllegalArgumentException("Cannot close an object that doesn't exist");
        }

        var removed = objectsQueue.removeLast();
        if (objectsQueue.isEmpty()) {
            results.add(removed);
        }

        tokensCache.clear();
    }

    private void parseObjectStart() {
        if (tokensCache.size() != 2) {
            throw new IllegalArgumentException("Cannot parse %s as an object".formatted(tokensCache));
        }

        var instruction = tokensCache.get(0);
        var name = tokensCache.get(1);
        var statement = switch (instruction) {
            case "message" -> new MessageStatement(name);
            case "oneof" -> new OneOfStatement(name);
            case "enum" -> new EnumStatement(name);
            default -> throw new IllegalArgumentException("%s is not a valid instruction for an object".formatted(tokensCache.get(0)));
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

        throw new IllegalArgumentException("Cannot put object %s in an object that isn't a message".formatted(tokensCache));
    }

    private void parseField() {
        if (tokensCache.isEmpty()) {
            return;
        }

        var instruction = tokensCache.get(0);
        if (IGNORE.contains(instruction)) {
            tokensCache.clear();
            return;
        }

        switch (tokensCache.size()) {
            case 3 -> {
                if (instruction.isBlank() || Character.isDigit(instruction.charAt(0))) {
                    throw new IllegalArgumentException("Cannot parse %s as an enum's field: invalid name".formatted(tokensCache));
                }

                var operator = tokensCache.get(1);
                if (!operator.equals("=")) {
                    throw new IllegalArgumentException("Cannot parse %s as an enum's field: assignment operator expected".formatted(tokensCache));
                }

                var index = parseIndex(tokensCache.get(2));
                if (!(objectsQueue.peekLast() instanceof EnumStatement enumStatement)) {
                    throw new IllegalArgumentException("Cannot parse %s as an enum's field: invalid scope".formatted(tokensCache));
                }

                enumStatement.getStatements().add(new EnumConstantStatement(instruction, index));
            }

            case 4, 5, 10 -> {
                var modifier = FieldModifier.forName(instruction);
                var offset = modifier.isPresent() ? 1 : 0;
                var operator = tokensCache.get(2 + offset);
                if (!operator.equals("=")) {
                    throw new IllegalArgumentException("Cannot parse %s as a regular field: assignment operator expected".formatted(tokensCache));
                }

                var type = tokensCache.get(offset);
                var name = tokensCache.get(1 + offset);
                if (name.isBlank() || Character.isDigit(name.charAt(0))) {
                    throw new IllegalArgumentException("Cannot parse %s as a regular field: invalid name".formatted(tokensCache));
                }

                var index = parseIndex(tokensCache.get(3 + offset));
                var scope = objectsQueue.peekLast();
                if (scope instanceof MessageStatement messageStatement) {
                    var checkedModifier = modifier.orElseThrow(() -> new IllegalArgumentException("Cannot parse %s as a regular field: invalid modifier".formatted(tokensCache)));
                    var fieldStatement = new FieldStatement(name, type, index, checkedModifier, isPacked());
                    messageStatement.getStatements().add(fieldStatement);
                    break;
                }

                if (scope instanceof OneOfStatement oneOfStatement) {
                    if (modifier.isPresent()) {
                        throw new IllegalArgumentException("Cannot parse %s as an enum's field: invalid name".formatted(tokensCache));
                    }

                    var oneOfOption = new FieldStatement(name, type, index, null, false);
                    oneOfStatement.getStatements().add(oneOfOption);
                    break;
                }

                throw new IllegalArgumentException("Cannot parse %s as a regular field: invalid scope".formatted(tokensCache));
            }

            default -> throw new IllegalArgumentException("Cannot parse %s as a regular field: invalid instruction".formatted(tokensCache));
        }

        tokensCache.clear();
    }

    private boolean isPacked(){
        if(tokensCache.size() != 10){
            return false;
        }

        var groupStart = tokensCache.get(tokensCache.size() - 5);
        if(!Objects.equals(groupStart, "[")){
            throw new IllegalArgumentException("Cannot parse %s as a regular field: invalid token".formatted(tokensCache));
        }

        var modifier = tokensCache.get(tokensCache.size() - 4);
        if(!Objects.equals(modifier, "packed")){
            return false;
        }

        var operator = tokensCache.get(tokensCache.size() - 3);
        if(!Objects.equals(operator, "packed")){
            return false;
        }

        var value = tokensCache.get(tokensCache.size() - 2);
        if(!Objects.equals(value, "true")){
            return false;
        }

        var closeGroup = tokensCache.get(tokensCache.size() - 1);
        if(!Objects.equals(closeGroup, "[")){
            throw new IllegalArgumentException("Cannot parse %s as a regular field: invalid token".formatted(tokensCache));
        }

        return true;
    }

    private int parseIndex(String parse) {
        try {
            return Integer.parseUnsignedInt(parse);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Cannot parse %s as a regular field, invalid index".formatted(parse), ex);
        }
    }
}
