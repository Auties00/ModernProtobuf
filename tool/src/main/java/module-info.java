module protoc.tool {
    requires info.picocli;
    requires lombok;
    requires com.google.googlejavaformat;
    requires write.it.once.core;
    requires protoc.parser;
    requires jdk.unsupported;
    requires java.compiler;
    requires org.apache.logging.log4j.core;
    requires protoc.decoder;
}