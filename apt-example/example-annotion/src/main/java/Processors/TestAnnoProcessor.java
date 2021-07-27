package Processors;

import annotions.BindView;
import com.alibaba.fastjson.JSON;
import com.google.auto.service.AutoService;
import com.squareup.javapoet.*;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

// 使用谷歌提供的类，快速实现注解处理器
@AutoService(Processor.class)
public class TestAnnoProcessor extends AbstractProcessor {

    /**
     * 初始化函数
     * @param processingEnv
     */
    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
    }

    /**
     * 指定JAVA版本，一般返回最新版本号
     * 可以使用 @SupportedSourceVersion注解替代
     * @return
     */
    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    /**
     * 指定可解析的注解类型
     * 可以使用 @SupportedAnnotationType
     * @return
     */
    @Override
    public Set<String> getSupportedAnnotationTypes() {
        Set<String> annotationTypes = new LinkedHashSet<>();
        annotationTypes.add(BindView.class.getCanonicalName());
        return annotationTypes;
    }

    /**
     * 核心处理函数
     * @param annotations
     * @param roundEnv
     * @return
     */
    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        final Map<String, Map<String, Set<Element>>> elementMap = parseElements(roundEnv.getElementsAnnotatedWith(BindView.class));
        System.out.println("--------------" + "\t" + elementMap.size());
        generateJavaFile(elementMap);
        return true;
    }

    // --------------------------  the base methods is all private methods ---------------------------------

    /**
     * 解析全部元素
     * @param elements
     * @return
     */
    private Map<String, Map<String, Set<Element>>> parseElements(Set<? extends Element> elements){
        Map<String, Map<String, Set<Element>>> elementMap = new LinkedHashMap<>();
        // 遍历全部元素
        for (Element element : elements){
            // 判断当前元素是否属于属性变量
            System.out.println(element.getSimpleName());
            if (!element.getKind().isField()){
                continue;
            }
            // 获取属性变量的上一级元素，即类元素
            // 返回包含该element的父element，与上一个方法相反，VariableElement，方法ExecutableElement的父级是TypeElemnt，而TypeElemnt的父级是PackageElment
            TypeElement typeElement = (TypeElement) element.getEnclosingElement();
            // 获取类名
            String typeName = typeElement.getSimpleName().toString();
            // 获取类的上一级元素，即包元素
            PackageElement packageElement = (PackageElement) typeElement.getEnclosingElement();
            // 获取包名
            String packageName = packageElement.getQualifiedName().toString();

            Map<String, Set<Element>> typeElementMap = elementMap.get(packageName);
            if (typeElementMap == null){
                typeElementMap = new LinkedHashMap<>();
            }

            Set<Element> variableElements = typeElementMap.get(typeName);
            if (variableElements == null){
                variableElements = new LinkedHashSet<>();
            }

            variableElements.add(element);

            typeElementMap.put(typeName, variableElements);
            elementMap.put(packageName, typeElementMap);
        }
        return elementMap;
    }


    /**
     * 生成Java文件
     *
     * @param elementMap
     */
    private void generateJavaFile(Map<String, Map<String, Set<Element>>> elementMap) {
        Set<Map.Entry<String, Map<String, Set<Element>>>> packageElements = elementMap.entrySet();
        for (Map.Entry<String, Map<String, Set<Element>>> packageEntry : packageElements) {

            String packageName = packageEntry.getKey();
            Map<String, Set<Element>> typeElementMap = packageEntry.getValue();

            Set<Map.Entry<String, Set<Element>>> typeElements = typeElementMap.entrySet();
            for (Map.Entry<String, Set<Element>> typeEntry : typeElements) {

                String typeName = typeEntry.getKey();
                Set<Element> variableElements = typeEntry.getValue();

                ClassName className = ClassName.get(packageName, typeName);

                FieldSpec.Builder fieldSpecBuilder = FieldSpec.builder(className, "target", Modifier.PRIVATE);

                MethodSpec.Builder bindMethodBuilder = MethodSpec.methodBuilder("bind")
                        .addModifiers(Modifier.PUBLIC)
                        .addParameter(className, "activity")
                        .addStatement("target = activity");

                MethodSpec.Builder unbindMethodBuilder = MethodSpec.methodBuilder("unbind")
                        .addAnnotation(Override.class)
                        .addModifiers(Modifier.PUBLIC);

                for (Element element : variableElements) {

                    VariableElement variableElement = (VariableElement) element;

                    String variableName = variableElement.getSimpleName().toString();
                    BindView bindView = variableElement.getAnnotation(BindView.class);

                    bindMethodBuilder.addStatement("target." + variableName + " = activity.findViewById(" + bindView.viewId() + ")");
                    unbindMethodBuilder.addStatement("target." + variableName + " = null");
                }

                unbindMethodBuilder.addStatement("target = null");

                TypeSpec.Builder typeBuilder = TypeSpec.classBuilder(typeName + "_ViewBinding")
                        .addModifiers(Modifier.PUBLIC)
                        .addSuperinterface(ClassName.get("com.chad.apt.binder", "UnBinder"))
                        .addField(fieldSpecBuilder.build())
                        .addMethod(bindMethodBuilder.build())
                        .addMethod(unbindMethodBuilder.build());

                JavaFile javaFile = JavaFile.builder(packageName, typeBuilder.build()).build();

                try {
                    javaFile.writeTo(processingEnv.getFiler());
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
