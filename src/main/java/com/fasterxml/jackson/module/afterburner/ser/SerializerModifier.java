package com.fasterxml.jackson.module.afterburner.ser;

import java.lang.reflect.Modifier;
import java.util.*;

import org.codehaus.jackson.map.*;
import org.codehaus.jackson.map.introspect.AnnotatedMember;
import org.codehaus.jackson.map.introspect.AnnotatedMethod;
import org.codehaus.jackson.map.introspect.BasicBeanDescription;
import org.codehaus.jackson.map.ser.*;

public class SerializerModifier extends BeanSerializerModifier
{
    public List<BeanPropertyWriter> changeProperties(SerializationConfig config,
            BasicBeanDescription beanDesc, List<BeanPropertyWriter> beanProperties)
    {
        final Class<?> beanClass = beanDesc.getBeanClass();
        PropertyCollector collector = findProperties(beanProperties);
        
        /* First things first: we can't really access stuff within private
         * classes, so skip
         */
        if (collector.isEmpty()) {
            return beanProperties;
        }
        
        // if we had a match, need to materialize
        BeanPropertyAccessor acc = collector.findAccessor(beanClass);
        // and then link accessors to bean property writers:
        ListIterator<BeanPropertyWriter> it = beanProperties.listIterator();
        while (it.hasNext()) {
            BeanPropertyWriter bpw = it.next();
            if (bpw instanceof OptimizedBeanPropertyWriter<?>) {
                it.set(((OptimizedBeanPropertyWriter<?>) bpw).withAccessor(acc));
            }
        }
        return beanProperties;
    }

    protected PropertyCollector findProperties(List<BeanPropertyWriter> beanProperties)
    {
        PropertyCollector collector = new PropertyCollector();
        ListIterator<BeanPropertyWriter> it = beanProperties.listIterator();
        while (it.hasNext()) {
            BeanPropertyWriter bpw = it.next();
            Class<?> type = bpw.getPropertyType();
            AnnotatedMember member = bpw.getMember();

            // First: we can't access private fields or methods....
            if (Modifier.isPrivate(member.getMember().getModifiers())) {
                continue;
            }
            // (although, interestingly enough, can seem to access private classes...)
            
            boolean isMethod = (member instanceof AnnotatedMethod);
            if (type == Integer.TYPE) {
                if (isMethod) {
                    it.set(collector.addIntGetter(bpw));
                }
            } else if (type == Long.TYPE) {
                if (isMethod) {
                    it.set(collector.addLongGetter(bpw));
                }
            }
        }
        return collector;
    }
}
