package it.auties.protobuf.parser.tree;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.SequencedCollection;
import java.util.stream.Collectors;

public final class ProtobufExtensionsStatement
        extends ProtobufStatementImpl
        implements ProtobufStatement,
                   ProtobufMessageChild, ProtobufEnumChild, ProtobufGroupChild, ProtobufExtendChild {
    private final List<ProtobufExtensionsExpression> expressions;

    public ProtobufExtensionsStatement(int line) {
        super(line);
        this.expressions = new ArrayList<>();
    }

    public SequencedCollection<ProtobufExtensionsExpression> expressions() {
        return Collections.unmodifiableSequencedCollection(expressions);
    }

    public void addExpression(ProtobufExtensionsExpression expression) {
        if(expression != null) {
            if(expression.hasParent()) {
                throw new IllegalStateException("Expression is already owned by another tree");
            }
            if(expression instanceof ProtobufExpressionImpl impl) {
                impl.setParent(this);
            }
            expressions.add(expression);
        }
    }

    public void removeExpression(ProtobufExtensionsExpression expression) {
        var result = expressions.remove(expression);
        if(result) {
            if(expression.parent() != this) {
                throw new IllegalStateException("Expression is not owned by this tree");
            }
            if(expression instanceof ProtobufExpressionImpl impl) {
                impl.setParent(null);
            }
        }
    }

    @Override
    public String toString() {
        var builder = new StringBuilder();

        builder.append("extensions");
        builder.append(" ");

        var values = expressions.stream()
                .map(ProtobufExpression::toString)
                .collect(Collectors.joining(", "));
        builder.append(values);

        builder.append(";");

        return builder.toString();
    }

    @Override
    public boolean isAttributed() {
        return !expressions.isEmpty() && expressions.stream()
                .allMatch(ProtobufExpression::isAttributed);
    }
}
