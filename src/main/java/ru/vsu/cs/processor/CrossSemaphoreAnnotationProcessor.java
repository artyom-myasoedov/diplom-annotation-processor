package ru.vsu.cs.processor;

import com.google.auto.service.AutoService;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.TypeSpec;
import ru.cs.vsu.multithreading.annotation.CrossSemaphore;
import ru.cs.vsu.multithreading.annotation.StripedLock;
import ru.cs.vsu.multithreading.core.crosssync.CrossSyncSemaphore;
import ru.cs.vsu.multithreading.core.crosssync.CrossSyncSemaphoreUtils;
import ru.cs.vsu.multithreading.core.stripedlock.DefaultLocker;
import ru.vsu.cs.util.Utils;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import java.io.Writer;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@SupportedAnnotationTypes({"ru.cs.vsu.multithreading.annotation.CrossSemaphore"})
@SupportedSourceVersion(SourceVersion.RELEASE_11)
@AutoService(Processor.class)
public class CrossSemaphoreAnnotationProcessor extends AbstractProcessor {
    private static final String SUFFIX = "CrossSemaphoreProxy";

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        boolean result = true;
        try {
            for (TypeElement annotation : annotations) {
                Set<? extends Element> annotatedElements = roundEnv.getElementsAnnotatedWith(annotation);
                Map<String, List<ExecutableElement>> classes2Methods = annotatedElements.stream()
                        .peek(elem -> Utils.validateMethodForExtending((ExecutableElement) elem, processingEnv.getMessager()))
                        .map(it -> (ExecutableElement) it)
                        .collect(Collectors.groupingBy((ExecutableElement elem) -> elem.getEnclosingElement().getSimpleName().toString()));

                for (var entry : classes2Methods.entrySet()) {
                    processingEnv.getMessager().printMessage(Diagnostic.Kind.WARNING, "StripedLockAnnotationProcessor is working");
                    TypeElement enclosingClass = (TypeElement) entry.getValue().get(0).getEnclosingElement();
                    String packageName = enclosingClass.getQualifiedName().toString().substring(0, enclosingClass.getQualifiedName().toString().lastIndexOf("."));
                    String simpleClassName = enclosingClass.getSimpleName().toString() + SUFFIX;
                    TypeSpec.Builder lockStripingBuilder = TypeSpec.classBuilder(simpleClassName)
                            .addModifiers(Modifier.PUBLIC)
                            .superclass(enclosingClass.asType());
                    enclosingClass.getEnclosedElements().stream()
                            .filter(elem -> elem.getKind().equals(ElementKind.CONSTRUCTOR))
                            .forEach(elem -> Utils.addConstructor(lockStripingBuilder, (ExecutableElement) elem));
                    for (var method : entry.getValue()) {
                        CrossSemaphore methodAnnotation = method.getAnnotation(CrossSemaphore.class);
                        validateAnnotation(methodAnnotation);
                        CodeBlock codeBlockBefore = CodeBlock.builder()
                                .add("$T.getDefaultDaoInstance().createIfNotExists($T.newInstance($T.fromString(\"" + methodAnnotation.semophoreId() + "\"), " + methodAnnotation.permits() + ", " + methodAnnotation.permits() + "));", CrossSyncSemaphoreUtils.class, CrossSyncSemaphore.class, UUID.class)
                                .add("$T.getDefaultDaoInstance().acquire($T.fromString(\"" + methodAnnotation.semophoreId() + "\"))", CrossSyncSemaphoreUtils.class, UUID.class)
                                .build();
                        CodeBlock codeBlockAfter = CodeBlock.builder()
                                .add("$T.getDefaultDaoInstance().release($T.fromString(\"" + methodAnnotation.semophoreId() + "\"))", CrossSyncSemaphoreUtils.class, UUID.class)
                                .build();
                        Utils.addProxyMethod(lockStripingBuilder, method, codeBlockBefore, codeBlockAfter);
                    }
                    JavaFile javaFile = JavaFile
                            .builder(packageName, lockStripingBuilder.build())
                            .indent("    ")
                            .build();
                    JavaFileObject jfo = processingEnv.getFiler().createSourceFile(packageName + "." + simpleClassName);
                    Writer writer = jfo.openWriter();
                    writer.append(javaFile.toString());
                    writer.flush();
                    writer.close();
                }
            }
        } catch (Throwable e) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "Error: " + e.getMessage());
            throw new RuntimeException(e);
        }
        return result;
    }

    private boolean validateAnnotation(CrossSemaphore annotation) {
        if (annotation.permits() < 1) {
            var msg = "Permits for @CrossSemaphore must be more than 0";
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, msg);
            throw new IllegalStateException(msg);
        }

        try {
            UUID.fromString(annotation.semophoreId());
        } catch (Exception e) {
            var msg = "SemaphoreId for @CrossSemaphore must have uuid format";
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, msg);
            throw new IllegalStateException(msg, e);
        }
        return true;
    }
}
