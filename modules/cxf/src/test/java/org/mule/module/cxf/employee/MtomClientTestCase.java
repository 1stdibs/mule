/*
 * $Id$
 * --------------------------------------------------------------------------------------
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 *
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */

package org.mule.module.cxf.employee;

import org.mule.tck.DynamicPortTestCase;

public class MtomClientTestCase extends DynamicPortTestCase
{

    public void testEchoService() throws Exception
    {
        EmployeeDirectoryImpl svc = (EmployeeDirectoryImpl) getComponent("employeeDirectoryService");
        
        int count = 0;
        while (svc.getInvocationCount() == 0 && count < 5000) {
            count += 500;
            Thread.sleep(500);
        }
        
        assertEquals(1, svc.getInvocationCount());
        
        // ensure that an attachment was actually sent.
        assertTrue(AttachmentVerifyInterceptor.HasAttachments);
    }

    protected String getConfigResources()
    {
        return "mtom-client-conf.xml";
    }

    @Override
    protected int getNumPortsToFind()
    {
        return 1;
    }

}

