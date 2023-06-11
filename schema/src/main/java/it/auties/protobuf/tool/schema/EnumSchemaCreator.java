package it.auties.protobuf.tool.schema;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.*;
import it.auties.protobuf.base.ProtobufMessage;
import it.auties.protobuf.parser.statement.ProtobufEnumStatement;

import java.nio.file.Path;
import java.util.List;

import static com.github.javaparser.StaticJavaParser.parseClassOrInterfaceType;
import static com.github.javaparser.StaticJavaParser.parseType;

final class EnumSchemaCreator extends SchemaCreator<ProtobufEnumStatement> {
    EnumSchemaCreator(ProtobufEnumStatement protoStatement, boolean mutable, List<CompilationUnit> classPool) {
        super(protoStatement, mutable, classPool);
    }

    @Override
    CompilationUnit generate(Path output) {
        var compilationUnit = createCompilationUnit();
        compilationUnit.addType(generate());
        return compilationUnit;
    }

    @Override
    TypeDeclaration<?> generate() {
        var ctEnum = new EnumDeclaration(NodeList.nodeList(Modifier.publicModifier()), protoStatement.name());
        ctEnum.setImplementedTypes(NodeList.nodeList(parseClassOrInterfaceType(ProtobufMessage.class.getSimpleName())));
        getDeferredImplementation(protoStatement.name())
                .ifPresent(entry -> ctEnum.addImplementedType(entry.getNameAsString()));
        addEnumConstants(ctEnum);
        addIndexFieldAndConstructor(ctEnum);
        addReservedAnnotation(ctEnum);
        return ctEnum;
    }

    private void addEnumConstants(EnumDeclaration ctEnum) {
        for(var statement : protoStatement.statements()){
            var name = ctEnum.addEnumConstant(statement.name());
            name.addArgument(new IntegerLiteralExpr(String.valueOf(statement.index())));
        }
    }

    private void addIndexFieldAndConstructor(EnumDeclaration ctEnum) {
        var intType = parseType("int");
        ctEnum.addField(intType, "index", Modifier.Keyword.PRIVATE, Modifier.Keyword.FINAL);
        var constructor = ctEnum.addConstructor();
        var indexParameter = new Parameter(intType, "index");
        constructor.addParameter(indexParameter);
        var selectFieldExpression = new FieldAccessExpr(new ThisExpr(), "index");
        var selectParameterExpression = new NameExpr("index");
        var assignment = new AssignExpr(selectFieldExpression, selectParameterExpression, AssignExpr.Operator.ASSIGN);
        constructor.getBody().addStatement(assignment);
    }

    @Override
    CompilationUnit update(Path output) {
        return null;
    }

    @Override
    TypeDeclaration<?> update() {
        return null;
    }
}
