package it.auties.protobuf.schema;

import it.auties.protobuf.schema.command.BaseCommand;
import picocli.CommandLine;

public final class ProtobufApplication {
    void main(String... args) {
        var exitCode = new CommandLine(new BaseCommand(){})
                .execute(args);
        System.exit(exitCode);
    }
}
