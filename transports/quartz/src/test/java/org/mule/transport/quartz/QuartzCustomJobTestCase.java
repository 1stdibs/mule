/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.transport.quartz;

import static org.junit.Assert.assertTrue;
import org.mule.functional.junit4.FunctionalTestCase;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.Test;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

public class QuartzCustomJobTestCase extends FunctionalTestCase
{

    @Override
    protected String getConfigFile()
    {
        return "quartz-custom-job-flow.xml";
    }

    @Test
    public void testCustomJob() throws Exception
    {
        CountDownLatch eventLatch = muleContext.getRegistry().lookupObject("latch");

        // we wait up to 60 seconds here which is WAY too long for one tick but it seems that 
        // "sometimes" it takes a very long time for Quartz go kick in. Once it starts 
        // ticking everything is fine.
        assertTrue(eventLatch.await(60000, TimeUnit.MILLISECONDS));
    }
    
    public static class MockJob implements Job
    {
        private CountDownLatch eventLatch;

        public MockJob(CountDownLatch latch)
        {
            eventLatch = latch;
        }
        
        public void execute(JobExecutionContext context) throws JobExecutionException
        {
            eventLatch.countDown();
        }
    }
}


