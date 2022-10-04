package cbb.graphql;

import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;

public class CouchbeansDataFetcher<T> implements DataFetcher<T> {
    @Override
    public T get(DataFetchingEnvironment environment) throws Exception {
        return null;
    }
}
