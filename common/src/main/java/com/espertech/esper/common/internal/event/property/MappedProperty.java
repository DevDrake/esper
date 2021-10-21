/*
 ***************************************************************************************
 *  Copyright (C) 2006 EsperTech, Inc. All rights reserved.                            *
 *  http://www.espertech.com/esper                                                     *
 *  http://www.espertech.com                                                           *
 *  ---------------------------------------------------------------------------------- *
 *  The software in this package is published under the terms of the GPL license       *
 *  a copy of which has been included with this distribution in the license.txt file.  *
 ***************************************************************************************
 */
package com.espertech.esper.common.internal.event.property;

import com.espertech.esper.common.client.type.EPType;
import com.espertech.esper.common.client.type.EPTypeClass;
import com.espertech.esper.common.client.type.EPTypePremade;
import com.espertech.esper.common.internal.event.arr.ObjectArrayEventPropertyGetterAndMapped;
import com.espertech.esper.common.internal.event.arr.ObjectArrayMappedPropertyGetter;
import com.espertech.esper.common.internal.event.bean.core.BeanEventType;
import com.espertech.esper.common.internal.event.bean.core.PropertyStem;
import com.espertech.esper.common.internal.event.bean.getter.KeyedMapFieldPropertyGetter;
import com.espertech.esper.common.internal.event.bean.getter.KeyedMapMethodPropertyGetter;
import com.espertech.esper.common.internal.event.bean.getter.KeyedMethodPropertyGetter;
import com.espertech.esper.common.internal.event.bean.service.BeanEventTypeFactory;
import com.espertech.esper.common.internal.event.core.EventBeanTypedEventFactory;
import com.espertech.esper.common.internal.event.core.EventPropertyGetterAndMapped;
import com.espertech.esper.common.internal.event.core.EventPropertyGetterSPI;
import com.espertech.esper.common.internal.event.map.MapEventPropertyGetterAndMapped;
import com.espertech.esper.common.internal.event.map.MapMappedPropertyGetter;
import com.espertech.esper.common.internal.event.xml.*;
import com.espertech.esper.common.internal.util.ClassHelperGenericType;
import com.espertech.esper.common.internal.util.JavaClassHelper;

import java.io.StringWriter;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Locale;
import java.util.Map;

/**
 * Represents a mapped property or array property, ie. an 'value' property with read method getValue(int index)
 * or a 'array' property via read method getArray() returning an array.
 */
public class MappedProperty extends PropertyBase implements PropertyWithKey {
    private String key;

    public MappedProperty(String propertyName) {
        super(propertyName);
    }

    /**
     * Ctor.
     *
     * @param propertyName is the property name of the mapped property
     * @param key          is the key value to access the mapped property
     */
    public MappedProperty(String propertyName, String key) {
        super(propertyName);
        this.key = key;
    }

    /**
     * Returns the key value for mapped access.
     *
     * @return key value
     */
    public String getKey() {
        return key;
    }

    public String[] toPropertyArray() {
        return new String[]{this.getPropertyNameAtomic()};
    }

    public boolean isDynamic() {
        return false;
    }

    public EventPropertyGetterAndMapped getGetter(BeanEventType eventType, EventBeanTypedEventFactory eventBeanTypedEventFactory, BeanEventTypeFactory beanEventTypeFactory) {
        PropertyStem propertyDesc = eventType.getMappedProperty(propertyNameAtomic);
        if (propertyDesc != null) {
            Method method = propertyDesc.getReadMethod();
            return new KeyedMethodPropertyGetter(method, key, eventBeanTypedEventFactory, beanEventTypeFactory);
        }

        // Try the array as a simple property
        propertyDesc = eventType.getSimpleProperty(propertyNameAtomic);
        if (propertyDesc == null) {
            return null;
        }

        EPTypeClass returnType = propertyDesc.getReturnType(eventType.getUnderlyingEPType());
        if (!JavaClassHelper.isImplementsInterface(returnType, Map.class)) {
            return null;
        }

        if (propertyDesc.getReadMethod() != null) {
            Method method = propertyDesc.getReadMethod();
            return new KeyedMapMethodPropertyGetter(method, key, eventBeanTypedEventFactory, beanEventTypeFactory);
        } else {
            Field field = propertyDesc.getAccessorField();
            return new KeyedMapFieldPropertyGetter(field, key, eventBeanTypedEventFactory, beanEventTypeFactory);
        }
    }

    public EPTypeClass getPropertyType(BeanEventType eventType, BeanEventTypeFactory beanEventTypeFactory) {
        PropertyStem propertyDesc = eventType.getMappedProperty(propertyNameAtomic);
        if (propertyDesc != null) {
            return ClassHelperGenericType.getMethodReturnEPType(propertyDesc.getReadMethod(), eventType.getUnderlyingEPType());
        }

        // Check if this is an method returning array which is a type of simple property
        PropertyStem descriptor = eventType.getSimpleProperty(propertyNameAtomic);
        if (descriptor == null) {
            return null;
        }

        EPTypeClass returnType = descriptor.getReturnType(eventType.getUnderlyingEPType());
        if (!JavaClassHelper.isImplementsInterface(returnType, Map.class)) {
            return null;
        }
        return JavaClassHelper.getSecondParameterTypeOrObject(returnType);
    }

    public EPType getPropertyTypeMap(Map optionalMapPropTypes, BeanEventTypeFactory beanEventTypeFactory) {
        if (optionalMapPropTypes == null) {
            return null;
        }
        Object type = optionalMapPropTypes.get(this.getPropertyNameAtomic());
        if (type == null) {
            return null;
        }
        if (type instanceof EPTypeClass) {
            if (JavaClassHelper.isImplementsInterface((EPTypeClass) type, Map.class)) {
                return EPTypePremade.OBJECT.getEPType();
            }
        }
        return null;  // Mapped properties are not allowed in non-dynamic form in a map
    }

    public MapEventPropertyGetterAndMapped getGetterMap(Map optionalMapPropTypes, EventBeanTypedEventFactory eventBeanTypedEventFactory, BeanEventTypeFactory beanEventTypeFactory) {
        if (optionalMapPropTypes == null) {
            return null;
        }
        Object type = optionalMapPropTypes.get(getPropertyNameAtomic());
        if (type == null) {
            return null;
        }
        if (type instanceof EPTypeClass) {
            if (JavaClassHelper.isImplementsInterface((EPTypeClass) type, Map.class)) {
                return new MapMappedPropertyGetter(getPropertyNameAtomic(), this.getKey());
            }
        }
        if (type instanceof Map) {
            return new MapMappedPropertyGetter(getPropertyNameAtomic(), this.getKey());
        }
        return null;
    }

    public void toPropertyEPL(StringWriter writer) {
        writer.append(propertyNameAtomic);
        writer.append("('");
        writer.append(key);
        writer.append("')");
    }

    public EventPropertyGetterSPI getGetterDOM(SchemaElementComplex complexProperty, EventBeanTypedEventFactory eventBeanTypedEventFactory, BaseXMLEventType eventType, String propertyExpression) {
        for (SchemaElementComplex complex : complexProperty.getChildren()) {
            if (!complex.getName().equals(propertyNameAtomic)) {
                continue;
            }
            for (SchemaItemAttribute attribute : complex.getAttributes()) {
                if (!attribute.getName().toLowerCase(Locale.ENGLISH).equals("id")) {
                    continue;
                }
            }

            return new DOMMapGetter(propertyNameAtomic, key, null);
        }

        return null;
    }

    public EventPropertyGetterSPI getGetterDOM() {
        return new DOMMapGetter(propertyNameAtomic, key, null);
    }

    public SchemaItem getPropertyTypeSchema(SchemaElementComplex complexProperty) {
        for (SchemaElementComplex complex : complexProperty.getChildren()) {
            if (!complex.getName().equals(propertyNameAtomic)) {
                continue;
            }
            for (SchemaItemAttribute attribute : complex.getAttributes()) {
                if (!attribute.getName().toLowerCase(Locale.ENGLISH).equals("id")) {
                    continue;
                }
            }

            return complex;
        }

        return null;
    }

    public ObjectArrayEventPropertyGetterAndMapped getGetterObjectArray(Map<String, Integer> indexPerProperty, Map<String, Object> nestableTypes, EventBeanTypedEventFactory eventBeanTypedEventFactory, BeanEventTypeFactory beanEventTypeFactory) {
        Integer index = indexPerProperty.get(propertyNameAtomic);
        if (index == null) {
            return null;
        }
        Object type = nestableTypes.get(getPropertyNameAtomic());
        if (type instanceof EPTypeClass) {
            if (JavaClassHelper.isImplementsInterface((EPTypeClass) type, Map.class)) {
                return new ObjectArrayMappedPropertyGetter(index, this.getKey());
            }
        }
        if (type instanceof Map) {
            return new ObjectArrayMappedPropertyGetter(index, this.getKey());
        }
        return null;
    }
}
