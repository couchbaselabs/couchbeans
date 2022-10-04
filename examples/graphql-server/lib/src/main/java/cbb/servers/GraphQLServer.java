package cbb.servers;

import cbb.BeanScope;
import cbb.Couchbeans;
import cbb.annotations.Scope;
import cbb.graphql.SchemaGenerator;
import graphql.kickstart.servlet.GraphQLHttpServlet;
import graphql.schema.GraphQLSchema;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;

import javax.servlet.Servlet;

@Scope(BeanScope.GLOBAL)
public class GraphQLServer {
    private boolean running;
    private short port;

    private static Server server;

    private static transient GraphQLSchema schema;

    public void whenRunning() throws Exception {
        if (port != 0) {
            launchServer();
        }
    }

    public void setPort(short port) throws Exception {
        this.port = port;
        if (running) {
            stopServer();
            launchServer();
        }
    }

    private void launchServer() throws Exception {
        if (schema == null) {
            schema = SchemaGenerator.generate(Couchbeans.allChildren(this, Class.class));
        }
            ServletHolder holder = new ServletHolder(servlet);
            server = new Server(port);
            ServletContextHandler servletContextHandler = new ServletContextHandler();
            servletContextHandler.setContextPath("/");
            servletContextHandler.addServlet(holder, "/");
            server.setHandler(servletContextHandler);

        }
        server.start();
    }

    public void whenNotRunning() throws Exception {
        stopServer();
    }
    public void stopServer() throws Exception {
        if (server != null && server.isRunning()) {
            server.stop();
        }
        server = null;
    }

    public void linkChild(Class type) {
        // regenerate the server schema
        schema = SchemaGenerator.regenerate(type);
    }
}
