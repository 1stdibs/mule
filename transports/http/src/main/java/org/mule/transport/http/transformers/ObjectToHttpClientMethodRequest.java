/*
 * $Id$
 * --------------------------------------------------------------------------------------
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 *
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */

package org.mule.transport.http.transformers;

import org.mule.RequestContext;
import org.mule.api.MuleContext;
import org.mule.api.MuleEvent;
import org.mule.api.MuleMessage;
import org.mule.api.config.MuleProperties;
import org.mule.api.transformer.TransformerException;
import org.mule.api.transport.OutputHandler;
import org.mule.api.transport.PropertyScope;
import org.mule.message.ds.StringDataSource;
import org.mule.transformer.AbstractMessageTransformer;
import org.mule.transformer.types.DataTypeFactory;
import org.mule.transport.NullPayload;
import org.mule.transport.http.HttpConnector;
import org.mule.transport.http.HttpConstants;
import org.mule.transport.http.StreamPayloadRequestEntity;
import org.mule.transport.http.i18n.HttpMessages;
import org.mule.util.IOUtils;
import org.mule.util.ObjectUtils;
import org.mule.util.StringUtils;

import java.io.InputStream;
import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.util.Iterator;
import java.util.Map;

import javax.activation.DataHandler;
import javax.activation.FileDataSource;
import javax.activation.URLDataSource;

import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.HttpVersion;
import org.apache.commons.httpclient.methods.ByteArrayRequestEntity;
import org.apache.commons.httpclient.methods.DeleteMethod;
import org.apache.commons.httpclient.methods.EntityEnclosingMethod;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.HeadMethod;
import org.apache.commons.httpclient.methods.InputStreamRequestEntity;
import org.apache.commons.httpclient.methods.OptionsMethod;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.PutMethod;
import org.apache.commons.httpclient.methods.StringRequestEntity;
import org.apache.commons.httpclient.methods.TraceMethod;
import org.apache.commons.httpclient.methods.multipart.ByteArrayPartSource;
import org.apache.commons.httpclient.methods.multipart.FilePart;
import org.apache.commons.httpclient.methods.multipart.MultipartRequestEntity;
import org.apache.commons.httpclient.methods.multipart.Part;
import org.apache.commons.httpclient.methods.multipart.StringPart;
import org.apache.commons.httpclient.params.HttpMethodParams;
import org.apache.commons.lang.SerializationUtils;

/**
 * <code>ObjectToHttpClientMethodRequest</code> transforms a MuleMessage into a
 * HttpClient HttpMethod that represents an HttpRequest.
 */

public class ObjectToHttpClientMethodRequest extends AbstractMessageTransformer
{
    public ObjectToHttpClientMethodRequest()
    {
        setReturnDataType(DataTypeFactory.create(HttpMethod.class));
        registerSourceType(DataTypeFactory.MULE_MESSAGE);
        registerSourceType(DataTypeFactory.BYTE_ARRAY);
        registerSourceType(DataTypeFactory.STRING);
        registerSourceType(DataTypeFactory.INPUT_STREAM);
        registerSourceType(DataTypeFactory.create(OutputHandler.class));
        registerSourceType(DataTypeFactory.create(NullPayload.class));
    }

    public void setMuleContext(MuleContext context)
    {
        this.muleContext = context;
    }



    @Override
    public Object transformMessage(MuleMessage msg, String outputEncoding) throws TransformerException
    {
        Object src = msg.getPayload();

        String method = (String) msg.getProperty(HttpConnector.HTTP_METHOD_PROPERTY, PropertyScope.OUTBOUND);
        if (method == null)
        {
            method = msg.getOutboundProperty(HttpConnector.HTTP_METHOD_PROPERTY, null);
            if (method == null)
            {
                method = msg.getInvocationProperty(HttpConnector.HTTP_METHOD_PROPERTY, "POST");
            }
        }

        try
        {
            //TODO It makes testing much harder if we use the endpoint on the transformer since we need to create correct message types and endpoints
            //URI uri = getEndpoint().getEndpointURI().getUri();
            URI uri = getURI(msg);
            HttpMethod httpMethod;

            if (HttpConstants.METHOD_GET.equals(method))
            {
                httpMethod = createGetMethod(msg, outputEncoding);
            }
            else if (HttpConstants.METHOD_POST.equalsIgnoreCase(method))
            {
                httpMethod = createPostMethod(msg, outputEncoding);
            }
            else if (HttpConstants.METHOD_PUT.equalsIgnoreCase(method))
            {
                PutMethod putMethod = new PutMethod(uri.toString());

                setupEntityMethod(src, outputEncoding, msg, putMethod);

                httpMethod = putMethod;
            }
            else if (HttpConstants.METHOD_DELETE.equalsIgnoreCase(method))
            {
                httpMethod = new DeleteMethod(uri.toString());
            }
            else if (HttpConstants.METHOD_HEAD.equalsIgnoreCase(method))
            {
                httpMethod = new HeadMethod(uri.toString());
            }
            else if (HttpConstants.METHOD_OPTIONS.equalsIgnoreCase(method))
            {
                httpMethod = new OptionsMethod(uri.toString());
            }
            else if (HttpConstants.METHOD_TRACE.equalsIgnoreCase(method))
            {
                httpMethod = new TraceMethod(uri.toString());
            }
            else
            {
                throw new TransformerException(HttpMessages.unsupportedMethod(method));
            }

            // Allow the user to set HttpMethodParams as an object on the message
            HttpMethodParams params = (HttpMethodParams) msg.removeProperty(HttpConnector.HTTP_PARAMS_PROPERTY, PropertyScope.OUTBOUND);
            if (params != null)
            {
                httpMethod.setParams(params);
            }
            else
            {
                // TODO we should probably set other properties here
                String httpVersion = msg.getOutboundProperty(HttpConnector.HTTP_VERSION_PROPERTY,
                        HttpConstants.HTTP11);
                if (HttpConstants.HTTP10.equals(httpVersion))
                {
                    httpMethod.getParams().setVersion(HttpVersion.HTTP_1_0);
                }
                else
                {
                    httpMethod.getParams().setVersion(HttpVersion.HTTP_1_1);
                }
            }

            setHeaders(httpMethod, msg);

            return httpMethod;
        }
        catch (Exception e)
        {
            throw new TransformerException(this, e);
        }
    }

    protected HttpMethod createPostMethod(MuleMessage msg, String outputEncoding) throws Exception
    {
        Object src = msg.getPayload();
        //TODO It makes testing much harder if we use the endpoint on the transformer since we need to create correct message types and endpoints
        //URI uri = getEndpoint().getEndpointURI().getUri();
        URI uri = getURI(msg);
        PostMethod postMethod = new PostMethod(uri.toString());
        String paramName = msg.getOutboundProperty(HttpConnector.HTTP_POST_BODY_PARAM_PROPERTY, null);
        if (paramName == null)
        {
            paramName = msg.getInvocationProperty(HttpConnector.HTTP_POST_BODY_PARAM_PROPERTY);
        }

        if (src instanceof Map)
        {
            for (Iterator iterator = ((Map) src).entrySet().iterator(); iterator.hasNext();)
            {
                Map.Entry entry = (Map.Entry) iterator.next();
                postMethod.addParameter(entry.getKey().toString(), entry.getValue().toString());
            }
        }
        else if (paramName != null)
        {
            postMethod.addParameter(paramName, src.toString());

        }
        else
        {
            setupEntityMethod(src, outputEncoding, msg, postMethod);
        }

        return postMethod;
    }

    protected HttpMethod createGetMethod(MuleMessage msg, String outputEncoding) throws Exception
    {
        Object src = msg.getPayload();
        //TODO It makes testing much harder if we use the endpoint on the transformer since we need to create correct message types and endpoints
        //URI uri = getEndpoint().getEndpointURI().getUri();
        URI uri = getURI(msg);
        HttpMethod httpMethod;
        String query = uri.getRawQuery();

        httpMethod = new GetMethod(uri.toString());
        String paramName = msg.getOutboundProperty(HttpConnector.HTTP_GET_BODY_PARAM_PROPERTY, null);
        if (paramName != null)
        {
            paramName = URLEncoder.encode(paramName, outputEncoding);

            String paramValue;
            Boolean encode = msg.getInvocationProperty(HttpConnector.HTTP_ENCODE_PARAMVALUE);
            if (encode == null)
            {
                encode = msg.getOutboundProperty(HttpConnector.HTTP_ENCODE_PARAMVALUE, true);
            }

            if (encode)
            {
                paramValue = URLEncoder.encode(src.toString(), outputEncoding);
            }
            else
            {
                paramValue = src.toString();
            }

            if (!(src instanceof NullPayload) && !StringUtils.EMPTY.equals(src))
            {
                if (query == null)
                {
                    query = paramName + "=" + paramValue;
                }
                else
                {
                    query += "&" + paramName + "=" + paramValue;
                }
            }
        }

        httpMethod.setQueryString(query);
        return httpMethod;
    }

    protected URI getURI(MuleMessage message) throws URISyntaxException, TransformerException
    {
        String endpoint = message.getOutboundProperty(MuleProperties.MULE_ENDPOINT_PROPERTY, null);
        if (endpoint == null)
        {
            throw new TransformerException(
                    HttpMessages.eventPropertyNotSetCannotProcessRequest(
                            MuleProperties.MULE_ENDPOINT_PROPERTY), this);
        }
        return new URI(endpoint);
    }

    protected void setupEntityMethod(Object src,
                                     String encoding,
                                     MuleMessage msg,
                                     EntityEnclosingMethod postMethod)
            throws UnsupportedEncodingException, TransformerException
    {
        // Dont set a POST payload if the body is a Null Payload.
        // This way client calls
        // can control if a POST body is posted explicitly
        if (!(msg.getPayload() instanceof NullPayload))
        {
            String mimeType = (String) msg.getProperty(HttpConstants.HEADER_CONTENT_TYPE, PropertyScope.OUTBOUND);
            if (mimeType == null)
            {
                mimeType = (getEndpoint()!=null ? getEndpoint().getMimeType() : null);
            }
            if (mimeType == null)
            {
                mimeType = HttpConstants.DEFAULT_CONTENT_TYPE;
                logger.info("Content-Type not set on outgoing request, defaulting to: " + mimeType);
            }

            if (encoding != null
                    && !"UTF-8".equals(encoding.toUpperCase())
                    && mimeType.indexOf("charset") == -1)
            {
                mimeType += "; charset=" + encoding;
            }

            // Ensure that we have a cached representation of the message if we're using HTTP 1.0
            String httpVersion = msg.getOutboundProperty(HttpConnector.HTTP_VERSION_PROPERTY, HttpConstants.HTTP11);
            if (HttpConstants.HTTP10.equals(httpVersion))
            {
                try
                {
                    src = msg.getPayloadAsBytes();
                }
                catch (Exception e)
                {
                    throw new TransformerException(this, e);
                }
            }

            if (msg.getOutboundAttachmentNames() != null && msg.getOutboundAttachmentNames().size() > 0)
            {
                try
                {
                    postMethod.setRequestEntity(createMultiPart(msg, postMethod));
                    return;
                }
                catch (Exception e)
                {
                    throw new TransformerException(this, e);
                }
            }
            if (src instanceof String)
            {

                postMethod.setRequestEntity(new StringRequestEntity(src.toString(), mimeType, encoding));
                return;
            }

            if (src instanceof InputStream)
            {
                postMethod.setRequestEntity(new InputStreamRequestEntity((InputStream) src, mimeType));
            }
            else if (src instanceof byte[])
            {
                postMethod.setRequestEntity(new ByteArrayRequestEntity((byte[]) src, mimeType));
            }
            else if (src instanceof OutputHandler)
            {
                MuleEvent event = RequestContext.getEvent();
                postMethod.setRequestEntity(new StreamPayloadRequestEntity((OutputHandler) src, event));
            }
            else
            {
                byte[] buffer = SerializationUtils.serialize((Serializable) src);
                postMethod.setRequestEntity(new ByteArrayRequestEntity(buffer, mimeType));
            }
        }
        else if (msg.getOutboundAttachmentNames() != null && msg.getOutboundAttachmentNames().size() > 0)
        {
            try
            {
                postMethod.setRequestEntity(createMultiPart(msg, postMethod));
            }
            catch (Exception e)
            {
                throw new TransformerException(this, e);
            }
        }
    }

    protected void setHeaders(HttpMethod httpMethod, MuleMessage msg) throws TransformerException
    {
        String headerValue;
        for (String headerName : msg.getOutboundPropertyNames())
        {
            headerValue = ObjectUtils.getString(msg.getOutboundProperty(headerName), null);

            if (headerName.startsWith(MuleProperties.PROPERTY_PREFIX))
            {
                //Define Mule headers a custom headers
                headerName = new StringBuffer(30).append("X-").append(headerName).toString();
                httpMethod.addRequestHeader(headerName, headerValue);

            }
            else if (!HttpConstants.RESPONSE_HEADER_NAMES.containsKey(headerName)
                    && !HttpConnector.HTTP_INBOUND_PROPERTIES.contains(headerName))
            {

                httpMethod.addRequestHeader(headerName, headerValue);
            }
        }
    }

    protected MultipartRequestEntity createMultiPart(MuleMessage msg, EntityEnclosingMethod method) throws Exception
    {
        Part[] parts;
        int i = 0;
        if (msg.getPayload() instanceof NullPayload)
        {
            parts = new Part[msg.getOutboundAttachmentNames().size()];
        }
        else
        {
            parts = new Part[msg.getOutboundAttachmentNames().size() + 1];
            parts[i++] = new FilePart("payload", new ByteArrayPartSource("payload", msg.getPayloadAsBytes()));
        }

        for (Iterator<String> iterator = msg.getOutboundAttachmentNames().iterator(); iterator.hasNext(); i++)
        {
            String name = iterator.next();
            String fileName = name;
            DataHandler dh = msg.getOutboundAttachment(name);
            if (dh.getDataSource() instanceof StringDataSource)
            {
                StringDataSource ds = (StringDataSource) dh.getDataSource();
                parts[i] = new StringPart(ds.getName(), IOUtils.toString(ds.getInputStream()));
            }
            else
            {
                if (dh.getDataSource() instanceof FileDataSource)
                {
                    fileName = ((FileDataSource) dh.getDataSource()).getFile().getName();
                }
                else if (dh.getDataSource() instanceof URLDataSource)
                {
                    fileName = ((URLDataSource) dh.getDataSource()).getURL().getFile();
                    //Don't use the whole file path, just the file name
                    int x = fileName.lastIndexOf("/");
                    if (x > -1)
                    {
                        fileName = fileName.substring(x + 1);
                    }
                }
                parts[i] = new FilePart(dh.getName(), new ByteArrayPartSource(fileName, IOUtils.toByteArray(dh.getInputStream())),
                        dh.getContentType(), null);
            }
        }

        return new MultipartRequestEntity(parts, method.getParams());
    }
}
