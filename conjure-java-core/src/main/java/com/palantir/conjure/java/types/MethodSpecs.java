/*
 * (c) Copyright 2018 Palantir Technologies Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.palantir.conjure.java.types;

import com.palantir.conjure.java.util.JavaNameSanitizer;
import com.palantir.conjure.spec.FieldName;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Objects;
import java.util.stream.Collector;
import javax.lang.model.element.Modifier;

public final class MethodSpecs {

    public static MethodSpec createEquals(TypeName thisClass) {
        ParameterSpec other = ParameterSpec.builder(TypeName.OBJECT, "other").build();
        return MethodSpec.methodBuilder("equals")
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(Override.class)
                .addParameter(other)
                .returns(TypeName.BOOLEAN)
                .addStatement("return this == $1N || ($1N instanceof $2T && equalTo(($2T) $1N))",
                        other, thisClass)
                .build();
    }

    public static MethodSpec createEqualTo(TypeName thisClass, Collection<FieldSpec> fields) {
        CodeBlock equalsTo = createEqualsToStatement(fields);

        return MethodSpec.methodBuilder("equalTo")
                .addModifiers(Modifier.PRIVATE)
                .addParameter(thisClass, "other")
                .returns(TypeName.BOOLEAN)
                .addStatement("return $L", equalsTo)
                .build();
    }

    public static MethodSpec createHashCode(Collection<FieldSpec> fields) {
        return MethodSpec.methodBuilder("hashCode")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .returns(TypeName.INT)
                .addStatement("return $1T.$2N($3L)", Objects.class, "hash", getHashInput(fields))
                .build();
    }

    public static void addCachedHashCode(TypeSpec.Builder typeBuilder, Collection<FieldSpec> fields) {
        FieldSpec.Builder hashFieldSpec = FieldSpec.builder(TypeName.INT, "memoizedHashCode",
                Modifier.PRIVATE, Modifier.VOLATILE);
        typeBuilder.addField(hashFieldSpec.build());

        typeBuilder.addMethod(MethodSpec.methodBuilder("hashCode")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .returns(TypeName.INT)
                .beginControlFlow("if (memoizedHashCode == 0)")
                .addStatement("memoizedHashCode = $1T.$2N($3L)", Objects.class, "hash", getHashInput(fields))
                .endControlFlow()
                .addStatement("return memoizedHashCode")
                .build());
    }

    private static CodeBlock getHashInput(Collection<FieldSpec> fields) {
        return CodeBlocks.of(fields.stream()
                .map(MethodSpecs::createHashInput)
                .collect(joining(CodeBlock.of(", "))));
    }

    public static MethodSpec createToString(String thisClassName, Collection<FieldName> fieldNames) {
        CodeBlock returnStatement = fieldNames.isEmpty()
                ? CodeBlock.builder().addStatement("return $S", thisClassName + "{}").build()
                : CodeBlock.builder()
                        .add("return new $T($S).append('{')\n", StringBuilder.class, thisClassName)
                        .indent()
                        .indent()
                        .add(CodeBlocks.of(fieldNames.stream()
                                .map(MethodSpecs::createAppendStatement)
                                .collect(joining(CodeBlock.of(".append(\", \")")))))
                        .unindent()
                        .add(".append('}')\n")
                        .addStatement(".toString()")
                        .unindent()
                        .build();

        return MethodSpec.methodBuilder("toString")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .returns(ClassName.get(String.class))
                .addCode(returnStatement)
                .build();
    }

    private static CodeBlock createAppendStatement(FieldName fieldName) {
        return CodeBlock.builder()
                .add(".append($S)", fieldName.get())
                .add(".append(\": \")")
                .add(".append($N)", JavaNameSanitizer.sanitize(fieldName))
                .add("\n")
                .build();
    }

    private static CodeBlock createEqualsToStatement(Collection<FieldSpec> fields) {
        if (fields.isEmpty()) {
            return CodeBlock.of("$L", true);
        }

        return CodeBlocks.of(fields.stream()
                .map(MethodSpecs::createEqualsStatement)
                .collect(joining(CodeBlock.of(" && "))));
    }

    private static CodeBlock createEqualsStatement(FieldSpec field) {
        String thisField = "this." + field.name;
        String otherField = "other." + field.name;

        if (field.type.isPrimitive()) {
            return CodeBlock.of("$L == $L", thisField, otherField);
        } else if (field.type.equals(ClassName.get(OffsetDateTime.class))) {
            return CodeBlock.of("$L.isEqual($L)", thisField, otherField);
        }

        return CodeBlock.of("$L.equals($L)", thisField, otherField);
    }

    private static CodeBlock createHashInput(FieldSpec field) {
        if (field.type.equals(ClassName.get(OffsetDateTime.class))) {
            return CodeBlock.of("$N.toInstant()", field);
        }

        return CodeBlock.of("$N", field);
    }

    private static <T> Collector<T, ArrayList<T>, ArrayList<T>> joining(T delim) {
        return Collector.of(ArrayList::new,
                (list, element) -> {
                    if (!list.isEmpty()) {
                        list.add(delim);
                    }
                    list.add(element);
                },
                (list1, list2) -> {
                    if (!list1.isEmpty()) {
                        list1.add(delim);
                    }
                    list1.addAll(list2);
                    return list1;
                });
    }

    private MethodSpecs() {}

}
