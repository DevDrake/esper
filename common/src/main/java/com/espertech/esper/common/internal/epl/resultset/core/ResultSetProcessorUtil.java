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
package com.espertech.esper.common.internal.epl.resultset.core;

import com.espertech.esper.common.client.EventBean;
import com.espertech.esper.common.client.type.EPTypePremade;
import com.espertech.esper.common.internal.bytecodemodel.base.CodegenBlock;
import com.espertech.esper.common.internal.bytecodemodel.base.CodegenClassScope;
import com.espertech.esper.common.internal.bytecodemodel.base.CodegenMethod;
import com.espertech.esper.common.internal.bytecodemodel.core.CodegenInstanceAux;
import com.espertech.esper.common.internal.bytecodemodel.core.CodegenNamedParam;
import com.espertech.esper.common.internal.bytecodemodel.model.expression.CodegenExpressionRef;
import com.espertech.esper.common.internal.collection.ArrayEventIterator;
import com.espertech.esper.common.internal.collection.MultiKeyArrayOfKeys;
import com.espertech.esper.common.internal.collection.UniformPair;
import com.espertech.esper.common.internal.context.util.AgentInstanceContext;
import com.espertech.esper.common.internal.epl.agg.core.AggregationService;
import com.espertech.esper.common.internal.epl.expression.codegen.CodegenLegoMethodExpression;
import com.espertech.esper.common.internal.epl.expression.core.ExprEvaluator;
import com.espertech.esper.common.internal.epl.expression.core.ExprEvaluatorContext;
import com.espertech.esper.common.internal.epl.expression.core.ExprForge;
import com.espertech.esper.common.internal.epl.resultset.order.OrderByProcessor;
import com.espertech.esper.common.internal.epl.resultset.select.core.SelectExprProcessor;
import com.espertech.esper.common.internal.util.CollectionUtil;
import com.espertech.esper.common.internal.view.core.Viewable;

import java.util.*;
import java.util.function.Consumer;

import static com.espertech.esper.common.internal.bytecodemodel.model.expression.CodegenExpressionBuilder.*;
import static com.espertech.esper.common.internal.epl.expression.codegen.ExprForgeCodegenNames.*;
import static com.espertech.esper.common.internal.epl.resultset.codegen.ResultSetProcessorCodegenNames.*;
import static com.espertech.esper.common.internal.epl.util.EPTypeCollectionConst.EPTYPE_SET_MULTIKEYARRAYOFKEYS_EVENTBEAN;
import static com.espertech.esper.common.internal.epl.util.EPTypeCollectionConst.EPTYPE_SET_MULTIKEYARRAYOFKEYS_EVENTBEANARRAY;
import static com.espertech.esper.common.internal.metrics.instrumentation.InstrumentationCode.instblock;
import static com.espertech.esper.common.internal.util.CollectionUtil.*;

public class ResultSetProcessorUtil {
    public final static String METHOD_ITERATORTODEQUE = "iteratorToDeque";
    public final static String METHOD_TOPAIRNULLIFALLNULL = "toPairNullIfAllNull";
    public final static String METHOD_APPLYAGGVIEWRESULT = "applyAggViewResult";
    public final static String METHOD_APPLYAGGJOINRESULT = "applyAggJoinResult";
    public final static String METHOD_CLEARANDAGGREGATEUNGROUPED = "clearAndAggregateUngrouped";
    public final static String METHOD_POPULATESELECTJOINEVENTSNOHAVING = "populateSelectJoinEventsNoHaving";
    public final static String METHOD_POPULATESELECTJOINEVENTSNOHAVINGWITHORDERBY = "populateSelectJoinEventsNoHavingWithOrderBy";
    public final static String METHOD_POPULATESELECTEVENTSNOHAVING = "populateSelectEventsNoHaving";
    public final static String METHOD_POPULATESELECTEVENTSNOHAVINGWITHORDERBY = "populateSelectEventsNoHavingWithOrderBy";
    public final static String METHOD_GETSELECTJOINEVENTSNOHAVING = "getSelectJoinEventsNoHaving";
    public final static String METHOD_GETSELECTJOINEVENTSNOHAVINGWITHORDERBY = "getSelectJoinEventsNoHavingWithOrderBy";
    public final static String METHOD_GETSELECTEVENTSNOHAVING = "getSelectEventsNoHaving";
    public final static String METHOD_GETSELECTEVENTSNOHAVINGWITHORDERBY = "getSelectEventsNoHavingWithOrderBy";
    public final static String METHOD_ORDEROUTGOINGGETITERATOR = "orderOutgoingGetIterator";

    public static void evaluateHavingClauseCodegen(ExprForge optionalHavingClause, CodegenClassScope classScope, CodegenInstanceAux instance) {
        Consumer<CodegenMethod> code = method -> {
            if (optionalHavingClause == null) {
                method.getBlock().methodReturn(constantTrue());
            } else {
                method.getBlock()
                        .apply(instblock(classScope, "qHavingClause", REF_EPS))
                        .declareVar(EPTypePremade.BOOLEANBOXED.getEPType(), "passed", CodegenLegoMethodExpression.codegenBooleanExpressionReturnTrueFalse(optionalHavingClause, classScope, method, REF_EPS, REF_ISNEWDATA, REF_EXPREVALCONTEXT))
                        .apply(instblock(classScope, "aHavingClause", ref("passed")))
                        .methodReturn(ref("passed"));
            }
        };
        instance.getMethods().addMethod(EPTypePremade.BOOLEANPRIMITIVE.getEPType(), "evaluateHavingClause",
                CodegenNamedParam.from(EventBean.EPTYPEARRAY, NAME_EPS, EPTypePremade.BOOLEANPRIMITIVE.getEPType(), NAME_ISNEWDATA, ExprEvaluatorContext.EPTYPE, NAME_EXPREVALCONTEXT), ResultSetProcessorUtil.class, classScope, code);
    }

    /**
     * NOTE: Code-generation-invoked method, method name and parameter order matters
     *
     * @param aggregationService   aggregations
     * @param exprEvaluatorContext ctx
     * @param newData              istream
     * @param oldData              rstream
     * @param eventsPerStream      buf
     */
    public static void applyAggViewResult(AggregationService aggregationService, ExprEvaluatorContext exprEvaluatorContext, EventBean[] newData, EventBean[] oldData, EventBean[] eventsPerStream) {
        if (newData != null) {
            // apply new data to aggregates
            for (int i = 0; i < newData.length; i++) {
                eventsPerStream[0] = newData[i];
                aggregationService.applyEnter(eventsPerStream, null, exprEvaluatorContext);
            }
        }
        if (oldData != null) {
            // apply old data to aggregates
            for (int i = 0; i < oldData.length; i++) {
                eventsPerStream[0] = oldData[i];
                aggregationService.applyLeave(eventsPerStream, null, exprEvaluatorContext);
            }
        }
    }

    /**
     * NOTE: Code-generation-invoked method, method name and parameter order matters
     *
     * @param aggregationService   aggregations
     * @param exprEvaluatorContext ctx
     * @param newEvents            istream
     * @param oldEvents            rstream
     */
    public static void applyAggJoinResult(AggregationService aggregationService, ExprEvaluatorContext exprEvaluatorContext, Set<MultiKeyArrayOfKeys<EventBean>> newEvents, Set<MultiKeyArrayOfKeys<EventBean>> oldEvents) {
        if (newEvents != null) {
            // apply new data to aggregates
            for (MultiKeyArrayOfKeys<EventBean> events : newEvents) {
                aggregationService.applyEnter(events.getArray(), null, exprEvaluatorContext);
            }
        }
        if (oldEvents != null) {
            // apply old data to aggregates
            for (MultiKeyArrayOfKeys<EventBean> events : oldEvents) {
                aggregationService.applyLeave(events.getArray(), null, exprEvaluatorContext);
            }
        }
    }

    /**
     * NOTE: Code-generation-invoked method, method name and parameter order matters
     * Applies the select-clause to the given events returning the selected events. The number of events stays the
     * same, i.e. this method does not filter it just transforms the result set.
     *
     * @param exprProcessor        - processes each input event and returns output event
     * @param events               - input events
     * @param isNewData            - indicates whether we are dealing with new data (istream) or old data (rstream)
     * @param isSynthesize         - set to true to indicate that synthetic events are required for an iterator result set
     * @param exprEvaluatorContext context for expression evalauation
     * @return output events, one for each input event
     */
    public static EventBean[] getSelectEventsNoHaving(SelectExprProcessor exprProcessor, EventBean[] events, boolean isNewData, boolean isSynthesize, ExprEvaluatorContext exprEvaluatorContext) {
        if (events == null) {
            return null;
        }

        EventBean[] result = new EventBean[events.length];
        EventBean[] eventsPerStream = new EventBean[1];
        for (int i = 0; i < events.length; i++) {
            eventsPerStream[0] = events[i];
            result[i] = exprProcessor.process(eventsPerStream, isNewData, isSynthesize, exprEvaluatorContext);
        }
        return result;
    }

    /**
     * Applies the select-clause to the given events returning the selected events. The number of events stays the
     * same, i.e. this method does not filter it just transforms the result set.
     *
     * @param aggregationService   - aggregation svc
     * @param exprProcessor        - processes each input event and returns output event
     * @param orderByProcessor     - orders the outgoing events according to the order-by clause
     * @param events               - input events
     * @param isNewData            - indicates whether we are dealing with new data (istream) or old data (rstream)
     * @param isSynthesize         - set to true to indicate that synthetic events are required for an iterator result set
     * @param exprEvaluatorContext context for expression evalauation
     * @return output events, one for each input event
     */
    public static EventBean[] getSelectEventsNoHavingWithOrderBy(AggregationService aggregationService, SelectExprProcessor exprProcessor, OrderByProcessor orderByProcessor, EventBean[] events, boolean isNewData, boolean isSynthesize, ExprEvaluatorContext exprEvaluatorContext) {
        if (events == null) {
            return null;
        }

        EventBean[] result = new EventBean[events.length];
        EventBean[][] eventGenerators = new EventBean[events.length][];

        EventBean[] eventsPerStream = new EventBean[1];
        for (int i = 0; i < events.length; i++) {
            eventsPerStream[0] = events[i];
            result[i] = exprProcessor.process(eventsPerStream, isNewData, isSynthesize, exprEvaluatorContext);
            eventGenerators[i] = new EventBean[]{events[i]};
        }

        return orderByProcessor.sortPlain(result, eventGenerators, isNewData, exprEvaluatorContext, aggregationService);
    }

    /**
     * Applies the select-clause to the given events returning the selected events. The number of events stays the
     * same, i.e. this method does not filter it just transforms the result set.
     * <p>
     * Also applies a having clause.
     *
     * @param aggregationService   - aggregation svc
     * @param exprProcessor        - processes each input event and returns output event
     * @param orderByProcessor     - for sorting output events according to the order-by clause
     * @param events               - input events
     * @param havingNode           - supplies the having-clause expression
     * @param isNewData            - indicates whether we are dealing with new data (istream) or old data (rstream)
     * @param isSynthesize         - set to true to indicate that synthetic events are required for an iterator result set
     * @param exprEvaluatorContext context for expression evalauation
     * @return output events, one for each input event
     */
    public static EventBean[] getSelectEventsHavingWithOrderBy(AggregationService aggregationService, SelectExprProcessor exprProcessor, OrderByProcessor orderByProcessor, EventBean[] events, ExprEvaluator havingNode, boolean isNewData, boolean isSynthesize, ExprEvaluatorContext exprEvaluatorContext) {
        if (events == null) {
            return null;
        }

        ArrayDeque<EventBean> result = null;
        ArrayDeque<EventBean[]> eventGenerators = null;

        EventBean[] eventsPerStream = new EventBean[1];
        for (EventBean theEvent : events) {
            eventsPerStream[0] = theEvent;

            boolean passesHaving = ResultSetProcessorUtil.evaluateHavingClause(havingNode, eventsPerStream, isNewData, exprEvaluatorContext);
            if (!passesHaving) {
                continue;
            }

            EventBean generated = exprProcessor.process(eventsPerStream, isNewData, isSynthesize, exprEvaluatorContext);
            if (generated != null) {
                if (result == null) {
                    result = new ArrayDeque<>(events.length);
                    eventGenerators = new ArrayDeque<>(events.length);
                }
                result.add(generated);
                eventGenerators.add(new EventBean[]{theEvent});
            }
        }

        if (result != null) {
            return orderByProcessor.sortPlain(CollectionUtil.toArrayEvents(result), CollectionUtil.toArrayEventsArray(eventGenerators), isNewData, exprEvaluatorContext, aggregationService);
        }
        return null;
    }

    public static CodegenMethod getSelectEventsHavingWithOrderByCodegen(CodegenClassScope classScope, CodegenInstanceAux instance) {
        Consumer<CodegenMethod> code = methodNode -> {
            methodNode.getBlock().ifRefNullReturnNull("events")
                    .declareVar(EPTypePremade.ARRAYDEQUE.getEPType(), "result", constantNull())
                    .declareVar(EPTypePremade.ARRAYDEQUE.getEPType(), "eventGenerators", constantNull())
                    .declareVar(EventBean.EPTYPEARRAY, NAME_EPS, newArrayByLength(EventBean.EPTYPE, constant(1)));
            {
                CodegenBlock forEach = methodNode.getBlock().forEach(EventBean.EPTYPE, "theEvent", ref("events"));
                forEach.assignArrayElement(NAME_EPS, constant(0), ref("theEvent"));
                forEach.ifCondition(not(localMethod(instance.getMethods().getMethod("evaluateHavingClause"), REF_EPS, REF_ISNEWDATA, REF_EXPREVALCONTEXT))).blockContinue();
                forEach.declareVar(EventBean.EPTYPE, "generated", exprDotMethod(MEMBER_SELECTEXPRNONMEMBER, "process", REF_EPS, REF_ISNEWDATA, REF_ISSYNTHESIZE, REF_EXPREVALCONTEXT))
                        .ifCondition(notEqualsNull(ref("generated")))
                        .ifCondition(equalsNull(ref("result")))
                        .assignRef("result", newInstance(EPTypePremade.ARRAYDEQUE.getEPType(), arrayLength(ref("events"))))
                        .assignRef("eventGenerators", newInstance(EPTypePremade.ARRAYDEQUE.getEPType(), arrayLength(ref("events"))))
                        .blockEnd()
                        .exprDotMethod(ref("result"), "add", ref("generated"))
                        .declareVar(EventBean.EPTYPEARRAY, "tmp", newArrayByLength(EventBean.EPTYPE, constant(0)))
                        .assignArrayElement("tmp", constant(0), ref("theEvent"))
                        .exprDotMethod(ref("eventGenerators"), "add", ref("tmp"))
                        .blockEnd();
            }

            methodNode.getBlock().ifRefNullReturnNull("result")
                    .declareVar(EventBean.EPTYPEARRAY, "arr", staticMethod(CollectionUtil.class, METHOD_TOARRAYEVENTS, ref("result")))
                    .declareVar(EventBean.EPTYPEARRAYARRAY, "gen", staticMethod(CollectionUtil.class, METHOD_TOARRAYEVENTSARRAY, ref("eventGenerators")))
                    .methodReturn(exprDotMethod(MEMBER_ORDERBYPROCESSOR, "sortPlain", ref("arr"), ref("gen"), REF_ISNEWDATA, REF_EXPREVALCONTEXT, MEMBER_AGGREGATIONSVC));
        };

        return instance.getMethods().addMethod(EventBean.EPTYPEARRAY, "getSelectEventsHavingWithOrderBy",
                CodegenNamedParam.from(AggregationService.EPTYPE, MEMBER_AGGREGATIONSVC.getRef(), SelectExprProcessor.EPTYPE, NAME_SELECTEXPRPROCESSOR, OrderByProcessor.EPTYPE, NAME_ORDERBYPROCESSOR, EventBean.EPTYPEARRAY, "events", EPTypePremade.BOOLEANPRIMITIVE.getEPType(), NAME_ISNEWDATA, EPTypePremade.BOOLEANPRIMITIVE.getEPType(), NAME_ISSYNTHESIZE, ExprEvaluatorContext.EPTYPE, NAME_EXPREVALCONTEXT),
                ResultSetProcessorUtil.class, classScope, code);
    }

    /**
     * Applies the select-clause to the given events returning the selected events. The number of events stays the
     * same, i.e. this method does not filter it just transforms the result set.
     * <p>
     * Also applies a having clause.
     *
     * @param exprProcessor        - processes each input event and returns output event
     * @param events               - input events
     * @param havingNode           - supplies the having-clause expression
     * @param isNewData            - indicates whether we are dealing with new data (istream) or old data (rstream)
     * @param isSynthesize         - set to true to indicate that synthetic events are required for an iterator result set
     * @param exprEvaluatorContext context for expression evalauation
     * @return output events, one for each input event
     */
    public static EventBean[] getSelectEventsHaving(SelectExprProcessor exprProcessor,
                                                    EventBean[] events,
                                                    ExprEvaluator havingNode,
                                                    boolean isNewData,
                                                    boolean isSynthesize,
                                                    ExprEvaluatorContext exprEvaluatorContext) {
        if (events == null) {
            return null;
        }

        ArrayDeque<EventBean> result = null;
        EventBean[] eventsPerStream = new EventBean[1];

        for (EventBean theEvent : events) {
            eventsPerStream[0] = theEvent;

            boolean passesHaving = ResultSetProcessorUtil.evaluateHavingClause(havingNode, eventsPerStream, isNewData, exprEvaluatorContext);
            if (!passesHaving) {
                continue;
            }

            EventBean generated = exprProcessor.process(eventsPerStream, isNewData, isSynthesize, exprEvaluatorContext);
            if (generated != null) {
                if (result == null) {
                    result = new ArrayDeque<>(events.length);
                }
                result.add(generated);
            }
        }

        return CollectionUtil.toArrayMayNull(result);
    }

    public static CodegenMethod getSelectEventsHavingCodegen(CodegenClassScope classScope,
                                                             CodegenInstanceAux instance) {

        Consumer<CodegenMethod> code = methodNode -> {
            methodNode.getBlock()
                    .ifRefNullReturnNull("events")
                    .declareVar(EPTypePremade.ARRAYDEQUE.getEPType(), "result", constantNull())
                    .declareVar(EventBean.EPTYPEARRAY, "eventsPerStream", newArrayByLength(EventBean.EPTYPE, constant(1)));

            {
                CodegenBlock forEach = methodNode.getBlock().forEach(EventBean.EPTYPE, "theEvent", ref("events"));
                forEach.assignArrayElement(REF_EPS, constant(0), ref("theEvent"));
                forEach.ifCondition(not(localMethod(instance.getMethods().getMethod("evaluateHavingClause"), REF_EPS, REF_ISNEWDATA, REF_EXPREVALCONTEXT))).blockContinue();
                forEach.declareVar(EventBean.EPTYPE, "generated", exprDotMethod(MEMBER_SELECTEXPRNONMEMBER, "process", REF_EPS, REF_ISNEWDATA, REF_ISSYNTHESIZE, REF_EXPREVALCONTEXT))
                        .ifCondition(notEqualsNull(ref("generated")))
                        .ifCondition(equalsNull(ref("result")))
                        .assignRef("result", newInstance(EPTypePremade.ARRAYDEQUE.getEPType(), arrayLength(ref("events")))).blockEnd()
                        .exprDotMethod(ref("result"), "add", ref("generated")).blockEnd();
            }
            methodNode.getBlock().methodReturn(staticMethod(CollectionUtil.class, METHOD_TOARRAYMAYNULL, ref("result")));
        };

        return instance.getMethods().addMethod(EventBean.EPTYPEARRAY, "getSelectEventsHaving",
                CodegenNamedParam.from(SelectExprProcessor.EPTYPE, NAME_SELECTEXPRPROCESSOR, EventBean.EPTYPEARRAY, "events", EPTypePremade.BOOLEANPRIMITIVE.getEPType(), NAME_ISNEWDATA, EPTypePremade.BOOLEANPRIMITIVE.getEPType(), NAME_ISSYNTHESIZE, ExprEvaluatorContext.EPTYPE, NAME_EXPREVALCONTEXT),
                ResultSetProcessorUtil.class, classScope, code);
    }

    /**
     * Applies the select-clause to the given events returning the selected events. The number of events stays the
     * same, i.e. this method does not filter it just transforms the result set.
     *
     * @param aggregationService   - aggregation svc
     * @param exprProcessor        - processes each input event and returns output event
     * @param orderByProcessor     - for sorting output events according to the order-by clause
     * @param events               - input events
     * @param isNewData            - indicates whether we are dealing with new data (istream) or old data (rstream)
     * @param isSynthesize         - set to true to indicate that synthetic events are required for an iterator result set
     * @param exprEvaluatorContext context for expression evalauation
     * @return output events, one for each input event
     */
    public static EventBean[] getSelectJoinEventsNoHavingWithOrderBy(AggregationService aggregationService, SelectExprProcessor exprProcessor, OrderByProcessor orderByProcessor, Set<MultiKeyArrayOfKeys<EventBean>> events, boolean isNewData, boolean isSynthesize, ExprEvaluatorContext exprEvaluatorContext) {
        if ((events == null) || (events.isEmpty())) {
            return null;
        }

        EventBean[] result = new EventBean[events.size()];
        EventBean[][] eventGenerators = new EventBean[events.size()][];

        int count = 0;
        for (MultiKeyArrayOfKeys<EventBean> key : events) {
            EventBean[] eventsPerStream = key.getArray();
            result[count] = exprProcessor.process(eventsPerStream, isNewData, isSynthesize, exprEvaluatorContext);
            eventGenerators[count] = eventsPerStream;
            count++;
        }

        return orderByProcessor.sortPlain(result, eventGenerators, isNewData, exprEvaluatorContext, aggregationService);
    }

    /**
     * Applies the select-clause to the given events returning the selected events. The number of events stays the
     * same, i.e. this method does not filter it just transforms the result set.
     *
     * @param exprProcessor        - processes each input event and returns output event
     * @param events               - input events
     * @param isNewData            - indicates whether we are dealing with new data (istream) or old data (rstream)
     * @param isSynthesize         - set to true to indicate that synthetic events are required for an iterator result set
     * @param exprEvaluatorContext context for expression evalauation
     * @return output events, one for each input event
     */
    public static EventBean[] getSelectJoinEventsNoHaving(SelectExprProcessor exprProcessor, Set<MultiKeyArrayOfKeys<EventBean>> events, boolean isNewData, boolean isSynthesize, ExprEvaluatorContext exprEvaluatorContext) {
        if ((events == null) || (events.isEmpty())) {
            return null;
        }

        EventBean[] result = new EventBean[events.size()];
        int count = 0;

        for (MultiKeyArrayOfKeys<EventBean> key : events) {
            EventBean[] eventsPerStream = key.getArray();
            result[count] = exprProcessor.process(eventsPerStream, isNewData, isSynthesize, exprEvaluatorContext);
            count++;
        }

        return result;
    }

    /**
     * Applies the select-clause to the given events returning the selected events. The number of events stays the
     * same, i.e. this method does not filter it just transforms the result set.
     * <p>
     * Also applies a having clause.
     *
     * @param exprProcessor        - processes each input event and returns output event
     * @param events               - input events
     * @param havingNode           - supplies the having-clause expression
     * @param isNewData            - indicates whether we are dealing with new data (istream) or old data (rstream)
     * @param isSynthesize         - set to true to indicate that synthetic events are required for an iterator result set
     * @param exprEvaluatorContext context for expression evalauation
     * @return output events, one for each input event
     */
    public static EventBean[] getSelectJoinEventsHaving(SelectExprProcessor exprProcessor, Set<MultiKeyArrayOfKeys<EventBean>> events, ExprEvaluator havingNode, boolean isNewData, boolean isSynthesize, ExprEvaluatorContext exprEvaluatorContext) {
        if ((events == null) || (events.isEmpty())) {
            return null;
        }

        ArrayDeque<EventBean> result = null;

        for (MultiKeyArrayOfKeys<EventBean> key : events) {
            EventBean[] eventsPerStream = key.getArray();

            boolean passesHaving = ResultSetProcessorUtil.evaluateHavingClause(havingNode, eventsPerStream, isNewData, exprEvaluatorContext);
            if (!passesHaving) {
                continue;
            }

            EventBean resultEvent = exprProcessor.process(eventsPerStream, isNewData, isSynthesize, exprEvaluatorContext);
            if (resultEvent != null) {
                if (result == null) {
                    result = new ArrayDeque<>(events.size());
                }
                result.add(resultEvent);
            }
        }

        return CollectionUtil.toArrayMayNull(result);
    }

    public static CodegenMethod getSelectJoinEventsHavingCodegen(CodegenClassScope classScope,
                                                                 CodegenInstanceAux instance) {

        Consumer<CodegenMethod> code = methodNode -> {
            methodNode.getBlock()
                    .ifCondition(or(equalsNull(ref("events")), exprDotMethod(ref("events"), "isEmpty"))).blockReturn(constantNull())
                    .ifRefNullReturnNull("events")
                    .declareVar(EPTypePremade.ARRAYDEQUE.getEPType(), "result", constantNull());
            {
                CodegenBlock forEach = methodNode.getBlock().forEach(MultiKeyArrayOfKeys.EPTYPE, "key", ref("events"));
                forEach.declareVar(EventBean.EPTYPEARRAY, NAME_EPS, cast(EventBean.EPTYPEARRAY, exprDotMethod(ref("key"), "getArray")));
                forEach.ifCondition(not(localMethod(instance.getMethods().getMethod("evaluateHavingClause"), REF_EPS, REF_ISNEWDATA, REF_EXPREVALCONTEXT))).blockContinue();
                forEach.declareVar(EventBean.EPTYPE, "generated", exprDotMethod(MEMBER_SELECTEXPRNONMEMBER, "process", REF_EPS, REF_ISNEWDATA, REF_ISSYNTHESIZE, REF_EXPREVALCONTEXT))
                        .ifCondition(notEqualsNull(ref("generated")))
                        .ifCondition(equalsNull(ref("result")))
                        .assignRef("result", newInstance(EPTypePremade.ARRAYDEQUE.getEPType(), exprDotMethod(ref("events"), "size"))).blockEnd()
                        .exprDotMethod(ref("result"), "add", ref("generated")).blockEnd();
            }
            methodNode.getBlock().methodReturn(staticMethod(CollectionUtil.class, METHOD_TOARRAYMAYNULL, ref("result")));
        };

        return instance.getMethods().addMethod(EventBean.EPTYPEARRAY, "getSelectJoinEventsHaving",
                CodegenNamedParam.from(SelectExprProcessor.EPTYPE, NAME_SELECTEXPRPROCESSOR, EPTYPE_SET_MULTIKEYARRAYOFKEYS_EVENTBEAN, "events", EPTypePremade.BOOLEANPRIMITIVE.getEPType(), NAME_ISNEWDATA, EPTypePremade.BOOLEANPRIMITIVE.getEPType(), NAME_ISSYNTHESIZE, ExprEvaluatorContext.EPTYPE, NAME_EXPREVALCONTEXT),
                ResultSetProcessorUtil.class, classScope, code);
    }

    /**
     * Applies the select-clause to the given events returning the selected events. The number of events stays the
     * same, i.e. this method does not filter it just transforms the result set.
     * <p>
     * Also applies a having clause.
     *
     * @param aggregationService   - aggregation svc
     * @param exprProcessor        - processes each input event and returns output event
     * @param orderByProcessor     - for sorting output events according to the order-by clause
     * @param events               - input events
     * @param havingNode           - supplies the having-clause expression
     * @param isNewData            - indicates whether we are dealing with new data (istream) or old data (rstream)
     * @param isSynthesize         - set to true to indicate that synthetic events are required for an iterator result set
     * @param exprEvaluatorContext context for expression evalauation
     * @return output events, one for each input event
     */
    public static EventBean[] getSelectJoinEventsHavingWithOrderBy(AggregationService aggregationService, SelectExprProcessor exprProcessor, OrderByProcessor orderByProcessor, Set<MultiKeyArrayOfKeys<EventBean>> events, ExprEvaluator havingNode, boolean isNewData, boolean isSynthesize, ExprEvaluatorContext exprEvaluatorContext) {
        if ((events == null) || (events.isEmpty())) {
            return null;
        }

        ArrayDeque<EventBean> result = null;
        ArrayDeque<EventBean[]> eventGenerators = null;

        for (MultiKeyArrayOfKeys<EventBean> key : events) {
            EventBean[] eventsPerStream = key.getArray();

            boolean passesHaving = ResultSetProcessorUtil.evaluateHavingClause(havingNode, eventsPerStream, isNewData, exprEvaluatorContext);
            if (!passesHaving) {
                continue;
            }

            EventBean resultEvent = exprProcessor.process(eventsPerStream, isNewData, isSynthesize, exprEvaluatorContext);
            if (resultEvent != null) {
                if (result == null) {
                    result = new ArrayDeque<EventBean>(events.size());
                    eventGenerators = new ArrayDeque<EventBean[]>(events.size());
                }
                result.add(resultEvent);
                eventGenerators.add(eventsPerStream);
            }
        }

        if (result != null) {
            return orderByProcessor.sortPlain(CollectionUtil.toArrayEvents(result), CollectionUtil.toArrayEventsArray(eventGenerators), isNewData, exprEvaluatorContext, aggregationService);
        }
        return null;
    }

    public static CodegenMethod getSelectJoinEventsHavingWithOrderByCodegen(CodegenClassScope classScope, CodegenInstanceAux instance) {
        Consumer<CodegenMethod> code = methodNode -> {
            methodNode.getBlock()
                    .ifCondition(or(equalsNull(ref("events")), exprDotMethod(ref("events"), "isEmpty"))).blockReturn(constantNull())
                    .ifRefNullReturnNull("events")
                    .declareVar(EPTypePremade.ARRAYDEQUE.getEPType(), "result", constantNull())
                    .declareVar(EPTypePremade.ARRAYDEQUE.getEPType(), "eventGenerators", constantNull());
            {
                CodegenBlock forEach = methodNode.getBlock().forEach(MultiKeyArrayOfKeys.EPTYPE, "key", ref("events"));
                forEach.declareVar(EventBean.EPTYPEARRAY, NAME_EPS, cast(EventBean.EPTYPEARRAY, exprDotMethod(ref("key"), "getArray")));
                forEach.ifCondition(not(localMethod(instance.getMethods().getMethod("evaluateHavingClause"), REF_EPS, REF_ISNEWDATA, REF_EXPREVALCONTEXT))).blockContinue();
                forEach.declareVar(EventBean.EPTYPE, "resultEvent", exprDotMethod(MEMBER_SELECTEXPRNONMEMBER, "process", REF_EPS, REF_ISNEWDATA, REF_ISSYNTHESIZE, REF_EXPREVALCONTEXT))
                        .ifCondition(notEqualsNull(ref("resultEvent")))
                        .ifCondition(equalsNull(ref("result")))
                        .assignRef("result", newInstance(EPTypePremade.ARRAYDEQUE.getEPType(), exprDotMethod(ref("events"), "size")))
                        .assignRef("eventGenerators", newInstance(EPTypePremade.ARRAYDEQUE.getEPType(), exprDotMethod(ref("events"), "size")))
                        .blockEnd()
                        .exprDotMethod(ref("result"), "add", ref("resultEvent"))
                        .exprDotMethod(ref("eventGenerators"), "add", ref("eventsPerStream"))
                        .blockEnd();
            }
            methodNode.getBlock().ifRefNullReturnNull("result")
                    .declareVar(EventBean.EPTYPEARRAY, "arr", staticMethod(CollectionUtil.class, METHOD_TOARRAYEVENTS, ref("result")))
                    .declareVar(EventBean.EPTYPEARRAYARRAY, "gen", staticMethod(CollectionUtil.class, METHOD_TOARRAYEVENTSARRAY, ref("eventGenerators")))
                    .methodReturn(exprDotMethod(MEMBER_ORDERBYPROCESSOR, "sortPlain", ref("arr"), ref("gen"), REF_ISNEWDATA, REF_EXPREVALCONTEXT, MEMBER_AGGREGATIONSVC));
        };

        return instance.getMethods().addMethod(EventBean.EPTYPEARRAY, "getSelectJoinEventsHavingWithOrderBy",
                CodegenNamedParam.from(AggregationService.EPTYPE, MEMBER_AGGREGATIONSVC.getRef(), SelectExprProcessor.EPTYPE, NAME_SELECTEXPRPROCESSOR, OrderByProcessor.EPTYPE, NAME_ORDERBYPROCESSOR, EPTYPE_SET_MULTIKEYARRAYOFKEYS_EVENTBEANARRAY, "events", EPTypePremade.BOOLEANPRIMITIVE.getEPType(), NAME_ISNEWDATA, EPTypePremade.BOOLEANPRIMITIVE.getEPType(), NAME_ISSYNTHESIZE, ExprEvaluatorContext.EPTYPE, NAME_EXPREVALCONTEXT),
                ResultSetProcessorUtil.class, classScope, code);
    }

    public static void populateSelectEventsNoHaving(SelectExprProcessor exprProcessor, EventBean[] events, boolean isNewData, boolean isSynthesize, Collection<EventBean> result, ExprEvaluatorContext exprEvaluatorContext) {
        if (events == null) {
            return;
        }

        EventBean[] eventsPerStream = new EventBean[1];
        for (EventBean theEvent : events) {
            eventsPerStream[0] = theEvent;

            EventBean resultEvent = exprProcessor.process(eventsPerStream, isNewData, isSynthesize, exprEvaluatorContext);
            if (resultEvent != null) {
                result.add(resultEvent);
            }
        }
    }

    public static void populateSelectEventsNoHavingWithOrderBy(SelectExprProcessor exprProcessor, OrderByProcessor orderByProcessor, EventBean[] events, boolean isNewData, boolean isSynthesize, Collection<EventBean> result, List<Object> sortKeys, ExprEvaluatorContext exprEvaluatorContext) {
        if (events == null) {
            return;
        }

        EventBean[] eventsPerStream = new EventBean[1];
        for (EventBean theEvent : events) {
            eventsPerStream[0] = theEvent;

            EventBean resultEvent = exprProcessor.process(eventsPerStream, isNewData, isSynthesize, exprEvaluatorContext);
            if (resultEvent != null) {
                result.add(resultEvent);
                sortKeys.add(orderByProcessor.getSortKey(eventsPerStream, isNewData, exprEvaluatorContext));
            }
        }
    }

    public static void populateSelectEventsHaving(SelectExprProcessor exprProcessor, EventBean[] events, ExprEvaluator havingNode, boolean isNewData, boolean isSynthesize, List<EventBean> result, ExprEvaluatorContext exprEvaluatorContext) {
        if (events == null) {
            return;
        }

        EventBean[] eventsPerStream = new EventBean[1];
        for (EventBean theEvent : events) {
            eventsPerStream[0] = theEvent;

            boolean passesHaving = ResultSetProcessorUtil.evaluateHavingClause(havingNode, eventsPerStream, isNewData, exprEvaluatorContext);
            if (!passesHaving) {
                continue;
            }

            EventBean resultEvent = exprProcessor.process(eventsPerStream, isNewData, isSynthesize, exprEvaluatorContext);
            if (resultEvent != null) {
                result.add(resultEvent);
            }
        }
    }

    public static CodegenMethod populateSelectEventsHavingCodegen(CodegenClassScope classScope, CodegenInstanceAux instance) {
        Consumer<CodegenMethod> code = methodNode -> {
            methodNode.getBlock()
                    .ifRefNull("events").blockReturnNoValue()
                    .declareVar(EventBean.EPTYPEARRAY, "eventsPerStream", newArrayByLength(EventBean.EPTYPE, constant(1)));

            {
                CodegenBlock forEach = methodNode.getBlock().forEach(EventBean.EPTYPE, "theEvent", ref("events"));
                forEach.assignArrayElement(REF_EPS, constant(0), ref("theEvent"));
                forEach.ifCondition(not(localMethod(instance.getMethods().getMethod("evaluateHavingClause"), REF_EPS, REF_ISNEWDATA, REF_EXPREVALCONTEXT))).blockContinue();
                forEach.declareVar(EventBean.EPTYPE, "resultEvent", exprDotMethod(MEMBER_SELECTEXPRNONMEMBER, "process", REF_EPS, REF_ISNEWDATA, REF_ISSYNTHESIZE, REF_EXPREVALCONTEXT))
                        .ifCondition(notEqualsNull(ref("resultEvent")))
                        .exprDotMethod(ref("result"), "add", ref("resultEvent"));
            }
        };

        return instance.getMethods().addMethod(EPTypePremade.VOID.getEPType(), "populateSelectEventsHaving",
                CodegenNamedParam.from(SelectExprProcessor.EPTYPE, NAME_SELECTEXPRPROCESSOR, EventBean.EPTYPEARRAY, "events", EPTypePremade.BOOLEANPRIMITIVE.getEPType(), NAME_ISNEWDATA, EPTypePremade.BOOLEANPRIMITIVE.getEPType(), NAME_ISSYNTHESIZE, EPTypePremade.LIST.getEPType(), "result", ExprEvaluatorContext.EPTYPE, NAME_EXPREVALCONTEXT),
                ResultSetProcessorUtil.class, classScope, code);
    }

    public static void populateSelectEventsHavingWithOrderBy(SelectExprProcessor exprProcessor, OrderByProcessor orderByProcessor, EventBean[] events, ExprEvaluator havingNode, boolean isNewData, boolean isSynthesize, List<EventBean> result, List<Object> optSortKeys, ExprEvaluatorContext exprEvaluatorContext) {
        if (events == null) {
            return;
        }

        EventBean[] eventsPerStream = new EventBean[1];
        for (EventBean theEvent : events) {
            eventsPerStream[0] = theEvent;

            boolean passesHaving = ResultSetProcessorUtil.evaluateHavingClause(havingNode, eventsPerStream, isNewData, exprEvaluatorContext);
            if (!passesHaving) {
                continue;
            }

            EventBean resultEvent = exprProcessor.process(eventsPerStream, isNewData, isSynthesize, exprEvaluatorContext);
            if (resultEvent != null) {
                result.add(resultEvent);
                optSortKeys.add(orderByProcessor.getSortKey(eventsPerStream, isNewData, exprEvaluatorContext));
            }
        }
    }

    public static CodegenMethod populateSelectEventsHavingWithOrderByCodegen(CodegenClassScope classScope, CodegenInstanceAux instance) {
        Consumer<CodegenMethod> code = methodNode -> {
            methodNode.getBlock()
                    .ifRefNull("events").blockReturnNoValue()
                    .declareVar(EventBean.EPTYPEARRAY, "eventsPerStream", newArrayByLength(EventBean.EPTYPE, constant(1)));

            {
                CodegenBlock forEach = methodNode.getBlock().forEach(EventBean.EPTYPE, "theEvent", ref("events"));
                forEach.assignArrayElement(REF_EPS, constant(0), ref("theEvent"));
                forEach.ifCondition(not(localMethod(instance.getMethods().getMethod("evaluateHavingClause"), REF_EPS, REF_ISNEWDATA, REF_EXPREVALCONTEXT))).blockContinue();
                forEach.declareVar(EventBean.EPTYPE, "resultEvent", exprDotMethod(MEMBER_SELECTEXPRNONMEMBER, "process", REF_EPS, REF_ISNEWDATA, REF_ISSYNTHESIZE, REF_EXPREVALCONTEXT))
                        .ifCondition(notEqualsNull(ref("resultEvent")))
                        .exprDotMethod(ref("result"), "add", ref("resultEvent"))
                        .exprDotMethod(ref("optSortKeys"), "add", exprDotMethod(MEMBER_ORDERBYPROCESSOR, "getSortKey", REF_EPS, REF_ISNEWDATA, REF_EXPREVALCONTEXT));
            }
        };

        return instance.getMethods().addMethod(EPTypePremade.VOID.getEPType(), "populateSelectEventsHavingWithOrderBy",
                CodegenNamedParam.from(SelectExprProcessor.EPTYPE, NAME_SELECTEXPRPROCESSOR, OrderByProcessor.EPTYPE, NAME_ORDERBYPROCESSOR, EventBean.EPTYPEARRAY, "events", EPTypePremade.BOOLEANPRIMITIVE.getEPType(), NAME_ISNEWDATA, EPTypePremade.BOOLEANPRIMITIVE.getEPType(), NAME_ISSYNTHESIZE, EPTypePremade.LIST.getEPType(), "result", EPTypePremade.LIST.getEPType(), "optSortKeys", ExprEvaluatorContext.EPTYPE, NAME_EXPREVALCONTEXT),
                ResultSetProcessorUtil.class, classScope, code);
    }

    public static void populateSelectJoinEventsHaving(SelectExprProcessor exprProcessor, Set<MultiKeyArrayOfKeys<EventBean>> events, ExprEvaluator havingNode, boolean isNewData, boolean isSynthesize, List<EventBean> result, ExprEvaluatorContext exprEvaluatorContext) {
        if (events == null) {
            return;
        }

        for (MultiKeyArrayOfKeys<EventBean> key : events) {
            EventBean[] eventsPerStream = key.getArray();

            boolean passesHaving = ResultSetProcessorUtil.evaluateHavingClause(havingNode, eventsPerStream, isNewData, exprEvaluatorContext);
            if (!passesHaving) {
                continue;
            }

            EventBean resultEvent = exprProcessor.process(eventsPerStream, isNewData, isSynthesize, exprEvaluatorContext);
            if (resultEvent != null) {
                result.add(resultEvent);
            }
        }
    }

    public static CodegenMethod populateSelectJoinEventsHavingCodegen(CodegenClassScope classScope, CodegenInstanceAux instance) {
        Consumer<CodegenMethod> code = methodNode -> {
            methodNode.getBlock().ifRefNull("events").blockReturnNoValue();

            {
                CodegenBlock forEach = methodNode.getBlock().forEach(MultiKeyArrayOfKeys.EPTYPE, "key", ref("events"));
                forEach.declareVar(EventBean.EPTYPEARRAY, NAME_EPS, cast(EventBean.EPTYPEARRAY, exprDotMethod(ref("key"), "getArray")));
                forEach.ifCondition(not(localMethod(instance.getMethods().getMethod("evaluateHavingClause"), REF_EPS, REF_ISNEWDATA, REF_EXPREVALCONTEXT))).blockContinue();
                forEach.declareVar(EventBean.EPTYPE, "resultEvent", exprDotMethod(MEMBER_SELECTEXPRNONMEMBER, "process", REF_EPS, REF_ISNEWDATA, REF_ISSYNTHESIZE, REF_EXPREVALCONTEXT))
                        .ifCondition(notEqualsNull(ref("resultEvent")))
                        .exprDotMethod(ref("result"), "add", ref("resultEvent"));
            }
        };

        return instance.getMethods().addMethod(EPTypePremade.VOID.getEPType(), "populateSelectJoinEventsHavingCodegen",
                CodegenNamedParam.from(SelectExprProcessor.EPTYPE, NAME_SELECTEXPRPROCESSOR, EPTYPE_SET_MULTIKEYARRAYOFKEYS_EVENTBEAN, "events", EPTypePremade.BOOLEANPRIMITIVE.getEPType(), NAME_ISNEWDATA, EPTypePremade.BOOLEANPRIMITIVE.getEPType(), NAME_ISSYNTHESIZE, EPTypePremade.LIST.getEPType(), "result", ExprEvaluatorContext.EPTYPE, NAME_EXPREVALCONTEXT),
                ResultSetProcessorUtil.class, classScope, code);
    }

    public static void populateSelectJoinEventsHavingWithOrderBy(SelectExprProcessor exprProcessor, OrderByProcessor orderByProcessor, Set<MultiKeyArrayOfKeys<EventBean>> events, ExprEvaluator havingNode, boolean isNewData, boolean isSynthesize, List<EventBean> result, List<Object> sortKeys, ExprEvaluatorContext exprEvaluatorContext) {
        if (events == null) {
            return;
        }

        for (MultiKeyArrayOfKeys<EventBean> key : events) {
            EventBean[] eventsPerStream = key.getArray();

            boolean passesHaving = ResultSetProcessorUtil.evaluateHavingClause(havingNode, eventsPerStream, isNewData, exprEvaluatorContext);
            if (!passesHaving) {
                continue;
            }

            EventBean resultEvent = exprProcessor.process(eventsPerStream, isNewData, isSynthesize, exprEvaluatorContext);
            if (resultEvent != null) {
                result.add(resultEvent);
                sortKeys.add(orderByProcessor.getSortKey(eventsPerStream, isNewData, exprEvaluatorContext));
            }
        }
    }

    public static CodegenMethod populateSelectJoinEventsHavingWithOrderByCodegen(CodegenClassScope classScope, CodegenInstanceAux instance) {
        Consumer<CodegenMethod> code = methodNode -> {
            methodNode.getBlock().ifRefNull("events").blockReturnNoValue();

            {
                CodegenBlock forEach = methodNode.getBlock().forEach(MultiKeyArrayOfKeys.EPTYPE, "key", ref("events"));
                forEach.declareVar(EventBean.EPTYPEARRAY, NAME_EPS, cast(EventBean.EPTYPEARRAY, exprDotMethod(ref("key"), "getArray")));
                forEach.ifCondition(not(localMethod(instance.getMethods().getMethod("evaluateHavingClause"), REF_EPS, REF_ISNEWDATA, REF_EXPREVALCONTEXT))).blockContinue();
                forEach.declareVar(EventBean.EPTYPE, "resultEvent", exprDotMethod(MEMBER_SELECTEXPRNONMEMBER, "process", REF_EPS, REF_ISNEWDATA, REF_ISSYNTHESIZE, REF_EXPREVALCONTEXT))
                        .ifCondition(notEqualsNull(ref("resultEvent")))
                        .exprDotMethod(ref("result"), "add", ref("resultEvent"))
                        .exprDotMethod(ref("sortKeys"), "add", exprDotMethod(MEMBER_ORDERBYPROCESSOR, "getSortKey", REF_EPS, REF_ISNEWDATA, REF_EXPREVALCONTEXT));
            }
        };

        return instance.getMethods().addMethod(EPTypePremade.VOID.getEPType(), "populateSelectJoinEventsHavingWithOrderBy",
                CodegenNamedParam.from(SelectExprProcessor.EPTYPE, NAME_SELECTEXPRPROCESSOR, OrderByProcessor.EPTYPE, NAME_ORDERBYPROCESSOR, EPTYPE_SET_MULTIKEYARRAYOFKEYS_EVENTBEANARRAY, "events", EPTypePremade.BOOLEANPRIMITIVE.getEPType(), NAME_ISNEWDATA, EPTypePremade.BOOLEANPRIMITIVE.getEPType(), NAME_ISSYNTHESIZE, EPTypePremade.LIST.getEPType(), "result", EPTypePremade.LIST.getEPType(), "sortKeys", ExprEvaluatorContext.EPTYPE, NAME_EXPREVALCONTEXT),
                ResultSetProcessorUtil.class, classScope, code);
    }

    public static void populateSelectJoinEventsNoHaving(SelectExprProcessor exprProcessor, Set<MultiKeyArrayOfKeys<EventBean>> events, boolean isNewData, boolean isSynthesize, List<EventBean> result, ExprEvaluatorContext exprEvaluatorContext) {
        int length = (events != null) ? events.size() : 0;
        if (length == 0) {
            return;
        }

        for (MultiKeyArrayOfKeys<EventBean> key : events) {
            EventBean[] eventsPerStream = key.getArray();
            EventBean resultEvent = exprProcessor.process(eventsPerStream, isNewData, isSynthesize, exprEvaluatorContext);
            if (resultEvent != null) {
                result.add(resultEvent);
            }
        }
    }

    public static void populateSelectJoinEventsNoHavingWithOrderBy(SelectExprProcessor exprProcessor, OrderByProcessor orderByProcessor, Set<MultiKeyArrayOfKeys<EventBean>> events, boolean isNewData, boolean isSynthesize, List<EventBean> result, List<Object> optSortKeys, ExprEvaluatorContext exprEvaluatorContext) {
        int length = (events != null) ? events.size() : 0;
        if (length == 0) {
            return;
        }

        for (MultiKeyArrayOfKeys<EventBean> key : events) {
            EventBean[] eventsPerStream = key.getArray();
            EventBean resultEvent = exprProcessor.process(eventsPerStream, isNewData, isSynthesize, exprEvaluatorContext);
            if (resultEvent != null) {
                result.add(resultEvent);
                optSortKeys.add(orderByProcessor.getSortKey(eventsPerStream, isNewData, exprEvaluatorContext));
            }
        }
    }

    public static void clearAndAggregateUngrouped(ExprEvaluatorContext exprEvaluatorContext, AggregationService aggregationService, Viewable parent) {
        aggregationService.clearResults(exprEvaluatorContext);
        Iterator<EventBean> it = parent.iterator();
        EventBean[] eventsPerStream = new EventBean[1];
        for (; it.hasNext(); ) {
            eventsPerStream[0] = it.next();
            aggregationService.applyEnter(eventsPerStream, null, exprEvaluatorContext);
        }
    }

    /**
     * NOTE: Code-generation-invoked method, method name and parameter order matters
     *
     * @param it iterator
     * @return deque
     */
    public static ArrayDeque<EventBean> iteratorToDeque(Iterator<EventBean> it) {
        ArrayDeque<EventBean> deque = new ArrayDeque<EventBean>();
        for (; it.hasNext(); ) {
            deque.add(it.next());
        }
        return deque;
    }

    /**
     * NOTE: Code-generation-invoked method, method name and parameter order matters
     *
     * @param selectNewEvents new events
     * @param selectOldEvents old events
     * @return pair or null
     */
    public static UniformPair<EventBean[]> toPairNullIfAllNull(EventBean[] selectNewEvents, EventBean[] selectOldEvents) {
        if ((selectNewEvents != null) || (selectOldEvents != null)) {
            return new UniformPair<>(selectNewEvents, selectOldEvents);
        }
        return null;
    }

    public static void processViewResultCodegen(CodegenMethod method, CodegenClassScope classScope, CodegenInstanceAux instance, boolean hasHaving, boolean selectRStream, boolean hasOrderBy, boolean outputNullIfBothNull) {
        // generate new events using select expressions
        if (!hasHaving) {
            if (selectRStream) {
                if (!hasOrderBy) {
                    method.getBlock().assignRef("selectOldEvents", staticMethod(ResultSetProcessorUtil.class, METHOD_GETSELECTEVENTSNOHAVING, MEMBER_SELECTEXPRPROCESSOR, REF_OLDDATA, constant(false), REF_ISSYNTHESIZE, MEMBER_EXPREVALCONTEXT));
                } else {
                    method.getBlock().assignRef("selectOldEvents", staticMethod(ResultSetProcessorUtil.class, METHOD_GETSELECTEVENTSNOHAVINGWITHORDERBY, MEMBER_AGGREGATIONSVC, MEMBER_SELECTEXPRPROCESSOR, MEMBER_ORDERBYPROCESSOR, REF_OLDDATA, constantFalse(), REF_ISSYNTHESIZE, MEMBER_EXPREVALCONTEXT));
                }
            }

            if (!hasOrderBy) {
                method.getBlock().assignRef("selectNewEvents", staticMethod(ResultSetProcessorUtil.class, METHOD_GETSELECTEVENTSNOHAVING, MEMBER_SELECTEXPRPROCESSOR, REF_NEWDATA, constant(true), REF_ISSYNTHESIZE, MEMBER_EXPREVALCONTEXT));
            } else {
                method.getBlock().assignRef("selectNewEvents", staticMethod(ResultSetProcessorUtil.class, METHOD_GETSELECTEVENTSNOHAVINGWITHORDERBY, MEMBER_AGGREGATIONSVC, MEMBER_SELECTEXPRPROCESSOR, MEMBER_ORDERBYPROCESSOR, REF_NEWDATA, constantTrue(), REF_ISSYNTHESIZE, MEMBER_EXPREVALCONTEXT));
            }
        } else {
            if (selectRStream) {
                if (!hasOrderBy) {
                    CodegenMethod select = ResultSetProcessorUtil.getSelectEventsHavingCodegen(classScope, instance);
                    method.getBlock().assignRef("selectOldEvents", localMethod(select, MEMBER_SELECTEXPRPROCESSOR, REF_OLDDATA, constantFalse(), REF_ISSYNTHESIZE, MEMBER_EXPREVALCONTEXT));
                } else {
                    CodegenMethod select = ResultSetProcessorUtil.getSelectEventsHavingWithOrderByCodegen(classScope, instance);
                    method.getBlock().assignRef("selectOldEvents", localMethod(select, MEMBER_AGGREGATIONSVC, MEMBER_SELECTEXPRPROCESSOR, MEMBER_ORDERBYPROCESSOR, REF_OLDDATA, constantFalse(), REF_ISSYNTHESIZE, MEMBER_EXPREVALCONTEXT));
                }
            }

            if (!hasOrderBy) {
                CodegenMethod select = ResultSetProcessorUtil.getSelectEventsHavingCodegen(classScope, instance);
                method.getBlock().assignRef("selectNewEvents", localMethod(select, MEMBER_SELECTEXPRPROCESSOR, REF_NEWDATA, constantTrue(), REF_ISSYNTHESIZE, MEMBER_EXPREVALCONTEXT));
            } else {
                CodegenMethod select = ResultSetProcessorUtil.getSelectEventsHavingWithOrderByCodegen(classScope, instance);
                method.getBlock().assignRef("selectNewEvents", localMethod(select, MEMBER_AGGREGATIONSVC, MEMBER_SELECTEXPRPROCESSOR, MEMBER_ORDERBYPROCESSOR, REF_NEWDATA, constantTrue(), REF_ISSYNTHESIZE, MEMBER_EXPREVALCONTEXT));
            }
        }

        if (outputNullIfBothNull) {
            method.getBlock().ifCondition(and(equalsNull(ref("selectNewEvents")), equalsNull(ref("selectOldEvents")))).blockReturn(constantNull());
        }
        method.getBlock().methodReturn(newInstance(UniformPair.EPTYPE, ref("selectNewEvents"), ref("selectOldEvents")));
    }

    public static void processJoinResultCodegen(CodegenMethod method, CodegenClassScope classScope, CodegenInstanceAux instance, boolean hasHaving, boolean selectRStream, boolean hasOrderBy, boolean outputNullIfBothNull) {
        if (!hasHaving) {
            if (selectRStream) {
                if (!hasOrderBy) {
                    method.getBlock().assignRef("selectOldEvents", staticMethod(ResultSetProcessorUtil.class, METHOD_GETSELECTJOINEVENTSNOHAVING, MEMBER_SELECTEXPRPROCESSOR, REF_OLDDATA, constantFalse(), REF_ISSYNTHESIZE, MEMBER_EXPREVALCONTEXT));
                } else {
                    method.getBlock().assignRef("selectOldEvents", staticMethod(ResultSetProcessorUtil.class, METHOD_GETSELECTJOINEVENTSNOHAVINGWITHORDERBY, constantNull(), MEMBER_SELECTEXPRPROCESSOR, MEMBER_ORDERBYPROCESSOR, REF_OLDDATA, constantFalse(), REF_ISSYNTHESIZE, MEMBER_EXPREVALCONTEXT));
                }
            }

            if (!hasOrderBy) {
                method.getBlock().assignRef("selectNewEvents ", staticMethod(ResultSetProcessorUtil.class, METHOD_GETSELECTJOINEVENTSNOHAVING, MEMBER_SELECTEXPRPROCESSOR, REF_NEWDATA, constantTrue(), REF_ISSYNTHESIZE, MEMBER_EXPREVALCONTEXT));
            } else {
                method.getBlock().assignRef("selectNewEvents ", staticMethod(ResultSetProcessorUtil.class, METHOD_GETSELECTJOINEVENTSNOHAVINGWITHORDERBY, constantNull(), MEMBER_SELECTEXPRPROCESSOR, MEMBER_ORDERBYPROCESSOR, REF_NEWDATA, constantTrue(), REF_ISSYNTHESIZE, MEMBER_EXPREVALCONTEXT));
            }
        } else {
            if (selectRStream) {
                if (!hasOrderBy) {
                    CodegenMethod select = ResultSetProcessorUtil.getSelectJoinEventsHavingCodegen(classScope, instance);
                    method.getBlock().assignRef("selectOldEvents", localMethod(select, MEMBER_SELECTEXPRPROCESSOR, REF_OLDDATA, constantFalse(), REF_ISSYNTHESIZE, MEMBER_EXPREVALCONTEXT));
                } else {
                    CodegenMethod select = ResultSetProcessorUtil.getSelectJoinEventsHavingWithOrderByCodegen(classScope, instance);
                    method.getBlock().assignRef("selectOldEvents", localMethod(select, MEMBER_AGGREGATIONSVC, MEMBER_SELECTEXPRPROCESSOR, MEMBER_ORDERBYPROCESSOR, REF_OLDDATA, constantFalse(), REF_ISSYNTHESIZE, REF_EXPREVALCONTEXT));
                }
            }

            if (!hasOrderBy) {
                CodegenMethod select = ResultSetProcessorUtil.getSelectJoinEventsHavingCodegen(classScope, instance);
                method.getBlock().assignRef("selectNewEvents", localMethod(select, MEMBER_SELECTEXPRPROCESSOR, REF_NEWDATA, constantTrue(), REF_ISSYNTHESIZE, MEMBER_EXPREVALCONTEXT));
            } else {
                CodegenMethod select = ResultSetProcessorUtil.getSelectJoinEventsHavingWithOrderByCodegen(classScope, instance);
                method.getBlock().assignRef("selectNewEvents", localMethod(select, MEMBER_AGGREGATIONSVC, MEMBER_SELECTEXPRPROCESSOR, MEMBER_ORDERBYPROCESSOR, REF_NEWDATA, constantTrue(), REF_ISSYNTHESIZE, MEMBER_EXPREVALCONTEXT));
            }
        }

        if (outputNullIfBothNull) {
            method.getBlock().ifCondition(and(equalsNull(ref("selectNewEvents")), equalsNull(ref("selectOldEvents")))).blockReturn(constantNull());
        }
        method.getBlock().methodReturn(newInstance(UniformPair.EPTYPE, ref("selectNewEvents"), ref("selectOldEvents")));
    }

    /**
     * NOTE: Code-generation-invoked method, method name and parameter order matters
     *
     * @param outgoingEvents       events
     * @param orderKeys            keys
     * @param orderByProcessor     ordering
     * @param exprEvaluatorContext ctx
     * @return ordered events
     */
    public static ArrayEventIterator orderOutgoingGetIterator(List<EventBean> outgoingEvents, List<Object> orderKeys, OrderByProcessor orderByProcessor, ExprEvaluatorContext exprEvaluatorContext) {
        EventBean[] outgoingEventsArr = CollectionUtil.toArrayEvents(outgoingEvents);
        Object[] orderKeysArr = CollectionUtil.toArrayObjects(orderKeys);
        EventBean[] orderedEvents = orderByProcessor.sortWOrderKeys(outgoingEventsArr, orderKeysArr, exprEvaluatorContext);
        return new ArrayEventIterator(orderedEvents);
    }

    /**
     * NOTE: Code-generation-invoked method, method name and parameter order matters
     *
     * @param count                count
     * @param events               events
     * @param keys                 keys
     * @param currentGenerators    key-generators
     * @param isNewData            irstream
     * @param orderByProcessor     order-by
     * @param agentInstanceContext ctx
     * @param aggregationService   aggregation svc
     * @return events for output
     */
    public static EventBean[] outputFromCountMaySort(int count, EventBean[] events, Object[] keys, EventBean[][] currentGenerators, boolean isNewData, OrderByProcessor orderByProcessor, AgentInstanceContext agentInstanceContext, AggregationService aggregationService) {
        // Resize if some rows were filtered out
        if (count != events.length) {
            if (count == 0) {
                return null;
            }
            events = CollectionUtil.shrinkArrayEvents(count, events);

            if (orderByProcessor != null) {
                keys = CollectionUtil.shrinkArrayObjects(count, keys);
                currentGenerators = CollectionUtil.shrinkArrayEventArray(count, currentGenerators);
            }
        }

        if (orderByProcessor != null) {
            events = orderByProcessor.sortWGroupKeys(events, currentGenerators, keys, isNewData, agentInstanceContext, aggregationService);
        }

        return events;
    }

    public static void outputFromCountMaySortCodegen(CodegenBlock block, CodegenExpressionRef count, CodegenExpressionRef events, CodegenExpressionRef keys, CodegenExpressionRef currentGenerators, boolean hasOrderBy) {
        CodegenBlock resize = block.ifCondition(not(equalsIdentity(count, arrayLength(events))));
        resize.ifCondition(equalsIdentity(count, constant(0))).blockReturn(constantNull())
                .assignRef(events.getRef(), staticMethod(CollectionUtil.class, METHOD_SHRINKARRAYEVENTS, count, events));

        if (hasOrderBy) {
            resize.assignRef(keys.getRef(), staticMethod(CollectionUtil.class, METHOD_SHRINKARRAYOBJECTS, count, keys))
                    .assignRef(currentGenerators.getRef(), staticMethod(CollectionUtil.class, METHOD_SHRINKARRAYEVENTARRAY, count, currentGenerators));
        }

        if (hasOrderBy) {
            block.assignRef(events.getRef(), exprDotMethod(MEMBER_ORDERBYPROCESSOR, "sortWGroupKeys", events, currentGenerators, keys, REF_ISNEWDATA, MEMBER_EXPREVALCONTEXT, MEMBER_AGGREGATIONSVC));
        }

        block.methodReturn(events);
    }


    /**
     * NOTE: Code-generation-invoked method, method name and parameter order matters
     *
     * @param newEvents            newdata
     * @param newEventsSortKey     newdata sortkey
     * @param oldEvents            olddata
     * @param oldEventsSortKey     olddata sortkey
     * @param selectRStream        rstream flag
     * @param orderByProcessor     ordering
     * @param exprEvaluatorContext ctx
     * @return pair
     */
    public static UniformPair<EventBean[]> finalizeOutputMaySortMayRStream(List<EventBean> newEvents, List<Object> newEventsSortKey, List<EventBean> oldEvents, List<Object> oldEventsSortKey, boolean selectRStream, OrderByProcessor orderByProcessor, ExprEvaluatorContext exprEvaluatorContext) {
        EventBean[] newEventsArr = CollectionUtil.toArrayNullForEmptyEvents(newEvents);
        EventBean[] oldEventsArr = null;
        if (selectRStream) {
            oldEventsArr = CollectionUtil.toArrayNullForEmptyEvents(oldEvents);
        }

        if (orderByProcessor != null) {
            Object[] sortKeysNew = CollectionUtil.toArrayNullForEmptyObjects(newEventsSortKey);
            newEventsArr = orderByProcessor.sortWOrderKeys(newEventsArr, sortKeysNew, exprEvaluatorContext);
            if (selectRStream) {
                Object[] sortKeysOld = CollectionUtil.toArrayNullForEmptyObjects(oldEventsSortKey);
                oldEventsArr = orderByProcessor.sortWOrderKeys(oldEventsArr, sortKeysOld, exprEvaluatorContext);
            }
        }

        return ResultSetProcessorUtil.toPairNullIfAllNull(newEventsArr, oldEventsArr);
    }

    public static void finalizeOutputMaySortMayRStreamCodegen(CodegenBlock block, CodegenExpressionRef newEvents, CodegenExpressionRef newEventsSortKey, CodegenExpressionRef oldEvents, CodegenExpressionRef oldEventsSortKey, boolean selectRStream, boolean hasOrderBy) {
        block.declareVar(EventBean.EPTYPEARRAY, "newEventsArr", staticMethod(CollectionUtil.class, METHOD_TOARRAYNULLFOREMPTYEVENTS, newEvents))
                .declareVar(EventBean.EPTYPEARRAY, "oldEventsArr", selectRStream ? staticMethod(CollectionUtil.class, METHOD_TOARRAYNULLFOREMPTYEVENTS, oldEvents) : constantNull());

        if (hasOrderBy) {
            block.declareVar(EPTypePremade.OBJECTARRAY.getEPType(), "sortKeysNew", staticMethod(CollectionUtil.class, METHOD_TOARRAYNULLFOREMPTYOBJECTS, newEventsSortKey))
                    .assignRef("newEventsArr", exprDotMethod(MEMBER_ORDERBYPROCESSOR, "sortWOrderKeys", ref("newEventsArr"), ref("sortKeysNew"), MEMBER_EXPREVALCONTEXT));
            if (selectRStream) {
                block.declareVar(EPTypePremade.OBJECTARRAY.getEPType(), "sortKeysOld", staticMethod(CollectionUtil.class, METHOD_TOARRAYNULLFOREMPTYOBJECTS, oldEventsSortKey))
                        .assignRef("oldEventsArr", exprDotMethod(MEMBER_ORDERBYPROCESSOR, "sortWOrderKeys", ref("oldEventsArr"), ref("sortKeysOld"), MEMBER_EXPREVALCONTEXT));
            }
        }

        block.returnMethodOrBlock(staticMethod(ResultSetProcessorUtil.class, METHOD_TOPAIRNULLIFALLNULL, ref("newEventsArr"), ref("oldEventsArr")));
    }

    public static void prefixCodegenNewOldEvents(CodegenBlock block, boolean sorting, boolean selectRStream) {
        block.declareVar(EPTypePremade.LIST.getEPType(), "newEvents", newInstance(EPTypePremade.ARRAYLIST.getEPType()))
                .declareVar(EPTypePremade.LIST.getEPType(), "oldEvents", selectRStream ? newInstance(EPTypePremade.ARRAYLIST.getEPType()) : constantNull());

        block.declareVar(EPTypePremade.LIST.getEPType(), "newEventsSortKey", constantNull())
                .declareVar(EPTypePremade.LIST.getEPType(), "oldEventsSortKey", constantNull());
        if (sorting) {
            block.assignRef("newEventsSortKey", newInstance(EPTypePremade.ARRAYLIST.getEPType()))
                    .assignRef("oldEventsSortKey", selectRStream ? newInstance(EPTypePremade.ARRAYLIST.getEPType()) : constantNull());
        }
    }

    /**
     * NOTE: Code-generation-invoked method, method name and parameter order matters
     *
     * @param istream istream event
     * @param rstream rstream event
     * @return pair
     */
    public static UniformPair<EventBean[]> toPairNullIfAllNullSingle(EventBean istream, EventBean rstream) {
        if (istream != null) {
            return new UniformPair<>(new EventBean[]{istream}, rstream == null ? null : new EventBean[]{rstream});
        }
        return rstream == null ? null : new UniformPair<>(null, new EventBean[]{rstream});
    }

    /**
     * NOTE: Code-generation-invoked method, method name and parameter order matters
     *
     * @param istream istream event
     * @return pair
     */
    public static UniformPair<EventBean[]> toPairNullIfNullIStream(EventBean istream) {
        return istream == null ? null : new UniformPair<>(new EventBean[]{istream}, null);
    }

    public static boolean evaluateHavingClause(ExprEvaluator havingEval, EventBean[] eventsPerStream, boolean isNewData, ExprEvaluatorContext exprEvaluatorContext) {
        Boolean pass = (Boolean) havingEval.evaluate(eventsPerStream, isNewData, exprEvaluatorContext);
        return pass == null ? false : pass;
    }
}
