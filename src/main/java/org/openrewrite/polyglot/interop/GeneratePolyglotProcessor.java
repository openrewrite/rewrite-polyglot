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
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.Options;
import org.openrewrite.ExecutionContext;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.PrintOutputCapture;
import org.openrewrite.SourceFile;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.java.Java11ParserVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Space;
import org.openrewrite.scheduling.DirectScheduler;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import static java.nio.file.Files.newBufferedWriter;
import static java.nio.file.StandardOpenOption.*;

@SupportedAnnotationTypes("*")
@SupportedSourceVersion(SourceVersion.RELEASE_11)
public class GeneratePolyglotProcessor extends AbstractProcessor {

    @Nullable
    private Trees trees;

    public GeneratePolyglotProcessor() {
        super();
    }

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        this.trees = Trees.instance(processingEnv);
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (roundEnv.processingOver()) {
            return false;
        }

        Map<String, JavaType.Class> sharedClassTypes = new HashMap<>();
        ExecutionContext executionContext = new InMemoryExecutionContext(e -> processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, assembleThrowable(e)));

        List<SourceFile> sources = new ArrayList<>();

        for (Element element : roundEnv.getRootElements()) {
            JCTree.JCCompilationUnit jcu = toUnit(element);
            if (jcu == null || jcu.getSourceFile().getName().contains("package-info")) {
                continue;
            }

            processingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE,
                    "Outputting Polyglot interop stubs for: " + jcu.getSourceFile().getName());
            try {
                Path sourcePath = Paths.get(jcu.getSourceFile().toUri());
                String source = jcu.getSourceFile().getCharContent(true).toString();

                Context javacContext = new Context();
                // otherwise, consecutive string literals in binary expressions are concatenated by the parser, losing the original
                // structure of the expression!
                Options opts = Options.instance(javacContext);
                opts.put("allowStringFolding", "false");
                opts.put("compilePolicy", "attr");

                // JavaCompiler line 452 (call to ImplicitSourcePolicy.decode(..))
                opts.put("-implicit", "none");

                // https://docs.oracle.com/en/java/javacard/3.1/guide/setting-java-compiler-options.html
                opts.put("-g", "-g");
                opts.put("-proc", "none");

                Java11ParserVisitor parser = new Java11ParserVisitor(sourcePath,
                        source,
                        false,
                        Collections.emptyList(),
                        sharedClassTypes,
                        executionContext,
                        javacContext);

                J.CompilationUnit cu = (J.CompilationUnit) parser.scan(jcu, Space.EMPTY);
                sources.add(cu);
            } catch (Throwable t) {
                processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, assembleThrowable(t));
            }
        }

        GeneratePolyglotTypes recipe = new GeneratePolyglotTypes();

        recipe.run(sources, executionContext, DirectScheduler.common(), 3, 1);

        Map<String, PrintOutputCapture<ExecutionContext>> printersByPackage = recipe.getPrintersByPackage();
        printersByPackage.forEach((pkg, printer) -> {
            Set<J.FieldAccess> imports = recipe.getImportsByPackage().get(pkg);

            Map<String, Set<String>> importsByPackage = new HashMap<>();
            for (J.FieldAccess name : imports) {
                String importPkg = name.getTarget().printTrimmed();
                String importName = name.getSimpleName();
                importsByPackage.compute(importPkg, (_k, v) -> {
                    if (v == null) {
                        v = new HashSet<>();
                    }
                    v.add(importName);
                    return v;
                });
            }

            StringJoiner importsJoiner = importsByPackage.entrySet().stream()
                    .reduce(new StringJoiner("\r\n"), (j, e) -> j.add(String.join(" ",
                            "import",
                            "{",
                            String.join(", ", e.getValue()),
                            "}",
                            "from",
                            "'@openrewrite/types/" + e.getKey() + ".d.ts';")), (j1, j2) -> {
                        j1.add(j2.toString());
                        return j1;
                    });

            Path outputDir = Paths.get("src/main/typescript/");
            //noinspection ResultOfMethodCallIgnored
            outputDir.toFile().mkdirs();
            Path outputFile = outputDir.resolve(Paths.get(pkg + ".d.ts"));
            try (BufferedWriter out = newBufferedWriter(outputFile, CREATE, WRITE, TRUNCATE_EXISTING)) {
                out.write(importsJoiner.toString());
                out.write(printer.out.toString());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });

        return false;
    }

    private @Nullable JCTree.JCCompilationUnit toUnit(Element element) {
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

    private static String assembleThrowable(Throwable t) {
        StringWriter exceptionWriter = new StringWriter();
        exceptionWriter.write(t.getMessage());
        exceptionWriter.write("\n");
        t.printStackTrace();
        t.printStackTrace(new PrintWriter(exceptionWriter));
        return exceptionWriter.toString();
    }

}
