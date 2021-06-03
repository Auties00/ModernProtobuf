open module protoc.tool {
    requires static lombok;
    requires info.picocli;
    requires com.google.googlejavaformat;
    requires protoc.parser;
    requires jdk.unsupported;
    requires java.compiler;
    requires org.apache.logging.log4j;
    requires protoc.decoder;
    requires org.apache.logging.log4j.core;
    requires com.google.common;
    requires org.codehaus.groovy;
    requires org.codehaus.groovy.templates;
}