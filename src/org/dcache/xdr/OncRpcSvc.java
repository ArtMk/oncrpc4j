/*
 * This library is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Library General Public License as
 * published by the Free Software Foundation; either version 2 of the
 * License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Library General Public License for more details.
 *
 * You should have received a copy of the GNU Library General Public
 * License along with this program (see the file COPYING.LIB for more
 * details); if not, write to the Free Software Foundation, Inc.,
 * 675 Mass Ave, Cambridge, MA 02139, USA.
 */
package org.dcache.xdr;

import org.dcache.xdr.gss.GssProtocolFilter;
import com.sun.grizzly.Controller;
import com.sun.grizzly.ControllerStateListenerAdapter;
import com.sun.grizzly.DefaultProtocolChain;
import com.sun.grizzly.DefaultProtocolChainInstanceHandler;
import com.sun.grizzly.DefaultSelectionKeyHandler;
import com.sun.grizzly.PortRange;
import com.sun.grizzly.ProtocolChain;
import com.sun.grizzly.ProtocolChainInstanceHandler;
import com.sun.grizzly.ProtocolFilter;
import com.sun.grizzly.TCPSelectorHandler;
import com.sun.grizzly.UDPSelectorHandler;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.dcache.xdr.gss.GssSessionManager;
import org.dcache.xdr.portmap.GenericPortmapClient;
import org.dcache.xdr.portmap.OncPortmapClient;
import org.dcache.xdr.portmap.OncRpcPortmap;

public class OncRpcSvc {

    private final static Logger _log = Logger.getLogger(OncRpcSvc.class.getName());

    /**
     * Default name of RPC service.
     */
    private final static String DEFAULT_SERVICE_NAME = "ONCRPC Service";

    private final Controller _controller = new Controller();
    private final CountDownLatch _serverReady = new CountDownLatch(1);
    private final boolean _publish;

    /**
     * Handle RPCSEC_GSS
     */
    private GssSessionManager _gssSessionManager;

    /**
     * mapping of registered programs.
     */
    private final Map<OncRpcProgram, RpcDispatchable> _programs =
            new ConcurrentHashMap<OncRpcProgram, RpcDispatchable>();

    /**
     * Name of this service. Used as a thread name.
     */
    private final String _name;

    /**
     * Create a new server with default name. Bind to all supported protocols.
     *
     * @param port TCP/UDP port to which service will he bound.
     */
    public OncRpcSvc(int port) {
        this(port, IpProtocolType.TCP | IpProtocolType.UDP, true, DEFAULT_SERVICE_NAME);
    }

    /**
     * Create a new server with given. Bind to all supported protocols.
     *
     * @param port to bing
     * @param name of the service
     */
    public OncRpcSvc(int port, String name) {
        this(port, IpProtocolType.TCP | IpProtocolType.UDP, true, name);
    }

    /**
     * Create a new server. Bind to all supported protocols.
     *
     * @param port TCP/UDP port to which service will he bound.
     * @param publish if true, register service by portmap
     */
    public OncRpcSvc(int port, boolean publish, String nameOfServer) {
        this(port, IpProtocolType.TCP | IpProtocolType.UDP, publish, nameOfServer);
    }

    /**
     * Create a new server with given name, protocol and port number.
     *
     * @param port TCP/UDP port to which service will he bound.
     * @param protocol to bind (tcp or udp)
     * @param name of the service
     */
    public OncRpcSvc(int port, int protocol, boolean publish, String name) {
        this(new PortRange(port), protocol, publish, name);
    }

    /**
     * Create a new server with @{link PortRange} and name. If <code>publish</code>
     * is <code>true</code>, publish this service in a portmap.
     *
     * @param portRange to use.
     * @param publish this service
     * @param name of the service
     */
    public OncRpcSvc(PortRange portRange, boolean publish, String name) {
        this(portRange, IpProtocolType.TCP | IpProtocolType.UDP, publish, name);
    }

    /**
     * Create a new server with given @{link PortRange} and name. If <code>publish</code>
     * is <code>true</code>, publish this service in a portmap.
     *
     * @param {@link PortRange} of TCP/UDP ports to which service will he bound.
     * @param protocol to bind (tcp or udp).
     * @param publish this service.
     * @param name of the service.
     */
    public OncRpcSvc(PortRange portRange, int protocol, boolean publish, String name) {

        _publish = publish;
        _name = name;

        if( (protocol & (IpProtocolType.TCP | IpProtocolType.UDP)) == 0 ) {
            throw new IllegalArgumentException("TCP or UDP protocol have to be defined");
        }

        /*
         * By default, a SelectionKey will be active for 30 seconds.
         * If during that 30 seconds the client isn't pushing bytes
         * (or closing the connection), the SelectionKey will expire
         * and its channel closed.
         *
         * We set expire timeout to -1, which equal to 'never'.
         */
        DefaultSelectionKeyHandler keyHandler = new DefaultSelectionKeyHandler();
        keyHandler.setTimeout(-1);

        if((protocol & IpProtocolType.TCP) != 0) {
            final TCPSelectorHandler tcp_handler = new TCPSelectorHandler();
            tcp_handler.setPortRange(portRange);
            tcp_handler.setSelectionKeyHandler(keyHandler);
            _controller.addSelectorHandler(tcp_handler);
        }

        if((protocol & IpProtocolType.UDP) != 0) {
            final UDPSelectorHandler udp_handler = new UDPSelectorHandler();
            udp_handler.setPortRange(portRange);
            udp_handler.setSelectionKeyHandler(keyHandler);
            _controller.addSelectorHandler(udp_handler);
        }

        _controller.addStateListener(
                new ControllerStateListenerAdapter() {

                    @Override
                    public void onReady() {
                        _serverReady.countDown();
                    }
                });
    }

    /**
     * Register services in portmap.
     *
     * @throws IOException
     * @throws UnknownHostException
     */
    private void publishToPortmap() throws IOException, UnknownHostException {

        OncRpcClient rpcClient = new OncRpcClient(InetAddress.getLocalHost(),
                IpProtocolType.UDP, OncRpcPortmap.PORTMAP_PORT);
        XdrTransport transport = rpcClient.connect();
        OncPortmapClient portmapClient = new GenericPortmapClient(transport);

        try {
            TCPSelectorHandler tcp = (TCPSelectorHandler) _controller.getSelectorHandler(Controller.Protocol.TCP);
            UDPSelectorHandler udp = (UDPSelectorHandler) _controller.getSelectorHandler(Controller.Protocol.UDP);
            String username = System.getProperty("user.name");
            for (OncRpcProgram program : _programs.keySet()) {
                try {
                    if (tcp != null) {
                        portmapClient.setPort(program.getNumber(), program.getVersion(),
                                "tcp", netid.toString(tcp.getPortLowLevel()), username);
                    }
                    if (udp != null) {
                        portmapClient.setPort(program.getNumber(), program.getVersion(),
                                "udp", netid.toString(udp.getPortLowLevel()), username);
                    }
                } catch (OncRpcException ex) {
                    _log.log(Level.SEVERE, "Failed to register program", ex);
                }
            }
        } finally {
            rpcClient.close();
        }
    }

    public void setGssSessionManager( GssSessionManager gssSessionManager) {
        _gssSessionManager = gssSessionManager;
    }
    /**
     * Start service.
     */
    public void start() throws IOException  {

        final ProtocolFilter protocolKeeper = new ProtocolKeeperFilter();
        final ProtocolFilter rpcFilter = new RpcParserProtocolFilter();
        final ProtocolFilter rpcProcessor = new RpcProtocolFilter();
        final ProtocolFilter rpcDispatcher = new RpcDispatcher(_programs);

        final ProtocolChain protocolChain = new DefaultProtocolChain();
        protocolChain.addFilter(protocolKeeper);
        protocolChain.addFilter(rpcFilter);
        protocolChain.addFilter(rpcProcessor);

        // use GSS if configures
        if (_gssSessionManager != null ) {
            final ProtocolFilter gssManager = new GssProtocolFilter(_gssSessionManager);
            protocolChain.addFilter(gssManager);
        }
        protocolChain.addFilter(rpcDispatcher);

        ((DefaultProtocolChain) protocolChain).setContinuousExecution(true);

        ProtocolChainInstanceHandler pciHandler = new DefaultProtocolChainInstanceHandler() {

            @Override
            public ProtocolChain poll() {
                return protocolChain;
            }

            @Override
            public boolean offer(ProtocolChain pc) {
                return false;
            }
        };

        _controller.setProtocolChainInstanceHandler(pciHandler);
        new Thread(_controller, _name).start();
        try {
            _serverReady.await();
        } catch (InterruptedException ex) {
            _log.log(Level.SEVERE, "failed to start Controller", ex);
            throw new IOException(ex.getMessage());
        }

        if(_publish) {
            publishToPortmap();
        }
    }

    /**
     * Stop service.
     */
    public void stop() {
        try {
            _controller.stop();
        } catch (IOException e) {
           _log.log(Level.SEVERE, "failed to stop Controller", e);
        }
    }

    /**
     * Add programs to existing services.
     * @param services
     */
    public void setPrograms(Map<OncRpcProgram, RpcDispatchable> services) {
        _programs.putAll(services);
    }

    /**
     * Register a new PRC service. Existing registration will be overwritten.
     *
     * @param prog program number
     * @param handler RPC requests handler.
     */
    public void register(OncRpcProgram prog, RpcDispatchable handler) {
        _log.log(Level.INFO, "Registering new program {0} : {1}",
                new Object[] {prog, handler});
        _programs.put(prog, handler);
    }

    /**
     * Unregister program.
     *
     * @param prog
     */
    public void unregister(OncRpcProgram prog) {
        _log.log(Level.INFO, "Inregistering program {0}", prog);
        _programs.remove(prog);
    }

    /**
     * Get number of maximal concurrent threads.
     * @return thread number
     */
    public int getThreadCount() {
        return _controller.getReadThreadsCount();
    }

    /**
     * Set the maximal number of concurrent threads.
     * @param count
     */
    public void setThreadCount(int count) {
        _controller.setReadThreadsCount(count);
    }

    /**
     * Returns the address of the endpoint this service is bound to,
     * or <code>null<code> if it is not bound yet.
     * @param protocol
     * @return a {@link InetSocketAddress} representing the local endpoint of
     * this service, or <code>null</code> if it is not bound yet.
     */
    public InetSocketAddress getInetSocketAddress(int protocol) {

        TCPSelectorHandler handler;
        switch(protocol){
            case IpProtocolType.TCP:
                handler =
                    (TCPSelectorHandler) _controller.getSelectorHandler(Controller.Protocol.TCP);
                break;
            case IpProtocolType.UDP:
                handler =
                    (UDPSelectorHandler) _controller.getSelectorHandler(Controller.Protocol.UDP);
                break;
            default:
                handler = null;
        }

        if (handler != null) {
            return new InetSocketAddress(handler.getInet(), handler.getPort());
        }
       return null;
    }
}
