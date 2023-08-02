package it.auties.protobuf.tool.schema;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.EnumConstantDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;
import it.auties.protobuf.annotation.ProtobufEnumIndex;
import it.auties.protobuf.model.ProtobufEnum;
import it.auties.protobuf.parser.statement.ProtobufEnumStatement;
import it.auties.protobuf.parser.statement.ProtobufFieldStatement;
import lombok.NonNull;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static com.github.javaparser.StaticJavaParser.parseType;

final class EnumSchemaCreator extends SchemaCreator<ProtobufEnumStatement> {
    EnumSchemaCreator(String packageName, @NonNull ProtobufEnumStatement protoStatement, @NonNull List<CompilationUnit> classPool, Path output) {
        super(packageName, protoStatement, false, classPool, output);
    }

    @Override
    CompilationUnit generate() {
        var compilationUnit = createCompilationUnit(true);
        if(compilationUnit.existing()){
            return compilationUnit.compilationUnit();
        }

        var fresh = compilationUnit.compilationUnit();
        generate(fresh);
        return fresh;
    }

    @Override
    void generate(Node parent) {
        var ctEnum = new EnumDeclaration(NodeList.nodeList(Modifier.publicModifier()), protoStatement.name());
        linkToParent(parent, ctEnum);
        addImplementedType(ProtobufEnum.class.getSimpleName(), ctEnum);
        getDeferredImplementation(protoStatement.name())
                .ifPresent(entry -> addImplementedType(entry, ctEnum));
        addEnumConstants(ctEnum);
        addIndex(ctEnum);
        addReservedAnnotation(ctEnum);
    }

    private void addEnumConstants(EnumDeclaration ctEnum) {
        protoStatement.statements().forEach(statement -> addEnumConstant(ctEnum, statement));
    }

    private void addEnumConstant(EnumDeclaration ctEnum, ProtobufFieldStatement statement) {
        var existing = getEnumConstant(ctEnum, statement);
        if(existing.isPresent()){
            return;
        }

        var name = ctEnum.addEnumConstant(statement.name());
        name.addArgument(new IntegerLiteralExpr(String.valueOf(statement.index())));
    }

    private Optional<EnumConstantDeclaration> getEnumConstant(EnumDeclaration ctEnum, ProtobufFieldStatement statement) {
        return ctEnum.getEntries()
                .stream()
                .filter(entry -> getEnumConstant(statement, entry))
                .findFirst();
    }

    private boolean getEnumConstant(ProtobufFieldStatement statement, EnumConstantDeclaration entry) {
        if (entry.getArguments().isEmpty()) {
            return false;
        }

        var argument = entry.getArgument(0);
        if (!(argument instanceof IntegerLiteralExpr intExpression)) {
            return false;
        }

        return intExpression.asNumber().intValue() == statement.index();
    }

    private void addIndex(EnumDeclaration ctEnum) {
        var intType = parseType("int");
        ctEnum.addField(intType, "index", Modifier.Keyword.PRIVATE, Modifier.Keyword.FINAL);
        var constructor = ctEnum.addConstructor();
        var indexParameter = new Parameter(intType, "index");
        indexParameter.addAnnotation(new MarkerAnnotationExpr(ProtobufEnumIndex.class.getSimpleName()));
        constructor.addParameter(indexParameter);
        var selectFieldExpression = new FieldAccessExpr(new ThisExpr(), "index");
        var selectParameterExpression = new NameExpr("index");
        var assignment = new AssignExpr(selectFieldExpression, selectParameterExpression, AssignExpr.Operator.ASSIGN);
        constructor.getBody().addStatement(assignment);
        var method = new MethodDeclaration();
        method.setPublic(true);
        method.setType(intType);
        method.setName("index");
        method.addAnnotation(new MarkerAnnotationExpr(Override.class.getSimpleName()));
        var body = new BlockStmt();
        body.addStatement(new ReturnStmt(selectFieldExpression));
        method.setBody(body);
        ctEnum.addMember(method);
    }

    @Override
    Optional<CompilationUnit> update(String name) {
        var result = getTypeDeclaration(name, QueryType.ENUM);
        if (result.isEmpty()) {
            return Optional.of(generate());
        }

        var ctEnum = (EnumDeclaration) result.get().result();
        addEnumConstants(ctEnum);
        addIndex(ctEnum);
        addReservedAnnotation(ctEnum);
        return result.map(QueryResult::compilationUnit);
    }
}
