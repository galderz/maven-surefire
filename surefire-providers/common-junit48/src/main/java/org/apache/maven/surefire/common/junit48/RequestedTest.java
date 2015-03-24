package org.apache.maven.surefire.common.junit48;

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

import org.apache.maven.surefire.testset.ResolvedTest;
import org.junit.runner.Description;
import org.junit.runner.manipulation.Filter;

final class RequestedTest
    extends Filter
{
    private static final String CLASS_FILE_EXTENSION = ".class";

    private final ResolvedTest test;

    RequestedTest( ResolvedTest test )
    {
        this.test = test;
    }

    @Override
    public boolean shouldRun( Description description )
    {
        Class<?> realTestClass = description.getTestClass();
        String methodName = description.getMethodName();
        return realTestClass == null && methodName == null || test.shouldRun( classFile( realTestClass ), methodName );
    }

    @Override
    public String describe()
    {
        final String classPattern = test.getTestClassPattern();
        final String methodPattern = test.getTestMethodPattern();
        String description = classPattern == null ? "" : classPattern;
        if ( methodPattern != null )
        {
            description += "#" + methodPattern;
        }
        return description.length() == 0 ? "*" : description;
    }

    @Override
    public boolean equals( Object o )
    {
        return this == o || o != null && getClass() == o.getClass() && test.equals( ( (RequestedTest) o ).test );
    }

    @Override
    public int hashCode()
    {
        return test.hashCode();
    }

    private String classFile( Class<?> realTestClass )
    {
        return realTestClass.getName().replace( '.', '/' ) + CLASS_FILE_EXTENSION;
    }
}
