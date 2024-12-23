package agent.logger;

import java.io.IOException;
import java.io.InputStream;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;


public class MethodCallAgent {
    private static Set<String> loadTargetPackages() {
        Set<String> targetPackages = new HashSet<>();

        try (InputStream input = MethodCallAgent.class.getResourceAsStream("/agent-config.properties")) {
            Properties props = new Properties();
            props.load(input);
            
            String packages = props.getProperty("target.packages", "");

            for (String pkg : packages.split(",")) {
                targetPackages.add(pkg.replace(".", "/"));
            }
        }
        catch (IOException e) {
            e.printStackTrace();
        }

        return targetPackages;
    }

    public static void premain(String agentArgs, Instrumentation inst) {
        Set<String> targetPackages = loadTargetPackages();

        inst.addTransformer(new ClassFileTransformer() {
            @Override
            public byte[] transform(ClassLoader loader, String className,
                                    Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) {
                
                if (className == null || targetPackages.stream().noneMatch(className::startsWith)) {
                    return null;
                }

                try {
                    // Use ASM to instrument the class
                    ClassReader classReader = new ClassReader(classfileBuffer);
                    ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
                    ClassVisitor classVisitor = new MethodLoggingClassVisitor(classWriter, className);
                    classReader.accept(classVisitor, 0);
                    return classWriter.toByteArray();
                } 
                catch (Exception e) {
                    e.printStackTrace();
                    return classfileBuffer; // Return unmodified bytecode on failure
                }
            }
        });
    }

    // Custom ClassVisitor to add method logging
    private static class MethodLoggingClassVisitor extends ClassVisitor {
        private final String className;

        public MethodLoggingClassVisitor(ClassVisitor cv, String className) {
            super(Opcodes.ASM9, cv);
            this.className = className; // Store the class name
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
            MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
            
            if (mv != null && !name.equals("<init>") && !name.equals("<clinit>")) {
                mv = new MethodLoggingMethodVisitor(mv, className, name);
            }
            
            return mv;
        }
    }

    // Method to log app method called
    public static void logMethodCalled(String className, String methodName) {
        String message = "METHOD CALLED --> Class: \"" + className.replace('/', '.') + "\", Method: \"" + methodName + "\"";
        String timestamp = java.time.LocalDateTime.now().toString();

        System.out.println(String.format("%s  %s", timestamp, message));
    }

    // Custom MethodVisitor to insert logging at the start of each method
    private static class MethodLoggingMethodVisitor extends MethodVisitor {
        private final String className;
        private final String methodName;

        public MethodLoggingMethodVisitor(MethodVisitor mv, String className, String methodName) {
            super(Opcodes.ASM9, mv);
            this.className = className;
            this.methodName = methodName;
        }

        @Override
        public void visitCode() {
            // Step 1: Push className and methodName to the stack
            mv.visitLdcInsn(className);
            mv.visitLdcInsn(methodName);

            // Step 2: Call MethodCallAgent.logMethodCalled() with "className" and "methodName" as arguments
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "agent/logger/MethodCallAgent", "logMethodCalled", "(Ljava/lang/String;Ljava/lang/String;)V", false);

            super.visitCode();
        }
    }
}