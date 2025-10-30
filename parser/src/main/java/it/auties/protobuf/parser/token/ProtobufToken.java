package it.auties.protobuf.parser.token;

/**
 * Represents a token produced by the Protocol Buffer lexer during tokenization.
 * <p>
 * Tokens are the fundamental units of the Protocol Buffer language produced during lexical analysis.
 * This sealed interface ensures that all token types are known at compile time and can be exhaustively
 * pattern matched.
 * </p>
 * <p>
 * The lexer recognizes and produces the following token types:
 * </p>
 * <ul>
 *   <li>{@link ProtobufRawToken} - Raw identifiers, keywords, and operators</li>
 *   <li>{@link ProtobufBoolToken} - Boolean literals (true/false)</li>
 *   <li>{@link ProtobufNumberToken} - Numeric literals (integers and floating-point values)</li>
 *   <li>{@link ProtobufLiteralToken} - String literals enclosed in quotes</li>
 * </ul>
 *
 * @see it.auties.protobuf.parser.ProtobufLexer
 */
public sealed interface ProtobufToken permits ProtobufBoolToken, ProtobufLiteralToken, ProtobufNumberToken, ProtobufRawToken {

}
