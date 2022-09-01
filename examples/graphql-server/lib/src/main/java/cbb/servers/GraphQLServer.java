package cbb.servers;

import cbb.BeanScope;
import cbb.annotations.Scope;
import cbb.graphql.SchemaGenerator;
import graphql.kickstart.servlet.GraphQLHttpServlet;
import graphql.kickstart.servlet.OsgiGraphQLHttpServlet;
import graphql.schema.GraphQLSchema;

import java.net.ServerSocket;
import cbb.Couchbeans;
import jakarta.servlet.Servlet;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;

@Scope(BeanScope.GLOBAL)
public class GraphQLServer {
    private boolean running;
    private short port;

    private static Server server;

    private static transient GraphQLSchema schema;

    public void whenRunning() throws Exception {
        if (schema == null) {
            schema = SchemaGenerator.generate(Couchbeans.allChildren(this, Class.class));
        }
        if (server == null) {
            GraphQLHttpServlet servlet = GraphQLHttpServlet.with(schema);
            ServletHolder holder = new ServletHolder((Servlet) servlet);
            server = new Server(port);
            ServletContextHandler servletContextHandler = new ServletContextHandler();
            servletContextHandler.setContextPath("/");
            servletContextHandler.addServlet(holder, "/");
            server.setHandler(servletContextHandler);

        }
        server.start();
    }

    public void whenNotRunning() throws Exception {
        server.stop();
    }

    public void linkChild(Class type) {
        // regenerate the server schema
        schema = SchemaGenerator.regenerate(type);
    }
}
