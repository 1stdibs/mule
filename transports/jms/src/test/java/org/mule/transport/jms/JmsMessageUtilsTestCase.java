/*
 * $Id$
 * --------------------------------------------------------------------------------------
 * Copyright (c) MuleSource, Inc.  All rights reserved.  http://www.mulesource.com
 *
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */

package org.mule.transport.jms;

import static org.mockito.Matchers.anyObject;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.mule.tck.AbstractMuleTestCase;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.jms.BytesMessage;
import javax.jms.JMSException;
import javax.jms.MapMessage;
import javax.jms.Message;
import javax.jms.MessageFormatException;
import javax.jms.ObjectMessage;
import javax.jms.Session;
import javax.jms.StreamMessage;
import javax.jms.TextMessage;

import org.apache.activemq.command.ActiveMQBytesMessage;
import org.apache.activemq.command.ActiveMQMapMessage;
import org.apache.activemq.command.ActiveMQObjectMessage;
import org.apache.activemq.command.ActiveMQStreamMessage;
import org.apache.activemq.command.ActiveMQTextMessage;
import org.apache.commons.collections.IteratorUtils;
import org.apache.commons.collections.iterators.IteratorEnumeration;

public class JmsMessageUtilsTestCase extends AbstractMuleTestCase
{
    public static final String ENCODING = "UTF-8";
    private static final String OBJECT_ID = "id1234";
    private static final Integer NUMBER_VALUE = Integer.valueOf(7);
    private static final String STRING_VALUE = "Hello";
    private static final String OBJECT_KEY = "object";
    private static final String NUMBER_KEY = "number";
    private static final String STRING_KEY = "string";

    public void testHeaders()
    {
        // already valid headers are returned as-is, so we can assertSame
        assertSame("identifier", JmsMessageUtils.encodeHeader("identifier"));
        assertSame("_identifier", JmsMessageUtils.encodeHeader("_identifier"));
        assertSame("identifier_", JmsMessageUtils.encodeHeader("identifier_"));
        assertSame("ident_ifier", JmsMessageUtils.encodeHeader("ident_ifier"));

        assertEquals("_identifier", JmsMessageUtils.encodeHeader("-identifier"));
        assertEquals("identifier_", JmsMessageUtils.encodeHeader("identifier-"));
        assertEquals("ident_ifier", JmsMessageUtils.encodeHeader("ident-ifier"));
        assertEquals("_ident_ifier_", JmsMessageUtils.encodeHeader("-ident_ifier-"));
        assertEquals("_ident_ifier_", JmsMessageUtils.encodeHeader("-ident-ifier-"));
    }

    public void testTextMessageNullContent() throws Exception
    {
        TextMessage mockMessage = mock(TextMessage.class);
        when(mockMessage.getText()).thenReturn(null);
        
        byte[] result = JmsMessageUtils.toByteArray(mockMessage, JmsConstants.JMS_SPECIFICATION_102B, ENCODING);
        assertNotNull(result);
        assertEquals("Should return an empty byte array.", 0, result.length);

        verify(mockMessage).getText();
    }

    public void testByteMessageNullContentInJmsVersion_1_0_1() throws Exception
    {
        BytesMessage mockMessage1 = mock(BytesMessage.class);
        when(mockMessage1.readBytes((byte[]) anyObject())).thenReturn(-1);

        byte[] result1 = JmsMessageUtils.toByteArray(mockMessage1, JmsConstants.JMS_SPECIFICATION_102B,
            ENCODING);
        assertNotNull(result1);
        assertEquals("Should return an empty byte array.", 0, result1.length);
        verify(mockMessage1).reset();        
    }
    
    public void testByteMessageNullContentInJmsVersion_1_1() throws Exception
    {
        BytesMessage mockMessage2 = mock(BytesMessage.class);
        when(mockMessage2.getBodyLength()).thenReturn(Long.valueOf(0));

        byte[] result2 = JmsMessageUtils.toByteArray(mockMessage2, JmsConstants.JMS_SPECIFICATION_11,
            ENCODING);
        assertNotNull(result2);
        assertEquals("Should return an empty byte array.", 0, result2.length);
        verify(mockMessage2).reset();  
    }

    public void testStreamMessageSerialization() throws Exception
    {
        Session session = mock(Session.class);
        when(session.createStreamMessage()).thenReturn(new ActiveMQStreamMessage());

        // Creates a test list with data
        List data = new ArrayList();
        data.add(Boolean.TRUE);
        data.add(new Byte("1"));
        data.add(new Short("2"));
        data.add(new Character('3'));
        data.add(new Integer("4"));
        // can't write Longs: https://issues.apache.org/activemq/browse/AMQ-1965
        // data.add(new Long("5"));
        data.add(new Float("6"));
        data.add(new Double("7"));
        data.add(new String("8"));
        data.add(new byte[]{9, 10});

        StreamMessage result = (StreamMessage) JmsMessageUtils.toMessage(data, session);

        // Resets so it's readable
        result.reset();
        assertEquals(Boolean.TRUE, result.readObject());
        assertEquals(new Byte("1"), result.readObject());
        assertEquals(new Short("2"), result.readObject());
        assertEquals(new Character('3'), result.readObject());
        assertEquals(new Integer("4"), result.readObject());
        // can't write Longs: https://issues.apache.org/activemq/browse/AMQ-1965
        // assertEquals(new Long("5"), result.readObject());
        assertEquals(new Float("6"), result.readObject());
        assertEquals(new Double("7"), result.readObject());
        assertEquals(new String("8"), result.readObject());
        assertTrue(Arrays.equals(new byte[]{9, 10}, (byte[]) result.readObject()));
    }

    public void testStreamMessageSerializationWithInvalidType() throws Exception
    {
        Session session = null;
        session = mock(Session.class);
        when(session.createStreamMessage()).thenReturn(new ActiveMQStreamMessage());

        // Creates a test list with data
        List data = new ArrayList();
        data.add(new Object());

        try
        {
            JmsMessageUtils.toMessage(data, session);
            fail("Should've failed with MessageFormatException");
        }
        catch (MessageFormatException e)
        {
            // expected
        }
    }
    
    public void testMapMessageWithNullValue() throws Exception
    {
        String[] keys = new String[]{"key", "null"};
        Iterator<String> keyIterator = IteratorUtils.arrayIterator(keys);
        Enumeration<String> keyEnumeration = new IteratorEnumeration(keyIterator);

        MapMessage mockMessage1 = mock(MapMessage.class);
        when(mockMessage1.getMapNames()).thenReturn(keyEnumeration);
        when(mockMessage1.getObject("key")).thenReturn("value");
        when(mockMessage1.getObject("null")).thenReturn(null);

        Object result = JmsMessageUtils.toObject(mockMessage1, JmsConstants.JMS_SPECIFICATION_11, ENCODING);
        assertNotNull(result);
        assertTrue(result instanceof Map);
        Map map = (Map) result;
        assertEquals("value", map.get("key"));
        assertNull(map.get("null"));
    }

    /**
     * Tests that is able to convert a Map which only contains simple values into a
     * MapMessage.
     */
    public void testConvertsValidMapToMapMessage() throws JMSException
    {
        Map<String, Object> message = new HashMap<String, Object>();
        message.put(STRING_KEY, STRING_VALUE);
        message.put(NUMBER_KEY, NUMBER_VALUE);

        Session session = mock(Session.class);
        when(session.createMapMessage()).thenReturn(new ActiveMQMapMessage());

        MapMessage result = (MapMessage) JmsMessageUtils.toMessage(message, session);

        assertEquals(STRING_VALUE, result.getString(STRING_KEY));
        assertEquals(7, result.getInt(NUMBER_KEY));
    }

    /**
     * Tests that trying to convert a Map which contains a non valid value throws an
     * exception.
     */
    public void testConvertingMapIncludingNotValidValueThrowsException() throws JMSException
    {
        Map<String, Object> message = new HashMap<String, Object>();
        message.put(STRING_KEY, STRING_VALUE);
        message.put(OBJECT_KEY, new Object());

        Session session = mock(Session.class);
        when(session.createMapMessage()).thenReturn(new ActiveMQMapMessage());

        try
        {
            JmsMessageUtils.toMessage(message, session);
            fail("Able to convert an invalid Map instance: contains a non valid value");
        }
        catch (MessageFormatException expected)
        {
        }
    }

    public void testConvertingStringToTextMessage() throws JMSException
    {
        String text = "Hello world";

        Session session = mock(Session.class);
        TextMessage textMessage = new ActiveMQTextMessage();
        textMessage.setText(text);
        when(session.createTextMessage(text)).thenReturn(textMessage);

        TextMessage message = (TextMessage) JmsMessageUtils.toMessage(text, session);
        assertEquals(textMessage, message);
        verify(session, times(1)).createTextMessage(text);
    }
    
    public void testConvertingByteArrayToBytesMessage() throws JMSException {
        Session session = mock(Session.class);
        when(session.createBytesMessage()).thenReturn(new ActiveMQBytesMessage());
        
        byte[] bytesArray = new byte[] {1, 2};
        BytesMessage message = (BytesMessage) JmsMessageUtils.toMessage(bytesArray, session);
        
        // Makes the message readable
        message.reset();
        byte[] bytesArrayResult = new byte[(int) message.getBodyLength()];
        int length = message.readBytes(bytesArrayResult);        
        assertEquals(2, length);
        assertEquals(bytesArray[0],bytesArrayResult[0]);
        assertEquals(bytesArray[1],bytesArrayResult[1]);
    }

    public void testConvertingSerializableToObjectMessage() throws JMSException
    {
        Session session = mock(Session.class);
        when(session.createObjectMessage()).thenReturn(new ActiveMQObjectMessage());

        ObjectMessage message = (ObjectMessage) JmsMessageUtils.toMessage(new SerializableObject(OBJECT_ID),
            session);

        Serializable serializable = message.getObject();
        assertTrue(serializable instanceof SerializableObject);
        assertEquals(OBJECT_ID, ((SerializableObject) serializable).id);
    }

    public void testConvertingMessageToMessageReturnsSameObject() throws JMSException
    {
        Message original = mock(Message.class);
        Message result = JmsMessageUtils.toMessage(original, null);
        assertSame(original, result);
    }

    public void testConvertingInvalidTypeThrowsException() throws JMSException
    {
        try
        {
            JmsMessageUtils.toMessage(new Object(), null);
            fail("Able to convert to message an invalid type");
        }
        catch (JMSException e)
        {
            // Expected
        }
    }

    /**
     * Dummy serializable class used to test that conversion from Map to JmsMessage
     * is OK when it includes non primitive serializable values.
     */
    private static class SerializableObject implements Serializable
    {
        private static final long serialVersionUID = -4865136673252075014L;
        private String id;

        public SerializableObject(String id)
        {
            this.id = id;
        }

        private void readObject(ObjectInputStream aInputStream) throws ClassNotFoundException, IOException
        {
            aInputStream.defaultReadObject();
        }

        private void writeObject(ObjectOutputStream aOutputStream) throws IOException
        {
            aOutputStream.defaultWriteObject();
        }
    }
}
