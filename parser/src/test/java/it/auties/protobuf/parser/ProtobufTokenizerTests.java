package it.auties.protobuf.parser;

import it.auties.protobuf.annotation.ProtobufEnumIndex;
import it.auties.protobuf.annotation.ProtobufProperty;
import org.junit.Test;

import java.io.IOException;
import java.io.StringReader;

import static org.junit.Assert.*;

public class ProtobufTokenizerTests {
    @Test
    public void testEof() throws IOException {
        var tokenizer = new ProtobufTokenizer(new StringReader(""));
        assertNull(tokenizer.nextNullableToken());
    }

    @Test
    public void testToken() throws IOException {
        var tokenizer = new ProtobufTokenizer(new StringReader("word"));
        assertEquals("word", tokenizer.nextNullableToken());
    }

    @Test
    public void testNullableToken() throws IOException {
        var tokenizer = new ProtobufTokenizer(new StringReader("123"));
        assertEquals("123", tokenizer.nextNullableToken());
    }

    @Test
    public void testStringLiteral() throws IOException {
        var tokenizer = new ProtobufTokenizer(new StringReader("\"string\""));
        assertEquals("\"string\"", tokenizer.nextNullableToken());
    }

    @Test
    public void testPropertyIndex() throws IOException {
        var tokenizer = new ProtobufTokenizer(new StringReader("1 0"));
        assertEquals((Long) 1L, tokenizer.nextNullableIndex(false, false));
        assertNull(tokenizer.nextNullableIndex(false, false));
    }

    @Test
    public void testEnumConstantIndex() throws IOException {
        var tokenizer = new ProtobufTokenizer(new StringReader("0 -1"));
        assertEquals((Long) 0L, tokenizer.nextNullableIndex(true, false));
        assertNull(tokenizer.nextNullableIndex(true, false));
    }

    @Test
    public void testMaxIndex() throws IOException {
        var tokenizer = new ProtobufTokenizer(new StringReader("max max"));
        assertEquals((Long) ProtobufProperty.MAX_INDEX, tokenizer.nextNullableIndex(false, true));
        assertEquals((Long) ProtobufEnumIndex.MAX_VALUE, tokenizer.nextNullableIndex(true, true));
    }
}
