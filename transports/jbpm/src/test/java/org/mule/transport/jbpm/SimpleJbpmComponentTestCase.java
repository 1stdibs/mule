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
import org.mule.tck.FunctionalTestCase;
import org.mule.transport.bpm.BPMS;
import org.mule.transport.bpm.ProcessConnector;

import java.util.HashMap;
import java.util.Map;

/**
 * Tests jBPM component with a simple process.
 */
public class SimpleJbpmComponentTestCase extends FunctionalTestCase
{
    protected String getConfigResources()
    {
        return "jbpm-component-functional-test.xml";
    }

    public void testSimpleProcess() throws Exception 
    {
        BPMS bpms = muleContext.getRegistry().lookupObject(BPMS.class);
        assertNotNull(bpms);

        MuleClient client = new MuleClient(muleContext);
        try
        {
            // Create a new process.
            MuleMessage response = client.send("vm://simple", "data", null);
            Object process = response.getPayload();

            String processId = (String)bpms.getId(process);
            // The process should be started and in a wait state.
            assertFalse(processId == null);
            assertEquals("dummyState", bpms.getState(process));

            // Advance the process one step.
            Map props = new HashMap();
            props.put(ProcessConnector.PROPERTY_PROCESS_ID, processId);
            response = client.send("vm://simple", null, props);
            process = response.getPayload();

            // The process should have ended.
            assertTrue(bpms.hasEnded(process));
        }
        finally
        {
            client.dispose();
        }
    }
}
