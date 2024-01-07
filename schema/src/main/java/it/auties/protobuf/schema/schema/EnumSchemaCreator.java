package it.auties.protobuf.schema.schema;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;
import it.auties.protobuf.annotation.ProtobufEnumIndex;
import it.auties.protobuf.model.ProtobufEnum;
import it.auties.protobuf.parser.tree.ProtobufEnumTree;
import it.auties.protobuf.parser.tree.ProtobufFieldTree;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static com.github.javaparser.StaticJavaParser.parseType;

final class EnumSchemaCreator extends BaseProtobufSchemaCreator<ProtobufEnumTree> {
    EnumSchemaCreator(String packageName, ProtobufEnumTree protoStatement, List<CompilationUnit> classPool, Path output) {
        super(packageName, protoStatement, false, classPool, output);
    }

    @Override
    CompilationUnit generate() {
        var compilationUnit = createCompilationUnit(true);
        if(compilationUnit.existing()){
            return compilationUnit.compilationUnit();
        }

        var fresh = compilationUnit.compilationUnit();
        fresh.addType(generate(fresh));
        return fresh;
    }

    @Override
    TypeDeclaration<?> generate(Node parent) {
        var ctEnum = new EnumDeclaration(NodeList.nodeList(Modifier.publicModifier()), protoStatement.name().orElseThrow());
        allMembers.add(ctEnum);
        ctEnum.setParentNode(parent);
        addNameAnnotation(ctEnum);
        addImplementedType(ProtobufEnum.class.getSimpleName(), ctEnum);
        getDeferredImplementations().forEach(entry -> addImplementedType(entry, ctEnum));
        createEnumConstructor(ctEnum);
        createEnumConstants(ctEnum);
        createIndexField(ctEnum);
        createIndexAccessor(ctEnum);
        addReservedAnnotation(ctEnum);
        return ctEnum;
    }

    private void createEnumConstants(EnumDeclaration ctEnum) {
        protoStatement.statements().forEach(statement -> createEnumConstant(ctEnum, statement));
    }

    private void createEnumConstant(EnumDeclaration ctEnum, ProtobufFieldTree statement) {
        var existing = getEnumConstant(ctEnum, statement);
        if(existing.isPresent()){
            return;
        }

        var name = ctEnum.addEnumConstant(statement.name().orElseThrow());
        name.addArgument(new IntegerLiteralExpr(String.valueOf(statement.index().orElseThrow())));
    }

    private Optional<EnumConstantDeclaration> getEnumConstant(EnumDeclaration ctEnum, ProtobufFieldTree statement) {
        return ctEnum.getEntries()
                .stream()
                .filter(entry -> getEnumConstant(statement, entry))
                .findFirst();
    }

    private boolean getEnumConstant(ProtobufFieldTree statement, EnumConstantDeclaration entry) {
        if (entry.getArguments().isEmpty()) {
            return false;
        }

        var argument = entry.getArgument(0);
        if (!(argument instanceof IntegerLiteralExpr intExpression)) {
            return false;
        }

        return intExpression.asNumber().intValue() == statement.index().orElseThrow();
    }

    private void createIndexField(EnumDeclaration ctEnum) {
        if(ctEnum.getFieldByName("index").isPresent()) {
            return;
        }

        var intType = parseType("int");
        ctEnum.addField(intType, "index", Modifier.Keyword.PRIVATE, Modifier.Keyword.FINAL);
    }

    private void createEnumConstructor(EnumDeclaration ctEnum) {
        if(!ctEnum.getConstructors().isEmpty()) {
            return;
        }

        var intType = parseType("int");
        var constructor = ctEnum.addConstructor();
        var indexParameter = new Parameter(intType, "index");
        indexParameter.addAnnotation(new MarkerAnnotationExpr(ProtobufEnumIndex.class.getSimpleName()));
        constructor.addParameter(indexParameter);
        var selectFieldExpression = new FieldAccessExpr(new ThisExpr(), "index");
        var selectParameterExpression = new NameExpr("index");
        var assignment = new AssignExpr(selectFieldExpression, selectParameterExpression, AssignExpr.Operator.ASSIGN);
        constructor.getBody().addStatement(assignment);
    }

    private void createIndexAccessor(EnumDeclaration ctEnum) {
        if(getMethod(ctEnum, "index").isPresent()) {
            return;
        }

        var intType = parseType("int");
        var method = new MethodDeclaration();
        method.setPublic(true);
        method.setType(intType);
        method.setName("index");
        var body = new BlockStmt();
        var selectFieldExpression = new FieldAccessExpr(new ThisExpr(), "index");
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
        createEnumConstructor(ctEnum);
        createEnumConstants(ctEnum);
        createIndexField(ctEnum);
        createIndexAccessor(ctEnum);
        addReservedAnnotation(ctEnum);
        return result.map(QueryResult::compilationUnit);
    }
}
