/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */

package org.mule.module.db.resolver.param;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import org.mule.module.db.domain.connection.DbConnection;
import org.mule.module.db.domain.param.DefaultInputQueryParam;
import org.mule.module.db.domain.query.QueryTemplate;
import org.mule.module.db.domain.query.QueryType;
import org.mule.module.db.domain.type.DbType;
import org.mule.module.db.domain.type.DbTypeManager;
import org.mule.module.db.domain.type.UnknownDbType;
import org.mule.module.db.test.util.DbConnectionBuilder;
import org.mule.module.db.test.util.DbTypeManagerBuilder;
import org.mule.module.db.test.util.DbTypes;
import org.mule.module.db.test.util.ParameterMetaDataBuilder;
import org.mule.tck.junit4.AbstractMuleTestCase;
import org.mule.tck.size.SmallTest;

import java.sql.ParameterMetaData;
import java.sql.PreparedStatement;
import java.util.Collections;
import java.util.Map;

import org.junit.Test;

@SmallTest
public class QueryParamTypeResolverTestCase extends AbstractMuleTestCase
{

    @Test
    public void resolvesQueryParameterTypes() throws Exception
    {
        final String sqlText = "select * from test where id = ?";

        ParameterMetaData parameterMetaData = new ParameterMetaDataBuilder().withParameter(1, DbTypes.INTEGER_DB_TYPE).build();

        PreparedStatement preparedStatement = mock(PreparedStatement.class);
        when(preparedStatement.getParameterMetaData()).thenReturn(parameterMetaData);

        DbConnection connection = new DbConnectionBuilder().preparing(sqlText, preparedStatement).build();

        QueryTemplate queryTemplate = new QueryTemplate(sqlText, QueryType.SELECT, Collections.<org.mule.module.db.domain.param.QueryParam>singletonList(new DefaultInputQueryParam(1, UnknownDbType.getInstance(), "7", "param1")));

        DbTypeManager dbTypeManager = new DbTypeManagerBuilder().on(connection).managing(DbTypes.INTEGER_DB_TYPE).build();

        QueryParamTypeResolver paramTypeResolver = new QueryParamTypeResolver(dbTypeManager);

        Map<Integer, DbType> parameterTypes = paramTypeResolver.getParameterTypes(connection, queryTemplate);

        assertThat(parameterTypes.size(), equalTo(1));
        assertThat(parameterTypes.get(1), equalTo(DbTypes.INTEGER_DB_TYPE));
    }
}
