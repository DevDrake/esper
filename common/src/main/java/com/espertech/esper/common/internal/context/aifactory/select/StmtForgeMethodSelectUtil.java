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
package com.espertech.esper.common.internal.context.aifactory.select;

import com.espertech.esper.common.client.EventType;
import com.espertech.esper.common.client.annotation.HookType;
import com.espertech.esper.common.client.annotation.IterableUnbound;
import com.espertech.esper.common.client.hook.type.SQLColumnTypeConversion;
import com.espertech.esper.common.client.hook.type.SQLOutputRowConversion;
import com.espertech.esper.common.client.util.NameAccessModifier;
import com.espertech.esper.common.client.util.StateMgmtSetting;
import com.espertech.esper.common.internal.bytecodemodel.base.CodegenPackageScope;
import com.espertech.esper.common.internal.bytecodemodel.core.CodeGenerationIDGenerator;
import com.espertech.esper.common.internal.compile.stage1.spec.*;
import com.espertech.esper.common.internal.compile.stage2.FilterSpecCompiled;
import com.espertech.esper.common.internal.compile.stage2.FilterSpecTracked;
import com.espertech.esper.common.internal.compile.stage2.FilterStreamSpecCompiled;
import com.espertech.esper.common.internal.compile.stage2.StatementSpecCompiled;
import com.espertech.esper.common.internal.compile.stage3.*;
import com.espertech.esper.common.internal.compile.util.CallbackAttributionMatchRecognize;
import com.espertech.esper.common.internal.compile.util.CallbackAttributionStream;
import com.espertech.esper.common.internal.compile.util.CallbackAttributionStreamPattern;
import com.espertech.esper.common.internal.context.activator.*;
import com.espertech.esper.common.internal.context.module.StatementAIFactoryProvider;
import com.espertech.esper.common.internal.context.module.StatementFields;
import com.espertech.esper.common.internal.context.module.StatementInformationalsCompileTime;
import com.espertech.esper.common.internal.context.module.StatementProvider;
import com.espertech.esper.common.internal.epl.annotation.AnnotationUtil;
import com.espertech.esper.common.internal.epl.expression.core.*;
import com.espertech.esper.common.internal.epl.expression.subquery.ExprSubselectNode;
import com.espertech.esper.common.internal.epl.expression.table.ExprTableAccessNode;
import com.espertech.esper.common.internal.epl.historical.common.HistoricalEventViewableForge;
import com.espertech.esper.common.internal.epl.historical.common.HistoricalViewableDesc;
import com.espertech.esper.common.internal.epl.historical.database.core.HistoricalEventViewableDatabaseForge;
import com.espertech.esper.common.internal.epl.historical.database.core.HistoricalEventViewableDatabaseForgeFactory;
import com.espertech.esper.common.internal.epl.historical.method.core.HistoricalEventViewableMethodForge;
import com.espertech.esper.common.internal.epl.historical.method.core.HistoricalEventViewableMethodForgeDesc;
import com.espertech.esper.common.internal.epl.historical.method.core.HistoricalEventViewableMethodForgeFactory;
import com.espertech.esper.common.internal.epl.join.analyze.OuterJoinAnalyzer;
import com.espertech.esper.common.internal.epl.join.base.JoinSetComposerPrototypeDesc;
import com.espertech.esper.common.internal.epl.join.base.JoinSetComposerPrototypeForge;
import com.espertech.esper.common.internal.epl.join.base.JoinSetComposerPrototypeForgeFactory;
import com.espertech.esper.common.internal.epl.join.querygraph.QueryGraphForge;
import com.espertech.esper.common.internal.epl.join.queryplan.QueryPlanForge;
import com.espertech.esper.common.internal.epl.join.queryplan.QueryPlanNodeForge;
import com.espertech.esper.common.internal.epl.join.queryplan.TableLookupIndexReqKey;
import com.espertech.esper.common.internal.epl.namedwindow.path.NamedWindowMetaData;
import com.espertech.esper.common.internal.epl.output.core.OutputProcessViewFactoryForge;
import com.espertech.esper.common.internal.epl.output.core.OutputProcessViewFactoryForgeDesc;
import com.espertech.esper.common.internal.epl.output.core.OutputProcessViewFactoryProvider;
import com.espertech.esper.common.internal.epl.output.core.OutputProcessViewForgeFactory;
import com.espertech.esper.common.internal.epl.pattern.core.EvalForgeNode;
import com.espertech.esper.common.internal.epl.pattern.core.PatternAttributionKeyStream;
import com.espertech.esper.common.internal.epl.pattern.core.PatternContext;
import com.espertech.esper.common.internal.epl.resultset.core.*;
import com.espertech.esper.common.internal.epl.rowrecog.core.RowRecogNFAViewFactoryForge;
import com.espertech.esper.common.internal.epl.rowrecog.core.RowRecogNFAViewPlanUtil;
import com.espertech.esper.common.internal.epl.rowrecog.core.RowRecogPlan;
import com.espertech.esper.common.internal.epl.streamtype.StreamTypeService;
import com.espertech.esper.common.internal.epl.streamtype.StreamTypeServiceImpl;
import com.espertech.esper.common.internal.epl.subselect.*;
import com.espertech.esper.common.internal.epl.table.compiletime.TableMetaData;
import com.espertech.esper.common.internal.epl.table.strategy.ExprTableEvalHelperPlan;
import com.espertech.esper.common.internal.epl.table.strategy.ExprTableEvalStrategyFactoryForge;
import com.espertech.esper.common.internal.epl.util.EPLValidationUtil;
import com.espertech.esper.common.internal.epl.util.ViewResourceVerifyHelper;
import com.espertech.esper.common.internal.epl.util.ViewResourceVerifyResult;
import com.espertech.esper.common.internal.event.map.MapEventType;
import com.espertech.esper.common.internal.fabric.FabricCharge;
import com.espertech.esper.common.internal.schedule.ScheduleHandleTracked;
import com.espertech.esper.common.internal.serde.compiletime.eventtype.SerdeEventTypeUtility;
import com.espertech.esper.common.internal.settings.ClasspathImportUtil;
import com.espertech.esper.common.internal.statement.helper.EPStatementStartMethodHelperValidate;
import com.espertech.esper.common.internal.view.access.ViewResourceDelegateDesc;
import com.espertech.esper.common.internal.view.access.ViewResourceDelegateExpr;
import com.espertech.esper.common.internal.view.core.ViewFactoryForge;
import com.espertech.esper.common.internal.view.core.ViewFactoryForgeArgs;
import com.espertech.esper.common.internal.view.core.ViewFactoryForgeDesc;
import com.espertech.esper.common.internal.view.core.ViewFactoryForgeUtil;
import com.espertech.esper.common.internal.view.prior.PriorEventViewForge;

import java.util.*;

import static com.espertech.esper.common.internal.context.aifactory.select.StatementForgeMethodSelectUtil.*;

public class StmtForgeMethodSelectUtil {

    public static StmtForgeMethodSelectResult make(boolean dataflowOperator, String packageName, String classPostfix, StatementBaseInfo base, StatementCompileTimeServices services) throws ExprValidationException {
        List<FilterSpecTracked> filterSpecCompileds = new ArrayList<>();
        List<ScheduleHandleTracked> scheduleHandleCallbackProviders = new ArrayList<>();
        List<NamedWindowConsumerStreamSpec> namedWindowConsumers = new ArrayList<>();
        StatementSpecCompiled statementSpec = base.getStatementSpec();
        List<StmtClassForgeableFactory> additionalForgeables = new ArrayList<>(1);
        FabricCharge fabricCharge = services.getStateMgmtSettingsProvider().newCharge();

        String[] streamNames = determineStreamNames(statementSpec.getStreamSpecs());
        int numStreams = streamNames.length;

        // first we create streams for subselects, if there are any
        SubSelectActivationDesc subSelectActivationDesc = SubSelectHelperActivations.createSubSelectActivation(false, filterSpecCompileds, namedWindowConsumers, base, services);
        Map<ExprSubselectNode, SubSelectActivationPlan> subselectActivation = subSelectActivationDesc.getSubselects();
        additionalForgeables.addAll(subSelectActivationDesc.getAdditionalForgeables());
        fabricCharge.add(subSelectActivationDesc.getFabricCharge());
        scheduleHandleCallbackProviders.addAll(subSelectActivationDesc.getSchedules());

        // verify for joins that required views are present
        StreamJoinAnalysisResultCompileTime joinAnalysisResult = verifyJoinViews(statementSpec);

        EventType[] streamEventTypes = new EventType[statementSpec.getStreamSpecs().length];
        String[] eventTypeNames = new String[numStreams];
        boolean[] isNamedWindow = new boolean[numStreams];
        ViewableActivatorForge[] viewableActivatorForges = new ViewableActivatorForge[numStreams];
        List<ViewFactoryForge>[] viewForges = new List[numStreams];
        HistoricalEventViewableForge[] historicalEventViewables = new HistoricalEventViewableForge[numStreams];

        for (int stream = 0; stream < numStreams; stream++) {
            StreamSpecCompiled streamSpec = statementSpec.getStreamSpecs()[stream];
            boolean isCanIterateUnbound = streamSpec.getViewSpecs().length == 0 &&
                (services.getConfiguration().getCompiler().getViewResources().isIterableUnbound() ||
                    AnnotationUtil.hasAnnotation(statementSpec.getAnnotations(), IterableUnbound.class));
            ViewFactoryForgeArgs args = new ViewFactoryForgeArgs(stream, null, streamSpec.getOptions(), null, base.getStatementRawInfo(), services);

            if (dataflowOperator) {
                DataFlowActivationResult dfResult = handleDataflowActivation(args, streamSpec);
                streamEventTypes[stream] = dfResult.getStreamEventType();
                eventTypeNames[stream] = dfResult.getEventTypeName();
                viewableActivatorForges[stream] = dfResult.getViewableActivatorForge();
                viewForges[stream] = dfResult.getViewForges();
                additionalForgeables.addAll(dfResult.additionalForgeables);
                scheduleHandleCallbackProviders.addAll(dfResult.getSchedules());
            } else if (streamSpec instanceof FilterStreamSpecCompiled) {
                FilterStreamSpecCompiled filterStreamSpec = (FilterStreamSpecCompiled) statementSpec.getStreamSpecs()[stream];
                FilterSpecCompiled filterSpecCompiled = filterStreamSpec.getFilterSpecCompiled();
                streamEventTypes[stream] = filterSpecCompiled.getResultEventType();
                eventTypeNames[stream] = filterStreamSpec.getFilterSpecCompiled().getFilterForEventTypeName();

                viewableActivatorForges[stream] = new ViewableActivatorFilterForge(filterSpecCompiled, isCanIterateUnbound, stream, false, -1);
                services.getStateMgmtSettingsProvider().filterViewable(fabricCharge, stream, isCanIterateUnbound, base.getStatementRawInfo(), filterStreamSpec.getFilterSpecCompiled().getFilterForEventType());
                ViewFactoryForgeDesc viewForgeDesc = ViewFactoryForgeUtil.createForges(streamSpec.getViewSpecs(), args, streamEventTypes[stream]);
                viewForges[stream] = viewForgeDesc.getForges();
                fabricCharge.add(viewForgeDesc.getFabricCharge());
                additionalForgeables.addAll(viewForgeDesc.getMultikeyForges());
                filterSpecCompileds.add(new FilterSpecTracked(new CallbackAttributionStream(stream), filterSpecCompiled));
                scheduleHandleCallbackProviders.addAll(viewForgeDesc.getSchedules());
            } else if (streamSpec instanceof PatternStreamSpecCompiled) {
                PatternStreamSpecCompiled patternStreamSpec = (PatternStreamSpecCompiled) streamSpec;
                List<EvalForgeNode> forges = patternStreamSpec.getRoot().collectFactories();
                for (EvalForgeNode forge : forges) {
                    final int streamNum = stream;
                    forge.collectSelfFilterAndSchedule(factoryNodeId -> new CallbackAttributionStreamPattern(streamNum, factoryNodeId),
                        filterSpecCompileds, scheduleHandleCallbackProviders);
                }

                MapEventType patternType = ViewableActivatorPatternForge.makeRegisterPatternType(base.getModuleName(), stream, null, patternStreamSpec, services);
                PatternContext patternContext = new PatternContext(stream, patternStreamSpec.getMatchedEventMapMeta(), false, -1, false);
                viewableActivatorForges[stream] = new ViewableActivatorPatternForge(patternType, patternStreamSpec, patternContext, isCanIterateUnbound);
                services.getStateMgmtSettingsProvider().filterViewable(fabricCharge, stream, isCanIterateUnbound, base.getStatementRawInfo(), patternType);
                streamEventTypes[stream] = patternType;
                ViewFactoryForgeDesc viewForgeDesc = ViewFactoryForgeUtil.createForges(streamSpec.getViewSpecs(), args, patternType);
                fabricCharge.add(viewForgeDesc.getFabricCharge());
                viewForges[stream] = viewForgeDesc.getForges();
                scheduleHandleCallbackProviders.addAll(viewForgeDesc.getSchedules());
                additionalForgeables.addAll(viewForgeDesc.getMultikeyForges());
                services.getStateMgmtSettingsProvider().pattern(fabricCharge, new PatternAttributionKeyStream(stream), patternStreamSpec, base.getStatementRawInfo());
            } else if (streamSpec instanceof NamedWindowConsumerStreamSpec) {
                NamedWindowConsumerStreamSpec namedSpec = (NamedWindowConsumerStreamSpec) streamSpec;
                NamedWindowMetaData namedWindow = services.getNamedWindowCompileTimeResolver().resolve(namedSpec.getNamedWindow().getEventType().getName());
                EventType namedWindowType = namedWindow.getEventType();
                if (namedSpec.getOptPropertyEvaluator() != null) {
                    namedWindowType = namedSpec.getOptPropertyEvaluator().getFragmentEventType();
                }

                StreamTypeServiceImpl typesFilterValidation = new StreamTypeServiceImpl(namedWindowType, namedSpec.getOptionalStreamName(), false);
                ExprNode filterSingle = ExprNodeUtilityMake.connectExpressionsByLogicalAndWhenNeeded(namedSpec.getFilterExpressions());
                QueryGraphForge filterQueryGraph = EPLValidationUtil.validateFilterGetQueryGraphSafe(filterSingle, typesFilterValidation, base.getStatementRawInfo(), services);

                namedWindowConsumers.add(namedSpec);
                viewableActivatorForges[stream] = new ViewableActivatorNamedWindowForge(namedSpec, namedWindow, filterSingle, filterQueryGraph, true, namedSpec.getOptPropertyEvaluator());
                streamEventTypes[stream] = namedWindowType;
                viewForges[stream] = Collections.emptyList();
                joinAnalysisResult.setNamedWindowsPerStream(stream, namedWindow);
                eventTypeNames[stream] = namedSpec.getNamedWindow().getEventType().getName();
                isNamedWindow[stream] = true;

                // Consumers to named windows cannot declare a data window view onto the named window to avoid duplicate remove streams
                ViewFactoryForgeDesc viewForgeDesc = ViewFactoryForgeUtil.createForges(streamSpec.getViewSpecs(), args, namedWindowType);
                viewForges[stream] = viewForgeDesc.getForges();
                additionalForgeables.addAll(viewForgeDesc.getMultikeyForges());
                scheduleHandleCallbackProviders.addAll(viewForgeDesc.getSchedules());
                EPStatementStartMethodHelperValidate.validateNoDataWindowOnNamedWindow(viewForges[stream]);
            } else if (streamSpec instanceof TableQueryStreamSpec) {
                validateNoViews(streamSpec, "Table data");
                TableQueryStreamSpec tableStreamSpec = (TableQueryStreamSpec) streamSpec;
                if (numStreams > 1 && tableStreamSpec.getFilterExpressions().size() > 0) {
                    throw new ExprValidationException("Joins with tables do not allow table filter expressions, please add table filters to the where-clause instead");
                }
                TableMetaData table = tableStreamSpec.getTable();
                EPLValidationUtil.validateContextName(true, table.getTableName(), table.getOptionalContextName(), statementSpec.getRaw().getOptionalContextName(), false);
                ExprNode filter = ExprNodeUtilityMake.connectExpressionsByLogicalAndWhenNeeded(tableStreamSpec.getFilterExpressions());
                viewableActivatorForges[stream] = new ViewableActivatorTableForge(table, filter);
                viewForges[stream] = Collections.emptyList();
                eventTypeNames[stream] = tableStreamSpec.getTable().getTableName();
                streamEventTypes[stream] = tableStreamSpec.getTable().getInternalEventType();
                joinAnalysisResult.setTablesForStream(stream, table);

                if (tableStreamSpec.getOptions().isUnidirectional()) {
                    throw new ExprValidationException("Tables cannot be marked as unidirectional");
                }
                if (tableStreamSpec.getOptions().isRetainIntersection() || tableStreamSpec.getOptions().isRetainUnion()) {
                    throw new ExprValidationException("Tables cannot be marked with retain");
                }
            } else if (streamSpec instanceof DBStatementStreamSpec) {
                validateNoViews(streamSpec, "Historical data");
                DBStatementStreamSpec sqlStreamSpec = (DBStatementStreamSpec) streamSpec;
                SQLColumnTypeConversion typeConversionHook = (SQLColumnTypeConversion) ClasspathImportUtil.getAnnotationHook(statementSpec.getAnnotations(), HookType.SQLCOL, SQLColumnTypeConversion.class, services.getClasspathImportServiceCompileTime());
                SQLOutputRowConversion outputRowConversionHook = (SQLOutputRowConversion) ClasspathImportUtil.getAnnotationHook(statementSpec.getAnnotations(), HookType.SQLROW, SQLOutputRowConversion.class, services.getClasspathImportServiceCompileTime());
                HistoricalEventViewableDatabaseForge viewable = HistoricalEventViewableDatabaseForgeFactory.createDBStatementView(stream, sqlStreamSpec, typeConversionHook, outputRowConversionHook, base.getStatementRawInfo(), services);
                streamEventTypes[stream] = viewable.getEventType();
                viewForges[stream] = Collections.emptyList();
                viewableActivatorForges[stream] = new ViewableActivatorHistoricalForge(viewable);
                historicalEventViewables[stream] = viewable;
            } else if (streamSpec instanceof MethodStreamSpec) {
                validateNoViews(streamSpec, "Method data");
                MethodStreamSpec methodStreamSpec = (MethodStreamSpec) streamSpec;
                HistoricalEventViewableMethodForgeDesc desc = HistoricalEventViewableMethodForgeFactory.createMethodStatementView(stream, methodStreamSpec, base, services);
                HistoricalEventViewableMethodForge viewable = desc.getForge();
                fabricCharge.add(desc.getFabricCharge());
                historicalEventViewables[stream] = viewable;
                streamEventTypes[stream] = viewable.getEventType();
                viewForges[stream] = Collections.emptyList();
                viewableActivatorForges[stream] = new ViewableActivatorHistoricalForge(viewable);
                historicalEventViewables[stream] = viewable;
            } else {
                throw new IllegalStateException("Unrecognized stream " + streamSpec);
            }

            // plan serde for iterate-unbound
            if (isCanIterateUnbound) {
                List<StmtClassForgeableFactory> serdeForgeables = SerdeEventTypeUtility.plan(streamEventTypes[stream], base.getStatementRawInfo(), services.getSerdeEventTypeRegistry(), services.getSerdeResolver(), services.getStateMgmtSettingsProvider());
                additionalForgeables.addAll(serdeForgeables);
            }
        }

        // handle match-recognize pattern
        if (statementSpec.getRaw().getMatchRecognizeSpec() != null) {
            if (numStreams > 1) {
                throw new ExprValidationException("Joins are not allowed when using match-recognize");
            }
            if (joinAnalysisResult.getTablesPerStream()[0] != null) {
                throw new ExprValidationException("Tables cannot be used with match-recognize");
            }
            boolean isUnbound = (viewForges[0].isEmpty()) && (!(statementSpec.getStreamSpecs()[0] instanceof NamedWindowConsumerStreamSpec));
            EventType eventType = viewForges[0].isEmpty() ? streamEventTypes[0] : viewForges[0].get(viewForges[0].size() - 1).getEventType();
            RowRecogPlan plan = RowRecogNFAViewPlanUtil.validateAndPlan(eventType, isUnbound, base, services);
            RowRecogNFAViewFactoryForge forge = new RowRecogNFAViewFactoryForge(plan.getForge());
            additionalForgeables.addAll(plan.getAdditionalForgeables());
            scheduleHandleCallbackProviders.add(new ScheduleHandleTracked(CallbackAttributionMatchRecognize.INSTANCE, forge));
            viewForges[0].add(forge);
            List<StmtClassForgeableFactory> serdeForgeables = SerdeEventTypeUtility.plan(eventType, base.getStatementRawInfo(), services.getSerdeEventTypeRegistry(), services.getSerdeResolver(), services.getStateMgmtSettingsProvider());
            additionalForgeables.addAll(serdeForgeables);
            fabricCharge.add(plan.getFabricCharge());
        }

        // Obtain event types from view factory chains
        for (int i = 0; i < viewForges.length; i++) {
            streamEventTypes[i] = viewForges[i].isEmpty() ? streamEventTypes[i] : viewForges[i].get(viewForges[i].size() - 1).getEventType();
        }

        // add unique-information to join analysis
        joinAnalysisResult.addUniquenessInfo(viewForges, statementSpec.getAnnotations());

        // plan sub-selects
        SubSelectHelperForgePlan subselectForgePlan = SubSelectHelperForgePlanner.planSubSelect(base, subselectActivation, streamNames, streamEventTypes, eventTypeNames, services);
        Map<ExprSubselectNode, SubSelectFactoryForge> subselectForges = subselectForgePlan.getSubselects();
        additionalForgeables.addAll(subselectForgePlan.getAdditionalForgeables());
        fabricCharge.add(subselectForgePlan.getFabricCharge());

        // determine view schedules
        ViewResourceDelegateExpr viewResourceDelegateExpr = new ViewResourceDelegateExpr();

        boolean[] hasIStreamOnly = getHasIStreamOnly(isNamedWindow, viewForges);
        boolean optionalStreamsIfAny = OuterJoinAnalyzer.optionalStreamsIfAny(statementSpec.getRaw().getOuterJoinDescList());
        StreamTypeService typeService = new StreamTypeServiceImpl(streamEventTypes, streamNames, hasIStreamOnly, false, optionalStreamsIfAny);

        // Validate views that require validation, specifically streams that don't have
        // sub-views such as DB SQL joins
        HistoricalViewableDesc historicalViewableDesc = new HistoricalViewableDesc(numStreams);
        for (int stream = 0; stream < historicalEventViewables.length; stream++) {
            HistoricalEventViewableForge historicalEventViewable = historicalEventViewables[stream];
            if (historicalEventViewable == null) {
                continue;
            }
            scheduleHandleCallbackProviders.add(new ScheduleHandleTracked(new CallbackAttributionStream(stream), historicalEventViewable));
            List<StmtClassForgeableFactory> forgeables = historicalEventViewable.validate(typeService, base.getStatementSpec().getRaw().getSqlParameters(), base.getStatementRawInfo(), services);
            additionalForgeables.addAll(forgeables);
            historicalViewableDesc.setHistorical(stream, historicalEventViewable.getRequiredStreams());
            if (historicalEventViewable.getRequiredStreams().contains(stream)) {
                throw new ExprValidationException("Parameters for historical stream " + stream + " indicate that the stream is subordinate to itself as stream parameters originate in the same stream");
            }
        }

        // Validate where-clause filter tree, outer join clause and output limit expression
        ExprNode whereClauseValidated = EPStatementStartMethodHelperValidate.validateNodes(statementSpec.getRaw(), typeService, viewResourceDelegateExpr, base.getStatementRawInfo(), services);
        ExprForge whereClauseForge = whereClauseValidated == null ? null : whereClauseValidated.getForge();

        // Obtain result set processor
        ResultSetProcessorDesc resultSetProcessorDesc = ResultSetProcessorFactoryFactory.getProcessorPrototype(ResultSetProcessorAttributionKeyStatement.INSTANCE, new ResultSetSpec(statementSpec), typeService, viewResourceDelegateExpr, joinAnalysisResult.getUnidirectionalInd(), true, base.getContextPropertyRegistry(), false, false, base.getStatementRawInfo(), services);
        additionalForgeables.addAll(resultSetProcessorDesc.getAdditionalForgeables());
        fabricCharge.add(resultSetProcessorDesc.getFabricCharge());

        // Handle 'prior' function nodes in terms of view requirements
        ViewResourceVerifyResult viewVerifyResult = ViewResourceVerifyHelper.verifyPreviousAndPriorRequirements(viewForges, viewResourceDelegateExpr, null, base.getStatementRawInfo(), services);
        ViewResourceDelegateDesc[] viewResourceDelegateDesc = viewVerifyResult.getDescriptors();
        fabricCharge.add(viewVerifyResult.getFabricCharge());
        boolean hasPrior = ViewResourceDelegateDesc.hasPrior(viewResourceDelegateDesc);
        if (hasPrior) {
            for (int stream = 0; stream < numStreams; stream++) {
                SortedSet<Integer> priorRequesteds = viewResourceDelegateDesc[stream].getPriorRequests();
                if (!priorRequesteds.isEmpty()) {
                    boolean unbound = viewForges[stream].isEmpty();
                    EventType eventTypePrior = streamEventTypes[stream];
                    StateMgmtSetting setting = services.getStateMgmtSettingsProvider().prior(fabricCharge, base.getStatementRawInfo(), stream, null, unbound, eventTypePrior, priorRequesteds);
                    viewForges[stream].add(new PriorEventViewForge(unbound, eventTypePrior, setting));
                    List<StmtClassForgeableFactory> serdeForgeables = SerdeEventTypeUtility.plan(eventTypePrior, base.getStatementRawInfo(), services.getSerdeEventTypeRegistry(), services.getSerdeResolver(), services.getStateMgmtSettingsProvider());
                    additionalForgeables.addAll(serdeForgeables);
                }
            }
        }

        OutputProcessViewFactoryForgeDesc outputProcessDesc = OutputProcessViewForgeFactory.make(typeService.getEventTypes(), resultSetProcessorDesc.getResultEventType(), resultSetProcessorDesc.getResultSetProcessorType(), statementSpec, base.getStatementRawInfo(), services);
        OutputProcessViewFactoryForge outputProcessViewFactoryForge = outputProcessDesc.getForge();
        additionalForgeables.addAll(outputProcessDesc.getAdditionalForgeables());
        fabricCharge.add(outputProcessDesc.getFabricCharge());
        outputProcessViewFactoryForge.collectSchedules(scheduleHandleCallbackProviders);

        JoinSetComposerPrototypeForge joinForge = null;
        if (numStreams > 1) {
            boolean hasAggregations = !resultSetProcessorDesc.getAggregationServiceForgeDesc().getExpressions().isEmpty();
            JoinSetComposerPrototypeDesc desc = JoinSetComposerPrototypeForgeFactory.makeComposerPrototype(statementSpec, joinAnalysisResult, typeService, historicalViewableDesc, false, hasAggregations, base.getStatementRawInfo(), services);
            joinForge = desc.getForge();
            additionalForgeables.addAll(desc.getAdditionalForgeables());
            fabricCharge.add(desc.getFabricCharge());
            handleIndexDependencies(joinForge.getOptionalQueryPlan(), services);
        }

        // plan table access
        Map<ExprTableAccessNode, ExprTableEvalStrategyFactoryForge> tableAccessForges = ExprTableEvalHelperPlan.planTableAccess(base.getStatementSpec().getTableAccessNodes());
        validateTableAccessUse(statementSpec.getRaw().getIntoTableSpec(), statementSpec.getRaw().getTableExpressions());
        if (joinAnalysisResult.isUnidirectional() && statementSpec.getRaw().getIntoTableSpec() != null) {
            throw new ExprValidationException("Into-table does not allow unidirectional joins");
        }

        boolean orderByWithoutOutputLimit = statementSpec.getRaw().getOrderByList() != null && !statementSpec.getRaw().getOrderByList().isEmpty() && (statementSpec.getRaw().getOutputLimitSpec() == null);

        String statementAIFactoryProviderClassName = CodeGenerationIDGenerator.generateClassNameSimple(StatementAIFactoryProvider.class, classPostfix);
        String resultSetProcessorProviderClassName = CodeGenerationIDGenerator.generateClassNameSimple(ResultSetProcessorFactoryProvider.class, classPostfix);
        String outputProcessViewProviderClassName = CodeGenerationIDGenerator.generateClassNameSimple(OutputProcessViewFactoryProvider.class, classPostfix);
        String statementProviderClassName = CodeGenerationIDGenerator.generateClassNameSimple(StatementProvider.class, classPostfix);
        String statementFieldsClassName = CodeGenerationIDGenerator.generateClassNameSimple(StatementFields.class, classPostfix);

        StatementAgentInstanceFactorySelectForge forge = new StatementAgentInstanceFactorySelectForge(typeService.getStreamNames(), viewableActivatorForges, resultSetProcessorProviderClassName, viewForges, viewResourceDelegateDesc, whereClauseForge, joinForge, outputProcessViewProviderClassName, outputProcessViewFactoryForge.isDirectAndSimple(), subselectForges, tableAccessForges, orderByWithoutOutputLimit, joinAnalysisResult.isUnidirectional());

        CodegenPackageScope packageScope = new CodegenPackageScope(packageName, statementFieldsClassName, services.isInstrumented(), services.getConfiguration().getCompiler().getByteCode());
        List<StmtClassForgeable> forgeables = new ArrayList<>();
        for (StmtClassForgeableFactory additional : additionalForgeables) {
            forgeables.add(additional.make(packageScope, classPostfix));
        }
        forgeables.add(new StmtClassForgeableRSPFactoryProvider(resultSetProcessorProviderClassName, resultSetProcessorDesc, packageScope, base.getStatementRawInfo(), services.getSerdeResolver().isTargetHA()));
        forgeables.add(new StmtClassForgeableOPVFactoryProvider(outputProcessViewProviderClassName, outputProcessViewFactoryForge, packageScope, numStreams, base.getStatementRawInfo()));
        forgeables.add(new StmtClassForgeableAIFactoryProviderSelect(statementAIFactoryProviderClassName, packageScope, forge));
        forgeables.add(new StmtClassForgeableStmtFields(packageScope.getFieldsClassNameOptional(), packageScope, true));

        if (!dataflowOperator) {
            StatementInformationalsCompileTime informationals = StatementInformationalsUtil.getInformationals(base, filterSpecCompileds, scheduleHandleCallbackProviders, namedWindowConsumers, true, resultSetProcessorDesc.getSelectSubscriberDescriptor(), packageScope, services);
            forgeables.add(new StmtClassForgeableStmtProvider(statementAIFactoryProviderClassName, statementProviderClassName, informationals, packageScope));
        }

        StmtForgeMethodResult forgeableResult = new StmtForgeMethodResult(forgeables, filterSpecCompileds, scheduleHandleCallbackProviders, namedWindowConsumers, FilterSpecCompiled.makeExprNodeList(filterSpecCompileds, Collections.emptyList()), packageScope, fabricCharge);
        return new StmtForgeMethodSelectResult(forgeableResult, resultSetProcessorDesc.getResultEventType(), numStreams);
    }

    private static DataFlowActivationResult handleDataflowActivation(ViewFactoryForgeArgs args, StreamSpecCompiled streamSpec)
        throws ExprValidationException {
        if (!(streamSpec instanceof FilterStreamSpecCompiled)) {
            throw new ExprValidationException("Dataflow operator only allows filters for event types and does not allow tables, named windows or patterns");
        }
        FilterStreamSpecCompiled filterStreamSpec = (FilterStreamSpecCompiled) streamSpec;
        FilterSpecCompiled filterSpecCompiled = filterStreamSpec.getFilterSpecCompiled();
        EventType eventType = filterSpecCompiled.getResultEventType();
        String typeName = filterStreamSpec.getFilterSpecCompiled().getFilterForEventTypeName();
        ViewFactoryForgeDesc viewForgeDesc = ViewFactoryForgeUtil.createForges(streamSpec.getViewSpecs(), args, eventType);
        List<ViewFactoryForge> views = viewForgeDesc.getForges();
        ViewableActivatorDataFlowForge viewableActivator = new ViewableActivatorDataFlowForge(eventType);
        return new DataFlowActivationResult(eventType, typeName, viewableActivator, views, viewForgeDesc.getMultikeyForges(), viewForgeDesc.getSchedules());
    }

    private static void validateNoViews(StreamSpecCompiled streamSpec, String conceptName)
        throws ExprValidationException {
        if (streamSpec.getViewSpecs().length > 0) {
            throw new ExprValidationException(conceptName + " joins do not allow views onto the data, view '"
                + streamSpec.getViewSpecs()[0].getObjectName() + "' is not valid in this context");
        }
    }

    private static void validateTableAccessUse(IntoTableSpec intoTableSpec, Set<ExprTableAccessNode> tableNodes)
        throws ExprValidationException {
        if (intoTableSpec != null && tableNodes != null && tableNodes.size() > 0) {
            for (ExprTableAccessNode node : tableNodes) {
                if (node.getTableName().equals(intoTableSpec.getName())) {
                    throw new ExprValidationException("Invalid use of table '" + intoTableSpec.getName() + "', aggregate-into requires write-only, the expression '" +
                        ExprNodeUtilityPrint.toExpressionStringMinPrecedenceSafe(node) + "' is not allowed");
                }
            }
        }
    }

    private static void handleIndexDependencies(QueryPlanForge queryPlan, StatementCompileTimeServices services) {
        if (queryPlan == null) {
            return;
        }
        HashSet<TableLookupIndexReqKey> indexes = new HashSet<>();
        for (int streamnum = 0; streamnum < queryPlan.getExecNodeSpecs().length; streamnum++) {
            QueryPlanNodeForge node = queryPlan.getExecNodeSpecs()[streamnum];
            indexes.clear();
            node.addIndexes(indexes);
            for (TableLookupIndexReqKey index : indexes) {
                if (index.getTableName() != null) {
                    TableMetaData tableMeta = services.getTableCompileTimeResolver().resolve(index.getTableName());
                    if (tableMeta.getTableVisibility() == NameAccessModifier.PUBLIC) {
                        services.getModuleDependenciesCompileTime().addPathIndex(false, index.getTableName(), tableMeta.getTableModuleName(), index.getIndexName(), index.getIndexModuleName(), services.getNamedWindowCompileTimeRegistry(), services.getTableCompileTimeRegistry());
                    }
                }
            }
        }
    }

    private static class DataFlowActivationResult {
        private final EventType streamEventType;
        private final String eventTypeName;
        private final ViewableActivatorForge viewableActivatorForge;
        private final List<ViewFactoryForge> viewForges;
        private final List<StmtClassForgeableFactory> additionalForgeables;
        private final List<ScheduleHandleTracked> schedules;

        public DataFlowActivationResult(EventType streamEventType, String eventTypeName, ViewableActivatorForge viewableActivatorForge, List<ViewFactoryForge> viewForges, List<StmtClassForgeableFactory> additionalForgeables, List<ScheduleHandleTracked> schedules) {
            this.streamEventType = streamEventType;
            this.eventTypeName = eventTypeName;
            this.viewableActivatorForge = viewableActivatorForge;
            this.viewForges = viewForges;
            this.additionalForgeables = additionalForgeables;
            this.schedules = schedules;
        }

        public EventType getStreamEventType() {
            return streamEventType;
        }

        public String getEventTypeName() {
            return eventTypeName;
        }

        public ViewableActivatorForge getViewableActivatorForge() {
            return viewableActivatorForge;
        }

        public List<ViewFactoryForge> getViewForges() {
            return viewForges;
        }

        public List<StmtClassForgeableFactory> getAdditionalForgeables() {
            return additionalForgeables;
        }

        public List<ScheduleHandleTracked> getSchedules() {
            return schedules;
        }
    }
}
