package it.auties.protobuf.parser.tree;

import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Enumeration of field modifiers that control field cardinality and requirement semantics.
 */
public enum ProtobufModifier {
    /**
     * No explicit modifier - default for proto3 fields (implicitly optional) and the only valid modifier for enum constants.
     */
    NONE(null),
    /**
     * Required modifier (proto2 only) - field must be set.
     * <p>
     * Required fields must have a value when serializing a message. This modifier
     * is not available in proto3.
     * </p>
     */
    REQUIRED("required"),

    /**
     * Optional modifier - field may or may not be set.
     * <p>
     * Optional fields can be omitted from messages. In proto3, this is the default
     * for singular fields.
     * </p>
     */
    OPTIONAL("optional"),

    /**
     * Repeated modifier - field can appear zero or more times (array/list).
     * <p>
     * Repeated fields represent collections of values of the same type.
     * </p>
     */
    REPEATED("repeated");

    private final String token;

    ProtobufModifier(String token) {
        this.token = token;
    }

    /**
     * Returns the keyword token for this modifier.
     *
     * @return the token string
     */
    public String token() {
        return Objects.requireNonNullElse(token, "");
    }

    private static final Map<String, ProtobufModifier> VALUES = Arrays.stream(values())
            .filter(entry -> entry.token != null)
            .collect(Collectors.toUnmodifiableMap(entry -> entry.token, Function.identity()));

    /**
     * Looks up a modifier by its token string.
     *
     * @param name the token string
     * @return optional containing the modifier, or empty if not found
     */
    public static Optional<ProtobufModifier> of(String name) {
        return Optional.ofNullable(VALUES.get(name));
    }
}
