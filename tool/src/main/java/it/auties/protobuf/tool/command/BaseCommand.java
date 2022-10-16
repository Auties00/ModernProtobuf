package it.auties.protobuf.tool.command;

import picocli.CommandLine.Command;

@Command(
        mixinStandardHelpOptions = true,
        version = "ModernProtobuf 1.17",
        description = "A custom implementation of protobuf written in Java 17",
        subcommands = {GenerateSchemaCommand.class, UpdateCommand.class}
)
public class BaseCommand {

}
