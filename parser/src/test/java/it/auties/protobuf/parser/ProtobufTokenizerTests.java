package it.auties.protobuf.parser;

import it.auties.protobuf.annotation.ProtobufProperty;
import org.junit.Test;

import java.io.IOException;
import java.io.StringReader;
import java.util.Objects;

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
        assertEquals((Long) 1L, tokenizer.nextNullablePropertyIndex(false, false));
        assertNull(tokenizer.nextNullablePropertyIndex(false, false));
    }

    @Test
    public void testEnumConstantIndex() throws IOException {
        var tokenizer = new ProtobufTokenizer(new StringReader("0 -1"));
        assertEquals((Long) 0L, tokenizer.nextNullablePropertyIndex(true, false));
        assertNull(tokenizer.nextNullablePropertyIndex(true, false));
    }

    @Test
    public void testMaxIndex() throws IOException {
        var tokenizer = new ProtobufTokenizer(new StringReader("max max"));
        if(!Objects.equals(ProtobufProperty.MAX_INDEX, tokenizer.nextNullablePropertyIndex(false, true))) {
            fail();
        }
        if(!Objects.equals(ProtobufProperty.MAX_INDEX, tokenizer.nextNullablePropertyIndex(false, true))) {
            fail();
        }
    }
}
