package it.auties.protobuf.commands;

import picocli.CommandLine.Command;

@Command(mixinStandardHelpOptions = true, version = "Protobuf4j 1.0", description = "A custom implementation of protobuf written in Java 16", subcommands = {GenerateSchemaCommand.class, DecodeCommand.class})
public class BaseCommand {
}
