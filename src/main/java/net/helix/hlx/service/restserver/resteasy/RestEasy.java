package net.helix.hlx.service.restserver.resteasy;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.undertow.Handlers;
import io.undertow.Undertow;
import io.undertow.security.api.AuthenticationMode;
import io.undertow.security.impl.BasicAuthenticationMechanism;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.servlet.api.DeploymentInfo;
import io.undertow.util.*;
import net.helix.hlx.Helix;
import net.helix.hlx.conf.APIConfig;
import net.helix.hlx.service.RemoteAuth;
import net.helix.hlx.service.dto.AbstractResponse;
import net.helix.hlx.service.dto.AccessLimitedResponse;
import net.helix.hlx.service.dto.ErrorResponse;
import net.helix.hlx.service.dto.ExceptionResponse;
import net.helix.hlx.service.restserver.ApiProcessor;
import net.helix.hlx.service.restserver.RestConnector;
import net.helix.hlx.utils.HelixIOUtils;
import org.jboss.resteasy.plugins.server.undertow.UndertowJaxrsServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnio.channels.StreamSinkChannel;
import org.xnio.streams.ChannelInputStream;

import javax.ws.rs.ApplicationPath;
import javax.ws.rs.core.Application;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 *
 * Rest connector based on RestEasy, which uses Jaxrs server under the hood.
 * This will not actually have any REST endpoints, but rather handle all incoming connections from one point.
 *
 */
@ApplicationPath("")
public class RestEasy extends Application implements RestConnector {

    private static final Logger log = LoggerFactory.getLogger(RestEasy.class);

    private final Gson gson = new GsonBuilder().create();

    private UndertowJaxrsServer server;

    private DeploymentInfo info;

    private ApiProcessor processFunction;

    private int maxBodyLength;

    private String remoteAuth;

    private String apiHost;

    private int port;

    /**
     * Required for every {@link Application}
     * Will be instantiated once and is supposed to provide REST api classes
     * <b>Do not call manually</b>
     *
     * We handle all calls manually without ever using the {@link Application} functionality using a
     * {@link DeploymentInfo#addInnerHandlerChainWrapper}
     */
    public RestEasy() {

    }

    /**
     *
     * @param configuration
     */
    public RestEasy(APIConfig configuration) {
        maxBodyLength = configuration.getMaxBodyLength();
        port = configuration.getPort();
        apiHost = configuration.getApiHost();

        remoteAuth = configuration.getRemoteAuth();
    }

    /**
     * Prepares the API for usage. Until this method is called, no HTTP requests can be made.
     * The order of loading is as follows
     * <ol>
     *    <li>
     *        Read the spend addresses from the previous epoch. Used in {@link #wasAddressSpentFrom(Hash)}.
     *        This only happens if {@link APIConfig#isTestnet()} is <tt>false</tt>
     *        If reading from the previous epoch fails, a log is printed. The API will continue to initialize.
     *    </li>
     *    <li>
     *        Get the {@link APIConfig} from the {@link Helix} instance,
     *        and read {@link APIConfig#getPort()} and {@link APIConfig#getApiHost()}
     *    </li>
     *    <li>
     *        Builds a secure {@link Undertow} server with the port and host.
     *        If {@link APIConfig#getRemoteAuth()} is defined, remote authentication is blocked for anyone except
     *         those defined in {@link APIConfig#getRemoteAuth()} or localhost.
     *        This is done with {@link BasicAuthenticationMechanism} in a {@link AuthenticationMode#PRO_ACTIVE} mode.
     *        By default, this authentication is disabled.
     *    </li>
     *    <li>
     *        Starts the server, opening it for HTTP API requests
     *    </li>
     * </ol>
     */
    @Override
    public void init(ApiProcessor processFunction) {
        log.debug("Binding JSON-REST API Undertow server on {}:{}", apiHost, port);
        this.processFunction = processFunction;

        server = new UndertowJaxrsServer();

        info = server.undertowDeployment(RestEasy.class);
        info.setDisplayName("Helix Realm");
        info.setDeploymentName("Helix Realm");
        info.setContextPath("/");
        /** TODO: check credentials here instead of in process request
         info.addSecurityWrapper(new HandlerWrapper() {
        @Override
        public HttpHandler wrap(HttpHandler toWrap) {
        String credentials = remoteAuth;
        if (credentials == null || credentials.isEmpty()) {
        return toWrap;
        }

        final Map<String, char[]> users = new HashMap<>(2);
        users.put(credentials.split(":")[0], credentials.split(":")[1].toCharArray());

        IdentityManager identityManager = new MapIdentityManager(users);
        HttpHandler handler = toWrap;
        handler = new AuthenticationCallHandler(handler);
        handler = new AuthenticationConstraintHandler(handler);
        final List<AuthenticationMechanism> mechanisms =
        Collections.singletonList(new BasicAuthenticationMechanism("Helix Realm"));

        handler = new AuthenticationMechanismsHandler(handler, mechanisms);
        handler = new SecurityInitialHandler(AuthenticationMode.PRO_ACTIVE, identityManager, handler);
        return handler;
        }
        });*/

        info.addInnerHandlerChainWrapper(handler -> {
            return Handlers.path().addPrefixPath("/", new HttpHandler() {
                @Override
                public void handleRequest(final HttpServerExchange exchange) throws Exception {
                    HttpString requestMethod = exchange.getRequestMethod();
                    if (Methods.OPTIONS.equals(requestMethod)) {
                        String allowedMethods = "GET,HEAD,POST,PUT,DELETE,TRACE,OPTIONS,CONNECT,PATCH";
                        //return list of allowed methods in response headers
                        exchange.setStatusCode(StatusCodes.OK);
                        exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, MimeMappings.DEFAULT_MIME_MAPPINGS.get("txt"));
                        exchange.getResponseHeaders().put(Headers.CONTENT_LENGTH, 0);
                        exchange.getResponseHeaders().put(Headers.ALLOW, allowedMethods);
                        exchange.getResponseHeaders().put(new HttpString("Access-Control-Allow-Origin"), "*");
                        exchange.getResponseHeaders().put(new HttpString("Access-Control-Allow-Headers"), "User-Agent, Origin, X-Requested-With, Content-Type, Accept, X-HELIX-API-Version");
                        exchange.getResponseSender().close();
                        return;
                    }

                    if (exchange.isInIoThread()) {
                        exchange.dispatch(this);
                        return;
                    }
                    processRequest(exchange);
                }
            });
        });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void start() {
        if (info != null) {
            Undertow.Builder builder = Undertow.builder()
                    .addHttpListener(port, apiHost);
            server.start(builder);
            server.deploy(info);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void stop() {
        server.stop();
    }

    /**
     * Sends the API response back as JSON to the requester.
     * Status code of the HTTP request is also set according to the type of response.
     * <ul>
     *     <li>{@link ErrorResponse}: 400</li>
     *     <li>{@link AccessLimitedRprocessRequestesponse}: 401</li>
     *     <li>{@link ExceptionResponse}: 500</li>
     *     <li>Default: 200</li>
     * </ul>
     *
     * @param exchange Contains information about what the client sent to us
     * @param res The response of the API.
     *            See {@link #processRequest(HttpServerExchange)}
     *            and {@link #process(String, InetAddress)} for the different responses in each case.
     * @param beginningTime The time when we received the request, in milliseconds.
     *                      This will be used to set the response duration in {@link AbstractResponse#setDuration(Integer)}
     * @throws IOException When connection to client has been lost - Currently being caught.
     */
    private void sendResponse(HttpServerExchange exchange, AbstractResponse res, long beginningTime) throws IOException {
        res.setDuration((int) (System.currentTimeMillis() - beginningTime));
        final String response = gson.toJson(res);

        if (res instanceof ErrorResponse) {
            // bad request or invalid parameters
            exchange.setStatusCode(400);
        } else if (res instanceof AccessLimitedResponse) {
            // API method not allowed
            exchange.setStatusCode(401);
        } else if (res instanceof ExceptionResponse) {
            // internal error
            exchange.setStatusCode(500);
        }

        setupResponseHeaders(exchange);

        ByteBuffer responseBuf = ByteBuffer.wrap(response.getBytes(StandardCharsets.UTF_8));
        exchange.setResponseContentLength(responseBuf.array().length);
        StreamSinkChannel sinkChannel = exchange.getResponseChannel();
        sinkChannel.getWriteSetter().set( channel -> {
            if (responseBuf.remaining() > 0) {
                try {
                    sinkChannel.write(responseBuf);
                    if (responseBuf.remaining() == 0) {
                        exchange.endExchange();
                    }
                } catch (IOException e) {
                    log.error("Lost connection to client - cannot send response");
                    exchange.endExchange();
                    sinkChannel.getWriteSetter().set(null);
                }
            }
            else {
                exchange.endExchange();
            }
        });
        sinkChannel.resumeWrites();
    }

    /**
     * <p>
     *     Processes an API HTTP request.
     *     No checks have been done until now, except that it is not an OPTIONS request.
     *     We can be sure that we are in a thread that allows blocking.
     * </p>
     * <p>
     *     The request process duration is recorded.
     *     During this the request gets verified. If it is incorrect, an {@link ErrorResponse}
     *     or in the case of bad authorization {@link AccessLimitedResponse} is thrown.
     *     Otherwise it is processed in {@link #process(String, InetSocketAddress)}.
     *     The result is sent back to the requester.
     * </p>
     *
     * @param exchange Contains the data the client sent to us
     * @throws IOException If the body of this HTTP request cannot be read
     */
    private void processRequest(final HttpServerExchange exchange) throws IOException {
        final ChannelInputStream cis = new ChannelInputStream(exchange.getRequestChannel());
        exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json");

        final long beginningTime = System.currentTimeMillis();
        final String body = HelixIOUtils.toString(cis, StandardCharsets.UTF_8);
        AbstractResponse response;

        String rcvdToken = (exchange.getRequestHeaders().get("Authorization") == null) ? "" : RemoteAuth.getToken(exchange.getRequestHeaders().get("Authorization").get(0));
        if (!exchange.getRequestHeaders().contains("X-HELIX-API-Version")) {
            response = ErrorResponse.create("Invalid API Version");
        } else if(this.remoteAuth != null && !this.remoteAuth.equals("") && !rcvdToken.equals(this.remoteAuth)) { // TODO: review and improve the authentication mechanism
            response = AccessLimitedResponse.create("Authorization failed");
        } else if (body.length() > maxBodyLength) {
            response = ErrorResponse.create("Request too long");
        } else {
            response = this.processFunction.processFunction(body, exchange.getSourceAddress().getAddress());
        }

        sendResponse(exchange, response, beginningTime);
    }

    /**
     * Updates the {@link HttpServerExchange} {@link HeaderMap} with the proper response settings.
     * @param exchange Contains information about what the client has send to us
     */
    private static void setupResponseHeaders(HttpServerExchange exchange) {
        final HeaderMap headerMap = exchange.getResponseHeaders();
        headerMap.add(new HttpString("Access-Control-Allow-Origin"),"*");
        headerMap.add(new HttpString("Keep-Alive"), "timeout=500, max=100");
    }
}