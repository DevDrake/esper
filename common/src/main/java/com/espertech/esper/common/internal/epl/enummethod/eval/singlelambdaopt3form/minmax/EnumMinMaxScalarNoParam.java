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
package com.espertech.esper.common.internal.epl.enummethod.eval.singlelambdaopt3form.minmax;

import com.espertech.esper.common.client.EventBean;
import com.espertech.esper.common.client.type.EPTypeClass;
import com.espertech.esper.common.client.type.EPTypeClassParameterized;
import com.espertech.esper.common.internal.bytecodemodel.base.CodegenBlock;
import com.espertech.esper.common.internal.bytecodemodel.base.CodegenClassScope;
import com.espertech.esper.common.internal.bytecodemodel.base.CodegenMethod;
import com.espertech.esper.common.internal.bytecodemodel.base.CodegenMethodScope;
import com.espertech.esper.common.internal.bytecodemodel.model.expression.CodegenExpression;
import com.espertech.esper.common.internal.epl.enummethod.codegen.EnumForgeCodegenNames;
import com.espertech.esper.common.internal.epl.enummethod.codegen.EnumForgeCodegenParams;
import com.espertech.esper.common.internal.epl.enummethod.eval.EnumEval;
import com.espertech.esper.common.internal.epl.enummethod.eval.EnumForge;
import com.espertech.esper.common.internal.epl.enummethod.eval.EnumForgeBasePlain;
import com.espertech.esper.common.internal.epl.expression.core.ExprEvaluatorContext;
import com.espertech.esper.common.internal.rettype.EPChainableType;
import com.espertech.esper.common.internal.rettype.EPChainableTypeHelper;
import com.espertech.esper.common.internal.util.JavaClassHelper;

import java.util.Collection;

import static com.espertech.esper.common.internal.bytecodemodel.model.expression.CodegenExpressionBuilder.*;
import static com.espertech.esper.common.internal.bytecodemodel.model.expression.CodegenExpressionRelational.CodegenRelational.GT;
import static com.espertech.esper.common.internal.bytecodemodel.model.expression.CodegenExpressionRelational.CodegenRelational.LT;

public class EnumMinMaxScalarNoParam extends EnumForgeBasePlain implements EnumForge, EnumEval {

    private final boolean max;
    private final EPChainableType resultType;

    public EnumMinMaxScalarNoParam(int streamCountIncoming, boolean max, EPChainableType resultType) {
        super(streamCountIncoming);
        this.max = max;
        this.resultType = resultType;
    }

    public EnumEval getEnumEvaluator() {
        return this;
    }

    public Object evaluateEnumMethod(EventBean[] eventsLambda, Collection enumcoll, boolean isNewData, ExprEvaluatorContext context) {
        Comparable minKey = null;

        for (Object next : enumcoll) {

            Object comparable = next;
            if (comparable == null) {
                continue;
            }

            if (minKey == null) {
                minKey = (Comparable) comparable;
            } else {
                if (max) {
                    if (minKey.compareTo(comparable) < 0) {
                        minKey = (Comparable) comparable;
                    }
                } else {
                    if (minKey.compareTo(comparable) > 0) {
                        minKey = (Comparable) comparable;
                    }
                }
            }
        }

        return minKey;
    }

    public CodegenExpression codegen(EnumForgeCodegenParams args, CodegenMethodScope codegenMethodScope, CodegenClassScope codegenClassScope) {
        EPTypeClass innerTypeBoxed = JavaClassHelper.getBoxedType(EPChainableTypeHelper.getCodegenReturnType(resultType));

        CodegenBlock block = codegenMethodScope.makeChild(innerTypeBoxed, EnumMinMaxScalarNoParam.class, codegenClassScope).addParam(EnumForgeCodegenNames.PARAMSCOLLOBJ).getBlock()
                .declareVar(innerTypeBoxed, "minKey", constantNull())
                .declareVar(new EPTypeClassParameterized(Collection.class, innerTypeBoxed), "coll", EnumForgeCodegenNames.REF_ENUMCOLL);

        CodegenBlock forEach = block.forEach(innerTypeBoxed, "value", ref("coll"))
                .ifRefNull("value").blockContinue();

        forEach.ifCondition(equalsNull(ref("minKey")))
                .assignRef("minKey", ref("value"))
                .ifElse()
                .ifCondition(relational(exprDotMethod(ref("minKey"), "compareTo", ref("value")), max ? LT : GT, constant(0)))
                .assignRef("minKey", ref("value"));

        CodegenMethod method = block.methodReturn(ref("minKey"));
        return localMethod(method, args.getExpressions());
    }
}
