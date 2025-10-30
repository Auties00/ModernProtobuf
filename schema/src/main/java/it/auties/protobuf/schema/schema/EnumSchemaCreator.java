package it.auties.protobuf.schema.schema;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.nodeTypes.NodeWithAnnotations;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;
import it.auties.protobuf.annotation.ProtobufEnumIndex;
import it.auties.protobuf.parser.tree.ProtobufEnumChild;
import it.auties.protobuf.parser.tree.ProtobufEnumConstantStatement;
import it.auties.protobuf.parser.tree.ProtobufEnumStatement;
import it.auties.protobuf.parser.tree.ProtobufFieldStatement;
import it.auties.protobuf.schema.util.AstUtils;

import java.math.BigInteger;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static com.github.javaparser.StaticJavaParser.parseType;

final class EnumSchemaCreator extends BaseProtobufSchemaCreator<ProtobufEnumStatement> {
    EnumSchemaCreator(String packageName, ProtobufEnumStatement protoStatement, List<CompilationUnit> classPool, Path output) {
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
        var ctEnum = new EnumDeclaration(NodeList.nodeList(Modifier.publicModifier()), AstUtils.toJavaName(protoStatement.name()));
        allMembers.add(ctEnum);
        ctEnum.setParentNode(parent);
        addEnumName(ctEnum);
        getDeferredImplementations().forEach(entry -> addImplementedType(entry, ctEnum));
        createEnumConstructor(ctEnum);
        createEnumConstants(ctEnum);
        createIndexField(ctEnum);
        createIndexAccessor(ctEnum);
        addReservedAnnotation(ctEnum);
        return ctEnum;
    }

    private void addEnumName(NodeWithAnnotations<?> node) {
        var annotation = getOrAddAnnotation(node, it.auties.protobuf.annotation.ProtobufEnum.class);
        annotation.setPairs(NodeList.nodeList(new MemberValuePair("name", new StringLiteralExpr(protoStatement.qualifiedName()))));
        node.addAnnotation(annotation);
    }

    private void createEnumConstants(EnumDeclaration ctEnum) {
        for (ProtobufEnumChild statement : protoStatement.children()) {
            if(statement instanceof ProtobufEnumConstantStatement enumConstantStatement) {
                createEnumConstant(ctEnum, enumConstantStatement);
            }
        }
    }

    private void createEnumConstant(EnumDeclaration ctEnum, ProtobufFieldStatement statement) {
        var existing = getEnumConstant(ctEnum, statement);
        if(existing.isPresent()){
            return;
        }

        var name = ctEnum.addEnumConstant(AstUtils.toJavaName(statement.name()));
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

        return BigInteger.valueOf(intExpression.asNumber().intValue())
                .equals(statement.index().value());
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
