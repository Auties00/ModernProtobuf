package it.auties.protobuf.tool.application;

import it.auties.protobuf.tool.command.BaseCommand;
import picocli.CommandLine;

public class ModernProtocApplication {
    public static void main(String... args) {
        var exitCode = new CommandLine(new BaseCommand()).execute(args);
        System.exit(exitCode);
    }
}
