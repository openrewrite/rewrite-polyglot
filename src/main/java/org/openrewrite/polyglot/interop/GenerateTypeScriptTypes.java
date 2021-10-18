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
import lombok.With;
import org.openrewrite.*;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaPrinter;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.tree.*;
import org.openrewrite.marker.Marker;
import org.openrewrite.marker.Markers;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;

@Value
@EqualsAndHashCode(callSuper = true)
public class GenerateTypeScriptTypes extends Recipe {

    @Override
    public String getDisplayName() {
        return "Generate TypeScript d.ts files";
    }

    @Override
    public String getDescription() {
        return "Generate TypeScript types of some OpenRewrite API classes for use in TypeScript recipes.";
    }

    @Override
    protected List<SourceFile> visit(List<SourceFile> before, ExecutionContext ctx) {
        return ListUtils.concatAll(before, ctx.getMessage("output", emptyList()));
    }

    @Override
    protected JavaVisitor<ExecutionContext> getVisitor() {
        Map<String, Map<String, Set<String>>> importsByPackage = new HashMap<>();
        Path outputDir = Paths.get("src/main/typescript");
        List<SourceFile> srcs = new ArrayList<>();

        return new JavaVisitor<ExecutionContext>() {
            @SuppressWarnings("ConstantConditions")
            @Override
            public J visitCompilationUnit(J.CompilationUnit cu, ExecutionContext ctx) {
                cu = (J.CompilationUnit) super.visitCompilationUnit(cu, ctx);

                PrintOutputCapture<Map<String, Map<String, Set<String>>>> output = new PrintOutputCapture<>(importsByPackage);
                TypeScriptPrinter printer = new TypeScriptPrinter();
                cu = (J.CompilationUnit) printer.visitCompilationUnit(cu, output);

                String pkgName = cu.getPackageDeclaration().getExpression().printTrimmed(getCursor().fork());
                TypeScriptDefinitionFile dDotTs = new TypeScriptDefinitionFile(
                        Tree.randomId(),
                        Markers.EMPTY,
                        outputDir.resolve(pkgName + ".d.ts"),
                        output.out.toString());
                ctx.computeMessage("output", dDotTs, srcs, (d, l) -> {
                    l.add(d);
                    return l;
                });
                return cu;
            }
        };
    }

    @SuppressWarnings("ConstantConditions")
    @Value
    @EqualsAndHashCode(callSuper = true)
    private static class TypeScriptPrinter extends JavaPrinter<Map<String, Map<String, Set<String>>>> {
        SemicolonMarker semicolonMarker = new SemicolonMarker(Tree.randomId());

        @Override
        public Space visitSpace(Space space, Space.Location loc, PrintOutputCapture<Map<String, Map<String, Set<String>>>> p) {
            p.out.append(space.getWhitespace());
            return space;
        }

        @Override
        public J visitAssignment(J.Assignment assignment, PrintOutputCapture<Map<String, Map<String, Set<String>>>> p) {
            visitSpace(assignment.getPrefix(), Space.Location.ASSIGNMENT_PREFIX, p);
            visitMarkers(assignment.getMarkers(), p);
            visit(assignment.getVariable(), p);
            String prefix = "=";
            J.Annotation anno = getCursor().firstEnclosing(J.Annotation.class);
            if (anno != null) {
                prefix = ":";
            }
            visitLeftPadded(prefix, assignment.getPadding().getAssignment(), JLeftPadded.Location.ASSIGNMENT, p);
            return assignment;
        }

        @Override
        public J visitAssignmentOperation(J.AssignmentOperation assignOp, PrintOutputCapture<Map<String, Map<String, Set<String>>>> p) {
            return super.visitAssignmentOperation(assignOp, p);
        }

        @Override
        public J visitIdentifier(J.Identifier ident, PrintOutputCapture<Map<String, Map<String, Set<String>>>> p) {
            visitSpace(ident.getPrefix(), Space.Location.IDENTIFIER_PREFIX, p);
            visitMarkers(ident.getMarkers(), p);
            switch (ident.getSimpleName()) {
                case "long":
                case "float":
                case "double":
                    p.out.append("number");
                    break;
                case "String":
                    p.out.append("string");
                    break;
                case "List":
                case "Collection":
                case "Iterable":
                    p.out.append("Array");
                    break;
                case "WeakReference":
                    p.out.append("Maybe");
                    break;
                default:
                    p.out.append(ident.getSimpleName());
            }
            return ident;
        }

        @Override
        protected void visitModifiers(Iterable<J.Modifier> modifiers, PrintOutputCapture<Map<String, Map<String, Set<String>>>> p) {
            for (J.Modifier mod : modifiers) {
                visit(mod.getAnnotations(), p);
                String keyword = "";
                switch (mod.getType()) {
                    case Default:
                        keyword = "default";
                        break;
                    case Public:
                        keyword = "public";
                        break;
                    case Protected:
                        keyword = "protected";
                        break;
                    case Private:
                        keyword = "private";
                        break;
                    case Abstract:
                        keyword = "abstract";
                        break;
                    case Static:
                        keyword = "static";
                        break;
                    case Final:
                        keyword = "final";
                        break;
                    case Native:
                        keyword = "native";
                        break;
                    case Strictfp:
                        keyword = "strictfp";
                        break;
                    case Synchronized:
                        keyword = "synchronized";
                        break;
                    case Transient:
                        keyword = "transient";
                        break;
                    case Volatile:
                        keyword = "volatile";
                        break;
                }
                visitMarkers(mod.getMarkers(), p);

                p.out.append("\n").append(keyword);
            }
        }

        @Override
        public J visitTypeParameter(J.TypeParameter typeParam, PrintOutputCapture<Map<String, Map<String, Set<String>>>> p) {
            visitSpace(typeParam.getPrefix(), Space.Location.TYPE_PARAMETERS_PREFIX, p);
            visitMarkers(typeParam.getMarkers(), p);
            visit(typeParam.getAnnotations(), p);
            visit(typeParam.getName(), p);
            visitContainer("extends", typeParam.getPadding().getBounds(), JContainer.Location.TYPE_BOUNDS, "&", "", p);
            return typeParam;
        }

        @Override
        public J visitVariable(J.VariableDeclarations.NamedVariable variable, PrintOutputCapture<Map<String, Map<String, Set<String>>>> p) {
            visitMarkers(variable.getMarkers(), p);
            Space prefix = variable.getName().getPrefix();
            if (getCursor().firstEnclosing(J.MethodDeclaration.class) == null) {
                prefix = getCursor().firstEnclosing(J.VariableDeclarations.class).getPrefix();
            }
            visit(variable.getName().withPrefix(prefix), p);
            for (JLeftPadded<Space> dimension : variable.getDimensionsAfterName()) {
                visitSpace(dimension.getBefore(), Space.Location.DIMENSION_PREFIX, p);
                p.out.append('[');
                visitSpace(dimension.getElement(), Space.Location.DIMENSION, p);
                p.out.append(']');
            }
            visitLeftPadded("=", variable.getPadding().getInitializer(), JLeftPadded.Location.VARIABLE_INITIALIZER, p);
            return variable;
        }

        @Override
        public J visitVariableDeclarations(J.VariableDeclarations multiVariable, PrintOutputCapture<Map<String, Map<String, Set<String>>>> p) {
            if (getCursor().firstEnclosing(J.MethodDeclaration.class) == null) {
                p.out.append("\n\t");
            }
            visitMarkers(multiVariable.getMarkers(), p);
            visit(multiVariable.getLeadingAnnotations(), p);
            for (JLeftPadded<Space> dim : multiVariable.getDimensionsBeforeName()) {
                visitSpace(dim.getBefore(), Space.Location.DIMENSION_PREFIX, p);
                p.out.append('[');
                visitSpace(dim.getElement(), Space.Location.DIMENSION, p);
                p.out.append(']');
            }
            if (multiVariable.getVarargs() != null) {
                visitSpace(multiVariable.getVarargs(), Space.Location.VARARGS, p);
                p.out.append("...");
            }

            String names = multiVariable.getVariables().stream()
                    .map(J.VariableDeclarations.NamedVariable::getSimpleName)
                    .collect(joining(", "));
            p.out.append(names).append(": ");
            if (multiVariable.getTypeExpression() != null) {
                visit((TypeTree) multiVariable.getTypeExpression().withPrefix(Space.EMPTY), p);
            }

            return multiVariable;
        }

        @Override
        public J visitAnnotation(J.Annotation annotation, PrintOutputCapture<Map<String, Map<String, Set<String>>>> p) {
            if (annotation.getSimpleName().equals("Nullable")) {
                return annotation;
            }
            visitSpace(annotation.getPrefix(), Space.Location.ANNOTATION_PREFIX, p);
            visitMarkers(annotation.getMarkers(), p);
            p.out.append("@");
            visit(annotation.getAnnotationType(), p);
            visitContainer("({", annotation.getPadding().getArguments(), JContainer.Location.ANNOTATION_ARGUMENTS, ",", "})", p);
            return annotation;
        }

        @Override
        public J visitCompilationUnit(J.CompilationUnit cu, PrintOutputCapture<Map<String, Map<String, Set<String>>>> p) {
            visitMarkers(cu.getMarkers(), p);
            visit(cu.getClasses(), p);
            return cu;
        }

        @Override
        public J visitClassDeclaration(J.ClassDeclaration classDecl, PrintOutputCapture<Map<String, Map<String, Set<String>>>> p) {
            if (classDecl.getModifiers().stream().noneMatch(m -> m.getType() == J.Modifier.Type.Public || m.getType() == J.Modifier.Type.Protected)) {
                boolean innerClassOfIface = getCursor()
                        .getPathAsStream(o -> o instanceof J.ClassDeclaration && ((J.ClassDeclaration) o).getKind() == J.ClassDeclaration.Kind.Type.Interface)
                        .findFirst()
                        .isPresent();
                if (!innerClassOfIface) {
                    return classDecl;
                } else {
                    p.out.append("\n}\n");
                }
            }

            String kind = "";
            switch (classDecl.getKind()) {
                case Class:
                    kind = "class";
                    break;
                case Enum:
                    kind = "enum";
                    break;
                case Interface:
                    kind = "interface";
                    break;
                case Annotation:
                    return classDecl;
            }

            visitMarkers(classDecl.getMarkers(), p);

            List<J.Modifier> modifiers = classDecl.getModifiers() != null ? classDecl.getModifiers().stream()
                    .filter(m -> m.getType() == J.Modifier.Type.Abstract)
                    .collect(toList()) : emptyList();
            if (!modifiers.isEmpty()) {
                visitModifiers(modifiers, p);
                p.out.append(" ");
            }

            p.out.append(kind);
            visit(classDecl.getName(), p);
            visitContainer("<", classDecl.getPadding().getTypeParameters(), JContainer.Location.TYPE_PARAMETERS, ",", ">", p);
            visitLeftPadded("extends", classDecl.getPadding().getExtends(), JLeftPadded.Location.EXTENDS, p);
            visitContainer(classDecl.getKind().equals(J.ClassDeclaration.Kind.Type.Interface) ? "extends" : "implements",
                    classDecl.getPadding().getImplements(), JContainer.Location.IMPLEMENTS, ",", null, p);
            visit(classDecl.getBody(), p);
            return classDecl;
        }

        @Override
        public J visitBlock(J.Block block, PrintOutputCapture<Map<String, Map<String, Set<String>>>> p) {
            return super.visitBlock(block, p);
        }

        @Override
        public J visitMethodDeclaration(J.MethodDeclaration method, PrintOutputCapture<Map<String, Map<String, Set<String>>>> p) {
            boolean isPublicOrProtected =
                    method.getModifiers().stream().anyMatch(m -> m.getType() == J.Modifier.Type.Public || m.getType() == J.Modifier.Type.Protected);
            boolean isAbstract =
                    method.getModifiers().stream().anyMatch(m -> m.getType() == J.Modifier.Type.Abstract);
            boolean isDefault =
                    method.getModifiers().stream().anyMatch(m -> m.getType() == J.Modifier.Type.Default);
            boolean isInterface =
                    getCursor().firstEnclosing(J.ClassDeclaration.class).getKind() == J.ClassDeclaration.Kind.Type.Interface;
            switch (method.getSimpleName()) {
                case "toString":
                case "equals":
                case "hashCode":
                    return method;
                default:
                    if (!(isPublicOrProtected || isAbstract) && !isInterface) {
                        return method;
                    }
            }
            p.out.append("\n\t");
            visitMarkers(method.getMarkers(), p);

            p.out.append(method.getSimpleName());

            J.TypeParameters typeParameters = method.getAnnotations().getTypeParameters();
            if (typeParameters != null) {
                visitMarkers(typeParameters.getMarkers(), p);
                p.out.append("<");
                visitRightPadded(typeParameters.getPadding().getTypeParameters(), JRightPadded.Location.TYPE_PARAMETER, ",", p);
                p.out.append(">");
            }
            visitContainer("(", method.getPadding().getParameters(),
                    JContainer.Location.METHOD_DECLARATION_PARAMETERS, ", ", "):", p);

            visit((TypeTree) method.getReturnTypeExpression().withPrefix(method.getReturnTypeExpression().getPrefix().withWhitespace(" ")), p);

            if (!(isInterface || isAbstract) || isDefault) {
                p.out.append(" { return null; }");
            }

            return method;
        }
    }

    @Value
    @With
    @EqualsAndHashCode
    static class TypeScriptDefinitionFile implements SourceFile {
        UUID id;

        Markers markers;

        Path sourcePath;

        String content;

        @Override
        public <P> boolean isAcceptable(TreeVisitor<?, P> v, P p) {
            return true;
        }
    }

    @Value
    @EqualsAndHashCode
    private static class SemicolonMarker implements Marker {
        UUID id;
    }

}
