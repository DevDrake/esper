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
package com.espertech.esper.common.internal.epl.enummethod.eval.singlelambdaopt3form.average;

import com.espertech.esper.common.client.EventBean;
import com.espertech.esper.common.client.type.EPTypeClassParameterized;
import com.espertech.esper.common.client.type.EPTypePremade;
import com.espertech.esper.common.internal.bytecodemodel.base.CodegenClassScope;
import com.espertech.esper.common.internal.bytecodemodel.base.CodegenMethod;
import com.espertech.esper.common.internal.bytecodemodel.base.CodegenMethodScope;
import com.espertech.esper.common.internal.bytecodemodel.model.expression.CodegenExpression;
import com.espertech.esper.common.internal.epl.enummethod.codegen.EnumForgeCodegenNames;
import com.espertech.esper.common.internal.epl.enummethod.codegen.EnumForgeCodegenParams;
import com.espertech.esper.common.internal.epl.enummethod.eval.EnumEval;
import com.espertech.esper.common.internal.epl.enummethod.eval.EnumForgeBasePlain;
import com.espertech.esper.common.internal.epl.expression.core.ExprEvaluatorContext;

import java.util.Collection;

import static com.espertech.esper.common.internal.bytecodemodel.model.expression.CodegenExpressionBuilder.*;

public class EnumAverageScalarNoParam extends EnumForgeBasePlain implements EnumEval {

    public EnumAverageScalarNoParam(int streamCountIncoming) {
        super(streamCountIncoming);
    }

    public EnumEval getEnumEvaluator() {
        return this;
    }

    public Object evaluateEnumMethod(EventBean[] eventsLambda, Collection enumcoll, boolean isNewData, ExprEvaluatorContext context) {
        double sum = 0d;
        int count = 0;

        for (Object next : enumcoll) {

            Number num = (Number) next;
            if (num == null) {
                continue;
            }
            count++;
            sum += num.doubleValue();
        }

        if (count == 0) {
            return null;
        }
        return sum / count;
    }

    public CodegenExpression codegen(EnumForgeCodegenParams args, CodegenMethodScope codegenMethodScope, CodegenClassScope codegenClassScope) {
        CodegenMethod method = codegenMethodScope.makeChild(EPTypePremade.DOUBLEBOXED.getEPType(), EnumAverageScalarNoParam.class, codegenClassScope).addParam(EnumForgeCodegenNames.PARAMSCOLLOBJ).getBlock()
                .declareVar(EPTypePremade.DOUBLEPRIMITIVE.getEPType(), "sum", constant(0d))
                .declareVar(EPTypePremade.INTEGERPRIMITIVE.getEPType(), "count", constant(0))
                .declareVar(new EPTypeClassParameterized(Collection.class, EPTypePremade.NUMBER.getEPType()), "coll", EnumForgeCodegenNames.REF_ENUMCOLL)
                .forEach(EPTypePremade.NUMBER.getEPType(), "num", ref("coll"))
                .ifRefNull("num").blockContinue()
                .incrementRef("count")
                .assignRef("sum", op(ref("sum"), "+", exprDotMethod(ref("num"), "doubleValue")))
                .blockEnd()
                .ifCondition(equalsIdentity(ref("count"), constant(0))).blockReturn(constantNull())
                .methodReturn(op(ref("sum"), "/", ref("count")));
        return localMethod(method, args.getExpressions());
    }

}
