package it.auties.protobuf.tool.util;

import static it.auties.protobuf.parser.statement.ProtobufStatementType.ENUM;

import it.auties.protobuf.parser.statement.ProtobufObject;
import it.auties.protobuf.parser.statement.ProtobufOneOfStatement;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtEnumValue;
import spoon.reflect.declaration.CtType;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.support.reflect.declaration.CtClassImpl;
import spoon.support.reflect.declaration.CtConstructorImpl;
import spoon.support.reflect.declaration.CtEnumImpl;
import spoon.support.reflect.declaration.CtFieldImpl;
import spoon.support.reflect.declaration.CtMethodImpl;

@UtilityClass
public class AstWriter {
  private static final Map<String, CtType<?>> ORIGINAL_TYPES = new HashMap<>();
  private final List<Class<?>> ORDER = List.of(
      CtFieldImpl.class,
      CtEnumValue.class,
      CtConstructorImpl.class,
      CtMethodImpl.class,
      CtEnumImpl.class,
      CtClassImpl.class
  );


  public void cacheTypes(CtModel ctModel){
    ctModel.getElements(new TypeFilter<>(CtClass.class))
        .forEach(entry -> ORIGINAL_TYPES.put(entry.getQualifiedName(), entry.clone().setAnnotations(new ArrayList<>(entry.getAnnotations()))));
  }

  public void write(CtType<?> schema, Path path) {
    sortMembers(schema);
    writeFile(
        path,
        schema.toString()
    );
  }

  public void writeLazy(CtType<?> schema, ProtobufObject<?> protobuf, Path output){
    protobuf.getStatementsRecursive(ProtobufObject.class).forEach(entry -> {
      var ctType = getUpdateType(schema, entry);
      if(ctType == null){
        System.err.println(entry.name());
        return;
      }

      if(Objects.equals(ORIGINAL_TYPES.get(schema.getQualifiedName()), ctType)){
        return;
      }

      var ctClass = getTopClass(ctType);
      sortMembers(ctClass);
      var ctClassPath = ctClass.getQualifiedName().replaceAll("\\.", "/");
      var matchingFile = output.resolve("%s.java".formatted(ctClassPath));
      var oldFile = readOrThrow(matchingFile);
      var newFile = patch(oldFile, ctClass.toString());
      if(Objects.equals(toNeutralString(oldFile), toNeutralString(newFile))){
        return;
      }

      writeFile(
          matchingFile,
          newFile
      );
    });
  }

  private String toNeutralString(String input) {
    return input.replaceAll("\n", "")
        .replaceAll("\r", "")
        .replaceAll("\b", "")
        .replaceAll(" ", "")
        .replaceAll("\\)", "")
        .replaceAll("\\(", "")
        .replaceAll("\\{", "")
        .replaceAll("}", "");
  }

  private CtClass<?> getUpdateType(CtType<?> schema, ProtobufObject<?> entry) {
    var model = schema.getFactory().getModel();
    var name = entry instanceof ProtobufOneOfStatement oneOfStatement
        ? oneOfStatement.className() : entry.name();
    var enm = entry.statementType() == ENUM;
    return AstUtils.getProtobufClass(model, name, enm);
  }

  private CtClass<?> getTopClass(CtClass<?> ctClass){
    var parent = ctClass.getParent();
    return parent instanceof CtClass<?> newClass ? getTopClass(newClass) : ctClass;
  }

  private static String readOrThrow(Path path) {
    try {
      return Files.readString(path);
    }catch (IOException exception){
      throw new UncheckedIOException("Cannot read file", exception);
    }
  }

  private String patch(String oldMeta, String newMeta) {
    var packageMatcher = Pattern.compile("(?<=package )(.*)(?=;)")
        .matcher(oldMeta)
        .results()
        .findFirst()
        .map(MatchResult::group)
        .map("package %s;\n"::formatted)
        .orElse("");
    var oldImports = Pattern.compile("(?<=import )(.*)(?=;)")
        .matcher(oldMeta)
        .results()
        .map(MatchResult::group)
        .toList();
    var oldImportNames = oldImports.stream()
        .map(AstWriter::getClassName)
        .collect(Collectors.toUnmodifiableSet());
    var newImports = Pattern.compile("(?<=import )(.*)(?=;)")
        .matcher(oldMeta)
        .results()
        .map(MatchResult::group)
        .filter(entry -> !oldImportNames.contains(getClassName(entry)))
        .toList();
    var requiredImports =  AstElements.IMPORTS
        .entrySet()
        .stream()
        .filter(entry -> newMeta.contains(entry.getKey()))
        .map(Entry::getValue)
        .toList();
    var imports = Stream.of(oldImports, newImports, requiredImports)
        .flatMap(Collection::stream)
        .toList();
    var result = removeFullyQualifiedNames(newMeta, imports);
    var importsPretty = imports.stream()
        .map("import %s;"::formatted)
        .distinct()
        .collect(Collectors.joining("\n"));
    return "%s%s%s".formatted(
        packageMatcher,
        importsPretty.isBlank() ? "" : "%s\n".formatted(importsPretty),
        result
    );
  }

  private String removeFullyQualifiedNames(String newMeta, List<String> oldImports) {
    for (var importName : oldImports) {
      if (importName.endsWith(".*") || importName.contains("static ")) {
        continue;
      }

      var simpleName = importName.substring(importName.lastIndexOf("."));
      newMeta = newMeta.replaceAll(importName, simpleName);
    }

    return newMeta;
  }

  private String getClassName(String name) {
    return name.contains("static ") ? name
        : name.contains("\\.") ? name.substring(name.lastIndexOf("\\.")) : name;
  }

  private void writeFile(Path path, String formattedSchema) {
    try {
      Files.createDirectories(path.getParent());
      Files.writeString(path, formattedSchema,
          StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }catch (IOException exception){
      throw new UncheckedIOException("Cannot write schema to file", exception);
    }
  }

  private void sortMembers(CtType<?> ctType) {
    var parsed = ctType.getTypeMembers()
        .stream()
        .sorted(Comparator.comparingInt((entry) -> ORDER.indexOf(entry.getClass())))
        .toList();
    ctType.setTypeMembers(parsed);
  }
}
