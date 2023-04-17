package ru.vsu.cs.util;

import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.TypeSpec;

import javax.annotation.processing.Messager;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeKind;
import javax.tools.Diagnostic;
import java.util.stream.Collectors;

public class Utils {

    public static String replaceFirstToUpper(String source) {
        var first = source.substring(0, 1);
        var res = source.substring(1);
        return first.toUpperCase() + res;
    }

    public static boolean validateMethodForExtending(ExecutableElement method, Messager messager) {
        if (!method.getModifiers().contains(Modifier.PUBLIC) && method.getModifiers().contains(Modifier.PROTECTED)) {
            var message = "Method for overriding must be either public or protected" + ((TypeElement) method.getEnclosingElement()).getQualifiedName() + "." + method.getSimpleName();
            messager.printMessage(Diagnostic.Kind.ERROR, message);
            throw new IllegalStateException(message);
        }

        if (method.getModifiers().contains(Modifier.FINAL)) {
            var message = "Method for overriding must not be final" + ((TypeElement) method.getEnclosingElement()).getQualifiedName() + "." + method.getSimpleName();
            messager.printMessage(Diagnostic.Kind.ERROR, message);
            throw new IllegalStateException(message);
        }

        if (method.getModifiers().contains(Modifier.STATIC)) {
            var message = "Method for overriding must not be static" + ((TypeElement) method.getEnclosingElement()).getQualifiedName() + "." + method.getSimpleName();
            messager.printMessage(Diagnostic.Kind.ERROR, message);
            throw new IllegalStateException(message);
        }

        var enclosingElement = ((TypeElement) method.getEnclosingElement());
        if (enclosingElement.getModifiers().contains(Modifier.FINAL)) {
            var message = "Class for overriding must not be final" + ((TypeElement) method.getEnclosingElement()).getQualifiedName();
            messager.printMessage(Diagnostic.Kind.ERROR, message);
            throw new IllegalStateException(message);
        }

        if (!enclosingElement.getModifiers().contains(Modifier.PUBLIC)) {
            var message = "Class for overriding must be public" + ((TypeElement) method.getEnclosingElement()).getQualifiedName();
            messager.printMessage(Diagnostic.Kind.ERROR, message);
            throw new IllegalStateException(message);
        }


        return true;
    }

    public static TypeSpec.Builder addConstructor(TypeSpec.Builder builder, ExecutableElement constructorElement) {
        MethodSpec.Builder methodBuilder = MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PUBLIC);
        constructorElement.getParameters().forEach(param -> methodBuilder.addParameter(ParameterSpec.get(param)));
        methodBuilder.addStatement("super(" + constructorElement.getParameters().stream()
                .map(it -> it.getSimpleName().toString())
                .collect(Collectors.joining(", ")) + ")");
        builder.addMethod(methodBuilder.build());
        return builder;
    }

    public static TypeSpec.Builder addProxyMethod(TypeSpec.Builder builder, ExecutableElement methodElement, CodeBlock blockBefore, CodeBlock blockAfter) {
        MethodSpec.Builder methodBuilder = MethodSpec.overriding(methodElement)
                .addModifiers(Modifier.PUBLIC);
        methodBuilder.addStatement(blockBefore);
        String superCall = "super." + methodElement.getSimpleName().toString() + "(" + methodElement.getParameters().stream()
                .map(it -> it.getSimpleName().toString())
                .collect(Collectors.joining(", ")) + ")";
        if (!methodElement.getReturnType().getKind().equals(TypeKind.VOID)) {
            superCall = "var returnValueGenerated = " + superCall;
        }
        methodBuilder.addStatement(superCall);
        methodBuilder.addStatement(blockAfter);
        if (!methodElement.getReturnType().getKind().equals(TypeKind.VOID)) {
            methodBuilder.addStatement("return returnValueGenerated");
        }
        builder.addMethod(methodBuilder.build());
        return builder;
    }
}
