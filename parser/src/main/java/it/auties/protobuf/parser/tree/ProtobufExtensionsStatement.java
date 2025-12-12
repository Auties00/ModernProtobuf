package it.auties.protobuf.parser.tree;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Represents an extensions declaration in the Protocol Buffer AST.
 * <p>
 * The extensions statement declares ranges of field numbers that are reserved for third-party
 * extensions. This allows other .proto files to add fields to the message using extend blocks
 * without modifying the original message definition.
 * </p>
 * <h2>Examples:</h2>
 * <pre>{@code
 * message Foo {
 *   // Reserve field numbers 100-199 for extensions
 *   extensions 100 to 199;
 * }
 *
 * message Bar {
 *   // Multiple extension ranges
 *   extensions 1000 to 2000, 3000 to max;
 * }
 *
 * message Baz {
 *   // Single extension field number
 *   extensions 42;
 *
 *   // Mix of ranges and single numbers
 *   extensions 100, 200 to 300, 500;
 * }
 * }</pre>
 * <p>
 * Extension range requirements:
 * </p>
 * <ul>
 *   <li>Field numbers must be in the valid range (1 to 536,870,911)</li>
 *   <li>Cannot overlap with regular field numbers in the message</li>
 *   <li>Cannot overlap with reserved field numbers</li>
 *   <li>Cannot use the reserved range 19,000 to 19,999</li>
 *   <li>The special value {@code max} represents 2^29 - 1 (536,870,911)</li>
 * </ul>
 * <p>
 * This statement contains a list of {@link ProtobufExtensionsExpression} elements, each
 * representing either a single field number or a range of field numbers.
 * </p>
 * <p>
 * This class implements multiple child marker interfaces, allowing extensions declarations
 * to appear in messages, enums, groups, and extend blocks.
 * </p>
 *
 * @see ProtobufExtensionsExpression
 * @see ProtobufIntegerRangeExpression
 * @see ProtobufExtendStatement
 */
public final class ProtobufExtensionsStatement
        extends ProtobufStatementImpl
        implements ProtobufStatement, ProtobufTree.WithOptions,
                   ProtobufMessageChild, ProtobufGroupChild {
    private final List<ProtobufExtensionsExpression> expressions;
    private final SequencedMap<String, ProtobufOptionExpression> options;

    /**
     * Constructs a new extensions statement at the specified line number.
     *
     * @param line the line number in the source file
     */
    public ProtobufExtensionsStatement(int line) {
        super(line);
        this.expressions = new ArrayList<>();
        this.options = new LinkedHashMap<>();
    }

    /**
     * Returns the list of extension range expressions.
     * <p>
     * Each expression represents either a single field number or a range of numbers.
     * </p>
     *
     * @return an unmodifiable collection of extension expressions
     */
    public SequencedCollection<ProtobufExtensionsExpression> expressions() {
        return Collections.unmodifiableSequencedCollection(expressions);
    }

    /**
     * Adds an extension range expression to this statement.
     * <p>
     * The expression is automatically linked to this statement as its parent.
     * </p>
     *
     * @param expression the extension expression to add
     * @throws IllegalStateException if the expression already has a different parent
     */
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

    /**
     * Removes an extension range expression from this statement.
     * <p>
     * The expression's parent link is automatically cleared.
     * </p>
     *
     * @param expression the extension expression to remove
     * @throws IllegalStateException if the expression is not owned by this statement
     */
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
    public SequencedCollection<ProtobufOptionExpression> options() {
        return options.sequencedValues();
    }

    @Override
    public void addOption(ProtobufOptionExpression value) {
        Objects.requireNonNull(value, "Cannot add null option");
        options.put(value.name().toString(), value);
    }

    @Override
    public boolean removeOption(String name) {
        return options.remove(name) != null;
    }

    @Override
    public Optional<ProtobufOptionExpression> getOption(String name) {
        return Optional.ofNullable(options.get(name));
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
