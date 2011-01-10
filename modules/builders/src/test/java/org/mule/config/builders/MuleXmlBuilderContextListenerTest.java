/*
 * $Id$
 * --------------------------------------------------------------------------------------
 * Copyright (c) MuleSource, Inc.  All rights reserved.  http://www.mulesource.com
 *
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */

package org.mule.config.builders;

import org.mule.MuleServer;

import javax.servlet.ServletContext;

import junit.framework.TestCase;

import org.mockito.Mockito;
import org.springframework.web.context.WebApplicationContext;

public class MuleXmlBuilderContextListenerTest extends TestCase
{

    private MuleXmlBuilderContextListener listener;
    private ServletContext context;

    public void setUp() throws Exception
    {
        super.setUp();
        listener = new MuleXmlBuilderContextListener();
        context = Mockito.mock(ServletContext.class);
    }

    public void tearDown() throws Exception
    {
        super.tearDown();
        listener.muleContext.stop();
        MuleServer.setMuleContext(null);
    }

    public void testNoMuleAppProperties()
    {
        Mockito.when(context.getInitParameter(MuleXmlBuilderContextListener.INIT_PARAMETER_MULE_CONFIG))
            .thenReturn("mule-config.xml");
        Mockito.when(context.getAttribute(WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE))
            .thenReturn(null);

        listener.initialize(context);

        Mockito.verify(context).getInitParameter(MuleXmlBuilderContextListener.INIT_PARAMETER_MULE_CONFIG);
        Mockito.verify(context).getAttribute(WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE);

        assertEquals("./.mule", listener.muleContext.getConfiguration().getWorkingDirectory());
    }

    public void testWithImplicitMuleAppProperties()
    {
        Mockito.when(context.getInitParameter(MuleXmlBuilderContextListener.INIT_PARAMETER_MULE_CONFIG))
            .thenReturn("org/mule/config/builders/mule-config.xml");
        Mockito.when(context.getAttribute(WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE))
            .thenReturn(null);

        listener.initialize(context);

        Mockito.verify(context).getInitParameter(MuleXmlBuilderContextListener.INIT_PARAMETER_MULE_CONFIG);
        Mockito.verify(context).getAttribute(WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE);

        assertTrue(listener.muleContext.getConfiguration().getWorkingDirectory().endsWith(
            "modules/builders/target/.appTmp"));
    }

    public void testWithExplicitMuleAppProperties()
    {
        Mockito.when(context.getInitParameter(MuleXmlBuilderContextListener.INIT_PARAMETER_MULE_CONFIG))
            .thenReturn("org/mule/config/builders/mule-config.xml");
        Mockito.when(context.getInitParameter(MuleXmlBuilderContextListener.INIT_PARAMETER_MULE_APP_CONFIG))
        .thenReturn("org/mule/config/builders/mule-app-ppp.properties");
        Mockito.when(context.getAttribute(WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE))
            .thenReturn(null);

        listener.initialize(context);

        Mockito.verify(context).getInitParameter(MuleXmlBuilderContextListener.INIT_PARAMETER_MULE_CONFIG);
        Mockito.verify(context).getAttribute(WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE);

        assertTrue(listener.muleContext.getConfiguration().getWorkingDirectory().endsWith(
            "modules/builders/target/.appTmp2"));
    }
}
