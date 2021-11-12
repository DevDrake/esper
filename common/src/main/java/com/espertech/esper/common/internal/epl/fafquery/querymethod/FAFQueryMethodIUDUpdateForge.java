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
package com.espertech.esper.common.internal.epl.fafquery.querymethod;

import com.espertech.esper.common.client.EPException;
import com.espertech.esper.common.client.EventType;
import com.espertech.esper.common.client.type.EPTypeClass;
import com.espertech.esper.common.internal.bytecodemodel.base.CodegenClassScope;
import com.espertech.esper.common.internal.bytecodemodel.base.CodegenMethod;
import com.espertech.esper.common.internal.bytecodemodel.model.expression.CodegenExpressionRef;
import com.espertech.esper.common.internal.compile.stage1.Compilable;
import com.espertech.esper.common.internal.compile.stage1.spec.FireAndForgetSpecUpdate;
import com.espertech.esper.common.internal.compile.stage1.spec.OnTriggerSetAssignment;
import com.espertech.esper.common.internal.compile.stage2.StatementRawInfo;
import com.espertech.esper.common.internal.compile.stage2.StatementSpecCompiled;
import com.espertech.esper.common.internal.compile.stage3.StatementCompileTimeServices;
import com.espertech.esper.common.internal.context.aifactory.core.SAIFFInitializeSymbol;
import com.espertech.esper.common.internal.epl.expression.core.*;
import com.espertech.esper.common.internal.epl.fafquery.processor.FireAndForgetProcessorNamedWindowForge;
import com.espertech.esper.common.internal.epl.fafquery.processor.FireAndForgetProcessorTableForge;
import com.espertech.esper.common.internal.epl.streamtype.StreamTypeServiceImpl;
import com.espertech.esper.common.internal.epl.table.core.TableDeployTimeResolver;
import com.espertech.esper.common.internal.epl.updatehelper.EventBeanUpdateHelperForge;
import com.espertech.esper.common.internal.epl.updatehelper.EventBeanUpdateHelperForgeFactory;
import com.espertech.esper.common.internal.event.core.EventTypeSPI;

import static com.espertech.esper.common.internal.bytecodemodel.model.expression.CodegenExpressionBuilder.constantNull;

/**
 * Starts and provides the stop method for EPL statements.
 */
public class FAFQueryMethodIUDUpdateForge extends FAFQueryMethodIUDBaseForge {
    public static final String INITIAL_VALUE_STREAM_NAME = "initial";

    private EventBeanUpdateHelperForge updateHelper;

    public FAFQueryMethodIUDUpdateForge(StatementSpecCompiled spec, Compilable compilable, StatementRawInfo statementRawInfo, StatementCompileTimeServices services) throws ExprValidationException {
        super(spec, compilable, statementRawInfo, services);
    }

    protected void initExec(String aliasName, StatementSpecCompiled spec, StatementRawInfo statementRawInfo, StatementCompileTimeServices services) throws ExprValidationException {
        StreamTypeServiceImpl assignmentTypeService = new StreamTypeServiceImpl(
            new EventType[]{processor.getEventTypeRSPInputEvents(), null, processor.getEventTypeRSPInputEvents()},
            new String[]{aliasName, "", INITIAL_VALUE_STREAM_NAME},
            new boolean[]{true, true, true}, true, false);
        assignmentTypeService.setStreamZeroUnambigous(true);
        ExprValidationContext validationContext = new ExprValidationContextBuilder(assignmentTypeService, statementRawInfo, services)
            .withAllowBindingConsumption(true).build();

        // validate update expressions
        FireAndForgetSpecUpdate updateSpec = (FireAndForgetSpecUpdate) spec.getRaw().getFireAndForgetSpec();
        try {
            for (OnTriggerSetAssignment assignment : updateSpec.getAssignments()) {
                ExprNodeUtilityValidate.validateAssignment(false, ExprNodeOrigin.UPDATEASSIGN, assignment, validationContext);
            }
        } catch (ExprValidationException e) {
            throw new EPException(e.getMessage(), e);
        }

        // make updater
        try {
            boolean copyOnWrite = processor instanceof FireAndForgetProcessorNamedWindowForge;
            updateHelper = EventBeanUpdateHelperForgeFactory.make(processor.getProcessorName(),
                (EventTypeSPI) processor.getEventTypeRSPInputEvents(), updateSpec.getAssignments(), aliasName, null, copyOnWrite,
                statementRawInfo.getStatementName(), services.getEventTypeAvroHandler());
        } catch (ExprValidationException e) {
            throw new EPException(e.getMessage(), e);
        }
    }

    protected EPTypeClass typeOfMethod() {
        return FAFQueryMethodIUDUpdate.EPTYPE;
    }

    protected void makeInlineSpecificSetter(CodegenExpressionRef queryMethod, CodegenMethod method, SAIFFInitializeSymbol symbols, CodegenClassScope classScope) {
        method.getBlock().exprDotMethod(queryMethod, "setOptionalWhereClause", whereClause == null ? constantNull() : ExprNodeUtilityCodegen.codegenEvaluator(whereClause.getForge(), method, this.getClass(), classScope));
        if (processor instanceof FireAndForgetProcessorNamedWindowForge) {
            method.getBlock().exprDotMethod(queryMethod, "setUpdateHelperNamedWindow", updateHelper.makeWCopy(method, classScope));
        } else {
            FireAndForgetProcessorTableForge table = (FireAndForgetProcessorTableForge) processor;
            method.getBlock()
                .exprDotMethod(queryMethod, "setUpdateHelperTable", updateHelper.makeNoCopy(method, classScope))
                .exprDotMethod(queryMethod, "setTable", TableDeployTimeResolver.makeResolveTable(table.getTable(), symbols.getAddInitSvc(method)));
        }
    }
}
