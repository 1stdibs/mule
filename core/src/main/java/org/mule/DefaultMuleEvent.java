/*
 * $Id$
 * --------------------------------------------------------------------------------------
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 *
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */

package org.mule;

import org.mule.api.DefaultMuleException;
import org.mule.api.MuleContext;
import org.mule.api.MuleEvent;
import org.mule.api.MuleException;
import org.mule.api.MuleMessage;
import org.mule.api.MuleSession;
import org.mule.api.NamedObject;
import org.mule.api.ThreadSafeAccess;
import org.mule.api.config.MuleProperties;
import org.mule.api.construct.FlowConstruct;
import org.mule.api.endpoint.InboundEndpoint;
import org.mule.api.security.Credentials;
import org.mule.api.source.IdentifiableMessageSource;
import org.mule.api.transformer.DataType;
import org.mule.api.transformer.TransformerException;
import org.mule.api.transport.ReplyToHandler;
import org.mule.config.i18n.CoreMessages;
import org.mule.management.stats.ProcessingTime;
import org.mule.security.MuleCredentials;
import org.mule.session.DefaultMuleSession;
import org.mule.transformer.types.DataTypeFactory;
import org.mule.transport.DefaultReplyToHandler;
import org.mule.util.UUID;
import org.mule.util.store.DeserializationPostInitialisable;

import java.io.OutputStream;
import java.net.URI;
import java.util.EventObject;
import java.util.Iterator;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * <code>DefaultMuleEvent</code> represents any data event occurring in the Mule environment. All data sent or
 * received within the Mule environment will be passed between components as an MuleEvent.
 * <p/>
 * The MuleEvent holds some data and provides helper methods for obtaining the data in a format that the
 * receiving Mule component understands. The event can also maintain any number of properties that can be set
 * and retrieved by Mule components.
 */

public class DefaultMuleEvent extends EventObject
    implements MuleEvent, ThreadSafeAccess, DeserializationPostInitialisable
{
    /**
     * Serial version
     */
    private static final long serialVersionUID = 1L;

    /**
     * logger used by this class
     */
    private static Log logger = LogFactory.getLog(DefaultMuleEvent.class);

    /**
     * the Universally Unique ID for the event
     */
    private final String id;

    /**
     * The payload message used to read the payload of the event
     */
    private final MuleMessage message;

    private final MuleSession session;

    private boolean stopFurtherProcessing = false;

    private int timeout = TIMEOUT_NOT_SET_VALUE;

    private transient ResponseOutputStream outputStream;

    private transient Object transformedMessage;

    private Credentials credentials;

    protected String[] ignoredPropertyOverrides = new String[]{MuleProperties.MULE_METHOD_PROPERTY};

    private ProcessingTime processingTime;

    private final MessageExchangePattern exchangePattern;

    private final boolean transacted;

    private final ReplyToHandler replyToHandler;

    private final URI messageSourceURI;

    private final String messageSourceName;

    private final String encoding;

    public DefaultMuleEvent(MuleMessage message,
                            MessageExchangePattern exchangePattern,
                            FlowConstruct flowConstruct)
    {
        this(message, exchangePattern, new DefaultMuleSession(flowConstruct, message.getMuleContext()), null);
    }

    public DefaultMuleEvent(MuleMessage message, MessageExchangePattern exchangePattern, MuleSession session)
    {
        this(message, exchangePattern, session, null);
    }

    public DefaultMuleEvent(MuleMessage message,
                            MessageExchangePattern exchangePattern,
                            MuleSession session,
                            ResponseOutputStream outputStream)
    {
        super(message.getPayload());
        this.message = message;
        this.id = generateEventId();
        this.session = session;
        this.exchangePattern = exchangePattern;
        this.transacted = false;
        URI uri = URI.create("dynamic://null");
        this.messageSourceURI = uri;
        this.messageSourceName = uri.toString();
        this.timeout = message.getMuleContext().getConfiguration().getDefaultResponseTimeout();
        this.encoding = message.getMuleContext().getConfiguration().getDefaultEncoding();
        this.replyToHandler = null;
    }

    public DefaultMuleEvent(MuleMessage message,
                            IdentifiableMessageSource messageSource,
                            MessageExchangePattern exchangePattern,
                            MuleSession session,
                            ResponseOutputStream outputStream)
    {
        super(message.getPayload());
        this.message = message;
        this.id = generateEventId();
        this.session = session;
        this.exchangePattern = exchangePattern;
        this.transacted = false;
        this.messageSourceURI = messageSource.getURI();
        if (messageSource instanceof NamedObject)
        {
            this.messageSourceName = ((NamedObject) messageSource).getName();
        }
        else
        {
            this.messageSourceName = messageSource.getURI().toString();
        }
        this.timeout = message.getMuleContext().getConfiguration().getDefaultResponseTimeout();
        this.encoding = message.getMuleContext().getConfiguration().getDefaultEncoding();
        this.replyToHandler = null;
    }

    public DefaultMuleEvent(MuleMessage message, InboundEndpoint endpoint, MuleSession session)
    {
        this(message, endpoint, session, null, null, null);
    }

    public DefaultMuleEvent(MuleMessage message,
                            InboundEndpoint endpoint,
                            MuleSession session,
                            ResponseOutputStream outputStream,
                            ReplyToHandler replyToHandler)
    {
        this(message, endpoint, session, outputStream, null, replyToHandler);
    }

    public DefaultMuleEvent(MuleMessage message,
                            InboundEndpoint endpoint,
                            MuleSession session,
                            ResponseOutputStream outputStream,
                            ProcessingTime time,
                            ReplyToHandler replyToHandler)
    {
        super(message.getPayload());
        this.message = message;
        this.messageSourceURI = endpoint.getEndpointURI().getUri();
        this.messageSourceName = endpoint.getName();
        this.timeout = endpoint.getResponseTimeout();
        this.encoding = endpoint.getEncoding();
        this.session = session;
        this.id = generateEventId();
        this.outputStream = outputStream;
        this.exchangePattern = endpoint.getExchangePattern();
        transacted = endpoint.getTransactionConfig().isTransacted();
        fillProperties(endpoint);
        this.processingTime = time != null ? time : ProcessingTime.newInstance(this.session,
            message.getMuleContext());
        this.replyToHandler = replyToHandler;
    }

    /**
     * A helper constructor used to rewrite an event payload
     * 
     * @param message The message to use as the current payload of the event
     * @param rewriteEvent the previous event that will be used as a template for this event
     */
    public DefaultMuleEvent(MuleMessage message, MuleEvent rewriteEvent)
    {
        this(message, rewriteEvent, rewriteEvent.getSession());
    }

    /**
     * A helper constructor used to rewrite an event payload
     * 
     * @param message The message to use as the current payload of the event
     * @param rewriteEvent the previous event that will be used as a template for this event
     */
    public DefaultMuleEvent(MuleMessage message, MuleEvent rewriteEvent, MuleSession session)
    {
        super(message.getPayload());
        this.message = message;
        this.id = rewriteEvent.getId();
        this.session = session;
        this.messageSourceURI = rewriteEvent.getMessageSourceURI();
        this.messageSourceName = rewriteEvent.getMessageSourceName();
        this.timeout = rewriteEvent.getTimeout();
        this.encoding = rewriteEvent.getEncoding();
        this.outputStream = (ResponseOutputStream) rewriteEvent.getOutputStream();
        this.exchangePattern = rewriteEvent.getExchangePattern();
        transacted = rewriteEvent.isTransacted();
        if (rewriteEvent instanceof DefaultMuleEvent)
        {
            this.transformedMessage = ((DefaultMuleEvent) rewriteEvent).getCachedMessage();
            this.processingTime = ((DefaultMuleEvent) rewriteEvent).processingTime;
        }
        else
        {
            this.processingTime = ProcessingTime.newInstance(this.session, message.getMuleContext());
        }
        this.replyToHandler = rewriteEvent.getReplyToHandler();
        this.credentials = rewriteEvent.getCredentials();
    }

    protected void fillProperties(InboundEndpoint endpoint)
    {
        if (endpoint != null && endpoint.getProperties() != null)
        {
            for (Iterator<?> iterator = endpoint.getProperties().keySet().iterator(); iterator.hasNext();)
            {
                String prop = (String) iterator.next();

                // don't overwrite property on the message
                if (!ignoreProperty(prop))
                {
                    // inbound endpoint properties are in the invocation scope
                    Object value = endpoint.getProperties().get(prop);
                    message.setInvocationProperty(prop, value);
                }
            }
        }

        setCredentials(endpoint);
    }

    /**
     * This method is used to determine if a property on the previous event should be ignored for the next
     * event. This method is here because we don't have proper scoped handling of meta data yet The rules are
     * <ol>
     * <li>If a property is already set on the current event don't overwrite with the previous event value
     * <li>If the property name appears in the ignoredPropertyOverrides list, then we always set it on the new
     * event
     * </ol>
     * 
     * @param key The name of the property to ignore
     * @return true if the property should be ignored, false otherwise
     */
    protected boolean ignoreProperty(String key)
    {
        if (key == null || key.startsWith(MuleProperties.ENDPOINT_PROPERTY_PREFIX))
        {
            return true;
        }

        for (int i = 0; i < ignoredPropertyOverrides.length; i++)
        {
            if (key.equals(ignoredPropertyOverrides[i]))
            {
                return false;
            }
        }

        return null != message.getOutboundProperty(key);
    }

    protected void setCredentials(InboundEndpoint endpoint)
    {
        if (null != endpoint && null != endpoint.getEndpointURI()
            && null != endpoint.getEndpointURI().getUserInfo())
        {
            final String userName = endpoint.getEndpointURI().getUser();
            final String password = endpoint.getEndpointURI().getPassword();
            if (password != null && userName != null)
            {
                credentials = new MuleCredentials(userName, password.toCharArray());
            }
        }
    }

    @Override
    public Credentials getCredentials()
    {
        MuleCredentials creds = message.getOutboundProperty(MuleProperties.MULE_CREDENTIALS_PROPERTY);
        return (credentials != null ? credentials : creds);
    }

    Object getCachedMessage()
    {
        return transformedMessage;
    }

    @Override
    public MuleMessage getMessage()
    {
        return message;
    }

    @Override
    public byte[] getMessageAsBytes() throws DefaultMuleException
    {
        try
        {
            return message.getPayloadAsBytes();
        }
        catch (Exception e)
        {
            throw new DefaultMuleException(CoreMessages.cannotReadPayloadAsBytes(message.getPayload()
                .getClass()
                .getName()), e);
        }
    }

    @Override
    @SuppressWarnings("cast")
    public <T> T transformMessage(Class<T> outputType) throws TransformerException
    {
        return (T) transformMessage(DataTypeFactory.create(outputType));
    }

    @Override
    public <T> T transformMessage(DataType<T> outputType) throws TransformerException
    {
        if (outputType == null)
        {
            throw new TransformerException(CoreMessages.objectIsNull("outputType"));
        }
        return message.getPayload(outputType);
    }

    /**
     * This method will attempt to convert the transformed message into an array of bytes It will first check
     * if the result of the transformation is a byte array and return that. Otherwise if the the result is a
     * string it will serialized the CONTENTS of the string not the String object. finally it will check if
     * the result is a Serializable object and convert that to an array of bytes.
     * 
     * @return a byte[] representation of the message
     * @throws TransformerException if an unsupported encoding is being used or if the result message is not a
     *             String byte[] or Seializable object
     * @deprecated use {@link #transformMessage(org.mule.api.transformer.DataType)} instead
     */
    @Override
    @Deprecated
    public byte[] transformMessageToBytes() throws TransformerException
    {
        return transformMessage(DataType.BYTE_ARRAY_DATA_TYPE);
    }

    /**
     * Returns the message transformed into it's recognised or expected format and then into a String. The
     * transformer used is the one configured on the endpoint through which this event was received.
     * 
     * @return the message transformed into it's recognised or expected format as a Strings.
     * @throws org.mule.api.transformer.TransformerException if a failure occurs in the transformer
     * @see org.mule.api.transformer.Transformer
     */
    @Override
    public String transformMessageToString() throws TransformerException
    {
        return transformMessage(DataTypeFactory.createWithEncoding(String.class, getEncoding()));
    }

    @Override
    public String getMessageAsString() throws MuleException
    {
        return getMessageAsString(getEncoding());
    }

    /**
     * Returns the message contents for logging
     * 
     * @param encoding the encoding to use when converting bytes to a string, if necessary
     * @return the message contents as a string
     * @throws org.mule.api.MuleException if the message cannot be converted into a string
     */
    @Override
    public String getMessageAsString(String encoding) throws MuleException
    {
        try
        {
            return message.getPayloadForLogging(encoding);
        }
        catch (Exception e)
        {
            throw new DefaultMuleException(CoreMessages.cannotReadPayloadAsString(message.getClass()
                .getName()), e);
        }
    }

    @Override
    public String getId()
    {
        return id;
    }

    /**
     * @see #getMessage()
     * @deprecated use appropriate scope-aware calls on the MuleMessage (via event.getMessage())
     */
    @Override
    @Deprecated
    public Object getProperty(String name)
    {
        throw new UnsupportedOperationException(
            "Method's behavior has changed in Mule 3, use "
                            + "event.getMessage() and suitable scope-aware property access "
                            + "methods on it");
    }

    /**
     * @see #getMessage()
     * @deprecated use appropriate scope-aware calls on the MuleMessage (via event.getMessage())
     */
    @Override
    @Deprecated
    public Object getProperty(String name, Object defaultValue)
    {
        throw new UnsupportedOperationException(
            "Method's behavior has changed in Mule 3, use "
                            + "event.getMessage() and suitable scope-aware property access "
                            + "methods on it");
    }

    @Override
    public String toString()
    {
        StringBuffer buf = new StringBuffer(64);
        buf.append("MuleEvent: ").append(getId());
        buf.append(", stop processing=").append(isStopFurtherProcessing());
        buf.append(", ").append(messageSourceURI);

        return buf.toString();
    }

    protected String generateEventId()
    {
        return UUID.getUUID();
    }

    @Override
    public MuleSession getSession()
    {
        return session;
    }

    /**
     * Gets the recipient service of this event
     */
    @Override
    public FlowConstruct getFlowConstruct()
    {
        return session.getFlowConstruct();
    }

    /**
     * Determines whether the default processing for this event will be executed
     * 
     * @return Returns the stopFurtherProcessing.
     */
    @Override
    public boolean isStopFurtherProcessing()
    {
        return stopFurtherProcessing;
    }

    /**
     * Setting this parameter will stop the Mule framework from processing this event in the standard way.
     * This allow for client code to override default behaviour. The common reasons for doing this are - 1.
     * The service has more than one send endpoint configured; the service must dispatch to other prviders
     * programmatically by using the service on the current event 2. The service doesn't send the current
     * event out through a endpoint. i.e. the processing of the event stops in the uMO.
     * 
     * @param stopFurtherProcessing The stopFurtherProcessing to set.
     */
    @Override
    public void setStopFurtherProcessing(boolean stopFurtherProcessing)
    {
        this.stopFurtherProcessing = stopFurtherProcessing;
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o)
        {
            return true;
        }
        if (!(o instanceof DefaultMuleEvent))
        {
            return false;
        }

        final DefaultMuleEvent event = (DefaultMuleEvent) o;

        if (message != null ? !message.equals(event.message) : event.message != null)
        {
            return false;
        }
        return id.equals(event.id);
    }

    @Override
    public int hashCode()
    {
        return 29 * id.hashCode() + (message != null ? message.hashCode() : 0);
    }

    @Override
    public int getTimeout()
    {
        if (timeout == TIMEOUT_NOT_SET_VALUE)
        {
            return message.getMuleContext().getConfiguration().getDefaultResponseTimeout();
        }
        else
        {
            return timeout;
        }
    }

    @Override
    public void setTimeout(int timeout)
    {
        if (timeout != TIMEOUT_NOT_SET_VALUE)
        {
            this.timeout = timeout;
        }
    }

    /**
     * An output stream can optionally be used to write response data to an incoming message.
     * 
     * @return an output strem if one has been made available by the message receiver that received the
     *         message
     */
    @Override
    public OutputStream getOutputStream()
    {
        return outputStream;
    }

    /**
     * Invoked after deserialization. This is called when the marker interface
     * {@link org.mule.util.store.DeserializationPostInitialisable} is used. This will get invoked after the
     * object has been deserialized passing in the current MuleContext when using either
     * {@link org.mule.transformer.wire.SerializationWireFormat},
     * {@link org.mule.transformer.wire.SerializedMuleMessageWireFormat} or the
     * {@link org.mule.transformer.simple.ByteArrayToSerializable} transformer.
     * 
     * @param muleContext the current muleContext instance
     * @throws MuleException if there is an error initializing
     */
    @SuppressWarnings({"unused", "unchecked"})
    private void initAfterDeserialisation(MuleContext muleContext) throws MuleException
    {
        if (session instanceof DefaultMuleSession)
        {
            ((DefaultMuleSession) session).initAfterDeserialisation(muleContext);
        }
        if (message instanceof DefaultMuleMessage)
        {
            ((DefaultMuleMessage) message).initAfterDeserialisation(muleContext);
        }
        if (replyToHandler instanceof DefaultReplyToHandler)
        {
            ((DefaultReplyToHandler) replyToHandler).initAfterDeserialisation(muleContext);
        }
    }

    /**
     * Gets the encoding for this message. First it looks to see if encoding has been set on the endpoint, if
     * not it will check the message itself and finally it will fall back to the Mule global configuration for
     * encoding which cannot be null.
     * 
     * @return the encoding for the event
     */
    @Override
    public String getEncoding()
    {
        if (message.getEncoding() != null)
        {
            return message.getEncoding();
        }
        else
        {
            return encoding;
        }
    }

    @Override
    public MuleContext getMuleContext()
    {
        return message.getMuleContext();
    }

    @Override
    public ThreadSafeAccess newThreadCopy()
    {
        if (message instanceof ThreadSafeAccess)
        {
            DefaultMuleEvent copy = new DefaultMuleEvent(
                (MuleMessage) ((ThreadSafeAccess) message).newThreadCopy(), this);
            copy.resetAccessControl();
            return copy;
        }
        else
        {
            return this;
        }
    }

    @Override
    public void resetAccessControl()
    {
        if (message instanceof ThreadSafeAccess)
        {
            ((ThreadSafeAccess) message).resetAccessControl();
        }
    }

    @Override
    public void assertAccess(boolean write)
    {
        if (message instanceof ThreadSafeAccess)
        {
            ((ThreadSafeAccess) message).assertAccess(write);
        }
    }

    @Override
    @Deprecated
    public Object transformMessage() throws TransformerException
    {
        logger.warn("Deprecation warning: MuleEvent.transformMessage does nothing in Mule 3.x.  The message is already transformed before the event reaches a component");
        return message.getPayload();
    }

    @Override
    public ProcessingTime getProcessingTime()
    {
        return processingTime;
    }

    @Override
    public MessageExchangePattern getExchangePattern()
    {
        return exchangePattern;
    }

    @Override
    public boolean isTransacted()
    {
        return transacted;
    }

    @Override
    public URI getMessageSourceURI()
    {
        return messageSourceURI;
    }

    @Override
    public String getMessageSourceName()
    {
        return messageSourceName;
    }

    @Override
    public ReplyToHandler getReplyToHandler()
    {
        return replyToHandler;
    }
}
