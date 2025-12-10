package it.auties.protobuf.io;

import it.auties.protobuf.model.ProtobufWireType;

public final class ProtobufSizeCalculator {
    private ProtobufSizeCalculator() {
        throw new UnsupportedOperationException("ProtobufSizeCalculator is a utility class");
    }

    public static int getPropertyWireTagSize(long fieldIndex, int wireType) {
        return getVarIntSize(ProtobufWireType.makeTag(fieldIndex, wireType));
    }

    // Long values go from [-2^63, 2^63)
    // A negative var-int always take up 10 bits
    // A positive var int takes up log_2(value) / 7 + 1
    // Constants were folded here to save time
    public static int getVarIntSize(long value) {
        if(value < 0) {
            return 10;
        }else if (value < 128) {
            return 1;
        } else if (value < 16384) {
            return 2;
        } else if (value < 2097152) {
            return 3;
        } else if (value < 268435456) {
            return 4;
        } else if(value < 34359738368L) {
            return 5;
        }else if(value < 4398046511104L) {
            return 6;
        }else if(value < 562949953421312L) {
            return 7;
        }else if(value < 72057594037927936L) {
            return 8;
        }else {
            return 9;
        }
    }

    public static long getVarIntPropertySize(long fieldIndex, long value) {
        return getPropertyWireTagSize(fieldIndex, ProtobufWireType.WIRE_TYPE_VAR_INT)
               + getVarIntSize(value);
    }

    public static long getBoolPropertySize(long fieldIndex, boolean ignored) {
        return getPropertyWireTagSize(fieldIndex, ProtobufWireType.WIRE_TYPE_VAR_INT)
               + 1; // getVarIntSize(0 or 1) = 1
    }

    public static long getBoolPropertySize(long fieldIndex, Boolean value) {
        if(value == null) {
            return 0;
        } else {
            return getPropertyWireTagSize(fieldIndex, ProtobufWireType.WIRE_TYPE_VAR_INT)
                   + 1; // getVarIntSize(0 or 1) = 1
        }
    }

    public static long getFixed64PropertySize(long fieldIndex, long ignored) {
        return getPropertyWireTagSize(fieldIndex, ProtobufWireType.WIRE_TYPE_FIXED64)
               + Long.BYTES;
    }

    public static long getFixed64PropertySize(long fieldIndex, Long value) {
        if(value == null) {
            return 0;
        } else {
            return getPropertyWireTagSize(fieldIndex, ProtobufWireType.WIRE_TYPE_FIXED64)
                   + Long.BYTES;
        }
    }

    public static long getLengthDelimitedPropertySize(long fieldIndex, long length) {
        return getPropertyWireTagSize(fieldIndex, ProtobufWireType.WIRE_TYPE_LENGTH_DELIMITED)
               + getVarIntSize(length)
               + length;
    }

    public static long getFixed32PropertySize(long fieldIndex, int ignored) {
        return getPropertyWireTagSize(fieldIndex, ProtobufWireType.WIRE_TYPE_FIXED32)
               + Integer.BYTES;
    }

    public static long getFixed32PropertySize(long fieldIndex, Integer value) {
        if(value == null) {
            return 0;
        } else {
            return getPropertyWireTagSize(fieldIndex, ProtobufWireType.WIRE_TYPE_FIXED32)
                   + Integer.BYTES;
        }
    }

    public static int getVarIntPackedSize(long fieldIndex, byte[] values) {
        if (values == null) {
            return 0;
        } else {
            var valueSize = 0;
            for (var value : values) {
                valueSize += getVarIntSize(value);
            }
            return getPropertyWireTagSize(fieldIndex, ProtobufWireType.WIRE_TYPE_LENGTH_DELIMITED)
                   + getVarIntSize(valueSize)
                   + valueSize;
        }
    }

    public static int getVarIntPackedSize(long fieldIndex, short[] values) {
        if (values == null) {
            return 0;
        } else {
            var valueSize = 0;
            for (var value : values) {
                valueSize += getVarIntSize(value);
            }
            return getPropertyWireTagSize(fieldIndex, ProtobufWireType.WIRE_TYPE_LENGTH_DELIMITED)
                   + getVarIntSize(valueSize)
                   + valueSize;
        }
    }

    public static int getVarIntPackedSize(long fieldIndex, int[] values) {
        if (values == null) {
            return 0;
        } else {
            var valueSize = 0;
            for (var value : values) {
                valueSize += getVarIntSize(value);
            }
            return getPropertyWireTagSize(fieldIndex, ProtobufWireType.WIRE_TYPE_LENGTH_DELIMITED)
                   + getVarIntSize(valueSize)
                   + valueSize;
        }
    }

    public static int getVarIntPackedSize(long fieldIndex, long[] values) {
        if (values == null) {
            return 0;
        } else {
            var valueSize = 0;
            for (var value : values) {
                valueSize += getVarIntSize(value);
            }
            return getPropertyWireTagSize(fieldIndex, ProtobufWireType.WIRE_TYPE_LENGTH_DELIMITED)
                   + getVarIntSize(valueSize)
                   + valueSize;
        }
    }

    public static int getBoolPackedSize(long fieldIndex, boolean[] values) {
        if(values == null) {
            return 0;
        } else {
            var valuesSize = values.length;
            return getPropertyWireTagSize(fieldIndex, ProtobufWireType.WIRE_TYPE_LENGTH_DELIMITED)
                   + getVarIntSize(valuesSize)
                   + valuesSize;
        }
    }

    public static int getFixed64PackedSize(long fieldIndex, byte[] values) {
        if (values == null) {
            return 0;
        } else {
            var valuesSize = values.length * Long.BYTES;
            return getPropertyWireTagSize(fieldIndex, ProtobufWireType.WIRE_TYPE_LENGTH_DELIMITED)
                   + getVarIntSize(valuesSize)
                   + valuesSize;
        }
    }

    public static int getFixed64PackedSize(long fieldIndex, short[] values) {
        if (values == null) {
            return 0;
        } else {
            var valuesSize = values.length * Long.BYTES;
            return getPropertyWireTagSize(fieldIndex, ProtobufWireType.WIRE_TYPE_LENGTH_DELIMITED)
                   + getVarIntSize(valuesSize)
                   + valuesSize;
        }
    }

    public static int getFixed64PackedSize(long fieldIndex, int[] values) {
        if (values == null) {
            return 0;
        } else {
            var valuesSize = values.length * Long.BYTES;
            return getPropertyWireTagSize(fieldIndex, ProtobufWireType.WIRE_TYPE_LENGTH_DELIMITED)
                   + getVarIntSize(valuesSize)
                   + valuesSize;
        }
    }

    public static int getFixed64PackedSize(long fieldIndex, long[] values) {
        if (values == null) {
            return 0;
        } else {
            var valuesSize = values.length * Long.BYTES;
            return getPropertyWireTagSize(fieldIndex, ProtobufWireType.WIRE_TYPE_LENGTH_DELIMITED)
                   + getVarIntSize(valuesSize)
                   + valuesSize;
        }
    }

    public static int getFixed32PackedSize(long fieldIndex, byte[] values) {
        if (values == null) {
            return 0;
        } else {
            var valuesSize = values.length * Integer.BYTES;
            return getPropertyWireTagSize(fieldIndex, ProtobufWireType.WIRE_TYPE_LENGTH_DELIMITED)
                   + getVarIntSize(valuesSize)
                   + valuesSize;
        }
    }

    public static int getFixed32PackedSize(long fieldIndex, short[] values) {
        if (values == null) {
            return 0;
        } else {
            var valuesSize = values.length * Integer.BYTES;
            return getPropertyWireTagSize(fieldIndex, ProtobufWireType.WIRE_TYPE_LENGTH_DELIMITED)
                   + getVarIntSize(valuesSize)
                   + valuesSize;
        }
    }
    
    public static int getFixed32PackedSize(long fieldIndex, int[] values) {
        if (values == null) {
            return 0;
        } else {
            var valuesSize = values.length * Integer.BYTES;
            return getPropertyWireTagSize(fieldIndex, ProtobufWireType.WIRE_TYPE_LENGTH_DELIMITED)
                   + getVarIntSize(valuesSize)
                   + valuesSize;
        }
    }

    public static int getFloatPackedSize(long fieldIndex, float[] values) {
        if (values == null) {
            return 0;
        } else {
            var valuesSize = values.length * Float.BYTES;
            return getPropertyWireTagSize(fieldIndex, ProtobufWireType.WIRE_TYPE_LENGTH_DELIMITED)
                   + getVarIntSize(valuesSize)
                   + valuesSize;
        }
    }

    public static int getDoublePackedSize(long fieldIndex, double[] values) {
        if (values == null) {
            return 0;
        } else {
            var valuesSize = values.length * Double.BYTES;
            return getPropertyWireTagSize(fieldIndex, ProtobufWireType.WIRE_TYPE_LENGTH_DELIMITED)
                   + getVarIntSize(valuesSize)
                   + valuesSize;
        }
    }
}
