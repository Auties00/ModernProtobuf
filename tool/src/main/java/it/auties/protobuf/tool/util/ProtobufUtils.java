package it.auties.protobuf.tool.util;

import it.auties.protobuf.parser.statement.FieldStatement;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;

import javax.lang.model.SourceVersion;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Objects;

@UtilityClass
public class ProtobufUtils {
    @SneakyThrows
    public String readGenerator(String name) {
        try (var stream = ProtobufUtils.class.getClassLoader().getResourceAsStream("%s.java".formatted(name))) {
            return new String(Objects.requireNonNull(stream).readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    // This method is used inside the Message Model generator
    @SuppressWarnings("unused")
    public String toValidIdentifier(String identifier) {
        return SourceVersion.isKeyword(identifier) ? "_%s".formatted(identifier) : identifier;
    }

    // This method is used inside the Message Model generator
    @SuppressWarnings("unused")
    public String generateCondition(String oneOfName, Iterator<FieldStatement> statements) {
        var next = statements.next();
        return """
                if(%s != null) return %s.%s;
                %s
                """.formatted(next.getName(), oneOfName, next.getNameAsConstant(), statements.hasNext() ? generateCondition(oneOfName, statements) : "return %s.UNKNOWN;".formatted(oneOfName));
    }
}
