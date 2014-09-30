/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.transport.quartz;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import org.mule.api.client.MuleClient;
import org.mule.functional.functional.CountdownCallback;
import org.mule.functional.functional.FunctionalTestComponent;
import org.mule.functional.junit4.FunctionalTestCase;

import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

public class QuartzDispatchJobTestCase extends FunctionalTestCase
{

    @Override
    protected String getConfigFile()
    {
        return "quartz-dispatch-flow.xml";
    }

    @Test
    public void testMuleClientDispatchJob() throws Exception
    {
        FunctionalTestComponent component = getFunctionalTestComponent("scheduledService");
        assertNotNull(component);
        CountdownCallback count = new CountdownCallback(3);
        component.setEventCallback(count);

        MuleClient client = muleContext.getClient();
        client.dispatch("vm://quartz.scheduler", "quartz test", null);
        assertTrue(count.await(5000));
    }

    @Test
    public void testMuleClientDispatchJobWithExpressionAddress() throws Exception
    {
        FunctionalTestComponent component = getFunctionalTestComponent("expressionScheduledService");
        assertNotNull(component);
        CountdownCallback count = new CountdownCallback(3);
        component.setEventCallback(count);

        Map<String, Object> props = new HashMap<String, Object>();
        props.put("ENDPOINT_NAME", "quartz.expression.in");

        org.mule.api.client.MuleClient client = muleContext.getClient();
        client.dispatch("vm://quartz.expression.scheduler", "quartz test", props);
        assertTrue(count.await(5000));
    }
}
