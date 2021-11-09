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
package com.espertech.esper.common.internal.epl.enummethod.eval.plugin;

import com.espertech.esper.common.client.EventBean;
import com.espertech.esper.common.client.EventType;
import com.espertech.esper.common.client.hook.enummethod.*;
import com.espertech.esper.common.client.type.EPType;
import com.espertech.esper.common.client.type.EPTypeClass;
import com.espertech.esper.common.client.type.EPTypeNull;
import com.espertech.esper.common.client.type.EPTypePremade;
import com.espertech.esper.common.internal.bytecodemodel.base.CodegenBlock;
import com.espertech.esper.common.internal.bytecodemodel.base.CodegenClassScope;
import com.espertech.esper.common.internal.bytecodemodel.base.CodegenMethod;
import com.espertech.esper.common.internal.bytecodemodel.base.CodegenMethodScope;
import com.espertech.esper.common.internal.bytecodemodel.model.expression.CodegenExpression;
import com.espertech.esper.common.internal.bytecodemodel.model.expression.CodegenExpressionField;
import com.espertech.esper.common.internal.context.module.EPStatementInitServices;
import com.espertech.esper.common.internal.epl.enummethod.codegen.EnumForgeCodegenNames;
import com.espertech.esper.common.internal.epl.enummethod.codegen.EnumForgeCodegenParams;
import com.espertech.esper.common.internal.epl.enummethod.dot.ExprDotEvalParam;
import com.espertech.esper.common.internal.epl.enummethod.dot.ExprDotEvalParamExpr;
import com.espertech.esper.common.internal.epl.enummethod.dot.ExprDotEvalParamLambda;
import com.espertech.esper.common.internal.epl.enummethod.eval.EnumEval;
import com.espertech.esper.common.internal.epl.enummethod.eval.EnumForge;
import com.espertech.esper.common.internal.epl.expression.codegen.ExprForgeCodegenSymbol;
import com.espertech.esper.common.internal.epl.expression.core.ExprForge;
import com.espertech.esper.common.internal.event.arr.ObjectArrayEventBean;
import com.espertech.esper.common.internal.event.arr.ObjectArrayEventType;
import com.espertech.esper.common.internal.event.core.EventTypeUtility;

import java.util.ArrayList;
import java.util.List;

import static com.espertech.esper.common.internal.bytecodemodel.model.expression.CodegenExpressionBuilder.*;
import static com.espertech.esper.common.internal.epl.enummethod.codegen.EnumForgeCodegenNames.REF_ENUMCOLL;

public class EnumForgePlugin implements EnumForge {
    private final List<ExprDotEvalParam> bodiesAndParameters;
    private final EnumMethodModeStaticMethod mode;
    private final EPTypeClass expectedStateReturnType;
    private final int numStreamsIncoming;
    private final EventType inputEventType;

    public EnumForgePlugin(List<ExprDotEvalParam> bodiesAndParameters, EnumMethodModeStaticMethod mode, EPTypeClass expectedStateReturnType, int numStreamsIncoming, EventType inputEventType) {
        this.bodiesAndParameters = bodiesAndParameters;
        this.mode = mode;
        this.expectedStateReturnType = expectedStateReturnType;
        this.numStreamsIncoming = numStreamsIncoming;
        this.inputEventType = inputEventType;
    }

    public EnumEval getEnumEvaluator() {
        throw new UnsupportedOperationException("Enum-evaluator not available at compile-time");
    }

    public int getStreamNumSize() {
        int countLambda = 0;
        for (ExprDotEvalParam param : bodiesAndParameters) {
            if (param instanceof ExprDotEvalParamLambda) {
                ExprDotEvalParamLambda lambda = (ExprDotEvalParamLambda) param;
                countLambda += lambda.getGoesToNames().size();
            }
        }
        return numStreamsIncoming + countLambda;
    }

    public CodegenExpression codegen(EnumForgeCodegenParams args, CodegenMethodScope codegenMethodScope, CodegenClassScope codegenClassScope) {

        ExprForgeCodegenSymbol scope = new ExprForgeCodegenSymbol(false, null);
        CodegenMethod methodNode = codegenMethodScope.makeChildWithScope(expectedStateReturnType, EnumForgePlugin.class, scope, codegenClassScope).addParam(EnumForgeCodegenNames.PARAMSCOLLOBJ);
        methodNode.getBlock().declareVarNewInstance(mode.getStateClass(), "state");

        // call set-parameter for each non-lambda expression
        int indexNonLambda = 0;
        for (ExprDotEvalParam param : bodiesAndParameters) {
            if (param instanceof ExprDotEvalParamExpr) {
                CodegenExpression expression = param.getBodyForge().evaluateCodegen(EPTypePremade.OBJECT.getEPType(), methodNode, scope, codegenClassScope);
                methodNode.getBlock().exprDotMethod(ref("state"), "setParameter", constant(indexNonLambda), expression);
                indexNonLambda++;
            }
        }

        // allocate event type and field for each lambda expression
        int indexParameter = 0;
        for (ExprDotEvalParam param : bodiesAndParameters) {
            if (param instanceof ExprDotEvalParamLambda) {
                ExprDotEvalParamLambda lambda = (ExprDotEvalParamLambda) param;
                for (int i = 0; i < lambda.getLambdaDesc().getTypes().length; i++) {
                    EventType eventType = lambda.getLambdaDesc().getTypes()[i];
                    EnumMethodLambdaParameterType lambdaParameterType = mode.getLambdaParameters().apply(new EnumMethodLambdaParameterDescriptor(indexParameter, i));

                    if (eventType != inputEventType) {
                        CodegenExpressionField type = codegenClassScope.addFieldUnshared(true, ObjectArrayEventType.EPTYPE, cast(ObjectArrayEventType.EPTYPE, EventTypeUtility.resolveTypeCodegen(lambda.getLambdaDesc().getTypes()[i], EPStatementInitServices.REF)));
                        String eventName = getNameExt("resultEvent", indexParameter, i);
                        String propName = getNameExt("props", indexParameter, i);
                        methodNode.getBlock()
                            .declareVar(ObjectArrayEventBean.EPTYPE, eventName, newInstance(ObjectArrayEventBean.EPTYPE, newArrayByLength(EPTypePremade.OBJECT.getEPType(), constant(1)), type))
                            .assignArrayElement(EnumForgeCodegenNames.REF_EPS, constant(lambda.getStreamCountIncoming() + i), ref(eventName))
                            .declareVar(EPTypePremade.OBJECTARRAY.getEPType(), propName, exprDotMethod(ref(eventName), "getProperties"));

                        // initialize index-type lambda-parameters to zer0
                        if (lambdaParameterType instanceof EnumMethodLambdaParameterTypeIndex) {
                            methodNode.getBlock()
                                .assignArrayElement(propName, constant(0), constant(0));
                        }
                        if (lambdaParameterType instanceof EnumMethodLambdaParameterTypeSize) {
                            methodNode.getBlock()
                                .assignArrayElement(propName, constant(0), exprDotMethod(REF_ENUMCOLL, "size"));
                        }
                        if (lambdaParameterType instanceof EnumMethodLambdaParameterTypeStateGetter) {
                            EnumMethodLambdaParameterTypeStateGetter getter = (EnumMethodLambdaParameterTypeStateGetter) lambdaParameterType;
                            methodNode.getBlock()
                                .assignArrayElement(propName, constant(0), exprDotMethod(ref("state"), getter.getGetterMethodName()));
                        }
                    }
                }
            }
            indexParameter++;
        }

        EPTypeClass elementType = inputEventType == null ? EPTypePremade.OBJECT.getEPType() : EventBean.EPTYPE;
        methodNode.getBlock().declareVar(EPTypePremade.INTEGERPRIMITIVE.getEPType(), "count", constant(-1));
        CodegenBlock forEach = methodNode.getBlock().forEach(elementType, "next", EnumForgeCodegenNames.REF_ENUMCOLL);
        {
            forEach.incrementRef("count");

            List<CodegenExpression> paramsNext = new ArrayList<>();
            paramsNext.add(ref("state"));
            paramsNext.add(ref("next"));

            indexParameter = 0;
            for (ExprDotEvalParam param : bodiesAndParameters) {
                if (param instanceof ExprDotEvalParamLambda) {
                    ExprDotEvalParamLambda lambda = (ExprDotEvalParamLambda) param;
                    String valueName = "value_" + indexParameter;
                    for (int i = 0; i < lambda.getLambdaDesc().getTypes().length; i++) {
                        EnumMethodLambdaParameterType lambdaParameterType = mode.getLambdaParameters().apply(new EnumMethodLambdaParameterDescriptor(indexParameter, i));

                        String propName = getNameExt("props", indexParameter, i);
                        if (lambdaParameterType instanceof EnumMethodLambdaParameterTypeValue) {
                            EventType eventType = lambda.getLambdaDesc().getTypes()[i];
                            if (eventType == inputEventType) {
                                forEach.assignArrayElement(EnumForgeCodegenNames.REF_EPS, constant(lambda.getStreamCountIncoming() + i), ref("next"));
                            } else {
                                forEach.assignArrayElement(propName, constant(0), ref("next"));
                            }
                        } else if (lambdaParameterType instanceof EnumMethodLambdaParameterTypeIndex) {
                            forEach.assignArrayElement(propName, constant(0), ref("count"));
                        } else if (lambdaParameterType instanceof EnumMethodLambdaParameterTypeStateGetter) {
                            EnumMethodLambdaParameterTypeStateGetter getter = (EnumMethodLambdaParameterTypeStateGetter) lambdaParameterType;
                            forEach.assignArrayElement(propName, constant(0), exprDotMethod(ref("state"), getter.getGetterMethodName()));
                        } else if (lambdaParameterType instanceof EnumMethodLambdaParameterTypeSize) {
                            // no action needed
                        } else {
                            throw new UnsupportedOperationException("Unrecognized lambda parameter type " + lambdaParameterType);
                        }
                    }

                    ExprForge forge = lambda.getBodyForge();
                    EPType evalType = forge.getEvaluationType();
                    if (evalType == null || evalType == EPTypeNull.INSTANCE) {
                        forEach.declareVar(EPTypePremade.OBJECT.getEPType(), valueName, constantNull());
                    } else {
                        forEach.declareVar((EPTypeClass) evalType, valueName, forge.evaluateCodegen((EPTypeClass) evalType, methodNode, scope, codegenClassScope));
                    }
                    paramsNext.add(ref(valueName));
                }
                indexParameter++;
            }

            forEach.expression(staticMethod(mode.getServiceClass(), mode.getMethodName(), paramsNext.toArray(new CodegenExpression[0])));

            if (mode.isEarlyExit()) {
                forEach.ifCondition(exprDotMethod(ref("state"), "completed")).breakLoop();
            }
        }

        methodNode.getBlock().methodReturn(cast(expectedStateReturnType, exprDotMethod(ref("state"), "state")));
        return localMethod(methodNode, args.getEps(), args.getEnumcoll(), args.getIsNewData(), args.getExprCtx());
    }

    private String getNameExt(String prefix, int indexLambda, int number) {
        return prefix + "_" + indexLambda + "_" + number;
    }
}
