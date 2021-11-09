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
package com.espertech.esper.regressionlib.suite.expr.clazz;

import com.espertech.esper.common.client.EPCompiled;
import com.espertech.esper.common.internal.support.SupportBean;
import com.espertech.esper.regressionlib.framework.RegressionEnvironment;
import com.espertech.esper.regressionlib.framework.RegressionExecution;
import com.espertech.esper.regressionlib.framework.RegressionPath;

import java.util.ArrayList;
import java.util.Collection;

public class ExprClassClassDependency {

    public static Collection<RegressionExecution> executions() {
        ArrayList<RegressionExecution> executions = new ArrayList<>();
        executions.add(new ExprClassClassDependencyAllLocal());
        executions.add(new ExprClassClassDependencyInvalid());
        executions.add(new ExprClassClassDependencyClasspath());
        return executions;
    }

    private static class ExprClassClassDependencyClasspath implements RegressionExecution {
        public void run(RegressionEnvironment env) {
            String eplNoImport = "@name('s0') " +
                "inlined_class \"\"\"\n" +
                "    public class MyUtil {\n" +
                "        public static String doIt(String parameter) {\n" +
                "            return " + ExprClassClassDependency.class.getName() + ".supportQuoteString(parameter);\n" +
                "        }\n" +
                "    }\n" +
                "\"\"\" \n" +
                "select MyUtil.doIt(theString) as c0 from SupportBean\n";
            runAssertion(env, eplNoImport);

            String eplImport = "@name('s0') " +
                "inlined_class \"\"\"\n" +
                "    import " + ExprClassClassDependency.class.getName() + ";" +
                "    public class MyUtil {\n" +
                "        public static String doIt(String parameter) {\n" +
                "            return " + ExprClassClassDependency.class.getSimpleName() + ".supportQuoteString(parameter);\n" +
                "        }\n" +
                "    }\n" +
                "\"\"\" \n" +
                "select MyUtil.doIt(theString) as c0 from SupportBean\n";
            runAssertion(env, eplImport);
        }

        private void runAssertion(RegressionEnvironment env, String epl) {
            env.compileDeploy(epl).addListener("s0");

            env.sendEventBean(new SupportBean("E1", 1));
            env.assertEqualsNew("s0", "c0", "'E1'");

            env.undeployAll();
        }
    }

    private static class ExprClassClassDependencyInvalid implements RegressionExecution {
        public void run(RegressionEnvironment env) {
            // Class depending on create-class class
            RegressionPath path = new RegressionPath();
            String epl = "@public create inlined_class \"\"\"\n" +
                "    public class MyUtil {\n" +
                "        public static String someFunction(String parameter) {\n" +
                "            return \"|\" + parameter + \"|\";\n" +
                "        }\n" +
                "    }\n" +
                "\"\"\"";
            EPCompiled compiled = env.compile(epl);
            path.add(compiled);

            String eplInvalid = "inlined_class \"\"\"\n" +
                "    public class MyClass {\n" +
                "        public static String doIt(String parameter) {\n" +
                "            return MyUtil.someFunction(\">\" + parameter + \"<\");\n" +
                "        }\n" +
                "    }\n" +
                "\"\"\" \n" +
                "select MyClass.doIt(theString) as c0 from SupportBean\n";
            env.tryInvalidCompile(path, eplInvalid, "Failed to compile an inlined-class:");

            // create-class depending on create-class
            eplInvalid = "create inlined_class \"\"\"\n" +
                "    public class MyClass {\n" +
                "        public static String doIt(String parameter) {\n" +
                "            return MyUtil.someFunction(\">\" + parameter + \"<\");\n" +
                "        }\n" +
                "    }\n" +
                "\"\"\"";
            env.tryInvalidCompile(path, eplInvalid, "Failed to compile an inlined-class: ");
        }
    }

    private static class ExprClassClassDependencyAllLocal implements RegressionExecution {
        public void run(RegressionEnvironment env) {
            String epl = "@name('s0') " +
                "inlined_class \"\"\"\n" +
                "    public class MyUtil {\n" +
                "        public static String someFunction(String parameter) {\n" +
                "            return \"|\" + parameter + \"|\";\n" +
                "        }\n" +
                "    }\n" +
                "\"\"\" \n" +
                "inlined_class \"\"\"\n" +
                "    public class MyClass {\n" +
                "        public static String doIt(String parameter) {\n" +
                "            return MyUtil.someFunction(\">\" + parameter + \"<\");\n" +
                "        }\n" +
                "    }\n" +
                "\"\"\" \n" +
                "select MyClass.doIt(theString) as c0 from SupportBean\n";
            env.compileDeploy(epl).addListener("s0");

            env.sendEventBean(new SupportBean("E1", 1));
            env.assertEqualsNew("s0", "c0", "|>E1<|");

            env.undeployAll();
        }
    }

    public static String supportQuoteString(String s) {
        return "'" + s + "'";
    }
}
