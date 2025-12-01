package it.auties.protobuf;

import it.auties.protobuf.model.ProtobufLazyString;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class ProtobufStringTest {
    private static String TEST_STRING;

    // In UTF-8, a single Unicode character can be encoded using 1 to 4 bytes.
    // It shouldn't make a difference for the methods in ProtobufString to have one char takes n bytes or more
    // If that ever changes, update this method
    // Also added some sanity assertions just to be sure
    @BeforeAll
    public static void testUtf8Input() {
        var oneByteString = "A";
        Assertions.assertEquals(1, oneByteString.getBytes().length);
        var twoBytesString = "ñ";
        Assertions.assertEquals(2, twoBytesString.getBytes().length);
        var threeByteString = "€";
        Assertions.assertEquals(3, threeByteString.getBytes().length);
        var fourBytesString = "🌟";
        Assertions.assertEquals(4, fourBytesString.getBytes().length);
        TEST_STRING = oneByteString + twoBytesString + threeByteString + fourBytesString;
    }

    @Test
    public void testWrappers() {
        var wrapped = TEST_STRING;
        var lazy = ProtobufLazyString.of(TEST_STRING.getBytes());
        Assertions.assertEquals(TEST_STRING, wrapped);
        Assertions.assertEquals(TEST_STRING, lazy.toString());
    }

    @Test
    public void testLength() {
        var wrapped = TEST_STRING;
        var lazy = ProtobufLazyString.of(TEST_STRING.getBytes());
        var javaLength = TEST_STRING.length();
        var wrappedLength = wrapped.length();
        var lazyLength = lazy.length();
        Assertions.assertEquals(javaLength, wrappedLength);
        Assertions.assertEquals(javaLength, lazyLength);
    }

    @Test
    public void testSubsequence() {
        var wrapped = TEST_STRING;
        var lazy = ProtobufLazyString.of(TEST_STRING.getBytes());
        var length = TEST_STRING.length();
        for(var i = 0; i < length; i++) {
            for(var j = i + 1; j <= length; j++) {
                var javaSubsequence = TEST_STRING.substring(i, j);
                var subsequence = wrapped.subSequence(i, j);
                var lazySubsequence = lazy.subSequence(i, j);
                Assertions.assertEquals(javaSubsequence, subsequence.toString());
                Assertions.assertEquals(javaSubsequence, lazySubsequence.toString());
            }
        }
    }

    @Test
    public void testCharAt() {
        var wrapped = TEST_STRING;
        var lazy = ProtobufLazyString.of(TEST_STRING.getBytes());
        var length = TEST_STRING.length();
        for(var i = 0; i < length; i++) {
            var javaCharAt = TEST_STRING.charAt(i);
            var wrappedCharAt = wrapped.charAt(i);
            var lazyCharAt = lazy.charAt(i);
            Assertions.assertEquals(javaCharAt, wrappedCharAt);
            Assertions.assertEquals(javaCharAt, lazyCharAt);
        }
    }
}
