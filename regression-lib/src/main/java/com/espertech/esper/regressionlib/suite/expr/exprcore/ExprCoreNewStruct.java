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
package com.espertech.esper.regressionlib.suite.expr.exprcore;

import com.espertech.esper.common.client.FragmentEventType;
import com.espertech.esper.common.client.scopetest.EPAssertionUtil;
import com.espertech.esper.common.internal.avro.support.SupportAvroUtil;
import com.espertech.esper.common.internal.support.EventRepresentationChoice;
import com.espertech.esper.common.internal.support.SupportBean;
import com.espertech.esper.common.internal.util.JavaClassHelper;
import com.espertech.esper.regressionlib.framework.RegressionEnvironment;
import com.espertech.esper.regressionlib.framework.RegressionExecution;
import com.espertech.esper.regressionlib.support.expreval.SupportEvalBuilder;
import org.apache.avro.generic.GenericData;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

public class ExprCoreNewStruct {

    public static Collection<RegressionExecution> executions() {
        List<RegressionExecution> execs = new ArrayList<>();
        execs.add(new ExprCoreNewStructNewWRepresentation());
        execs.add(new ExprCoreNewStructDefaultColumnsAndSODA());
        execs.add(new ExprCoreNewStructNewWithCase());
        execs.add(new ExprCoreNewStructInvalid());
        execs.add(new ExprCoreNewStructWithBacktick());
        return execs;
    }

    private static class ExprCoreNewStructWithBacktick implements RegressionExecution {
        public void run(RegressionEnvironment env) {
            String[] fields = "c0".split(",");
            SupportEvalBuilder builder = new SupportEvalBuilder("SupportBean")
                .expressions(fields, "new { `a` = theString, `b.c` = theString, `}` = theString }");

            builder.assertion(new SupportBean("E1", 0)).verify("c0", actual -> {
                Map<String, Object> c0 = (Map<String, Object>) actual;
                assertEquals("E1", c0.get("a"));
                assertEquals("E1", c0.get("b.c"));
                assertEquals("E1", c0.get("}"));
            });

            builder.run(env);
            env.undeployAll();
        }
    }

    private static class ExprCoreNewStructNewWRepresentation implements RegressionExecution {
        public void run(RegressionEnvironment env) {
            AtomicInteger milestone = new AtomicInteger();
            for (EventRepresentationChoice rep : EventRepresentationChoice.values()) {
                tryAssertionNewWRepresentation(env, rep, milestone);
            }
        }
    }

    private static class ExprCoreNewStructDefaultColumnsAndSODA implements RegressionExecution {
        public void run(RegressionEnvironment env) {
            String epl = "@name('s0') select " +
                "case theString" +
                " when \"A\" then new{theString=\"Q\",intPrimitive,col2=theString||\"A\"}" +
                " when \"B\" then new{theString,intPrimitive=10,col2=theString||\"B\"} " +
                "end as val0 from SupportBean as sb";

            env.compileDeploy(epl).addListener("s0");
            tryAssertionDefault(env);
            env.undeployAll();

            env.eplToModelCompileDeploy(epl).addListener("s0");
            tryAssertionDefault(env);
            env.undeployAll();

            // test to-expression string
            epl = "@name('s0') select " +
                "case theString" +
                " when \"A\" then new{theString=\"Q\",intPrimitive,col2=theString||\"A\" }" +
                " when \"B\" then new{theString,intPrimitive = 10,col2=theString||\"B\" } " +
                "end from SupportBean as sb";
            env.compileDeploy(epl).addListener("s0");
            env.assertStatement("s0", statement -> assertEquals("case theString when \"A\" then new{theString=\"Q\",intPrimitive,col2=theString||\"A\"} when \"B\" then new{theString,intPrimitive=10,col2=theString||\"B\"} end", statement.getEventType().getPropertyNames()[0]));
            env.undeployAll();
        }
    }

    private static void tryAssertionDefault(RegressionEnvironment env) {

        env.assertStatement("s0", statement -> {
            assertEquals(Map.class, statement.getEventType().getPropertyType("val0"));
            FragmentEventType fragType = statement.getEventType().getFragmentType("val0");
            assertFalse(fragType.isIndexed());
            assertFalse(fragType.isNative());
            assertEquals(String.class, fragType.getFragmentType().getPropertyType("theString"));
            assertEquals(Integer.class, fragType.getFragmentType().getPropertyType("intPrimitive"));
            assertEquals(String.class, fragType.getFragmentType().getPropertyType("col2"));
        });

        String[] fieldsInner = "theString,intPrimitive,col2".split(",");
        env.sendEventBean(new SupportBean("E1", 1));
        assertPropsMap(env, fieldsInner, new Object[]{null, null, null});

        env.sendEventBean(new SupportBean("A", 2));
        assertPropsMap(env, fieldsInner, new Object[]{"Q", 2, "AA"});

        env.sendEventBean(new SupportBean("B", 3));
        assertPropsMap(env, fieldsInner, new Object[]{"B", 10, "BB"});
    }

    private static class ExprCoreNewStructNewWithCase implements RegressionExecution {
        public void run(RegressionEnvironment env) {
            AtomicInteger milestone = new AtomicInteger();
            String epl = "@name('s0') select " +
                "case " +
                "  when theString = 'A' then new { col1 = 'X', col2 = 10 } " +
                "  when theString = 'B' then new { col1 = 'Y', col2 = 20 } " +
                "  when theString = 'C' then new { col1 = null, col2 = null } " +
                "  else new { col1 = 'Z', col2 = 30 } " +
                "end as val0 from SupportBean sb";
            tryAssertion(env, epl, milestone);

            epl = "@name('s0') select " +
                "case theString " +
                "  when 'A' then new { col1 = 'X', col2 = 10 } " +
                "  when 'B' then new { col1 = 'Y', col2 = 20 } " +
                "  when 'C' then new { col1 = null, col2 = null } " +
                "  else new{ col1 = 'Z', col2 = 30 } " +
                "end as val0 from SupportBean sb";
            tryAssertion(env, epl, milestone);
        }
    }

    private static class ExprCoreNewStructInvalid implements RegressionExecution {
        public void run(RegressionEnvironment env) {
            String epl;

            epl = "select case when true then new { col1 = 'a' } else 1 end from SupportBean";
            env.tryInvalidCompile(epl, "Failed to validate select-clause expression 'case when true then new{col1=\"a\"} e...(44 chars)': Case node 'when' expressions require that all results either return a single value or a Map-type (new-operator) value, check the else-condition [select case when true then new { col1 = 'a' } else 1 end from SupportBean]");

            epl = "select case when true then new { col1 = 'a' } when false then 1 end from SupportBean";
            env.tryInvalidCompile(epl, "Failed to validate select-clause expression 'case when true then new{col1=\"a\"} w...(55 chars)': Case node 'when' expressions require that all results either return a single value or a Map-type (new-operator) value, check when-condition number 1 [select case when true then new { col1 = 'a' } when false then 1 end from SupportBean]");

            epl = "select case when true then new { col1 = 'a' } else new { col1 = 1 } end from SupportBean";
            env.tryInvalidCompile(epl, "Failed to validate select-clause expression 'case when true then new{col1=\"a\"} e...(54 chars)': Incompatible case-when return types by new-operator in case-when number 1: Type by name 'Case-when number 1' in property 'col1' expected String but receives Integer [select case when true then new { col1 = 'a' } else new { col1 = 1 } end from SupportBean]");

            epl = "select case when true then new { col1 = 'a' } else new { col2 = 'a' } end from SupportBean";
            env.tryInvalidCompile(epl, "Failed to validate select-clause expression 'case when true then new{col1=\"a\"} e...(56 chars)': Incompatible case-when return types by new-operator in case-when number 1: Type by name 'Case-when number 1' in property 'col2' property name not found in target");

            epl = "select case when true then new { col1 = 'a', col1 = 'b' } end from SupportBean";
            env.tryInvalidCompile(epl, "Failed to validate select-clause expression 'case when true then new{col1=\"a\",co...(46 chars)': Failed to validate new-keyword property names, property 'col1' has already been declared [select case when true then new { col1 = 'a', col1 = 'b' } end from SupportBean]");
        }
    }

    private static void tryAssertion(RegressionEnvironment env, String epl, AtomicInteger milestone) {
        env.compileDeploy(epl).addListener("s0").milestone(milestone.getAndIncrement());

        env.assertStatement("s0", statement -> {
            assertEquals(Map.class, statement.getEventType().getPropertyType("val0"));
            FragmentEventType fragType = statement.getEventType().getFragmentType("val0");
            assertFalse(fragType.isIndexed());
            assertFalse(fragType.isNative());
            assertEquals(String.class, fragType.getFragmentType().getPropertyType("col1"));
            assertEquals(Integer.class, fragType.getFragmentType().getPropertyType("col2"));
        });

        String[] fieldsInner = "col1,col2".split(",");
        env.sendEventBean(new SupportBean("E1", 1));
        assertPropsMap(env, fieldsInner, new Object[]{"Z", 30});

        env.sendEventBean(new SupportBean("A", 2));
        assertPropsMap(env, fieldsInner, new Object[]{"X", 10});

        env.sendEventBean(new SupportBean("B", 3));
        assertPropsMap(env, fieldsInner, new Object[]{"Y", 20});

        env.sendEventBean(new SupportBean("C", 4));
        assertPropsMap(env, fieldsInner, new Object[]{null, null});

        env.undeployAll();
    }

    private static void tryAssertionNewWRepresentation(RegressionEnvironment env, EventRepresentationChoice rep, AtomicInteger milestone) {
        String epl = rep.getAnnotationTextWJsonProvided(MyLocalJsonProvided.class) + "@name('s0') select new { theString = 'x' || theString || 'x', intPrimitive = intPrimitive + 2} as val0 from SupportBean as sb";
        env.compileDeploy(epl).addListener("s0").milestone(milestone.getAndIncrement());

        env.assertStatement("s0", statement -> {
            assertEquals(rep.isAvroEvent() ? GenericData.Record.class : Map.class, statement.getEventType().getPropertyType("val0"));
            FragmentEventType fragType = statement.getEventType().getFragmentType("val0");
            if (rep == EventRepresentationChoice.JSONCLASSPROVIDED) {
                assertNull(fragType);
            } else {
                assertFalse(fragType.isIndexed());
                assertFalse(fragType.isNative());
                assertEquals(String.class, fragType.getFragmentType().getPropertyType("theString"));
                assertEquals(Integer.class, JavaClassHelper.getBoxedType(fragType.getFragmentType().getPropertyType("intPrimitive")));
            }
        });

        String[] fieldsInner = "theString,intPrimitive".split(",");
        env.sendEventBean(new SupportBean("E1", -5));
        env.assertEventNew("s0", event -> {
            if (rep.isAvroEvent()) {
                SupportAvroUtil.avroToJson(event);
                GenericData.Record inner = (GenericData.Record) event.get("val0");
                assertEquals("xE1x", inner.get("theString"));
                assertEquals(-3, inner.get("intPrimitive"));
            } else {
                EPAssertionUtil.assertPropsMap((Map) event.get("val0"), fieldsInner, new Object[]{"xE1x", -3});
            }
        });

        env.undeployAll();
    }

    public static class MyLocalJsonProvided implements Serializable {
        public Map<String, Object> val0;
    }

    private static void assertPropsMap(RegressionEnvironment env, String[] fieldsInner, Object[] expecteds) {
        env.assertEventNew("s0", event -> EPAssertionUtil.assertPropsMap((Map) event.get("val0"), fieldsInner, expecteds));
    }
}
