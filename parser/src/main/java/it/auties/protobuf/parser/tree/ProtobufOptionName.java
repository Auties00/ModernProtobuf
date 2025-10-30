package it.auties.protobuf.parser.tree;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Represents the name component of a Protocol Buffer option.
 * <p>
 * Option names can take several forms:
 * </p>
 * <ul>
 *   <li>Simple names: {@code java_package}</li>
 *   <li>Extension names (in parentheses): {@code (my.custom.option)}</li>
 *   <li>Names with member selection: {@code (my.option).field.subfield}</li>
 * </ul>
 * <h2>Examples:</h2>
 * <pre>{@code
 * option java_package = "com.example";           // Simple name
 * option (my.extension) = "value";               // Extension name
 * option (file_option).nested.field = 42;        // Extension with member selection
 * }</pre>
 *
 * @param name the base name of the option
 * @param extension whether this is an extension option (surrounded by parentheses)
 * @param membersSelected list of member names accessed via dot notation after the base name
 */
public record ProtobufOptionName(String name, boolean extension, List<String> membersSelected) {
    /**
     * Returns an unmodifiable view of the member selection path.
     *
     * @return unmodifiable list of selected member names
     */
    @Override
    public List<String> membersSelected() {
        return Collections.unmodifiableList(membersSelected);
    }

    @Override
    public String toString() {
        var result = new StringBuilder();
        if (extension) {
            result.append('(');
        }
        result.append(name);
        if (extension) {
            result.append(')');
        }
        for (var member : membersSelected) {
            result.append('.');
            result.append(member);
        }
        return result.toString();
    }
}
