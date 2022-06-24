package it.auties.protobuf.tool.util;

import lombok.experimental.UtilityClass;
import sun.misc.Unsafe;

@UtilityClass
public class LoggerUtils {
    public void suppressIllegalAccessWarning() {
        try {
            var unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
            unsafeField.setAccessible(true);
            var unsafe = (Unsafe) unsafeField.get(null);

            var loggerClass = Class.forName("jdk.internal.module.IllegalAccessLogger");
            var logger = loggerClass.getDeclaredField("logger");
            unsafe.putObjectVolatile(loggerClass, unsafe.staticFieldOffset(logger), null);
        } catch (Throwable ignored) {

        }
    }
}
