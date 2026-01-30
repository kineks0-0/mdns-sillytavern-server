/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package javax.jmdns.impl;

import java.io.IOException;
import java.net.DatagramPacket;

import javax.jmdns.impl.constants.DNSConstants;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Listen for multicast packets.
 * Patched version to handle Legacy Unicast Responses (RFC 6762 Sec 6.7).
 */
public class PatchedSocketListener extends Thread {
    final Logger logger = LoggerFactory.getLogger(PatchedSocketListener.class);

    private final JmDNSImpl _jmDNSImpl;

    public PatchedSocketListener(JmDNSImpl jmDNSImpl) {
        super("PatchedSocketListener(" + (jmDNSImpl != null ? jmDNSImpl.getName() : "") + ")");
        this.setDaemon(true);
        this._jmDNSImpl = jmDNSImpl;
    }

    private void sleepThread() {
        if (_jmDNSImpl._threadSleepDurationMs > 0) {
            try {
                // sleep a small amount of time in case the network is overloaded with mdns packets (some devices do this),
                // in order to allow other threads to get some cpu time
                Thread.sleep(_jmDNSImpl._threadSleepDurationMs);
            } catch (InterruptedException e) {
                logger.warn("{}.run() interrupted ", this.getName(), e);
                Thread.currentThread().interrupt();
            }
        }
    }

    @Override
    public void run() {
        try {
            byte[] buf = new byte[DNSConstants.MAX_MSG_ABSOLUTE];
            DatagramPacket packet = new DatagramPacket(buf, buf.length);

            while (!this._jmDNSImpl.isCanceling() && !this._jmDNSImpl.isCanceled()) {
                sleepThread();
                packet.setLength(buf.length);
                this._jmDNSImpl.getSocket().receive(packet);
                if (this._jmDNSImpl.isCanceling() || this._jmDNSImpl.isCanceled() || this._jmDNSImpl.isClosing() || this._jmDNSImpl.isClosed()) {
                    break;
                }
                try {
                    if (this._jmDNSImpl.getLocalHost().shouldIgnorePacket(packet)) {
                        continue;
                    }

                    DNSIncoming msg = new DNSIncoming(packet);
                    if (msg.isValidResponseCode()) {
                        if (logger.isTraceEnabled()) {
                            logger.trace("{}.run() JmDNS in:{}", this.getName(), msg.print(true));
                        }
                        if (msg.isQuery()) {
                            // PATCH START: Check for Legacy Unicast Query (Source Port != 5353)
                            // RFC 6762 Section 6.7
                            if (packet.getPort() != DNSConstants.MDNS_PORT) {
                                if (logger.isDebugEnabled()) {
                                    logger.debug("{}.run() JmDNS Legacy Unicast Query from {}:{}", this.getName(), packet.getAddress(), packet.getPort());
                                }
                                this._jmDNSImpl.handleQuery(msg, packet.getAddress(), packet.getPort());
                            }
                            // When we have a QUERY, unique means that QU is true, and we should respond to the sender directly
                            else if (msg.getQuestions().stream().anyMatch(DNSEntry::isUnique)) {
                                this._jmDNSImpl.handleQuery(msg, packet.getAddress(), packet.getPort());
                            } else {
                                this._jmDNSImpl.handleQuery(msg, this._jmDNSImpl.getGroup(), DNSConstants.MDNS_PORT);
                            }
                            // PATCH END
                        } else {
                            this._jmDNSImpl.handleResponse(msg);
                        }
                    } else {
                        if (logger.isDebugEnabled()) {
                            logger.debug("{}.run() JmDNS in message with error code: {}", this.getName(), msg.print(true));
                        }
                    }
                } catch (IOException e) {
                    logger.warn("{}.run() exception ", this.getName(), e);
                }
            }
        } catch (IOException e) {
            if (!this._jmDNSImpl.isCanceling() && !this._jmDNSImpl.isCanceled() && !this._jmDNSImpl.isClosing() && !this._jmDNSImpl.isClosed()) {
                logger.warn("{}.run() exception ", this.getName(), e);
                this._jmDNSImpl.recover();
            }
        }
        logger.trace("{}.run() exiting.", this.getName());
    }

    public JmDNSImpl getDns() {
        return _jmDNSImpl;
    }

}
