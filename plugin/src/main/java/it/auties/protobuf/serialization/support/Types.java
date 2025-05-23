package it.auties.protobuf.serialization.support;

import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.ProtobufType;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.*;
import javax.lang.model.type.*;
import java.lang.annotation.Annotation;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class Types {
    private static final String GETTER_PREFIX = "get";

    private final ProcessingEnvironment processingEnv;
    private final TypeMirror rawGroupType;
    private final TypeMirror voidType;
    public Types(ProcessingEnvironment processingEnv) {
        this.processingEnv = processingEnv;
        this.rawGroupType = getType(Map.class, Integer.class, Object.class);
        this.voidType = processingEnv.getTypeUtils().getNoType(TypeKind.VOID);
    }

    public TypeMirror rawGroupType() {
        return rawGroupType;
    }

    public TypeMirror voidType() {
        return voidType;
    }

    // Convert a Java type into an AST type mirror
    public TypeMirror getType(Class<?> type, Class<?>... params) {
        if(type == null) {
            return null;
        }

        if(type.isPrimitive()) {
            var kind = TypeKind.valueOf(type.getName().toUpperCase(Locale.ROOT));
            return processingEnv.getTypeUtils().getPrimitiveType(kind);
        }

        if(type.isArray()) {
            return processingEnv.getTypeUtils().getArrayType(getType(type.getComponentType()));
        }

        var result = processingEnv.getElementUtils().getTypeElement(type.getCanonicalName());
        if(params.length == 0) {
            return erase(result.asType());
        }else {
            var typeArgs = Arrays.stream(params)
                    .map(this::getType)
                    .toArray(TypeMirror[]::new);
            return processingEnv.getTypeUtils().getDeclaredType(result, typeArgs);
        }
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
        if(rhs instanceof PrimitiveType primitiveType) {
            var boxed = processingEnv.getTypeUtils().boxedClass(primitiveType);
            rhs = boxed.asType();
        }

        if(lhs instanceof PrimitiveType primitiveType) {
            var boxed = processingEnv.getTypeUtils().boxedClass(primitiveType);
            lhs = boxed.asType();
        }

        return processingEnv.getTypeUtils().isAssignable(erase ? erase(rhs) : rhs, erase ? erase(lhs) : lhs);
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

    public Optional<TypeElement> getTypeElement(TypeMirror entry) {
        if(entry instanceof DeclaredType declaredType
                && declaredType.asElement() instanceof TypeElement typeElement) {
            return Optional.of(typeElement);
        }else {
            return Optional.empty();
        }
    }

    public List<TypeElement> getMixins(ProtobufSerializer.GroupProperty groupProperty) {
        return getMirroredTypes(groupProperty::mixins);
    }

    public List<TypeElement> getMixins(ProtobufProperty property) {
        return getMirroredTypes(property::mixins);
    }

    public List<TypeElement> getMixins(ProtobufUnknownFields property) {
        return getMirroredTypes(property::mixins);
    }

    public List<TypeElement> getMixins(ProtobufGetter property) {
        return getMirroredTypes(property::mixins);
    }

    public TypeElement getMirroredType(Supplier<Class<?>> supplier) {
        try {
            return processingEnv.getElementUtils().getTypeElement(supplier.get().getName());
        }catch (MirroredTypeException exception) {
            return (TypeElement) ((DeclaredType) exception.getTypeMirror()).asElement();
        }
    }

    public List<TypeElement> getMirroredTypes(Supplier<Class<?>[]> supplier) {
        try {
            return Arrays.stream(supplier.get())
                    .map(mixin -> processingEnv.getElementUtils().getTypeElement(mixin.getName()))
                    .collect(Collectors.toList());
        }catch (MirroredTypesException exception) {
            return exception.getTypeMirrors()
                    .stream()
                    .map(this::getTypeElement)
                    .flatMap(Optional::stream)
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

    /// NEEDS REFACTOR
    public TypeMirror getReturnType(ExecutableElement method, List<TypeMirror> arguments) {
        var returnType = method.getReturnType();
        if(!isParametrized(returnType)) {
            return returnType;
        }

        var typeParametersArguments = new HashMap<String, List<TypeMirror>>();
        for (var index = 0; index < method.getParameters().size(); index++) {
            var methodParameterUses = getTypeUses(method.getParameters().get(index).asType(), arguments.get(index));
            methodParameterUses.forEach((key, value) -> typeParametersArguments.merge(key, value, (first, second) -> {
                var result = new ArrayList<TypeMirror>();
                result.addAll(first);
                result.addAll(second);
                return result;
            }));
        }

        var typeParametersResolvedTypes = new HashMap<String, TypeMirror>();
        typeParametersArguments.forEach((type, values) -> typeParametersResolvedTypes.put(type, lowerCommonBound(values)));
        return createTypeWithGenericData(returnType, typeParametersResolvedTypes);
    }

    private TypeMirror createTypeWithGenericData(TypeMirror type, Map<String, TypeMirror> typeParametersResolvedTypes) {
        if(type.getKind() == TypeKind.TYPEVAR) {
            return typeParametersResolvedTypes.getOrDefault(type.toString(), type);
        }

        if(type instanceof PrimitiveType primitiveType) {
            return processingEnv.getTypeUtils()
                    .boxedClass(primitiveType)
                    .asType();
        }

        if(!(type instanceof DeclaredType declaredType)) {
            return type;
        }

        var resultArguments = new TypeMirror[declaredType.getTypeArguments().size()];
        for(var index = 0; index < resultArguments.length; index++) {
            var typeArgument = declaredType.getTypeArguments().get(index);
            resultArguments[index] = createTypeWithGenericData(typeArgument, typeParametersResolvedTypes);
        }

        return processingEnv.getTypeUtils().getDeclaredType((TypeElement) declaredType.asElement(), resultArguments);
    }

    // 100% this shouldn't be implemented like this
    private TypeMirror lowerCommonBound(List<TypeMirror> types) {
        var counter = new HashMap<TypeMirror, Integer>();
        for(var type : types) {
            while (type != null) {
                counter.compute(type, (key, value) -> value == null ? 1 : value + 1);
                type = getSuperClass(type)
                        .orElse(null);
            }
        }
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

    private Map<String, List<TypeMirror>> getTypeUses(TypeMirror methodParameterType, TypeMirror methodArgumentType) {
        if(methodParameterType.getKind() == TypeKind.TYPEVAR) {
            return Map.of(methodParameterType.toString(), List.of(methodArgumentType));
        }

        if(!(methodParameterType instanceof DeclaredType methodDeclaredParameterType)) {
            return Map.of();
        }

        var uses = new HashMap<String, List<TypeMirror>>();

        var methodParameterTypeParameters = methodDeclaredParameterType.getTypeArguments();
        for (var index = 0; index < methodParameterTypeParameters.size(); index++) {
            var methodParameterTypeParameter = methodParameterTypeParameters.get(index);
            if (methodParameterTypeParameter.getKind() == TypeKind.TYPEVAR) {
                var type = getTypeParameter(methodArgumentType, methodParameterType, index)
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


    public Optional<TypeMirror> getSuperClass(TypeMirror mirror) {
        return switch (mirror) {
            case ArrayType ignored -> Optional.of(getType(Object.class));
            case DeclaredType declaredType when declaredType.asElement() instanceof TypeElement typeElement
                    && typeElement.getSuperclass() != null
                    && typeElement.getSuperclass().getKind() != TypeKind.NONE -> Optional.ofNullable(typeElement.getSuperclass());
            default -> Optional.empty();
        };
    }

    public Optional<TypeElement> getSuperClass(TypeElement typeElement) {
        var superClass = typeElement.getSuperclass();
        if(superClass == null || superClass.getKind() == TypeKind.NONE) {
            return Optional.empty();
        }

        var superClassElement = ((DeclaredType) superClass).asElement();
        return Optional.of((TypeElement) superClassElement);
    }

    public ProtobufProperty getProperty(ProtobufGetter getter) {
        return new ProtobufProperty() {
            @Override
            public Class<? extends Annotation> annotationType() {
                return ProtobufProperty.class;
            }

            @Override
            public int index() {
                return getter.index();
            }

            @Override
            public ProtobufType type() {
                return getter.type();
            }

            @Override
            public ProtobufType mapKeyType() {
                return ProtobufType.UNKNOWN;
            }

            @Override
            public ProtobufType mapValueType() {
                return ProtobufType.UNKNOWN;
            }

            @Override
            public Class<?>[] mixins() {
                return getter.mixins();
            }

            @Override
            public boolean required() {
                return false;
            }

            @Override
            public boolean ignored() {
                return false;
            }

            @Override
            public boolean packed() {
                return getter.packed();
            }
        };
    }

    public ProtobufProperty getProperty(ProtobufSerializer.GroupProperty property) {
        return new ProtobufProperty() {
            @Override
            public Class<? extends Annotation> annotationType() {
                return ProtobufProperty.class;
            }

            @Override
            public int index() {
                return property.index();
            }

            @Override
            public ProtobufType type() {
                return property.type();
            }

            @Override
            public ProtobufType mapKeyType() {
                return property.mapKeyType();
            }

            @Override
            public ProtobufType mapValueType() {
                return property.mapValueType();
            }

            @Override
            public Class<?>[] mixins() {
                return property.mixins();
            }

            @Override
            public boolean required() {
                return false;
            }

            @Override
            public boolean ignored() {
                return false;
            }

            @Override
            public boolean packed() {
                return property.packed();
            }
        };
    }

    public String getPropertyName(String string) {
        if (!string.toLowerCase().startsWith(GETTER_PREFIX)) {
            return string;
        }

        var start = GETTER_PREFIX.length() + 1;
        if (string.length() <= start) {
            return "";
        }

        return string.substring(start);
    }
}