package it.auties.protobuf.tool;

import it.auties.protobuf.parser.ProtobufParser;
import it.auties.protobuf.tool.util.AstUtils;
import spoon.reflect.declaration.CtClass;

public class Balls {
    public static void main(String[] args) {
        var test = """
                package it.auties;
                
                message Abc {
                  message Def {
                    enum Sigma {
                       UNKNOWN = 0;
                    }
                  }
                }
                """;
        var document = new ProtobufParser(test).tokenizeAndParse();
        System.out.println(document);
    }
}
