/*
 * $Id$
 * --------------------------------------------------------------------------------------
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 *
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */

package org.mule.util.store;

import org.mule.DefaultMuleMessage;
import org.mule.api.MuleContext;
import org.mule.api.MuleRuntimeException;
import org.mule.api.config.MuleConfiguration;
import org.mule.api.store.ObjectStoreException;
import org.mule.config.i18n.CoreMessages;
import org.mule.util.FileUtils;
import org.mule.util.SerializationUtils;
import org.mule.util.UUID;
import org.mule.util.queue.QueueKey;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.Serializable;
import java.util.List;

import org.junit.rules.TemporaryFolder;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class QueuePersistenceObjectStoreTestCase extends AbstractObjectStoreContractTestCase
{
    private static final String QUEUE_NAME = "the-queue";

    private TemporaryFolder tempFolder;
    private File persistenceFolder;
    private MuleContext mockMuleContext;

    @Override
    protected void doSetUp() throws Exception
    {
        super.doSetUp();
        initTemporaryFolder();
        initMockMuleContext();
    }

    private void initTemporaryFolder() throws IOException
    {
        tempFolder = new TemporaryFolder();
        tempFolder.create();
    }

    private void initMockMuleContext()
    {
        persistenceFolder = tempFolder.newFolder("persistence");

        MuleConfiguration mockConfig = mock(MuleConfiguration.class);
        when(mockConfig.getWorkingDirectory()).thenReturn(persistenceFolder.getAbsolutePath());

        mockMuleContext = mock(MuleContext.class);
        when(mockMuleContext.getConfiguration()).thenReturn(mockConfig);
        when(mockMuleContext.getExecutionClassLoader()).thenReturn(getClass().getClassLoader());
    }

    @Override
    protected void doTearDown() throws Exception
    {
        tempFolder.delete();
        super.doTearDown();
    }

    @Override
    public QueuePersistenceObjectStore<Serializable> getObjectStore() throws ObjectStoreException
    {
        QueuePersistenceObjectStore<Serializable> store =
            new QueuePersistenceObjectStore<Serializable>(mockMuleContext);
        store.open();
        return store;
    }

    @Override
    public Serializable getStorableValue()
    {
        return new DefaultMuleMessage(TEST_MESSAGE, muleContext);
    }

    @Override
    protected Serializable createKey()
    {
        return new QueueKey("theQueue", UUID.getUUID());
    }

    public void testCreatingTheObjectStoreThrowsMuleRuntimeException()
    {
        MuleRuntimeException muleRuntimeException = new MuleRuntimeException(CoreMessages.createStaticMessage("boom"));

        MuleContext mockContext = mock(MuleContext.class);
        when(mockContext.getConfiguration()).thenThrow(muleRuntimeException);

        QueuePersistenceObjectStore<Serializable> store =
            new QueuePersistenceObjectStore<Serializable>(mockContext);

        try
        {
            store.open();
            fail();
        }
        catch (ObjectStoreException ose)
        {
            // this one was expected
        }
    }

    public void testAllKeysOnNotYetOpenedStore() throws ObjectStoreException
    {
        QueuePersistenceObjectStore<Serializable> store =
            new QueuePersistenceObjectStore<Serializable>(mockMuleContext);

        List<Serializable> allKeys = store.allKeys();
        assertEquals(0, allKeys.size());
    }

    public void testListExistingFiles() throws Exception
    {
        String id = UUID.getUUID();
        File storeFile = createStoreFile(id);
        FileUtils.touch(storeFile);

        QueuePersistenceObjectStore<Serializable> store = getObjectStore();

        List<Serializable> allKeys = store.allKeys();
        assertEquals(1, allKeys.size());

        QueueKey key = (QueueKey)allKeys.get(0);
        assertEquals(id, key.id);
    }

    public void testRetrieveFileFromDisk() throws Exception
    {
        // create the store first so that the queuestore directory is created as a side effect
        QueuePersistenceObjectStore<Serializable> store = getObjectStore();

        String id = UUID.getUUID();
        createAndPopulateStoreFile(id, TEST_MESSAGE);

        QueueKey key = new QueueKey(QUEUE_NAME, id);
        Serializable value = store.retrieve(key);
        assertEquals(TEST_MESSAGE, value);
    }

    public void testRemove() throws Exception
    {
        // create the store first so that the queuestore directory is created as a side effect
        QueuePersistenceObjectStore<Serializable> store = getObjectStore();

        String id = UUID.getUUID();
        File storeFile = createAndPopulateStoreFile(id, TEST_MESSAGE);

        QueueKey key = new QueueKey(QUEUE_NAME, id);
        store.remove(key);

        assertFalse(storeFile.exists());
    }

    private File createAndPopulateStoreFile(String id, String payload) throws IOException
    {
        File storeFile = createStoreFile(id);

        // create the directory for the queue
        storeFile.getParentFile().mkdir();

        FileOutputStream fos = new FileOutputStream(storeFile);
        SerializationUtils.serialize(payload, fos);

        return storeFile;
    }

    private File createStoreFile(String id)
    {
        String path = String.format("%1s/%2s/%3s/%4s.msg", persistenceFolder.getAbsolutePath(),
            QueuePersistenceObjectStore.DEFAULT_QUEUE_STORE, QUEUE_NAME, id);
        return FileUtils.newFile(path);
    }
}
