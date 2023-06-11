package it.auties.protobuf.tool.schema;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.ArrayInitializerExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import it.auties.protobuf.base.ProtobufMessage;
import it.auties.protobuf.base.ProtobufProperty;
import it.auties.protobuf.base.ProtobufReserved;
import it.auties.protobuf.base.ProtobufType;
import it.auties.protobuf.parser.statement.ProtobufFieldStatement;
import it.auties.protobuf.parser.statement.ProtobufObject;
import it.auties.protobuf.parser.statement.ProtobufReservable;
import it.auties.protobuf.tool.util.LogProvider;
import lombok.NonNull;

import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

abstract sealed class SchemaCreator<V extends ProtobufObject<?>> implements LogProvider permits EnumSchemaCreator, MessageSchemaCreator {
    private final static Set<CompilationUnit> compilationUnits = new HashSet<>();
    private static final Map<String, ClassOrInterfaceDeclaration> oneOfImplementMap = new HashMap<>();

    protected boolean mutable;

    @NonNull
    protected V protoStatement;

    @NonNull
    protected List<CompilationUnit> classPool;

    SchemaCreator(V protoStatement, boolean mutable, List<CompilationUnit> classPool) {
        this.protoStatement = protoStatement;
        this.classPool = classPool;
        this.mutable = mutable;
    }

    abstract CompilationUnit generate(Path output);

    abstract TypeDeclaration<?> generate();

    abstract CompilationUnit update(Path output);

    abstract TypeDeclaration<?> update();

    void addOneOfDeferredImplementation(String className, ClassOrInterfaceDeclaration declaration) {
        oneOfImplementMap.put(className, declaration);
    }

    Optional<ClassOrInterfaceDeclaration> getDeferredImplementation(String className){
        return Optional.ofNullable(oneOfImplementMap.get(className));
    }

    Optional<TypeDeclaration<?>> getCompilationUnit(String name){
        return compilationUnits.stream()
                .map(CompilationUnit::getTypes)
                .flatMap(Collection::stream)
                .filter(entry -> Objects.equals(entry.getNameAsString(), name))
                .findFirst();
    }

    CompilationUnit createCompilationUnit() {
        var compilationUnit = new CompilationUnit();
        compilationUnit.addImport(ProtobufMessage.class.getName());
        if(!protoStatement.statements().isEmpty()){
            compilationUnit.addImport(ProtobufProperty.class.getName());
            compilationUnit.addImport(ProtobufType.class.getName());
        }
        if(hasReservedFields()){
            compilationUnit.addImport(ProtobufReserved.class.getName());
        }
        if(hasRequiredFields()){
            compilationUnit.addImport(Objects.class.getName());
        }
        if(hasRepeatedFields()){
            compilationUnit.addImport(List.class.getName());
        }
        compilationUnits.add(compilationUnit);
        return compilationUnit;
    }

    private boolean hasReservedFields() {
        return protoStatement instanceof ProtobufReservable<?> protobufReservable
                && (!protobufReservable.reservedIndexes().isEmpty() || !protobufReservable.reservedNames().isEmpty());
    }

    private boolean hasRequiredFields() {
        return protoStatement.statements()
                .stream()
                .anyMatch(entry -> entry instanceof ProtobufFieldStatement fieldStatement && fieldStatement.required());

    }

    private boolean hasRepeatedFields() {
        return protoStatement.statements()
                .stream()
                .anyMatch(entry -> entry instanceof ProtobufFieldStatement fieldStatement && fieldStatement.repeated());
    }

    void addReservedAnnotation(TypeDeclaration<?> ctEnum) {
        if(!(protoStatement instanceof ProtobufReservable<?> protobufReservable)){
            return;
        }

        var hasReservedIndexes = !protobufReservable.reservedIndexes().isEmpty();
        var hasReservedNames = !protobufReservable.reservedNames().isEmpty();
        if(!hasReservedIndexes && !hasReservedNames){
            return;
        }

        var annotation = new NormalAnnotationExpr();
        annotation.setName(ProtobufReserved.class.getSimpleName());
        if(hasReservedIndexes){
            var expressions = protobufReservable.reservedIndexes()
                    .stream()
                    .map(String::valueOf)
                    .map(StringLiteralExpr::new)
                    .collect(Collectors.toCollection(NodeList<Expression>::new));
            annotation.addPair("indexes", new ArrayInitializerExpr(expressions));
        }

        if(hasReservedNames){
            var expressions = protobufReservable.reservedNames()
                    .stream()
                    .map(StringLiteralExpr::new)
                    .collect(Collectors.toCollection(NodeList<Expression>::new));
            annotation.addPair("names", new ArrayInitializerExpr(expressions));
        }

        ctEnum.addAnnotation(annotation);
    }
}
