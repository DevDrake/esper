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

import com.espertech.esper.common.client.EventBean;
import com.espertech.esper.common.client.EventType;
import com.espertech.esper.common.client.type.EPType;
import com.espertech.esper.common.client.type.EPTypeClass;
import com.espertech.esper.common.client.type.EPTypeClassParameterized;
import com.espertech.esper.common.client.type.EPTypePremade;
import com.espertech.esper.common.internal.bytecodemodel.base.CodegenClassScope;
import com.espertech.esper.common.internal.bytecodemodel.base.CodegenMethod;
import com.espertech.esper.common.internal.bytecodemodel.base.CodegenMethodScope;
import com.espertech.esper.common.internal.bytecodemodel.model.expression.CodegenExpression;
import com.espertech.esper.common.internal.bytecodemodel.name.CodegenFieldNameSubqueryResult;
import com.espertech.esper.common.internal.compile.stage1.spec.StatementSpecRaw;
import com.espertech.esper.common.internal.compile.stage2.StatementSpecCompiled;
import com.espertech.esper.common.internal.epl.expression.codegen.ExprForgeCodegenNames;
import com.espertech.esper.common.internal.epl.expression.codegen.ExprForgeCodegenSymbol;
import com.espertech.esper.common.internal.epl.expression.core.*;
import com.espertech.esper.common.internal.epl.lookup.SubordTableLookupStrategy;
import com.espertech.esper.common.internal.epl.streamtype.StreamTypeService;
import com.espertech.esper.common.internal.metrics.instrumentation.InstrumentationBuilderExpr;

import java.io.StringWriter;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;

import static com.espertech.esper.common.internal.bytecodemodel.model.expression.CodegenExpressionBuilder.*;
import static com.espertech.esper.common.internal.epl.expression.subquery.ExprSubselectEvalMatchSymbol.NAME_MATCHINGEVENTS;
import static com.espertech.esper.common.internal.epl.expression.subquery.ExprSubselectEvalMatchSymbol.REF_MATCHINGEVENTS;
import static com.espertech.esper.common.internal.epl.expression.subquery.ExprSubselectNode.SubselectEvaluationType.*;
import static com.espertech.esper.common.internal.epl.util.EPTypeCollectionConst.EPTYPE_COLLECTION_EVENTBEAN;

/**
 * Represents a subselect in an expression tree.
 */
public abstract class ExprSubselectNode extends ExprNodeBase implements ExprEvaluator, ExprEnumerationForge, ExprTypableReturnForge, ExprForgeInstrumentable {
    public static final ExprSubselectNode[] EMPTY_SUBSELECT_ARRAY = new ExprSubselectNode[0];

    protected ExprNode[] selectClause;
    protected String[] selectAsNames;
    protected ExprForge filterExpr;
    protected ExprForge havingExpr;
    protected EventType rawEventType;
    private StreamTypeService filterSubqueryStreamTypes;
    private StatementSpecRaw statementSpecRaw;
    private StatementSpecCompiled statementSpecCompiled;
    private SubqueryAggregationType subselectAggregationType;
    private int subselectNumber = -1;
    private boolean filterStreamSubselect;
    private ExprValidationContext filterStreamExprValidationContext;

    public abstract boolean isAllowMultiColumnSelect();

    public abstract void validateSubquery(ExprValidationContext validationContext) throws ExprValidationException;

    public abstract LinkedHashMap<String, Object> typableGetRowProperties() throws ExprValidationException;

    protected abstract CodegenExpression evalMatchesPlainCodegen(CodegenMethodScope parent, ExprSubselectEvalMatchSymbol symbols, CodegenClassScope classScope);

    protected abstract CodegenExpression evalMatchesGetCollEventsCodegen(CodegenMethodScope parent, ExprSubselectEvalMatchSymbol symbols, CodegenClassScope classScope);

    protected abstract CodegenExpression evalMatchesGetCollScalarCodegen(CodegenMethodScope parent, ExprSubselectEvalMatchSymbol symbols, CodegenClassScope classScope);

    protected abstract CodegenExpression evalMatchesGetEventBeanCodegen(CodegenMethodScope parent, ExprSubselectEvalMatchSymbol symbols, CodegenClassScope classScope);

    protected abstract CodegenExpression evalMatchesTypableSingleCodegen(CodegenMethodScope parent, ExprSubselectEvalMatchSymbol symbols, CodegenClassScope classScope);

    protected abstract CodegenExpression evalMatchesTypableMultiCodegen(CodegenMethodScope parent, ExprSubselectEvalMatchSymbol symbols, CodegenClassScope classScope);

    /**
     * Ctor.
     *
     * @param statementSpec is the lookup statement spec from the parser, unvalidated
     */
    public ExprSubselectNode(StatementSpecRaw statementSpec) {
        this.statementSpecRaw = statementSpec;
    }

    public ExprEvaluator getExprEvaluator() {
        return this;
    }

    public boolean isConstantResult() {
        return false;
    }

    public ExprForge getForge() {
        return this;
    }

    public ExprNodeRenderable getForgeRenderable() {
        return this;
    }

    public ExprNode validate(ExprValidationContext validationContext) throws ExprValidationException {
        validateSubquery(validationContext);
        if (filterStreamSubselect) {
            filterStreamExprValidationContext = validationContext;
        }
        return null;
    }

    /**
     * Supplies a compiled statement spec.
     *
     * @param statementSpecCompiled compiled validated filters
     * @param subselectNumber       subselect assigned number
     */
    public void setStatementSpecCompiled(StatementSpecCompiled statementSpecCompiled, int subselectNumber) {
        this.statementSpecCompiled = statementSpecCompiled;
        this.subselectNumber = subselectNumber;
    }

    /**
     * Returns the compiled statement spec.
     *
     * @return compiled statement
     */
    public StatementSpecCompiled getStatementSpecCompiled() {
        return statementSpecCompiled;
    }

    /**
     * Sets the validates select clause
     *
     * @param selectClause is the expression representing the select clause
     */
    public void setSelectClause(ExprNode[] selectClause) {
        this.selectClause = selectClause;
    }

    public Object evaluate(EventBean[] eventsPerStream, boolean isNewData, ExprEvaluatorContext exprEvaluatorContext) {
        throw ExprNodeUtilityMake.makeUnsupportedCompileTime();
    }

    public Object evaluate(EventBean[] eventsPerStream, boolean isNewData, Collection<EventBean> matchingEvents, ExprEvaluatorContext exprEvaluatorContext) {
        throw ExprNodeUtilityMake.makeUnsupportedCompileTime();
    }

    public CodegenExpression evaluateCodegenUninstrumented(EPTypeClass requiredType, CodegenMethodScope codegenMethodScope, ExprForgeCodegenSymbol exprSymbol, CodegenClassScope codegenClassScope) {
        return makeEvaluate(PLAIN, this, getEvaluationType(), codegenMethodScope, exprSymbol, codegenClassScope);
    }

    public CodegenExpression evaluateCodegen(EPTypeClass requiredType, CodegenMethodScope parent, ExprForgeCodegenSymbol exprSymbol, CodegenClassScope codegenClassScope) {
        return new InstrumentationBuilderExpr(this.getClass(), this, "ExprSubselect", requiredType, parent, exprSymbol, codegenClassScope).build();
    }

    public CodegenExpression evaluateGetROCollectionEventsCodegen(CodegenMethodScope parent, ExprForgeCodegenSymbol exprSymbol, CodegenClassScope codegenClassScope) {
        return makeEvaluate(GETEVENTCOLL, this, EPTypePremade.COLLECTION.getEPType(), parent, exprSymbol, codegenClassScope);
    }

    public CodegenExpression evaluateGetROCollectionScalarCodegen(CodegenMethodScope parent, ExprForgeCodegenSymbol exprSymbol, CodegenClassScope codegenClassScope) {
        return makeEvaluate(GETSCALARCOLL, this, EPTypePremade.COLLECTION.getEPType(), parent, exprSymbol, codegenClassScope);
    }

    public CodegenExpression evaluateGetEventBeanCodegen(CodegenMethodScope parent, ExprForgeCodegenSymbol exprSymbol, CodegenClassScope codegenClassScope) {
        return makeEvaluate(GETEVENT, this, EventBean.EPTYPE, parent, exprSymbol, codegenClassScope);
    }

    public CodegenExpression evaluateTypableSingleCodegen(CodegenMethodScope codegenMethodScope, ExprForgeCodegenSymbol exprSymbol, CodegenClassScope codegenClassScope) {
        return makeEvaluate(TYPABLESINGLE, this, EPTypePremade.OBJECTARRAY.getEPType(), codegenMethodScope, exprSymbol, codegenClassScope);
    }

    public CodegenExpression evaluateTypableMultiCodegen(CodegenMethodScope parent, ExprForgeCodegenSymbol exprSymbol, CodegenClassScope codegenClassScope) {
        return makeEvaluate(TYPABLEMULTI, this, EPTypePremade.OBJECTARRAYARRAY.getEPType(), parent, exprSymbol, codegenClassScope);
    }

    public ExprForgeConstantType getForgeConstantType() {
        return ExprForgeConstantType.NONCONST;
    }

    public LinkedHashMap<String, Object> getRowProperties() throws ExprValidationException {
        return typableGetRowProperties();
    }

    public Boolean isMultirow() {
        return true;   // subselect can always return multiple rows
    }

    /**
     * Returns the uncompiled statement spec.
     *
     * @return statement spec uncompiled
     */
    public StatementSpecRaw getStatementSpecRaw() {
        return statementSpecRaw;
    }

    /**
     * Supplies the name of the select expression as-tag
     *
     * @param selectAsNames is the as-name(s)
     */
    public void setSelectAsNames(String[] selectAsNames) {
        this.selectAsNames = selectAsNames;
    }

    /**
     * Sets the validated filter expression, or null if there is none.
     *
     * @param filterExpr is the filter
     */
    public void setFilterExpr(ExprForge filterExpr) {
        this.filterExpr = filterExpr;
    }

    public void setHavingExpr(ExprForge havingExpr) {
        this.havingExpr = havingExpr;
    }

    public void toPrecedenceFreeEPL(StringWriter writer, ExprNodeRenderableFlags flags) {
        if ((selectAsNames != null) && (selectAsNames[0] != null)) {
            writer.append(selectAsNames[0]);
            return;
        }
        writer.append("subselect_");
        writer.append(Integer.toString(subselectNumber + 1));  // Error-reporting starts at 1, internally we start at zero
    }

    public ExprPrecedenceEnum getPrecedence() {
        return ExprPrecedenceEnum.UNARY;
    }

    public boolean equalsNode(ExprNode node, boolean ignoreStreamPrefix) {
        return false;   // 2 subselects are never equivalent
    }

    /**
     * Sets the event type generated for wildcard selects.
     *
     * @param rawEventType is the wildcard type (parent view)
     */
    public void setRawEventType(EventType rawEventType) {
        this.rawEventType = rawEventType;
    }

    /**
     * Returns the select clause or null if none.
     *
     * @return clause
     */
    public ExprNode[] getSelectClause() {
        return selectClause;
    }

    /**
     * Returns the event type.
     *
     * @return type
     */
    public EventType getRawEventType() {
        return rawEventType;
    }

    /**
     * Return stream types.
     *
     * @return types
     */
    public StreamTypeService getFilterSubqueryStreamTypes() {
        return filterSubqueryStreamTypes;
    }

    /**
     * Set stream types.
     *
     * @param filterSubqueryStreamTypes types
     */
    public void setFilterSubqueryStreamTypes(StreamTypeService filterSubqueryStreamTypes) {
        this.filterSubqueryStreamTypes = filterSubqueryStreamTypes;
    }

    public SubqueryAggregationType getSubselectAggregationType() {
        return subselectAggregationType;
    }

    public void setSubselectAggregationType(SubqueryAggregationType subselectAggregationType) {
        this.subselectAggregationType = subselectAggregationType;
    }

    public int getSubselectNumber() {
        return subselectNumber;
    }

    public void setFilterStreamSubselect(boolean filterStreamSubselect) {
        this.filterStreamSubselect = filterStreamSubselect;
    }

    public boolean isFilterStreamSubselect() {
        return filterStreamSubselect;
    }

    public ExprEnumerationEval getExprEvaluatorEnumeration() {
        throw ExprNodeUtilityMake.makeUnsupportedCompileTime();
    }

    public ExprValidationContext getFilterStreamExprValidationContext() {
        return filterStreamExprValidationContext;
    }

    public static ExprSubselectNode[] toArray(List<ExprSubselectNode> subselectNodes) {
        if (subselectNodes.isEmpty()) {
            return EMPTY_SUBSELECT_ARRAY;
        }
        return subselectNodes.toArray(new ExprSubselectNode[subselectNodes.size()]);
    }

    public static enum SubqueryAggregationType {
        NONE,
        FULLY_AGGREGATED_NOPROPS,
        FULLY_AGGREGATED_WPROPS
    }

    static enum SubselectEvaluationType {
        PLAIN,
        GETEVENTCOLL,
        GETSCALARCOLL,
        GETEVENT,
        TYPABLESINGLE,
        TYPABLEMULTI
    }

    private static CodegenExpression makeEvaluate(SubselectEvaluationType evaluationType, ExprSubselectNode subselectNode, EPType resultTypeMayNull, CodegenMethodScope parent, ExprForgeCodegenSymbol symbols, CodegenClassScope classScope) {
        if (resultTypeMayNull == null) {
            return constantNull();
        }
        EPTypeClass resultType = (EPTypeClass) resultTypeMayNull;
        CodegenMethod method = parent.makeChild(resultType, ExprSubselectNode.class, classScope);

        CodegenExpression eps = symbols.getAddEPS(method);
        CodegenExpression newData = symbols.getAddIsNewData(method);
        CodegenExpression evalCtx = symbols.getAddExprEvalCtx(method);

        // get matching events
        CodegenExpression future = classScope.getPackageScope().addOrGetFieldWellKnown(new CodegenFieldNameSubqueryResult(subselectNode.subselectNumber), SubordTableLookupStrategy.EPTYPE);
        CodegenExpression evalMatching = exprDotMethod(future, "lookup", eps, evalCtx);
        method.getBlock().declareVar(EPTypeClassParameterized.from(Collection.class, EventBean.class), NAME_MATCHINGEVENTS, evalMatching);

        // process matching events
        ExprSubselectEvalMatchSymbol evalMatchSymbol = new ExprSubselectEvalMatchSymbol();
        CodegenMethod processMethod = method.makeChildWithScope(resultType, ExprSubselectNode.class, evalMatchSymbol, classScope).addParam(EPTYPE_COLLECTION_EVENTBEAN, NAME_MATCHINGEVENTS).addParam(ExprForgeCodegenNames.PARAMS);
        CodegenExpression process;
        if (evaluationType == PLAIN) {
            process = subselectNode.evalMatchesPlainCodegen(processMethod, evalMatchSymbol, classScope);
        } else if (evaluationType == GETEVENTCOLL) {
            process = subselectNode.evalMatchesGetCollEventsCodegen(processMethod, evalMatchSymbol, classScope);
        } else if (evaluationType == GETSCALARCOLL) {
            process = subselectNode.evalMatchesGetCollScalarCodegen(processMethod, evalMatchSymbol, classScope);
        } else if (evaluationType == GETEVENT) {
            process = subselectNode.evalMatchesGetEventBeanCodegen(processMethod, evalMatchSymbol, classScope);
        } else if (evaluationType == TYPABLESINGLE) {
            process = subselectNode.evalMatchesTypableSingleCodegen(processMethod, evalMatchSymbol, classScope);
        } else if (evaluationType == TYPABLEMULTI) {
            process = subselectNode.evalMatchesTypableMultiCodegen(processMethod, evalMatchSymbol, classScope);
        } else {
            throw new IllegalStateException("Unrecognized evaluation type " + evaluationType);
        }
        evalMatchSymbol.derivedSymbolsCodegen(processMethod, processMethod.getBlock(), classScope);
        processMethod.getBlock().methodReturn(process);

        method.getBlock().methodReturn(localMethod(processMethod, REF_MATCHINGEVENTS, eps, newData, evalCtx));

        return localMethod(method);
    }
}
