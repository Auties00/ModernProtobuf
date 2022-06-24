package it.auties.protobuf.tool.command;

import picocli.CommandLine.Command;

@Command(
        mixinStandardHelpOptions = true,
        version = "ModernProtoc 1.0",
        description = "A custom implementation of protobuf written in Java 17",
        subcommands = {GenerateSchemaCommand.class, DecodeCommand.class, UpdateCommand.class}
)
public class BaseCommand {

}
