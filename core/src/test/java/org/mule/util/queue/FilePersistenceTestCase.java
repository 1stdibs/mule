/*
 * $Id$
 * --------------------------------------------------------------------------------------
 * Copyright (c) MuleSource, Inc.  All rights reserved.  http://www.mulesource.com
 *
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */

package org.mule.util.queue;

import org.mule.util.FileUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.mule.util.queue.FilePersistenceStrategy.DEFAULT_QUEUE_STORE;

public class FilePersistenceTestCase extends AbstractTransactionQueueManagerTestCase
{

    @Override
    protected TransactionalQueueManager createQueueManager() throws Exception
    {
        TransactionalQueueManager mgr = new TransactionalQueueManager();
        FilePersistenceStrategy ps = new FilePersistenceStrategy();
        ps.setMuleContext(muleContext);
        mgr.setPersistenceStrategy(ps);
        mgr.setDefaultQueueConfiguration(new QueueConfiguration(true));
        return mgr;
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.mule.transaction.xa.queue.AbstractTransactionQueueManagerTestCase#isPersistent()
     */
    @Override
    protected boolean isPersistent()
    {
        return true;
    }

    public void testGenerateUniqueAndIncrementalIds() throws Exception
    {
        FilePersistenceStrategy ps = new FilePersistenceStrategy();

        final Set<String> ids = new HashSet<String>();
        final List<Object[]> idsWithIndexes = new ArrayList<Object[]>(1000);
        final int numberOfIdsToGenerate = 10000;
        for (int index = 0; index < numberOfIdsToGenerate; index++)
        {
            String generatedId = ps.generateId();
            idsWithIndexes.add(new Object[]{generatedId, Integer.valueOf(index)});
            if (ids.contains(generatedId))
            {
                fail("REPEATED ID :" + index + ": " + generatedId);
            }
            else
            {
                ids.add(generatedId);
            }
        }
        final Comparator<Object[]> comparatorById = new Comparator<Object[]>()
        {
            public int compare(Object[] o1, Object[] o2)
            {
                return ((String) o1[0]).compareTo((String) o2[0]);
            }
        };
        Collections.sort(idsWithIndexes, comparatorById);
        for (int index = 0; index < numberOfIdsToGenerate; index++)
        {
            assertEquals(Integer.valueOf(index), idsWithIndexes.get(index)[1]);
        }
    }

    public void testUnhealthyEmptyMessage() throws Exception
    {
        FilePersistenceStrategy ps = new FilePersistenceStrategy();
        ps.setMuleContext(muleContext);
        ps.open();
        String path = muleContext.getConfiguration().getWorkingDirectory() + File.separator + DEFAULT_QUEUE_STORE;
        String queue = "queue";
        String id = "unhealthy";
        FileUtils.createFile(path + File.separator + queue + File.separator + id + ".msg");
        Object result = ps.load(queue, id);
        assertNull(result);
    }

    public void testUnhealthyMessage() throws Exception
    {
        FilePersistenceStrategy ps = new FilePersistenceStrategy();
        ps.setMuleContext(muleContext);
        ps.open();
        String path = muleContext.getConfiguration().getWorkingDirectory() + File.separator + DEFAULT_QUEUE_STORE;
        String queue = "queue";
        String id = "unhealthy";
        FileUtils.stringToFile(path + File.separator + queue + File.separator + id + ".msg", "\u00AC\u00ED");
        Object result = ps.load(queue, id);
        assertNull(result);
    }
}
