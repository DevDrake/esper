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
package com.espertech.esper.common.internal.epl.expression.subquery;

import com.espertech.esper.common.client.type.EPTypeClass;
import com.espertech.esper.common.internal.bytecodemodel.base.CodegenMethodScope;
import com.espertech.esper.common.internal.bytecodemodel.model.expression.CodegenExpressionRef;
import com.espertech.esper.common.internal.epl.expression.codegen.ExprForgeCodegenSymbol;

import java.util.Map;

import static com.espertech.esper.common.internal.bytecodemodel.model.expression.CodegenExpressionBuilder.ref;
import static com.espertech.esper.common.internal.epl.util.EPTypeCollectionConst.EPTYPE_COLLECTION_EVENTBEAN;

public class ExprSubselectEvalMatchSymbol extends ExprForgeCodegenSymbol {

    public final static String NAME_MATCHINGEVENTS = "matchingEvents";

    public final static CodegenExpressionRef REF_MATCHINGEVENTS = ref(NAME_MATCHINGEVENTS);

    private CodegenExpressionRef optionalMatchingEventRef;

    public ExprSubselectEvalMatchSymbol() {
        super(false, null);
    }

    public CodegenExpressionRef getAddMatchingEvents(CodegenMethodScope scope) {
        if (optionalMatchingEventRef == null) {
            optionalMatchingEventRef = REF_MATCHINGEVENTS;
        }
        scope.addSymbol(optionalMatchingEventRef);
        return optionalMatchingEventRef;
    }

    @Override
    public void provide(Map<String, EPTypeClass> symbols) {
        if (optionalMatchingEventRef != null) {
            symbols.put(optionalMatchingEventRef.getRef(), EPTYPE_COLLECTION_EVENTBEAN);
        }
        super.provide(symbols);
    }
}
