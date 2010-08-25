/*
 * $Id$
 * --------------------------------------------------------------------------------------
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 *
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.expression;

import org.mule.DefaultMuleMessage;
import org.mule.api.MuleMessage;
import org.mule.api.config.MuleProperties;
import org.mule.api.expression.ExpressionRuntimeException;
import org.mule.api.expression.RequiredValueException;
import org.mule.api.transport.PropertyScope;
import org.mule.routing.correlation.CorrelationPropertiesExpressionEvaluator;
import org.mule.tck.AbstractMuleTestCase;
import org.mule.util.UUID;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HeadersExpressionEvaluatorTestCase extends AbstractMuleTestCase
{
    private Map<String, Object> props;

    @Override
    public void doSetUp()
    {
        props = new HashMap<String, Object>(3);
        props.put("foo", "foovalue");
        props.put("bar", "barvalue");
        props.put("baz", "bazvalue");
    }

    public void testSingleHeader() throws Exception
    {
        MessageHeaderExpressionEvaluator eval = new MessageHeaderExpressionEvaluator();
        MuleMessage message = new DefaultMuleMessage("test", props, muleContext);

        // Value required + found
        Object result = eval.evaluate("foo", message);
        assertNotNull(result);
        assertEquals("foovalue", result);

        // Value not required + found
        result = eval.evaluate("foo?", message);
        assertNotNull(result);
        assertEquals("foovalue", result);

        // Value not required + not found
        result = eval.evaluate("fool?", message);
        assertNull(result);

        // Value required + not found (throws exception)
        try
        {
            eval.evaluate("fool", message);
            fail("required value");
        }
        catch (Exception e)
        {
            //Expected
        }

        ((DefaultMuleMessage) message).addInboundProperties(Collections.singletonMap("testProp", (Object) "value"));
        result = eval.evaluate("testProp?", message);
        assertNull(result);

        result = eval.evaluate("INBOUND:testProp", message);
        assertEquals("value", result);
    }

    public void testMapHeaders() throws Exception
    {
        MessageHeadersExpressionEvaluator eval = new MessageHeadersExpressionEvaluator();

        MuleMessage message = new DefaultMuleMessage("test", props, muleContext);

        // Value required + found
        Object result = eval.evaluate("foo, baz", message);
        assertNotNull(result);
        assertTrue(result instanceof Map);
        assertEquals(2, ((Map)result).size());
        assertTrue(((Map)result).values().contains("foovalue"));
        assertTrue(((Map)result).values().contains("bazvalue"));
        assertFalse(((Map)result).values().contains("barvalue"));

        // Value not required + found
        result = eval.evaluate("foo?, baz", message);
        assertNotNull(result);        
        assertTrue(result instanceof Map);
        assertEquals(2, ((Map)result).size());
        assertTrue(((Map)result).values().contains("foovalue"));
        assertTrue(((Map)result).values().contains("bazvalue"));
        assertFalse(((Map)result).values().contains("barvalue"));
        
        // Value not required + not found
        result = eval.evaluate("fool?", message);
        assertNotNull(result);
        assertTrue(result instanceof Map);
        assertEquals(0, ((Map)result).size());

        // Value required + not found (throws exception)
        try
        {
            eval.evaluate("fool", message);
            fail("required value");
        }
        catch (Exception e)
        {
            //Expected
        }

        //Test all
        result = eval.evaluate("*", message);
        assertNotNull(result);
        assertTrue(result instanceof Map);
        assertEquals(3, ((Map)result).size());
        assertTrue(((Map)result).values().contains("foovalue"));
        assertTrue(((Map)result).values().contains("bazvalue"));
        assertTrue(((Map)result).values().contains("barvalue"));
    }

public void testHeadersWithScopes() throws Exception
    {
        MessageHeadersExpressionEvaluator eval = new MessageHeadersExpressionEvaluator();
        MuleMessage message = new DefaultMuleMessage("test", props, muleContext);
        message.setProperty("faz", "fazvalue", PropertyScope.INVOCATION);

        Object result = eval.evaluate("OUTBOUND:foo, OUTBOUND:baz", message);
        assertNotNull(result);
        assertTrue(result instanceof Map);
        assertEquals(2, ((Map)result).size());
        assertTrue(((Map)result).values().contains("foovalue"));
        assertTrue(((Map)result).values().contains("bazvalue"));

        //Setting the scope once will set the default for following names
        result = eval.evaluate("OUTBOUND:foo, baz", message);
        assertNotNull(result);
        assertTrue(result instanceof Map);
        assertEquals(2, ((Map)result).size());
        assertTrue(((Map)result).values().contains("foovalue"));
        assertTrue(((Map)result).values().contains("bazvalue"));

        result = eval.evaluate("OUTBOUND:foo, OUTBOUND:baz, INVOCATION:faz", message);
        assertNotNull(result);
        assertTrue(result instanceof Map);
        assertEquals(3, ((Map)result).size());
        assertTrue(((Map)result).values().contains("foovalue"));
        assertTrue(((Map)result).values().contains("bazvalue"));
        assertTrue(((Map)result).values().contains("fazvalue"));

        result = eval.evaluate("OUTBOUND:foo, baz, INVOCATION:faz", message);
        assertNotNull(result);
        assertTrue(result instanceof Map);
        assertEquals(3, ((Map)result).size());
        assertTrue(((Map)result).values().contains("foovalue"));
        assertTrue(((Map)result).values().contains("bazvalue"));
        assertTrue(((Map)result).values().contains("fazvalue"));


        try
        {
            eval.evaluate("OUTBOUND:foo, baz, faz", message);
            fail("faz is not in outbound scope and is not optional");
        }
        catch (RequiredValueException e)
        {
            //expected
        }

        result = eval.evaluate("OUTBOUND:foo, faz?, baz", message);
        assertNotNull(result);
        assertTrue(result instanceof Map);
        assertEquals(2, ((Map)result).size());
        assertTrue(((Map)result).values().contains("foovalue"));
        assertTrue(((Map)result).values().contains("bazvalue"));

        message.setInboundProperty("infoo", "infoovalue");
        result = eval.evaluate("infoo?", message);
        assertNotNull(result);
        assertTrue(result instanceof Map);
        assertEquals(0, ((Map)result).size());
        result = eval.evaluate("INBOUND:infoo?", message);
        assertNotNull(result);
        assertTrue(result instanceof Map);
        assertEquals(1, ((Map)result).size());
    }


    public void testListHeaders() throws Exception
    {
        MessageHeadersListExpressionEvaluator eval = new MessageHeadersListExpressionEvaluator();
        MuleMessage message = new DefaultMuleMessage("test", props, muleContext);

        // Value required + found
        Object result = eval.evaluate("foo, baz", message);
        assertNotNull(result);
        assertTrue(result instanceof List);
        assertEquals(2, ((List)result).size());
        assertTrue(((List)result).contains("foovalue"));
        assertTrue(((List)result).contains("bazvalue"));
        // redundant since we already know that the map is of size 2
        assertFalse(((List)result).contains("barvalue"));

        // Value not required + found
        result = eval.evaluate("foo?, baz", message);
        assertNotNull(result);
        assertTrue(result instanceof List);
        assertEquals(2, ((List)result).size());
        assertTrue(((List)result).contains("foovalue"));
        assertTrue(((List)result).contains("bazvalue"));
        // redundant since we already know that the map is of size 2
        assertFalse(((List)result).contains("barvalue"));        

        // Value not required + not found
        result = eval.evaluate("fool?", message);
        assertNotNull(result);
        assertTrue(result instanceof List);
        assertEquals(0, ((List)result).size());

        // Value required + not found (throws exception)
        try
        {
            eval.evaluate("fool", message);
            fail("required value");
        }
        catch (Exception e)
        {
            //Expected
        }
    }

    public void testListHeadersWithScopes() throws Exception
    {
        MessageHeadersListExpressionEvaluator eval = new MessageHeadersListExpressionEvaluator();
        MuleMessage message = new DefaultMuleMessage("test", props, muleContext);
        message.setProperty("faz", "fazvalue", PropertyScope.INVOCATION);

        Object result = eval.evaluate("OUTBOUND:foo, OUTBOUND:baz", message);
        assertNotNull(result);
        assertTrue(result instanceof List);
        assertEquals(2, ((List)result).size());
        assertTrue(((List)result).contains("foovalue"));
        assertTrue(((List)result).contains("bazvalue"));
        // redundant since we already know that the map is of size 2
        assertFalse(((List)result).contains("barvalue"));

        //Setting the scope once will set the default for following names
        result = eval.evaluate("OUTBOUND:foo, baz", message);
        assertNotNull(result);
        assertTrue(result instanceof List);
        assertEquals(2, ((List)result).size());
        assertTrue(((List)result).contains("foovalue"));
        assertTrue(((List)result).contains("bazvalue"));

        result = eval.evaluate("OUTBOUND:foo, OUTBOUND:baz, INVOCATION:faz", message);
        assertNotNull(result);
        assertTrue(result instanceof List);
        assertEquals(3, ((List)result).size());
        assertTrue(((List)result).contains("foovalue"));
        assertTrue(((List)result).contains("bazvalue"));
        // redundant since we already know that the map is of size 2
        assertTrue(((List)result).contains("fazvalue"));

        try
        {
            eval.evaluate("OUTBOUND:foo, baz, faz", message);
            fail("faz is not in outbound scope and is not optional");
        }
        catch (RequiredValueException e)
        {
            //expected
        }

        result = eval.evaluate("OUTBOUND:foo, faz?, baz", message);
        assertNotNull(result);
        assertTrue(result instanceof List);
        assertEquals(2, ((List)result).size());
        assertTrue(((List)result).contains("foovalue"));
        assertTrue(((List)result).contains("bazvalue"));

    }

    public void testListHeadersWithWildcard() throws Exception
    {
        MessageHeadersListExpressionEvaluator eval = new MessageHeadersListExpressionEvaluator();
        MuleMessage message = new DefaultMuleMessage("test", props, muleContext);

        // Wildcard match all
        Object result = eval.evaluate("*", message);
        assertNotNull(result);
        assertTrue(result instanceof List);
        assertEquals(3, ((List)result).size());
        assertTrue(((List)result).contains("foovalue"));
        assertTrue(((List)result).contains("bazvalue"));
        assertTrue(((List)result).contains("barvalue"));

        // Wildcard
        result = eval.evaluate("ba*", message);
        assertNotNull(result);
        assertTrue(result instanceof List);
        assertEquals(2, ((List)result).size());
        assertTrue(((List)result).contains("barvalue"));
        assertTrue(((List)result).contains("bazvalue"));

        // Wildcard no match
        result = eval.evaluate("x*", message);
        assertNotNull(result);
        assertTrue(result instanceof List);
        assertEquals(0, ((List)result).size());

        // Comma separated Wildcards
        result = eval.evaluate("ba*, f*", message);
        assertNotNull(result);
        assertTrue(result instanceof List);
        assertEquals(3, ((List)result).size());
        assertTrue(((List)result).contains("foovalue"));
        assertTrue(((List)result).contains("bazvalue"));
        assertTrue(((List)result).contains("barvalue"));
    }


    public void testMapHeadersWithWildcards() throws Exception
    {
        MessageHeadersExpressionEvaluator eval = new MessageHeadersExpressionEvaluator();

        MuleMessage message = new DefaultMuleMessage("test", props, muleContext);

        //Test all
        Object result = eval.evaluate("*", message);
        assertNotNull(result);
        assertTrue(result instanceof Map);
        assertEquals(3, ((Map)result).size());
        assertTrue(((Map)result).values().contains("foovalue"));
        assertTrue(((Map)result).values().contains("bazvalue"));
        assertTrue(((Map)result).values().contains("barvalue"));

        // Wildcard
        result = eval.evaluate("ba*", message);
        assertNotNull(result);
        assertTrue(result instanceof Map);
        assertEquals(2, ((Map)result).size());
        assertFalse(((Map)result).values().contains("foovalue"));
        assertTrue(((Map)result).values().contains("bazvalue"));
        assertTrue(((Map)result).values().contains("barvalue"));

        // Wildcard no match
        result = eval.evaluate("x*", message);
        assertNotNull(result);
        assertTrue(result instanceof Map);
        assertEquals(0, ((Map)result).size());

        //Test comma separated list of wildcards
        result = eval.evaluate("ba*, f*", message);
        assertNotNull(result);
        assertTrue(result instanceof Map);
        assertEquals(3, ((Map)result).size());
        assertTrue(((Map)result).values().contains("foovalue"));
        assertTrue(((Map)result).values().contains("bazvalue"));
        assertTrue(((Map)result).values().contains("barvalue"));
    }
    
    public void testSingleHeaderUsingManager() throws Exception
    {
        MuleMessage message = new DefaultMuleMessage("test", props, muleContext);

        // Value required + found
        Object result = muleContext.getExpressionManager().evaluate("#[header:foo]", message);
        assertNotNull(result);
        assertEquals("foovalue", result);

        // Value not required + found
        result = muleContext.getExpressionManager().evaluate("#[header:foo?]", message);
        assertNotNull(result);
        assertEquals("foovalue", result);

        // Value not required + not found
        result = muleContext.getExpressionManager().evaluate("#[header:fool?]", message);
        assertNull(result);

        // Value required + not found (throws exception)
        try
        {
            muleContext.getExpressionManager().evaluate("#[header:fool]", message);
            fail("Required value");
        }
        catch (ExpressionRuntimeException e)
        {
            //expected
        }

    }

    public void testMapHeadersUsingManager() throws Exception
    {

        MuleMessage message = new DefaultMuleMessage("test", props, muleContext);

        // Value required + found
        Object result = muleContext.getExpressionManager().evaluate("#[headers:foo, baz]", message);
        assertNotNull(result);
        assertTrue(result instanceof Map);
        assertEquals(2, ((Map)result).size());
        assertTrue(((Map)result).values().contains("foovalue"));
        assertTrue(((Map)result).values().contains("bazvalue"));
        assertFalse(((Map)result).values().contains("barvalue"));

        // Value not required + found
        result = muleContext.getExpressionManager().evaluate("#[headers:foo?, baz]", message);
        assertNotNull(result);
        assertTrue(result instanceof Map);
        assertEquals(2, ((Map)result).size());
        assertTrue(((Map)result).values().contains("foovalue"));
        assertTrue(((Map)result).values().contains("bazvalue"));
        assertFalse(((Map)result).values().contains("barvalue"));
        
        // Value not required + not found
        result = muleContext.getExpressionManager().evaluate("#[headers:fool?]", message);
        assertNotNull(result);
        assertTrue(result instanceof Map);
        assertEquals(0, ((Map)result).size());

        // Value required + not found (throws exception)
        try
        {
            muleContext.getExpressionManager().evaluate("#[headers:fool]", message);
            fail("Required value");
        }
        catch (ExpressionRuntimeException e)
        {
            //expected
        }

    }

    public void testMapHeadersWithWildcardsUsingManager() throws Exception
    {

        MuleMessage message = new DefaultMuleMessage("test", props, muleContext);

        // All headers
        Object result = muleContext.getExpressionManager().evaluate("#[headers:*]", message);
        assertNotNull(result);
        assertTrue(result instanceof Map);
        assertEquals(3, ((Map)result).size());
        assertTrue(((Map)result).values().contains("foovalue"));
        assertTrue(((Map)result).values().contains("bazvalue"));
        assertTrue(((Map)result).values().contains("barvalue"));

        // Wildcard headers
        result = muleContext.getExpressionManager().evaluate("#[headers:ba*]", message);
        assertNotNull(result);
        assertTrue(result instanceof Map);
        assertEquals(2, ((Map)result).size());
        assertFalse(((Map)result).values().contains("foovalue"));
        assertTrue(((Map)result).values().contains("bazvalue"));
        assertTrue(((Map)result).values().contains("barvalue"));

        //Wildcard no match
        result = muleContext.getExpressionManager().evaluate("#[headers:x*]", message);
        assertNotNull(result);
        assertTrue(result instanceof Map);
        assertEquals(0, ((Map)result).size());

        // comma-separated list of wildcards
        result = muleContext.getExpressionManager().evaluate("#[headers:ba*, f*]", message);
        assertNotNull(result);
        assertTrue(result instanceof Map);
        assertEquals(3, ((Map)result).size());
        assertTrue(((Map)result).values().contains("foovalue"));
        assertTrue(((Map)result).values().contains("bazvalue"));
        assertTrue(((Map)result).values().contains("barvalue"));

    }

    public void testListHeadersUsingManager() throws Exception
    {
        MuleMessage message = new DefaultMuleMessage("test", props, muleContext);

        // Value required + found
        Object result = muleContext.getExpressionManager().evaluate("#[headers-list:foo, baz]", message);
        assertNotNull(result);
        assertTrue(result instanceof List);
        assertEquals(2, ((List)result).size());
        assertTrue(((List)result).contains("foovalue"));
        assertTrue(((List)result).contains("bazvalue"));
        assertFalse(((List)result).contains("barvalue"));

        // Value not required + found
        result = muleContext.getExpressionManager().evaluate("#[headers-list:foo?, baz]", message);
        assertNotNull(result);
        assertTrue(result instanceof List);
        assertEquals(2, ((List)result).size());
        assertTrue(((List)result).contains("foovalue"));
        assertTrue(((List)result).contains("bazvalue"));
        assertFalse(((List)result).contains("barvalue"));        

        // Value not required + not found
        result = muleContext.getExpressionManager().evaluate("#[headers-list:fool?]", message);
        assertNotNull(result);
        assertTrue(result instanceof List);
        assertEquals(0, ((List)result).size());

        // Value required + not found (throws exception)
        try
        {
            muleContext.getExpressionManager().evaluate("#[headers-list:fool]", message);
            fail("Required value");
        }
        catch (ExpressionRuntimeException e)
        {
            //expected
        }
    }

    public void testListHeadersWithWildCardsUsingManager() throws Exception
    {
        MuleMessage message = new DefaultMuleMessage("test", props, muleContext);

        // All
        Object result = muleContext.getExpressionManager().evaluate("#[headers-list:*]", message);
        assertNotNull(result);
        assertTrue(result instanceof List);
        assertEquals(3, ((List)result).size());
        assertTrue(((List)result).contains("foovalue"));
        assertTrue(((List)result).contains("bazvalue"));
        assertTrue(((List)result).contains("barvalue"));

        // wildcard
        result = muleContext.getExpressionManager().evaluate("#[headers-list:ba*]", message);
        assertNotNull(result);
        assertTrue(result instanceof List);
        assertEquals(2, ((List)result).size());
        assertFalse(((List)result).contains("foovalue"));
        assertTrue(((List)result).contains("bazvalue"));
        assertTrue(((List)result).contains("barvalue"));

        // wildcard no match
        result = muleContext.getExpressionManager().evaluate("#[headers-list:x*]", message);
        assertNotNull(result);
        assertTrue(result instanceof List);
        assertEquals(0, ((List)result).size());

        // Comma list of wildcards
        result = muleContext.getExpressionManager().evaluate("#[headers-list:ba*, f*]", message);
        assertNotNull(result);
        assertTrue(result instanceof List);
        assertEquals(3, ((List)result).size());
        assertTrue(((List)result).contains("foovalue"));
        assertTrue(((List)result).contains("bazvalue"));
        assertTrue(((List)result).contains("barvalue"));
    }
    
    public void testCorrelationManagerCorrelationId()
    {
        CorrelationPropertiesExpressionEvaluator evaluator = new CorrelationPropertiesExpressionEvaluator();
        String correlationId = UUID.getUUID();
        
        MuleMessage message = new DefaultMuleMessage("test", props, muleContext);
        message.setCorrelationId(correlationId);
        
        Object result = evaluator.evaluate(MuleProperties.MULE_CORRELATION_ID_PROPERTY, message);
        assertNotNull(result);
        assertEquals(correlationId, result);
    }
    
    public void testCorrelationManagerNullResult()
    {
        CorrelationPropertiesExpressionEvaluator evaluator = new CorrelationPropertiesExpressionEvaluator();
        
        DefaultMuleMessage message = new DefaultMuleMessage("test", props, muleContext);
        message.setUniqueId(null);
        
        try
        {
            evaluator.evaluate(MuleProperties.MULE_CORRELATION_ID_PROPERTY, message);
            fail("Null result on CorrelationPropertiesExpressionEvaluator must throw");
        }
        catch (IllegalArgumentException iae)
        {
            // this one was expected
        }
    }    
    
    public void testCorrelationManagerUniqueId()
    {
        CorrelationPropertiesExpressionEvaluator evaluator = new CorrelationPropertiesExpressionEvaluator();
        
        MuleMessage message = new DefaultMuleMessage("test", props, muleContext);        
        Object result = evaluator.evaluate(MuleProperties.MULE_MESSAGE_ID_PROPERTY, message);
        assertNotNull(result);
        assertEquals(message.getUniqueId(), result);
    }
    
//    public void testCorrelationManagerNullInput()
//    {
//        CorrelationPropertiesExpressionEvaluator evaluator = new CorrelationPropertiesExpressionEvaluator();
//        evaluator.evaluate(MuleProperties.MULE_CORRELATION_ID_PROPERTY, null);
//    }
    
    public void testCorrelationManagerInvalidKey()
    {
        CorrelationPropertiesExpressionEvaluator evaluator = new CorrelationPropertiesExpressionEvaluator();

        MuleMessage message = new DefaultMuleMessage("test", props, muleContext);
        try
        {
            evaluator.evaluate("invalid-key", message);
            fail("invalid key on CorrelationPropertiesExpressionEvaluator must fail");
        }
        catch (IllegalArgumentException iax)
        {
            // this one was expected
        }
    }
}
