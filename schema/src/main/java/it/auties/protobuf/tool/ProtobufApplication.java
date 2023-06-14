package it.auties.protobuf.tool;

import it.auties.protobuf.tool.command.BaseCommand;
import picocli.CommandLine;

public class ProtobufApplication {
    public static void main(String... args) {
        var exitCode = new CommandLine(new BaseCommand(){}).execute(args);
        System.exit(exitCode);
    }
}
