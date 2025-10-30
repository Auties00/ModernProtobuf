package it.auties.protobuf.parser;

import it.auties.protobuf.parser.exception.ProtobufParserException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class ProtobufAnalyzerTest {
    @Test
    public void testTypeResolutionGlobalScope() {
        var proto = """
                syntax = "proto3";

                message Outer {
                  message Inner {
                    string value = 1;
                  }
                  .Outer.Inner field = 1;
                }
                """;
        var doc = ProtobufParser.parseOnly(proto);
        assertNotNull(doc);
    }

    @Test
    public void testTypeResolutionNestedMultipleLevels() {
        var proto = """
                syntax = "proto3";

                message L1 {
                  message L2 {
                    message L3 {
                      message L4 {
                        string value = 1;
                      }
                    }
                  }
                }

                message Test {
                  L1.L2.L3.L4 field = 1;
                }
                """;
        var doc = ProtobufParser.parseOnly(proto);
        assertNotNull(doc);
    }

    @Test
    public void testTypeResolutionShadowedNames() {
        var proto = """
                syntax = "proto3";

                message Data {
                  string global = 1;
                }

                message Outer {
                  message Data {
                    string outer = 1;
                  }

                  message Inner {
                    Data local = 1;
                    .Data globalRef = 2;
                  }
                }
                """;
        var doc = ProtobufParser.parseOnly(proto);
        assertNotNull(doc);
    }

    @Test
    public void testTypeResolutionUnresolved() {
        var proto = """
                syntax = "proto3";

                message Test {
                  NonExistent field = 1;
                }
                """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(proto));
    }

    @Test
    public void testMapKeyMessageType() {
        var proto = """
                syntax = "proto3";

                message Key {
                  string id = 1;
                }

                message Test {
                  map<Key, string> data = 1;
                }
                """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(proto));
    }

    @Test
    public void testMapKeyEnumType() {
        var proto = """
                syntax = "proto3";

                enum Status {
                  UNKNOWN = 0;
                }

                message Test {
                  map<Status, string> data = 1;
                }
                """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(proto));
    }

    @Test
    public void testMapKeyFloatingPointTypes() {
        var protoFloat = """
                syntax = "proto3";

                message Test {
                  map<float, string> data = 1;
                }
                """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(protoFloat));

        var protoDouble = """
                syntax = "proto3";

                message Test {
                  map<double, string> data = 1;
                }
                """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(protoDouble));
    }

    @Test
    public void testMapValueNestedMapType() {
        var proto = """
                syntax = "proto3";

                message Test {
                  map<string, map<int32, string>> data = 1;
                }
                """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(proto));
    }

    @Test
    public void testMapWithAllValidKeyTypes() {
        var proto = """
                syntax = "proto3";

                message Test {
                  map<int32, string> m1 = 1;
                  map<int64, string> m2 = 2;
                  map<uint32, string> m3 = 3;
                  map<uint64, string> m4 = 4;
                  map<sint32, string> m5 = 5;
                  map<sint64, string> m6 = 6;
                  map<fixed32, string> m7 = 7;
                  map<fixed64, string> m8 = 8;
                  map<sfixed32, string> m9 = 9;
                  map<sfixed64, string> m10 = 10;
                  map<bool, string> m11 = 11;
                  map<string, string> m12 = 12;
                }
                """;
        var doc = ProtobufParser.parseOnly(proto);
        assertNotNull(doc);
    }

    @Test
    public void testExtensionFieldNumberBoundaryMinimum() {
        var proto = """
                syntax = "proto2";

                message Extendable {
                  extensions 100 to 199;
                }

                extend Extendable {
                  optional string field = 100;
                }
                """;
        var doc = ProtobufParser.parseOnly(proto);
        assertNotNull(doc);
    }

    @Test
    public void testExtensionFieldNumberBoundaryMaximum() {
        var proto = """
                syntax = "proto2";

                message Extendable {
                  extensions 100 to 199;
                }

                extend Extendable {
                  optional string field = 199;
                }
                """;
        var doc = ProtobufParser.parseOnly(proto);
        assertNotNull(doc);
    }

    @Test
    public void testExtensionFieldNumberBelowRange() {
        var proto = """
                syntax = "proto2";

                message Extendable {
                  extensions 100 to 199;
                }

                extend Extendable {
                  optional string field = 99;
                }
                """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(proto));
    }

    @Test
    public void testExtensionFieldNumberAboveRange() {
        var proto = """
                syntax = "proto2";

                message Extendable {
                  extensions 100 to 199;
                }

                extend Extendable {
                  optional string field = 200;
                }
                """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(proto));
    }

    @Test
    public void testExtensionWithMaxKeyword() {
        var proto = """
                syntax = "proto2";

                message Extendable {
                  extensions 1000 to max;
                }

                extend Extendable {
                  optional string field = 536870911;
                }
                """;
        var doc = ProtobufParser.parseOnly(proto);
        assertNotNull(doc);
    }

    @Test
    public void testExtensionMultipleRanges() {
        var proto = """
                syntax = "proto2";

                message Extendable {
                  extensions 100 to 199, 500, 1000 to 2000;
                }

                extend Extendable {
                  optional string f1 = 100;
                  optional string f2 = 500;
                  optional string f3 = 1500;
                }
                """;
        var doc = ProtobufParser.parseOnly(proto);
        assertNotNull(doc);
    }

    @Test
    public void testExtensionRangeOverlapsField() {
        var proto = """
                syntax = "proto2";

                message Test {
                  optional string field = 100;
                  extensions 100 to 199;
                }
                """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(proto));
    }

    @Test
    public void testExtensionRangeOverlapsReserved() {
        var proto = """
                syntax = "proto2";

                message Test {
                  reserved 150 to 200;
                  extensions 100 to 199;
                }
                """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(proto));
    }

    @Test
    public void testFieldNumberZero() {
        var proto = """
                syntax = "proto3";

                message Test {
                  string field = 0;
                }
                """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(proto));
    }

    @Test
    public void testFieldNumberNegative() {
        var proto = """
                syntax = "proto3";

                message Test {
                  string field = -1;
                }
                """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(proto));
    }

    @Test
    public void testFieldNumberMaximumValid() {
        var proto = """
                syntax = "proto3";

                message Test {
                  string field = 536870911;
                }
                """;
        var doc = ProtobufParser.parseOnly(proto);
        assertNotNull(doc);
    }

    @Test
    public void testFieldNumberAboveMaximum() {
        var proto = """
                syntax = "proto3";

                message Test {
                  string field = 536870912;
                }
                """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(proto));
    }

    @Test
    public void testFieldNumberReservedImplementationRange() {
        var proto = """
                syntax = "proto3";

                message Test {
                  string field1 = 19000;
                }
                """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(proto));

        var proto2 = """
                syntax = "proto3";

                message Test {
                  string field2 = 19999;
                }
                """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(proto2));
    }

    @Test
    public void testFieldNumberReservedRangeBoundaries() {
        var proto = """
                syntax = "proto3";

                message Test {
                  string f1 = 18999;
                  string f2 = 20000;
                }
                """;
        var doc = ProtobufParser.parseOnly(proto);
        assertNotNull(doc);
    }

    @Test
    public void testReservedRangeInvalidOrder() {
        var proto = """
                syntax = "proto3";

                message Test {
                  reserved 10 to 5;
                }
                """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(proto));
    }

    @Test
    public void testReservedRangesOverlapping() {
        var proto = """
                syntax = "proto3";

                message Test {
                  reserved 1 to 10, 5 to 15;
                }
                """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(proto));
    }

    @Test
    public void testReservedRangesAdjacent() {
        var proto = """
                syntax = "proto3";

                message Test {
                  reserved 1 to 10, 11 to 20;
                }
                """;
        var doc = ProtobufParser.parseOnly(proto);
        assertNotNull(doc);
    }

    @Test
    public void testReservedRangesSameValue() {
        var proto = """
                syntax = "proto3";

                message Test {
                  reserved 5 to 5, 10 to 10;
                }
                """;
        var doc = ProtobufParser.parseOnly(proto);
        assertNotNull(doc);
    }

    @Test
    public void testReservedMixedNumbersAndNames() {
        var proto = """
                syntax = "proto3";

                message Test {
                  reserved 1, "foo", 2;
                }
                """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(proto));
    }

    @Test
    public void testReservedNumberZero() {
        var proto = """
                syntax = "proto3";

                message Test {
                  reserved 0;
                }
                """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(proto));
    }

    @Test
    public void testReservedNumberAboveMaximum() {
        var proto = """
                syntax = "proto3";

                message Test {
                  reserved 536870912;
                }
                """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(proto));
    }

    @Test
    public void testFieldUsesReservedNumber() {
        var proto = """
                syntax = "proto3";

                message Test {
                  reserved 5, 10 to 20;
                  string field = 15;
                }
                """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(proto));
    }

    @Test
    public void testFieldUsesReservedName() {
        var proto = """
                syntax = "proto3";

                message Test {
                  reserved "foo", "bar";
                  string foo = 1;
                }
                """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(proto));
    }

    @Test
    public void testOneofEmpty() {
        var proto = """
                syntax = "proto3";

                message Test {
                  oneof choice {
                  }
                }
                """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(proto));
    }

    @Test
    public void testOneofFieldWithRepeatedModifier() {
        var proto = """
                syntax = "proto3";

                message Test {
                  oneof choice {
                    repeated string names = 1;
                  }
                }
                """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(proto));
    }

    @Test
    public void testOneofFieldWithOptionalModifier() {
        var proto = """
                syntax = "proto3";

                message Test {
                  oneof choice {
                    optional string name = 1;
                  }
                }
                """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(proto));
    }

    @Test
    public void testOneofFieldWithMapType() {
        var proto = """
                syntax = "proto3";

                message Test {
                  oneof choice {
                    map<string, int32> data = 1;
                  }
                }
                """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(proto));
    }

    @Test
    public void testOneofFieldNumberDuplicate() {
        var proto = """
                syntax = "proto3";

                message Test {
                  oneof choice {
                    string name = 1;
                    int32 id = 1;
                  }
                }
                """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(proto));
    }

    @Test
    public void testOneofFieldNameDuplicate() {
        var proto = """
                syntax = "proto3";

                message Test {
                  oneof choice {
                    string name = 1;
                    string name = 2;
                  }
                }
                """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(proto));
    }

    @Test
    public void testEnumFirstValueMustBeZeroProto3() {
        var proto = """
                syntax = "proto3";

                enum Test {
                  VALUE = 1;
                }
                """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(proto));
    }

    @Test
    public void testEnumFirstValueNonZeroProto2() {
        var proto = """
                syntax = "proto2";

                enum Test {
                  VALUE = 1;
                }
                """;
        var doc = ProtobufParser.parseOnly(proto);
        assertNotNull(doc);
    }

    @Test
    public void testEnumValueMaximumInt32() {
        var proto = """
                syntax = "proto2";

                enum Test {
                  MAX = 2147483647;
                }
                """;
        var doc = ProtobufParser.parseOnly(proto);
        assertNotNull(doc);
    }

    @Test
    public void testEnumValueMinimumInt32() {
        var proto = """
                syntax = "proto2";

                enum Test {
                  MIN = -2147483648;
                }
                """;
        var doc = ProtobufParser.parseOnly(proto);
        assertNotNull(doc);
    }

    @Test
    public void testEnumValueAboveMaxInt32() {
        var proto = """
                syntax = "proto3";

                enum Test {
                  ZERO = 0;
                  TOO_LARGE = 2147483648;
                }
                """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(proto));
    }

    @Test
    public void testEnumValueBelowMinInt32() {
        var proto = """
                syntax = "proto3";

                enum Test {
                  ZERO = 0;
                  TOO_SMALL = -2147483649;
                }
                """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(proto));
    }

    @Test
    public void testEnumDuplicateValueWithoutAllowAlias() {
        var proto = """
                syntax = "proto3";

                enum Test {
                  A = 0;
                  B = 1;
                  C = 1;
                }
                """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(proto));
    }

    @Test
    public void testEnumDuplicateValueWithAllowAlias() {
        var proto = """
                syntax = "proto3";

                enum Test {
                  option allow_alias = true;
                  A = 0;
                  B = 1;
                  C = 1;
                }
                """;
        var doc = ProtobufParser.parseOnly(proto);
        assertNotNull(doc);
    }

    @Test
    public void testEnumReservedValueConflict() {
        var proto = """
                syntax = "proto3";

                enum Test {
                  reserved 5, 10 to 20;
                  ZERO = 0;
                  INVALID = 15;
                }
                """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(proto));
    }

    @Test
    public void testEnumReservedNameConflict() {
        var proto = """
                syntax = "proto3";

                enum Test {
                  reserved "FOO", "BAR";
                  ZERO = 0;
                  FOO = 1;
                }
                """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(proto));
    }

    @Test
    public void testPackageNameStartsWithDot() {
        var proto = """
                syntax = "proto3";
                package .foo.bar;

                message Test {
                  string value = 1;
                }
                """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(proto));
    }

    @Test
    public void testPackageNameEndsWithDot() {
        var proto = """
                syntax = "proto3";
                package foo.bar.;

                message Test {
                  string value = 1;
                }
                """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(proto));
    }

    @Test
    public void testPackageNameConsecutiveDots() {
        var proto = """
                syntax = "proto3";
                package foo..bar;

                message Test {
                  string value = 1;
                }
                """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(proto));
    }

    @Test
    public void testFieldNameConflictWithNestedMessage() {
        var proto = """
                syntax = "proto3";

                message Test {
                  string Inner = 1;

                  message Inner {
                    string value = 1;
                  }
                }
                """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(proto));
    }

    @Test
    public void testFieldNameConflictWithEnumConstant() {
        var proto = """
                syntax = "proto3";

                message Test {
                  string ACTIVE = 1;

                  enum Status {
                    UNKNOWN = 0;
                    ACTIVE = 1;
                  }
                }
                """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(proto));
    }

    @Test
    public void testFieldNumberDuplicateAcrossOneof() {
        var proto = """
                syntax = "proto3";

                message Test {
                  string regular = 1;
                  oneof choice {
                    string option = 1;
                  }
                }
                """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(proto));
    }

    @Test
    public void testRequiredFieldProto2() {
        var proto = """
                syntax = "proto2";

                message Test {
                  required string name = 1;
                }
                """;
        var doc = ProtobufParser.parseOnly(proto);
        assertNotNull(doc);
    }

    @Test
    public void testRequiredFieldProto3() {
        var proto = """
                syntax = "proto3";

                message Test {
                  required string name = 1;
                }
                """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(proto));
    }

    @Test
    public void testDefaultValueProto2() {
        var proto = """
                syntax = "proto2";

                message Test {
                  optional string name = 1 [default = "test"];
                }
                """;
        var doc = ProtobufParser.parseOnly(proto);
        assertNotNull(doc);
    }

    @Test
    public void testDefaultValueProto3() {
        var proto = """
                syntax = "proto3";

                message Test {
                  string name = 1 [default = "test"];
                }
                """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(proto));
    }

    @Test
    public void testDefaultValueOnRepeatedField() {
        var proto = """
                syntax = "proto2";

                message Test {
                  repeated string names = 1 [default = "test"];
                }
                """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(proto));
    }

    @Test
    public void testDefaultValueTypeMismatch() {
        var proto = """
                syntax = "proto2";

                message Test {
                  optional int32 num = 1 [default = "not_a_number"];
                }
                """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(proto));
    }

    @Test
    public void testPackedOptionOnRepeatedNumericField() {
        var proto = """
                syntax = "proto3";

                message Test {
                  repeated int32 numbers = 1 [packed = true];
                }
                """;
        var doc = ProtobufParser.parseOnly(proto);
        assertNotNull(doc);
    }

    @Test
    public void testPackedOptionOnNonRepeatedField() {
        var proto = """
                syntax = "proto3";

                message Test {
                  int32 number = 1 [packed = true];
                }
                """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(proto));
    }

    @Test
    public void testPackedOptionOnNonNumericField() {
        var proto = """
                syntax = "proto3";

                message Test {
                  repeated string names = 1 [packed = true];
                }
                """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(proto));
    }

    @Test
    public void testDeeplyNestedMessages() {
        var proto = """
                syntax = "proto3";

                message L1 {
                  message L2 {
                    message L3 {
                      message L4 {
                        message L5 {
                          string value = 1;
                        }
                        L5 field = 1;
                      }
                      L4 field = 1;
                    }
                    L3 field = 1;
                  }
                  L2 field = 1;
                }
                """;
        var doc = ProtobufParser.parseOnly(proto);
        assertNotNull(doc);
    }

    @Test
    public void testComplexExtensionRanges() {
        var proto = """
                syntax = "proto2";

                message Test {
                  extensions 1, 3 to 5, 10, 100 to 200, 500 to max;
                  optional string field = 2;
                  reserved 6 to 9, 50 to 99, 300 to 499;
                }

                extend Test {
                  optional string e1 = 1;
                  optional string e2 = 4;
                  optional string e3 = 10;
                  optional string e4 = 150;
                  optional string e5 = 536870911;
                }
                """;
        var doc = ProtobufParser.parseOnly(proto);
        assertNotNull(doc);
    }

    // Additional comprehensive tests for spec compliance

    @Test
    public void testFieldNumberOne() {
        var proto = """
                syntax = "proto3";

                message Test {
                  string field = 1;
                }
                """;
        var doc = ProtobufParser.parseOnly(proto);
        assertNotNull(doc);
    }

    @Test
    public void testFieldNumberJustBeforeReservedRange() {
        var proto = """
                syntax = "proto3";

                message Test {
                  string field = 18999;
                }
                """;
        var doc = ProtobufParser.parseOnly(proto);
        assertNotNull(doc);
    }

    @Test
    public void testFieldNumberJustAfterReservedRange() {
        var proto = """
                syntax = "proto3";

                message Test {
                  string field = 20000;
                }
                """;
        var doc = ProtobufParser.parseOnly(proto);
        assertNotNull(doc);
    }

    @Test
    public void testFieldNumberAtReservedRangeLowerBound() {
        var proto = """
                syntax = "proto3";

                message Test {
                  string field = 19000;
                }
                """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(proto));
    }

    @Test
    public void testFieldNumberInMiddleOfReservedRange() {
        var proto = """
                syntax = "proto3";

                message Test {
                  string field = 19500;
                }
                """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(proto));
    }

    @Test
    public void testDuplicateEnumValueNames() {
        var proto = """
                syntax = "proto3";

                enum Status {
                  UNKNOWN = 0;
                  ACTIVE = 1;
                  ACTIVE = 2;
                }
                """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(proto));
    }

    @Test
    public void testEnumWithOnlyZeroValue() {
        var proto = """
                syntax = "proto3";

                enum Status {
                  UNKNOWN = 0;
                }
                """;
        var doc = ProtobufParser.parseOnly(proto);
        assertNotNull(doc);
    }

    @Test
    public void testEnumValueNegativeNumber() {
        var proto = """
                syntax = "proto2";

                enum Status {
                  UNKNOWN = -1;
                }
                """;
        var doc = ProtobufParser.parseOnly(proto);
        assertNotNull(doc);
    }

    @Test
    public void testMapWithValidStringKey() {
        var proto = """
                syntax = "proto3";

                message Test {
                  map<string, int32> data = 1;
                }
                """;
        var doc = ProtobufParser.parseOnly(proto);
        assertNotNull(doc);
    }

    @Test
    public void testMapWithValidBoolKey() {
        var proto = """
                syntax = "proto3";

                message Test {
                  map<bool, string> data = 1;
                }
                """;
        var doc = ProtobufParser.parseOnly(proto);
        assertNotNull(doc);
    }

    @Test
    public void testMapWithValidInt32Key() {
        var proto = """
                syntax = "proto3";

                message Test {
                  map<int32, string> data = 1;
                }
                """;
        var doc = ProtobufParser.parseOnly(proto);
        assertNotNull(doc);
    }

    @Test
    public void testMapWithValidInt64Key() {
        var proto = """
                syntax = "proto3";

                message Test {
                  map<int64, string> data = 1;
                }
                """;
        var doc = ProtobufParser.parseOnly(proto);
        assertNotNull(doc);
    }

    @Test
    public void testMapWithValidUint32Key() {
        var proto = """
                syntax = "proto3";

                message Test {
                  map<uint32, string> data = 1;
                }
                """;
        var doc = ProtobufParser.parseOnly(proto);
        assertNotNull(doc);
    }

    @Test
    public void testMapWithValidUint64Key() {
        var proto = """
                syntax = "proto3";

                message Test {
                  map<uint64, string> data = 1;
                }
                """;
        var doc = ProtobufParser.parseOnly(proto);
        assertNotNull(doc);
    }

    @Test
    public void testMapWithValidSint32Key() {
        var proto = """
                syntax = "proto3";

                message Test {
                  map<sint32, string> data = 1;
                }
                """;
        var doc = ProtobufParser.parseOnly(proto);
        assertNotNull(doc);
    }

    @Test
    public void testMapWithValidSint64Key() {
        var proto = """
                syntax = "proto3";

                message Test {
                  map<sint64, string> data = 1;
                }
                """;
        var doc = ProtobufParser.parseOnly(proto);
        assertNotNull(doc);
    }

    @Test
    public void testMapWithBytesKeyError() {
        var proto = """
                syntax = "proto3";

                message Test {
                  map<bytes, string> data = 1;
                }
                """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(proto));
    }

    @Test
    public void testMapWithMessageValue() {
        var proto = """
                syntax = "proto3";

                message Value {
                  string data = 1;
                }

                message Test {
                  map<string, Value> data = 1;
                }
                """;
        var doc = ProtobufParser.parseOnly(proto);
        assertNotNull(doc);
    }

    @Test
    public void testMapWithEnumValue() {
        var proto = """
                syntax = "proto3";

                enum Status {
                  UNKNOWN = 0;
                  ACTIVE = 1;
                }

                message Test {
                  map<string, Status> data = 1;
                }
                """;
        var doc = ProtobufParser.parseOnly(proto);
        assertNotNull(doc);
    }

    @Test
    public void testOneofWithSingleField() {
        var proto = """
                syntax = "proto3";

                message Test {
                  oneof choice {
                    string name = 1;
                  }
                }
                """;
        var doc = ProtobufParser.parseOnly(proto);
        assertNotNull(doc);
    }

    @Test
    public void testOneofWithMultipleFields() {
        var proto = """
                syntax = "proto3";

                message Test {
                  oneof choice {
                    string name = 1;
                    int32 id = 2;
                    bool active = 3;
                  }
                }
                """;
        var doc = ProtobufParser.parseOnly(proto);
        assertNotNull(doc);
    }

    @Test
    public void testMultipleOneofsInSameMessage() {
        var proto = """
                syntax = "proto3";

                message Test {
                  oneof choice1 {
                    string name = 1;
                  }
                  oneof choice2 {
                    int32 id = 2;
                  }
                }
                """;
        var doc = ProtobufParser.parseOnly(proto);
        assertNotNull(doc);
    }

    @Test
    public void testOneofFieldNumberConflictWithRegularField() {
        var proto = """
                syntax = "proto3";

                message Test {
                  string regular = 1;
                  oneof choice {
                    int32 option = 1;
                  }
                }
                """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(proto));
    }

    @Test
    public void testReservedRangeSingleNumber() {
        var proto = """
                syntax = "proto3";

                message Test {
                  reserved 5;
                  string field = 1;
                }
                """;
        var doc = ProtobufParser.parseOnly(proto);
        assertNotNull(doc);
    }

    @Test
    public void testReservedRangeMultipleNumbers() {
        var proto = """
                syntax = "proto3";

                message Test {
                  reserved 2, 5, 8, 10;
                  string field = 1;
                }
                """;
        var doc = ProtobufParser.parseOnly(proto);
        assertNotNull(doc);
    }

    @Test
    public void testReservedRangeWithMaxKeyword() {
        var proto = """
                syntax = "proto3";

                message Test {
                  reserved 1000 to max;
                }
                """;
        var doc = ProtobufParser.parseOnly(proto);
        assertNotNull(doc);
    }

    @Test
    public void testReservedNamesOnly() {
        var proto = """
                syntax = "proto3";

                message Test {
                  reserved "foo", "bar", "baz";
                  string field = 1;
                }
                """;
        var doc = ProtobufParser.parseOnly(proto);
        assertNotNull(doc);
    }

    @Test
    public void testReservedMultipleStatements() {
        var proto = """
                syntax = "proto3";

                message Test {
                  reserved 1, 2, 3;
                  reserved 10 to 20;
                  reserved "foo", "bar";
                  string field = 4;
                }
                """;
        var doc = ProtobufParser.parseOnly(proto);
        assertNotNull(doc);
    }

    @Test
    public void testFieldNameConflictWithOneof() {
        var proto = """
                syntax = "proto3";

                message Test {
                  string choice = 1;
                  oneof choice {
                    int32 option = 2;
                  }
                }
                """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(proto));
    }

    @Test
    public void testNestedMessageWithSameName() {
        var proto = """
                syntax = "proto3";

                message Test {
                  message Test {
                    string value = 1;
                  }
                  Test field = 1;
                }
                """;
        var doc = ProtobufParser.parseOnly(proto);
        assertNotNull(doc);
    }

    @Test
    public void testGroupInProto3Error() {
        var proto = """
                syntax = "proto3";

                message Test {
                  optional group Result = 1 {
                    string url = 2;
                  }
                }
                """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(proto));
    }

    @Test
    public void testGroupInProto2() {
        var proto = """
                syntax = "proto2";

                message Test {
                  optional group Result = 1 {
                    optional string url = 2;
                  }
                }
                """;
        var doc = ProtobufParser.parseOnly(proto);
        assertNotNull(doc);
    }

    @Test
    public void testOptionalInProto3WithExplicitLabel() {
        var proto = """
                syntax = "proto3";

                message Test {
                  optional string name = 1;
                }
                """;
        var doc = ProtobufParser.parseOnly(proto);
        assertNotNull(doc);
    }

    @Test
    public void testRepeatedMessageField() {
        var proto = """
                syntax = "proto3";

                message Item {
                  string name = 1;
                }

                message Test {
                  repeated Item items = 1;
                }
                """;
        var doc = ProtobufParser.parseOnly(proto);
        assertNotNull(doc);
    }

    @Test
    public void testRepeatedEnumField() {
        var proto = """
                syntax = "proto3";

                enum Status {
                  UNKNOWN = 0;
                  ACTIVE = 1;
                }

                message Test {
                  repeated Status statuses = 1;
                }
                """;
        var doc = ProtobufParser.parseOnly(proto);
        assertNotNull(doc);
    }

    @Test
    public void testPackedOptionOnRepeatedMessageFieldError() {
        var proto = """
                syntax = "proto3";

                message Item {
                  string name = 1;
                }

                message Test {
                  repeated Item items = 1 [packed = true];
                }
                """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(proto));
    }

    @Test
    public void testPackedOptionOnRepeatedBoolField() {
        var proto = """
                syntax = "proto3";

                message Test {
                  repeated bool flags = 1 [packed = true];
                }
                """;
        var doc = ProtobufParser.parseOnly(proto);
        assertNotNull(doc);
    }

    @Test
    public void testPackedOptionOnRepeatedInt32Field() {
        var proto = """
                syntax = "proto3";

                message Test {
                  repeated int32 numbers = 1 [packed = true];
                }
                """;
        var doc = ProtobufParser.parseOnly(proto);
        assertNotNull(doc);
    }

    @Test
    public void testDeprecatedOption() {
        var proto = """
                syntax = "proto3";

                message Test {
                  string old_field = 1 [deprecated = true];
                }
                """;
        var doc = ProtobufParser.parseOnly(proto);
        assertNotNull(doc);
    }

    @Test
    public void testDefaultValueOnOptionalFieldProto2() {
        var proto = """
                syntax = "proto2";

                message Test {
                  optional int32 number = 1 [default = 42];
                }
                """;
        var doc = ProtobufParser.parseOnly(proto);
        assertNotNull(doc);
    }

    @Test
    public void testDefaultValueStringFieldProto2() {
        var proto = """
                syntax = "proto2";

                message Test {
                  optional string name = 1 [default = "default"];
                }
                """;
        var doc = ProtobufParser.parseOnly(proto);
        assertNotNull(doc);
    }

    @Test
    public void testDefaultValueBoolFieldProto2() {
        var proto = """
                syntax = "proto2";

                message Test {
                  optional bool active = 1 [default = true];
                }
                """;
        var doc = ProtobufParser.parseOnly(proto);
        assertNotNull(doc);
    }

    @Test
    public void testDefaultValueEnumFieldProto2() {
        var proto = """
                syntax = "proto2";

                enum Status {
                  UNKNOWN = 0;
                  ACTIVE = 1;
                }

                message Test {
                  optional Status status = 1 [default = ACTIVE];
                }
                """;
        var doc = ProtobufParser.parseOnly(proto);
        assertNotNull(doc);
    }

    @Test
    public void testDefaultValueFloatFieldProto2() {
        var proto = """
                syntax = "proto2";

                message Test {
                  optional float ratio = 1 [default = 1.5];
                }
                """;
        var doc = ProtobufParser.parseOnly(proto);
        assertNotNull(doc);
    }

    @Test
    public void testDefaultValueOnMapFieldError() {
        var proto = """
                syntax = "proto2";

                message Test {
                  map<string, int32> data = 1 [default = {}];
                }
                """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(proto));
    }

    @Test
    public void testExtensionInProto3Error() {
        var proto = """
                syntax = "proto3";

                message Foo {
                  extensions 100 to 199;
                }
                """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(proto));
    }

    @Test
    public void testExtensionRangeSingleNumber() {
        var proto = """
                syntax = "proto2";

                message Test {
                  extensions 100;
                }
                """;
        var doc = ProtobufParser.parseOnly(proto);
        assertNotNull(doc);
    }

    @Test
    public void testExtensionNonOverlappingRanges() {
        var proto = """
                syntax = "proto2";

                message Test {
                  extensions 100 to 200;
                  extensions 300 to 400;
                }
                """;
        var doc = ProtobufParser.parseOnly(proto);
        assertNotNull(doc);
    }

    @Test
    public void testExtensionAdjacentRanges() {
        var proto = """
                syntax = "proto2";

                message Test {
                  extensions 100 to 200;
                  extensions 201 to 300;
                }
                """;
        var doc = ProtobufParser.parseOnly(proto);
        assertNotNull(doc);
    }

    @Test
    public void testFieldNumberConflictAcrossNestedMessages() {
        var proto = """
                syntax = "proto3";

                message Outer {
                  string field = 1;
                  message Inner {
                    string field = 1;
                  }
                }
                """;
        var doc = ProtobufParser.parseOnly(proto);
        assertNotNull(doc);
    }

    @Test
    public void testEnumValueConflictWithinEnum() {
        var proto = """
                syntax = "proto3";

                enum Status {
                  UNKNOWN = 0;
                  ACTIVE = 1;
                  UNKNOWN = 2;
                }
                """;
        assertThrows(ProtobufParserException.class, () -> ProtobufParser.parseOnly(proto));
    }

    @Test
    public void testEnumReservedNonOverlappingRanges() {
        var proto = """
                syntax = "proto3";

                enum Status {
                  reserved 1 to 10;
                  reserved 15 to 20;
                  UNKNOWN = 0;
                }
                """;
        var doc = ProtobufParser.parseOnly(proto);
        assertNotNull(doc);
    }

    @Test
    public void testMessageWithAllScalarTypes() {
        var proto = """
                syntax = "proto3";

                message Test {
                  double f1 = 1;
                  float f2 = 2;
                  int32 f3 = 3;
                  int64 f4 = 4;
                  uint32 f5 = 5;
                  uint64 f6 = 6;
                  sint32 f7 = 7;
                  sint64 f8 = 8;
                  fixed32 f9 = 9;
                  fixed64 f10 = 10;
                  sfixed32 f11 = 11;
                  sfixed64 f12 = 12;
                  bool f13 = 13;
                  string f14 = 14;
                  bytes f15 = 15;
                }
                """;
        var doc = ProtobufParser.parseOnly(proto);
        assertNotNull(doc);
    }

    @Test
    public void testServiceWithEmptyBody() {
        var proto = """
                syntax = "proto3";

                service TestService {
                }
                """;
        var doc = ProtobufParser.parseOnly(proto);
        assertNotNull(doc);
    }

    @Test
    public void testServiceWithSingleMethod() {
        var proto = """
                syntax = "proto3";

                message Request {}
                message Response {}

                service TestService {
                  rpc Method(Request) returns (Response);
                }
                """;
        var doc = ProtobufParser.parseOnly(proto);
        assertNotNull(doc);
    }

    @Test
    public void testServiceWithStreamingRequest() {
        var proto = """
                syntax = "proto3";

                message Request {}
                message Response {}

                service TestService {
                  rpc Method(stream Request) returns (Response);
                }
                """;
        var doc = ProtobufParser.parseOnly(proto);
        assertNotNull(doc);
    }

    @Test
    public void testServiceWithStreamingResponse() {
        var proto = """
                syntax = "proto3";

                message Request {}
                message Response {}

                service TestService {
                  rpc Method(Request) returns (stream Response);
                }
                """;
        var doc = ProtobufParser.parseOnly(proto);
        assertNotNull(doc);
    }

    @Test
    public void testServiceWithBidirectionalStreaming() {
        var proto = """
                syntax = "proto3";

                message Request {}
                message Response {}

                service TestService {
                  rpc Method(stream Request) returns (stream Response);
                }
                """;
        var doc = ProtobufParser.parseOnly(proto);
        assertNotNull(doc);
    }
}
