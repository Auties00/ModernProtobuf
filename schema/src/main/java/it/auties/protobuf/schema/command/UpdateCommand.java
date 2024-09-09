package it.auties.protobuf.schema.command;

import it.auties.protobuf.parser.ProtobufParser;
import it.auties.protobuf.schema.schema.ProtobufSchemaCreator;
import it.auties.protobuf.schema.util.AstUtils;
import it.auties.protobuf.schema.util.LogProvider;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.File;
import java.io.IOException;
import java.lang.System.Logger.Level;
import java.util.Objects;
import java.util.concurrent.Callable;

@Command(
        name = "update",
        mixinStandardHelpOptions = true,
        version = "update 3.3.0",
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

    @Option(
            names = {"-n", "--nullable"},
            description = "Whether the generated classes should have nullable fields (by default Optionals are used)"
    )
    private boolean nullable = false;

    @Override
    public Integer call() {
        try {
            log.log(Level.INFO, "Generating AST for protobuf file...");
            var parser = new ProtobufParser();
            var document = parser.parseOnly(protobuf.toPath());
            log.log(Level.INFO, "Generated AST successfully");
            log.log(Level.INFO, "Creating AST model from existing Java classes...");
            var classPool = AstUtils.createClassPool(input);
            log.log(Level.INFO, "Created AST model from existing Java classes");
            log.log(Level.INFO, "Starting update...");
            var creator = new ProtobufSchemaCreator(document, Objects.requireNonNullElse(output, input));
            creator.update(mutable, nullable, classPool);
            log.log(Level.INFO, "Finished update successfully");
            return 0;
        } catch (IOException ex) {
            log.log(Level.ERROR, "Cannot parse Protobuf message", ex);
            return -1;
        }
    }
}
