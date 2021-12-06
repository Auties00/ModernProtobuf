package it.auties.protobuf.decoder;

import java.io.IOException;

class InvalidProtocolBufferException extends IOException {
    public InvalidProtocolBufferException(String description) {
        super(description);
    }

    public static InvalidProtocolBufferException truncatedMessage() {
        return new InvalidProtocolBufferException("While parsing a protocol message, the input ended unexpectedly in the middle of a field.  This could mean either that the input has been truncated or that an embedded message misreported its own length.");
    }

    public static InvalidProtocolBufferException negativeSize() {
        return new InvalidProtocolBufferException("CodedInputStream encountered an embedded string or message which claimed to have negative size.");
    }

    public static InvalidProtocolBufferException malformedVarint() {
        return new InvalidProtocolBufferException("CodedInputStream encountered a malformed varint.");
    }

    public static InvalidProtocolBufferException invalidTag() {
        return new InvalidProtocolBufferException("Protocol message contained an invalid tag (zero).");
    }

    public static InvalidProtocolBufferException invalidEndTag(int tag) {
        return new InvalidProtocolBufferException("Protocol message end-group tag(%s) did not match expected tag.".formatted(tag));
    }

    public static class InvalidWireTypeException extends InvalidProtocolBufferException {
        public InvalidWireTypeException(String description) {
            super(description);
        }
    }
}
