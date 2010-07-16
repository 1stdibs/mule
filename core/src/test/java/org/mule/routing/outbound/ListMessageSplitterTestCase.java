/*
 * $Id$
 * --------------------------------------------------------------------------------------
 * Copyright (c) MuleSource, Inc.  All rights reserved.  http://www.mulesource.com
 *
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */

package org.mule.routing.outbound;

import org.mule.DefaultMuleMessage;
import org.mule.api.MuleEvent;
import org.mule.api.MuleMessage;
import org.mule.api.MuleMessageCollection;
import org.mule.api.MuleSession;
import org.mule.api.endpoint.OutboundEndpoint;
import org.mule.api.service.Service;
import org.mule.routing.filters.PayloadTypeFilter;
import org.mule.tck.AbstractMuleTestCase;
import org.mule.tck.MuleTestUtils;
import org.mule.tck.testmodels.fruit.Apple;
import org.mule.tck.testmodels.fruit.Orange;

import com.mockobjects.dynamic.Mock;

import java.util.ArrayList;
import java.util.List;

public class ListMessageSplitterTestCase extends AbstractMuleTestCase
{
    public ListMessageSplitterTestCase()
    {
        setStartContext(true);
    }

    public void testCorrelationGroupSizePropertySet() throws Exception
    {
        Service testService = getTestService("test", Apple.class);
        MuleSession session = getTestSession(testService, muleContext);

        OutboundEndpoint endpoint = getTestOutboundEndpoint("Test1Endpoint", "test://endpoint?exchange-pattern=request-response");
        ListMessageSplitter router = new ListMessageSplitter();
        router.setFilter(null);
        router.addRoute(endpoint);
        router.setMuleContext(muleContext);

        List<String> payload = new ArrayList<String>();
        payload.add("one");
        payload.add("two");
        payload.add("three");
        payload.add("four");

        MuleMessage message = new DefaultMuleMessage(payload, muleContext);

        MuleEvent result = router.route(new OutboundRoutingTestEvent(message, session));
        assertNotNull(result);
        MuleMessage resultMessage = result.getMessage();
        assertNotNull(resultMessage);
        assertTrue(resultMessage instanceof MuleMessageCollection);
        assertEquals("There should be 4 results for 4 split messages.", 4, ((MuleMessageCollection) resultMessage).size());
    }

    public void testMessageSplitterRouter() throws Exception
    {
        Mock session = MuleTestUtils.getMockSession();
        session.matchAndReturn("getFlowConstruct", null);
        session.matchAndReturn("setFlowConstruct", RouterTestUtils.getArgListCheckerFlowConstruct(), null);

        OutboundEndpoint endpoint1 = getTestOutboundEndpoint("Test1endpoint", "test://endpointUri.1", null, new PayloadTypeFilter(Apple.class), null);
        OutboundEndpoint endpoint2 = getTestOutboundEndpoint("Test2Endpoint", "test://endpointUri.2", null, new PayloadTypeFilter(Orange.class), null);
        OutboundEndpoint endpoint3 = getTestOutboundEndpoint("Test3Endpoint", "test://endpointUri.3");
        Mock mockendpoint1 = RouterTestUtils.getMockEndpoint(endpoint1);
        Mock mockendpoint2 = RouterTestUtils.getMockEndpoint(endpoint2);
        Mock mockendpoint3 = RouterTestUtils.getMockEndpoint(endpoint3);

        OutboundEndpoint endpoint4 = getTestOutboundEndpoint("Test4endpoint", "test://endpointUri.4?exchange-pattern=request-response", null, new PayloadTypeFilter(Apple.class), null);
        OutboundEndpoint endpoint5 = getTestOutboundEndpoint("Test5Endpoint", "test://endpointUri.5?exchange-pattern=request-response", null, new PayloadTypeFilter(Orange.class), null);
        OutboundEndpoint endpoint6 = getTestOutboundEndpoint("Test6Endpoint", "test://endpointUri.6?exchange-pattern=request-response");
        Mock mockendpoint4 = RouterTestUtils.getMockEndpoint(endpoint4);
        Mock mockendpoint5 = RouterTestUtils.getMockEndpoint(endpoint5);
        Mock mockendpoint6 = RouterTestUtils.getMockEndpoint(endpoint6);

        ListMessageSplitter asyncSplitter = new ListMessageSplitter();
        asyncSplitter.setMuleContext(muleContext);
        asyncSplitter.setDisableRoundRobin(true);
        asyncSplitter.setFilter(new PayloadTypeFilter(List.class));
        asyncSplitter.addRoute((OutboundEndpoint) mockendpoint1.proxy());
        asyncSplitter.addRoute((OutboundEndpoint) mockendpoint2.proxy());
        asyncSplitter.addRoute((OutboundEndpoint) mockendpoint3.proxy());

        ListMessageSplitter syncSplitter = new ListMessageSplitter();
        syncSplitter.setMuleContext(muleContext);
        syncSplitter.setDisableRoundRobin(true);
        syncSplitter.setFilter(new PayloadTypeFilter(List.class));
        syncSplitter.addRoute((OutboundEndpoint) mockendpoint4.proxy());
        syncSplitter.addRoute((OutboundEndpoint) mockendpoint5.proxy());
        syncSplitter.addRoute((OutboundEndpoint) mockendpoint6.proxy());
        List<Object> payload = new ArrayList<Object>();
        payload.add(new Apple());
        payload.add(new Apple());
        payload.add(new Orange());
        payload.add("");
        MuleMessage message = new DefaultMuleMessage(payload, muleContext);

        assertTrue(asyncSplitter.isMatch(message));
        mockendpoint1.expect("process", RouterTestUtils.getArgListCheckerMuleEvent());
        mockendpoint1.expect("process", RouterTestUtils.getArgListCheckerMuleEvent());
        mockendpoint2.expect("process", RouterTestUtils.getArgListCheckerMuleEvent());
        mockendpoint3.expect("process", RouterTestUtils.getArgListCheckerMuleEvent());
        asyncSplitter.route(new OutboundRoutingTestEvent(message, (MuleSession) session.proxy()));
        session.verify();

        message = new DefaultMuleMessage(payload, muleContext);
        MuleEvent event = new OutboundRoutingTestEvent(message, null);

        mockendpoint4.expectAndReturn("process", RouterTestUtils.getArgListCheckerMuleEvent(), event);
        mockendpoint4.expectAndReturn("process", RouterTestUtils.getArgListCheckerMuleEvent(), event);
        mockendpoint5.expectAndReturn("process", RouterTestUtils.getArgListCheckerMuleEvent(), event);
        mockendpoint6.expectAndReturn("process", RouterTestUtils.getArgListCheckerMuleEvent(), event);
        MuleEvent result = syncSplitter.route(new OutboundRoutingTestEvent(message, (MuleSession) session.proxy()));
        assertNotNull(result);
        MuleMessage resultMessage = result.getMessage();
        assertNotNull(resultMessage);
        assertTrue(resultMessage instanceof MuleMessageCollection);
        assertEquals(4, ((MuleMessageCollection) resultMessage).size());
        session.verify();
    }
}
