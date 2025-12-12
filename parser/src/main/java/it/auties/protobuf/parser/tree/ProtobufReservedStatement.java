package it.auties.protobuf.parser.tree;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.SequencedCollection;
import java.util.stream.Collectors;

/**
 * Represents a reserved declaration in the Protocol Buffer AST.
 * <p>
 * The reserved statement prevents field numbers or field names from being used in future versions
 * of a message or enum. This is essential for maintaining backward compatibility when removing
 * fields, as it prevents accidental reuse of field numbers or names that might cause data corruption.
 * </p>
 * <h2>Examples:</h2>
 * <pre>{@code
 * message Foo {
 *   // Reserve field numbers to prevent reuse
 *   reserved 2, 15, 9 to 11;
 *
 *   // Reserve field names
 *   reserved "foo", "bar";
 *
 *   string name = 1;
 *   // int32 age = 2;  // Cannot use 2, it's reserved
 * }
 *
 * enum Status {
 *   // Reserve enum values
 *   reserved 2, 4 to 6;
 *   reserved "OLD_STATUS", "DEPRECATED_STATUS";
 *
 *   UNKNOWN = 0;
 *   ACTIVE = 1;
 * }
 *
 * message Bar {
 *   // Mix of ranges and single numbers
 *   reserved 100, 200 to 300, 500 to max;
 * }
 * }</pre>
 * <p>
 * Reserved statement requirements:
 * </p>
 * <ul>
 *   <li>Field number reserves and field name reserves must be in separate statements</li>
 *   <li>Cannot mix numbers and names in the same reserved statement</li>
 *   <li>Reserved numbers must be within valid field number ranges</li>
 *   <li>Reserved ranges use the same syntax as extension ranges</li>
 *   <li>The special value {@code max} represents 2^29 - 1 (536,870,911)</li>
 * </ul>
 * <p>
 * Reserved statements are important for schema evolution:
 * </p>
 * <ul>
 *   <li>When removing a field, reserve its number to prevent accidental reuse</li>
 *   <li>Reserve field names to prevent confusion in code and documentation</li>
 *   <li>Helps maintain wire format compatibility across versions</li>
 *   <li>Prevents data corruption from field number collisions</li>
 * </ul>
 * <p>
 * This statement contains a list of {@link ProtobufReservedExpression} elements, which can be
 * either numeric ranges/values or string literals representing field names.
 * </p>
 * <p>
 * This class implements multiple child marker interfaces, allowing reserved declarations
 * to appear in messages, enums, groups, and extend blocks.
 * </p>
 *
 * @see ProtobufReservedExpression
 * @see ProtobufIntegerRangeExpression
 * @see ProtobufLiteralExpression
 */
public final class ProtobufReservedStatement
        extends ProtobufStatementImpl
        implements ProtobufStatement,
                   ProtobufMessageChild, ProtobufEnumChild, ProtobufGroupChild {
    private final List<ProtobufReservedExpression> expressions;

    /**
     * Constructs a new reserved statement at the specified line number.
     *
     * @param line the line number in the source file
     */
    public ProtobufReservedStatement(int line) {
        super(line);
        this.expressions = new ArrayList<>();
    }

    /**
     * Returns the list of reserved expressions.
     * <p>
     * Each expression represents either a field number, a range of numbers, or a field name string.
     * </p>
     *
     * @return an unmodifiable collection of reserved expressions
     */
    public SequencedCollection<ProtobufReservedExpression> expressions() {
        return Collections.unmodifiableSequencedCollection(expressions);
    }

    /**
     * Adds a reserved expression to this statement.
     * <p>
     * The expression is automatically linked to this statement as its parent.
     * </p>
     *
     * @param expression the reserved expression to add
     * @throws IllegalStateException if the expression already has a different parent
     */
    public void addExpression(ProtobufReservedExpression expression) {
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
     * Removes a reserved expression from this statement.
     * <p>
     * The expression's parent link is automatically cleared.
     * </p>
     *
     * @param expression the reserved expression to remove
     * @throws IllegalStateException if the expression is not owned by this statement
     */
    public void removeExpression(ProtobufReservedExpression expression) {
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
