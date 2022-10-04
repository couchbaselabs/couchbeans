package cbb.graphql;

import cbb.BeanLink;
import cbb.Utils;
import com.google.common.collect.Streams;
import graphql.TypeResolutionEnvironment;
import graphql.schema.GraphQLCodeRegistry;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLOutputType;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLTypeReference;
import graphql.schema.GraphQLUnionType;
import graphql.schema.TypeResolver;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.stream.Stream;

public class SchemaGenerator {

    protected static final Map<Class, GraphQLObjectType> TYPES = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Set<Class> BUILDING_TYPES = Collections.synchronizedSet(new HashSet<>());

    private static final GraphQLUnionType.Builder QUERY_INTERFACE_BUILDER = GraphQLUnionType.newUnionType();
    private static GraphQLUnionType QUERY_INTERFACE;
    private static GraphQLObjectType QUERY_TYPE;
    public static GraphQLSchema generate(Stream<Class> types) {

        GraphQLSchema.Builder builder = GraphQLSchema.newSchema();

        types
                .peek(t -> QUERY_INTERFACE_BUILDER.possibleType(new GraphQLTypeReference(typeName(t))))
                .map(SchemaGenerator::processClass)
                .forEach(builder::additionalType);

        final GraphQLOutputType linkType = processClass(BeanLink.class);
        builder.additionalType(linkType);

        QUERY_INTERFACE_BUILDER.name("couchbean");
        QUERY_INTERFACE_BUILDER.possibleType(new GraphQLTypeReference(typeName(BeanLink.class)));
        QUERY_INTERFACE = QUERY_INTERFACE_BUILDER.build();

        builder.additionalType(QUERY_INTERFACE);
        builder.codeRegistry(GraphQLCodeRegistry.newCodeRegistry().typeResolver(QUERY_INTERFACE, new CouchbeansTypeResolver()).build());

        QUERY_TYPE = GraphQLObjectType.newObject().name("queryType").field(b -> b.name("object").type(QUERY_INTERFACE)).build();
        builder.query(QUERY_TYPE);

        return builder.build();
    }

    private static GraphQLOutputType processClass(Class type) {
        if (!TYPES.containsKey(type)) {
            if (BUILDING_TYPES.contains(type)) {
                return new GraphQLTypeReference(typeName(type));
            } else {
                BUILDING_TYPES.add(type);
            }

            GraphQLObjectType.Builder builder = new GraphQLObjectType.Builder();
            builder.name(typeName(type));
            Utils.getFields(type).map(field -> new GraphQLFieldDefinition.Builder()
                            .name(field.getName())
                            .type(processClass(type))
                            .build())
                    .forEach(builder::field);
            TYPES.put(type, builder.build());
            BUILDING_TYPES.remove(type);
        }
        return TYPES.get(type);
    }

    public static String typeName(Class type) {
        return type.getCanonicalName().replaceAll("\\.", "_");
    }

    public static GraphQLSchema regenerate(Class type) {
        return generate(Streams.concat(TYPES.keySet().stream(), Stream.of(type)));
    }

    public static class CouchbeansTypeResolver implements TypeResolver {
        @Override
        public GraphQLObjectType getType(TypeResolutionEnvironment env) {
            return TYPES.get(env.getObject().getClass());
        }
    }
}
