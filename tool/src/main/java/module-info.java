open module it.auties.protoc.tool {
    requires static lombok;

    requires info.picocli;

    requires org.codehaus.groovy;
    requires org.codehaus.groovy.templates;

    requires com.google.googlejavaformat;
    requires java.compiler;
    requires jdk.unsupported;
    requires jdk.compiler;

    requires org.apache.logging.log4j;
    requires org.apache.logging.log4j.core;

    requires it.auties.protoc.api;
    requires it.auties.protoc.parser;
}