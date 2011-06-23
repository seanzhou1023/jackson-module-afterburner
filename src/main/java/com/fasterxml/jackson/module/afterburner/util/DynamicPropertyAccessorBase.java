package com.fasterxml.jackson.module.afterburner.util;

import static org.codehaus.jackson.org.objectweb.asm.Opcodes.ATHROW;
import static org.codehaus.jackson.org.objectweb.asm.Opcodes.DUP;
import static org.codehaus.jackson.org.objectweb.asm.Opcodes.ICONST_0;
import static org.codehaus.jackson.org.objectweb.asm.Opcodes.ICONST_1;
import static org.codehaus.jackson.org.objectweb.asm.Opcodes.ICONST_2;
import static org.codehaus.jackson.org.objectweb.asm.Opcodes.ICONST_3;
import static org.codehaus.jackson.org.objectweb.asm.Opcodes.ICONST_4;
import static org.codehaus.jackson.org.objectweb.asm.Opcodes.ILOAD;
import static org.codehaus.jackson.org.objectweb.asm.Opcodes.INVOKESPECIAL;
import static org.codehaus.jackson.org.objectweb.asm.Opcodes.INVOKEVIRTUAL;
import static org.codehaus.jackson.org.objectweb.asm.Opcodes.NEW;

import java.util.List;

import org.codehaus.jackson.org.objectweb.asm.MethodVisitor;

public class DynamicPropertyAccessorBase
{
    protected final static int[] ALL_INT_CONSTS = new int[] {
        ICONST_0, ICONST_1, ICONST_2, ICONST_3, ICONST_4
    };

    /*
    /**********************************************************
    /* Helper methods, generating common pieces
    /**********************************************************
     */
    
    protected static void _generateException(MethodVisitor mv, String beanClass, int propertyCount)
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

    protected static String internalClassName(String className) {
        return className.replace(".", "/");
    }
    
    protected <T> T _add(List<T> list, T value) {
        list.add(value);
        return value;
    }

}
