package it.auties.protobuf.cli;

import picocli.CommandLine;

public class ProtobufCLI {
    public static void main(String... args) {
        var exitCode = new CommandLine(new BaseCommand()).execute(args);
        System.exit(exitCode);
    }
}
