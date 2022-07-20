package it.auties.protobuf.tool.util;

import it.auties.protobuf.parser.statement.FieldStatement;
import lombok.experimental.UtilityClass;

import javax.lang.model.SourceVersion;
import java.util.Arrays;
import java.util.Iterator;
import java.util.stream.Collectors;

@UtilityClass
public class ProtobufUtils {
    private final String INDENT = "    ";

    public String indentLines(String input, int level){
        if(level == 0){
            return input;
        }

        return Arrays.stream(input.split("\n"))
                .map(entry -> indentLine(entry, level))
                .collect(Collectors.joining("\n"));
    }

    public String indentLine(String entry, int level) {
        return INDENT.repeat(level) + entry;
    }

    public String toValidIdentifier(String identifier) {
        return SourceVersion.isKeyword(identifier) ? "_%s".formatted(identifier) : identifier;
    }

    public String generateCondition(String oneOfName, Iterator<FieldStatement> statements) {
        var next = statements.next();
        var nextString = statements.hasNext() ? generateCondition(oneOfName, statements)
                : "return %s.UNKNOWN;".formatted(oneOfName);
        return """
                if(%s != null) {
                    return %s.%s;
                }
                
                %s
                """.formatted(next.name(), oneOfName, next.nameAsConstant(), nextString);
    }
}
