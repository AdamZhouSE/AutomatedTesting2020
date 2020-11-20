import com.ibm.wala.classLoader.CallSiteReference;
import com.ibm.wala.classLoader.ShrikeBTMethod;
import com.ibm.wala.ipa.callgraph.AnalysisScope;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.CallGraphStats;
import com.ibm.wala.ipa.callgraph.cha.CHACallGraph;
import com.ibm.wala.ipa.callgraph.impl.AllApplicationEntrypoints;
import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.ibm.wala.ipa.cha.ClassHierarchyException;
import com.ibm.wala.ipa.cha.ClassHierarchyFactory;
import com.ibm.wala.shrikeCT.InvalidClassFileException;
import com.ibm.wala.types.ClassLoaderReference;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.util.CancelException;
import com.ibm.wala.util.config.AnalysisScopeReader;
import java.io.*;
import java.util.*;

/**
 * 经典自动化测试大作业
 * @author 181850254 周杼彦
 */
public class WalaTest {

    /**
     * IS_CLASS 是否执行方法级选择
     * PROJECT_TARGET target目录
     * CHANGE_INFO 变更信息路径
     */
    private static boolean IS_CLASS;
    private static String PROJECT_TARGET;
    private static String CHANGE_INFO;

    private static final String SCOPE_FILE_NAME = "scope.txt";
    private static final String PATHNAME = "exclusion.txt";

    private AnalysisScope scope;
    private ClassHierarchy cha;
    private CHACallGraph cg;
    private HashSet<String> testMethodsSig = new HashSet<String>();
    private HashSet<String> testMethods = new HashSet<String>();
    private HashSet<CallRelation> callRelations = new HashSet<CallRelation>();
    private HashSet<CallRelation> methodCallRelations = new HashSet<CallRelation>();
    private HashSet<CallRelation> classCallRelations = new HashSet<CallRelation>();
    private HashSet<String> changeInfo = new HashSet<String>();


    /**
     * 从命令行参数获取路径
     * @param args 命令行参数
     */
    private void getPath(String[] args) {
        IS_CLASS = "-c".equals(args[0]);
        PROJECT_TARGET = args[1];
        CHANGE_INFO = args[2];
        System.out.println("project_target: " + PROJECT_TARGET);
        System.out.println("change_info: " + CHANGE_INFO);
        System.out.println("IS_CLASS: " + IS_CLASS);
    }

    /**
     * 递归获取一个目录下的所有class文件
     * @param dir           文件路径
     * @param classFiles    存储读取到的所有class文件
     */
    private void getClazz(String dir, ArrayList<File> classFiles) {
        File file = new File(dir);
        File[] files = file.listFiles();
        if (files != null) {
            for (File f : files) {
                // 如果是目录，递归读取
                if (f.isDirectory()) {
                    getClazz(f.getAbsolutePath(), classFiles);
                }
                else {
                    // 匹配.class文件
                    if (f.getName().endsWith(".class")) {
                        System.out.println("getClassFile: " + f.getName());
                        classFiles.add(f);
                    }
                }
            }
        }
    }

    /**
     * 生成分析域
     */
    private void generateScope() throws IOException {
        scope = AnalysisScopeReader.readJavaScope(
                SCOPE_FILE_NAME,
                new File(PATHNAME),
                ClassLoader.getSystemClassLoader());
        System.out.println("generateScope");
    }

    /**
     * 将类加入分析域中
     * @param classFiles 存储想要动态加入分析域的类
     */
    private void addClazz(ArrayList<File> classFiles) throws InvalidClassFileException {
        for (File clazz : classFiles) {
            scope.addClassFileToScope(ClassLoaderReference.Application, clazz);
        }
        System.out.println(scope);
    }

    /**
     * 生成类层次关系对象，确定进入点并构建调用图
     */
    private void generateHierarchy() throws ClassHierarchyException, CancelException {
        cha = ClassHierarchyFactory.makeWithRoot(scope);
        // 构建调用图
        cg = new CHACallGraph(cha);
        cg.init(new AllApplicationEntrypoints(scope, cha));
        String stats = CallGraphStats.getStats(cg);
        System.out.println(stats);
    }

    /**
     * 获取测试方法的签名
     */
    private void getSignature() {
        // 遍历cg中所有的节点
        for (CGNode node : cg) {
            if (node.getMethod() instanceof ShrikeBTMethod) {
                // 一般地，本项目中所有和业务逻辑相关的方法都是ShrikeBTMethod对象
                ShrikeBTMethod method = (ShrikeBTMethod) node.getMethod();
                if ("Application".equals(method.getDeclaringClass().getClassLoader().toString())) {
                    // 获取声明该方法的类的内部表示
                    String classInnerName = method.getDeclaringClass().getName().toString();
                    // 获取方法签名
                    String signature = method.getSignature();
                    testMethodsSig.add(signature);
                }
            }
        }
    }

    /**
     * 构建方法关系依赖集合
     * @throws InvalidClassFileException
     */
    private void buildRelations() throws InvalidClassFileException {
        for (CGNode node : cg) {
            if (node.getMethod() instanceof ShrikeBTMethod) {
                ShrikeBTMethod method = (ShrikeBTMethod) node.getMethod();
                if ("Application".equals(method.getDeclaringClass().getClassLoader().toString())) {
                    String signature = method.getSignature();
                    // 获取调用信息
                    Collection<CallSiteReference> references = method.getCallSites();
                    for (CallSiteReference reference : references) {
                        MethodReference methodReference = reference.getDeclaredTarget();
                        String refSignature = methodReference.getSignature();
                        if (!refSignature.startsWith("java") && !refSignature.startsWith("org")) {
                            methodCallRelations.add(new CallRelation(signature, refSignature));
                        }
                    }
                }
            }
        }
        callRelations = methodCallRelations;
        if (IS_CLASS) {
            for (CallRelation relation : methodCallRelations) {
                classCallRelations.add(new CallRelation(relation.getCallerClass(), relation.getCalleeClass()));
            }
            callRelations = classCallRelations;
        }
    }

    /**
     * 生层关系依赖图dot文件
     */
    private void generateDot() throws IOException {
        String path, header;
        if (IS_CLASS) {
            path = "./class.dot";
            header = "digraph _class {\n";
        }
        else {
            path = "./method.dot";
            header = "digraph _method {\n";
        }
        BufferedWriter out = new BufferedWriter(new FileWriter(path));
        out.write(header);
        for (CallRelation relation : callRelations) {
            out.write(relation.getDotFormat());
        }
        out.write("}");
        out.close();
        System.out.println("DotFile: " + path);
    }

    /**
     * 获得修改信息
     * @throws IOException
     */
    private void getChangeInfo() throws IOException {
        BufferedReader in = new BufferedReader(new FileReader(CHANGE_INFO));
        while (true) {
            String line = in.readLine();
            if (line == null || "\n".equals(line)) {
                break;
            }
            // 根据是类级测试还是方法级测试获取不同的信息
            String info;
            if (IS_CLASS) {
                info = line.split(" ")[0].substring(1).replace('/', '.');
            }
            else {
                info = line.split(" ")[1];

            }
            changeInfo.add(info);
        }
    }

    /**
     * 选择测试方法
     */
    private void selectTestMethods() {
        HashSet<String> visited = new HashSet<String>();
        Queue<String> queue;
        // 如果是类级检测，将需要被修改的类的所有方法入队
        if (IS_CLASS) {
            queue = new LinkedList<String>();
            for (CallRelation callRelation : methodCallRelations) {
                String calleeClass = callRelation.getCalleeClass();
                String callee = callRelation.getCallee();
                if (changeInfo.contains(calleeClass)) {
                    if (!visited.contains(callee)) {
                        queue.offer(callee);
                        visited.add(callee);
                    }
                }
            }
        }
        // 如果是方法级检测，将所有需要被修改的方法入队
        else {
            queue = new LinkedList<String>(changeInfo);
        }
    // 每次出队一个方法，在方法依赖关系中找到所有依赖于此方法的方法
    while (!queue.isEmpty()) {
            String q = queue.poll();
            for (CallRelation relation : methodCallRelations) {
                String callee = relation.getCallee();
                if (q.equals(callee)) {
                    String caller = relation.getCaller();
                    // 将依赖关系的左侧入队
                    if (!visited.contains(caller)) {
                        queue.add(caller);
                        visited.add(caller);
                    }
                    // 如果左侧是个测试方法就选中
                    if (testMethodsSig.contains(caller)) {
                        testMethods.add(caller);
                    }
                }
            }
        }
    }

    /**
     * 保存结果
     * @throws IOException
     */
    private void saveRes() throws IOException {
        String filename;
        if (IS_CLASS) {
            filename = "selection-class.txt";
        }
        else {
            filename = "selection-method.txt";
        }
        BufferedWriter out = new BufferedWriter(new FileWriter(filename));
        for (String s : testMethods) {
            out.write(s + "\n");
        }
        out.close();
        System.out.println("SelectionFile: " + filename);
    }


    public static void main(String[] args)
            throws IOException, InvalidClassFileException, ClassHierarchyException, CancelException {
        WalaTest wala = new WalaTest();
        // 解析参数
        wala.getPath(args);
        // 加载类文件
        ArrayList<File> classFiles = new ArrayList<File>();
        ArrayList<File> testClassFiles = new ArrayList<File>();
        File file = new File(PROJECT_TARGET);
        File[] files = file.listFiles();
        // 只有classes和test-classes文件夹里面有.class文件
        if (files != null) {
            for (File f : files) {
                if ("classes".equals(f.getName())) {
                    wala.getClazz(f.getAbsolutePath(), classFiles);
                }
                else if ("test-classes".equals(f.getName())) {
                    wala.getClazz(f.getAbsolutePath(), testClassFiles);
                }
            }
        }
        else {
            System.out.println("Empty directory!");
            System.exit(1);
        }
        // 生成分析域并添加相应的类文件对象
        wala.generateScope();
        wala.addClazz(testClassFiles);
        // 生成类层次并构建调用图
        wala.generateHierarchy();
        // 获取方法签名
        wala.getSignature();
        // 加入生产类，构建完整的依赖图
        wala.addClazz(classFiles);
        // 重新生成类层次关系对象
        wala.generateHierarchy();
        // 构建关系依赖图
        wala.buildRelations();
        // 输出关系依赖图
        wala.generateDot();
        // 根据change_info.txt获取更改信息
        wala.getChangeInfo();
        // 挑选测试方法并存储到指定文件中
        wala.selectTestMethods();
        wala.saveRes();
        System.out.println("Finish!");
    }
}
