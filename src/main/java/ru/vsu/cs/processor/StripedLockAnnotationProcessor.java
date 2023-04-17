package ru.vsu.cs.processor;

import com.google.auto.service.AutoService;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeSpec;
import ru.cs.vsu.multithreading.annotation.Scheduled;
import ru.cs.vsu.multithreading.annotation.StripedLock;
import ru.cs.vsu.multithreading.core.schedule.Schedulable;
import ru.cs.vsu.multithreading.core.schedule.task.ScheduleTask;
import ru.cs.vsu.multithreading.core.stripedlock.DefaultLocker;
import ru.cs.vsu.multithreading.util.SchedulingIntersectionStrategy;
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
import java.util.stream.Collectors;

@SupportedAnnotationTypes({"ru.cs.vsu.multithreading.annotation.StripedLock"})
@SupportedSourceVersion(SourceVersion.RELEASE_11)
@AutoService(Processor.class)
public class StripedLockAnnotationProcessor extends AbstractProcessor {

    private static final String SUFFIX = "StripedLockProxy";

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
                        StripedLock methodAnnotation = method.getAnnotation(StripedLock.class);
                        CodeBlock codeBlockBefore = CodeBlock.builder()
                                .add("$T.getInstance().lock(" + methodAnnotation.lockIdentifier() + ")", DefaultLocker.class)
                                .build();
                        CodeBlock codeBlockAfter = CodeBlock.builder()
                                .add("$T.getInstance().unlock(" + methodAnnotation.lockIdentifier() + ")", DefaultLocker.class)
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
}
