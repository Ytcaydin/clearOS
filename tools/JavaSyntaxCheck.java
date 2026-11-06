import com.sun.source.util.JavacTask;
import javax.tools.*;
import java.io.File;
import java.util.*;

/** Parse only: detects source syntax issues without Android SDK. Does NOT link APIs. */
public class JavaSyntaxCheck {
    public static void main(String[] ignored) throws Exception {
        JavaCompiler compiler=ToolProvider.getSystemJavaCompiler();
        if(compiler==null)throw new IllegalStateException("JDK gerekiyor");
        File root=new File("app/src/main/java");
        List<File> sources=new ArrayList<>();
        find(root,sources);
        DiagnosticCollector<JavaFileObject> reports=new DiagnosticCollector<>();
        try(StandardJavaFileManager files=compiler.getStandardFileManager(reports,null,null)) {
            Iterable<? extends JavaFileObject> units=files.getJavaFileObjectsFromFiles(sources);
            JavacTask task=(JavacTask)compiler.getTask(null,files,reports,Arrays.asList("-proc:none"),null,units);
            int parsed=0;for(Object tree:task.parse())parsed++;
            for(Diagnostic<? extends JavaFileObject> item:reports.getDiagnostics()) {
                if(item.getKind()==Diagnostic.Kind.ERROR) throw new AssertionError(item.toString());
            }
            if(parsed!=sources.size())throw new AssertionError("Sözdizimi kaynağı eksik");
            System.out.println("PASS: " + parsed + " Android Java dosyasının sözdizimi (API bağlama/derleme değil)");
        }
    }
    static void find(File root,List<File> list) {
        File[] children=root.listFiles();if(children==null)return;
        for(File f:children)if(f.isDirectory())find(f,list);else if(f.getName().endsWith(".java"))list.add(f);
    }
}
