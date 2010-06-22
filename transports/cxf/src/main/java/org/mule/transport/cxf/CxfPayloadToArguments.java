/*
 * $Id$
 * --------------------------------------------------------------------------------------
 * Copyright (c) MuleSource, Inc.  All rights reserved.  http://www.mulesource.com
 *
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */

package org.mule.transport.cxf;

import org.mule.api.endpoint.MalformedEndpointException;
import org.mule.api.endpoint.OutboundEndpoint;
import org.mule.transport.NullPayload;
import org.mule.transport.cxf.i18n.CxfMessages;

/**
 * This enum defines the strategies to convert a Payload to an array of arguments
 * that will be used to call the webservice in
 * {@link CxfMessageDispatcher#doSendWithClient(org.mule.api.MuleEvent)} and in
 * {@link CxfMessageDispatcher#doSendWithProxy(org.mule.api.MuleEvent)}.
 */
public enum CxfPayloadToArguments
{
    /**
     * In this strategy, if the payload is of type {@link NullPayload} it will be
     * send as a parameter just like any other object.
     */
    NULL_PAYLOAD_AS_PARAMETER(CxfConstants.PAYLOAD_TO_ARGUMENTS_NULL_PAYLOAD_AS_PARAMETER)
    {

    },
    /**
     * In this strategy, if the payload is of type {@link NullPayload} it will not be
     * send as a parameter. The array of arguments in this case will be empty. For
     * the rest of the objects it behaves just like
     * {@link #NULL_PAYLOAD_AS_PARAMETER} (it will delegate to
     * {@link CxfPayloadToArguments#payloadToArrayOfArguments(Object)}).
     */
    NULL_PAYLOAD_AS_VOID(CxfConstants.PAYLOAD_TO_ARGUMENTS_NULL_PAYLOAD_AS_VOID)
    {
        @Override
        public Object[] payloadToArrayOfArguments(Object payload)
        {
            if (payload instanceof NullPayload)
            {
                return new Object[]{};
            }
            else
            {
                return super.payloadToArrayOfArguments(payload);
            }
        }
    };

    /**
     * This is the value that is needed to be configured in the endpoint under
     * property {@link CxfConstants#PAYLOAD_TO_ARGUMENTS} so this
     * {@link CxfPayloadToArguments} is selected on method
     * {@link #getPayloadToArgumentsForEndpoint(OutboundEndpoint)}.
     */
    private final String payloadToArgumentsParameterValue;

    private CxfPayloadToArguments(String payloadToArgumentsParameterValue)
    {
        this.payloadToArgumentsParameterValue = payloadToArgumentsParameterValue;
    }

    /**
     * This method is the one that converts the payload in an array of arguments. In
     * this default implementation if the payload is already an array of
     * {@link Object objects} that array will be returned. Otherwise, an array with
     * one element, the payload, will be returned.
     * 
     * @param payload the payload to convert to array of arguments.
     * @return the array of arguments
     */
    public Object[] payloadToArrayOfArguments(Object payload)
    {
        Object[] args;
        if (payload instanceof Object[])
        {
            args = (Object[]) payload;
        }
        else
        {
            args = new Object[]{payload};
        }
        return args;
    }

    public String getPayloadToArgumentsParameterValue()
    {
        return payloadToArgumentsParameterValue;
    }

    /**
     * This returns the proper {@link CxfPayloadToArguments} for the provided
     * endpoint. The proper {@link CxfPayloadToArguments} is indicated by the
     * property {@link CxfConstants#PAYLOAD_TO_ARGUMENTS} in the endoint. The valid
     * values are <code>null</code> and any of the possible values returned by
     * {@link #getPayloadToArgumentsParameterValue()}.
     * 
     * @param endpoint
     * @return
     * @throws MalformedEndpointException
     */
    public static CxfPayloadToArguments getPayloadToArgumentsForEndpoint(OutboundEndpoint endpoint)
        throws MalformedEndpointException
    {
        String nullPayloadParameterValue = (String) endpoint.getProperty(CxfConstants.PAYLOAD_TO_ARGUMENTS);
        if (nullPayloadParameterValue == null)
        {
            nullPayloadParameterValue = CxfConstants.PAYLOAD_TO_ARGUMENTS_BY_DEFAULT;
        }
        for (CxfPayloadToArguments transformer : values())
        {
            if (transformer.getPayloadToArgumentsParameterValue().equals(nullPayloadParameterValue))
            {
                return transformer;
            }
        }
        throw new MalformedEndpointException(
            CxfMessages.invalidPayloadToArgumentsParameter(nullPayloadParameterValue), endpoint.getName());
    }
}
