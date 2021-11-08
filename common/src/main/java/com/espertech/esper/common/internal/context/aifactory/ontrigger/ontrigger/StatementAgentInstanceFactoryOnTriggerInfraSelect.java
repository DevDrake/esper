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
package com.espertech.esper.common.internal.context.aifactory.ontrigger.ontrigger;

import com.espertech.esper.common.client.EventPropertyValueGetter;
import com.espertech.esper.common.client.EventType;
import com.espertech.esper.common.client.type.EPTypeClass;
import com.espertech.esper.common.internal.context.util.StatementAgentInstanceLock;
import com.espertech.esper.common.internal.context.util.StatementContext;
import com.espertech.esper.common.internal.epl.expression.core.ExprEvaluator;
import com.espertech.esper.common.internal.epl.namedwindow.core.NamedWindow;
import com.espertech.esper.common.internal.epl.ontrigger.InfraOnExprBaseViewFactory;
import com.espertech.esper.common.internal.epl.ontrigger.InfraOnSelectViewFactory;
import com.espertech.esper.common.internal.epl.resultset.core.ResultSetProcessorFactoryProvider;
import com.espertech.esper.common.internal.epl.table.core.Table;

public class StatementAgentInstanceFactoryOnTriggerInfraSelect extends StatementAgentInstanceFactoryOnTriggerInfraBase {
    public final static EPTypeClass EPTYPE = new EPTypeClass(StatementAgentInstanceFactoryOnTriggerInfraSelect.class);

    private ResultSetProcessorFactoryProvider resultSetProcessorFactoryProvider;
    private boolean insertInto;
    private boolean addToFront;
    private Table optionalInsertIntoTable;
    private boolean selectAndDelete;
    private boolean isDistinct;
    private EventPropertyValueGetter distinctKeyGetter;
    private ExprEvaluator eventPrecedence;

    public void setResultSetProcessorFactoryProvider(ResultSetProcessorFactoryProvider resultSetProcessorFactoryProvider) {
        this.resultSetProcessorFactoryProvider = resultSetProcessorFactoryProvider;
    }

    public void setInsertInto(boolean insertInto) {
        this.insertInto = insertInto;
    }

    public void setOptionalInsertIntoTable(Table optionalInsertIntoTable) {
        this.optionalInsertIntoTable = optionalInsertIntoTable;
    }

    public void setSelectAndDelete(boolean selectAndDelete) {
        this.selectAndDelete = selectAndDelete;
    }

    protected boolean isSelect() {
        return true;
    }

    public void setDistinct(boolean distinct) {
        isDistinct = distinct;
    }

    public void setDistinctKeyGetter(EventPropertyValueGetter distinctKeyGetter) {
        this.distinctKeyGetter = distinctKeyGetter;
    }

    public void setAddToFront(boolean addToFront) {
        this.addToFront = addToFront;
    }

    public void setEventPrecedence(ExprEvaluator eventPrecedence) {
        this.eventPrecedence = eventPrecedence;
    }

    protected InfraOnExprBaseViewFactory setupFactory(EventType infraEventType, NamedWindow namedWindow, Table table, StatementContext statementContext) {
        return new InfraOnSelectViewFactory(infraEventType, addToFront,
            isDistinct, distinctKeyGetter, selectAndDelete, null, optionalInsertIntoTable, insertInto, resultSetProcessorFactoryProvider, eventPrecedence);
    }

    public StatementAgentInstanceLock obtainAgentInstanceLock(StatementContext statementContext, int agentInstanceId) {
        return StatementAgentInstanceFactoryOnTriggerUtil.obtainAgentInstanceLock(this, statementContext, agentInstanceId);
    }
}
