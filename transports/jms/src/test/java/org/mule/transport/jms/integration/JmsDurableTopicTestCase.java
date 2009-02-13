/*
 * $Id$
 * --------------------------------------------------------------------------------------
 * Copyright (c) MuleSource, Inc.  All rights reserved.  http://www.mulesource.com
 *
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.transport.jms.integration;

import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.Topic;
import javax.jms.ConnectionFactory;
import javax.jms.Connection;
import javax.jms.Session;

import org.junit.Test;

public class JmsDurableTopicTestCase extends AbstractJmsFunctionalTestCase
{
    public static final String TOPIC_QUEUE_NAME = "durable.broadcast";
    private String clientId;


    public JmsDurableTopicTestCase(JmsVendorConfiguration config)
    {
        super(config);
    }

    protected String getConfigResources()
    {
        return "integration/jms-durable-topic.xml";
    }
    
    @Test
    public void testProviderDurableSubscriber() throws Exception
    {
        setClientId("Client1");
        receive(scenarioNotReceive);
        setClientId("Client2");
        receive(scenarioNotReceive);

        setClientId("Sender");
        send(scenarioNonTx);

        setClientId("Client1");
        receive(scenarioNonTx);
        receive(scenarioNotReceive);
        setClientId("Client2");
        receive(scenarioNonTx);
        receive(scenarioNotReceive);
    }

    Scenario scenarioNonTx = new NonTransactedScenario()
    {
        public String getOutputDestinationName()
        {
            return TOPIC_QUEUE_NAME;
        }
    };

    Scenario scenarioNotReceive = new ScenarioNotReceive()
    {
        public String getOutputDestinationName()
        {
            return TOPIC_QUEUE_NAME;
        }
    };

    public Message receive(Scenario scenario) throws Exception
    {
        Connection connection = null;
        try
        {
            ConnectionFactory factory = getConnectionFactory(true, false);
            connection = factory.createConnection();
            connection.setClientID(getClientId());
            connection.start();
            Session session = null;
            try
            {
                session = connection.createSession(scenario.isTransacted(), scenario.getAcknowledge());
                Topic destination = session.createTopic(scenario.getOutputDestinationName());
                MessageConsumer consumer = null;
                try
                {
                    consumer = session.createDurableSubscriber(destination, getClientId());
                    return scenario.receive(session, consumer);
                }
                finally
                {
                    if (consumer != null)
                    {
                        consumer.close();
                    }
                }
            }
            finally
            {
                if (session != null)
                {
                    session.close();
                }
            }
        }
        finally
        {
            if (connection != null)
            {
                connection.close();
            }
        }
    }

    public String getClientId()
    {
        return clientId;
    }

    public void setClientId(String clientId)
    {
        this.clientId = clientId;
    }
}
