package cbb.graphql;

import cbb.servers.GraphQLServer;
import graphql.schema.GraphQLSchema;
import junit.framework.TestCase;

import java.util.stream.Stream;

public class SchemaGeneratorTest extends TestCase {

    public void testSchemaGenerator() {
        GraphQLSchema schema = SchemaGenerator.generate(Stream.of(GraphQLServer.class));
        assertNotNull(schema);
    }
}