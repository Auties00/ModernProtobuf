package it.auties.protobuf.annotation;

import it.auties.protobuf.builtin.*;

import javax.lang.model.element.Modifier;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * This annotation can be applied to static methods and constructors
 * in a non-abstract class or record annotated with {@link ProtobufMessage} or {@link ProtobufGroup}
 * to auto-generate a new builder class using the parameters of the annotated type.
 * <h2>Usage Example:</h2>
 * <pre>{@code
 * @ProtobufMessage
 * public record InteractiveHeader(
 *     @ProtobufProperty(index = 1, type = ProtobufType.STRING)
 *     String title,
 *     @ProtobufProperty(index = 2, type = ProtobufType.STRING)
 *     String subtitle,
 *     @ProtobufProperty(index = 3, type = ProtobufType.MESSAGE)
 *     DocumentMessage attachmentDocument,
 *     @ProtobufProperty(index = 4, type = ProtobufType.MESSAGE)
 *     ImageMessage attachmentImage,
 *     @ProtobufProperty(index = 5, type = ProtobufType.BYTES)
 *     InteractiveHeaderThumbnail attachmentThumbnail,
 *     @ProtobufProperty(index = 6, type = ProtobufType.MESSAGE)
 *     VideoOrGifMessage attachmentVideo
 * ) {
 *     @ProtobufBuilder
 *     static InteractiveHeader simpleBuilder(String title, String subtitle, InteractiveHeaderAttachment attachment) {
 *         var builder = new InteractiveHeaderBuilder()
 *                 .title(title)
 *                 .subtitle(subtitle);
 *         switch (attachment) {
 *             case DocumentMessage documentMessage ->
 *                     builder.attachmentDocument(documentMessage);
 *             case ImageMessage imageMessage ->
 *                     builder.attachmentImage(imageMessage);
 *             case InteractiveHeaderThumbnail thumbnail ->
 *                     builder.attachmentThumbnail(thumbnail);
 *             case VideoOrGifMessage videoMessage ->
 *                     builder.attachmentVideo(videoMessage);
 *             case null ->
 *                     {}
 *         }
 *         return builder.build();
 *     }
 * }
 * }</pre>
 */
@Target({ElementType.METHOD, ElementType.CONSTRUCTOR})
@Retention(RetentionPolicy.RUNTIME)
public @interface ProtobufBuilder {
    /**
     * Specifies the modifier of the builder class to be auto-generated
     * for a static method or constructor annotated with {@code @ProtobufBuilder}.
     *
     * @return the modifiers of the builder class to be generated
     */
    Modifier[] modifiers() default {
            Modifier.PUBLIC,
            Modifier.FINAL
    };

    /**
     * Specifies the name of the builder class to be auto-generated
     * for a static method or constructor annotated with {@code @ProtobufBuilder}.
     * If no name is specified, this builder will be interpreted
     * as the default builder for the containing type.
     *
     * @return the name of the builder class to be generated
     */
    String name() default "";

    /**
     * Returns the list of mixin classes associated with the builder class to be auto-generated
     * for a static method or constructor annotated with {@code @ProtobufBuilder}.
     * Mixins provide additional functionalities such as default value generation.
     *
     * @return an array of mixin classes including:
     *         {@link ProtobufAtomicMixin}, {@link ProtobufOptionalMixin}, {@link ProtobufUUIDMixin},
     *         {@link ProtobufURIMixin}, {@link ProtobufRepeatedMixin}, {@link ProtobufMapMixin},
     *         {@link ProtobufFutureMixin}, and {@link ProtobufLazyMixin}
     */
    Class<?>[] mixins() default {
            ProtobufAtomicMixin.class,
            ProtobufOptionalMixin.class,
            ProtobufUUIDMixin.class,
            ProtobufURIMixin.class,
            ProtobufRepeatedMixin.class,
            ProtobufMapMixin.class,
            ProtobufFutureMixin.class,
            ProtobufLazyMixin.class
    };
}
