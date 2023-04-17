package ru.vsu.cs.processor;

import com.google.auto.service.AutoService;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeSpec;
import ru.cs.vsu.multithreading.annotation.Scheduled;
import ru.cs.vsu.multithreading.core.schedule.Schedulable;
import ru.cs.vsu.multithreading.core.schedule.task.ScheduleTask;
import ru.cs.vsu.multithreading.util.SchedulingIntersectionStrategy;
import ru.vsu.cs.util.Utils;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import java.io.Writer;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.Set;
import java.util.UUID;

@SupportedAnnotationTypes({"ru.cs.vsu.multithreading.annotation.Scheduled"})
@SupportedSourceVersion(SourceVersion.RELEASE_11)
@AutoService(Processor.class)
public class ScheduledAnnotationProcessor extends AbstractProcessor {

    private static final String SUFFIX = "ScheduledInit";


    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        boolean result = true;
        try {
            for (TypeElement annotation : annotations) {
                Set<? extends Element> annotatedElements = roundEnv.getElementsAnnotatedWith(annotation);
                for (Element method : annotatedElements) {
                    processingEnv.getMessager().printMessage(Diagnostic.Kind.WARNING, "ScheduledAnnotationProcessor is working");
                    result = validateMethod((ExecutableElement) method);
                    Scheduled methodAnnotation = method.getAnnotation(Scheduled.class);
                    result = validateAnnotation(methodAnnotation, (ExecutableElement) method);
                    TypeElement enclosingClass = (TypeElement) method.getEnclosingElement();
                    String packageName = enclosingClass.getQualifiedName().toString().substring(0, enclosingClass.getQualifiedName().toString().lastIndexOf("."));
                    String simpleClassName = enclosingClass.getSimpleName().toString()
                            + Utils.replaceFirstToUpper(method.getSimpleName().toString()) +
                            SUFFIX;
                    TypeSpec schedulable = TypeSpec.classBuilder(simpleClassName)
                            .addModifiers(Modifier.PUBLIC)
                            .addSuperinterface(Schedulable.class)
                            .addMethod(MethodSpec.methodBuilder("initScheduleTask")
                                    .returns(ScheduleTask.class)
                                    .addModifiers(Modifier.PUBLIC)
                                    .addStatement("return ScheduleTask.of(" + methodAnnotation.strategy() + ", " + methodAnnotation.interval() + ", \"" + methodAnnotation.start() + "\", () -> " + enclosingClass.getSimpleName() + "." + method.getSimpleName() + "()" + ", \"" + methodAnnotation.id() + "\", " + methodAnnotation.queueSize() + ")")
                                    .build())
                            .build();
                    JavaFile javaFile = JavaFile
                            .builder(packageName, schedulable)
                            .addStaticImport(SchedulingIntersectionStrategy.class, methodAnnotation.strategy().toString())
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

    private boolean validateMethod(ExecutableElement methodElement) {
        if (!methodElement.getParameters().isEmpty()) {
            var message = "Scheduled method "
                    + ((TypeElement) methodElement.getEnclosingElement()).getQualifiedName()
                    + "." + methodElement.getSimpleName() + " must not have any params";
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, message);
            throw new IllegalStateException(message);
        }
        var modifiers = methodElement.getModifiers();
        if (!modifiers.contains(Modifier.STATIC)) {
            var message = "Scheduled method "
                    + ((TypeElement) methodElement.getEnclosingElement()).getQualifiedName()
                    + "." + methodElement.getSimpleName() + " must be static";
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, message);
            throw new IllegalStateException(message);
        }

        if (!modifiers.contains(Modifier.PUBLIC)) {
            var message = "Scheduled method "
                    + ((TypeElement) methodElement.getEnclosingElement()).getQualifiedName()
                    + "." + methodElement.getSimpleName() + " must be public";
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, message);
            throw new IllegalStateException(message);
        }
        return true;
    }

    private boolean validateAnnotation(Scheduled annotation, ExecutableElement methodElement) {
        if (annotation.interval() <= 100) {
            var message = "Interval between task runs must be much than 100ms for " + ((TypeElement) methodElement.getEnclosingElement()).getQualifiedName()
                    + "." + methodElement.getSimpleName();
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, message);
            throw new IllegalStateException(message);
        }
        if (annotation.strategy().equals(SchedulingIntersectionStrategy.PUT_TO_QUEUE) && annotation.queueSize() < 1) {
            var message = "Queue size for scheduled task with type 'PUT_TO_QUEUE' must be positive" + ((TypeElement) methodElement.getEnclosingElement()).getQualifiedName()
                    + "." + methodElement.getSimpleName();
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, message);
            throw new IllegalStateException(message);
        }
        try {
            UUID.fromString(annotation.id());
        } catch (Exception e) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "Invalid UUID for " + ((TypeElement) methodElement.getEnclosingElement()).getQualifiedName()
                    + "." + methodElement.getSimpleName());
            throw new RuntimeException(e);
        }
        if (!"DEFAULT".equals(annotation.start())) {
            try {
                (new SimpleDateFormat("dd-MM-yyyyTHH:mm:ss")).parse(annotation.start());
            } catch (Exception e) {
                processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "Invalid startTime for " + ((TypeElement) methodElement.getEnclosingElement()).getQualifiedName()
                        + "." + methodElement.getSimpleName());
                throw new RuntimeException(e);
            }
        }
        return true;
    }
}
