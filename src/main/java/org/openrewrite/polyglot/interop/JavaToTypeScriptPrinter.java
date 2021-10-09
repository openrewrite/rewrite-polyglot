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

import org.openrewrite.ExecutionContext;
import org.openrewrite.PrintOutputCapture;
import org.openrewrite.Tree;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaPrinter;
import org.openrewrite.java.tree.*;
import org.openrewrite.marker.Markers;

import java.util.List;
import java.util.stream.Collectors;

@SuppressWarnings("ConstantConditions")
public class JavaToTypeScriptPrinter<C extends ExecutionContext> extends JavaPrinter<C> {

    private final OutputMarker outputMarker = new OutputMarker(Tree.randomId());

    @Override
    public J visitPackage(J.Package pkg, PrintOutputCapture<C> p) {
        // Don't print `namespace $package { }` for now.
        return pkg;
    }

    @Override
    public J visitImport(J.Import impoort, PrintOutputCapture<C> p) {
        p.getContext().putMessageInSet("imports", impoort.getQualid());
        // Imports are printed differently in TypeScript than in Java.
        return impoort;
    }

    @Override
    public J visitAnnotation(J.Annotation annotation, PrintOutputCapture<C> p) {
        visitSpace(annotation.getPrefix(), Space.Location.ANNOTATION_PREFIX, p);
        visitMarkers(annotation.getMarkers(), p);
        p.out.append("@");
        visit(annotation.getAnnotationType(), p);
        visitContainer("({",
                annotation.getPadding().getArguments(), JContainer.Location.ANNOTATION_ARGUMENTS, ",",
                "})", p);
        return annotation;
    }

    @Override
    public J visitAssignment(J.Assignment assignment, PrintOutputCapture<C> p) {
        visitSpace(assignment.getPrefix(), Space.Location.ASSIGNMENT_PREFIX, p);
        visitMarkers(assignment.getMarkers(), p);
        visit(assignment.getVariable(), p);
        String prefix = "=";
        if (getCursor().getPathAsStream().anyMatch(o -> J.Annotation.class.isAssignableFrom(o.getClass()))) {
            prefix = ":";
        }
        visitLeftPadded(prefix, assignment.getPadding().getAssignment(), JLeftPadded.Location.ASSIGNMENT, p);
        return assignment;
    }

    @Override
    public J visitVariableDeclarations(J.VariableDeclarations multiVariable, PrintOutputCapture<C> p) {
        visitSpace(multiVariable.getPrefix(), Space.Location.VARIABLE_DECLARATIONS_PREFIX, p);
        visitMarkers(multiVariable.getMarkers(), p);
        visitSpace(Space.EMPTY, Space.Location.ANNOTATIONS, p);
        visit(multiVariable.getLeadingAnnotations(), p);
        visitModifiers(multiVariable.getModifiers(), p);

        TypeTree variableType = multiVariable.getTypeExpression();
        List<JRightPadded<J.VariableDeclarations.NamedVariable>> variables = multiVariable.getPadding().getVariables().stream()
                .map(rp -> rp.withElement(rp.getElement().withPrefix(variableType.getPrefix())))
                .collect(Collectors.toList());
        visitRightPadded(variables, JRightPadded.Location.NAMED_VARIABLE, ",", p);
        p.out.append(": ");

        if (isString(variableType)) {
            visitPrimitive(new J.Primitive(Tree.randomId(), Space.EMPTY, Markers.EMPTY, JavaType.Primitive.String), p);
        } else {
            visitIdentifier(variableType.withPrefix(Space.EMPTY), p);
        }
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
        return multiVariable;
    }

    @Override
    public J visitCompilationUnit(J.CompilationUnit cu, PrintOutputCapture<C> p) {
        if (p.getContext().getMessage(cu.getId().toString()) == null) {
            p.getContext().putMessage(cu.getId().toString(), true);
        } else {
            return cu;
        }

        visitSpace(cu.getPrefix(), Space.Location.COMPILATION_UNIT_PREFIX, p);
        visitMarkers(cu.getMarkers(), p);
        for (J.Import imp : cu.getImports()) {
            visitImport(imp, p);
        }
        visit(cu.getClasses(), p);
        visitSpace(cu.getEof(), Space.Location.COMPILATION_UNIT_EOF, p);
        return cu;
    }

    @Override
    public J visitTypeParameter(J.TypeParameter typeParam, PrintOutputCapture<C> p) {
        visitSpace(typeParam.getPrefix(), Space.Location.TYPE_PARAMETERS_PREFIX, p);
        visitMarkers(typeParam.getMarkers(), p);
        visit(typeParam.getAnnotations(), p);
        visit(typeParam.getName(), p);
        visitContainer("extends", typeParam.getPadding().getBounds(), JContainer.Location.TYPE_BOUNDS, "&", "", p);
        return typeParam;
    }

    @Override
    public J visitMethodDeclaration(J.MethodDeclaration method, PrintOutputCapture<C> p) {
        visitSpace(method.getPrefix(), Space.Location.METHOD_DECLARATION_PREFIX, p);
        visitMarkers(method.getMarkers(), p);
        visitSpace(Space.EMPTY, Space.Location.ANNOTATIONS, p);
        visitModifiers(method.getModifiers(), p);

        visit(method.getName(), p);

        J.TypeParameters typeParameters = method.getAnnotations().getTypeParameters();
        if (typeParameters != null) {
            visit(typeParameters.getAnnotations(), p);
            visitSpace(typeParameters.getPrefix(), Space.Location.TYPE_PARAMETERS, p);
            visitMarkers(typeParameters.getMarkers(), p);
            p.out.append("<");
            visitRightPadded(typeParameters.getPadding().getTypeParameters(), JRightPadded.Location.TYPE_PARAMETER, ",", p);
            p.out.append(">");
        }

        JContainer<Statement> methodParamStmts = method.withParameters(ListUtils.map(method.getParameters(),
                st -> {
                    if (st instanceof J.VariableDeclarations && isString(st)) {
                        J.VariableDeclarations vars = (J.VariableDeclarations) st;
                        return vars
                                .withType(JavaType.Primitive.String)
                                .withTypeExpression(new J.Primitive(Tree.randomId(), st.getPrefix(), st.getMarkers(), JavaType.Primitive.String));
                    }
                    return st;
                })).getPadding().getParameters();
        visitContainer("(", methodParamStmts, JContainer.Location.METHOD_DECLARATION_PARAMETERS, ",", ")", p);
        p.out.append(":");

        TypeTree returnType = method.getReturnTypeExpression();
        visit(isString(returnType)
                        ? new J.Primitive(Tree.randomId(), method.getReturnTypeExpression().getPrefix(), method.getReturnTypeExpression().getMarkers(), JavaType.Primitive.String)
                        : returnType,
                p);

        p.out.append(" { return null; }");

        return method;
    }

    @Override
    public J visitPrimitive(J.Primitive primitive, PrintOutputCapture<C> p) {
        String keyword;
        switch (primitive.getType()) {
            case Boolean:
                keyword = "boolean";
                break;
            case Double:
            case Float:
            case Int:
            case Long:
            case Short:
                keyword = "number";
                break;
            case Void:
                keyword = "void";
                break;
            case String:
                keyword = "string";
                break;
            case Wildcard:
                keyword = "*";
                break;
            default:
                keyword = "undefined";
                break;
        }
        visitSpace(primitive.getPrefix(), Space.Location.PRIMITIVE_PREFIX, p);
        visitMarkers(primitive.getMarkers(), p);
        p.out.append(keyword);
        return primitive;
    }

    @Override
    public J visitWildcard(J.Wildcard wildcard, PrintOutputCapture<C> p) {
        visitSpace(wildcard.getPrefix(), Space.Location.WILDCARD_PREFIX, p);
        visitMarkers(wildcard.getMarkers(), p);
        p.out.append("unknown");
        return wildcard;
    }

    private static boolean isString(Statement stmt) {
        if (stmt instanceof J.VariableDeclarations) {
            J.VariableDeclarations varDecls = (J.VariableDeclarations) stmt;
            if (varDecls.getTypeExpression() instanceof J.Identifier) {
                return isString((J.Identifier) varDecls.getTypeExpression());
            }
        }
        return false;
    }

    private static boolean isString(TypeTree type) {
        if (type instanceof J.Identifier) {
            return isString((J.Identifier) type);
        } else if (type instanceof J.Primitive) {
            return ((J.Primitive) type).getType() == JavaType.Primitive.String;
        }
        return false;
    }

    private static boolean isString(J.Identifier ident) {
        return "String".equals(ident.getSimpleName());
    }

}
