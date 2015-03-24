package org.apache.maven.surefire.testng;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import java.io.File;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.SortedMap;
import java.util.TreeMap;

import org.apache.maven.surefire.NonAbstractClassFilter;
import org.apache.maven.surefire.report.ConsoleOutputCapture;
import org.apache.maven.surefire.report.ConsoleOutputReceiver;
import org.apache.maven.surefire.report.ReportEntry;
import org.apache.maven.surefire.report.ReporterException;
import org.apache.maven.surefire.report.ReporterFactory;
import org.apache.maven.surefire.report.RunListener;
import org.apache.maven.surefire.report.SimpleReportEntry;
import org.apache.maven.surefire.testset.TestListResolver;
import org.apache.maven.surefire.testset.TestSetFailedException;
import org.apache.maven.surefire.util.RunOrderCalculator;
import org.apache.maven.surefire.util.ScanResult;
import org.apache.maven.surefire.util.TestsToRun;

/**
 * Test suite for TestNG based on a directory of Java test classes. Can also execute JUnit tests.
 *
 * @author <a href="mailto:brett@apache.org">Brett Porter</a>
 * @author <a href='mailto:the[dot]mindstorm[at]gmail[dot]com'>Alex Popescu</a>
 */
public class TestNGDirectoryTestSuite
    implements TestNgTestSuite
{

    private final Map options;

    private final Map junitOptions;

    private final String testSourceDirectory;

    private final File reportsDirectory;

    private SortedMap<String, TestNGTestSet> testSets;

    private final ScanResult scanResult;

    private final TestListResolver methodFilter;

    private final RunOrderCalculator runOrderCalculator;

    private final Class junitTestClass;

    private Class<? extends Annotation> junitRunWithAnnotation;

    private Class<? extends Annotation> junitTestAnnotation;

    public TestNGDirectoryTestSuite( String testSourceDirectory, Properties confOptions, File reportsDirectory,
                                     TestListResolver methodFilter, RunOrderCalculator runOrderCalculator,
                                     ScanResult scanResult )
    {

        this.runOrderCalculator = runOrderCalculator;

        this.options = confOptions;

        this.testSourceDirectory = testSourceDirectory;
        this.reportsDirectory = reportsDirectory;
        this.scanResult = scanResult;
        this.methodFilter = methodFilter;
        this.junitTestClass = findJUnitTestClass();
        this.junitRunWithAnnotation = findJUnitRunWithAnnotation();
        this.junitTestAnnotation = findJUnitTestAnnotation();
        this.junitOptions = createJUnitOptions();
    }

    public void execute( TestsToRun testsToRun, ReporterFactory reporterManagerFactory )
        throws TestSetFailedException
    {

        if ( !testsToRun.allowEagerReading() )
        {
            executeLazy( testsToRun, reporterManagerFactory );
        }
        else if ( testsToRun.containsAtLeast( 2 ) )
        {
            executeMulti( testsToRun, reporterManagerFactory );
        }
        else if ( testsToRun.containsAtLeast( 1 ) )
        {
            Class testClass = testsToRun.iterator().next();
            executeSingleClass( reporterManagerFactory, testClass );
        }
    }

    private void executeSingleClass( ReporterFactory reporterManagerFactory, Class testClass )
        throws TestSetFailedException
    {
        this.options.put( "suitename", testClass.getName() );

        RunListener reporter = reporterManagerFactory.createReporter();
        ConsoleOutputCapture.startCapture( (ConsoleOutputReceiver) reporter );

        startTestSuite( reporter, this );

        final Map optionsToUse = isJUnitTest( testClass ) ? junitOptions : options;

        TestNGExecutor.run( new Class[]{ testClass }, testSourceDirectory, optionsToUse, reporter, this,
                            reportsDirectory, methodFilter );

        finishTestSuite( reporter, this );
    }

    public void executeLazy( TestsToRun testsToRun, ReporterFactory reporterFactory )
        throws TestSetFailedException
    {

        for ( Class c : testsToRun )
        {
            executeSingleClass( reporterFactory, c );
        }
    }

    private Class findJUnitTestClass()
    {
        return lookupClass( "junit.framework.Test" );
    }

    private Class findJUnitRunWithAnnotation()
    {
        return lookupClass( "org.junit.runner.RunWith" );
    }

    private Class findJUnitTestAnnotation()
    {
        return lookupClass( "org.junit.Test" );
    }

    private Class lookupClass( String className )
    {
        Class junitClass;
        try
        {
            junitClass = Class.forName( className );
        }
        catch ( ClassNotFoundException e )
        {
            junitClass = null;
        }
        return junitClass;
    }

    public void executeMulti( TestsToRun testsToRun, ReporterFactory reporterFactory )
        throws TestSetFailedException
    {
        List<Class> testNgTestClasses = new ArrayList<Class>();
        List<Class> junitTestClasses = new ArrayList<Class>();
        for ( Class c : testsToRun )
        {
            if ( isJUnitTest( c ) )
            {
                junitTestClasses.add( c );
            }
            else
            {
                testNgTestClasses.add( c );
            }
        }

        File testNgReportsDirectory = reportsDirectory, junitReportsDirectory = reportsDirectory;

        if ( junitTestClasses.size() > 0 && testNgTestClasses.size() > 0 )
        {
            testNgReportsDirectory = new File( reportsDirectory, "testng-native-results" );
            junitReportsDirectory = new File( reportsDirectory, "testng-junit-results" );
        }

        RunListener reporterManager = reporterFactory.createReporter();
        ConsoleOutputCapture.startCapture( (ConsoleOutputReceiver) reporterManager );
        startTestSuite( reporterManager, this );

        Class[] testClasses = testNgTestClasses.toArray( new Class[testNgTestClasses.size()] );

        TestNGExecutor.run( testClasses, this.testSourceDirectory, options, reporterManager, this,
                            testNgReportsDirectory, methodFilter );

        if ( junitTestClasses.size() > 0 )
        {
            testClasses = junitTestClasses.toArray( new Class[junitTestClasses.size()] );

            TestNGExecutor.run( testClasses, testSourceDirectory, junitOptions, reporterManager, this,
                                junitReportsDirectory, methodFilter );
        }

        finishTestSuite( reporterManager, this );
    }

    private boolean isJUnitTest( Class c )
    {
        return isJunit3Test( c ) || isJunit4Test( c );
    }

    private boolean isJunit4Test( Class c )
    {
        return hasJunit4RunWithAnnotation( c ) || hasJunit4TestAnnotation( c );
    }

    private boolean hasJunit4RunWithAnnotation( Class c )
    {
        return junitRunWithAnnotation != null && c.getAnnotation( junitRunWithAnnotation ) != null;
    }

    private boolean hasJunit4TestAnnotation( Class c )
    {
        if ( junitTestAnnotation != null )
        {
            for ( Method m : c.getMethods() )
            {
                if ( m.getAnnotation( junitTestAnnotation ) != null )
                {
                    return true;
                }
            }
        }

        return false;
    }

    private boolean isJunit3Test( Class c )
    {
        return junitTestClass != null && junitTestClass.isAssignableFrom( c );
    }

    private Map createJUnitOptions()
    {
        Map junitOptions = new HashMap( this.options );
        junitOptions.put( "junit", Boolean.TRUE );
        return junitOptions;
    }

    // single class test
    public void execute( String testSetName, ReporterFactory reporterManagerFactory )
        throws TestSetFailedException
    {
        if ( testSets == null )
        {
            throw new IllegalStateException( "You must call locateTestSets before calling execute" );
        }
        TestNGTestSet testSet = testSets.get( testSetName );

        if ( testSet == null )
        {
            throw new TestSetFailedException( "Unable to find test set '" + testSetName + "' in suite" );
        }

        RunListener reporter = reporterManagerFactory.createReporter();
        ConsoleOutputCapture.startCapture( (ConsoleOutputReceiver) reporter );

        startTestSuite( reporter, this );

        TestNGExecutor.run( new Class[] { testSet.getTestClass() }, this.testSourceDirectory, this.options, reporter,
                            this, reportsDirectory, methodFilter );

        finishTestSuite( reporter, this );
    }

    public static void startTestSuite( RunListener reporter, Object suite )
    {
        ReportEntry report = new SimpleReportEntry( suite.getClass().getName(), getSuiteName( suite ) );

        try
        {
            reporter.testSetStarting( report );
        }
        catch ( ReporterException e )
        {
            // TODO: remove this exception from the report manager
        }
    }

    public static void finishTestSuite( RunListener reporterManager, Object suite )
    {
        ReportEntry report = new SimpleReportEntry( suite.getClass().getName(), getSuiteName( suite ) );

        reporterManager.testSetCompleted( report );
    }

    public String getSuiteName()
    {
        String result = (String) options.get( "suitename" );
        if ( result == null )
        {
            result = "TestSuite";
        }
        return result;
    }

    private static String getSuiteName( Object suite )
    {
        String result;
        if ( suite instanceof TestNGDirectoryTestSuite )
        {
            return ( (TestNGDirectoryTestSuite) suite ).getSuiteName();
        }
        else if ( suite instanceof TestNGXmlTestSuite )
        {
            return ( (TestNGXmlTestSuite) suite ).getSuiteName();
        }
        else
        {
            result = "TestSuite";
        }

        return result;
    }

    public Map locateTestSets( ClassLoader classLoader )
        throws TestSetFailedException
    {
        if ( testSets != null )
        {
            throw new IllegalStateException( "You can't call locateTestSets twice" );
        }
        testSets = new TreeMap<String, TestNGTestSet>();

        final TestsToRun scanned = scanResult.applyFilter( new NonAbstractClassFilter(), classLoader );

        final TestsToRun testsToRun = runOrderCalculator.orderTestClasses( scanned );

        for ( Class testClass : testsToRun )
        {
            TestNGTestSet testSet = new TestNGTestSet( testClass );

            if ( testSets.containsKey( testSet.getName() ) )
            {
                throw new TestSetFailedException( "Duplicate test set '" + testSet.getName() + "'" );
            }
            testSets.put( testSet.getName(), testSet );

        }

        return Collections.unmodifiableSortedMap( testSets );
    }

}
