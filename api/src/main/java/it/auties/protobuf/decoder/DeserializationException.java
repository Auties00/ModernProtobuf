package it.auties.protobuf.decoder;

import java.io.IOException;

class DeserializationException extends IOException {
    public DeserializationException(String description) {
        super(description);
    }

    public static DeserializationException truncatedMessage() {
        return new DeserializationException("While parsing a protocol message, the input ended unexpectedly in the middle of a field.  This could mean either that the input has been truncated or that an embedded message misreported its own length.");
    }

    public static DeserializationException negativeSize() {
        return new DeserializationException("CodedInputStream encountered an embedded string or message which claimed to have negative size.");
    }

    public static DeserializationException malformedVarInt() {
        return new DeserializationException("CodedInputStream encountered a malformed varint.");
    }

    public static DeserializationException invalidTag() {
        return new DeserializationException("Protocol message contained an invalid tag (zero).");
    }

    public static DeserializationException invalidEndTag(int tag) {
        return new DeserializationException("Protocol message end-group tag(%s) did not match expected tag.".formatted(tag));
    }
}
