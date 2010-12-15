/*
 * $Id$
 * --------------------------------------------------------------------------------------
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 *
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */

package org.mule.session;

import org.mule.api.MuleException;
import org.mule.api.MuleMessage;
import org.mule.api.MuleSession;
import org.mule.api.config.MuleProperties;
import org.mule.api.transport.SessionHandler;

import java.io.Serializable;

import org.apache.commons.lang.SerializationUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * A session handler used to store and retrieve session information on an
 * event. The MuleSession information is stored as a header on the message (does not
 * support Tcp, Udp, etc. unless the MuleMessage object is serialised across the
 * wire). The session is stored in the "MULE_SESSION" property as an array of bytes (byte[])
 */
public class SerializeOnlySessionHandler implements SessionHandler
{
    protected static Log logger = LogFactory.getLog(SerializeOnlySessionHandler.class);

    public MuleSession retrieveSessionInfoFromMessage(MuleMessage message) throws MuleException
    {
        MuleSession session = null;
        byte[] serializedSession = message.getInboundProperty(MuleProperties.MULE_SESSION_PROPERTY);

        if (serializedSession != null)
        {
            session = (MuleSession) SerializationUtils.deserialize(serializedSession);
        }
        return session;
    }

    /**
     * @deprecated Use retrieveSessionInfoFromMessage(MuleMessage message) instead
     */
    public void retrieveSessionInfoFromMessage(MuleMessage message, MuleSession session) throws MuleException
    {
        session = retrieveSessionInfoFromMessage(message);
    }

    public void storeSessionInfoToMessage(MuleSession session, MuleMessage message) throws MuleException
    {
        byte[] serializedSession = SerializationUtils.serialize(removeNonSerializableProperties(session));
        
        if (logger.isDebugEnabled())
        {
            logger.debug("Adding serialized Session header to message: " + serializedSession);
        }
        message.setOutboundProperty(MuleProperties.MULE_SESSION_PROPERTY, serializedSession);
    }
    
    protected MuleSession removeNonSerializableProperties(MuleSession session)
    {
        for (String key : session.getPropertyNamesAsSet())
        {
            final Object value = session.getProperty(key);
            if (!(value instanceof Serializable))
            {
                logger.warn(String.format("Property %s is not serializable, it will not be preserved " +
                                          "as part of the MuleSession", key));
                session.removeProperty(key);
            }
        }
        return session;
    }
    
    /**
     * @deprecated This method is no longer needed and will be removed in the next major release
     */
    public String getSessionIDKey()
    {
        return "ID";
    }
}
