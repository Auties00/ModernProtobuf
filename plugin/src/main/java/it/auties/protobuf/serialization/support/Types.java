package it.auties.protobuf.serialization.support;

import it.auties.protobuf.annotation.*;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.*;
import javax.lang.model.type.*;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

//  ProtobufConverterGraph requires this class to be thread-safe to optimize compilation times
//  It turns out though that processingEnv.getTypeUtils() has operations that aren't thread-safe
//
//  An example I discover while debugging is isAssignable:
//  at com.sun.tools.javac.code.Types$12.visitClassType (Types.java:2215)
//  at com.sun.tools.javac.code.Types$12.visitClassType (Types.java:2180)
//  at com.sun.tools.javac.code.Type$ClassType.accept (Type.java:1053)
//  at com.sun.tools.javac.code.Types$DefaultTypeVisitor.visit (Types.java:4936)
//  at com.sun.tools.javac.code.Types.asSuper (Types.java:2177)
//  at com.sun.tools.javac.code.Types$4.visitClassType (Types.java:1186)
//  at com.sun.tools.javac.code.Types$4.visitClassType (Types.java:1107)
//  at com.sun.tools.javac.code.Type$ClassType.accept (Type.java:1053)
//  at com.sun.tools.javac.code.Types$DefaultTypeVisitor.visit (Types.java:4936)
//  at com.sun.tools.javac.code.Types.isSubtype (Types.java:1103)
//  at com.sun.tools.javac.code.Types.isSubtypeUncheckedInternal (Types.java:1029)
//  at com.sun.tools.javac.code.Types.isSubtypeUnchecked (Types.java:1015)
//  at com.sun.tools.javac.code.Types.isConvertible (Types.java:610)
//  at com.sun.tools.javac.code.Types.isAssignable (Types.java:2392)
//  at com.sun.tools.javac.code.Types.isAssignable (Types.java:2360)
//
//  Erase might have the same problem, but I don't have any evidence of that
//
//  As a general rule if a method in the Type/Element-Utils isn't:
//  - called in the constructor
//  - doesn't build a type/element, which I'm assuming has no side effects
//  it shouldn't be called, as it might be broken now or might break in the future,
//  and should instead be reimplemented, unless it's infeasible (it is what it is)
public final class Types {
    private final ProcessingEnvironment processingEnv;
    private final TypeMirror booleanType;
    private final TypeMirror byteType;
    private final TypeMirror shortType;
    private final TypeMirror intType;
    private final TypeMirror longType;
    private final TypeMirror charType;
    private final TypeMirror floatType;
    private final TypeMirror doubleType;
    private final TypeMirror wrappedBooleanType;
    private final TypeMirror wrappedByteType;
    private final TypeMirror wrappedShortType;
    private final TypeMirror wrappedIntType;
    private final TypeMirror wrappedLongType;
    private final TypeMirror wrappedCharType;
    private final TypeMirror wrappedFloatType;
    private final TypeMirror wrappedDoubleType;
    public Types(ProcessingEnvironment processingEnv) {
        this.processingEnv = processingEnv;
        this.booleanType = processingEnv.getTypeUtils().getPrimitiveType(TypeKind.BOOLEAN);
        this.byteType = processingEnv.getTypeUtils().getPrimitiveType(TypeKind.BYTE);
        this.shortType = processingEnv.getTypeUtils().getPrimitiveType(TypeKind.SHORT);
        this.intType = processingEnv.getTypeUtils().getPrimitiveType(TypeKind.INT);
        this.longType = processingEnv.getTypeUtils().getPrimitiveType(TypeKind.LONG);
        this.charType = processingEnv.getTypeUtils().getPrimitiveType(TypeKind.CHAR);
        this.floatType = processingEnv.getTypeUtils().getPrimitiveType(TypeKind.FLOAT);
        this.doubleType = processingEnv.getTypeUtils().getPrimitiveType(TypeKind.DOUBLE);
        this.wrappedBooleanType = getType(Boolean.class);
        this.wrappedByteType = getType(Byte.class);
        this.wrappedShortType = getType(Short.class);
        this.wrappedIntType = getType(Integer.class);
        this.wrappedLongType = getType(Long.class);
        this.wrappedCharType = getType(Character.class);
        this.wrappedFloatType = getType(Float.class);
        this.wrappedDoubleType = getType(Double.class);
    }

    // Convert a Java type into an AST type mirror
    public TypeMirror getType(Class<?> type, Class<?>... params) {
        if(type == null) {
            return null;
        }

        if(type.isPrimitive()) {
            return getPrimitiveType(TypeKind.valueOf(type.getName().toUpperCase(Locale.ROOT)));
        }

        if(type.isArray()) {
            return processingEnv.getTypeUtils().getArrayType(getType(type.getComponentType()));
        }

        var result = processingEnv.getElementUtils().getTypeElement(type.getCanonicalName());
        if(params.length == 0) {
            return erase(result.asType());
        }

        var typeArgs = Arrays.stream(params)
                .map(this::getType)
                .toArray(TypeMirror[]::new);
        return processingEnv.getTypeUtils().getDeclaredType(result, typeArgs);
    }

    private TypeMirror getPrimitiveType(TypeKind kind) {
        return switch (kind) {
            case BOOLEAN -> booleanType;
            case BYTE -> byteType;
            case SHORT -> shortType;
            case INT -> intType;
            case LONG -> longType;
            case CHAR -> charType;
            case FLOAT -> floatType;
            case DOUBLE -> doubleType;
            default -> throw new IllegalStateException("Unexpected value: " + kind);
        };
    }

    public boolean isGroup(TypeMirror mirror) {
        return erase(mirror) instanceof DeclaredType declaredType
                && declaredType.asElement().getAnnotation(ProtobufGroup.class) != null;
    }

    public boolean isMessage(TypeMirror mirror) {
        return erase(mirror) instanceof DeclaredType declaredType
                && declaredType.asElement().getAnnotation(ProtobufMessage.class) != null;
    }

    public boolean isEnum(TypeMirror mirror) {
        return erase(mirror) instanceof DeclaredType declaredType
                && declaredType.asElement().getAnnotation(ProtobufEnum.class) != null;
    }

    public boolean isMixin(TypeMirror mirror) {
        return erase(mirror) instanceof DeclaredType declaredType
                && declaredType.asElement().getAnnotation(ProtobufMixin.class) != null;
    }

    public boolean isObject(TypeMirror mirror) {
        return erase(mirror) instanceof DeclaredType declaredType
                && (declaredType.asElement().getAnnotation(ProtobufMessage.class) != null
                        || declaredType.asElement().getAnnotation(ProtobufEnum.class) != null
                        || declaredType.asElement().getAnnotation(ProtobufGroup.class) != null);
    }

    public boolean isSameType(TypeMirror firstType, Class<?> secondType) {
        return isSameType(firstType, getType(secondType));
    }

    public boolean isSameType(TypeMirror firstType, TypeMirror secondType) {
        return isSameType(firstType, secondType, true);
    }

    public boolean isSameType(TypeMirror firstType, TypeMirror secondType, boolean erase) {
        return firstType != null &&
                secondType != null
                && processingEnv.getTypeUtils().isSameType(erase ? erase(firstType) : firstType, erase ? erase(secondType) : secondType);
    }

    public TypeMirror erase(TypeMirror typeMirror) {
        var result = processingEnv.getTypeUtils().erasure(typeMirror);
        return result == null ? typeMirror : result;
    }

    public boolean isAssignable(TypeMirror rhs, Class<?> lhs) {
        return isAssignable(rhs, lhs, true);
    }

    public boolean isAssignable(TypeMirror rhs, Class<?> lhs, boolean erase) {
        return isAssignable(rhs, getType(lhs), erase);
    }

    public boolean isAssignable(TypeMirror rhs, TypeMirror lhs) {
        return isAssignable(rhs, lhs, true);
    }

    public boolean isAssignable(TypeMirror rhs, TypeMirror lhs, boolean erase) {
        lhs = boxOrErase(lhs, erase);
        rhs = boxOrErase(rhs, erase);
        var rhsTypes = new LinkedList<TypeMirror>();
        rhsTypes.add(rhs);
        while (!rhsTypes.isEmpty()) {
            var rhsAncestorType = rhsTypes.removeFirst();
            if(isSameType(rhsAncestorType, lhs, erase)) {
                return true;
            }
            getDirectSuperClass(rhsAncestorType)
                    .ifPresent(rhsTypes::add);
            rhsTypes.addAll(getAllImplementedInterfaces(rhsAncestorType));
        }
        return false;
    }

    private TypeMirror boxOrErase(TypeMirror rhs, boolean erase) {
        return switch (rhs.getKind()) {
            case BOOLEAN -> wrappedBooleanType;
            case BYTE -> wrappedByteType;
            case SHORT -> wrappedShortType;
            case INT -> wrappedIntType;
            case LONG -> wrappedLongType;
            case CHAR -> wrappedCharType;
            case FLOAT -> wrappedFloatType;
            case DOUBLE -> wrappedDoubleType;
            default -> erase ? erase(rhs) : rhs;
        };
    }

    public Optional<TypeElement> getDefaultConstructor(TypeMirror type) {
        if(erase(type) instanceof DeclaredType declaredType
                && declaredType.asElement() instanceof TypeElement typeElement
                && !typeElement.getModifiers().contains(Modifier.ABSTRACT)
                && hasNoArgsConstructor(typeElement)) {
            return Optional.of(typeElement);
        }

        return Optional.empty();
    }

    private boolean hasNoArgsConstructor(TypeElement typeElement) {
        return typeElement.getEnclosedElements()
                .stream()
                .anyMatch(entry -> entry.getKind() == ElementKind.CONSTRUCTOR && ((ExecutableElement) entry).getParameters().isEmpty());
    }

    public List<TypeElement> getMixins(ProtobufProperty property) {
        return getMirroredTypes(property::mixins);
    }

    public List<TypeElement> getMixins(ProtobufUnknownFields property) {
        return getMirroredTypes(property::mixins);
    }

    public List<TypeElement> getMirroredTypes(Supplier<Class<?>[]> supplier) {
        try {
            return Arrays.stream(supplier.get())
                    .map(mixin -> processingEnv.getElementUtils().getTypeElement(mixin.getName()))
                    .collect(Collectors.toList());
        }catch (MirroredTypesException exception) {
            return exception.getTypeMirrors()
                    .stream()
                    .map(entry -> entry instanceof DeclaredType declaredType
                            && declaredType.asElement() instanceof TypeElement typeElement ? typeElement : null)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        }
    }

    public boolean isParametrized(ExecutableElement element) {
        return element.getParameters()
                .stream()
                .anyMatch(this::isParametrized);
    }

    private boolean isParametrized(VariableElement parameter) {
        return parameter.asType().getKind() == TypeKind.TYPEVAR ||
                parameter.asType() instanceof DeclaredType declaredType
                        && declaredType.asElement() instanceof TypeElement typeElement
                        && isParametrized(typeElement);
    }

    public boolean isParametrized(TypeMirror mirror) {
        return mirror.getKind() == TypeKind.TYPEVAR ||
                (mirror instanceof DeclaredType declaredType && declaredType.getTypeArguments()
                        .stream()
                        .anyMatch(this::isParametrized));
    }

    public boolean isParametrized(Element element) {
        return element instanceof TypeElement typeElement && typeElement.getTypeParameters()
                .stream()
                .anyMatch(entry -> entry.asType().getKind() == TypeKind.TYPEVAR || isParametrized(entry));
    }

    // This method is quite complex to implement.
    // The problem is that converters can have a generic signature.
    // To understand if a converter with a generic signature can be used when searching for a path from type A to type B,
    // we have to apply type resolution based on the method and its arguments.
    // The good news is we don't have to care about receiver types, explicit type parameters or varargs as converters don't support/have a use for them.
    // The bad news is that there is no documented way on how to do any of this,
    // and we can't even use some parts of the compiler as they are not thread safe.
    public TypeMirror getReturnType(ExecutableElement method, List<TypeMirror> arguments) {
        var returnType = method.getReturnType();
        if(!isParametrized(returnType)) {
            // Simple case: the return type is not parametrized
            return returnType;
        }

        if((method.getReceiverType() != null && method.getReceiverType().getKind() != TypeKind.NONE) || method.isVarArgs()) {
            // Simple case: an error will have already been issued by preliminary checks
            return returnType;
        }

        // Map each type parameter of the method to the arguments that use it
        Map<String, Stream<TypeMirror>> typeParametersToArguments = HashMap.newHashMap(method.getParameters().size());
        var parametersIterator = method.getParameters().iterator();
        var argumentsIterator = arguments.iterator();
        while (parametersIterator.hasNext() && argumentsIterator.hasNext()) {
            var methodParameterUses = getTypeUses(parametersIterator.next().asType(), argumentsIterator.next());
            for (var entry : methodParameterUses.entrySet()) {
                typeParametersToArguments.merge(
                        entry.getKey(),
                        entry.getValue().stream(),
                        Stream::concat
                );
            }
        }

        // Compute the lower common bound for each of the type parameters
        Map<String, TypeMirror> typeParametersToLcb = HashMap.newHashMap(method.getParameters().size());
        for(var entry : typeParametersToArguments.entrySet()) {
            typeParametersToLcb.put(entry.getKey(), lowerCommonBound(entry.getValue()));
        }

        return getReturnType(returnType, typeParametersToLcb);
    }

    private TypeMirror getReturnType(TypeMirror type, Map<String, TypeMirror> typeParametersToMirrors) {
        if(type.getKind() == TypeKind.TYPEVAR) {
            var result = typeParametersToMirrors.getOrDefault(type.toString(), type);
            return boxOrErase(result, false);
        }

        if(type instanceof PrimitiveType primitiveType) {
            return boxOrErase(primitiveType, false);
        }

        if(!(type instanceof DeclaredType declaredType)) {
            return type;
        }

        if(!(declaredType.asElement() instanceof TypeElement typeElement)) {
            return type;
        }

        var resultArguments = new TypeMirror[declaredType.getTypeArguments().size()];
        for(var index = 0; index < resultArguments.length; index++) {
            var typeArgument = declaredType.getTypeArguments().get(index);
            resultArguments[index] = getReturnType(typeArgument, typeParametersToMirrors);
        }

        return processingEnv.getTypeUtils()
                .getDeclaredType(typeElement, resultArguments);
    }

    // TODO: Can this be reimplemented with an intersection type?
    private TypeMirror lowerCommonBound(Stream<TypeMirror> types) {
        var counter = new HashMap<TypeMirror, Integer>();
        types.forEach(type -> {
            for (var implementedInterface : getAllImplementedInterfaces(type)) {
                counter.compute(implementedInterface, (key, value) -> value == null ? 1 : value + 1);
            }
            while (type != null) {
                counter.compute(type, (key, value) -> value == null ? 1 : value + 1);
                type = getDirectSuperClass(type)
                        .orElse(null);
            }
        });
        TypeMirror bestElement = null;
        int bestCount = 0;
        for(var entry : counter.entrySet()) {
            if(entry.getValue() > bestCount) {
                bestElement = entry.getKey();
                bestCount = entry.getValue();
            }else if(entry.getValue() == bestCount) {
                if(isAssignable(entry.getKey(), bestElement)) {
                    bestElement = entry.getKey();
                }
            }
        }
        return bestElement;
    }

    // FIXME: Polish me
    private Map<String, List<TypeMirror>> getTypeUses(TypeMirror parameterType, TypeMirror argumentType) {
        if(parameterType.getKind() == TypeKind.TYPEVAR) {
            return Map.of(parameterType.toString(), List.of(argumentType));
        }

        if(!(parameterType instanceof DeclaredType methodDeclaredParameterType)) {
            return Map.of();
        }

        var uses = new HashMap<String, List<TypeMirror>>();

        var methodParameterTypeParameters = methodDeclaredParameterType.getTypeArguments();
        for (var index = 0; index < methodParameterTypeParameters.size(); index++) {
            var methodParameterTypeParameter = methodParameterTypeParameters.get(index);
            if (methodParameterTypeParameter.getKind() == TypeKind.TYPEVAR) {
                var type = getTypeParameter(argumentType, parameterType, index)
                        .orElseThrow(() -> new IllegalStateException("Cannot determine type"));
                uses.compute(methodParameterTypeParameter.toString(), (key, value) -> {
                    if (value == null) {
                        var data = new ArrayList<TypeMirror>();
                        data.add(type);
                        return data;
                    }

                    value.add(type);
                    return value;
                });
            } else if (methodParameterTypeParameter instanceof DeclaredType methodParameterDeclaredTypeParameter) {
                var methodArgumentTypeParameter = methodDeclaredParameterType.getTypeArguments().get(index);
                if(methodArgumentTypeParameter instanceof DeclaredType methodArgumentDeclaredTypeParameter){
                    uses.putAll(getTypeUses(methodParameterDeclaredTypeParameter, methodArgumentDeclaredTypeParameter));
                }
            }
        }

        return uses;
    }

    public Optional<TypeMirror> getTypeParameter(TypeMirror concrete, TypeMirror model, int index) {
        if(!(concrete instanceof DeclaredType declaredType)) {
            return Optional.empty();
        }

        if (isSameType(concrete, model) && index < declaredType.getTypeArguments().size()) {
            var collectionTypeArgument = declaredType.getTypeArguments().get(index);
            return getConcreteTypeParameter(collectionTypeArgument, declaredType, index);
        }

        var typeElement = (TypeElement) declaredType.asElement();
        return typeElement.getInterfaces()
                .stream()
                .filter(implemented -> implemented instanceof DeclaredType)
                .map(implemented -> (DeclaredType) implemented)
                .map(implemented -> getTypeParameterByImplement(declaredType, implemented, model, index))
                .flatMap(Optional::stream)
                .findFirst()
                .or(() -> getTypeParameterBySuperClass(declaredType, typeElement, model, index));
    }

    private Optional<TypeMirror> getTypeParameterByImplement(DeclaredType declaredType, DeclaredType implemented, TypeMirror targetType, int index) {
        if (isSameType(implemented, targetType)) {
            var collectionTypeArgument = implemented.getTypeArguments().get(index);
            return getConcreteTypeParameter(collectionTypeArgument, declaredType, index);
        }

        return getTypeParameter(implemented, targetType, index)
                .flatMap(result -> getConcreteTypeParameter(result, declaredType, index));
    }

    private Optional<TypeMirror> getTypeParameterBySuperClass(DeclaredType declaredType, TypeElement typeElement, TypeMirror targetType, int index) {
        if (!(typeElement.getSuperclass() instanceof DeclaredType superDeclaredType)) {
            return Optional.empty();
        }

        return getTypeParameter(superDeclaredType, targetType, index)
                .flatMap(result -> getConcreteTypeParameter(result, superDeclaredType, index))
                .flatMap(result -> getConcreteTypeParameter(result, declaredType, index));
    }

    private Optional<TypeMirror> getConcreteTypeParameter(TypeMirror argumentMirror, DeclaredType previousType, int index) {
        return switch (argumentMirror) {
            case DeclaredType declaredTypeArgument -> Optional.of(declaredTypeArgument);
            case ArrayType arrayType -> Optional.of(arrayType);
            case TypeVariable typeVariableArgument -> getConcreteTypeFromTypeVariable(typeVariableArgument, previousType, index);
            case null, default -> Optional.empty();
        };
    }

    private Optional<TypeMirror> getConcreteTypeFromTypeVariable(TypeVariable typeVariableArgument, DeclaredType previousType, int index) {
        var currentTypeVarName = typeVariableArgument.asElement().getSimpleName();
        var previousTypeArguments = previousType.getTypeArguments();
        var previousElement = (TypeElement) previousType.asElement();
        var previousTypeParameters = previousElement.getTypeParameters();
        for(;index < previousTypeParameters.size() && index < previousTypeArguments.size(); index++) {
            if(previousTypeParameters.get(index).getSimpleName().equals(currentTypeVarName)){
                return Optional.of(previousTypeArguments.get(index));
            }
        }
        return Optional.empty();
    }


    public Set<? extends TypeMirror> getAllImplementedInterfaces(TypeMirror typeMirror) {
        var results = new HashSet<TypeMirror>();
        var types = new LinkedList<TypeElement>();
        if(typeMirror instanceof DeclaredType declaredType
                && declaredType.asElement() instanceof TypeElement typeElement) {
            types.add(typeElement);
        }
        while (!types.isEmpty()) {
            var typeElement = types.removeFirst();
            getDirectSuperClass(typeElement)
                    .ifPresent(types::add);
            for(var interfaceMirror : typeElement.getInterfaces() ) {
                results.add(interfaceMirror);
                if(interfaceMirror instanceof DeclaredType declaredType
                        && declaredType.asElement() instanceof TypeElement interfaceElement) {
                    types.add(interfaceElement);
                }
            }
        }
        return results;
    }

    public Optional<TypeMirror> getDirectSuperClass(TypeMirror mirror) {
        return switch (mirror) {
            case ArrayType ignored -> Optional.of(getType(Object.class));
            case DeclaredType declaredType
                    when declaredType.asElement() instanceof TypeElement typeElement
                        && typeElement.getSuperclass() != null
                        && typeElement.getSuperclass().getKind() != TypeKind.NONE
                            -> Optional.ofNullable(typeElement.getSuperclass());
            default -> Optional.empty();
        };
    }

    public Optional<TypeElement> getDirectSuperClass(TypeElement typeElement) {
        var superClassMirror = typeElement.getSuperclass();
        if(superClassMirror == null || superClassMirror.getKind() == TypeKind.NONE) {
            return Optional.empty();
        }

        if(!(superClassMirror instanceof DeclaredType superClassType)) {
            return Optional.empty();
        }

        if(!(superClassType instanceof TypeElement superClassElement)) {
            return Optional.empty();
        }

        return Optional.of(superClassElement);
    }
}