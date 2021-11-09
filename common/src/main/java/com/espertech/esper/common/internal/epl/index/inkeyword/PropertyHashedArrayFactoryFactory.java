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
package com.espertech.esper.common.internal.epl.index.inkeyword;

import com.espertech.esper.common.client.EventPropertyValueGetter;
import com.espertech.esper.common.client.EventType;
import com.espertech.esper.common.client.serde.DataInputOutputSerde;
import com.espertech.esper.common.client.type.EPTypeClass;
import com.espertech.esper.common.client.util.StateMgmtSetting;
import com.espertech.esper.common.internal.epl.index.base.EventTableFactory;
import com.espertech.esper.common.internal.epl.index.base.EventTableFactoryFactory;
import com.espertech.esper.common.internal.epl.index.base.EventTableFactoryFactoryContext;

public class PropertyHashedArrayFactoryFactory implements EventTableFactoryFactory {
    public final static EPTypeClass EPTYPE = new EPTypeClass(PropertyHashedArrayFactoryFactory.class);

    protected final int streamNum;
    protected final String[] propertyNames;
    protected final EPTypeClass[] propertyTypes;
    protected final DataInputOutputSerde[] propertySerdes;
    protected final boolean unique;
    protected final EventPropertyValueGetter[] propertyGetters;
    protected final boolean isFireAndForget;
    private final StateMgmtSetting stateMgmtSettings;

    public PropertyHashedArrayFactoryFactory(int streamNum, String[] propertyNames, EPTypeClass[] propertyTypes, DataInputOutputSerde[] propertySerdes, boolean unique, EventPropertyValueGetter[] propertyGetters, boolean isFireAndForget, StateMgmtSetting stateMgmtSettings) {
        this.streamNum = streamNum;
        this.propertyNames = propertyNames;
        this.propertyTypes = propertyTypes;
        this.propertySerdes = propertySerdes;
        this.unique = unique;
        this.propertyGetters = propertyGetters;
        this.isFireAndForget = isFireAndForget;
        this.stateMgmtSettings = stateMgmtSettings;
    }

    public EventTableFactory create(EventType eventType, EventTableFactoryFactoryContext eventTableFactoryContext) {
        return eventTableFactoryContext.getEventTableIndexService().createInArray(streamNum, eventType, propertyNames, propertyTypes, propertySerdes, unique, propertyGetters, isFireAndForget, stateMgmtSettings);
    }
}
