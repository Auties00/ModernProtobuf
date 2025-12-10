package it.auties.protobuf.io;

import jdk.incubator.vector.ByteVector;
import jdk.incubator.vector.VectorSpecies;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public sealed abstract class ProtobufIO
        implements AutoCloseable
        permits ProtobufReader, ProtobufWriter {
    protected static final long INT8_PEXT_MASK = 0x000000000000017fL;
    protected static final long INT16_PEXT_MASK = 0x0000000000037f7fL;
    protected static final long INT32_PEXT_MASK = 0x0000000f7f7f7f7fL;
    protected static final long INT64_PEXT_MASK_LOW = 0x7f7f7f7f7f7f7f7fL;
    protected static final long INT64_PEXT_MASK_HIGH = 0x000000000000017fL;

    private static final VarHandle ARRAY_AS_INT16 = MethodHandles.byteArrayViewVarHandle(short[].class, ByteOrder.LITTLE_ENDIAN);
    private static final VarHandle ARRAY_AS_INT32 = MethodHandles.byteArrayViewVarHandle(int[].class, ByteOrder.LITTLE_ENDIAN);
    private static final VarHandle ARRAY_AS_INT64 = MethodHandles.byteArrayViewVarHandle(long[].class, ByteOrder.LITTLE_ENDIAN);
    private static final VarHandle ARRAY_AS_FLOAT = MethodHandles.byteArrayViewVarHandle(float[].class, ByteOrder.LITTLE_ENDIAN);
    private static final VarHandle ARRAY_AS_DOUBLE = MethodHandles.byteArrayViewVarHandle(double[].class, ByteOrder.LITTLE_ENDIAN);

    private static final VarHandle BUFFER_AS_INT16 = MethodHandles.byteBufferViewVarHandle(short[].class, ByteOrder.LITTLE_ENDIAN);
    private static final VarHandle BUFFER_AS_INT32 = MethodHandles.byteBufferViewVarHandle(int[].class, ByteOrder.LITTLE_ENDIAN);
    private static final VarHandle BUFFER_AS_INT64 = MethodHandles.byteBufferViewVarHandle(long[].class, ByteOrder.LITTLE_ENDIAN);
    private static final VarHandle BUFFER_AS_FLOAT = MethodHandles.byteBufferViewVarHandle(float[].class, ByteOrder.LITTLE_ENDIAN);
    private static final VarHandle BUFFER_AS_DOUBLE = MethodHandles.byteBufferViewVarHandle(double[].class, ByteOrder.LITTLE_ENDIAN);

    private static final ValueLayout.OfInt INT32_LAYOUT = ValueLayout.JAVA_INT_UNALIGNED.withOrder(ByteOrder.LITTLE_ENDIAN);
    private static final ValueLayout.OfLong INT64_LAYOUT = ValueLayout.JAVA_LONG_UNALIGNED.withOrder(ByteOrder.LITTLE_ENDIAN);
    private static final ValueLayout.OfFloat FLOAT_LAYOUT = ValueLayout.JAVA_FLOAT_UNALIGNED.withOrder(ByteOrder.LITTLE_ENDIAN);
    private static final ValueLayout.OfDouble DOUBLE_LAYOUT = ValueLayout.JAVA_DOUBLE_UNALIGNED.withOrder(ByteOrder.LITTLE_ENDIAN);

    protected static final VectorSpecies<Byte> V64 = ByteVector.SPECIES_64;
    protected static final boolean SUPPORTS_V64 = isSpeciesSupported(V64);

    protected static final VectorSpecies<Byte> V128 = ByteVector.SPECIES_128;
    protected static final boolean SUPPORTS_V128 = isSpeciesSupported(V128);

    protected static final VectorSpecies<Byte> V256 = ByteVector.SPECIES_256;
    protected static final boolean SUPPORTS_V256 = isSpeciesSupported(V256);

    protected static final VectorSpecies<Byte> V512 = ByteVector.SPECIES_512;
    protected static final boolean SUPPORTS_V512 = isSpeciesSupported(V512);

    private static boolean isSpeciesSupported(VectorSpecies<?> species) {
        return species.vectorBitSize() <= ByteVector.SPECIES_PREFERRED.vectorBitSize();
    }

    protected static final int[][] LOOKUP_DOUBLE_VEC = generateDoubleVec();
    protected static final int[] LOOKUP_DOUBLE_STEP1 = generateDoubleStep1();

    protected static final int[] LOOKUP_QUAD_STEP1 = generateQuadStep1();
    protected static final int[][] LOOKUP_QUAD_VEC = generateQuadVec();

    private static int[] generateDoubleStep1() {
        var table = new int[1024];

        for (var bitmask = 0; bitmask < 1024; bitmask++) {
            var bmNot = (~bitmask) & 0x3FF;
            var firstLen = Integer.numberOfTrailingZeros(bmNot | 0x400) + 1;
            if (firstLen > 10) {
                firstLen = 10;
            }

            var bmNot2 = bmNot >>> firstLen;
            var secondLen = Integer.numberOfTrailingZeros(bmNot2 | 0x400) + 1;
            if (secondLen > 10) {
                secondLen = 10;
            }

            // Calculate lookup index based on (firstLen, secondLen) pattern
            // The pattern caps second length based on remaining space in 16 bytes
            var maxSecondLen = Math.min(secondLen, 16 - firstLen);
            var lookupIndex = calculateDoubleLookupIndex(firstLen, maxSecondLen);

            table[bitmask] = (lookupIndex & 0xFF)
                             | ((firstLen & 0xF) << 8)
                             | ((secondLen & 0xF) << 12);
        }

        return table;
    }

    private static int calculateDoubleLookupIndex(int firstLen, int secondLen) {
        // Clamp to valid ranges
        if (firstLen < 1) {
            firstLen = 1;
        }
        if (firstLen > 10) {
            firstLen = 10;
        }
        if (secondLen < 1) {
            secondLen = 1;
        }

        // The index calculation follows the pattern in the Rust lookup table
        // Each firstLen value covers up to (10 - (firstLen-1)) secondLen values
        // but we cap at 10 for firstLen 1-2, and reduce for larger firstLen

        int maxSecond;
        if (firstLen <= 8) {
            maxSecond = Math.min(10, 16 - firstLen);
        } else {
            maxSecond = 16 - firstLen;
        }

        secondLen = Math.min(secondLen, maxSecond);

        // Calculate offset: sum of entries for all smaller firstLen values
        var offset = 0;
        for (var fl = 1; fl < firstLen; fl++) {
            if (fl <= 8) {
                offset += Math.min(10, 16 - fl);
            } else {
                offset += 16 - fl;
            }
        }

        return offset + (secondLen - 1);
    }

    private static int[][] generateDoubleVec() {
        var table = new int[90][16];

        var index = 0;
        for (var firstLen = 1; firstLen <= 10; firstLen++) {
            int maxSecondLen;
            if (firstLen <= 8) {
                maxSecondLen = Math.min(10, 16 - firstLen);
            } else {
                maxSecondLen = 16 - firstLen;
            }

            for (var secondLen = 1; secondLen <= maxSecondLen; secondLen++) {
                var shuffle = new int[16];
                // Fill with 0xFF (invalid lane markers)
                for (var i = 0; i < 16; i++) {
                    shuffle[i] = (byte) 0xFF;
                }

                // First varint bytes go to positions 0-7
                for (var i = 0; i < Math.min(firstLen, 8); i++) {
                    shuffle[i] = (byte) i;
                }

                // Second varint bytes go to positions 8-15
                for (var i = 0; i < Math.min(secondLen, 8); i++) {
                    shuffle[8 + i] = (byte) (firstLen + i);
                }

                table[index++] = shuffle;
            }
        }

        return table;
    }

    private static int[] generateQuadStep1() {
        var table = new int[4096];

        for (var bitmask = 0; bitmask < 4096; bitmask++) {
            var bmNot = (~bitmask) & 0xFFF;

            var firstLen = Integer.numberOfTrailingZeros(bmNot | 0x1000) + 1;
            var bmNot2 = bmNot >>> firstLen;

            var secondLen = Integer.numberOfTrailingZeros(bmNot2 | 0x1000) + 1;
            var bmNot3 = bmNot2 >>> secondLen;

            var thirdLen = Integer.numberOfTrailingZeros(bmNot3 | 0x1000) + 1;
            var bmNot4 = bmNot3 >>> thirdLen;

            var fourthLen = Integer.numberOfTrailingZeros(bmNot4 | 0x1000) + 1;

            // Check if all four fit in 12 bytes (max for u16 varints in quad mode)
            var invalid = (firstLen + secondLen + thirdLen + fourthLen) > 12;

            // Calculate lookup index for 3^4 pattern (lengths 1-3 each)
            var l1 = Math.min(firstLen, 3) - 1;
            var l2 = Math.min(secondLen, 3) - 1;
            var l3 = Math.min(thirdLen, 3) - 1;
            var l4 = Math.min(fourthLen, 3) - 1;
            var lookupIndex = l1 * 27 + l2 * 9 + l3 * 3 + l4;

            // Pack into u32: [invalid:1][unused:7][fourthLen:4][thirdLen:4][secondLen:4][firstLen:4][lookupIndex:8]
            var packed = (lookupIndex & 0xFF)
                         | ((firstLen & 0xF) << 8)
                         | ((secondLen & 0xF) << 12)
                         | ((thirdLen & 0xF) << 16)
                         | ((fourthLen & 0xF) << 20)
                         | (invalid ? 0x80000000 : 0);

            table[bitmask] = packed;
        }

        return table;
    }

    private static int[][] generateQuadVec() {
        // 81 entries for 3^4 combinations of lengths (1, 2, or 3 bytes each)
        var table = new int[81][16];

        var index = 0;
        for (var l1 = 1; l1 <= 3; l1++) {
            for (var l2 = 1; l2 <= 3; l2++) {
                for (var l3 = 1; l3 <= 3; l3++) {
                    for (var l4 = 1; l4 <= 3; l4++) {
                        var shuffle = new int[16];
                        // Fill with 0xFF (invalid lane markers)
                        for (var i = 0; i < 16; i++) {
                            shuffle[i] = (byte) 0xFF;
                        }

                        var pos = 0;

                        // First varint in bytes 0-3
                        for (var i = 0; i < l1; i++) {
                            shuffle[i] = (byte) pos++;
                        }

                        // Second varint in bytes 4-7
                        for (var i = 0; i < l2; i++) {
                            shuffle[4 + i] = (byte) pos++;
                        }

                        // Third varint in bytes 8-11
                        for (var i = 0; i < l3; i++) {
                            shuffle[8 + i] = (byte) pos++;
                        }

                        // Fourth varint in bytes 12-15
                        for (var i = 0; i < l4; i++) {
                            shuffle[12 + i] = (byte) pos++;
                        }

                        table[index++] = shuffle;
                    }
                }
            }
        }

        return table;
    }

    protected static int getLookupIndex(int packed) {
        return packed & 0xFF;
    }

    protected static int getFirstLength(int packed) {
        return (packed >>> 8) & 0xF;
    }

    protected static int getSecondLength(int packed) {
        return (packed >>> 12) & 0xF;
    }

    protected static int getThirdLength(int packed) {
        return (packed >>> 16) & 0xF;
    }

    protected static int getFourthLength(int packed) {
        return (packed >>> 20) & 0xF;
    }

    protected static boolean isInvalid(int packed) {
        return (packed & 0x80000000) != 0;
    }

    protected static short getShortLE(byte[] arr, int offset) {
        return (short) ARRAY_AS_INT16.get(arr, offset);
    }

    protected static int getIntLE(byte[] arr, int offset) {
        return (int) ARRAY_AS_INT32.get(arr, offset);
    }

    protected static long getLongLE(byte[] arr, int offset) {
        return (long) ARRAY_AS_INT64.get(arr, offset);
    }

    protected static int getFloatLE(byte[] arr, int offset) {
        return (int) ARRAY_AS_FLOAT.get(arr, offset);
    }

    protected static long getDoubleLE(byte[] arr, int offset) {
        return (long) ARRAY_AS_DOUBLE.get(arr, offset);
    }

    protected static void putShortLE(byte[] arr, int offset, short value) {
        ARRAY_AS_INT16.set(arr, offset, value);
    }

    protected static void putIntLE(byte[] arr, int offset, int value) {
        ARRAY_AS_INT32.set(arr, offset, value);
    }

    protected static void putLongLE(byte[] arr, int offset, long value) {
        ARRAY_AS_INT64.set(arr, offset, value);
    }

    protected static void putFloatLE(byte[] arr, int offset, float value) {
        ARRAY_AS_FLOAT.set(arr, offset, value);
    }

    protected static void putDoubleLE(byte[] arr, int offset, double value) {
        ARRAY_AS_DOUBLE.set(arr, offset, value);
    }

    protected static short getShortLE(ByteBuffer buffer, int offset) {
        return (short) BUFFER_AS_INT16.get(buffer, offset);
    }

    protected static int getIntLE(ByteBuffer buffer, int offset) {
        return (int) BUFFER_AS_INT32.get(buffer, offset);
    }

    protected static long getLongLE(ByteBuffer buffer, int offset) {
        return (long) BUFFER_AS_INT64.get(buffer, offset);
    }

    protected static int getFloatLE(ByteBuffer buffer, int offset) {
        return (int) BUFFER_AS_FLOAT.get(buffer, offset);
    }

    protected static long getDoubleLE(ByteBuffer buffer, int offset) {
        return (long) BUFFER_AS_DOUBLE.get(buffer, offset);
    }

    protected static void putShortLE(ByteBuffer buffer, int offset, short value) {
        BUFFER_AS_INT16.set(buffer, offset, value);
    }

    protected static void putIntLE(ByteBuffer buffer, int offset, int value) {
        BUFFER_AS_INT32.set(buffer, offset, value);
    }

    protected static void putLongLE(ByteBuffer buffer, int offset, long value) {
        BUFFER_AS_INT64.set(buffer, offset, value);
    }

    protected static void putFloatLE(ByteBuffer arr, int offset, float value) {
        BUFFER_AS_FLOAT.set(arr, offset, value);
    }

    protected static void putDoubleLE(ByteBuffer arr, int offset, double value) {
        BUFFER_AS_DOUBLE.set(arr, offset, value);
    }

    protected static int[] toIntArrayLE(MemorySegment segment) {
        return segment.toArray(INT32_LAYOUT);
    }

    protected static long[] toLongArrayLE(MemorySegment segment) {
        return segment.toArray(INT64_LAYOUT);
    }

    protected static float[] toFloatArrayLE(MemorySegment segment) {
        return segment.toArray(FLOAT_LAYOUT);
    }

    protected static double[] toDoubleArrayLE(MemorySegment segment) {
        return segment.toArray(DOUBLE_LAYOUT);
    }

    protected ProtobufIO() {

    }

    public abstract DataType rawDataTypePreference();
    // TODO: Maybe offer a performance/memory usage preference?

    public enum DataType {
        BYTE_ARRAY,
        BYTE_BUFFER,
        MEMORY_SEGMENT
    }
}
