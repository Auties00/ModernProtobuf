package it.auties.protobuf.schema.schema;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.nodeTypes.NodeWithAnnotations;
import com.github.javaparser.ast.nodeTypes.NodeWithImplements;
import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.ProtobufType;
import it.auties.protobuf.parser.tree.*;
import it.auties.protobuf.parser.type.ProtobufRange;
import it.auties.protobuf.schema.util.LogProvider;

import java.lang.annotation.Annotation;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Stream;

abstract sealed class BaseProtobufSchemaCreator<V extends ProtobufTree.WithName & ProtobufTree.WithBody<?>> implements LogProvider permits EnumSchemaCreator, MessageSchemaCreator {
    private static final String SRC_MAIN_JAVA = "src.main.java.";
    private static final String SRC_TEST_JAVA = "src.test.java.";

    protected final static Set<TypeDeclaration<?>> allMembers = new HashSet<>();
    private static final Map<String, List<String>> oneOfImplementMap = new HashMap<>();

    protected V protoStatement;
    protected boolean nullable;
    protected List<CompilationUnit> classPool;
    protected Path output;
    protected String packageName;

    BaseProtobufSchemaCreator(String packageName, V protoStatement, boolean nullable, List<CompilationUnit> classPool, Path output) {
        this.protoStatement = protoStatement;
        this.nullable = nullable;
        this.classPool = classPool;
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

    abstract TypeDeclaration<?> generate(Node parent);

    Optional<CompilationUnit> update() {
        return update(protoStatement.qualifiedName());
    }

    abstract Optional<CompilationUnit> update(String name);

    void addOneOfDeferredImplementation(String className, ClassOrInterfaceDeclaration declaration) {
        var known = oneOfImplementMap.get(className);
        var qualifiedName = declaration.getFullyQualifiedName().orElseThrow();
        if (known != null) {
            known.add(qualifiedName);
            return;
        }

        var results = new ArrayList<String>();
        results.add(qualifiedName);
        oneOfImplementMap.put(className, results);
    }

    List<String> getDeferredImplementations() {
        return Objects.requireNonNullElseGet(oneOfImplementMap.remove(protoStatement.qualifiedName()), List::of);
    }

    Optional<QueryResult> getTypeDeclaration(String qualifiedName, QueryType queryType) {
        return Stream.of(allMembers.stream(), getClassPoolTypes())
                .flatMap(Function.identity())
                .map(member -> getTypeDeclarationAny(qualifiedName, queryType, member))
                .flatMap(Optional::stream)
                .findFirst();
    }

    private Stream<TypeDeclaration<?>> getClassPoolTypes() {
        return classPool.stream()
                .map(CompilationUnit::getTypes)
                .flatMap(Collection::stream);
    }

    private Optional<QueryResult> getTypeDeclarationAny(String qualifiedName, QueryType queryType, TypeDeclaration<?> compilationUnitType) {
        return getTypeDeclaration(qualifiedName, queryType, compilationUnitType)
                .or(() -> getTypeDeclarationDeep(qualifiedName, queryType, compilationUnitType));
    }

    private Optional<QueryResult> getTypeDeclarationDeep(String qualifiedName, QueryType queryType, TypeDeclaration<?> compilationUnitChild) {
        return compilationUnitChild.getMembers()
                .stream()
                .filter(entry -> entry instanceof TypeDeclaration<?>)
                .map(entry -> (TypeDeclaration<?>) entry)
                .map(innerEntry -> getTypeDeclaration(qualifiedName, queryType, innerEntry))
                .flatMap(Optional::stream)
                .findFirst();
    }

    private Optional<QueryResult> getTypeDeclaration(String qualifiedName, QueryType queryType, TypeDeclaration<?> compilationUnitDeepChild) {
        if (queryType == QueryType.ENUM && !(compilationUnitDeepChild instanceof EnumDeclaration)) {
            return Optional.empty();
        }

        var annotationName = readAnnotatedName(compilationUnitDeepChild);
        var qualifiedNameWithoutPackage = withoutPackage(qualifiedName);
        if (Objects.equals(compilationUnitDeepChild.getFullyQualifiedName().orElseThrow(), qualifiedName) || Objects.equals(annotationName, qualifiedNameWithoutPackage)) {
            return Optional.of(new QueryResult(compilationUnitDeepChild.findCompilationUnit().orElseThrow(), compilationUnitDeepChild));
        }

        return Optional.empty();
    }

    private String withoutPackage(String qualifiedName) {
        if(packageName == null) {
            return qualifiedName;
        }

        return qualifiedName.replaceFirst(packageName + "\\.", "");
    }

    private String readAnnotatedName(TypeDeclaration<?> entry) {
        var annotation = entry.getAnnotationByClass(ProtobufMessage.class)
                .or(() -> entry.getAnnotationByClass(ProtobufEnum.class))
                .orElse(null);
        if(annotation instanceof SingleMemberAnnotationExpr singleMemberAnnotationExpr && singleMemberAnnotationExpr.getMemberValue() instanceof StringLiteralExpr expr) {
            return expr.asString();
        }else if(annotation instanceof NormalAnnotationExpr normalAnnotationExpr) {
            return normalAnnotationExpr.getPairs()
                    .stream()
                    .filter(valuePair -> valuePair.getNameAsString().equals("name"))
                    .findFirst()
                    .filter(arg -> arg.getValue() instanceof StringLiteralExpr)
                    .map(arg -> (StringLiteralExpr) arg.getValue())
                    .map(StringLiteralExpr::asString)
                    .orElse(null);
        }else {
            if(annotation != null) {
                log.log(System.Logger.Level.WARNING, "Unknown annotation type: " + annotation.getClass().getName());
            }

            return null;
        }
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

        return Optional.of(results.getFirst());
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
        var existing = getTypeDeclaration(protoStatement.qualifiedName(), isEnum ? QueryType.ENUM : QueryType.MESSAGE);
        if(existing.isPresent()){
            return new CompilationUnitResult(existing.get().compilationUnit(), true);
        }

        var compilationUnit = new CompilationUnit();
        if(packageName != null) {
            compilationUnit.setPackageDeclaration(packageName);
        }

        if(protoStatement instanceof ProtobufMessageStatement || protoStatement.getAnyChildByType(ProtobufMessageStatement.class).isPresent()) {
            compilationUnit.addImport(ProtobufMessage.class.getName());
        }

        if(protoStatement instanceof ProtobufEnumStatement || protoStatement.getAnyChildByType(ProtobufEnumStatement.class).isPresent()){
            compilationUnit.addImport(ProtobufEnum.class.getName());
            compilationUnit.addImport(ProtobufEnum.Constant.class.getName());
        }

        if(hasFields(protoStatement)){
            compilationUnit.addImport(ProtobufProperty.class.getName());
            compilationUnit.addImport(ProtobufType.class.getName(), true, true);
            if(!nullable) {
                compilationUnit.addImport(Optional.class.getName());
                if(hasFieldsWithType(ProtobufType.DOUBLE)) {
                    compilationUnit.addImport(OptionalDouble.class.getName());
                }

                if(hasFieldsWithType(ProtobufType.INT32, ProtobufType.SINT32, ProtobufType.UINT32, ProtobufType.FIXED32, ProtobufType.SFIXED32)) {
                    compilationUnit.addImport(OptionalInt.class.getName());
                }

                if(hasFieldsWithType(ProtobufType.INT64, ProtobufType.SINT64, ProtobufType.UINT64, ProtobufType.FIXED64, ProtobufType.SFIXED64)) {
                    compilationUnit.addImport(OptionalLong.class.getName());
                }
            }
        }

        if(hasFieldsWithModifier(ProtobufFieldStatement.Modifier.REQUIRED)){
            compilationUnit.addImport(Objects.class.getName());
        }

        if(hasFieldsWithModifier(ProtobufFieldStatement.Modifier.REPEATED)){
            compilationUnit.addImport(List.class.getName());
        }

        return new CompilationUnitResult(compilationUnit, false);
    }

    private boolean hasFields(ProtobufTree.WithBody<?> statement) {
        return statement instanceof ProtobufMessageStatement messageTree
                && messageTree.children()
                .stream()
                .anyMatch(entry -> entry instanceof ProtobufFieldStatement 
                        || (entry instanceof ProtobufMessageStatement nestedMessageTree && hasFields(nestedMessageTree)));
    }

    record CompilationUnitResult(CompilationUnit compilationUnit, boolean existing){

    }

    private boolean hasFieldsWithModifier(ProtobufFieldStatement.Modifier modifier) {
        return hasFieldsWithModifier(protoStatement, modifier);
    }

    private boolean hasFieldsWithModifier(ProtobufTree.WithBody<?> statement, ProtobufFieldStatement.Modifier modifier) {
        return statement.children()
                .stream()
                .anyMatch(entry -> (entry instanceof ProtobufMessageStatement messageStatement && hasFieldsWithModifier(messageStatement, modifier))
                        || (entry instanceof ProtobufFieldStatement fieldStatement && fieldStatement.modifier() == modifier));

    }

    private boolean hasFieldsWithType(ProtobufType... types) {
        return hasFieldsWithType(protoStatement, types);
    }

    private boolean hasFieldsWithType(ProtobufTree.WithBody<?> statement, ProtobufType... types) {
        var typesSet = Set.of(types);
        return statement.children()
                .stream()
                .anyMatch(entry -> (entry instanceof ProtobufMessageStatement messageStatement && hasFieldsWithType(messageStatement, types))
                        || (entry instanceof ProtobufFieldStatement fieldStatement && typesSet.contains(fieldStatement.type().protobufType())));

    }

    void addReservedAnnotation(TypeDeclaration<?> ctEnum) {
        if(!(protoStatement instanceof ProtobufTree.WithBody<?> protobufReservable)){
            return;
        }

        var nameValues = new NodeList<Expression>();
        var indexValues = new NodeList<Expression>();
        var indexRanges = new NodeList<Expression>();
        protobufReservable.getDirectChildrenByType(ProtobufReservedStatement.class).forEachOrdered(child -> {
            for(var expression : child.expressions()) {
                switch (expression) {
                    case ProtobufNumberExpression indexExpression -> {
                        var valueIndex = indexExpression.value()
                                .toEnumConstant()
                                .orElseThrow(() -> new IllegalArgumentException("Overflow: " + indexExpression));
                        var entry = new IntegerLiteralExpr(String.valueOf(valueIndex));
                        indexValues.add(entry);
                    }

                    case ProtobufIntegerRangeExpression rangeExpression -> {
                        var entry = new NormalAnnotationExpr();
                        entry.setName(ProtobufReservedRange.class.getSimpleName());
                        var minIndex = rangeExpression.value()
                                .min()
                                .toEnumConstant()
                                .orElseThrow(() -> new IllegalArgumentException("Overflow: " + rangeExpression.value().min()));
                        entry.addPair("min", new IntegerLiteralExpr(String.valueOf(minIndex)));
                        var max = switch (rangeExpression.value()) {
                            case ProtobufRange.Bounded bounded -> {
                                var maxIndex = bounded.max()
                                        .toEnumConstant()
                                        .orElseThrow(() -> new IllegalArgumentException("Overflow: " + rangeExpression.value().min()));
                                yield String.valueOf(maxIndex);
                            }
                            case ProtobufRange.LowerBounded _ -> "max";
                        };
                        entry.addPair("max", new IntegerLiteralExpr(max));
                        indexRanges.add(entry);
                    }

                    case ProtobufLiteralExpression literalExpression -> {
                        var entry = new StringLiteralExpr(literalExpression.value());
                        nameValues.add(entry);
                    }
                }
            }
        });

        var annotation = getOrAddAnnotation(ctEnum, ProtobufEnum.class);
        if(!indexValues.isEmpty()){
            annotation.addPair("reservedIndexes", new ArrayInitializerExpr(indexValues));
        }

        if(!nameValues.isEmpty()){
            annotation.addPair("reservedNames", new ArrayInitializerExpr(nameValues));
        }

        if(!indexRanges.isEmpty()){
            annotation.addPair("reservedRanges", new ArrayInitializerExpr(indexRanges));
        }
    }

    void addImplementedType(ClassOrInterfaceDeclaration ctInterface, TypeDeclaration<?> target) {
        var nodeWithImplements = (NodeWithImplements<?>) target;
        addImplementedType(ctInterface.getFullyQualifiedName().orElseThrow(), nodeWithImplements);
    }

    void addImplementedType(String ctInterface, NodeWithImplements<?> nodeWithImplements) {
        if(nodeWithImplements.getImplementedTypes().stream().anyMatch(entry -> Objects.equals(entry.getNameWithScope(), ctInterface))){
            return;
        }

        nodeWithImplements.addImplementedType(ctInterface);
    }

    NormalAnnotationExpr getOrAddAnnotation(NodeWithAnnotations<?> typeDeclaration, Class<? extends Annotation> annotationType) {
        var candidateAnnotation = typeDeclaration.getAnnotationByClass(annotationType);
        if(candidateAnnotation.isPresent() && candidateAnnotation.get() instanceof NormalAnnotationExpr annotation) {
            return annotation;
        }

        candidateAnnotation.ifPresent(Node::remove);
        var annotation = new NormalAnnotationExpr();
        annotation.setName(annotationType.getSimpleName());
        return annotation;
    }
}
