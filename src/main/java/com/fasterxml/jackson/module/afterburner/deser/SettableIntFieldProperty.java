package com.fasterxml.jackson.module.afterburner.deser;

import java.io.IOException;

import org.codehaus.jackson.JsonParser;
import org.codehaus.jackson.JsonProcessingException;
import org.codehaus.jackson.map.DeserializationContext;
import org.codehaus.jackson.map.deser.SettableBeanProperty;

public class SettableIntFieldProperty
    extends OptimizedSettableBeanProperty<SettableIntFieldProperty>
{
    public SettableIntFieldProperty(SettableBeanProperty src,
            BeanPropertyMutator mutator, int index)
    {
        super(src, mutator, index);
    }

    @Override
    public SettableIntFieldProperty withMutator(BeanPropertyMutator mut) {
        return new SettableIntFieldProperty(_originalSettable, mut, _propertyIndex);
    }
    
    @Override
    public void deserializeAndSet(JsonParser jp, DeserializationContext ctxt,
            Object bean) throws IOException, JsonProcessingException
    {
        int value = jp.getValueAsInt();
        _propertyMutator.intField(bean, _propertyIndex, value);
    }

    @Override
    public void set(Object bean, Object value) throws IOException {
        // not optimal (due to boxing), but better than using reflection:
        _propertyMutator.intField(bean, _propertyIndex, ((Number) value).intValue());
    }
}
