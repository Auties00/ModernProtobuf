package it.auties.protobuf.tool.command;

import picocli.CommandLine.Command;

@Command(
        mixinStandardHelpOptions = true,
        version = "ModernProtobuf 2.0.6",
        description = "A custom implementation of protobuf written in Java 17",
        subcommands = {GenerateCommand.class, UpdateCommand.class}
)
public class BaseCommand {

}
