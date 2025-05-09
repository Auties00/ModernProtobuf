package it.auties.protobuf.serialization.writer;

import java.io.PrintWriter;
import java.io.Writer;
import java.util.Locale;

abstract class JavaWriter extends PrintWriter {
    int level;

    public JavaWriter(Writer out) {
        super(out);
    }

    @Override
    public void print(String s) {
        super.print("    ".repeat(level) + s);
    }

    @Override
    public PrintWriter format(String format, Object... args) {
        return super.format("    ".repeat(level) + format, args);
    }

    @Override
    public PrintWriter format(Locale locale, String format, Object... args) {
        return super.format(locale, "    ".repeat(level) + format, args);
    }

    public void printSeparator() {
        println();
    }
}
