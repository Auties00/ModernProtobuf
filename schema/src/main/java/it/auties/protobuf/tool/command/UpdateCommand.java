package it.auties.protobuf.tool.command;

import it.auties.protobuf.parser.ProtobufParser;
import it.auties.protobuf.tool.schema.ProtobufSchemaCreator;
import it.auties.protobuf.tool.util.AstUtils;
import it.auties.protobuf.tool.util.LogProvider;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.File;
import java.io.IOException;
import java.lang.System.Logger.Level;
import java.util.concurrent.Callable;

@Command(
        name = "update",
        mixinStandardHelpOptions = true,
        version = "update 2.0.5",
        description = "Updates a protobuf model class using a schema"
)
public class UpdateCommand implements Callable<Integer>, LogProvider {
    @Parameters(
            index = "0",
            description = "The protobuf file used to update the java classes"
    )
    private File protobuf = null;
    
    @Parameters(
            index = "1",
            description = "The input classes to update"
    )
    private File input = null;
    
    @Option(
            names = {"-o", "--output"},
            description = "The package where new classes should be put"
    )
    private File output = null;

    @Option(
            names = {"-m", "--mutable"},
            description = "Whether the generated classes should have mutable fields"
    )
    private boolean mutable = false;

    @Override
    public Integer call() {
        try {
            log.log(Level.INFO, "Generating AST for protobuf file...");
            var parser = new ProtobufParser(protobuf);
            var document = parser.tokenizeAndParse();
            log.log(Level.INFO, "Generated AST successfully");
            log.log(Level.INFO, "Creating AST model from existing Java classes...");
            var classPool = AstUtils.createClassPool(input);
            log.log(Level.INFO, "Created AST model from existing Java classes");
            log.log(Level.INFO, "Starting update...");
            for (var entry : document.statements()) {
                log.log(Level.INFO, "Updating %s".formatted(entry));
                var creator = new ProtobufSchemaCreator(document);
                creator.update(entry, mutable, classPool, output.toPath());
            }
            log.log(Level.INFO, "Finished update successfully");
            return 0;
        } catch (IOException ex) {
            log.log(Level.ERROR, "Cannot parse Protobuf message", ex);
            return -1;
        }
    }
}
