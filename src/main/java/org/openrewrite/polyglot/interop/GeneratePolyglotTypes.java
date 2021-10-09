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

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.openrewrite.*;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.search.FindTypes;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.NameTree;
import org.openrewrite.marker.SearchResult;

import java.util.*;

import static java.util.Objects.requireNonNull;

@Value
@EqualsAndHashCode(callSuper = true)
public class GeneratePolyglotTypes extends Recipe {

    @Option(displayName = "Fully-qualified type names",
            description = "A list of fully-qualified Java type names of which the target class should be a subclasses or implementation.",
            example = "org.openrewrite.Recipe")
    List<String> assignableTypes;

    Map<String, Set<J.FieldAccess>> importsByPackage = new HashMap<>();
    Map<String, PrintOutputCapture<ExecutionContext>> printersByPackage = new HashMap<>();
    JavaToTypeScriptPrinter<ExecutionContext> printerVisitor = new JavaToTypeScriptPrinter<>();

    public GeneratePolyglotTypes() {
        this(Arrays.asList(Recipe.class.getName(), Tree.class.getName(), TreeVisitor.class.getName()));
    }

    public GeneratePolyglotTypes(List<String> assignableTypes) {
        this.assignableTypes = assignableTypes;
    }

    @Override
    public String getDisplayName() {
        return "Generate Polyglot types";
    }

    public Map<String, Set<J.FieldAccess>> getImportsByPackage() {
        return importsByPackage;
    }

    public Map<String, PrintOutputCapture<ExecutionContext>> getPrintersByPackage() {
        return printersByPackage;
    }

    @Override
    public String getDescription() {
        return "Generate Polyglot interop files for the OpenRewrite Java API.";
    }

    @Override
    protected JavaIsoVisitor<ExecutionContext> getSingleSourceApplicableTest() {
        return new JavaIsoVisitor<>() {
            @Override
            public J.CompilationUnit visitCompilationUnit(J.CompilationUnit cu, ExecutionContext ctx) {
                Optional<NameTree> isAssignable = assignableTypes.stream()
                        .flatMap(type -> FindTypes.findAssignable(cu, type).stream())
                        .findAny();
                return isAssignable
                        .map(nameTree -> cu.withMarkers(cu.getMarkers().addIfAbsent(new SearchResult(Tree.randomId(), nameTree.toString()))))
                        .orElseGet(() -> super.visitCompilationUnit(cu, ctx));
            }
        };
    }

    @Override
    protected JavaIsoVisitor<ExecutionContext> getVisitor() {

        return new JavaIsoVisitor<>() {
            @Override
            public J.CompilationUnit visitCompilationUnit(J.CompilationUnit cu, ExecutionContext ctx) {
                cu = super.visitCompilationUnit(cu, ctx);

                String pkg = requireNonNull(cu.getPackageDeclaration()).getExpression().printTrimmed(getCursor());
                PrintOutputCapture<ExecutionContext> output = printersByPackage.compute(pkg, (_k, v) -> {
                    if (v == null) {
                        v = new PrintOutputCapture<>(ctx);
                    }
                    return v;
                });
                printerVisitor.visitCompilationUnit(cu, output);
                importsByPackage.compute(pkg, (_k, v) -> {
                    if (v == null) {
                        v = new HashSet<>();
                    }
                    v.addAll(ctx.getMessage("imports", Collections.emptySet()));
                    return v;
                });

                return cu;
            }
        };
    }

}