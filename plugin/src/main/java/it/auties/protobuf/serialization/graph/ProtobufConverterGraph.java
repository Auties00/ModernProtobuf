package it.auties.protobuf.serialization.graph;

import it.auties.protobuf.serialization.generator.ProtobufMethodGenerator;
import it.auties.protobuf.serialization.model.ProtobufConverterMethod;
import it.auties.protobuf.serialization.support.Types;

import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import java.util.*;

// Let's consider the following transformations:
//   - byte[] -> Message(annotated with @ProtobufMessage -> no conversions required)
//     The path resolver works as described here:
//       - receives as the expected input the byte[] type and as the expected out the Message type
//       - reaches a Node whose arc is a transformer that takes byte[] and returns Message
//       - determines that Message is assignable to Message
//       - returns the path: byte[]->Message
//     This case is very simple as it requires a simple step and no generics are used
//
//   - ProtobufString -> Message(contains a @ProtobufDeserializer that takes a String)
//     The path resolver works as described here:
//       - receives as the expected input the ProtobufString type and as the expected out the Message type
//       - reaches a Node whose arc is a transformer that takes ProtobufString and returns String
//       - determines that String is not assignable to Message
//       - reaches a Node whose arc is a transformer that takes String and returns Message
//       - determines that Message is assignable to Message
//       - returns the complete path: ProtobufString->String->Message
//     This case is more complex as it requires multiple steps, but no generics are used.
//
//   - byte[] -> CompletableFuture<Message>
//     The path resolver works as described here:
//       - receives as input byte[] and CompletableFuture<Message>
//       - reaches a Node whose arc is a transformer that takes byte[] and returns Message
//       - determines that Message is not assignable to CompletableFuture<Message>
//       - starts resolving a new path from Message to CompletableFuture<Message>
//       - reaches a Node whose arc is a transformer that takes T and returns CompletableFuture<T>
//       - resolves the generic signature of the transformer as Message->CompletableFuture<Message>
//       - determines that CompletableFuture<Message> is assignable to CompletableFuture<Message>
//       - returns the complete path: byte[]->Message->CompletableFuture<Message>
//     This case is again more complex as it requires two steps and generics are used.
//
//   - byte[] -> CompletableFuture<CompletableFuture<Message>>
//     The path resolver works as described here:
//       - receives as input byte[] and CompletableFuture<Message>
//       - reaches a Node whose arc is a transformer that takes byte[] and returns Message
//       - determines that Message is not assignable to CompletableFuture<CompletableFuture<Message>>
//       - starts resolving a new path from Message to CompletableFuture<CompletableFuture<Message>>
//       - reaches a Node whose arc is a transformer that takes T and returns CompletableFuture<T>
//       - resolves the generic signature of the transformer as Message->CompletableFuture<Message>
//       - determines that CompletableFuture<Message> is not assignable to CompletableFuture<CompletableFuture<Message>>
//       - reaches again a Node whose arc is a transformer that takes T and returns CompletableFuture<T>
//       - resolves the generic signature of the transformer as CompletableFuture<Message>->CompletableFuture<CompletableFuture<Message>>
//       - determines that CompletableFuture<CompletableFuture<Message>> is assignable to CompletableFuture<CompletableFuture<Message>>
//       - returns the complete path: byte[]->Message->CompletableFuture<Message>
//
//   - byte[] -> CompletableFuture<CompletableFuture<String>>
//      In this case no valid path exists, but the path resolver has to terminate in a finite amount of time.
//
public final class ProtobufConverterGraph {
    private final Types types; // Expected to be thread-safe
    private final Set<ProtobufConverterNode> nodes;

    public ProtobufConverterGraph(Types types) {
        this.types = types;
        this.nodes = new HashSet<>();
    }

    public void link(TypeMirror from, TypeMirror to, ProtobufConverterMethod arc) {
        link(from, to, arc, "");
    }

    public void link(TypeMirror from, TypeMirror to, ProtobufConverterMethod arc, String warning) {
        var node = new ProtobufConverterNode(from, to, arc, warning);
        nodes.add(node);
    }

    public List<ProtobufConverterArc> findPath(TypeMirror from, TypeMirror to, List<TypeElement> mixins) {
        var mixinsSet = Set.copyOf(mixins);
        return findAnyPath(from, to, mixinsSet);
    }

    private List<ProtobufConverterArc> findAnyPath(TypeMirror from, TypeMirror to, Set<TypeElement> mixins) {
        return nodes.parallelStream()
                .map(node -> findSubPath(node, to, mixins, from))
                .filter(entry -> !entry.isEmpty())
                .findFirst()
                .orElse(List.of());
    }

    private List<ProtobufConverterArc> findSubPath(ProtobufConverterNode node, TypeMirror to, Set<TypeElement> mixins, TypeMirror from) {
        if(!types.isAssignable(from, node.from())) {
            return List.of();
        }else if (node.arc().parametrized()) {
            var returnType = node.arc()
                    .element()
                    .map(element -> types.getReturnType(element, List.of(from)))
                    .orElse(node.arc().returnType());
            var arc = new ProtobufConverterArc(node.arc(), returnType, node.warning());
            if (types.isAssignable(to, returnType, false)) {
                return List.of(arc);
            }

            var length = countTypeArguments(returnType);
            if (length > countTypeArguments(to) && length > countTypeArguments(from)) {
                return List.of();
            }

            var nested = findAnyPath(returnType, to, mixins);
            if (nested.isEmpty() || !isPathLegal(node, from, to, mixins)) {
                return List.of();
            }

            return ProtobufConverterArcs.of(arc, nested);
        } else if (types.isAssignable(to, node.to()) && isPathLegal(node, from, to, mixins)) {
            var arc = new ProtobufConverterArc(node.arc(), node.arc().returnType(), node.warning());
            return List.of(arc);
        }else if(isPathLegal(node, from, node.to(), mixins)) {
            var nested = findAnyPath(node.to(), to, mixins);
            if (nested.isEmpty()) {
                return List.of();
            }

            var arc = new ProtobufConverterArc(node.arc(), node.arc().returnType(), node.warning());
            return ProtobufConverterArcs.of(arc, nested);
        }else {
            return List.of();
        }
    }

    // Checks whether the call necessary to walk this path is legal
    // Doesn't check anything type related(i.e. is the conversion from->to compatible with the node's arc)
    private boolean isPathLegal(ProtobufConverterNode node, TypeMirror from, TypeMirror to, Set<TypeElement> mixins) {
        return isPathLegalThroughMixin(node, mixins)
                || isPathLegalThroughInterpretedObject(node, from)
                || isPathLegalThroughInterpretedObject(node, to)
                || isPathLegalThroughObject(node, from)
                || isPathLegalThroughObject(node, to);
    }

    // Checks whether the node's arc is inside a mixin
    // This check is necessary because even if a transformation is legal, if it comes from a mixin, it needs to be included in the mixins property of the annotation
    private boolean isPathLegalThroughMixin(ProtobufConverterNode node, Set<TypeElement> mixins) {
        return mixins.stream()
                .anyMatch(mixin -> Objects.equals(node.arc().ownerName(), types.erase(mixin.asType()).toString()));
    }

    // Checks whether the node's arc is inside the provided type
    // This check is necessary because non-object messages can be interpreted as messages if they contain a @ProtobufSerializer and @ProtobufDeserializer
    private boolean isPathLegalThroughInterpretedObject(ProtobufConverterNode node, TypeMirror type) {
        return type instanceof DeclaredType firstDeclaredType
                && firstDeclaredType.asElement() instanceof TypeElement firstTypeElement
                && firstTypeElement.getQualifiedName().contentEquals(node.arc().ownerName());
    }

    // Checks whether the node's arc is inside the spec of the provided type
    // This check is necessary because objects can be converted from/to byte[] using decode/encode methods in the corresponding object spec class
    private boolean isPathLegalThroughObject(ProtobufConverterNode node, TypeMirror type) {
        return types.isObject(type)
                && Objects.equals(ProtobufMethodGenerator.getSpecFromObject(type), node.arc().ownerName());
    }

    // Counts the number of type arguments(not parameters) in a type
    private int countTypeArguments(TypeMirror type) {
        var counter = 0;
        var queue = new LinkedList<TypeMirror>();
        queue.add(type);
        while (!queue.isEmpty()) {
            type = queue.pop();
            if (type.getKind() != TypeKind.TYPEVAR && type instanceof DeclaredType declaredType) {
                var args = declaredType.getTypeArguments();
                counter += args.size();
                queue.addAll(args);
            }
        }
        return counter;
    }
}
