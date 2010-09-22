/*
 * $Id$
 * --------------------------------------------------------------------------------------
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 *
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.transport.bpm.config;

import org.mule.tck.FunctionalTestCase;
import org.mule.transport.bpm.BPMS;
import org.mule.transport.bpm.ProcessComponent;
import org.mule.transport.bpm.ProcessConnector;
import org.mule.transport.bpm.test.TestBpms;


/**
 * Tests the Spring XML namespace for the BPM transport.
 */
public class BpmNamespaceHandlerTestCase extends FunctionalTestCase
{
    protected String getConfigResources()
    {
        return "bpm-namespace-config.xml";
    }

    public void testDefaultsConnector() throws Exception
    {
        ProcessConnector c = (ProcessConnector)muleContext.getRegistry().lookupConnector("bpmConnectorDefaults");
        assertNotNull(c);
        
        assertFalse(c.isAllowGlobalReceiver());
        assertNull(c.getProcessIdField());
        
        BPMS bpms = c.getBpms();
        assertNotNull(bpms);
        assertEquals(TestBpms.class, bpms.getClass());
        assertEquals("bar", ((TestBpms) bpms).getFoo());
        
        assertTrue(c.isConnected());
        assertTrue(c.isStarted());
    }
    
    public void testDefaultsComponent() throws Exception
    {
        ProcessComponent c = (ProcessComponent) muleContext.getRegistry().lookupService("Service1").getComponent();
        assertNotNull(c);
        
        assertEquals("test.def", c.getResource());
        assertNull(c.getProcessIdField());
        
        // BPMS gets set explicitly in config
        BPMS bpms = c.getBpms();
        assertNotNull(bpms);
        assertEquals(TestBpms.class, bpms.getClass());
        assertEquals("bar", ((TestBpms) bpms).getFoo());
    }
    
    public void testConfigConnector() throws Exception
    {
        ProcessConnector c = (ProcessConnector)muleContext.getRegistry().lookupConnector("bpmConnector1");
        assertNotNull(c);
        
        assertTrue(c.isAllowGlobalReceiver());
        assertEquals("myId", c.getProcessIdField());
        
        BPMS bpms = c.getBpms();
        assertNotNull(bpms);
        assertEquals(TestBpms.class, bpms.getClass());
        assertEquals("bar", ((TestBpms) bpms).getFoo());

        assertTrue(c.isConnected());
        assertTrue(c.isStarted());
    }    

    public void testConfigComponent() throws Exception
    {
        ProcessComponent c = (ProcessComponent) muleContext.getRegistry().lookupService("Service2").getComponent();
        assertNotNull(c);
        
        assertEquals("test.def", c.getResource());
        assertEquals("myId", c.getProcessIdField());
        
        // BPMS gets set implicitly via MuleRegistry.lookupObject(BPMS.class)
        BPMS bpms = c.getBpms();
        assertNotNull(bpms);
        assertEquals(TestBpms.class, bpms.getClass());
        assertEquals("bar", ((TestBpms) bpms).getFoo());
    }
    
}
