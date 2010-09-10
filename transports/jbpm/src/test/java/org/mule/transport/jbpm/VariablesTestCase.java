/*
 * $Id$
 * --------------------------------------------------------------------------------------
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 *
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */

package org.mule.transport.jbpm;

import org.mule.api.MuleMessage;
import org.mule.module.client.MuleClient;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class VariablesTestCase extends AbstractJbpmTestCase
{
    static
    {
        System.setProperty(PROPERTY_MULE_TEST_TIMEOUT, "300");
    }

    @Override
    protected String getConfigResources()
    {
        return "jbpm-functional-test.xml";
    }

    public void testVariables() throws Exception
    {
        MuleClient client = new MuleClient(muleContext);
        try
        {
            Map<String, Object> props = new HashMap<String, Object>();
            props.put("foo", "bar");
            MuleMessage response = client.send("bpm://variables", "data", props);
            String processId = (String)bpms.getId(response.getPayload());
            assertNotNull(processId);

            response = client.request("vm://queueA", 5000);
            assertNotNull(response);
            assertEquals("bar", response.getInboundProperty("foo"));
            assertEquals(0.75, response.getInboundProperty("fraction"));

            // Advance the process
            props = new HashMap<String, Object>();
            props.put("straw", "berry");
            props.put("time", new Date());
            response = client.send("bpm://variables/" + processId, "data", props);
            
            response = client.request("vm://queueB", 5000);
            assertNotNull(response);
            assertEquals("bar", response.getInboundProperty("foo"));
            assertEquals(0.75, response.getInboundProperty("fraction"));
            assertEquals("berry", response.getInboundProperty("straw"));
            final Object o = response.getInboundProperty("time");
            assertTrue(o instanceof Date);
        }
        finally
        {
            client.dispose();
        }
    }
}
