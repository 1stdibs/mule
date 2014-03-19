/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */

package org.mule.module.db.resolver.query;

import org.mule.api.MuleEvent;
import org.mule.module.db.domain.connection.DbConnection;
import org.mule.module.db.domain.param.DefaultInOutQueryParam;
import org.mule.module.db.domain.param.DefaultInputQueryParam;
import org.mule.module.db.domain.param.DefaultOutputQueryParam;
import org.mule.module.db.domain.param.InOutQueryParam;
import org.mule.module.db.domain.param.InputQueryParam;
import org.mule.module.db.domain.param.OutputQueryParam;
import org.mule.module.db.domain.param.QueryParam;
import org.mule.module.db.domain.query.Query;
import org.mule.module.db.domain.query.QueryParamValue;
import org.mule.module.db.domain.query.QueryTemplate;
import org.mule.module.db.domain.type.DbType;
import org.mule.module.db.domain.type.UnknownDbType;
import org.mule.module.db.resolver.param.ParamValueResolver;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Resolves a parameterized query evaluating parameter value expression using a given event
 */
public class ParametrizedQueryResolver implements QueryResolver
{

    private static final Log logger = LogFactory.getLog(ParametrizedQueryResolver.class);

    private final Query query;
    private final ParamValueResolver paramValueResolver;

    public ParametrizedQueryResolver(Query query, ParamValueResolver paramValueResolver)
    {
        this.query = query;
        this.paramValueResolver = paramValueResolver;
    }

    @Override
    public Query resolve(DbConnection connection, MuleEvent muleEvent)
    {
        List<QueryParamValue> resolvedParams = paramValueResolver.resolveParams(muleEvent, query.getParamValues());

        QueryTemplate queryTemplate = query.getQueryTemplate();

        if (needsParamTypeResolution(queryTemplate.getParams()))
        {
            Map<Integer, DbType> paramTypes = getParameterTypes(connection, queryTemplate);

            queryTemplate = resolveQueryTemplate(queryTemplate, paramTypes);
        }

        return new Query(queryTemplate, resolvedParams);
    }

    private Map<Integer, DbType> getParameterTypes(DbConnection connection, QueryTemplate queryTemplate)
    {
        Map<Integer, DbType> paramTypes;

        try
        {
            paramTypes = connection.getParamTypes(queryTemplate);
        }
        catch (SQLException e)
        {
            logger.warn("Unable to resolve query parameter types. Using unresolved types", e);
            paramTypes = extractParamTypesFromQueryTemplate(queryTemplate);
        }
        return paramTypes;
    }

    private boolean needsParamTypeResolution(List<QueryParam> params)
    {
        for (QueryParam param : params)
        {
            if (param.getType() == UnknownDbType.getInstance())
            {
                return true;
            }
        }

        return false;
    }

    private Map<Integer, DbType> extractParamTypesFromQueryTemplate(QueryTemplate queryTemplate)
    {
        Map<Integer, DbType> paramTypes = new HashMap<Integer, DbType>();

        for (QueryParam queryParam : queryTemplate.getParams())
        {
            paramTypes.put(queryParam.getIndex(), queryParam.getType());
        }

        return paramTypes;
    }

    private QueryTemplate resolveQueryTemplate(QueryTemplate queryTemplate, Map<Integer, DbType> paramTypes)
    {
        List<QueryParam> newParams = new ArrayList<QueryParam>();

        for (QueryParam originalParam : queryTemplate.getParams())
        {
            DbType type = paramTypes.get((originalParam).getIndex());
            QueryParam newParam;

            if (originalParam instanceof InOutQueryParam)
            {
                newParam = new DefaultInOutQueryParam(originalParam.getIndex(), type, originalParam.getName(), ((InOutQueryParam) originalParam).getValue());
            }
            else if (originalParam instanceof InputQueryParam)
            {
                newParam = new DefaultInputQueryParam(originalParam.getIndex(), type, ((InputQueryParam) originalParam).getValue(), originalParam.getName());
            }
            else if (originalParam instanceof OutputQueryParam)
            {
                newParam = new DefaultOutputQueryParam(originalParam.getIndex(), type, originalParam.getName());
            }
            else
            {
                throw new IllegalArgumentException("Unknown parameter type: " + originalParam.getClass().getName());

            }

            newParams.add(newParam);
        }

        return new QueryTemplate(queryTemplate.getSqlText(), queryTemplate.getType(), newParams);
    }
}