package it.auties.protobuf.parser.tree;

import it.auties.protobuf.parser.expression.ProtobufExpression;

/**
 * Represents a statement in the Protocol Buffer abstract syntax tree.
 * <p>
 * Statements are declarations and structural elements that make up Protocol Buffer definitions.
 * Unlike {@link ProtobufExpression expressions}, statements represent declarations rather than values.
 * All statements must have a parent container (a node implementing {@link ProtobufTree.WithBody}).
 * </p>
 * <p>
 * Statement types include:
 * </p>
 * <ul>
 *   <li><strong>File-level statements:</strong>
 *       <ul>
 *         <li>{@link ProtobufSyntaxStatement} - Syntax version declaration ({@code syntax = "proto3"})</li>
 *         <li>{@link ProtobufPackageStatement} - Package declaration ({@code package com.example})</li>
 *         <li>{@link ProtobufImportStatement} - Import statement ({@code import "other.proto"})</li>
 *         <li>{@link ProtobufOptionStatement} - File-level option ({@code option java_package = "..."})</li>
 *       </ul>
 *   </li>
 *   <li><strong>Type declarations:</strong>
 *       <ul>
 *         <li>{@link ProtobufMessageStatement} - Message definition</li>
 *         <li>{@link ProtobufEnumStatement} - Enum definition</li>
 *         <li>{@link ProtobufServiceStatement} - Service definition</li>
 *       </ul>
 *   </li>
 *   <li><strong>Member declarations:</strong>
 *       <ul>
 *         <li>{@link ProtobufFieldStatement} - Field definition</li>
 *         <li>{@link ProtobufMethodStatement} - RPC method definition</li>
 *         <li>{@link ProtobufOneofStatement} - Oneof definition</li>
 *         <li>{@link ProtobufGroupStatement} - Group field (deprecated)</li>
 *       </ul>
 *   </li>
 *   <li><strong>Other statements:</strong>
 *       <ul>
 *         <li>{@link ProtobufExtendStatement} - Extension block</li>
 *         <li>{@link ProtobufExtensionsStatement} - Extensions range declaration</li>
 *         <li>{@link ProtobufReservedStatement} - Reserved fields/names declaration</li>
 *         <li>{@link ProtobufEmptyStatement} - Empty/placeholder statement</li>
 *       </ul>
 *   </li>
 * </ul>
 *
 * @see ProtobufExpression
 * @see ProtobufTree
 */
public sealed interface ProtobufStatement
        extends ProtobufTree
        permits ProtobufDocumentChild, ProtobufEmptyStatement, ProtobufEnumChild, ProtobufEnumStatement, ProtobufExtendChild, ProtobufExtendStatement, ProtobufExtensionsStatement, ProtobufFieldStatement, ProtobufGroupChild, ProtobufGroupStatement, ProtobufImportStatement, ProtobufMessageChild, ProtobufMessageStatement, ProtobufMethodChild, ProtobufMethodStatement, ProtobufOneofChild, ProtobufOneofStatement, ProtobufOptionStatement, ProtobufPackageStatement, ProtobufReservedStatement, ProtobufServiceChild, ProtobufServiceStatement, ProtobufStatementImpl, ProtobufSyntaxStatement {
    /**
     * Returns the parent container of this statement.
     * <p>
     * All statements must have a parent that implements {@link ProtobufTree.WithBody}.
     * For file-level statements, the parent is the {@link ProtobufDocumentTree}.
     * For nested statements, the parent is the containing message, enum, service, etc.
     * </p>
     *
     * @return the parent container
     */
    @Override
    ProtobufTree.WithBody<?> parent();
}
