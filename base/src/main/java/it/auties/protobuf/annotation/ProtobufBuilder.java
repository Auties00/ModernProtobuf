package it.auties.protobuf.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * This annotation can be applied to static methods and constructors
 * in a non-abstract class or record annotated with {@link ProtobufMessage} or {@link ProtobufGroup}
 * to auto-generate a new builder class named {@link ProtobufBuilder#className()}
 * using the parameters of the annotated type.
 * <p> 
 * Here is an example of how it can be used:
 * {@snippet :
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
 *     @ProtobufBuilder(className = "InteractiveHeaderSimpleBuilder")
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
 * }
 */
@Target({ElementType.METHOD, ElementType.CONSTRUCTOR})
@Retention(RetentionPolicy.RUNTIME)
public @interface ProtobufBuilder {
    /**
     * Specifies the name of the builder class to be auto-generated
     * for a static method or constructor annotated with {@code @ProtobufBuilder}.
     *
     * @return the name of the builder class to be generated
     */
    String className();
}
