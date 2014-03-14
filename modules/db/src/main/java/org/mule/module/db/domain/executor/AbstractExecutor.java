/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */

package org.mule.module.db.domain.executor;

import org.mule.module.db.domain.logger.DefaultQueryLoggerFactory;
import org.mule.module.db.domain.logger.QueryLoggerFactory;
import org.mule.module.db.domain.logger.SingleQueryLogger;
import org.mule.module.db.domain.param.InputQueryParam;
import org.mule.module.db.domain.param.OutputQueryParam;
import org.mule.module.db.domain.param.QueryParam;
import org.mule.module.db.domain.query.QueryParamValue;
import org.mule.module.db.domain.query.QueryTemplate;
import org.mule.module.db.domain.statement.StatementFactory;
import org.mule.module.db.domain.type.DbType;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Base class for query executors
 */
public abstract class AbstractExecutor
{

    protected final Log logger = LogFactory.getLog(this.getClass());
    protected final StatementFactory statementFactory;
    protected QueryLoggerFactory queryLoggerFactory = new DefaultQueryLoggerFactory();

    public AbstractExecutor(StatementFactory statementFactory)
    {
        this.statementFactory = statementFactory;
    }

    protected void doProcessParameters(PreparedStatement statement, QueryTemplate queryTemplate, List<QueryParamValue> paramValues, SingleQueryLogger queryLogger) throws SQLException
    {
        for (int index = 1, inputParamsSize = queryTemplate.getParams().size(); index <= inputParamsSize; index++)
        {
            QueryParam queryParam = queryTemplate.getParams().get(index - 1);
            if (queryParam instanceof InputQueryParam)
            {
                QueryParamValue param = paramValues.get(index - 1);

                queryLogger.addParameter(queryTemplate.getInputParams().get(index - 1), param.getValue());

                processInputParam(statement, index, param.getValue(), queryParam.getType());
            }

            if (queryParam instanceof OutputQueryParam)
            {
                processOutputParam((CallableStatement) statement, index, queryParam.getType());
            }
        }
    }

    protected void processInputParam(PreparedStatement statement, int index, Object value, DbType type) throws SQLException
    {
        type.setParameterValue(statement, index, value);
    }

    private void processOutputParam(CallableStatement statement, int index, DbType type) throws SQLException
    {
        type.registerOutParameter(statement, index);
    }

    public void setQueryLoggerFactory(QueryLoggerFactory queryLoggerFactory)
    {
        this.queryLoggerFactory = queryLoggerFactory;
    }
}
