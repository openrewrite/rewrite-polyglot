/*
 *  Copyright 2021 the original author or authors.
 *  <p>
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *  <p>
 *  https://www.apache.org/licenses/LICENSE-2.0
 *  <p>
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.openrewrite.polyglot.interop;

import com.sun.source.util.TreePath;
import com.sun.source.util.Trees;
import com.sun.tools.javac.tree.JCTree;
import org.openrewrite.ExecutionContext;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.Result;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.java.Java11Parser;
import org.openrewrite.java.tree.J;
import org.openrewrite.scheduling.DirectScheduler;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

import static java.nio.file.Files.newBufferedWriter;
import static java.nio.file.StandardOpenOption.*;

public class GeneratePolyglotProcessor extends AbstractProcessor {


    private Java11Parser parser;
    private Trees trees;

    public GeneratePolyglotProcessor() {
    }

    private static String assembleThrowable(Throwable t) {
        StringWriter exceptionWriter = new StringWriter();
        exceptionWriter.write(t.getMessage());
        exceptionWriter.write(" : ");
        exceptionWriter.write("\n");
        t.printStackTrace();
        t.printStackTrace(new PrintWriter(exceptionWriter));
        return exceptionWriter.toString();
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return Collections.singleton("*");
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.RELEASE_8;
    }


    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        this.parser = Java11Parser.builder().build();
        this.trees = Trees.instance(processingEnv);
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (roundEnv.processingOver()) {
            return false;
        }

        ExecutionContext ctx = new InMemoryExecutionContext(t -> {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, assembleThrowable(t));
        });
        GenerateTypeScriptTypes recipe = new GenerateTypeScriptTypes();
        List<J.CompilationUnit> srcs = roundEnv.getRootElements().stream()
                .map(this::toUnit)
                .filter(Objects::nonNull)
                .flatMap(jcu -> {
                    try {
                        return parser.reset().parse(ctx, jcu.getSourceFile().getCharContent(true).toString()).stream();
                    } catch (IOException e) {
                        throw new IllegalStateException(e);
                    }
                })
                .collect(Collectors.toList());

        List<Result> results = recipe.run(srcs, ctx, DirectScheduler.common(), 3, 1);

        Map<String, BufferedWriter> outputFiles = new HashMap<>();
        try {
            for (Result result : results) {
                if (result.getAfter() == null || !(result.getAfter() instanceof GenerateTypeScriptTypes.TypeScriptDefinitionFile)) {
                    continue;
                }
                Path outputPath = result.getAfter().getSourcePath();
                try {
                    Files.createDirectories(outputPath.getParent());
                    processingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE,
                            "Outputting Polyglot interop stubs for: " + outputPath);
                    BufferedWriter out = outputFiles.computeIfAbsent(outputPath.toString(), _p -> {
                        try {
                            return newBufferedWriter(outputPath, CREATE, WRITE, TRUNCATE_EXISTING);
                        } catch (IOException e) {
                            throw new IllegalStateException(e);
                        }
                    });
                    out.write(((GenerateTypeScriptTypes.TypeScriptDefinitionFile) result.getAfter()).getContent());
                } catch (IOException e) {
                    throw new IllegalStateException(e);
                }
            }
        } finally {
            outputFiles.values().forEach(out -> {
                try {
                    out.close();
                } catch (IOException e) {
                    throw new IllegalStateException(e);
                }
            });
        }

        return false;
    }

    @Nullable
    private JCTree.JCCompilationUnit toUnit(Element element) {
        TreePath path = null;
        if (trees != null) {
            try {
                path = trees.getPath(element);
            } catch (NullPointerException ignore) {
                // Happens if a package-info.java doesn't conatin a package declaration.
                // We can safely ignore those, since they do not need any processing
            }
        }

        if (path == null) {
            return null;
        }
        return (JCTree.JCCompilationUnit) path.getCompilationUnit();
    }

}
