package it.auties.protobuf.application;

import it.auties.protobuf.commands.BaseCommand;
import picocli.CommandLine;

public class ModernProtocApplication {
    public static void main(String... args) {
        ModuleOpener.openJavac();
        var exitCode = new CommandLine(new BaseCommand()).execute(args);
        System.exit(exitCode);
    }
}
