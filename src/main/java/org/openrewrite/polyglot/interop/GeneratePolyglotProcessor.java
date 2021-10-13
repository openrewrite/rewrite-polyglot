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
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeScanner;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import static java.nio.file.Files.newBufferedWriter;
import static java.nio.file.StandardOpenOption.*;
import static java.util.stream.Collectors.joining;

@SupportedAnnotationTypes("*")
@SupportedSourceVersion(SourceVersion.RELEASE_8)
public class GeneratePolyglotProcessor extends AbstractProcessor {

    private static final SortedSet<String> ASSIGNABLE_TYPES = new TreeSet<>() {
        {
            add("org.openrewrite.Recipe");
            add("org.openrewrite.Tree");
            add("org.openrewrite.TreeVisitor");
            add("org.openrewrite.ExecutionContext");
            add("org.openrewrite.marker.Marker");
            add("org.openrewrite.style.Style");
            add("org.openrewrite.Validated");
            add("org.openrewrite.PrintOutputCapture");
        }
    };
    private static final Set<String> MAP_TYPE = Collections.singleton("java.util.Map");
    private static final Set<String> SET_TYPE = Collections.singleton("java.util.Set");
    private static final Set<String> COLLECTION_TYPE = Collections.singleton("java.util.Collection");

    private Trees trees;

    public GeneratePolyglotProcessor() {
    }

    private static boolean isAssignableTo(Type type, Set<String> targetTypes) {
        if (type == null || type.tsym == null) {
            return false;
        } else if (targetTypes.contains(type.tsym.getQualifiedName().toString())) {
            return true;
        } else if (type.tsym.type instanceof Type.ClassType) {
            Type.ClassType t = (Type.ClassType) type.tsym.type;
            boolean superTypeAssignable = isAssignableTo(t.supertype_field, targetTypes);
            boolean implementsAssignable = t.interfaces_field != null && t.interfaces_field.stream()
                    .anyMatch(i -> isAssignableTo(i, targetTypes));
            return superTypeAssignable || implementsAssignable;
        } else {
            System.err.println(type + " not found in " + targetTypes);
            return false;
        }
    }

    private static boolean isAssignableTo(JCTree tree, Set<String> targetTypes) {
        if (tree instanceof JCTree.JCClassDecl) {
            JCTree.JCClassDecl classDecl = (JCTree.JCClassDecl) tree;
            if (isAssignableTo(classDecl.sym.type, targetTypes)) {
                return true;
            }
            boolean isAssignable = false;
            if (classDecl.getExtendsClause() != null) {
                isAssignable = isAssignableTo(classDecl.getExtendsClause(), targetTypes);
            } else if (classDecl.getImplementsClause() != null) {
                List<JCTree.JCExpression> types = classDecl.getImplementsClause();
                for (int i = 0, len = types.size(); i < len && !isAssignable; i++) {
                    isAssignable = isAssignableTo(types.get(i), targetTypes);
                }
            }
            return isAssignable;
        } else if (tree instanceof JCTree.JCIdent) {
            Symbol symbol = ((JCTree.JCIdent) tree).sym;
            String fqdn = symbol.getQualifiedName().toString();
            if (targetTypes.contains(fqdn)) {
                return true;
            } else {
                return isAssignableTo(symbol.type, targetTypes);
            }
        } else if (tree instanceof JCTree.JCTypeApply) {
            JCTree.JCTypeApply type = (JCTree.JCTypeApply) tree;
            return isAssignableTo(type.getType(), targetTypes);
        }
        return false;
    }

    private static String replaceWildcardsWithUnknown(String s) {
        return s.replaceAll("\\?", "unknown");
    }

    private static String liftInnerClassName(String s) {
        return s.replaceAll("\\.", "");
    }

    private static void maybeAddImport(Map<String, Set<String>> imports, JCTree tree) {
        if (tree instanceof JCTree.JCTypeParameter
                || tree.type.tsym.packge().toString().startsWith("java")
                || tree.type.tsym instanceof Symbol.TypeVariableSymbol) {
            return;
        }

        String importPkg;
        String importName;
        if (tree instanceof JCTree.JCTypeApply) {
            JCTree.JCTypeApply parameterizedType = (JCTree.JCTypeApply) tree;
            Symbol.TypeSymbol ts = parameterizedType.getType().type.tsym;
            importPkg = ts.packge().toString();
            importName = ts.getSimpleName().toString();
        } else if (tree instanceof JCTree.JCIdent) {
            JCTree.JCIdent type = (JCTree.JCIdent) tree;
            Symbol.TypeSymbol ts = type.type.tsym;
            importPkg = ts.packge().toString();
            importName = ts.getSimpleName().toString();
        } else if (tree instanceof JCTree.JCVariableDecl) {
            JCTree.JCVariableDecl varDecl = (JCTree.JCVariableDecl) tree;
            maybeAddImport(imports, varDecl.getType());
            return;
        } else if (tree instanceof JCTree.JCFieldAccess) {
            JCTree.JCFieldAccess fa = (JCTree.JCFieldAccess) tree;
            Symbol.TypeSymbol ts = fa.type.tsym;
            importPkg = ts.packge().toString();
            importName = ts.getSimpleName().toString();
        } else {
            return;
        }
        imports.compute(importPkg, (_k, v) -> {
            if (v == null) {
                v = new HashSet<>();
            }
            v.add(importName);
            return v;
        });
    }

    public static String nameForType(JCTree tree, Map<String, Set<String>> imports) {
        StringJoiner currentLine = new StringJoiner(" ");
        boolean isMap = isAssignableTo(tree, MAP_TYPE);
        boolean isSet = isAssignableTo(tree, SET_TYPE);
        boolean isCollection = isAssignableTo(tree, COLLECTION_TYPE);
        boolean isFunctional = false;
        if (tree.type.isInterface()) {
            isFunctional = tree.type.tsym.getAnnotation(FunctionalInterface.class) != null;
        }
        String name = liftInnerClassName(replaceWildcardsWithUnknown(tree.toString()));
        switch (name) {
            case "String":
                currentLine.add("string");
                break;
            case "int":
            case "long":
            case "float":
            case "double":
            case "Number":
                currentLine.add("number");
                break;
            default:
                if (isFunctional) {
                    currentLine.add("Function");
                } else if (isMap) {
                    currentLine.add("Map");
                } else if (isSet) {
                    currentLine.add("Set");
                } else if (isCollection) {
                    Type type = tree.type.getTypeArguments().get(0);
                    currentLine.add("Array<" + type.tsym.getSimpleName() + ">");
                } else if (name.startsWith("WeakReference")) {
                    Type type = tree.type.getTypeArguments().get(0);
                    currentLine.add("Maybe<" + type.tsym.getSimpleName() + ">");
                } else {
                    maybeAddImport(imports, tree);
                    currentLine.add(name);
                }
        }
        return currentLine.toString();
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

        Map<String, Map<String, Set<String>>> imports = new HashMap<>();
        Map<String, StringBuilder> outputTypes = new HashMap<>();

        roundEnv.getRootElements().stream()
                .map(this::toUnit)
                .filter(jcu -> jcu != null && !jcu.getSourceFile().getName().contains("package-info"))
                .forEach(jcu -> {
                    processingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE,
                            "Outputting Polyglot interop stubs for: " + jcu.getSourceFile().getName());
                    String pkgName = jcu.getPackageName().toString();
                    StringOutputTreeScanner scanner = new StringOutputTreeScanner(imports.computeIfAbsent(pkgName, _k -> new HashMap<>()));
                    scanner.scan(jcu);
                    outputTypes.compute(pkgName,
                            (_k, sb) -> {
                                if (sb == null) {
                                    sb = new StringBuilder();
                                }
                                sb.append(scanner.output);
                                return sb;
                            });
                });

        outputTypes.forEach((pkg, sb) -> {
            String importsJoined = imports.get(pkg).entrySet().stream()
                    .map(perPkg -> perPkg.getValue().stream()
                            .collect(joining(", ", "import { ", " } from '@openrewrite/types/" + perPkg.getKey() + ".d';")))
                    .collect(joining("\n"));

            Path outputDir = Paths.get("src/main/typescript/");
            //noinspection ResultOfMethodCallIgnored
            outputDir.toFile().mkdirs();
            Path outputFile = outputDir.resolve(Paths.get(pkg + ".d.ts"));
            String output = sb.toString();
            if (!output.trim().isEmpty()) {
                try (BufferedWriter out = newBufferedWriter(outputFile, CREATE, WRITE, TRUNCATE_EXISTING)) {
                    out.write("type Maybe<T> = T | null;\n\n" + importsJoined + "\n" + output);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        });

        return false;
    }

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

    private static class StringOutputTreeScanner extends TreeScanner {
        private final Map<String, Set<String>> imports;

        private final StringJoiner output = new StringJoiner("\n");
        private final Stack<JCTree.JCClassDecl> classStack = new Stack<>();

        public StringOutputTreeScanner(Map<String, Set<String>> imports) {
            this.imports = imports;
        }

        @Override
        public void visitVarDef(JCTree.JCVariableDecl vd) {
            if (vd.sym != null && vd.sym.owner == classStack.peek().sym) {
                StringJoiner currentLine = new StringJoiner(" ");
                if (vd.getModifiers().getFlags().contains(Modifier.STATIC)) {
                    currentLine.add("\tstatic " + vd.getName().toString() + ":");
                } else {
                    currentLine.add("\t" + vd.getName().toString() + ":");
                }
                currentLine.add(nameForType(vd.getType(), imports));
                output.add(currentLine.toString());
                output.add("");
            }
        }

        @Override
        public void visitClassDef(JCTree.JCClassDecl classDecl) {
            classStack.push(classDecl);

            StringJoiner currentLine = new StringJoiner(" ");
            Set<Modifier> classModifiers = classDecl.getModifiers().getFlags();
            boolean isPublicInnerClass = classStack.size() > 0 && classModifiers.contains(Modifier.PUBLIC);
            boolean isAssignable = isAssignableTo(classDecl, ASSIGNABLE_TYPES);
            if (!isPublicInnerClass || !isAssignable) {
                classStack.pop();
                output.add("/* Skipped transpiling: " + classDecl.sym.getQualifiedName() + " */\n");
                return;
            }

            if (classStack.size() > 1) {
                output.add("}");
                output.add("");
            }
            output.add("");
            currentLine.add("export");

            if (classModifiers.contains(Modifier.ABSTRACT)) {
                currentLine.add("abstract");
            }
            if (classDecl.sym.isInterface()) {
                currentLine.add("interface");
            } else {
                currentLine.add("class");
            }
            String name = classStack.stream()
                    .map(cd -> cd.getSimpleName().toString())
                    .collect(joining());
            if (!classDecl.getTypeParameters().isEmpty()) {
                name += classDecl.getTypeParameters().stream()
                        .peek(t -> maybeAddImport(imports, t))
                        .map(tp -> tp.getName().toString())
                        .collect(joining(", ", "<", ">"));
            }
            currentLine.add(name);

            if (classDecl.getExtendsClause() != null) {
                currentLine.add("extends");
                currentLine.add(nameForType(classDecl.getExtendsClause(), imports));
            }
            if (classDecl.getImplementsClause() != null && !classDecl.getImplementsClause().isEmpty()) {
                if (classDecl.sym.isInterface()) {
                    currentLine.add("extends");
                } else {
                    currentLine.add("implements");
                }
                currentLine.add(classDecl.getImplementsClause().stream()
                        .map(e -> nameForType(e, imports))
                        .collect(joining(", ")));
            }

            currentLine.add("{\n");
            output.add(currentLine.toString());

            classDecl.getMembers().forEach(this::scan);

            classStack.pop();
            if (classStack.size() == 0) {
                output.add("}");
                output.add("");
            }
        }

        @Override
        public void visitMethodDef(JCTree.JCMethodDecl methodDecl) {
            Set<Modifier> methodModifiers = methodDecl.getModifiers().getFlags();
            boolean isPublic = methodModifiers.contains(Modifier.PUBLIC);
            boolean isProtected = methodModifiers.contains(Modifier.PROTECTED);
            boolean isConstructor = "<init>".equals(methodDecl.getName().toString());
            boolean isInterface = methodDecl.sym.owner.isInterface();
            if (isConstructor || (!(isPublic || isProtected) && !isInterface)) {
                return;
            }

            StringJoiner currentLine = new StringJoiner(" ");
            currentLine.add(isProtected ? "protected" : "public");
            if (methodModifiers.contains(Modifier.STATIC)) {
                currentLine.add("static");
            } else if (methodModifiers.contains(Modifier.ABSTRACT)) {
                currentLine.add("abstract");
            }

            String name = methodDecl.getName().toString();
            switch (name) {
                case "equals":
                case "toString":
                case "hashCode":
                case "canEqual":
                    return;
            }
            if (!methodDecl.getTypeParameters().isEmpty()) {
                name = methodDecl.getTypeParameters().stream()
                        .map(tp -> nameForType(tp, imports))
                        .collect(joining(", ", name + "<", ">"));
            }
            name = methodDecl.getParameters().stream()
                    .map(vd -> vd.getName() + ": " + nameForType(vd.getType(), imports))
                    .collect(joining(", ", name + "(", "):"));

            currentLine.add(name);
            currentLine.add(nameForType(methodDecl.getReturnType(), imports));

            boolean isOverloaded = methodDecl.sym.owner.members()
                    .anyMatch(s -> s.getSimpleName().equals(methodDecl.getName()));
            if (!isOverloaded && !isInterface) {
                currentLine.add("{ return null as any; }");
            } else {
                Iterable<Symbol> overloadsIter = methodDecl.sym.owner.members()
                        .getSymbols(s -> s.getSimpleName().equals(methodDecl.getName()));
                SortedSet<Symbol> overloads = new TreeSet<>(Comparator.comparing(
                        s -> s.getSimpleName() + "_" + (s instanceof Symbol.MethodSymbol
                                ? String.format("%03d", ((Symbol.MethodSymbol) s).getParameters().size())
                                : "")));
                overloadsIter.forEach(overloads::add);
                if (!isInterface && (overloads.first() == methodDecl.sym)) {
                    currentLine.add("{ return null as any; }");
                }
            }
            output.add("\t" + currentLine);
            output.add("");

        }
    }

}
