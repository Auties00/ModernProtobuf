package it.auties.protobuf.utils;

import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;

import javax.lang.model.SourceVersion;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

@UtilityClass
public class ProtobufUtils {
    // This method is used inside the Message Model generator
    @SuppressWarnings("unused")
    public String toValidIdentifier(String identifier){
        return SourceVersion.isKeyword(identifier) ? "_%s".formatted(identifier) : identifier;
    }

    @SneakyThrows
    public String readGenerator(String name){
        var stream = ProtobufUtils.class.getClassLoader().getResourceAsStream("%s.java".formatted(name));
        return new String(Objects.requireNonNull(stream).readAllBytes(), StandardCharsets.UTF_8);
    }
}
