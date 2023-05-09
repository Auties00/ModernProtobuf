import it.auties.protobuf.base.ProtobufDeserializationException;
import it.auties.protobuf.base.ProtobufInputStream;

import java.util.ArrayList;
import java.util.HexFormat;

public class Testing {
    public static void main(String[] args) {
        ProtobufInputStream var2 = new ProtobufInputStream(HexFormat.of().parseHex("080108020803"));
        ArrayList var3 = new ArrayList();

        while(true) {
            int var4 = var2.readTag();
            if (var4 == 0) {
                break;
            }

            int var5 = var4 >>> 3;
            System.out.println("Field index: " + var5);
            int var6 = var4 & 7;
            switch (var5) {
                case 1:
                    switch (var6) {
                        case 0:
                            var3.add(var2.readInt32());
                            break;
                        case 2:
                            var3.addAll(var2.readInt32Packed());
                            break;
                        default:
                            throw ProtobufDeserializationException.invalidTag(var6);
                    }
                    break;
                default:
                    var2.readBytes();
            }
        }

        System.out.println(var3);
    }
}
