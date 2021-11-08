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
package com.espertech.esper.runtime.internal.kernel.stage;

import com.espertech.esper.common.client.*;
import com.espertech.esper.common.client.hook.exception.ExceptionHandlerExceptionType;
import com.espertech.esper.common.internal.collection.ArrayBackedCollection;
import com.espertech.esper.common.internal.context.util.*;
import com.espertech.esper.common.internal.event.arr.EventSenderObjectArray;
import com.espertech.esper.common.internal.event.arr.ObjectArrayEventType;
import com.espertech.esper.common.internal.event.avro.AvroSchemaEventType;
import com.espertech.esper.common.internal.event.avro.EventSenderAvro;
import com.espertech.esper.common.internal.event.bean.core.BeanEventType;
import com.espertech.esper.common.internal.event.bean.core.EventSenderBean;
import com.espertech.esper.common.internal.event.core.NaturalEventBean;
import com.espertech.esper.common.internal.event.json.compiletime.EventSenderJsonImpl;
import com.espertech.esper.common.internal.event.json.core.JsonEventType;
import com.espertech.esper.common.internal.event.map.EventSenderMap;
import com.espertech.esper.common.internal.event.map.MapEventType;
import com.espertech.esper.common.internal.event.util.EPRuntimeEventProcessWrapped;
import com.espertech.esper.common.internal.event.xml.BaseXMLEventType;
import com.espertech.esper.common.internal.event.xml.EventSenderXMLDOM;
import com.espertech.esper.common.internal.filtersvc.FilterHandle;
import com.espertech.esper.common.internal.filtersvc.FilterHandleCallback;
import com.espertech.esper.common.internal.schedule.ScheduleHandle;
import com.espertech.esper.common.internal.schedule.ScheduleHandleCallback;
import com.espertech.esper.common.internal.statement.insertintolatch.InsertIntoLatchSpin;
import com.espertech.esper.common.internal.statement.insertintolatch.InsertIntoLatchWait;
import com.espertech.esper.common.internal.util.DeploymentIdNamePair;
import com.espertech.esper.common.internal.util.ExecutionPathDebugLog;
import com.espertech.esper.common.internal.util.MetricUtil;
import com.espertech.esper.common.internal.util.ThreadLogUtil;
import com.espertech.esper.runtime.client.UnmatchedListener;
import com.espertech.esper.runtime.internal.kernel.service.EPEventServiceQueueProcessor;
import com.espertech.esper.runtime.internal.kernel.service.EPEventServiceThreadLocalEntry;
import com.espertech.esper.runtime.internal.kernel.service.EPStatementAgentInstanceHandleComparator;
import com.espertech.esper.runtime.internal.kernel.service.WorkQueue;
import com.espertech.esper.runtime.internal.kernel.statement.EPStatementSPI;
import com.espertech.esper.runtime.internal.kernel.thread.*;
import com.espertech.esper.runtime.internal.metrics.instrumentation.InstrumentationHelper;
import com.espertech.esper.runtime.internal.metrics.jmx.JmxGetter;
import com.espertech.esper.runtime.internal.schedulesvcimpl.ScheduleVisit;
import com.espertech.esper.runtime.internal.schedulesvcimpl.ScheduleVisitor;
import com.espertech.esper.runtime.internal.schedulesvcimpl.SchedulingServiceSPI;
import com.espertech.esper.runtime.internal.statementlifesvc.StatementLifecycleService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Node;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

import static com.espertech.esper.runtime.internal.kernel.service.EPEventServiceHelper.*;
import static com.espertech.esper.runtime.internal.kernel.service.EPEventServiceImpl.MAX_FILTER_FAULT_COUNT;

public class EPStageEventServiceImpl implements EPStageEventServiceSPI, InternalEventRouteDest, EPRuntimeEventProcessWrapped, EPEventServiceQueueProcessor {
    protected static final Logger log = LoggerFactory.getLogger(EPStageEventServiceImpl.class);

    protected StageSpecificServices specificServices;
    protected StageRuntimeServices runtimeServices;
    private final String stageUri;

    private boolean inboundThreading;
    private boolean routeThreading;
    private boolean timerThreading;
    private boolean isUsingExternalClocking;
    protected boolean isPrioritized;
    protected volatile UnmatchedListener unmatchedListener;
    private AtomicLong routedInternal;
    private AtomicLong routedExternal;
    private InternalEventRouter internalEventRouter;
    protected ThreadLocal<EPEventServiceThreadLocalEntry> threadLocals;

    public EPStageEventServiceImpl(StageSpecificServices specificServices, StageRuntimeServices runtimeServices, String stageUri) {
        this.specificServices = specificServices;
        this.runtimeServices = runtimeServices;
        this.stageUri = stageUri;
        this.inboundThreading = specificServices.getThreadingService().isInboundThreading();
        this.routeThreading = specificServices.getThreadingService().isRouteThreading();
        this.timerThreading = specificServices.getThreadingService().isTimerThreading();
        isUsingExternalClocking = true;
        isPrioritized = runtimeServices.getRuntimeSettingsService().getConfigurationRuntime().getExecution().isPrioritized();
        routedInternal = new AtomicLong();
        routedExternal = new AtomicLong();

        initThreadLocals();

        specificServices.getThreadingService().initThreading(stageUri, specificServices);
    }

    public StageSpecificServices getSpecificServices() {
        return specificServices;
    }

    /**
     * Sets the route for events to use
     *
     * @param internalEventRouter router
     */
    public void setInternalEventRouter(InternalEventRouter internalEventRouter) {
        this.internalEventRouter = internalEventRouter;
    }

    @JmxGetter(name = "NumInsertIntoEvents", description = "Number of inserted-into events")
    public long getRoutedInternal() {
        return routedInternal.get();
    }

    @JmxGetter(name = "NumRoutedEvents", description = "Number of routed events")
    public long getRoutedExternal() {
        return routedExternal.get();
    }

    public void sendEventAvro(Object avroGenericDataDotRecord, String avroEventTypeName) {
        if (avroGenericDataDotRecord == null) {
            throw new IllegalArgumentException("Invalid null event object");
        }

        if ((ExecutionPathDebugLog.isDebugEnabled) && (log.isDebugEnabled())) {
            log.debug(".sendMap Processing event " + avroGenericDataDotRecord.toString());
        }

        if (inboundThreading) {
            specificServices.getThreadingService().submitInbound(new InboundUnitSendAvro(avroGenericDataDotRecord, avroEventTypeName, this, specificServices));
        } else {
            EventBean eventBean = wrapEventAvro(avroGenericDataDotRecord, avroEventTypeName);
            processWrappedEvent(eventBean);
        }
    }

    public void sendEventJson(String json, String jsonEventTypeName) {
        if (json == null) {
            throw new IllegalArgumentException("Invalid null event object");
        }

        if ((ExecutionPathDebugLog.isDebugEnabled) && (log.isDebugEnabled())) {
            log.debug(".sendEventJson Processing event " + json);
        }

        if (inboundThreading) {
            specificServices.getThreadingService().submitInbound(new InboundUnitSendJson(json, jsonEventTypeName, this, specificServices));
        } else {
            EventBean eventBean = wrapEventJson(json, jsonEventTypeName);
            processWrappedEvent(eventBean);
        }
    }

    public void sendEventBean(Object theEvent, String eventTypeName) {
        if (theEvent == null) {
            log.error(".sendEvent Null object supplied");
            return;
        }

        if ((ExecutionPathDebugLog.isDebugEnabled) && (log.isDebugEnabled())) {
            log.debug(".sendEvent Processing event " + theEvent);
        }

        if (inboundThreading) {
            specificServices.getThreadingService().submitInbound(new InboundUnitSendEvent(theEvent, eventTypeName, this, specificServices));
        } else {
            EventBean eventBean = runtimeServices.getEventTypeResolvingBeanFactory().adapterForBean(theEvent, eventTypeName);
            processWrappedEvent(eventBean);
        }
    }

    public void advanceTime(long time) {
        if (InstrumentationHelper.ENABLED) {
            InstrumentationHelper.get().qStimulantTime(specificServices.getSchedulingService().getTime(), time, time, false, null, stageUri);
        }

        specificServices.getSchedulingService().setTime(time);

        specificServices.getMetricReportingService().processTimeEvent(time);

        processSchedule(time);

        // Let listeners know of results
        dispatch();

        // Work off the event queue if any events accumulated in there via a route()
        processThreadWorkQueue();

        if (InstrumentationHelper.ENABLED) {
            InstrumentationHelper.get().aStimulantTime();
        }
    }

    public void advanceTimeSpan(long targetTime) {
        advanceTimeSpanInternal(targetTime, null);
    }

    public void advanceTimeSpan(long targetTime, long resolution) {
        advanceTimeSpanInternal(targetTime, resolution);
    }

    public Long getNextScheduledTime() {
        return specificServices.getSchedulingService().getNearestTimeHandle();
    }

    private void advanceTimeSpanInternal(long targetTime, Long optionalResolution) {
        long currentTime = specificServices.getSchedulingService().getTime();

        while (currentTime < targetTime) {

            if ((optionalResolution != null) && (optionalResolution > 0)) {
                currentTime += optionalResolution;
            } else {
                Long nearest = specificServices.getSchedulingService().getNearestTimeHandle();
                if (nearest == null) {
                    currentTime = targetTime;
                } else {
                    currentTime = nearest;
                }
            }
            if (currentTime > targetTime) {
                currentTime = targetTime;
            }

            if (InstrumentationHelper.ENABLED) {
                InstrumentationHelper.get().qStimulantTime(specificServices.getSchedulingService().getTime(), currentTime, targetTime, true, optionalResolution, stageUri);
            }

            specificServices.getSchedulingService().setTime(currentTime);

            processSchedule(currentTime);

            // Let listeners know of results
            dispatch();

            // Work off the event queue if any events accumulated in there via a route()
            processThreadWorkQueue();

            if (InstrumentationHelper.ENABLED) {
                InstrumentationHelper.get().aStimulantTime();
            }
        }

        if (InstrumentationHelper.ENABLED) {
            InstrumentationHelper.get().aStimulantTime();
        }
    }

    public void sendEventXMLDOM(org.w3c.dom.Node node, String eventTypeName) {
        if (node == null) {
            log.error(".sendEvent Null object supplied");
            return;
        }

        // Process event
        if (inboundThreading) {
            specificServices.getThreadingService().submitInbound(new InboundUnitSendDOM(node, eventTypeName, this, specificServices));
        } else {
            EventBean eventBean = wrapEventBeanXMLDOM(node, eventTypeName);
            processWrappedEvent(eventBean);
        }
    }

    public void sendEventObjectArray(Object[] propertyValues, String eventTypeName) throws EPException {
        if (propertyValues == null) {
            throw new IllegalArgumentException("Invalid null event object");
        }

        if ((ExecutionPathDebugLog.isDebugEnabled) && (log.isDebugEnabled())) {
            log.debug(".sendEventObjectArray Processing event " + Arrays.toString(propertyValues));
        }

        if (inboundThreading) {
            specificServices.getThreadingService().submitInbound(new InboundUnitSendObjectArray(propertyValues, eventTypeName, this, specificServices));
        } else {
            EventBean eventBean = wrapEventObjectArray(propertyValues, eventTypeName);
            processWrappedEvent(eventBean);
        }
    }

    public void sendEventMap(Map<String, Object> map, String mapEventTypeName) throws EPException {
        if (map == null) {
            throw new IllegalArgumentException("Invalid null event object");
        }

        if ((ExecutionPathDebugLog.isDebugEnabled) && (log.isDebugEnabled())) {
            log.debug(".sendMap Processing event " + map);
        }

        if (inboundThreading) {
            specificServices.getThreadingService().submitInbound(new InboundUnitSendMap(map, mapEventTypeName, this, specificServices));
        } else {
            EventBean eventBean = wrapEventMap(map, mapEventTypeName);
            processWrappedEvent(eventBean);
        }
    }

    public void routeEventBean(EventBean theEvent) {
        threadLocals.get().getWorkQueue().add(theEvent);
    }

    // Internal route of events via insert-into, holds a statement lock
    public void route(EventBean theEvent, EPStatementHandle epStatementHandle, boolean addToFront, int precedence) {
        if (InstrumentationHelper.ENABLED) {
            InstrumentationHelper.get().qRouteBetweenStmt(theEvent, epStatementHandle, addToFront);
        }

        if (theEvent instanceof NaturalEventBean) {
            theEvent = ((NaturalEventBean) theEvent).getOptionalSynthetic();
        }
        routedInternal.incrementAndGet();
        WorkQueue threadWorkQueue = threadLocals.get().getWorkQueue();
        threadWorkQueue.add(theEvent, epStatementHandle, addToFront, precedence);

        if (InstrumentationHelper.ENABLED) {
            InstrumentationHelper.get().aRouteBetweenStmt();
        }
    }

    public void processWrappedEvent(EventBean eventBean) {
        if (InstrumentationHelper.ENABLED) {
            InstrumentationHelper.get().qStimulantEvent(eventBean, stageUri);
        }

        EPEventServiceThreadLocalEntry tlEntry = threadLocals.get();
        if (internalEventRouter.isHasPreprocessing()) {
            eventBean = internalEventRouter.preprocess(eventBean, tlEntry.getExprEvaluatorContext(), InstrumentationHelper.get());
            if (eventBean == null) {
                return;
            }
        }

        // Acquire main processing lock which locks out statement management
        if (InstrumentationHelper.ENABLED) {
            InstrumentationHelper.get().qEvent(eventBean, stageUri, true);
        }
        specificServices.getEventProcessingRWLock().acquireReadLock();
        try {
            processMatches(eventBean);
        } catch (RuntimeException ex) {
            tlEntry.getMatchesArrayThreadLocal().clear();
            throw new EPException(ex);
        } finally {
            specificServices.getEventProcessingRWLock().releaseReadLock();
            if (InstrumentationHelper.ENABLED) {
                InstrumentationHelper.get().aEvent();
            }
        }

        // Dispatch results to listeners
        // Done outside of the read-lock to prevent lockups when listeners create statements
        dispatch();

        // Work off the event queue if any events accumulated in there via a route() or insert-into
        processThreadWorkQueue();

        if (InstrumentationHelper.ENABLED) {
            InstrumentationHelper.get().aStimulantEvent();
        }
    }

    /**
     * Works off the thread's work queue.
     */
    public void processThreadWorkQueue() {
        WorkQueue queues = threadLocals.get().getWorkQueue();

        if (queues.isFrontEmpty()) {
            boolean haveDispatched = runtimeServices.getNamedWindowDispatchService().dispatch();
            if (haveDispatched) {
                // Dispatch results to listeners
                dispatch();

                if (!queues.isFrontEmpty()) {
                    processThreadWorkQueueFront(queues);
                }
            }
        } else {
            processThreadWorkQueueFront(queues);
        }

        while (queues.processBack(this)) {
            boolean haveDispatched = runtimeServices.getNamedWindowDispatchService().dispatch();
            if (haveDispatched) {
                dispatch();
            }

            if (!queues.isFrontEmpty()) {
                processThreadWorkQueueFront(queues);
            }
        }
    }

    private void processThreadWorkQueueFront(WorkQueue queues) {
        while (queues.processFront(this)) {
            boolean haveDispatched = runtimeServices.getNamedWindowDispatchService().dispatch();
            if (haveDispatched) {
                dispatch();
            }
        }
    }

    public void processThreadWorkQueueLatchedWait(InsertIntoLatchWait insertIntoLatch) {
        // wait for the latch to complete
        EventBean eventBean = insertIntoLatch.await();

        if (InstrumentationHelper.ENABLED) {
            InstrumentationHelper.get().qEvent(eventBean, stageUri, false);
        }
        specificServices.getEventProcessingRWLock().acquireReadLock();
        try {
            processMatches(eventBean);
        } catch (RuntimeException ex) {
            threadLocals.get().getMatchesArrayThreadLocal().clear();
            throw ex;
        } finally {
            insertIntoLatch.done();
            specificServices.getEventProcessingRWLock().releaseReadLock();
            if (InstrumentationHelper.ENABLED) {
                InstrumentationHelper.get().aEvent();
            }
        }

        dispatch();
    }

    public void processThreadWorkQueueLatchedSpin(InsertIntoLatchSpin insertIntoLatch) {
        // wait for the latch to complete
        EventBean eventBean = insertIntoLatch.await();

        if (InstrumentationHelper.ENABLED) {
            InstrumentationHelper.get().qEvent(eventBean, stageUri, false);
        }
        specificServices.getEventProcessingRWLock().acquireReadLock();
        try {
            processMatches(eventBean);
        } catch (RuntimeException ex) {
            threadLocals.get().getMatchesArrayThreadLocal().clear();
            throw ex;
        } finally {
            insertIntoLatch.done();
            specificServices.getEventProcessingRWLock().releaseReadLock();
            if (InstrumentationHelper.ENABLED) {
                InstrumentationHelper.get().aEvent();
            }
        }

        dispatch();
    }

    public void processThreadWorkQueueUnlatched(Object item) {
        EventBean eventBean;
        if (item instanceof EventBean) {
            eventBean = (EventBean) item;
        } else {
            throw new IllegalStateException("Unexpected item type " + item + " in queue");
        }

        if (InstrumentationHelper.ENABLED) {
            InstrumentationHelper.get().qEvent(eventBean, stageUri, false);
        }
        specificServices.getEventProcessingRWLock().acquireReadLock();
        try {
            processMatches(eventBean);
        } catch (RuntimeException ex) {
            threadLocals.get().getMatchesArrayThreadLocal().clear();
            throw ex;
        } finally {
            specificServices.getEventProcessingRWLock().releaseReadLock();
            if (InstrumentationHelper.ENABLED) {
                InstrumentationHelper.get().aEvent();
            }
        }

        dispatch();
    }

    protected void processMatches(EventBean theEvent) {
        // get matching filters
        EPEventServiceThreadLocalEntry tlEntry = threadLocals.get();
        ArrayBackedCollection<FilterHandle> matches = tlEntry.getMatchesArrayThreadLocal();
        long version = specificServices.getFilterService().evaluate(theEvent, matches, tlEntry.getExprEvaluatorContext());

        if (ThreadLogUtil.ENABLED_TRACE) {
            ThreadLogUtil.trace("Found matches for underlying ", matches.size(), theEvent.getUnderlying());
        }

        if (matches.size() == 0) {
            if (unmatchedListener != null) {
                specificServices.getEventProcessingRWLock().releaseReadLock();  // Allow listener to create new statements
                try {
                    unmatchedListener.update(theEvent);
                } catch (Throwable t) {
                    log.error("Exception thrown by unmatched listener: " + t.getMessage(), t);
                } finally {
                    // acquire read lock for release by caller
                    specificServices.getEventProcessingRWLock().acquireReadLock();
                }
            }
            return;
        }

        Map<EPStatementAgentInstanceHandle, Object> stmtCallbacks = tlEntry.getMatchesPerStmtThreadLocal();
        Object[] matchArray = matches.getArray();
        int entryCount = matches.size();

        for (int i = 0; i < entryCount; i++) {
            EPStatementHandleCallbackFilter handleCallback = (EPStatementHandleCallbackFilter) matchArray[i];
            EPStatementAgentInstanceHandle handle = handleCallback.getAgentInstanceHandle();

            // Self-joins require that the internal dispatch happens after all streams are evaluated.
            // Priority or preemptive settings also require special ordering.
            if (handle.isCanSelfJoin() || isPrioritized) {
                Object callbacks = stmtCallbacks.get(handle);
                if (callbacks == null) {
                    stmtCallbacks.put(handle, handleCallback.getFilterCallback());
                } else if (callbacks instanceof ArrayDeque) {
                    ArrayDeque<FilterHandleCallback> q = (ArrayDeque<FilterHandleCallback>) callbacks;
                    q.add(handleCallback.getFilterCallback());
                } else {
                    ArrayDeque<FilterHandleCallback> q = new ArrayDeque<>(4);
                    q.add((FilterHandleCallback) callbacks);
                    q.add(handleCallback.getFilterCallback());
                    stmtCallbacks.put(handle, q);
                }
                continue;
            }

            if (handle.getStatementHandle().getMetricsHandle().isEnabled()) {
                long cpuTimeBefore = MetricUtil.getCPUCurrentThread();
                long wallTimeBefore = MetricUtil.getWall();

                processStatementFilterSingle(handle, handleCallback, theEvent, version, 0);

                long wallTimeAfter = MetricUtil.getWall();
                long cpuTimeAfter = MetricUtil.getCPUCurrentThread();
                long deltaCPU = cpuTimeAfter - cpuTimeBefore;
                long deltaWall = wallTimeAfter - wallTimeBefore;
                specificServices.getMetricReportingService().accountTime(handle.getStatementHandle().getMetricsHandle(), deltaCPU, deltaWall, 1);
            } else {
                if (routeThreading) {
                    specificServices.getThreadingService().submitRoute(new RouteUnitSingleStaged(this, handleCallback, theEvent, version));
                } else {
                    processStatementFilterSingle(handle, handleCallback, theEvent, version, 0);
                }
            }
        }
        matches.clear();
        if (stmtCallbacks.isEmpty()) {
            return;
        }

        for (Map.Entry<EPStatementAgentInstanceHandle, Object> entry : stmtCallbacks.entrySet()) {
            EPStatementAgentInstanceHandle handle = entry.getKey();
            Object callbackList = entry.getValue();

            if (handle.getStatementHandle().getMetricsHandle().isEnabled()) {
                long cpuTimeBefore = MetricUtil.getCPUCurrentThread();
                long wallTimeBefore = MetricUtil.getWall();

                processStatementFilterMultiple(handle, callbackList, theEvent, version, 0);

                long wallTimeAfter = MetricUtil.getWall();
                long cpuTimeAfter = MetricUtil.getCPUCurrentThread();
                long deltaCPU = cpuTimeAfter - cpuTimeBefore;
                long deltaWall = wallTimeAfter - wallTimeBefore;
                int size = 1;
                if (callbackList instanceof Collection) {
                    size = ((Collection) callbackList).size();
                }
                specificServices.getMetricReportingService().accountTime(handle.getStatementHandle().getMetricsHandle(), deltaCPU, deltaWall, size);
            } else {
                if (routeThreading) {
                    specificServices.getThreadingService().submitRoute(new RouteUnitMultipleStaged(this, callbackList, theEvent, handle, version));
                } else {
                    processStatementFilterMultiple(handle, callbackList, theEvent, version, 0);
                }
            }

            if (isPrioritized && handle.isPreemptive()) {
                break;
            }
        }
        stmtCallbacks.clear();
    }

    /**
     * Processing multiple filter matches for a statement.
     *
     * @param handle           statement handle
     * @param callbackList     object containing callbacks
     * @param theEvent         to process
     * @param version          filter version
     * @param filterFaultCount filter fault count
     */
    public void processStatementFilterMultiple(EPStatementAgentInstanceHandle handle, Object callbackList, EventBean theEvent, long version, int filterFaultCount) {
        if (InstrumentationHelper.ENABLED) {
            InstrumentationHelper.get().qEventCP(theEvent, handle, specificServices.getSchedulingService().getTime());
        }
        handle.getStatementAgentInstanceLock().acquireWriteLock();
        try {
            if (handle.isHasVariables()) {
                runtimeServices.getVariableManagementService().setLocalVersion();
            }
            if (!handle.isCurrentFilter(version)) {
                boolean handled = false;
                if (handle.getFilterFaultHandler() != null) {
                    handled = handle.getFilterFaultHandler().handleFilterFault(theEvent, version);
                }
                if (!handled && filterFaultCount < MAX_FILTER_FAULT_COUNT) {
                    handleFilterFault(handle, theEvent, filterFaultCount);
                }
            } else {
                if (callbackList instanceof Collection) {
                    Collection<FilterHandleCallback> callbacks = (Collection<FilterHandleCallback>) callbackList;
                    handle.getMultiMatchHandler().handle(callbacks, theEvent);
                } else {
                    FilterHandleCallback single = (FilterHandleCallback) callbackList;
                    single.matchFound(theEvent, null);
                }

                // internal join processing, if applicable
                handle.internalDispatch();
            }
        } catch (RuntimeException ex) {
            runtimeServices.getExceptionHandlingService().handleException(ex, handle, ExceptionHandlerExceptionType.PROCESS, theEvent);
        } finally {
            if (handle.isHasTableAccess()) {
                runtimeServices.getTableExprEvaluatorContext().releaseAcquiredLocks();
            }
            handle.getStatementAgentInstanceLock().releaseWriteLock();
            if (InstrumentationHelper.ENABLED) {
                InstrumentationHelper.get().aEventCP();
            }
        }
    }

    /**
     * Process a single match.
     *
     * @param handle           statement
     * @param handleCallback   callback
     * @param theEvent         event to indicate
     * @param version          filter version
     * @param filterFaultCount filter fault count
     */
    public void processStatementFilterSingle(EPStatementAgentInstanceHandle handle, EPStatementHandleCallbackFilter handleCallback, EventBean theEvent, long version, int filterFaultCount) {
        if (InstrumentationHelper.ENABLED) {
            InstrumentationHelper.get().qEventCP(theEvent, handle, specificServices.getSchedulingService().getTime());
        }
        handle.getStatementAgentInstanceLock().acquireWriteLock();
        try {
            if (handle.isHasVariables()) {
                runtimeServices.getVariableManagementService().setLocalVersion();
            }
            if (!handle.isCurrentFilter(version)) {
                boolean handled = false;
                if (handle.getFilterFaultHandler() != null) {
                    handled = handle.getFilterFaultHandler().handleFilterFault(theEvent, version);
                }
                if (!handled && filterFaultCount < MAX_FILTER_FAULT_COUNT) {
                    handleFilterFault(handle, theEvent, filterFaultCount);
                }
            } else {
                handleCallback.getFilterCallback().matchFound(theEvent, null);
            }

            // internal join processing, if applicable
            handle.internalDispatch();
        } catch (RuntimeException ex) {
            runtimeServices.getExceptionHandlingService().handleException(ex, handle, ExceptionHandlerExceptionType.PROCESS, theEvent);
        } finally {
            if (handle.isHasTableAccess()) {
                runtimeServices.getTableExprEvaluatorContext().releaseAcquiredLocks();
            }
            handleCallback.getAgentInstanceHandle().getStatementAgentInstanceLock().releaseWriteLock();
            if (InstrumentationHelper.ENABLED) {
                InstrumentationHelper.get().aEventCP();
            }
        }
    }

    protected void handleFilterFault(EPStatementAgentInstanceHandle faultingHandle, EventBean theEvent, int filterFaultCount) {
        ArrayDeque<FilterHandle> callbacksForStatement = new ArrayDeque<FilterHandle>();
        long version = specificServices.getFilterService().evaluate(theEvent, callbacksForStatement, faultingHandle.getStatementId(), threadLocals.get().getExprEvaluatorContext());

        if (callbacksForStatement.size() == 1) {
            EPStatementHandleCallbackFilter handleCallback = (EPStatementHandleCallbackFilter) callbacksForStatement.getFirst();
            processStatementFilterSingle(handleCallback.getAgentInstanceHandle(), handleCallback, theEvent, version, filterFaultCount + 1);
            return;
        }
        if (callbacksForStatement.isEmpty()) {
            return;
        }

        Map<EPStatementAgentInstanceHandle, Object> stmtCallbacks;
        if (isPrioritized) {
            stmtCallbacks = new TreeMap<>(EPStatementAgentInstanceHandleComparator.INSTANCE);
        } else {
            stmtCallbacks = new HashMap<>();
        }

        for (FilterHandle filterHandle : callbacksForStatement) {
            EPStatementHandleCallbackFilter handleCallback = (EPStatementHandleCallbackFilter) filterHandle;
            EPStatementAgentInstanceHandle handle = handleCallback.getAgentInstanceHandle();

            if (handle.isCanSelfJoin() || isPrioritized) {
                Object callbacks = stmtCallbacks.get(handle);
                if (callbacks == null) {
                    stmtCallbacks.put(handle, handleCallback.getFilterCallback());
                } else if (callbacks instanceof ArrayDeque) {
                    ArrayDeque<FilterHandleCallback> q = (ArrayDeque<FilterHandleCallback>) callbacks;
                    q.add(handleCallback.getFilterCallback());
                } else {
                    ArrayDeque<FilterHandleCallback> q = new ArrayDeque<>(4);
                    q.add((FilterHandleCallback) callbacks);
                    q.add(handleCallback.getFilterCallback());
                    stmtCallbacks.put(handle, q);
                }
                continue;
            }

            processStatementFilterSingle(handle, handleCallback, theEvent, version, filterFaultCount + 1);
        }

        if (stmtCallbacks.isEmpty()) {
            return;
        }

        for (Map.Entry<EPStatementAgentInstanceHandle, Object> entry : stmtCallbacks.entrySet()) {
            EPStatementAgentInstanceHandle handle = entry.getKey();
            Object callbackList = entry.getValue();

            processStatementFilterMultiple(handle, callbackList, theEvent, version, filterFaultCount + 1);

            if (isPrioritized && handle.isPreemptive()) {
                break;
            }
        }
    }

    /**
     * Dispatch events.
     */
    public void dispatch() {
        try {
            runtimeServices.getDispatchService().dispatch();
        } catch (RuntimeException ex) {
            throw new EPException(ex);
        }
    }

    public boolean isExternalClockingEnabled() {
        return isUsingExternalClocking;
    }

    /**
     * Destroy for destroying an runtime instance: sets references to null and clears thread-locals
     */
    public void destroy() {
        runtimeServices = null;
        specificServices = null;
        removeFromThreadLocals();
        threadLocals = null;
    }

    public void initialize() {
        initThreadLocals();
    }

    public void clearCaches() {
        initThreadLocals();
    }

    public void setUnmatchedListener(UnmatchedListener listener) {
        this.unmatchedListener = listener;
    }

    public long getCurrentTime() {
        return specificServices.getSchedulingService().getTime();
    }

    public String getRuntimeURI() {
        return stageUri;
    }

    private void removeFromThreadLocals() {
        if (threadLocals != null) {
            threadLocals.remove();
        }
    }

    private void initThreadLocals() {
        removeFromThreadLocals();
        threadLocals = allocateThreadLocals(isPrioritized, runtimeServices.getRuntimeURI(), runtimeServices.getConfigSnapshot(), runtimeServices.getEventBeanService(), runtimeServices.getExceptionHandlingService(), specificServices.getSchedulingService(), runtimeServices.getClasspathImportServiceRuntime().getTimeZone(), runtimeServices.getClasspathImportServiceRuntime().getTimeAbacus(), runtimeServices.getVariableManagementService());
    }

    private void processSchedule(long time) {
        if (InstrumentationHelper.ENABLED) {
            InstrumentationHelper.get().qTime(time, stageUri);
        }

        ArrayBackedCollection<ScheduleHandle> handles = threadLocals.get().getScheduleArrayThreadLocal();

        // Evaluation of schedules is protected by an optional scheduling service lock and then the runtimelock
        // We want to stay in this order for allowing the runtimelock as a second-order lock to the
        // services own lock, if it has one.
        specificServices.getEventProcessingRWLock().acquireReadLock();
        try {
            specificServices.getSchedulingService().evaluate(handles);
        } finally {
            specificServices.getEventProcessingRWLock().releaseReadLock();
        }

        specificServices.getEventProcessingRWLock().acquireReadLock();
        try {
            processScheduleHandles(handles);
        } catch (RuntimeException ex) {
            handles.clear();
            throw ex;
        } finally {
            specificServices.getEventProcessingRWLock().releaseReadLock();
            if (InstrumentationHelper.ENABLED) {
                InstrumentationHelper.get().aTime();
            }
        }
    }

    public void processScheduleHandles(ArrayBackedCollection<ScheduleHandle> handles) {
        if (ThreadLogUtil.ENABLED_TRACE) {
            ThreadLogUtil.trace("Found schedules for", handles.size());
        }

        if (handles.size() == 0) {
            return;
        }

        // handle 1 result separately for performance reasons
        if (handles.size() == 1) {
            Object[] handleArray = handles.getArray();
            EPStatementHandleCallbackSchedule handle = (EPStatementHandleCallbackSchedule) handleArray[0];

            if (handle.getAgentInstanceHandle().getStatementHandle().getMetricsHandle().isEnabled()) {
                long cpuTimeBefore = MetricUtil.getCPUCurrentThread();
                long wallTimeBefore = MetricUtil.getWall();

                processStatementScheduleSingle(handle, specificServices);

                long wallTimeAfter = MetricUtil.getWall();
                long cpuTimeAfter = MetricUtil.getCPUCurrentThread();
                long deltaCPU = cpuTimeAfter - cpuTimeBefore;
                long deltaWall = wallTimeAfter - wallTimeBefore;
                specificServices.getMetricReportingService().accountTime(handle.getAgentInstanceHandle().getStatementHandle().getMetricsHandle(), deltaCPU, deltaWall, 1);
            } else {
                if (timerThreading) {
                    specificServices.getThreadingService().submitTimerWork(new TimerUnitSingleStaged(specificServices, this, handle));
                } else {
                    processStatementScheduleSingle(handle, specificServices);
                }
            }

            handles.clear();
            return;
        }

        Object[] matchArray = handles.getArray();
        int entryCount = handles.size();

        // sort multiple matches for the event into statements
        Map<EPStatementAgentInstanceHandle, Object> stmtCallbacks = threadLocals.get().getSchedulePerStmtThreadLocal();
        stmtCallbacks.clear();
        for (int i = 0; i < entryCount; i++) {
            EPStatementHandleCallbackSchedule handleCallback = (EPStatementHandleCallbackSchedule) matchArray[i];
            EPStatementAgentInstanceHandle handle = handleCallback.getAgentInstanceHandle();
            ScheduleHandleCallback callback = handleCallback.getScheduleCallback();

            Object entry = stmtCallbacks.get(handle);

            // This statement has not been encountered before
            if (entry == null) {
                stmtCallbacks.put(handle, callback);
                continue;
            }

            // This statement has been encountered once before
            if (entry instanceof ScheduleHandleCallback) {
                ScheduleHandleCallback existingCallback = (ScheduleHandleCallback) entry;
                ArrayDeque<ScheduleHandleCallback> entries = new ArrayDeque<ScheduleHandleCallback>();
                entries.add(existingCallback);
                entries.add(callback);
                stmtCallbacks.put(handle, entries);
                continue;
            }

            // This statement has been encountered more then once before
            ArrayDeque<ScheduleHandleCallback> entries = (ArrayDeque<ScheduleHandleCallback>) entry;
            entries.add(callback);
        }
        handles.clear();

        for (Map.Entry<EPStatementAgentInstanceHandle, Object> entry : stmtCallbacks.entrySet()) {
            EPStatementAgentInstanceHandle handle = entry.getKey();
            Object callbackObject = entry.getValue();

            if (handle.getStatementHandle().getMetricsHandle().isEnabled()) {
                long cpuTimeBefore = MetricUtil.getCPUCurrentThread();
                long wallTimeBefore = MetricUtil.getWall();

                processStatementScheduleMultiple(handle, callbackObject, specificServices);

                long wallTimeAfter = MetricUtil.getWall();
                long cpuTimeAfter = MetricUtil.getCPUCurrentThread();
                long deltaCPU = cpuTimeAfter - cpuTimeBefore;
                long deltaWall = wallTimeAfter - wallTimeBefore;
                int numInput = (callbackObject instanceof Collection) ? ((Collection) callbackObject).size() : 1;
                specificServices.getMetricReportingService().accountTime(handle.getStatementHandle().getMetricsHandle(), deltaCPU, deltaWall, numInput);
            } else {
                if (timerThreading) {
                    specificServices.getThreadingService().submitTimerWork(new TimerUnitMultipleStaged(specificServices, this, handle, callbackObject));
                } else {
                    processStatementScheduleMultiple(handle, callbackObject, specificServices);
                }
            }

            if (isPrioritized && handle.isPreemptive()) {
                break;
            }
        }
    }


    private EventBean wrapEventMap(Map<String, Object> map, String eventTypeName) {
        return runtimeServices.getEventTypeResolvingBeanFactory().adapterForMap(map, eventTypeName);
    }

    private EventBean wrapEventObjectArray(Object[] objectArray, String eventTypeName) {
        return runtimeServices.getEventTypeResolvingBeanFactory().adapterForObjectArray(objectArray, eventTypeName);
    }

    private EventBean wrapEventBeanXMLDOM(org.w3c.dom.Node node, String eventTypeName) {
        return runtimeServices.getEventTypeResolvingBeanFactory().adapterForXMLDOM(node, eventTypeName);
    }

    private EventBean wrapEventAvro(Object avroGenericDataDotRecord, String eventTypeName) {
        return runtimeServices.getEventTypeResolvingBeanFactory().adapterForAvro(avroGenericDataDotRecord, eventTypeName);
    }

    private EventBean wrapEventJson(String json, String eventTypeName) {
        return runtimeServices.getEventTypeResolvingBeanFactory().adapterForJson(json, eventTypeName);
    }

    public void routeEventMap(Map<String, Object> map, String eventTypeName) {
        if (map == null) {
            throw new IllegalArgumentException("Invalid null event object");
        }
        EventBean theEvent = runtimeServices.getEventTypeResolvingBeanFactory().adapterForMap(map, eventTypeName);
        routeEventInternal(theEvent);
    }

    public void routeEventBean(Object event, String eventTypeName) {
        if (event == null) {
            throw new IllegalArgumentException("Invalid null event object");
        }
        EventBean theEvent = runtimeServices.getEventTypeResolvingBeanFactory().adapterForBean(event, eventTypeName);
        routeEventInternal(theEvent);
    }

    public void routeEventObjectArray(Object[] event, String eventTypeName) {
        if (event == null) {
            throw new IllegalArgumentException("Invalid null event object");
        }
        EventBean theEvent = runtimeServices.getEventTypeResolvingBeanFactory().adapterForObjectArray(event, eventTypeName);
        routeEventInternal(theEvent);
    }

    public void routeEventXMLDOM(Node event, String eventTypeName) {
        if (event == null) {
            throw new IllegalArgumentException("Invalid null event object");
        }
        EventBean theEvent = runtimeServices.getEventTypeResolvingBeanFactory().adapterForXMLDOM(event, eventTypeName);
        routeEventInternal(theEvent);
    }

    public void routeEventAvro(Object avroGenericDataDotRecord, String eventTypeName) {
        if (avroGenericDataDotRecord == null) {
            throw new IllegalArgumentException("Invalid null event object");
        }
        EventBean theEvent = runtimeServices.getEventTypeResolvingBeanFactory().adapterForAvro(avroGenericDataDotRecord, eventTypeName);
        routeEventInternal(theEvent);
    }

    public void routeEventJson(String json, String eventTypeName) {
        if (json == null) {
            throw new IllegalArgumentException("Invalid null event object");
        }
        EventBean theEvent = runtimeServices.getEventTypeResolvingBeanFactory().adapterForJson(json, eventTypeName);
        routeEventInternal(theEvent);
    }

    public EventSender getEventSender(String eventTypeName) throws EventTypeException {
        EventType eventType = runtimeServices.getEventTypeRepositoryBus().getTypeByName(eventTypeName);
        if (eventType == null) {
            throw new EventTypeException("Event type named '" + eventTypeName + "' could not be found");
        }

        // handle built-in types
        ThreadingService threadingService = specificServices.getThreadingService();
        if (eventType instanceof BeanEventType) {
            return new EventSenderBean(this, (BeanEventType) eventType, runtimeServices.getEventBeanTypedEventFactory(), threadingService);
        }
        if (eventType instanceof MapEventType) {
            return new EventSenderMap(this, (MapEventType) eventType, runtimeServices.getEventBeanTypedEventFactory(), threadingService);
        }
        if (eventType instanceof ObjectArrayEventType) {
            return new EventSenderObjectArray(this, (ObjectArrayEventType) eventType, runtimeServices.getEventBeanTypedEventFactory(), threadingService);
        }
        if (eventType instanceof BaseXMLEventType) {
            return new EventSenderXMLDOM(this, (BaseXMLEventType) eventType, runtimeServices.getEventBeanTypedEventFactory(), threadingService);
        }
        if (eventType instanceof AvroSchemaEventType) {
            return new EventSenderAvro(this, eventType, runtimeServices.getEventBeanTypedEventFactory(), threadingService);
        }
        if (eventType instanceof JsonEventType) {
            return new EventSenderJsonImpl(this, (JsonEventType) eventType, runtimeServices.getEventBeanTypedEventFactory(), threadingService);
        }

        throw new EventTypeException("An event sender for event type named '" + eventTypeName + "' could not be created as the type is not known");
    }

    public Map<DeploymentIdNamePair, Long> getStatementNearestSchedules() {
        return getStatementNearestSchedulesInternal(specificServices.getSchedulingService(), runtimeServices.getStatementLifecycleService());
    }

    public void clockInternal() {
        throw new UnsupportedOperationException("Not support for stage-provided time processing, only external time is supported");
    }

    public void clockExternal() {
        // no action
    }

    public long getNumEventsEvaluated() {
        return specificServices.getFilterService().getNumEventsEvaluated();
    }

    public void resetStats() {
        specificServices.getFilterService().resetStats();
        routedInternal.set(0);
        routedExternal.set(0);
    }

    public String getURI() {
        return stageUri;
    }

    private static Map<DeploymentIdNamePair, Long> getStatementNearestSchedulesInternal(SchedulingServiceSPI schedulingService, StatementLifecycleService statementLifecycleSvc) {
        final Map<Integer, Long> schedulePerStatementId = new HashMap<>();
        schedulingService.visitSchedules(new ScheduleVisitor() {
            public void visit(ScheduleVisit visit) {
                if (schedulePerStatementId.containsKey(visit.getStatementId())) {
                    return;
                }
                schedulePerStatementId.put(visit.getStatementId(), visit.getTimestamp());
            }
        });

        Map<DeploymentIdNamePair, Long> result = new HashMap<>();
        for (Map.Entry<Integer, Long> schedule : schedulePerStatementId.entrySet()) {
            EPStatementSPI spi = statementLifecycleSvc.getStatementById(schedule.getKey());
            if (spi != null) {
                result.put(new DeploymentIdNamePair(spi.getDeploymentId(), spi.getName()), schedule.getValue());
            }
        }
        return result;
    }

    private void routeEventInternal(EventBean theEvent) {
        EPEventServiceThreadLocalEntry tlEntry = threadLocals.get();
        if (internalEventRouter.isHasPreprocessing()) {
            theEvent = internalEventRouter.preprocess(theEvent, tlEntry.getExprEvaluatorContext(), InstrumentationHelper.get());
            if (theEvent == null) {
                return;
            }
        }
        tlEntry.getWorkQueue().add(theEvent);
    }
}
