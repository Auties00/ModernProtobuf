package it.auties.protobuf.schema.schema;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.EnumConstantDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.IntegerLiteralExpr;
import com.github.javaparser.ast.expr.MemberValuePair;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.nodeTypes.NodeWithAnnotations;
import it.auties.protobuf.annotation.ProtobufEnum;
import it.auties.protobuf.parser.tree.ProtobufEnumChild;
import it.auties.protobuf.parser.tree.ProtobufEnumConstantStatement;
import it.auties.protobuf.parser.tree.ProtobufEnumStatement;
import it.auties.protobuf.parser.tree.ProtobufFieldStatement;
import it.auties.protobuf.schema.util.AstUtils;

import java.math.BigInteger;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

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
        createEnumConstants(ctEnum);
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

        var enumConstant = ctEnum.addEnumConstant(AstUtils.toJavaName(statement.name()));

        var enumConstantAnnotation = new NormalAnnotationExpr();
        enumConstantAnnotation.setName(ProtobufEnum.Constant.class.getCanonicalName());
        enumConstantAnnotation.addPair("index", statement.index().value().toString());

        enumConstant.addAnnotation(enumConstantAnnotation);
    }

    private Optional<EnumConstantDeclaration> getEnumConstant(EnumDeclaration ctEnum, ProtobufFieldStatement statement) {
        return ctEnum.getEntries()
                .stream()
                .filter(entry -> hasEnumConstant(statement, entry))
                .findFirst();
    }

    private boolean hasEnumConstant(ProtobufFieldStatement statement, EnumConstantDeclaration entry) {
        if (entry.getArguments().isEmpty()) {
            return false;
        }

        var argument = entry.getArgument(0);
        if (!(argument instanceof IntegerLiteralExpr intExpression)) {
            return false;
        }

        try {
            var enumConstantIndex = BigInteger.valueOf(intExpression.asNumber().intValue());
            return enumConstantIndex.equals(statement.index().value());
        }catch (NumberFormatException _) {
            return false;
        }
    }

    @Override
    Optional<CompilationUnit> update(String name) {
        var result = getTypeDeclaration(name, QueryType.ENUM);
        if (result.isEmpty()) {
            return Optional.of(generate());
        }

        var ctEnum = (EnumDeclaration) result.get().result();
        createEnumConstants(ctEnum);
        addReservedAnnotation(ctEnum);
        return result.map(QueryResult::compilationUnit);
    }
}
