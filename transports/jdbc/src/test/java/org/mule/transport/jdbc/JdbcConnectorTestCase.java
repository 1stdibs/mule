/*
* Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
* The software in this package is published under the terms of the CPAL v1.0
* license, a copy of which has been included with this distribution in the
* LICENSE.txt file.
*/
package org.mule.transport.jdbc;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import org.mule.api.MuleException;
import org.mule.api.lifecycle.InitialisationException;
import org.mule.api.transport.Connector;
import org.mule.common.TestResult;
import org.mule.common.Testable;
import org.mule.module.bti.transaction.TransactionManagerWrapper;
import org.mule.tck.util.MuleDerbyTestUtils;
import org.mule.transport.AbstractConnectorTestCase;
import org.mule.transport.jdbc.xa.BitronixXaDataSourceWrapper;
import org.mule.transport.jdbc.xa.DataSourceWrapper;

import java.util.HashMap;
import java.util.Map;

import javax.sql.DataSource;
import javax.sql.XADataSource;

import org.apache.derby.jdbc.EmbeddedDataSource;
import org.enhydra.jdbc.standard.StandardDataSource;
import org.hamcrest.core.IsInstanceOf;
import org.junit.Test;
import org.mockito.Mockito;

public class JdbcConnectorTestCase extends AbstractConnectorTestCase
{

    private static final String DATABASE_NAME = "embeddedDb";

    @Override
    protected void doSetUp() throws Exception
    {
        MuleDerbyTestUtils.createDataBase(DATABASE_NAME);
        super.doSetUp();
    }

    @Override
    protected void doTearDown() throws Exception
    {
        MuleDerbyTestUtils.cleanupDerbyDb(DATABASE_NAME);
        super.doTearDown();
    }

    @Override
    public Connector createConnector() throws Exception
    {
        JdbcConnector c = new JdbcConnector(muleContext);
        EmbeddedDataSource embeddedDS = new EmbeddedDataSource();
        embeddedDS.setDatabaseName(DATABASE_NAME);
        c.setName("JdbcConnector");
        c.setDataSource(embeddedDS);
        c.setPollingFrequency(1000);
        return c;
    }

    public Object getValidMessage() throws Exception
    {
        Map map = new HashMap();
        return map;
    }

    public String getTestEndpointURI()
    {
        return "jdbc://test?sql=SELECT * FROM TABLE";
    }

    @Test
    public void testConnectionConnectorStartedSucess() throws MuleException
    {
        getConnector().start();
        assertTrue(getConnector().isStarted());
        assertTrue(getConnector().isConnected());
        assertTrue(getConnector() instanceof Testable);
        assertTrue(((Testable) getConnector()).test().getStatus() == TestResult.Status.SUCCESS);
        assertTrue(getConnector().isStarted());
        assertTrue(getConnector().isConnected());
    }

    @Test
    public void testConnectionConnectorStoppedSucess()
    {
        assertFalse(getConnector().isStarted());
        assertFalse(getConnector().isConnected());
        assertTrue(getConnector() instanceof Testable);
        assertTrue(((Testable) getConnector()).test().getStatus() == TestResult.Status.SUCCESS);
        assertFalse(getConnector().isStarted());
        assertFalse(getConnector().isConnected());
    }

    @Test
    public void testConnectionInvalidConfigParamFailure() throws MuleException
    {
        ((JdbcConnector) getConnector()).setDataSource(null);
        assertFalse(getConnector().isStarted());
        assertFalse(getConnector().isConnected());
        assertTrue(getConnector() instanceof Testable);
        assertTrue(((Testable) getConnector()).test().getStatus() == TestResult.Status.FAILURE);
        System.out.println(((Testable) getConnector()).test().getMessage());
        assertFalse(getConnector().isStarted());
        assertFalse(getConnector().isConnected());
    }

    @Test
    public void testConnectionUnreachableFailure() throws MuleException
    {
        ((JdbcConnector) getConnector()).setDataSource(new StandardDataSource());
        assertFalse(getConnector().isStarted());
        assertFalse(getConnector().isConnected());
        assertTrue(getConnector() instanceof Testable);
        assertTrue(((Testable) getConnector()).test().getStatus() == TestResult.Status.FAILURE);
        System.out.println(((Testable) getConnector()).test().getMessage());
        assertFalse(getConnector().isStarted());
        assertFalse(getConnector().isConnected());
    }

    @Test
    public void dataSourceIsNotWrappedWhenNotUsingXA() throws InitialisationException
    {
        DataSource mockDataSource = Mockito.mock(DataSource.class);
        DataSource dataSource = getDataSourceAfterInitialization(mockDataSource);
        assertThat(dataSource, is(mockDataSource));
    }

    @Test
    public void dataSourceIsWrappedWhenUsingXA() throws InitialisationException
    {
        DataSource mockDataSource = Mockito.mock(TestXADataSource.class);
        DataSource dataSource = getDataSourceAfterInitialization(mockDataSource);
        assertThat(dataSource, IsInstanceOf.instanceOf(DataSourceWrapper.class));
    }

    @Test
    public void dataSourceIsWrappedWithBtmWrapperWhenUsingXA() throws Exception
    {
        muleContext.setTransactionManager(Mockito.mock(TransactionManagerWrapper.class));
        DataSource mockDataSource = Mockito.mock(TestXADataSource.class);
        DataSource dataSource = getDataSourceAfterInitialization(mockDataSource);
        assertThat(dataSource, IsInstanceOf.instanceOf(BitronixXaDataSourceWrapper.class));
    }

    private DataSource getDataSourceAfterInitialization(DataSource mockDataSource) throws InitialisationException
    {
        JdbcConnector connector = new JdbcConnector(muleContext);
        connector.setDataSource(mockDataSource);
        connector.initialise();
        return connector.getDataSource();
    }

    private interface TestXADataSource extends XADataSource, DataSource
    {

    }

}
