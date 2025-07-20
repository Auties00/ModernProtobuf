package it.auties.protobuf.parser.tree;

import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class ProtobufImportStatement
        extends ProtobufStatementImpl
        implements ProtobufStatement,
                   ProtobufDocumentChild {
    private Modifier modifier;
    private String location;
    private ProtobufDocumentTree document;

    public ProtobufImportStatement(int line) {
        super(line);
    }

    public Modifier modifier() {
        return modifier;
    }

    public boolean hasModifier() {
        return modifier != null;
    }

    public void setModifier(Modifier modifier) {
        this.modifier = modifier;
    }

    public String location() {
        return location;
    }

    public boolean hasLocation() {
        return location != null;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public ProtobufDocumentTree document() {
        return document;
    }

    public boolean hasDocument() {
        return document != null;
    }

    public void setDocument(ProtobufDocumentTree document) {
        this.document = document;
    }

    @Override
    public String toString() {
        var builder = new StringBuilder();
        builder.append("import");
        if(modifier != Modifier.NONE) {
            builder.append(" ");
            builder.append(modifier.token());
        }
        builder.append(" \"");
        builder.append(Objects.requireNonNullElse(location, "[missing]"));
        builder.append("\"");
        builder.append(';');
        return builder.toString();
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(location);
    }

    @Override
    public boolean equals(Object obj) {
        return this == obj || obj instanceof ProtobufImportStatement that
                && Objects.equals(this.location(), that.location());
    }

    @Override
    public boolean isAttributed() {
        return hasDocument();
    }

    public enum Modifier {
        NONE(""),
        PUBLIC("public"),
        WEAK("weak");

        private final String token;

        Modifier(String token) {
            this.token = token;
        }

        public String token() {
            return token;
        }

        private static final Map<String, Modifier> VALUES = Arrays.stream(values())
                .collect(Collectors.toUnmodifiableMap(Modifier::token, Function.identity()));

        public static Optional<Modifier> of(String name) {
            return Optional.ofNullable(VALUES.get(name));
        }
    }
}
