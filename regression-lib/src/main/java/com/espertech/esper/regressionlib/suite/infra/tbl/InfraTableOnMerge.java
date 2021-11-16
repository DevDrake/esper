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
package com.espertech.esper.regressionlib.suite.infra.tbl;

import com.espertech.esper.common.client.scopetest.EPAssertionUtil;
import com.espertech.esper.common.internal.support.*;
import com.espertech.esper.regressionlib.framework.RegressionEnvironment;
import com.espertech.esper.regressionlib.framework.RegressionExecution;
import com.espertech.esper.regressionlib.framework.RegressionPath;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

/**
 * NOTE: More table-related tests in "nwtable"
 */
public class InfraTableOnMerge {

    public static Collection<RegressionExecution> executions() {
        ArrayList<RegressionExecution> execs = new ArrayList<>();
        execs.add(new InfraTableOnMergeSimple());
        execs.add(new InfraOnMergePlainPropsAnyKeyed());
        execs.add(new InfraMergeWhereWithMethodRead());
        execs.add(new InfraMergeSelectWithAggReadAndEnum());
        execs.add(new InfraMergeTwoTables());
        execs.add(new InfraTableEMACompute());
        execs.add(new InfraTableArrayAssignmentBoxed());
        return execs;
    }

    private static class InfraTableArrayAssignmentBoxed implements RegressionExecution {
        public void run(RegressionEnvironment env) {
            String epl =
                "create table MyTable(dbls double[]);\n" +
                "@priority(2) on SupportBean merge MyTable when not matched then insert select new java.lang.Double[3] as dbls;\n" +
                "@priority(1) on SupportBean merge MyTable when matched then update set dbls[intPrimitive] = 1;\n" +
                "@name('s0') select MyTable.dbls as c0 from SupportBean;\n";
            env.compileDeploy(epl).addListener("s0");

            env.sendEventBean(new SupportBean("E1", 1));
            env.assertEventNew("s0", event -> assertArrayEquals(new Double[] {null, 1d, null}, (Double[]) event.get("c0")));

            env.undeployAll();
        }
    }

    private static class InfraTableEMACompute implements RegressionExecution {
        /**
         * let p = 0.1
         * a = average(x1, x2, x3, x4, x5)    // Assume 5, in reality use a parameter
         * y1 = p * x1 + (p - 1) * a          // Recursive calculation initialized with look-ahead average
         * y2 = p * x2 + (p - 1) * y1
         * y3 = p * x3 + (p - 1) * y2
         *    ....
         *
         * The final stream should only publish y5, y6, y7, ...
         */
        public void run(RegressionEnvironment env) {
            String epl =
                    "@public @buseventtype create schema MyEvent(id string, x double);\n" +
                    "create constant variable int BURN_LENGTH = 5;\n" +
                    "create constant variable double ALPHA = 0.1;\n" +
                    "create table EMA(burnValues double[primitive], cnt int, value double);\n" +
                    "" +
                    "// Seed the row when the table is empty\n" +
                    "@priority(2) on MyEvent merge EMA\n" +
                    "  when not matched then insert select new double[BURN_LENGTH] as burnValues, 0 as cnt, null as value;\n" +
                    "" +
                    "inlined_class \"\"\"\n" +
                    "  public class Helper {\n" +
                    "    public static double computeInitialValue(double alpha, double[] burnValues) {\n" +
                    "      double total = 0;\n" +
                    "      for (int i = 0; i < burnValues.length; i++) {\n" +
                    "        total = total + burnValues[i];\n" +
                    "      }\n" +
                    "      double value = total / burnValues.length;\n" +
                    "      for (int i = 0; i < burnValues.length; i++) {\n" +
                    "        value = alpha * burnValues[i] + (1 - alpha) * value;\n" +
                    "      }\n" +
                    "      return value;" +
                    "    }\n" +
                    "  }\n" +
                    "\"\"\"\n" +
                    "// Update the 'value' field with the current value\n" +
                    "@priority(1) on MyEvent merge EMA as ema\n" +
                    "  when matched and cnt < BURN_LENGTH - 1 then update set burnValues[cnt] = x, cnt = cnt + 1\n" +
                    "  when matched and cnt = BURN_LENGTH - 1 then update set burnValues[cnt] = x, cnt = cnt + 1, value = Helper.computeInitialValue(ALPHA, burnValues), burnValues = null\n" +
                    "  when matched then update set value = ALPHA * x + (1 - ALPHA) * value;\n" +
                    "" +
                    "// Output value\n" +
                    "@name('output') select EMA.value as burn from MyEvent;\n";
            env.compileDeploy(epl).addListener("output");

            sendAssertEMA(env, "E1", 1, null);

            sendAssertEMA(env, "E2", 2, null);

            sendAssertEMA(env, "E3", 3, null);

            sendAssertEMA(env, "E4", 4, null);

            // Last of the burn period
            // We expect:
            // a = (1+2+3+4+5) / 5 = 3
            // y1 = 0.1 * 1 + 0.9 * 3 = 2.8
            // y2 = 0.1 * 2 + 0.9 * 2.8
            //    ... leading to
            // y5 = 3.08588
            sendAssertEMA(env, "E5", 5, 3.08588);

            // Outside burn period
            sendAssertEMA(env, "E6", 6, 3.377292);

            env.milestone(0);

            sendAssertEMA(env, "E7", 7, 3.7395628);

            sendAssertEMA(env, "E8", 8, 4.16560652);

            env.undeployAll();
        }
    }

    private static class InfraMergeTwoTables implements RegressionExecution {
        public void run(RegressionEnvironment env) {
            String epl =
                    "@name('T0') create table TableZero(k0 string primary key, v0 int);\n" +
                    "@name('T1') create table TableOne(k1 string primary key, v1 int);\n" +
                    "on SupportBean merge TableZero " +
                    "  where theString = k0 when not matched " +
                    "  then insert select theString as k0, intPrimitive as v0" +
                    "  then insert into TableOne(k1, v1) select theString, intPrimitive;\n";
            env.compileDeploy(epl);

            env.sendEventBean(new SupportBean("E1", 1));
            assertTables(env, new Object[][] {{"E1", 1}});

            env.milestone(0);

            env.sendEventBean(new SupportBean("E2", 2));
            env.sendEventBean(new SupportBean("E2", 3));
            assertTables(env, new Object[][] {{"E1", 1}, {"E2", 2}});

            env.undeployAll();
        }

        private void assertTables(RegressionEnvironment env, Object[][] expected) {
            env.assertPropsPerRowIteratorAnyOrder("T0", "k0,v0".split(","), expected);
            env.assertPropsPerRowIteratorAnyOrder("T1", "k1,v1".split(","), expected);
        }
    }

    private static class InfraTableOnMergeSimple implements RegressionExecution {
        public void run(RegressionEnvironment env) {
            RegressionPath path = new RegressionPath();
            String[] fields = "k1,v1".split(",");

            env.compileDeploy("@name('tbl') @public create table varaggKV (k1 string primary key, v1 int)", path);
            env.compileDeploy("on SupportBean as sb merge varaggKV as va where sb.theString = va.k1 " +
                "when not matched then insert select theString as k1, intPrimitive as v1 " +
                "when matched then update set v1 = intPrimitive", path);

            env.sendEventBean(new SupportBean("E1", 10));
            env.assertPropsPerRowIterator("tbl", fields, new Object[][]{{"E1", 10}});

            env.milestone(0);

            env.sendEventBean(new SupportBean("E1", 11));
            env.assertPropsPerRowIterator("tbl", fields, new Object[][]{{"E1", 11}});

            env.milestone(1);

            env.sendEventBean(new SupportBean("E2", 100));
            env.assertPropsPerRowIteratorAnyOrder("tbl", fields, new Object[][]{{"E1", 11}, {"E2", 100}});

            env.milestone(2);

            env.sendEventBean(new SupportBean("E2", 101));
            env.assertPropsPerRowIteratorAnyOrder("tbl", fields, new Object[][]{{"E1", 11}, {"E2", 101}});

            env.undeployAll();
        }
    }

    private static class InfraMergeWhereWithMethodRead implements RegressionExecution {
        public void run(RegressionEnvironment env) {
            RegressionPath path = new RegressionPath();
            env.compileDeploy("@public create table varaggMMR (keyOne string primary key, cnt count(*))", path);
            env.compileDeploy("into table varaggMMR select count(*) as cnt " +
                "from SupportBean#lastevent group by theString", path);

            env.compileDeploy("@name('s0') select varaggMMR[p00].keyOne as c0 from SupportBean_S0", path).addListener("s0");
            env.compileDeploy("on SupportBean_S1 merge varaggMMR where cnt = 0 when matched then delete", path);

            env.sendEventBean(new SupportBean("G1", 0));
            env.sendEventBean(new SupportBean("G2", 0));
            assertKeyFound(env, "G1,G2,G3", new boolean[]{true, true, false});

            env.sendEventBean(new SupportBean_S1(0)); // delete
            assertKeyFound(env, "G1,G2,G3", new boolean[]{false, true, false});

            env.milestone(0);

            env.sendEventBean(new SupportBean("G3", 0));
            assertKeyFound(env, "G1,G2,G3", new boolean[]{false, true, true});

            env.sendEventBean(new SupportBean_S1(0));  // delete
            assertKeyFound(env, "G1,G2,G3", new boolean[]{false, false, true});

            env.undeployAll();
        }
    }

    private static class InfraMergeSelectWithAggReadAndEnum implements RegressionExecution {
        public void run(RegressionEnvironment env) {
            RegressionPath path = new RegressionPath();
            env.compileDeploy("@public create table varaggMS (eventset window(*) @type(SupportBean), total sum(int))", path);
            env.compileDeploy("into table varaggMS select window(*) as eventset, " +
                "sum(intPrimitive) as total from SupportBean#length(2)", path);
            env.compileDeploy("@public on SupportBean_S0 merge varaggMS " +
                "when matched then insert into ResultStream select eventset, total, eventset.takeLast(1) as c0", path);
            env.compileDeploy("@name('s0') select * from ResultStream", path).addListener("s0");

            SupportBean e1 = new SupportBean("E1", 15);
            env.sendEventBean(e1);

            assertResultAggRead(env, new Object[]{e1}, 15);

            env.milestone(0);

            SupportBean e2 = new SupportBean("E2", 20);
            env.sendEventBean(e2);

            assertResultAggRead(env, new Object[]{e1, e2}, 35);

            env.milestone(1);

            SupportBean e3 = new SupportBean("E3", 30);
            env.sendEventBean(e3);

            assertResultAggRead(env, new Object[]{e2, e3}, 50);

            env.undeployAll();
        }
    }

    private static class InfraOnMergePlainPropsAnyKeyed implements RegressionExecution {
        public void run(RegressionEnvironment env) {
            AtomicInteger milestone = new AtomicInteger();
            runOnMergeInsertUpdDeleteSingleKey(env, false, milestone);
            runOnMergeInsertUpdDeleteSingleKey(env, true, milestone);

            runOnMergeInsertUpdDeleteTwoKey(env, false, milestone);
            runOnMergeInsertUpdDeleteTwoKey(env, true, milestone);

            runOnMergeInsertUpdDeleteUngrouped(env, false, milestone);
            runOnMergeInsertUpdDeleteUngrouped(env, true, milestone);
        }
    }

    private static void runOnMergeInsertUpdDeleteUngrouped(RegressionEnvironment env, boolean soda, AtomicInteger milestone) {
        RegressionPath path = new RegressionPath();
        String eplDeclare = "@public create table varaggIUD (p0 string, sumint sum(int))";
        env.compileDeploy(soda, eplDeclare, path);

        String[] fields = "c0,c1".split(",");
        String eplRead = "@name('s0') select varaggIUD.p0 as c0, varaggIUD.sumint as c1, varaggIUD as c2 from SupportBean_S0";
        env.compileDeploy(soda, eplRead, path).addListener("s0");

        // assert selected column types
        Object[][] expectedAggType = new Object[][]{{"c0", String.class}, {"c1", Integer.class}};
        env.assertStatement("s0", statement -> SupportEventTypeAssertionUtil.assertEventTypeProperties(expectedAggType, statement.getEventType(), SupportEventTypeAssertionEnum.NAME, SupportEventTypeAssertionEnum.TYPE));

        // assert no row
        env.sendEventBean(new SupportBean_S0(0));
        env.assertPropsNew("s0", fields, new Object[]{null, null});

        // create merge
        String eplMerge = "on SupportBean merge varaggIUD" +
            " when not matched then" +
            " insert select theString as p0" +
            " when matched and theString like \"U%\" then" +
            " update set p0=\"updated\"" +
            " when matched and theString like \"D%\" then" +
            " delete";
        env.compileDeploy(soda, eplMerge, path);

        // merge for varagg
        env.sendEventBean(new SupportBean("E1", 0));

        // assert
        env.sendEventBean(new SupportBean_S0(0));
        env.assertPropsNew("s0", fields, new Object[]{"E1", null});

        // also aggregate-into the same key
        env.compileDeploy(soda, "into table varaggIUD select sum(50) as sumint from SupportBean_S1", path);
        env.sendEventBean(new SupportBean_S1(0));

        env.milestoneInc(milestone);

        env.sendEventBean(new SupportBean_S0(0));
        env.assertPropsNew("s0", fields, new Object[]{"E1", 50});

        // update for varagg
        env.sendEventBean(new SupportBean("U2", 10));

        env.milestoneInc(milestone);

        env.sendEventBean(new SupportBean_S0(0));
        env.assertEventNew("s0", received -> {
            EPAssertionUtil.assertProps(received, fields, new Object[]{"updated", 50});
            EPAssertionUtil.assertPropsMap((Map) received.get("c2"), "p0,sumint".split(","), new Object[]{"updated", 50});
        });

        // delete for varagg
        env.sendEventBean(new SupportBean("D3", 0));

        env.milestoneInc(milestone);

        env.sendEventBean(new SupportBean_S0(0));
        env.assertPropsNew("s0", fields, new Object[]{null, null});

        env.undeployAll();
    }

    private static void runOnMergeInsertUpdDeleteSingleKey(RegressionEnvironment env, boolean soda, AtomicInteger milestone) {
        String[] fieldsTable = "key,p0,p1,p2,sumint".split(",");
        RegressionPath path = new RegressionPath();
        String eplDeclare = "@public create table varaggMIU (key int primary key, p0 string, p1 int, p2 int[], sumint sum(int))";
        env.compileDeploy(soda, eplDeclare, path);

        String[] fields = "c0,c1,c2,c3".split(",");
        String eplRead = "@name('s0') select varaggMIU[id].p0 as c0, varaggMIU[id].p1 as c1, varaggMIU[id].p2 as c2, varaggMIU[id].sumint as c3 from SupportBean_S0";
        env.compileDeploy(soda, eplRead, path).addListener("s0");

        // assert selected column types
        Object[][] expectedAggType = new Object[][]{{"c0", String.class}, {"c1", Integer.class}, {"c2", Integer[].class}, {"c3", Integer.class}};
        env.assertStatement("s0", statement -> SupportEventTypeAssertionUtil.assertEventTypeProperties(expectedAggType, statement.getEventType(), SupportEventTypeAssertionEnum.NAME, SupportEventTypeAssertionEnum.TYPE));

        // assert no row
        env.sendEventBean(new SupportBean_S0(10));
        env.assertPropsNew("s0", fields, new Object[]{null, null, null, null});

        // create merge
        String eplMerge = "@name('merge') on SupportBean merge varaggMIU" +
            " where intPrimitive=key" +
            " when not matched then" +
            " insert select intPrimitive as key, \"v1\" as p0, 1000 as p1, {1,2} as p2" +
            " when matched and theString like \"U%\" then" +
            " update set p0=\"v2\", p1=2000, p2={3,4}" +
            " when matched and theString like \"D%\" then" +
            " delete";
        env.compileDeploy(soda, eplMerge, path).addListener("merge");

        // merge for varagg[10]
        env.sendEventBean(new SupportBean("E1", 10));
        env.assertPropsNew("merge", fieldsTable, new Object[]{10, "v1", 1000, new Integer[]{1, 2}, null});

        // assert key "10"
        env.sendEventBean(new SupportBean_S0(10));
        env.assertPropsNew("s0", fields, new Object[]{"v1", 1000, new Integer[]{1, 2}, null});

        // also aggregate-into the same key
        env.compileDeploy(soda, "into table varaggMIU select sum(50) as sumint from SupportBean_S1 group by id", path);
        env.sendEventBean(new SupportBean_S1(10));

        env.milestoneInc(milestone);

        env.sendEventBean(new SupportBean_S0(10));
        env.assertPropsNew("s0", fields, new Object[]{"v1", 1000, new Integer[]{1, 2}, 50});

        // update for varagg[10]
        env.sendEventBean(new SupportBean("U2", 10));
        env.assertPropsIRPair("merge", fieldsTable, new Object[]{10, "v2", 2000, new Integer[]{3, 4}, 50}, new Object[]{10, "v1", 1000, new Integer[]{1, 2}, 50});

        env.milestoneInc(milestone);

        env.sendEventBean(new SupportBean_S0(10));
        env.assertPropsNew("s0", fields, new Object[]{"v2", 2000, new Integer[]{3, 4}, 50});

        // delete for varagg[10]
        env.sendEventBean(new SupportBean("D3", 10));
        env.assertPropsOld("merge", fieldsTable, new Object[]{10, "v2", 2000, new Integer[]{3, 4}, 50});

        env.milestoneInc(milestone);

        env.sendEventBean(new SupportBean_S0(10));
        env.assertPropsNew("s0", fields, new Object[]{null, null, null, null});

        env.undeployAll();
    }

    private static void runOnMergeInsertUpdDeleteTwoKey(RegressionEnvironment env, boolean soda, AtomicInteger milestone) {
        RegressionPath path = new RegressionPath();
        String eplDeclare = "@public create table varaggMIUD (keyOne int primary key, keyTwo string primary key, prop string)";
        env.compileDeploy(soda, eplDeclare, path);

        String[] fields = "c0,c1,c2".split(",");
        String eplRead = "@name('s0') select varaggMIUD[id,p00].keyOne as c0, varaggMIUD[id,p00].keyTwo as c1, varaggMIUD[id,p00].prop as c2 from SupportBean_S0";
        env.compileDeploy(soda, eplRead, path).addListener("s0");

        // assert selected column types
        Object[][] expectedAggType = new Object[][]{{"c0", Integer.class}, {"c1", String.class}, {"c2", String.class}};
        env.assertStatement("s0", statement -> SupportEventTypeAssertionUtil.assertEventTypeProperties(expectedAggType, statement.getEventType(), SupportEventTypeAssertionEnum.NAME, SupportEventTypeAssertionEnum.TYPE));

        // assert no row
        env.sendEventBean(new SupportBean_S0(10, "A"));
        env.assertPropsNew("s0", fields, new Object[]{null, null, null});

        // create merge
        String eplMerge = "@name('merge') on SupportBean merge varaggMIUD" +
            " where intPrimitive=keyOne and theString=keyTwo" +
            " when not matched then" +
            " insert select intPrimitive as keyOne, theString as keyTwo, \"inserted\" as prop" +
            " when matched and longPrimitive>0 then" +
            " update set prop=\"updated\"" +
            " when matched and longPrimitive<0 then" +
            " delete";
        env.compileDeploy(soda, eplMerge, path);
        Object[][] expectedType = new Object[][]{{"keyOne", Integer.class}, {"keyTwo", String.class}, {"prop", String.class}};
        env.assertStatement("merge", statement -> SupportEventTypeAssertionUtil.assertEventTypeProperties(expectedType, statement.getEventType(), SupportEventTypeAssertionEnum.NAME, SupportEventTypeAssertionEnum.TYPE));

        // merge for varagg[10, "A"]
        env.sendEventBean(new SupportBean("A", 10));

        env.milestoneInc(milestone);

        // assert key {"10", "A"}
        env.sendEventBean(new SupportBean_S0(10, "A"));
        env.assertPropsNew("s0", fields, new Object[]{10, "A", "inserted"});

        // update for varagg[10, "A"]
        env.sendEventBean(makeSupportBean("A", 10, 1));

        env.milestoneInc(milestone);

        env.sendEventBean(new SupportBean_S0(10, "A"));
        env.assertPropsNew("s0", fields, new Object[]{10, "A", "updated"});

        // test typable output
        env.compileDeploy("@name('convert') insert into LocalBean select varaggMIUD[10, 'A'] as val0 from SupportBean_S1", path).addListener("convert");
        env.sendEventBean(new SupportBean_S1(2));
        env.assertPropsNew("convert", "val0.keyOne".split(","), new Object[]{10});

        // delete for varagg[10, "A"]
        env.sendEventBean(makeSupportBean("A", 10, -1));

        env.milestoneInc(milestone);

        env.sendEventBean(new SupportBean_S0(10, "A"));
        env.assertPropsNew("s0", fields, new Object[]{null, null, null});

        env.undeployAll();
    }

    private static void sendAssertEMA(RegressionEnvironment env, String id, double x, Double expected) {
        Map<String, Object> event = new HashMap<>();
        event.put("id", id);
        event.put("x", x);
        env.sendEventMap(event, "MyEvent");
        env.assertEventNew("output", output -> {
            Double burn = (Double) output.get("burn");
            if (expected == null) {
                assertNull(burn);
            } else {
                assertEquals(expected, burn, 1e-10);
            }
        });
    }

    private static SupportBean makeSupportBean(String theString, int intPrimitive, long longPrimitive) {
        SupportBean bean = new SupportBean(theString, intPrimitive);
        bean.setLongPrimitive(longPrimitive);
        return bean;
    }

    private static void assertResultAggRead(RegressionEnvironment env, Object[] objects, int total) {
        String[] fields = "eventset,total".split(",");
        env.sendEventBean(new SupportBean_S0(0));
        env.assertEventNew("s0", event -> {
            EPAssertionUtil.assertProps(event, fields, new Object[]{objects, total});
            EPAssertionUtil.assertEqualsExactOrder(new Object[]{objects[objects.length - 1]}, ((Collection) event.get("c0")).toArray());
        });
    }

    private static void assertKeyFound(RegressionEnvironment env, String keyCsv, boolean[] expected) {
        String[] split = keyCsv.split(",");
        for (int i = 0; i < split.length; i++) {
            String key = split[i];
            env.sendEventBean(new SupportBean_S0(0, key));
            String expectedString = expected[i] ? key : null;
            env.assertEventNew("s0", event -> assertEquals("failed for key '" + key + "'", expectedString, event.get("c0")));
        }
    }

    public static class LocalSubBean {
        private int keyOne;
        private String keyTwo;
        private String prop;

        public int getKeyOne() {
            return keyOne;
        }

        public void setKeyOne(int keyOne) {
            this.keyOne = keyOne;
        }

        public String getKeyTwo() {
            return keyTwo;
        }

        public void setKeyTwo(String keyTwo) {
            this.keyTwo = keyTwo;
        }

        public String getProp() {
            return prop;
        }

        public void setProp(String prop) {
            this.prop = prop;
        }
    }

    public static class LocalBean {
        private LocalSubBean val0;

        public LocalSubBean getVal0() {
            return val0;
        }

        public void setVal0(LocalSubBean val0) {
            this.val0 = val0;
        }
    }
}
