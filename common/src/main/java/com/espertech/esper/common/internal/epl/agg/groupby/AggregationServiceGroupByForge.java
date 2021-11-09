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
package com.espertech.esper.common.internal.epl.agg.groupby;

import com.espertech.esper.common.client.annotation.AppliesTo;
import com.espertech.esper.common.client.serde.DataInputOutputSerde;
import com.espertech.esper.common.client.type.EPTypePremade;
import com.espertech.esper.common.client.util.StateMgmtSetting;
import com.espertech.esper.common.internal.bytecodemodel.base.CodegenBlock;
import com.espertech.esper.common.internal.bytecodemodel.base.CodegenClassScope;
import com.espertech.esper.common.internal.bytecodemodel.base.CodegenMethod;
import com.espertech.esper.common.internal.bytecodemodel.core.CodegenCtor;
import com.espertech.esper.common.internal.bytecodemodel.core.CodegenNamedMethods;
import com.espertech.esper.common.internal.bytecodemodel.core.CodegenTypedParam;
import com.espertech.esper.common.internal.bytecodemodel.model.expression.CodegenExpression;
import com.espertech.esper.common.internal.bytecodemodel.model.expression.CodegenExpressionBuilder;
import com.espertech.esper.common.internal.bytecodemodel.model.expression.CodegenExpressionField;
import com.espertech.esper.common.internal.bytecodemodel.model.expression.CodegenExpressionMember;
import com.espertech.esper.common.internal.context.module.EPStatementInitServices;
import com.espertech.esper.common.internal.epl.agg.core.*;
import com.espertech.esper.common.internal.epl.expression.core.ExprEvaluatorContext;
import com.espertech.esper.common.internal.epl.expression.time.abacus.TimeAbacus;
import com.espertech.esper.common.internal.epl.expression.time.abacus.TimeAbacusField;
import com.espertech.esper.common.internal.fabric.FabricTypeCollector;

import java.util.List;

import static com.espertech.esper.common.internal.bytecodemodel.model.expression.CodegenExpressionBuilder.*;
import static com.espertech.esper.common.internal.bytecodemodel.model.expression.CodegenExpressionRelational.CodegenRelational.LE;
import static com.espertech.esper.common.internal.context.module.EPStatementInitServices.GETAGGREGATIONSERVICEFACTORYSERVICE;
import static com.espertech.esper.common.internal.epl.agg.core.AggregationServiceCodegenNames.*;
import static com.espertech.esper.common.internal.epl.expression.codegen.ExprForgeCodegenNames.*;
import static com.espertech.esper.common.internal.epl.resultset.codegen.ResultSetProcessorCodegenNames.MEMBER_EXPREVALCONTEXT;
import static com.espertech.esper.common.internal.epl.util.EPTypeCollectionConst.EPTYPE_MAP_OBJECT_AGGROW;
import static com.espertech.esper.common.internal.metrics.instrumentation.InstrumentationCode.instblock;

/**
 * Implementation for handling aggregation with grouping by group-keys.
 */
public class AggregationServiceGroupByForge implements AggregationServiceFactoryForgeWMethodGen {
    private final static CodegenExpressionMember MEMBER_CURRENTROW = member("currentRow");
    private final static CodegenExpressionMember MEMBER_CURRENTGROUPKEY = member("currentGroupKey");
    final static CodegenExpressionMember MEMBER_AGGREGATORSPERGROUP = member("aggregatorsPerGroup");
    private final static CodegenExpressionMember MEMBER_REMOVEDKEYS = member("removedKeys");

    protected final AggGroupByDesc aggGroupByDesc;
    protected final TimeAbacus timeAbacus;
    private StateMgmtSetting stateMgmtSetting;

    protected CodegenExpression reclaimAge;
    protected CodegenExpression reclaimFreq;

    public AggregationServiceGroupByForge(AggGroupByDesc aggGroupByDesc, TimeAbacus timeAbacus) {
        this.aggGroupByDesc = aggGroupByDesc;
        this.timeAbacus = timeAbacus;
    }

    public AppliesTo appliesTo() {
        return AppliesTo.AGGREGATION_GROUPBY;
    }

    public void setStateMgmtSetting(StateMgmtSetting stateMgmtSetting) {
        this.stateMgmtSetting = stateMgmtSetting;
    }

    public void appendRowFabricType(FabricTypeCollector fabricTypeCollector) {
        AggregationServiceCodegenUtil.appendIncidentals(hasRefCounting(), aggGroupByDesc.isReclaimAged(), fabricTypeCollector);
    }

    public void providerCodegen(CodegenMethod method, CodegenClassScope classScope, AggregationClassNames classNames) {
        if (aggGroupByDesc.isReclaimAged()) {
            reclaimAge = aggGroupByDesc.getReclaimEvaluationFunctionMaxAge().make(classScope);
            reclaimFreq = aggGroupByDesc.getReclaimEvaluationFunctionFrequency().make(classScope);
        } else {
            reclaimAge = constantNull();
            reclaimFreq = constantNull();
        }

        CodegenExpressionField timeAbacus = classScope.addOrGetFieldSharable(TimeAbacusField.INSTANCE);

        method.getBlock()
                .declareVar(AggregationRowFactory.EPTYPE, "rowFactory", CodegenExpressionBuilder.newInstance(classNames.getRowFactoryTop(), ref("this")))
                .declareVar(DataInputOutputSerde.EPTYPE, "rowSerde", CodegenExpressionBuilder.newInstance(classNames.getRowSerdeTop(), ref("this")))
                .declareVar(AggregationServiceFactory.EPTYPE, "svcFactory", CodegenExpressionBuilder.newInstance(classNames.getServiceFactory(), ref("this")))
                .declareVar(DataInputOutputSerde.EPTYPE, "serde", aggGroupByDesc.getGroupByMultiKey().getExprMKSerde(method, classScope))
                .methodReturn(exprDotMethodChain(EPStatementInitServices.REF).add(GETAGGREGATIONSERVICEFACTORYSERVICE).add(
                        "groupBy", ref("svcFactory"), ref("rowFactory"), aggGroupByDesc.getRowStateForgeDescs().getUseFlags().toExpression(),
                        ref("rowSerde"), reclaimAge, reclaimFreq, timeAbacus, ref("serde"), stateMgmtSetting.toExpression()));
    }

    public void rowCtorCodegen(AggregationRowCtorDesc rowCtorDesc) {
        AggregationServiceCodegenUtil.generateIncidentals(hasRefCounting(), aggGroupByDesc.isReclaimAged(), rowCtorDesc);
    }

    public void rowWriteMethodCodegen(CodegenMethod method, int level) {
        if (hasRefCounting()) {
            method.getBlock().exprDotMethod(ref("output"), "writeInt", ref("row.refcount"));
        }
        if (aggGroupByDesc.isReclaimAged()) {
            method.getBlock().exprDotMethod(ref("output"), "writeLong", ref("row.lastUpd"));
        }
    }

    public void rowReadMethodCodegen(CodegenMethod method, int level) {
        if (hasRefCounting()) {
            method.getBlock().assignRef("row.refcount", exprDotMethod(ref("input"), "readInt"));
        }
        if (aggGroupByDesc.isReclaimAged()) {
            method.getBlock().assignRef("row.lastUpd", exprDotMethod(ref("input"), "readLong"));
        }
    }

    public void makeServiceCodegen(CodegenMethod method, CodegenClassScope classScope, AggregationClassNames classNames) {
        method.getBlock().methodReturn(CodegenExpressionBuilder.newInstance(classNames.getService(), ref("o"), MEMBER_EXPREVALCONTEXT));
    }

    public void ctorCodegen(CodegenCtor ctor, List<CodegenTypedParam> explicitMembers, CodegenClassScope classScope, AggregationClassNames classNames) {
        ctor.getCtorParams().add(new CodegenTypedParam(ExprEvaluatorContext.EPTYPE, NAME_EXPREVALCONTEXT));
        explicitMembers.add(new CodegenTypedParam(EPTYPE_MAP_OBJECT_AGGROW, MEMBER_AGGREGATORSPERGROUP.getRef()));
        explicitMembers.add(new CodegenTypedParam(EPTypePremade.OBJECT.getEPType(), MEMBER_CURRENTGROUPKEY.getRef()).setFinal(false));
        explicitMembers.add(new CodegenTypedParam(classNames.getRowTop(), MEMBER_CURRENTROW.getRef()).setFinal(false));
        ctor.getBlock().assignRef(MEMBER_AGGREGATORSPERGROUP, newInstance(EPTypePremade.HASHMAP.getEPType()));
        if (aggGroupByDesc.isReclaimAged()) {
            AggSvcGroupByReclaimAgedImpl.ctorCodegenReclaim(ctor, explicitMembers, classScope, reclaimAge, reclaimFreq);
        }
        if (hasRefCounting()) {
            explicitMembers.add(new CodegenTypedParam(EPTypePremade.LIST.getEPType(), MEMBER_REMOVEDKEYS.getRef()));
            ctor.getBlock().assignRef(MEMBER_REMOVEDKEYS, newInstance(EPTypePremade.ARRAYLIST.getEPType(), constant(4)));
        }
    }

    public void getValueCodegen(CodegenMethod method, CodegenClassScope classScope, CodegenNamedMethods namedMethods) {
        method.getBlock().methodReturn(exprDotMethod(MEMBER_CURRENTROW, "getValue", REF_VCOL, REF_EPS, REF_ISNEWDATA, REF_EXPREVALCONTEXT));
    }

    public void getEventBeanCodegen(CodegenMethod method, CodegenClassScope classScope, CodegenNamedMethods namedMethods) {
        method.getBlock().methodReturn(exprDotMethod(MEMBER_CURRENTROW, "getEventBean", REF_VCOL, REF_EPS, REF_ISNEWDATA, REF_EXPREVALCONTEXT));
    }

    public void getCollectionScalarCodegen(CodegenMethod method, CodegenClassScope classScope, CodegenNamedMethods namedMethods) {
        method.getBlock().methodReturn(exprDotMethod(MEMBER_CURRENTROW, "getCollectionScalar", REF_VCOL, REF_EPS, REF_ISNEWDATA, REF_EXPREVALCONTEXT));
    }

    public void getCollectionOfEventsCodegen(CodegenMethod method, CodegenClassScope classScope, CodegenNamedMethods namedMethods) {
        method.getBlock().methodReturn(exprDotMethod(MEMBER_CURRENTROW, "getCollectionOfEvents", REF_VCOL, REF_EPS, REF_ISNEWDATA, REF_EXPREVALCONTEXT));
    }

    public void applyEnterCodegen(CodegenMethod method, CodegenClassScope classScope, CodegenNamedMethods namedMethods, AggregationClassNames classNames) {
        method.getBlock()
                .apply(instblock(classScope, "qAggregationGroupedApplyEnterLeave", constantTrue(), constant(aggGroupByDesc.getNumMethods()), constant(aggGroupByDesc.getNumAccess()), REF_GROUPKEY));

        if (aggGroupByDesc.isReclaimAged()) {
            AggSvcGroupByReclaimAgedImpl.applyEnterCodegenSweep(method, classScope, classNames);
        }

        if (hasRefCounting()) {
            method.getBlock().localMethod(handleRemovedKeysCodegen(method, classScope));
        }

        CodegenBlock block = method.getBlock().assignRef(MEMBER_CURRENTROW, cast(classNames.getRowTop(), exprDotMethod(MEMBER_AGGREGATORSPERGROUP, "get", REF_GROUPKEY)));
        block.ifCondition(equalsNull(MEMBER_CURRENTROW))
                .assignRef(MEMBER_CURRENTROW, CodegenExpressionBuilder.newInstance(classNames.getRowTop()))
                .exprDotMethod(MEMBER_AGGREGATORSPERGROUP, "put", REF_GROUPKEY, MEMBER_CURRENTROW);

        if (hasRefCounting()) {
            block.exprDotMethod(MEMBER_CURRENTROW, "increaseRefcount");
        }
        if (aggGroupByDesc.isReclaimAged()) {
            block.exprDotMethod(MEMBER_CURRENTROW, "setLastUpdateTime", ref("currentTime"));
        }

        block.exprDotMethod(MEMBER_CURRENTROW, "applyEnter", REF_EPS, REF_EXPREVALCONTEXT)
                .apply(instblock(classScope, "aAggregationGroupedApplyEnterLeave", constantTrue()));
    }

    public void applyLeaveCodegen(CodegenMethod method, CodegenClassScope classScope, CodegenNamedMethods namedMethods, AggregationClassNames classNames) {
        method.getBlock()
                .apply(instblock(classScope, "qAggregationGroupedApplyEnterLeave", constantFalse(), constant(aggGroupByDesc.getNumMethods()), constant(aggGroupByDesc.getNumAccess()), REF_GROUPKEY))
                .assignRef(MEMBER_CURRENTROW, cast(classNames.getRowTop(), exprDotMethod(MEMBER_AGGREGATORSPERGROUP, "get", REF_GROUPKEY)))
                .ifCondition(equalsNull(MEMBER_CURRENTROW))
                .assignRef(MEMBER_CURRENTROW, CodegenExpressionBuilder.newInstance(classNames.getRowTop()))
                .exprDotMethod(MEMBER_AGGREGATORSPERGROUP, "put", REF_GROUPKEY, MEMBER_CURRENTROW);

        if (hasRefCounting()) {
            method.getBlock().exprDotMethod(MEMBER_CURRENTROW, "decreaseRefcount");
        }
        if (aggGroupByDesc.isReclaimAged()) {
            method.getBlock().exprDotMethod(MEMBER_CURRENTROW, "setLastUpdateTime", exprDotMethodChain(REF_EXPREVALCONTEXT).add("getTimeProvider").add("getTime"));
        }
        method.getBlock().exprDotMethod(MEMBER_CURRENTROW, "applyLeave", REF_EPS, REF_EXPREVALCONTEXT);

        if (hasRefCounting()) {
            method.getBlock().ifCondition(relational(exprDotMethod(MEMBER_CURRENTROW, "getRefcount"), LE, constant(0)))
                    .exprDotMethod(MEMBER_REMOVEDKEYS, "add", REF_GROUPKEY);
        }

        method.getBlock().apply(instblock(classScope, "aAggregationGroupedApplyEnterLeave", constantFalse()));
    }

    public void stopMethodCodegen(AggregationServiceFactoryForgeWMethodGen forge, CodegenMethod method) {
        // no code
    }

    public void setRemovedCallbackCodegen(CodegenMethod method) {
        if (aggGroupByDesc.isReclaimAged()) {
            method.getBlock().assignRef("removedCallback", AggregationServiceCodegenNames.REF_CALLBACK);
        }
    }

    public void setCurrentAccessCodegen(CodegenMethod method, CodegenClassScope classScope, AggregationClassNames classNames) {
        method.getBlock().assignRef(MEMBER_CURRENTGROUPKEY, REF_GROUPKEY)
                .assignRef(MEMBER_CURRENTROW, cast(classNames.getRowTop(), exprDotMethod(MEMBER_AGGREGATORSPERGROUP, "get", REF_GROUPKEY)))
                .ifCondition(equalsNull(MEMBER_CURRENTROW))
                .assignRef(MEMBER_CURRENTROW, CodegenExpressionBuilder.newInstance(classNames.getRowTop()));
    }

    public void clearResultsCodegen(CodegenMethod method, CodegenClassScope classScope) {
        method.getBlock().exprDotMethod(MEMBER_AGGREGATORSPERGROUP, "clear");
    }

    public AggregationCodegenRowLevelDesc getRowLevelDesc() {
        return AggregationCodegenRowLevelDesc.fromTopOnly(aggGroupByDesc.getRowStateForgeDescs());
    }

    public void acceptCodegen(CodegenMethod method, CodegenClassScope classScope) {
        method.getBlock().exprDotMethod(REF_AGGVISITOR, "visitAggregations", exprDotMethod(MEMBER_AGGREGATORSPERGROUP, "size"), MEMBER_AGGREGATORSPERGROUP);
    }

    public void getGroupKeysCodegen(CodegenMethod method, CodegenClassScope classScope) {
        if (aggGroupByDesc.isRefcounted()) {
            method.getBlock().localMethod(handleRemovedKeysCodegen(method, classScope));
        }
        method.getBlock().methodReturn(exprDotMethod(MEMBER_AGGREGATORSPERGROUP, "keySet"));
    }

    public void getGroupKeyCodegen(CodegenMethod method, CodegenClassScope classScope) {
        method.getBlock().methodReturn(MEMBER_CURRENTGROUPKEY);
    }

    public void acceptGroupDetailCodegen(CodegenMethod method, CodegenClassScope classScope) {
        method.getBlock().exprDotMethod(REF_AGGVISITOR, "visitGrouped", exprDotMethod(MEMBER_AGGREGATORSPERGROUP, "size"))
                .forEach(EPTypePremade.MAPENTRY.getEPType(), "entry", exprDotMethod(MEMBER_AGGREGATORSPERGROUP, "entrySet"))
                .exprDotMethod(REF_AGGVISITOR, "visitGroup", exprDotMethod(ref("entry"), "getKey"), exprDotMethod(ref("entry"), "getValue"));
    }

    public void isGroupedCodegen(CodegenMethod method, CodegenClassScope classScope) {
        method.getBlock().methodReturn(constantTrue());
    }

    public void getRowCodegen(CodegenMethod method, CodegenClassScope classScope, CodegenNamedMethods namedMethods) {
        method.getBlock().methodReturn(MEMBER_CURRENTROW);
    }

    public <T> T accept(AggregationServiceFactoryForgeVisitor<T> visitor) {
        return visitor.visit(this);
    }

    private boolean hasRefCounting() {
        return aggGroupByDesc.isRefcounted() || aggGroupByDesc.isReclaimAged();
    }

    private CodegenMethod handleRemovedKeysCodegen(CodegenMethod scope, CodegenClassScope classScope) {
        CodegenMethod method = scope.makeChild(EPTypePremade.VOID.getEPType(), AggregationServiceGroupByForge.class, classScope);
        method.getBlock().ifCondition(not(exprDotMethod(MEMBER_REMOVEDKEYS, "isEmpty")))
                .forEach(EPTypePremade.OBJECT.getEPType(), "removedKey", MEMBER_REMOVEDKEYS)
                .exprDotMethod(MEMBER_AGGREGATORSPERGROUP, "remove", ref("removedKey"))
                .blockEnd()
                .exprDotMethod(MEMBER_REMOVEDKEYS, "clear");
        return method;
    }

    public AggGroupByDesc getAggGroupByDesc() {
        return aggGroupByDesc;
    }
}
