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
package com.espertech.esper.common.internal.context.aifactory.ontrigger.onsplit;

import com.espertech.esper.common.client.type.EPTypeClass;
import com.espertech.esper.common.internal.epl.expression.core.ExprEvaluator;
import com.espertech.esper.common.internal.epl.resultset.core.ResultSetProcessorFactoryProvider;
import com.espertech.esper.common.internal.epl.table.core.Table;
import com.espertech.esper.common.internal.filterspec.PropertyEvaluator;

public class OnSplitItemEval {
    public final static EPTypeClass EPTYPE = new EPTypeClass(OnSplitItemEval.class);

    private ExprEvaluator whereClause;
    private boolean isNamedWindowInsert;
    private Table insertIntoTable;
    private ResultSetProcessorFactoryProvider rspFactoryProvider;
    private PropertyEvaluator propertyEvaluator;
    private ExprEvaluator eventPrecedence;

    public ExprEvaluator getWhereClause() {
        return whereClause;
    }

    public void setWhereClause(ExprEvaluator whereClause) {
        this.whereClause = whereClause;
    }

    public boolean isNamedWindowInsert() {
        return isNamedWindowInsert;
    }

    public void setNamedWindowInsert(boolean namedWindowInsert) {
        isNamedWindowInsert = namedWindowInsert;
    }

    public Table getInsertIntoTable() {
        return insertIntoTable;
    }

    public void setInsertIntoTable(Table insertIntoTable) {
        this.insertIntoTable = insertIntoTable;
    }

    public ResultSetProcessorFactoryProvider getRspFactoryProvider() {
        return rspFactoryProvider;
    }

    public void setRspFactoryProvider(ResultSetProcessorFactoryProvider rspFactoryProvider) {
        this.rspFactoryProvider = rspFactoryProvider;
    }

    public PropertyEvaluator getPropertyEvaluator() {
        return propertyEvaluator;
    }

    public void setPropertyEvaluator(PropertyEvaluator propertyEvaluator) {
        this.propertyEvaluator = propertyEvaluator;
    }

    public ExprEvaluator getEventPrecedence() {
        return eventPrecedence;
    }

    public void setEventPrecedence(ExprEvaluator eventPrecedence) {
        this.eventPrecedence = eventPrecedence;
    }
}
