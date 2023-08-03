package it.auties.protobuf.exception;

public class ProtobufAnnotationProcessorException extends ProtobufException {
    private static final String ERROR = """
            This method is generated automatically by the annotation processor.
            If you are seeing this exception, you should enable annotation processing and then add this snippet to your pom.xml:
            <build>
                 <plugins>
                    <plugin>
                        <groupId>org.apache.maven.plugins</groupId>
                        <artifactId>maven-compiler-plugin</artifactId>
                        <version>3.8.1</version>
                        <configuration>
                         <annotationProcessorPaths>
                             <annotationProcessorPath>
                              <groupId>com.github.auties00</groupId>
                              <artifactId>protobuf-serialization-plugin</artifactId>
                              <version>2.0.6</version>
                             </annotationProcessorPath>
                         </annotationProcessorPaths>
                        </configuration>
                    </plugin>
                 </plugins>
            </build>
            """;

    public ProtobufAnnotationProcessorException() {
        super(ERROR);
    }
}
