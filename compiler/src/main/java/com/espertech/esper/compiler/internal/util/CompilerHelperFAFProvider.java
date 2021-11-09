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
package com.espertech.esper.compiler.internal.util;

import com.espertech.esper.common.client.EPCompiled;
import com.espertech.esper.common.client.EPCompiledManifest;
import com.espertech.esper.common.internal.compile.compiler.CompilerAbstractionClassCollection;
import com.espertech.esper.common.internal.compile.compiler.CompilerAbstractionCompilationContext;
import com.espertech.esper.common.client.type.EPTypePremade;
import com.espertech.esper.common.client.util.StatementType;
import com.espertech.esper.common.internal.bytecodemodel.base.CodegenClassScope;
import com.espertech.esper.common.internal.bytecodemodel.base.CodegenMethod;
import com.espertech.esper.common.internal.bytecodemodel.base.CodegenPackageScope;
import com.espertech.esper.common.internal.bytecodemodel.base.CodegenSymbolProviderEmpty;
import com.espertech.esper.common.internal.bytecodemodel.core.*;
import com.espertech.esper.common.internal.bytecodemodel.util.CodegenStackGenerator;
import com.espertech.esper.common.internal.bytecodemodel.util.IdentifierUtil;
import com.espertech.esper.common.internal.compile.stage1.Compilable;
import com.espertech.esper.common.internal.compile.stage1.spec.FireAndForgetSpec;
import com.espertech.esper.common.internal.compile.stage1.spec.FireAndForgetSpecDelete;
import com.espertech.esper.common.internal.compile.stage1.spec.FireAndForgetSpecUpdate;
import com.espertech.esper.common.internal.compile.stage1.spec.StatementSpecRaw;
import com.espertech.esper.common.internal.compile.stage2.*;
import com.espertech.esper.common.internal.compile.stage3.ModuleCompileTimeServices;
import com.espertech.esper.common.internal.compile.stage3.StatementCompileTimeServices;
import com.espertech.esper.common.internal.compile.stage3.StatementTypeUtil;
import com.espertech.esper.common.internal.context.compile.ContextCompileTimeDescriptor;
import com.espertech.esper.common.internal.context.compile.ContextMetaData;
import com.espertech.esper.common.internal.context.module.EPStatementInitServices;
import com.espertech.esper.common.internal.context.module.ModuleDependenciesRuntime;
import com.espertech.esper.common.internal.context.query.FAFProvider;
import com.espertech.esper.common.internal.context.util.ContextPropertyRegistry;
import com.espertech.esper.common.internal.epl.annotation.AnnotationUtil;
import com.espertech.esper.common.internal.epl.expression.core.ExprValidationException;
import com.espertech.esper.common.internal.epl.expression.visitor.ExprNodeSubselectDeclaredDotVisitor;
import com.espertech.esper.common.internal.epl.fafquery.querymethod.*;
import com.espertech.esper.common.internal.epl.resultset.core.ResultSetProcessorFactoryProvider;
import com.espertech.esper.common.internal.epl.util.StatementSpecRawWalkerSubselectAndDeclaredDot;
import com.espertech.esper.compiler.client.*;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.espertech.esper.common.internal.bytecodemodel.model.expression.CodegenExpressionBuilder.newInstance;
import static com.espertech.esper.common.internal.bytecodemodel.model.expression.CodegenExpressionBuilder.ref;
import static com.espertech.esper.compiler.internal.util.CompilerHelperModuleProvider.makeInitEventTypesOptional;
import static com.espertech.esper.compiler.internal.util.CompilerHelperStatementProvider.getNameFromAnnotation;
import static com.espertech.esper.compiler.internal.util.CompilerHelperValidator.verifySubstitutionParams;
import static com.espertech.esper.compiler.internal.util.CompilerVersion.COMPILER_VERSION;

public class CompilerHelperFAFProvider {
    private final static String MEMBERNAME_QUERY_METHOD_PROVIDER = "provider";

    public static EPCompiled compile(Compilable compilable, ModuleCompileTimeServices services, CompilerArguments args) throws ExprValidationException, StatementSpecCompileException, EPCompileException {
        StatementCompileTimeServices compileTimeServices = new StatementCompileTimeServices(0, services);
        CompilerHelperSingleResult walkResult = CompilerHelperSingleEPL.parseCompileInlinedClassesWalk(compilable, args.getOptions() == null ? null : args.getOptions().getInlinedClassInspection(), compileTimeServices);
        StatementSpecRaw raw = walkResult.getStatementSpecRaw();

        StatementType statementType = StatementTypeUtil.getStatementType(raw);
        if (statementType != StatementType.SELECT) { // the fire-and-forget spec is null for "select" and populated for I/U/D
            throw new StatementSpecCompileException("Provided EPL expression is a continuous query expression (not an on-demand query)", compilable.toEPL());
        }

        Annotation[] annotations = AnnotationUtil.compileAnnotations(raw.getAnnotations(), services.getClasspathImportServiceCompileTime(), compilable);

        // walk subselects, alias expressions, declared expressions, dot-expressions
        ExprNodeSubselectDeclaredDotVisitor visitor = StatementSpecRawWalkerSubselectAndDeclaredDot.walkSubselectAndDeclaredDotExpr(raw);

        // compile context descriptor
        ContextCompileTimeDescriptor contextDescriptor = null;
        String optionalContextName = raw.getOptionalContextName();
        if (optionalContextName != null) {
            ContextMetaData detail = services.getContextCompileTimeResolver().getContextInfo(optionalContextName);
            if (detail == null) {
                throw new StatementSpecCompileException("Context by name '" + optionalContextName + "' could not be found", compilable.toEPL());
            }
            contextDescriptor = new ContextCompileTimeDescriptor(optionalContextName, detail.getContextModuleName(), detail.getContextVisibility(), new ContextPropertyRegistry(detail), detail.getValidationInfos());
        }

        String statementNameFromAnnotation = getNameFromAnnotation(annotations);
        String statementName = statementNameFromAnnotation == null ? "q0" : statementNameFromAnnotation.trim();
        StatementRawInfo statementRawInfo = new StatementRawInfo(0, statementName, annotations, statementType, contextDescriptor, null, compilable, null);
        StatementSpecCompiledDesc compiledDesc = StatementRawCompiler.compile(raw, compilable, false, true, annotations, visitor.getSubselects(), new ArrayList<>(raw.getTableExpressions()), statementRawInfo, compileTimeServices);
        StatementSpecCompiled specCompiled = compiledDesc.getCompiled();
        FireAndForgetSpec fafSpec = specCompiled.getRaw().getFireAndForgetSpec();

        CompilerAbstractionClassCollection compilerState = services.getCompilerAbstraction().newClassCollection();
        compilerState.add(walkResult.getClassesInlined().getBytes());

        EPCompiledManifest manifest;
        String classPostfix = IdentifierUtil.getIdentifierMayStartNumeric(statementName);

        FAFQueryMethodForge query;
        if (specCompiled.getRaw().getInsertIntoDesc() != null) {
            query = new FAFQueryMethodIUDInsertIntoForge(specCompiled, compilable, statementRawInfo, compileTimeServices);
        } else if (fafSpec == null) {   // null indicates a select-statement, same as continuous query
            FAFQueryMethodSelectDesc desc = new FAFQueryMethodSelectDesc(specCompiled, compilable, statementRawInfo, compileTimeServices);
            String classNameResultSetProcessor = CodeGenerationIDGenerator.generateClassNameSimple(ResultSetProcessorFactoryProvider.class, classPostfix);
            query = new FAFQueryMethodSelectForge(desc, classNameResultSetProcessor, statementRawInfo, services);
        } else if (fafSpec instanceof FireAndForgetSpecDelete) {
            query = new FAFQueryMethodIUDDeleteForge(specCompiled, compilable, statementRawInfo, compileTimeServices);
        } else if (fafSpec instanceof FireAndForgetSpecUpdate) {
            query = new FAFQueryMethodIUDUpdateForge(specCompiled, compilable, statementRawInfo, compileTimeServices);
        } else {
            throw new IllegalStateException("Unrecognized FAF code " + fafSpec);
        }

        // verify substitution parameters
        verifySubstitutionParams(raw.getSubstitutionParameters());

        try {
            manifest = compileToBytes(query, classPostfix, compilerState, services, args.getPath());
        } catch (EPCompileException ex) {
            throw ex;
        } catch (Throwable t) {
            throw new EPCompileException("Unexpected exception compiling module: " + t.getMessage(), t, Collections.emptyList());
        }

        return new EPCompiled(compilerState.getClasses(), manifest);
    }

    private static EPCompiledManifest compileToBytes(FAFQueryMethodForge query, String classPostfix, CompilerAbstractionClassCollection compilerState, ModuleCompileTimeServices compileTimeServices, CompilerPath path) throws EPCompileException {

        String queryMethodProviderClassName;
        try {
            queryMethodProviderClassName = CompilerHelperFAFQuery.compileQuery(query, classPostfix, compilerState, compileTimeServices, path);
        } catch (StatementSpecCompileException ex) {
            EPCompileExceptionItem first;
            if (ex instanceof StatementSpecCompileSyntaxException) {
                first = new EPCompileExceptionSyntaxItem(ex.getMessage(), ex, ex.getExpression(), -1);
            } else {
                first = new EPCompileExceptionItem(ex.getMessage(), ex, ex.getExpression(), -1);
            }
            List<EPCompileExceptionItem> items = Collections.singletonList(first);
            throw new EPCompileException(ex.getMessage() + " [" + ex.getExpression() + "]", ex, items);
        }

        // compile query provider
        String fafProviderClassName = makeFAFProvider(queryMethodProviderClassName, classPostfix, compilerState, compileTimeServices, path);

        // create manifest
        return new EPCompiledManifest(COMPILER_VERSION, null, fafProviderClassName, false);
    }

    private static String makeFAFProvider(String queryMethodProviderClassName, String classPostfix, CompilerAbstractionClassCollection compilerState, ModuleCompileTimeServices compileTimeServices, CompilerPath path) {
        CodegenPackageScope packageScope = new CodegenPackageScope(compileTimeServices.getPackageName(), null, compileTimeServices.isInstrumented(), compileTimeServices.getConfiguration().getCompiler().getByteCode());
        String fafProviderClassName = CodeGenerationIDGenerator.generateClassNameSimple(FAFProvider.class, classPostfix);
        CodegenClassScope classScope = new CodegenClassScope(true, packageScope, fafProviderClassName);
        CodegenClassMethods methods = new CodegenClassMethods();

        // provide module dependencies
        CodegenMethod getModuleDependenciesMethod = CodegenMethod.makeParentNode(ModuleDependenciesRuntime.EPTYPE, EPCompilerImpl.class, CodegenSymbolProviderEmpty.INSTANCE, classScope);
        compileTimeServices.getModuleDependencies().make(getModuleDependenciesMethod, classScope);

        // initialize-event-types
        CodegenMethod initializeEventTypesMethod = makeInitEventTypesOptional(classScope, compileTimeServices);

        // initialize-query
        CodegenMethod initializeQueryMethod = CodegenMethod.makeParentNode(EPTypePremade.VOID.getEPType(), EPCompilerImpl.class, CodegenSymbolProviderEmpty.INSTANCE, classScope).addParam(EPStatementInitServices.EPTYPE, EPStatementInitServices.REF.getRef());
        initializeQueryMethod.getBlock().assignMember(MEMBERNAME_QUERY_METHOD_PROVIDER, newInstance(queryMethodProviderClassName, EPStatementInitServices.REF));

        // get-execute
        CodegenMethod getQueryMethodProviderMethod = CodegenMethod.makeParentNode(FAFQueryMethodProvider.EPTYPE, EPCompilerImpl.class, CodegenSymbolProviderEmpty.INSTANCE, classScope);
        getQueryMethodProviderMethod.getBlock().methodReturn(ref(MEMBERNAME_QUERY_METHOD_PROVIDER));

        // build stack
        CodegenStackGenerator.recursiveBuildStack(getModuleDependenciesMethod, "getModuleDependencies", methods);
        if (initializeEventTypesMethod != null) {
            CodegenStackGenerator.recursiveBuildStack(initializeEventTypesMethod, "initializeEventTypes", methods);
        }
        CodegenStackGenerator.recursiveBuildStack(initializeQueryMethod, "initializeQuery", methods);
        CodegenStackGenerator.recursiveBuildStack(getQueryMethodProviderMethod, "getQueryMethodProvider", methods);

        List<CodegenTypedParam> members = new ArrayList<>();
        members.add(new CodegenTypedParam(FAFQueryMethodProvider.EPTYPE, MEMBERNAME_QUERY_METHOD_PROVIDER).setFinal(false));

        CodegenClass clazz = new CodegenClass(CodegenClassType.FAFPROVIDER, FAFProvider.EPTYPE, fafProviderClassName, classScope, members, null, methods, Collections.emptyList());
        CompilerAbstractionCompilationContext context = new CompilerAbstractionCompilationContext(compileTimeServices, path.getCompileds());
        compileTimeServices.getCompilerAbstraction().compileClasses(Collections.singletonList(clazz), context, compilerState);

        return CodeGenerationIDGenerator.generateClassNameWithPackage(compileTimeServices.getPackageName(), FAFProvider.class, classPostfix);
    }
}
