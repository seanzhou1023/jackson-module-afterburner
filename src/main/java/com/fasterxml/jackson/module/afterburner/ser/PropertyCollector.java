package com.fasterxml.jackson.module.afterburner.ser;

import java.util.*;

import org.codehaus.jackson.map.ser.BeanPropertyWriter;

import org.codehaus.jackson.org.objectweb.asm.ClassWriter;
import org.codehaus.jackson.org.objectweb.asm.Label;
import org.codehaus.jackson.org.objectweb.asm.MethodVisitor;

import static org.codehaus.jackson.org.objectweb.asm.Opcodes.*;

import com.fasterxml.jackson.module.afterburner.util.MyClassLoader;

/**
 * Simple collector used to keep track of properties for which code-generated
 * accessors are needed.
 */
public class PropertyCollector
{
    private final ArrayList<IntMethodPropertyWriter> _intGetters = new ArrayList<IntMethodPropertyWriter>();
    private final ArrayList<LongMethodPropertyWriter> _longGetters = new ArrayList<LongMethodPropertyWriter>();
    
    public PropertyCollector() { }

    /*
    /**********************************************************
    /* Methods for collecting properties
    /**********************************************************
     */
    
    public IntMethodPropertyWriter addIntGetter(BeanPropertyWriter bpw) {
        return _add(_intGetters, new IntMethodPropertyWriter(bpw, null, _intGetters.size(), null));
    }
    public LongMethodPropertyWriter addLongGetter(BeanPropertyWriter bpw) {
        return _add(_longGetters, new LongMethodPropertyWriter(bpw, null, _longGetters.size(), null));
    }

    public boolean isEmpty() {
        return _intGetters.isEmpty()
            && _longGetters.isEmpty()
        ;
    }
    
    /*
    /**********************************************************
    /* Code generation; high level
    /**********************************************************
     */

    public BeanPropertyAccessor findAccessor(Class<?> beanType)
    {
        String srcName = beanType.getName() + "$Access4JacksonSerializer";
        
        String generatedClass = internalClassName(srcName);
        MyClassLoader classLoader = new MyClassLoader(beanType.getClassLoader());
        Class<?> accessorClass = null;
        try {
            accessorClass = classLoader.loadClass(srcName);
        } catch (ClassNotFoundException e) { }
        if (accessorClass == null) {
            accessorClass = generateAccessorClass(beanType, classLoader, srcName, generatedClass);
        }
        try {
            return (BeanPropertyAccessor) accessorClass.newInstance();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to generate accessor class '"+srcName+"': "+e.getMessage(), e);
        }
    }
        
    public Class<?> generateAccessorClass(Class<?> beanType,
            MyClassLoader classLoader, String srcName, String generatedClass)
    {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        String superClass = internalClassName(BeanPropertyAccessor.class.getName());
        
        // muchos important: level at least 1.5 to get generics!!!
        cw.visit(V1_5, ACC_PUBLIC + ACC_SUPER, generatedClass, null, superClass, null);
        cw.visitSource(srcName + ".java", null);

        // add default (no-arg) constructor:
        MethodVisitor mv = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
        mv.visitCode();
        mv.visitVarInsn(ALOAD, 0);
        mv.visitMethodInsn(INVOKESPECIAL, superClass, "<init>", "()V");
        mv.visitInsn(RETURN);
        mv.visitMaxs(0, 0); // don't care (real values: 1,1)
        mv.visitEnd();
        
        final String beanClass = internalClassName(beanType.getName());
 
        // and then add various accessors
        if (!_intGetters.isEmpty()) {
            _addIntGetters(cw, _intGetters, beanClass);
        }
        if (!_longGetters.isEmpty()) {
            _addLongGetters(cw, _longGetters, beanClass);
        }
        cw.visitEnd();
        byte[] byteCode = cw.toByteArray();
        return classLoader.loadAndResolve(srcName, byteCode);
    }

    /*
    /**********************************************************
    /* Code generation; int getters
    /**********************************************************
     */
    
    private static void _addIntGetters(ClassWriter cw, List<IntMethodPropertyWriter> props,
            String beanClass)
    {
System.err.println("Adding "+props.size()+" int getters for "+beanClass);        
        MethodVisitor mv = cw.visitMethod(ACC_PUBLIC, "intGetter", "(Ljava/lang/Object;I)I", /*generic sig*/null, null);
        mv.visitCode();
        // first: cast bean to proper type
        mv.visitVarInsn(ALOAD, 1);
        mv.visitTypeInsn(CHECKCAST, beanClass);
        mv.visitVarInsn(ASTORE, 3);

        // Ok; minor optimization, 4 or less accessors, just do IFs; over that, use switch
        if (props.size() <= 4) {
            _addGettersUsingIf(mv, props, beanClass, IRETURN, "()I",
                    new int[] {
                    ICONST_0, ICONST_1, ICONST_2, ICONST_3, ICONST_4
                });
        } else {
            _addGettersUsingSwitch(mv, props, beanClass, IRETURN, "()I");
        }
        // and if no match, generate exception:
        _generateException(mv, beanClass, props.size());
        // and that's it
        mv.visitMaxs(0, 0); // don't care (real values: 1,1)
        mv.visitEnd();
    }

    private static void _addLongGetters(ClassWriter cw, List<LongMethodPropertyWriter> props,
            String beanClass)
    {
        MethodVisitor mv = cw.visitMethod(ACC_PUBLIC, "longGetter", "(Ljava/lang/Object;I)J", /*generic sig*/null, null);
        mv.visitCode();
        // first: cast bean to proper type
        mv.visitVarInsn(ALOAD, 1);
        mv.visitTypeInsn(CHECKCAST, beanClass);
        mv.visitVarInsn(ASTORE, 3);

        if (props.size() <= 1) { // there are just 2 constants...  so almost not worth it but
            _addGettersUsingIf(mv, props, beanClass, LRETURN, "()J",
                    new int[] { LCONST_0, LCONST_1 });
        } else {
            _addGettersUsingSwitch(mv, props, beanClass, LRETURN, "()J");
        }

        // and if no match, generate exception:
        _generateException(mv, beanClass, props.size());

        // and that's it
        mv.visitMaxs(0, 0); // don't care (real values: 1,1)
        mv.visitEnd();
    }
    
    /*
    /**********************************************************
    /* Helper methods, method accessor creation
    /**********************************************************
     */
    
    private static <T extends OptimizedBeanPropertyWriter<T>> void _addGettersUsingIf(MethodVisitor mv,
            List<T> props, String beanClass, int returnOpcode, String getterSignature,
            int[] constantOpcodes)
    {
        mv.visitVarInsn(ILOAD, 2); // load second arg (index)
        Label next = new Label();
        // first: check if 'index == 0'
        mv.visitJumpInsn(IFNE, next); // "if not zero, goto L (skip stuff)"

        // call first getter:
        mv.visitVarInsn(ALOAD, 3); // load local for cast bean
        mv.visitMethodInsn(INVOKEVIRTUAL, beanClass, props.get(0).getMember().getName(), getterSignature);
        mv.visitInsn(returnOpcode);

        // And from this point on, loop a bit
        for (int i = 1, len = props.size(); i < len; ++i) {
            mv.visitLabel(next);
            next = new Label();
            mv.visitVarInsn(ILOAD, 2); // load second arg (index)
            mv.visitInsn(constantOpcodes[i]);
            mv.visitJumpInsn(IF_ICMPNE, next);
            mv.visitVarInsn(ALOAD, 3); // load bean
            mv.visitMethodInsn(INVOKEVIRTUAL, beanClass, props.get(i).getMember().getName(), getterSignature);
            mv.visitInsn(returnOpcode);
        }
        mv.visitLabel(next);
    }        

    private static <T extends OptimizedBeanPropertyWriter<T>> void _addGettersUsingSwitch(MethodVisitor mv,
            List<T> props, String beanClass, int returnOpcode, String getterSignature)
    {
        mv.visitVarInsn(ILOAD, 2); // load second arg (index)

        Label[] labels = new Label[props.size()];
        for (int i = 0, len = labels.length; i < len; ++i) {
            labels[i] = new Label();
        }
        Label defaultLabel = new Label();
        mv.visitTableSwitchInsn(0, labels.length - 1, defaultLabel, labels);
        for (int i = 0, len = labels.length; i < len; ++i) {
            mv.visitLabel(labels[i]);
            mv.visitVarInsn(ALOAD, 3); // load bean
            mv.visitMethodInsn(INVOKEVIRTUAL, beanClass, props.get(i).getMember().getName(), getterSignature);
            mv.visitInsn(returnOpcode);
        }
        mv.visitLabel(defaultLabel);
    }        

    /*
    /**********************************************************
    /* Helper methods, generating common pieces
    /**********************************************************
     */
    
    private static void _generateException(MethodVisitor mv, String beanClass, int propertyCount)
    {
        mv.visitTypeInsn(NEW, "java/lang/IllegalArgumentException");
        mv.visitInsn(DUP);
        mv.visitTypeInsn(NEW, "java/lang/StringBuilder");
        mv.visitInsn(DUP);
        mv.visitLdcInsn("Invalid field index (valid; 0 <= n < "+propertyCount+"): ");
        mv.visitMethodInsn(INVOKESPECIAL, "java/lang/StringBuilder", "<init>", "(Ljava/lang/String;)V");
        mv.visitVarInsn(ILOAD, 2);
        mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(I)Ljava/lang/StringBuilder;");
        mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "toString", "()Ljava/lang/String;");
        mv.visitMethodInsn(INVOKESPECIAL, "java/lang/IllegalArgumentException", "<init>", "(Ljava/lang/String;)V");
        mv.visitInsn(ATHROW);
    }
    
    /*
    /**********************************************************
    /* Helper methods, other
    /**********************************************************
     */

    private static String internalClassName(String className) {
        return className.replace(".", "/");
    }
    
    private <T extends OptimizedBeanPropertyWriter<T>> T _add(List<T> list, T value) {
        list.add(value);
        return value;
    }
}
