package it.auties.protobuf.tool.schema;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.ArrayInitializerExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.nodeTypes.NodeWithImplements;
import com.github.javaparser.ast.type.ArrayType;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import it.auties.protobuf.annotation.ProtobufEnumIndex;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.annotation.ProtobufReserved;
import it.auties.protobuf.model.ProtobufEnum;
import it.auties.protobuf.model.ProtobufMessage;
import it.auties.protobuf.model.ProtobufType;
import it.auties.protobuf.parser.statement.*;
import it.auties.protobuf.tool.util.LogProvider;

import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

abstract sealed class SchemaCreator<V extends ProtobufObject<?>> implements LogProvider permits EnumSchemaCreator, MessageSchemaCreator {
    private static final String SRC_MAIN_JAVA = "src.main.java.";
    private static final String SRC_TEST_JAVA = "src.test.java.";

    private final static Set<CompilationUnit> compilationUnits = new HashSet<>();
    private static final Map<String, ClassOrInterfaceDeclaration> oneOfImplementMap = new HashMap<>();

    protected V protoStatement;

    protected List<CompilationUnit> classPool;

    protected Path output;

    protected String packageName;

    protected boolean mutable;

    SchemaCreator(String packageName, V protoStatement, boolean mutable, List<CompilationUnit> classPool, Path output) {
        this.protoStatement = protoStatement;
        this.classPool = classPool;
        this.mutable = mutable;
        this.output = output;
        this.packageName = getPackageName(packageName, output);
    }

    private String getPackageName(String packageName, Path output) {
        if (packageName != null) {
            return packageName;
        }

        if (output != null) {
            return inferPackageName(output)
                    .orElse(null);
        }

        return null;
    }

    private Optional<String> inferPackageName(Path output){
        var outputDir = output.toString()
                .replaceAll("\\\\", ".")
                .replaceAll("/", ".");
        var mainIndex = outputDir.indexOf(SRC_MAIN_JAVA);
        if(mainIndex != -1){
            return Optional.of(outputDir.substring(mainIndex + SRC_MAIN_JAVA.length()));
        }

        var testIndex = outputDir.indexOf(SRC_TEST_JAVA);
        if(testIndex != -1){
            return Optional.of(outputDir.substring(testIndex + SRC_TEST_JAVA.length()));
        }

        return Optional.empty();
    }

    abstract CompilationUnit generate();

    abstract void generate(Node parent);

    Optional<CompilationUnit> update() {
        return update(protoStatement.name());
    }

    abstract Optional<CompilationUnit> update(String name);

    void addOneOfDeferredImplementation(String className, ClassOrInterfaceDeclaration declaration) {
        oneOfImplementMap.put(className, declaration);
    }

    Optional<ClassOrInterfaceDeclaration> getDeferredImplementation(String className) {
        return Optional.ofNullable(oneOfImplementMap.remove(className));
    }

    Optional<QueryResult> getTypeDeclaration(String name, QueryType queryType){
        for (var compilationUnit : compilationUnits) {
            var types = compilationUnit.getTypes();
            for (var entry : types) {
                if (queryType != QueryType.ENUM || entry instanceof EnumDeclaration) {
                    if (Objects.equals(entry.getNameAsString(), name)) {
                        return Optional.of(new QueryResult(compilationUnit, entry));
                    }
                }
            }
        }
        return Optional.empty();
    }

    record QueryResult(CompilationUnit compilationUnit, TypeDeclaration<?> result) {

    }

    enum QueryType {
        MESSAGE,
        ENUM,
        ANY
    }

    Optional<MethodDeclaration> getMethod(TypeDeclaration<?> typeDeclaration, String name) {
        var results = typeDeclaration.getMethodsByName(name);
        if(results.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(results.get(0));
    }

    Optional<? extends TypeDeclaration<?>> getTypeMember(TypeDeclaration<?> typeDeclaration, String name) {
        return typeDeclaration.getMembers()
                .stream()
                .filter(entry -> entry instanceof TypeDeclaration<?>)
                .map(entry -> (TypeDeclaration<?>) entry)
                .filter(entry -> entry.getNameAsString().equals(name))
                .findFirst();
    }

    CompilationUnitResult createCompilationUnit(boolean isEnum) {
        var existing = getTypeDeclaration(protoStatement.name(), isEnum ? QueryType.ENUM : QueryType.MESSAGE);
        if(existing.isPresent()){
            return new CompilationUnitResult(existing.get().compilationUnit(), true);
        }

        var compilationUnit = new CompilationUnit();
        if(packageName != null) {
            compilationUnit.setPackageDeclaration(packageName);
        }

        compilationUnit.addImport(ProtobufMessage.class.getName());
        if(protoStatement.statementType() == ProtobufStatementType.ENUM || protoStatement.getStatementRecursive(ProtobufEnumStatement.class).isPresent()){
            compilationUnit.addImport(ProtobufEnum.class.getName());
            compilationUnit.addImport(ProtobufEnumIndex.class.getName());
            compilationUnit.addImport(Override.class.getName());
        }

        if(!protoStatement.statements().isEmpty()){
            compilationUnit.addImport(ProtobufProperty.class.getName());
            compilationUnit.addImport(ProtobufType.class.getName(), true, true);
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
        return new CompilationUnitResult(compilationUnit, false);
    }

    record CompilationUnitResult(CompilationUnit compilationUnit, boolean existing){

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

    void linkToParent(Node parent, TypeDeclaration<?> ctClass) {
        if(parent instanceof CompilationUnit compilationUnit){
            compilationUnit.addType(ctClass);
        }else if(parent instanceof TypeDeclaration<?> declaration){
            declaration.addMember(ctClass);
        }else {
            throw new IllegalArgumentException("Unknown parent type: " + parent.getClass().getName());
        }
    }

    void addImplementedType(ClassOrInterfaceDeclaration ctInterface, TypeDeclaration<?> target) {
        var simpleName = getQualifiedName(target, ctInterface);
        var nodeWithImplements = (NodeWithImplements<?>) target;
        addImplementedType(simpleName, nodeWithImplements);
    }

    void addImplementedType(String ctInterface, NodeWithImplements<?> nodeWithImplements) {
        if(nodeWithImplements.getImplementedTypes().stream().anyMatch(entry -> Objects.equals(entry.getNameWithScope(), ctInterface))){
            return;
        }

        nodeWithImplements.addImplementedType(ctInterface);
    }

    String getQualifiedName(TypeDeclaration<?> scopeDeclaration, TypeDeclaration<?> typeDeclaration) {
        var scope = scopeDeclaration.getFullyQualifiedName()
                .orElseThrow(() -> new IllegalStateException("Declaration node isn't attached to any leaf"));
        var type = typeDeclaration.getFullyQualifiedName()
                .orElseThrow(() -> new IllegalStateException("Target node isn't attached to any leaf"));
        return getQualifiedName(scope, type);
    }

    String getQualifiedName(TypeDeclaration<?> scopeDeclaration, String type) {
        var scope = scopeDeclaration.getFullyQualifiedName()
                .orElseThrow(() -> new IllegalStateException("Target node isn't attached to any leaf"));
        return getQualifiedName(scope, type);
    }

    private String getQualifiedName(String scope, String type) {
        if(type.startsWith("java.") && !type.contains("<")) {
            return type.substring(type.lastIndexOf(".") + 1);
        }

        var parsedType = StaticJavaParser.parseType(type);
        if(parsedType.isPrimitiveType()) {
            return type;
        }

        if(parsedType instanceof ArrayType arrayType) {
            return getQualifiedName(scope, arrayType.getComponentType().toString()) + "[]";
        }

        if(parsedType instanceof ClassOrInterfaceType classOrInterfaceType && classOrInterfaceType.getTypeArguments().isPresent()) {
            return getQualifiedName(scope, classOrInterfaceType.getNameWithScope()) +
                    "<" +
                    classOrInterfaceType.getTypeArguments()
                            .get()
                            .stream()
                            .map(entry -> getQualifiedName(scope, entry.toString()))
                            .collect(Collectors.joining(", ")) +
                    ">";
        }

        return buildQualifiedMinimalName(scope, type);
    }

    private String buildQualifiedMinimalName(String scope, String type) {
        var scopeParts = scope.split("\\.");
        var typeParts = type.split("\\.");
        var result = new StringBuilder();
        var maxParts = Math.max(scopeParts.length, typeParts.length);
        for(var i = 0; i < maxParts; i++){
            var typePart = i < typeParts.length ? typeParts[i] : null;
            var scopePart = i < scopeParts.length ? scopeParts[i] : null;
            if(Objects.equals(typePart, scopePart)){
                continue;
            }

            result.append(Objects.requireNonNullElse(typePart, scopePart));
            if (i == maxParts - 1) {
                continue;
            }

            result.append(".");
        }

        return result.toString();
    }
}
