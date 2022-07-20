open module it.auties.protoc.tool {
    requires static lombok;

    requires info.picocli;
    requires com.fasterxml.jackson.core;
    requires com.fasterxml.jackson.databind;

    requires java.compiler;
    requires jdk.unsupported;
    requires jdk.compiler;

    requires org.apache.logging.log4j;
    requires org.apache.logging.log4j.core;

    requires it.auties.reflection;
    requires it.auties.protoc.api;
    requires it.auties.protoc.parser;
}