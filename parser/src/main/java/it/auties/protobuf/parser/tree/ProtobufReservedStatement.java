package it.auties.protobuf.parser.tree;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.SequencedCollection;
import java.util.stream.Collectors;

public final class ProtobufReservedStatement
        extends ProtobufStatementImpl
        implements ProtobufStatement,
                   ProtobufMessageChild, ProtobufEnumChild, ProtobufGroupChild {
    private final List<ProtobufReservedChild> expressions;

    public ProtobufReservedStatement(int line) {
        super(line);
        this.expressions = new ArrayList<>();
    }

    public SequencedCollection<ProtobufReservedChild> expressions() {
        return Collections.unmodifiableSequencedCollection(expressions);
    }

    public void addExpression(ProtobufReservedChild expression) {
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

    public void removeExpression(ProtobufReservedChild expression) {
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

        builder.append("reserved");
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
