/*
 * $Id: OutboundEndpointFactoryBean.java 11079 2008-02-27 15:52:01Z tcarlson $
 * --------------------------------------------------------------------------------------
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 *
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */

package org.mule.config.spring.factories;

import org.mule.api.MuleContext;
import org.mule.api.NamedObject;
import org.mule.api.config.MuleConfiguration;
import org.mule.api.config.ThreadingProfile;
import org.mule.api.context.MuleContextAware;
import org.mule.api.processor.MessageProcessor;
import org.mule.api.processor.MessageProcessorBuilder;
import org.mule.processor.AsyncInterceptingMessageProcessor;
import org.mule.processor.builder.InterceptingChainMessageProcessorBuilder;

import org.springframework.beans.factory.FactoryBean;

import java.util.List;

public class AsyncMessageProcessorsFactoryBean implements FactoryBean, MuleContextAware, NamedObject
{

    protected MuleContext muleContext;

    protected List messageProcessors;
    protected ThreadingProfile threadingProfile;
    protected String name;

    public Class getObjectType()
    {
        return MessageProcessor.class;
    }

    public void setThreadingProfile(ThreadingProfile threadingProfile)
    {
        this.threadingProfile = threadingProfile;
    }

    public void setMessageProcessors(List messageProcessors)
    {
        this.messageProcessors = messageProcessors;
    }

    public Object getObject() throws Exception
    {
        if (threadingProfile == null)
        {
            threadingProfile = muleContext.getDefaultThreadingProfile();
        }

        InterceptingChainMessageProcessorBuilder builder = new InterceptingChainMessageProcessorBuilder();
        final MuleConfiguration config = muleContext.getConfiguration();
        final boolean containerMode = config.isContainerMode();
        final String threadPrefix = containerMode
                                        ? String.format("[%s].%s.processor.async", config.getId(), name)
                                        : String.format("%s.processor.async", name);
        AsyncInterceptingMessageProcessor asyncProcessor = new AsyncInterceptingMessageProcessor(threadingProfile,
                                                                                                 threadPrefix,
                                                                                                 config.getShutdownTimeout());
        builder.chain(asyncProcessor);
        for (Object processor : messageProcessors)
        {
            if (processor instanceof MessageProcessor)
            {
                builder.chain((MessageProcessor) processor);
            }
            else if (processor instanceof MessageProcessorBuilder)
            {
                builder.chain((MessageProcessorBuilder) processor);
            }
            else
            {
                throw new IllegalArgumentException(
                    "MessageProcessorBuilder should only have MessageProcessor's or MessageProcessorBuilder's configured");
            }
        }
        return builder.build();
    }

    public boolean isSingleton()
    {
        return false;
    }

    public void setMuleContext(MuleContext context)
    {
        this.muleContext = context;
    }

    public String getName()
    {
        return name;
    }

    public void setName(String name)
    {
        this.name = name;
    }

}
