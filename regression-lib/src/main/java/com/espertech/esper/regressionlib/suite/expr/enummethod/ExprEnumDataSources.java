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
package com.espertech.esper.regressionlib.suite.expr.enummethod;

import com.espertech.esper.common.client.EPCompiled;
import com.espertech.esper.common.client.EventBean;
import com.espertech.esper.common.client.scopetest.EPAssertionUtil;
import com.espertech.esper.common.client.type.EPTypeClass;
import com.espertech.esper.common.client.type.EPTypeClassParameterized;
import com.espertech.esper.common.client.type.EPTypePremade;
import com.espertech.esper.common.internal.support.SupportBean;
import com.espertech.esper.common.internal.support.SupportBean_S0;
import com.espertech.esper.common.internal.util.CollectionUtil;
import com.espertech.esper.regressionlib.framework.RegressionEnvironment;
import com.espertech.esper.regressionlib.framework.RegressionExecution;
import com.espertech.esper.regressionlib.framework.RegressionFlag;
import com.espertech.esper.regressionlib.framework.RegressionPath;
import com.espertech.esper.regressionlib.support.bean.*;
import com.espertech.esper.regressionlib.support.bookexample.BookDesc;
import com.espertech.esper.regressionlib.support.client.SupportPortableDeploySubstitutionParams;
import com.espertech.esper.regressionlib.support.util.LambdaAssertionUtil;
import com.espertech.esper.runtime.client.DeploymentOptions;

import java.io.Serializable;
import java.util.*;

import static junit.framework.TestCase.fail;
import static org.junit.Assert.*;

public class ExprEnumDataSources {

    public static Collection<RegressionExecution> executions() {
        ArrayList<RegressionExecution> execs = new ArrayList<>();
        execs.add(new ExprEnumProperty());
        execs.add(new ExprEnumSubstitutionParameter());
        execs.add(new ExprEnumEnumObject());
        execs.add(new ExprEnumSortedMaxMinBy());
        execs.add(new ExprEnumJoin());
        execs.add(new ExprEnumPrevWindowSorted());
        execs.add(new ExprEnumNamedWindow());
        execs.add(new ExprEnumSubselect());
        execs.add(new ExprEnumAccessAggregation());
        execs.add(new ExprEnumPrevFuncs());
        execs.add(new ExprEnumUDFStaticMethod());
        execs.add(new ExprEnumPropertySchema());
        execs.add(new ExprEnumPropertyInsertIntoAtEventBean());
        execs.add(new ExprEnumPatternInsertIntoAtEventBean());
        execs.add(new ExprEnumPatternFilter());
        execs.add(new ExprEnumVariable());
        execs.add(new ExprEnumTableRow());
        execs.add(new ExprEnumMatchRecognizeDefine());
        execs.add(new ExprEnumMatchRecognizeMeasures(false));
        execs.add(new ExprEnumMatchRecognizeMeasures(true));
        execs.add(new ExprEnumCast());
        execs.add(new ExprEnumPropertyGenericComponentType());
        execs.add(new ExprEnumUDFStaticMethodGeneric());
        execs.add(new ExprEnumSubqueryGenericComponentType());
        execs.add(new ExprEnumBeanWithMap());
        execs.add(new ExprEnumContextPropUnnested());
        execs.add(new ExprEnumContextPropNested());
        return execs;
    }

    private static class ExprEnumContextPropUnnested implements RegressionExecution {
        public void run(RegressionEnvironment env) {
            String epl = "@public @buseventtype create schema MyLocalEventWithInts as " + MyLocalEventWithInts.class.getName() + ";\n" +
                "@public create context MyContext start MyLocalEventWithInts as mle end SupportBean_S0;\n" +
                "@name('s0') context MyContext select \n" +
                "  context.mle.myFunc() as c0,\n" +
                "  context.mle.intValues.anyOf(i => i.intValue() > 0) as c1,\n" +
                "  context.mle.intValues.where(i => i.intValue() > 0).countOf() > 0 as c2\n" +
                "  from SupportBean\n";
            env.compileDeploy(epl).addListener("s0");

            sendAssert(env, true, 0, 1);
            sendAssert(env, false, 0, 0);

            env.undeployAll();
        }

        private void sendAssert(RegressionEnvironment env, boolean expected, Integer... intValues) {
            env.sendEventBean(new MyLocalEventWithInts(new HashSet<>(Arrays.asList(intValues))));
            env.sendEventBean(new SupportBean());
            env.assertPropsNew("s0", new String[]{"c0", "c1", "c2"}, new Object[]{expected, expected, expected});
            env.sendEventBean(new SupportBean_S0(0));
        }
    }

    private static class ExprEnumContextPropNested implements RegressionExecution {
        public void run(RegressionEnvironment env) {
            String epl = "@public @buseventtype create schema MyLocalEventWithInts as " + MyLocalEventWithInts.class.getName() + ";\n" +
                "@public create context MyContext " +
                "  context ACtx start MyLocalEventWithInts as mle end SupportBean_S0,\n" +
                "  context BCtx start SupportBean\n" +
                ";\n" +
                "@name('s0') context MyContext select \n" +
                "  context.ACtx.mle.myFunc() as c0,\n" +
                "  context.ACtx.mle.intValues.anyOf(i => i.intValue() > 0) as c1,\n" +
                "  context.ACtx.mle.intValues.where(i => i.intValue() > 0).countOf() > 0 as c2\n" +
                "  from SupportBean\n";
            env.compileDeploy(epl).addListener("s0");

            sendAssert(env, true, 0, 1);
            sendAssert(env, false, 0, 0);

            env.undeployAll();
        }

        private void sendAssert(RegressionEnvironment env, boolean expected, Integer... intValues) {
            env.sendEventBean(new MyLocalEventWithInts(new HashSet<>(Arrays.asList(intValues))));
            env.sendEventBean(new SupportBean());
            env.assertPropsNew("s0", new String[]{"c0", "c1", "c2"}, new Object[]{expected, expected, expected});
            env.sendEventBean(new SupportBean_S0(0));
        }
    }

    private static class ExprEnumBeanWithMap implements RegressionExecution {
        public void run(RegressionEnvironment env) {
            String epl = "@public @buseventtype create schema MyEvent as " + SupportEventWithMapOfCollOfString.class.getName() + ";\n" +
                "@name('s0') select * from MyEvent(mymap('a').anyOf(x -> x = 'x'));\n";
            env.compileDeploy(epl).addListener("s0");

            sendAssert(env, "a", Collections.emptyList(), false);
            sendAssert(env, "a", Arrays.asList("a", "b"), false);
            sendAssert(env, "a", Arrays.asList("a", "x"), true);
            sendAssert(env, "b", Arrays.asList("a", "x"), false);

            env.undeployAll();
        }

        private void sendAssert(RegressionEnvironment env, String mapKey, List<String> values, boolean received) {
            env.sendEventBean(new SupportEventWithMapOfCollOfString(mapKey, values), "MyEvent");
            env.assertListenerInvokedFlag("s0", received);
        }
    }

    private static class ExprEnumSubqueryGenericComponentType implements RegressionExecution {
        public void run(RegressionEnvironment env) {
            String epl = "@public @buseventType create schema MyEvent as (item Optional<Integer>);\n" +
                "@name('s0') select (select item from MyEvent#keepall).sumOf(v => v.get()) as c0 from SupportBean;\n";
            env.compileDeploy(epl).addListener("s0");

            sendEvent(env, 10);
            sendEvent(env, -2);
            env.sendEventBean(new SupportBean());
            env.assertPropsNew("s0", "c0".split(","), new Object[]{8});

            env.undeployAll();
        }

        public EnumSet<RegressionFlag> flags() {
            return EnumSet.of(RegressionFlag.SERDEREQUIRED);
        }

        private void sendEvent(RegressionEnvironment env, int i) {
            env.sendEventMap(Collections.singletonMap("item", Optional.of(i)), "MyEvent");
        }
    }

    public static class ExprEnumUDFStaticMethodGeneric implements RegressionExecution {
        public void run(RegressionEnvironment env) {
            String epl = "@name('s0') select " + this.getClass().getName() + ".doit().sumOf(v => v.get()) as c0 from SupportBean;";
            env.compileDeploy(epl).addListener("s0");

            env.sendEventBean(new SupportBean());
            env.assertEqualsNew("s0", "c0", 30);

            env.undeployAll();
        }

        public static Collection<Optional<Integer>> doit() {
            return Arrays.asList(Optional.of(10), Optional.of(20));
        }
    }

    private static class ExprEnumPropertyGenericComponentType implements RegressionExecution {
        public void run(RegressionEnvironment env) {
            String epl = "@public @buseventType create schema MyEvent as (arrayOfOptionalInt Optional<Integer>[], listOfOptionalInt List<Optional<Integer>>);\n" +
                "@name('s0') select arrayOfOptionalInt.sumOf(v => v.get()) as c0, arrayOfOptionalInt.where(v => v.get() > 0).sumOf(v => v.get()) as c1," +
                "listOfOptionalInt.sumOf(v => v.get()) as c2, listOfOptionalInt.where(v => v.get() > 0).sumOf(v => v.get()) as c3 from MyEvent;\n";
            env.compileDeploy(epl).addListener("s0");

            Map<String, Object> event = new HashMap<>();
            event.put("arrayOfOptionalInt", makeOptional(10, -1));
            event.put("listOfOptionalInt", Arrays.asList(makeOptional(5, -2)));
            env.sendEventMap(event, "MyEvent");
            env.assertPropsNew("s0", "c0,c1,c2,c3".split(","), new Object[]{9, 10, 3, 5});

            env.undeployAll();
        }

        private Optional<Integer>[] makeOptional(int first, int second) {
            return (Optional<Integer>[]) new Optional[]{Optional.of(first), Optional.of(second)};
        }

        public EnumSet<RegressionFlag> flags() {
            return EnumSet.of(RegressionFlag.SERDEREQUIRED);
        }
    }

    private static class ExprEnumCast implements RegressionExecution {
        public void run(RegressionEnvironment env) {
            String epl = "@public @buseventtype create schema MyLocalEvent as " + MyLocalEvent.class.getName() + ";\n" +
                "@name('s0') select cast(value.someCollection?, java.util.Collection).countOf() as cnt from MyLocalEvent";
            env.compileDeploy(epl).addListener("s0");

            env.sendEventBean(new MyLocalEvent(new MyLocalWithCollection(Arrays.asList("a", "b"))));
            env.assertEqualsNew("s0", "cnt", 2);

            env.undeployAll();
        }
    }

    private static class ExprEnumPropertySchema implements RegressionExecution {
        public void run(RegressionEnvironment env) {
            String epl = "@public @buseventtype create schema OrderDetail(itemId string);\n" +
                "@public @buseventtype create schema OrderEvent(details OrderDetail[]);\n" +
                "@name('s0') select details.where(i => i.itemId = '001') as c0 from OrderEvent;\n";
            env.compileDeploy(epl, new RegressionPath()).addListener("s0");

            Map<String, Object> detailOne = CollectionUtil.populateNameValueMap("itemId", "002");
            Map<String, Object> detailTwo = CollectionUtil.populateNameValueMap("itemId", "001");
            env.sendEventMap(CollectionUtil.populateNameValueMap("details", new Map[]{detailOne, detailTwo}), "OrderEvent");

            env.assertEventNew("s0", event -> {
                Collection c = (Collection) event.get("c0");
                EPAssertionUtil.assertEqualsExactOrder(c.toArray(), new Map[]{detailTwo});
            });

            env.undeployAll();
        }
    }

    private static class ExprEnumPropertyInsertIntoAtEventBean implements RegressionExecution {
        public void run(RegressionEnvironment env) {
            String epl = "@public @buseventtype create objectarray schema StockTick(id string, price int);\n" +
                "insert into TicksLarge select window(*).where(e => e.price > 100) @eventbean as ticksLargePrice\n" +
                "from StockTick#time(10) having count(*) > 2;\n" +
                "@name('s0') select ticksLargePrice.where(e => e.price < 200) as ticksLargeLess200 from TicksLarge;\n";
            env.compileDeploy(epl, new RegressionPath()).addListener("s0");

            env.sendEventObjectArray(new Object[]{"E1", 90}, "StockTick");
            env.sendEventObjectArray(new Object[]{"E2", 120}, "StockTick");
            env.sendEventObjectArray(new Object[]{"E3", 95}, "StockTick");

            env.assertEventNew("s0", event -> assertEquals(1, ((Collection) event.get("ticksLargeLess200")).size()));

            env.undeployAll();
        }
    }

    private static class ExprEnumPatternInsertIntoAtEventBean implements RegressionExecution {
        public void run(RegressionEnvironment env) {
            String epl = "@public @buseventtype create schema MyEvent(id string, value int);\n" +
                "insert into StreamWithAll select * from pattern[[4] me=MyEvent];\n" +
                "insert into StreamGreaterZero select me.where(v => v.value>0) @eventbean as megt from StreamWithAll;\n" +
                "insert into StreamLessThenTen select megt.where(v => v.value<10) @eventbean as melt from StreamGreaterZero;\n" +
                "@name('s0') select * from StreamLessThenTen;\n";
            env.compileDeploy(epl).addListener("s0");

            Map<String, Object> e1 = sendEvent(env, "E1", 1);
            sendEvent(env, "E2", -1);
            sendEvent(env, "E3", 11);
            Map<String, Object> e4 = sendEvent(env, "E4", 4);

            env.assertEventNew("s0", event -> {
                Map<String, Object> result = (Map<String, Object>) event.getUnderlying();
                EventBean[] events = (EventBean[]) result.get("melt");
                assertSame(e1, events[0].getUnderlying());
                assertSame(e4, events[1].getUnderlying());
            });

            env.undeployAll();
        }

        private Map<String, Object> sendEvent(RegressionEnvironment env, String id, int value) {
            Map<String, Object> event = CollectionUtil.buildMap("id", id, "value", value);
            env.sendEventMap(event, "MyEvent");
            return event;
        }
    }

    private static class ExprEnumMatchRecognizeMeasures implements RegressionExecution {
        private final boolean select;

        public ExprEnumMatchRecognizeMeasures(boolean select) {
            this.select = select;
        }

        public void run(RegressionEnvironment env) {
            String epl;
            if (!select) {
                epl = "select ids from SupportBean match_recognize ( " +
                    "  measures A.selectFrom(o -> o.theString) as ids ";
            } else {
                epl = "select a.selectFrom(o -> o.theString) as ids from SupportBean match_recognize (measures A as a ";
            }
            epl = "@name('s0') " + epl + " pattern (A{3}) define A as A.intPrimitive = 1)";
            env.compileDeploy(epl).addListener("s0");

            env.sendEventBean(new SupportBean("E1", 1));
            env.sendEventBean(new SupportBean("E2", 1));
            env.assertListenerNotInvoked("s0");

            env.sendEventBean(new SupportBean("E3", 1));
            env.assertEventNew("s0", event -> assertColl("E1,E2,E3", event.get("ids")));

            env.sendEventBean(new SupportBean("E4", 1));
            env.sendEventBean(new SupportBean("E5", 1));
            env.assertListenerNotInvoked("s0");

            env.sendEventBean(new SupportBean("E6", 1));
            env.assertEventNew("s0", event -> assertColl("E4,E5,E6", event.get("ids")));

            env.undeployAll();
        }

        public String name() {
            return this.getClass().getSimpleName() + "{" +
                "select=" + select +
                '}';
        }
    }

    private static class ExprEnumSubstitutionParameter implements RegressionExecution {
        public void run(RegressionEnvironment env) {
            trySubstitutionParameter(env, "?::int[primitive]", new int[]{1, 10, 100});
            trySubstitutionParameter(env, "?::java.lang.Object[]", new Object[]{1, 10, 100});
            trySubstitutionParameter(env, "?::Integer[]", new Integer[]{1, 10, 100});
        }
    }

    private static class ExprEnumTableRow implements RegressionExecution {
        public void run(RegressionEnvironment env) {
            // test table access expression
            RegressionPath path = new RegressionPath();
            env.compileDeploy("@public create table MyTableUnkeyed(theWindow window(*) @type(SupportBean))", path);
            env.compileDeploy("into table MyTableUnkeyed select window(*) as theWindow from SupportBean#time(30)", path);
            env.sendEventBean(new SupportBean("E1", 10));
            env.sendEventBean(new SupportBean("E2", 20));

            env.compileDeploy("@name('s0')select MyTableUnkeyed.theWindow.anyOf(v=>intPrimitive=10) as c0 from SupportBean_A", path);
            env.addListener("s0");

            env.sendEventBean(new SupportBean_A("A0"));
            env.assertEqualsNew("s0", "c0", true);

            env.undeployAll();
        }
    }

    private static class ExprEnumPatternFilter implements RegressionExecution {
        public void run(RegressionEnvironment env) {
            String epl = "@name('s0') select * from pattern [ ([2]a=SupportBean_ST0) -> b=SupportBean(intPrimitive > a.max(i -> p00))]";
            env.compileDeploy(epl).addListener("s0");

            env.sendEventBean(new SupportBean_ST0("E1", 10));
            env.sendEventBean(new SupportBean_ST0("E2", 15));
            env.sendEventBean(new SupportBean("E3", 15));
            env.assertListenerNotInvoked("s0");

            env.sendEventBean(new SupportBean("E4", 16));
            env.assertPropsNew("s0", "a[0].id,a[1].id,b.theString".split(","), new Object[]{"E1", "E2", "E4"});
            env.undeployAll();

            env.compileDeploy("@name('s0') select * from pattern [ a=SupportBean_ST0 until b=SupportBean -> c=SupportBean(intPrimitive > a.sumOf(i => p00))]");
            env.addListener("s0");

            env.sendEventBean(new SupportBean_ST0("E10", 10));
            env.sendEventBean(new SupportBean_ST0("E11", 15));
            env.sendEventBean(new SupportBean("E12", -1));
            env.sendEventBean(new SupportBean("E13", 25));
            env.assertListenerNotInvoked("s0");

            env.sendEventBean(new SupportBean("E14", 26));
            env.assertPropsNew("s0", "a[0].id,a[1].id,b.theString,c.theString".split(","), new Object[]{"E10", "E11", "E12", "E14"});

            env.undeployAll();
        }
    }

    private static class ExprEnumMatchRecognizeDefine implements RegressionExecution {
        public void run(RegressionEnvironment env) {

            // try define-clause
            String[] fieldsOne = "a_array[0].theString,a_array[1].theString,b.theString".split(",");
            String textOne = "@name('s0') select * from SupportBean " +
                "match_recognize (" +
                " measures A as a_array, B as b " +
                " pattern (A* B)" +
                " define" +
                " B as A.anyOf(v=> v.intPrimitive = B.intPrimitive)" +
                ")";
            env.compileDeploy(textOne).addListener("s0");
            env.sendEventBean(new SupportBean("A1", 10));
            env.sendEventBean(new SupportBean("A2", 20));
            env.sendEventBean(new SupportBean("A3", 20));
            env.assertPropsNew("s0", fieldsOne, new Object[]{"A1", "A2", "A3"});

            env.sendEventBean(new SupportBean("A4", 1));
            env.sendEventBean(new SupportBean("A5", 2));
            env.sendEventBean(new SupportBean("A6", 3));
            env.assertListenerNotInvoked("s0");

            env.sendEventBean(new SupportBean("A7", 2));
            env.assertPropsNew("s0", fieldsOne, new Object[]{"A4", "A5", "A7"});
            env.undeployAll();

            // try measures-clause
            String[] fieldsTwo = "c0".split(",");
            String textTwo = "@name('s0') select * from SupportBean " +
                "match_recognize (" +
                " measures A.anyOf(v=> v.intPrimitive = B.intPrimitive) as c0 " +
                " pattern (A* B)" +
                " define" +
                " A as A.theString like 'A%'," +
                " B as B.theString like 'B%'" +
                ")";
            env.compileDeploy(textTwo).addListener("s0");

            env.sendEventBean(new SupportBean("A1", 10));
            env.sendEventBean(new SupportBean("A2", 20));
            env.assertListenerNotInvoked("s0");
            env.sendEventBean(new SupportBean("B1", 20));
            env.assertPropsNew("s0", fieldsTwo, new Object[]{true});

            env.sendEventBean(new SupportBean("A1", 10));
            env.sendEventBean(new SupportBean("A2", 20));
            env.sendEventBean(new SupportBean("B1", 15));
            env.assertPropsNew("s0", fieldsTwo, new Object[]{false});

            env.undeployAll();
        }
    }

    private static class ExprEnumEnumObject implements RegressionExecution {
        public void run(RegressionEnvironment env) {
            String[] fields = "c0,c1".split(",");
            String epl = "@name('s0') select " +
                "SupportEnumTwo.ENUM_VALUE_1.getMystrings().anyOf(v => v = id) as c0, " +
                "value.getMystrings().anyOf(v => v = '2') as c1 " +
                "from SupportEnumTwoEvent";
            env.compileDeploy(epl).addListener("s0");

            env.sendEventBean(new SupportEnumTwoEvent("0", SupportEnumTwo.ENUM_VALUE_1));
            env.assertPropsNew("s0", fields, new Object[]{true, false});

            env.sendEventBean(new SupportEnumTwoEvent("2", SupportEnumTwo.ENUM_VALUE_2));
            env.assertPropsNew("s0", fields, new Object[]{false, true});

            env.undeployAll();
        }
    }

    private static class ExprEnumSortedMaxMinBy implements RegressionExecution {
        public void run(RegressionEnvironment env) {
            String[] fields = "c0,c1,c2,c3,c4".split(",");

            String eplWindowAgg = "@name('s0') select " +
                "sorted(theString).allOf(x => x.intPrimitive < 5) as c0," +
                "maxby(theString).allOf(x => x.intPrimitive < 5) as c1," +
                "minby(theString).allOf(x => x.intPrimitive < 5) as c2," +
                "maxbyever(theString).allOf(x => x.intPrimitive < 5) as c3," +
                "minbyever(theString).allOf(x => x.intPrimitive < 5) as c4" +
                " from SupportBean#length(5)";
            env.compileDeploy(eplWindowAgg).addListener("s0");

            env.sendEventBean(new SupportBean("E1", 1));
            env.assertPropsNew("s0", fields, new Object[]{true, true, true, true, true});

            env.undeployAll();
        }
    }

    private static class ExprEnumJoin implements RegressionExecution {
        public void run(RegressionEnvironment env) {

            String epl = "@name('s0') select * from SupportSelectorEvent#keepall as sel, SupportContainerEvent#keepall as cont " +
                "where cont.items.anyOf(i => sel.selector = i.selected)";
            env.compileDeploy(epl).addListener("s0");

            env.sendEventBean(new SupportSelectorEvent("S1", "sel1"));
            env.sendEventBean(new SupportContainerEvent("C1", new SupportContainedItem("I1", "sel1")));
            env.assertListenerInvoked("s0");

            env.undeployAll();
        }
    }

    private static class ExprEnumPrevWindowSorted implements RegressionExecution {
        public void run(RegressionEnvironment env) {
            String epl = "@name('s0') select prevwindow(st0) as val0, prevwindow(st0).esperInternalNoop() as val1 " +
                "from SupportBean_ST0#sort(3, p00 asc) as st0";
            env.compileDeploy(epl).addListener("s0");

            env.assertStmtTypes("s0", "val0,val1".split(","), new EPTypeClass[]{new EPTypeClass(SupportBean_ST0[].class),
                EPTypeClassParameterized.from(Collection.class, SupportBean_ST0.class)});

            env.sendEventBean(new SupportBean_ST0("E1", 5));
            LambdaAssertionUtil.assertST0IdWReset(env, "val1", "E1");

            env.sendEventBean(new SupportBean_ST0("E2", 6));
            LambdaAssertionUtil.assertST0IdWReset(env, "val1", "E1,E2");

            env.sendEventBean(new SupportBean_ST0("E3", 4));
            LambdaAssertionUtil.assertST0IdWReset(env, "val1", "E3,E1,E2");

            env.sendEventBean(new SupportBean_ST0("E5", 3));
            LambdaAssertionUtil.assertST0IdWReset(env, "val1", "E5,E3,E1");
            env.undeployAll();

            // Scalar version
            String[] fields = new String[]{"val0"};
            String stmtScalar = "@name('s0') select prevwindow(id).where(x => x not like '%ignore%') as val0 " +
                "from SupportBean_ST0#keepall as st0";
            env.compileDeploy(stmtScalar).addListener("s0");
            env.assertStmtTypes("s0", fields, new EPTypeClass[]{EPTypeClassParameterized.from(Collection.class, String.class)});

            env.sendEventBean(new SupportBean_ST0("E1", 5));
            LambdaAssertionUtil.assertValuesArrayScalarWReset(env, "val0", "E1");

            env.sendEventBean(new SupportBean_ST0("E2ignore", 6));
            LambdaAssertionUtil.assertValuesArrayScalarWReset(env, "val0", "E1");

            env.sendEventBean(new SupportBean_ST0("E3", 4));
            LambdaAssertionUtil.assertValuesArrayScalarWReset(env, "val0", "E3", "E1");

            env.sendEventBean(new SupportBean_ST0("ignoreE5", 3));
            LambdaAssertionUtil.assertValuesArrayScalarWReset(env, "val0", "E3", "E1");

            env.undeployAll();
        }
    }

    private static class ExprEnumNamedWindow implements RegressionExecution {
        public void run(RegressionEnvironment env) {
            RegressionPath path = new RegressionPath();
            String epl = "@public create window MyWindow#keepall as SupportBean_ST0;\n" +
                "on SupportBean_A delete from MyWindow;\n" +
                "insert into MyWindow select * from SupportBean_ST0;\n";
            env.compileDeploy(epl, path);

            env.compileDeploy("@name('s0') select MyWindow.allOf(x => x.p00 < 5) as allOfX from SupportBean#keepall", path);
            env.addListener("s0");
            env.assertStmtTypes("s0", "allOfX".split(","), new EPTypeClass[]{EPTypePremade.BOOLEANBOXED.getEPType()});

            env.sendEventBean(new SupportBean("E1", 1));
            env.assertEqualsNew("s0", "allOfX", null);

            env.sendEventBean(new SupportBean_ST0("ST0", "1", 10));
            env.sendEventBean(new SupportBean("E2", 10));
            env.assertEqualsNew("s0", "allOfX", false);

            env.undeployModuleContaining("s0");
            env.sendEventBean(new SupportBean_A("A1"));

            // test named window correlated
            String eplNamedWindowCorrelated = "@name('s0') select MyWindow(key0 = sb.theString).allOf(x => x.p00 < 5) as allOfX from SupportBean#keepall sb";
            env.compileDeploy(eplNamedWindowCorrelated, path).addListener("s0");

            env.sendEventBean(new SupportBean("E1", 1));
            env.assertEqualsNew("s0", "allOfX", null);

            env.sendEventBean(new SupportBean_ST0("E2", "KEY1", 1));
            env.sendEventBean(new SupportBean("E2", 0));
            env.assertEqualsNew("s0", "allOfX", null);

            env.sendEventBean(new SupportBean("KEY1", 0));
            env.assertEqualsNew("s0", "allOfX", true);

            env.undeployAll();
        }
    }

    private static class ExprEnumSubselect implements RegressionExecution {
        public void run(RegressionEnvironment env) {

            // test subselect-wildcard
            String eplSubselect = "@name('s0') select (select * from SupportBean_ST0#keepall).allOf(x => x.p00 < 5) as allOfX from SupportBean#keepall";
            env.compileDeploy(eplSubselect).addListener("s0");

            env.sendEventBean(new SupportBean_ST0("ST0", "1", 0));
            env.sendEventBean(new SupportBean("E1", 1));
            env.assertEqualsNew("s0", "allOfX", true);

            env.sendEventBean(new SupportBean_ST0("ST0", "1", 10));
            env.sendEventBean(new SupportBean("E2", 2));
            env.assertEqualsNew("s0", "allOfX", false);
            env.undeployAll();

            // test subselect scalar return
            String eplSubselectScalar = "@name('s0') select (select id from SupportBean_ST0#keepall).allOf(x => x  like '%B%') as allOfX from SupportBean#keepall";
            env.compileDeploy(eplSubselectScalar).addListener("s0");

            env.sendEventBean(new SupportBean_ST0("B1", 0));
            env.sendEventBean(new SupportBean("E1", 1));
            env.assertEqualsNew("s0", "allOfX", true);

            env.sendEventBean(new SupportBean_ST0("A1", 0));
            env.sendEventBean(new SupportBean("E2", 2));
            env.assertEqualsNew("s0", "allOfX", false);
            env.undeployAll();

            // test subselect-correlated scalar return
            String eplSubselectScalarCorrelated = "@name('s0') select (select key0 from SupportBean_ST0#keepall st0 where st0.id = sb.theString).allOf(x => x  like '%hello%') as allOfX from SupportBean#keepall sb";
            env.compileDeploy(eplSubselectScalarCorrelated).addListener("s0");

            env.sendEventBean(new SupportBean_ST0("A1", "hello", 0));
            env.sendEventBean(new SupportBean("E1", 1));
            env.assertEqualsNew("s0", "allOfX", null);

            env.sendEventBean(new SupportBean_ST0("A2", "hello", 0));
            env.sendEventBean(new SupportBean("A2", 1));
            env.assertEqualsNew("s0", "allOfX", true);

            env.sendEventBean(new SupportBean_ST0("A3", "test", 0));
            env.sendEventBean(new SupportBean("A3", 1));
            env.assertEqualsNew("s0", "allOfX", false);
            env.undeployAll();

            // test subselect multivalue return
            String[] fields = new String[]{"id", "p00"};
            String eplSubselectMultivalue = "@name('s0') select (select id, p00 from SupportBean_ST0#keepall).take(10) as c0 from SupportBean";
            env.compileDeploy(eplSubselectMultivalue).addListener("s0");

            env.sendEventBean(new SupportBean_ST0("B1", 10));
            env.sendEventBean(new SupportBean("E1", 0));
            env.assertEventNew("s0", event -> assertPropsMapRows((Collection) event.get("c0"), fields, new Object[][]{{"B1", 10}}));

            env.sendEventBean(new SupportBean_ST0("B2", 20));
            env.sendEventBean(new SupportBean("E2", 0));
            env.assertEventNew("s0", event -> assertPropsMapRows((Collection) event.get("c0"), fields, new Object[][]{{"B1", 10}, {"B2", 20}}));
            env.undeployAll();

            // test subselect that delivers events
            String epl = "@public @buseventtype create schema AEvent (symbol string);\n" +
                "@public @buseventtype create schema BEvent (a AEvent);\n" +
                "@name('s0') select (select a from BEvent#keepall).anyOf(v => symbol = 'GE') as flag from SupportBean;\n";
            env.compileDeploy(epl, new RegressionPath()).addListener("s0");

            env.sendEventMap(makeBEvent("XX"), "BEvent");
            env.sendEventBean(new SupportBean());
            env.assertEqualsNew("s0", "flag", false);

            env.sendEventMap(makeBEvent("GE"), "BEvent");
            env.sendEventBean(new SupportBean());
            env.assertEqualsNew("s0", "flag", true);

            env.undeployAll();
        }
    }

    private static class ExprEnumVariable implements RegressionExecution {
        public void run(RegressionEnvironment env) {
            String epl = "create variable string[] myvar = { 'E1', 'E3' };\n" +
                "@name('s0') select * from SupportBean(myvar.anyOf(v => v = theString));\n";
            env.compileDeploy(epl).addListener("s0");

            env.sendEventBean(new SupportBean("E1", 1));
            env.assertListenerInvoked("s0");
            env.sendEventBean(new SupportBean("E2", 1));
            env.assertListenerNotInvoked("s0");

            env.undeployAll();
        }
    }

    private static class ExprEnumAccessAggregation implements RegressionExecution {
        public void run(RegressionEnvironment env) {
            String[] fields = new String[]{"val0", "val1", "val2", "val3", "val4"};

            // test window(*) and first(*)
            String eplWindowAgg = "@name('s0') select " +
                "window(*).allOf(x => x.intPrimitive < 5) as val0," +
                "first(*).allOf(x => x.intPrimitive < 5) as val1," +
                "first(*, 1).allOf(x => x.intPrimitive < 5) as val2," +
                "last(*).allOf(x => x.intPrimitive < 5) as val3," +
                "last(*, 1).allOf(x => x.intPrimitive < 5) as val4" +
                " from SupportBean#length(2)";
            env.compileDeploy(eplWindowAgg).addListener("s0");

            env.sendEventBean(new SupportBean("E1", 1));
            env.assertPropsNew("s0", fields, new Object[]{true, true, null, true, null});

            env.sendEventBean(new SupportBean("E2", 10));
            env.assertPropsNew("s0", fields, new Object[]{false, true, false, false, true});

            env.sendEventBean(new SupportBean("E3", 2));
            env.assertPropsNew("s0", fields, new Object[]{false, false, true, true, false});

            env.undeployAll();

            // test scalar: window(*) and first(*)
            String eplWindowAggScalar = "@name('s0') select " +
                "window(intPrimitive).allOf(x => x < 5) as val0," +
                "first(intPrimitive).allOf(x => x < 5) as val1," +
                "first(intPrimitive, 1).allOf(x => x < 5) as val2," +
                "last(intPrimitive).allOf(x => x < 5) as val3," +
                "last(intPrimitive, 1).allOf(x => x < 5) as val4" +
                " from SupportBean#length(2)";
            env.compileDeploy(eplWindowAggScalar).addListener("s0");

            env.sendEventBean(new SupportBean("E1", 1));
            env.assertPropsNew("s0", fields, new Object[]{true, true, null, true, null});

            env.sendEventBean(new SupportBean("E2", 10));
            env.assertPropsNew("s0", fields, new Object[]{false, true, false, false, true});

            env.sendEventBean(new SupportBean("E3", 2));
            env.assertPropsNew("s0", fields, new Object[]{false, false, true, true, false});

            env.undeployAll();
        }
    }

    private static class ExprEnumProperty implements RegressionExecution {
        public void run(RegressionEnvironment env) {
            // test fragment type - collection inside
            String eplFragment = "@name('s0') select contained.allOf(x => x.p00 < 5) as allOfX from SupportBean_ST0_Container#keepall";
            env.compileDeploy(eplFragment).addListener("s0");

            env.sendEventBean(SupportBean_ST0_Container.make3Value("ID1,KEY1,1"));
            env.assertEqualsNew("s0", "allOfX", true);

            env.sendEventBean(SupportBean_ST0_Container.make3Value("ID1,KEY1,10"));
            env.assertEqualsNew("s0", "allOfX", false);
            env.undeployAll();

            // test array and iterable
            String[] fields = "val0,val1".split(",");
            eplFragment = "@name('s0') select intarray.sumof() as val0, " +
                "intiterable.sumOf() as val1 " +
                " from SupportCollection#keepall";
            env.compileDeploy(eplFragment).addListener("s0");

            env.sendEventBean(SupportCollection.makeNumeric("5,6,7"));
            env.assertPropsNew("s0", fields, new Object[]{5 + 6 + 7, 5 + 6 + 7});

            env.undeployAll();

            // test map event type with object-array prop
            RegressionPath path = new RegressionPath();
            env.compileDeploy("@buseventtype @public create schema MySchema (books BookDesc[])", path);

            env.compileDeploy("@name('s0') select books.max(i => i.price) as mymax from MySchema", path);
            env.addListener("s0");

            Map<String, Object> event = Collections.singletonMap("books", new BookDesc[]{new BookDesc("1", "book1", "dave", 1.00, null)});
            env.sendEventMap(event, "MySchema");
            env.assertPropsNew("s0", "mymax".split(","), new Object[]{1.0});

            env.undeployAll();

            // test method invocation variations returning list/array of string and test UDF +property as well
            runAssertionMethodInvoke(env, "select e.getTheList().anyOf(v => v = selector) as flag from SupportSelectorWithListEvent e");
            runAssertionMethodInvoke(env, "select convertToArray(theList).anyOf(v => v = selector) as flag from SupportSelectorWithListEvent e");
            runAssertionMethodInvoke(env, "select theArray.anyOf(v => v = selector) as flag from SupportSelectorWithListEvent e");
            runAssertionMethodInvoke(env, "select e.getTheArray().anyOf(v => v = selector) as flag from SupportSelectorWithListEvent e");
            runAssertionMethodInvoke(env, "select e.theList.anyOf(v => v = e.selector) as flag from pattern[every e=SupportSelectorWithListEvent]");
            runAssertionMethodInvoke(env, "select e.nestedMyEvent.myNestedList.anyOf(v => v = e.selector) as flag from pattern[every e=SupportSelectorWithListEvent]");
            runAssertionMethodInvoke(env, "select " + SupportSelectorWithListEvent.class.getName() + ".convertToArray(theList).anyOf(v => v = selector) as flag from SupportSelectorWithListEvent e");

            env.undeployAll();
        }
    }

    public static void runAssertionMethodInvoke(RegressionEnvironment env, String epl) {
        String[] fields = "flag".split(",");
        env.compileDeploy("@name('s0') " + epl).addListener("s0");

        env.sendEventBean(new SupportSelectorWithListEvent("1"));
        env.assertPropsNew("s0", fields, new Object[]{true});

        env.sendEventBean(new SupportSelectorWithListEvent("4"));
        env.assertPropsNew("s0", fields, new Object[]{false});

        env.undeployAll();
    }

    private static class ExprEnumPrevFuncs implements RegressionExecution {
        public void run(RegressionEnvironment env) {
            String[] fields = new String[]{"val0", "val1", "val2"};
            // test prevwindow(*) etc
            String epl = "@name('s0') select " +
                "prevwindow(sb).allOf(x => x.intPrimitive < 5) as val0," +
                "prev(sb,1).allOf(x => x.intPrimitive < 5) as val1," +
                "prevtail(sb,1).allOf(x => x.intPrimitive < 5) as val2" +
                " from SupportBean#length(2) as sb";
            env.compileDeploy(epl).addListener("s0");

            env.sendEventBean(new SupportBean("E1", 1));
            env.assertPropsNew("s0", fields, new Object[]{true, null, null});

            env.sendEventBean(new SupportBean("E2", 10));
            env.assertPropsNew("s0", fields, new Object[]{false, true, false});

            env.sendEventBean(new SupportBean("E3", 2));
            env.assertPropsNew("s0", fields, new Object[]{false, false, true});

            env.sendEventBean(new SupportBean("E4", 3));
            env.assertPropsNew("s0", fields, new Object[]{true, true, true});
            env.undeployAll();

            // test scalar prevwindow(property) etc
            String eplScalar = "@name('s0') select " +
                "prevwindow(intPrimitive).allOf(x => x < 5) as val0," +
                "prev(intPrimitive,1).allOf(x => x < 5) as val1," +
                "prevtail(intPrimitive,1).allOf(x => x < 5) as val2" +
                " from SupportBean#length(2) as sb";
            env.compileDeploy(eplScalar).addListener("s0");

            env.sendEventBean(new SupportBean("E1", 1));
            env.assertPropsNew("s0", fields, new Object[]{true, null, null});

            env.sendEventBean(new SupportBean("E2", 10));
            env.assertPropsNew("s0", fields, new Object[]{false, true, false});

            env.sendEventBean(new SupportBean("E3", 2));
            env.assertPropsNew("s0", fields, new Object[]{false, false, true});

            env.sendEventBean(new SupportBean("E4", 3));
            env.assertPropsNew("s0", fields, new Object[]{true, true, true});

            env.undeployAll();
        }
    }

    private static class ExprEnumUDFStaticMethod implements RegressionExecution {
        public void run(RegressionEnvironment env) {

            final String[] fields = "val0,val1,val2,val3".split(",");
            String epl = "@name('s0') select " +
                "SupportBean_ST0_Container.makeSampleList().where(x => x.p00 < 5) as val0, " +
                "SupportBean_ST0_Container.makeSampleArray().where(x => x.p00 < 5) as val1, " +
                "makeSampleList().where(x => x.p00 < 5) as val2, " +
                "makeSampleArray().where(x => x.p00 < 5) as val3 " +
                "from SupportBean#length(2) as sb";
            env.compileDeploy(epl).addListener("s0");

            SupportBean_ST0_Container.setSamples(new String[]{"E1,1", "E2,20", "E3,3"});
            env.sendEventBean(new SupportBean());
            env.assertListener("s0", listener -> {
                for (String field : fields) {
                    SupportBean_ST0[] result = toArray((Collection) listener.assertOneGetNew().get(field));
                    assertEquals("Failed for field " + field, 2, result.length);
                }
                listener.reset();
            });

            SupportBean_ST0_Container.setSamples(null);
            env.sendEventBean(new SupportBean());
            env.assertListener("s0", listener -> {
                for (String field : fields) {
                    assertNull(listener.assertOneGetNew().get(field));
                }
                listener.reset();
            });

            SupportBean_ST0_Container.setSamples(new String[0]);
            env.sendEventBean(new SupportBean());
            env.assertListener("s0", listener -> {
                for (String field : fields) {
                    SupportBean_ST0[] result = toArray((Collection) listener.assertOneGetNew().get(field));
                    assertEquals(0, result.length);
                }
                listener.reset();
            });
            env.undeployAll();

            // test UDF returning scalar values collection
            String eplScalar = "@name('s0') select " +
                "SupportCollection.makeSampleListString().where(x => x != 'E1') as val0, " +
                "SupportCollection.makeSampleArrayString().where(x => x != 'E1') as val1, " +
                "makeSampleListString().where(x => x != 'E1') as val2, " +
                "makeSampleArrayString().where(x => x != 'E1') as val3 " +
                "from SupportBean#length(2) as sb";
            env.compileDeploy(eplScalar).addListener("s0");
            env.assertStatement("s0", statement -> env.assertStmtTypesAllSame("s0", fields, EPTypeClassParameterized.from(Collection.class, String.class)));

            SupportCollection.setSampleCSV("E1,E2,E3");
            env.sendEventBean(new SupportBean());
            env.assertListener("s0", listener -> {
                EventBean event = listener.assertOneGetNewAndReset();
                for (String field : fields) {
                    LambdaAssertionUtil.assertValuesArrayScalar(event, field, "E2", "E3");
                }
            });

            SupportCollection.setSampleCSV(null);
            env.sendEventBean(new SupportBean());
            env.assertListener("s0", listener -> {
                EventBean event = listener.assertOneGetNewAndReset();
                for (String field : fields) {
                    LambdaAssertionUtil.assertValuesArrayScalar(event, field, null);
                }
            });

            SupportCollection.setSampleCSV("");
            env.sendEventBean(new SupportBean());
            env.assertListener("s0", listener -> {
                EventBean event = listener.assertOneGetNewAndReset();
                for (String field : fields) {
                    LambdaAssertionUtil.assertValuesArrayScalar(event, field);
                }
            });

            env.undeployAll();
        }
    }

    private static void trySubstitutionParameter(RegressionEnvironment env, String substitution, Object parameter) {

        EPCompiled compiled = env.compile("@name('s0') select * from SupportBean(" + substitution + ".sequenceEqual({1, intPrimitive, 100}))");
        env.deploy(compiled, new DeploymentOptions().setStatementSubstitutionParameter(new SupportPortableDeploySubstitutionParams(1, parameter)));
        env.addListener("s0");

        env.sendEventBean(new SupportBean("E1", 10));
        env.assertListenerInvoked("s0");

        env.sendEventBean(new SupportBean("E2", 20));
        env.assertListenerNotInvoked("s0");

        env.undeployAll();
    }

    private static SupportBean_ST0[] toArray(Collection<SupportBean_ST0> it) {
        if (!it.isEmpty() && it.iterator().next() instanceof EventBean) {
            fail("Iterator provides EventBean instances");
        }
        return it.toArray(new SupportBean_ST0[it.size()]);
    }

    private static Map<String, Object> makeBEvent(String symbol) {
        Map<String, Object> map = new HashMap<String, Object>();
        map.put("a", Collections.singletonMap("symbol", symbol));
        return map;
    }

    private static void assertPropsMapRows(Collection rows, String[] fields, Object[][] objects) {
        Collection<Map> mapsColl = (Collection<Map>) rows;
        Map[] maps = mapsColl.toArray(new Map[mapsColl.size()]);
        EPAssertionUtil.assertPropsPerRow(maps, fields, objects);
    }

    private static void assertColl(String expected, Object value) {
        EPAssertionUtil.assertEqualsExactOrder(expected.split(","), ((Collection) value).toArray());
    }

    public static class MyLocalEvent implements Serializable {
        private static final long serialVersionUID = 7831666209757672187L;
        private Object value;

        public MyLocalEvent(Object value) {
            this.value = value;
        }

        public Object getValue() {
            return value;
        }
    }

    public static class MyLocalEventWithInts implements Serializable {
        private static final long serialVersionUID = 7831666209757672187L;
        private final Set<Integer> intValues;

        public MyLocalEventWithInts(Set<Integer> intValues) {
            this.intValues = intValues;
        }

        public Set<Integer> getIntValues() {
            return intValues;
        }

        public boolean myFunc() {
            for (Integer val : intValues) {
                if (val > 0) {
                    return true;
                }
            }
            return false;
        }
    }

    public static class MyLocalWithCollection implements Serializable {
        private static final long serialVersionUID = -770597812826108976L;
        private final Collection someCollection;

        public MyLocalWithCollection(Collection someCollection) {
            this.someCollection = someCollection;
        }

        public Collection getSomeCollection() {
            return someCollection;
        }
    }

    public static class SupportEventWithMapOfCollOfString implements Serializable {
        private static final long serialVersionUID = -6440296126743879784L;
        private final Map<String, Collection<String>> mymap;

        public SupportEventWithMapOfCollOfString(String mapkey, Collection<String> mymap) {
            this.mymap = Collections.singletonMap(mapkey, mymap);
        }

        public Map<String, Collection<String>> getMymap() {
            return mymap;
        }
    }
}
