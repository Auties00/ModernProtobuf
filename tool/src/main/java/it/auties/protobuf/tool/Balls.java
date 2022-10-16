package it.auties.protobuf.tool;

import it.auties.protobuf.tool.util.AstUtils;
import spoon.reflect.declaration.CtClass;

public class Balls {
    public static void main(String[] args) {
        var launcher = AstUtils.createLauncher();
        var code = launcher.getFactory()
                .createCodeSnippetStatement("class Abc { String abc; void abc() { if(this.abc == null) {} } }");
        System.out.println(code);
        CtClass<?> whatever = code.compile();
        System.out.println(whatever.toStringWithImports());
    }
}
