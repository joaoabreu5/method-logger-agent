package agent.logger;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;
import java.util.Set;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;


public class MethodCallAgent {
    private static BufferedWriter writer = null;

    private static final ConfigLoader config = new ConfigLoader();
    
    public static void premain(String agentArgs, Instrumentation inst) {
        Set<String> targetPackages = config.getTargetPackages();
        Boolean hasOutputFile = config.hasOutputFile();

        if (hasOutputFile) {
            String outputFilePath = config.getOutputFilePath();

            try {
                writer = new BufferedWriter(new FileWriter(outputFilePath, false));
            } 
            catch (IOException e) {
                System.err.println("Error initializing BufferedWriter for file: " + outputFilePath);
            }
        }

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
                    System.err.println("Error while transforming class: " + className);
                    return classfileBuffer; // Return unmodified bytecode on failure
                }
            }
        });
    }
    
    public static void logMethodCalled(String className, String methodName) {
        Set<String> targetPackages = config.getTargetPackages();
        String message = "METHOD CALLED --> Class: \"" + className.replace('/', '.') + "\", Method: \"" + methodName + "\"";
        
        boolean foundCalled = false;
        boolean foundCaller = false;

        StackTraceElement[] stackTraceElements = Thread.currentThread().getStackTrace();

        for (int i = 0; i < stackTraceElements.length && !foundCaller; i++) {
            String elementClassName = stackTraceElements[i].getClassName();
            String elementMethodName = stackTraceElements[i].getMethodName();

            String elementInternalClassName = elementClassName.replace(".", "/");

            if (foundCalled) {
                if (targetPackages.stream().anyMatch(elementInternalClassName::startsWith)) {
                    message += " --> Caller Class: \"" + elementClassName + "\", Caller Method \"" + elementMethodName + "\"";
                    foundCaller = true;
                }
            }
            else {
                if (elementInternalClassName.equals(className) && elementMethodName.equals(methodName)) {
                    foundCalled = true;
                }
            }
        }

        String timestamp = java.time.LocalDateTime.now().toString();
        String formattedMessage = String.format("%s %s", timestamp, message);

        if (writer == null) {
            System.out.println(formattedMessage);
        }
        else {
            try {
                writer.write(formattedMessage);
                writer.newLine();
                writer.flush();
            } 
            catch (IOException e) {
                System.err.println("Error writing to file: " + e.getMessage());
            }
        }
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
            
            if (mv != null) {
                mv = new MethodLoggingMethodVisitor(mv, className, name);
            }
            
            return mv;
        }
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
