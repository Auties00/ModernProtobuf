package it.auties.protobuf.schema.command;

import picocli.CommandLine.Command;

@Command(
        mixinStandardHelpOptions = true,
        version = "ModernProtobuf 3.0.0",
        description = "A custom implementation of protobuf written in Java 17",
        subcommands = {GenerateCommand.class, UpdateCommand.class}
)
public class BaseCommand {

}
