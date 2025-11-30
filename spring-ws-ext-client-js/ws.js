class WebSocketWrapper {
    constructor({
        host,
        maxReconnectAttempts = 5,
        reconnectDelay = 1000,
        maxReconnectDelay = 30000,
        debug = false,
        stopConnectionCodes = [1008, 1003] /* POLICY_VIOLATION and NOT_ACCEPTABLE */
    } = {}) {
        this.socket = null;
        this.url = null;
        this.host = host;
        this.callbacks = {};
        this.pendingCallbacks = {};
        this.connectedCallbacks = [];
        this.disconnectedCallbacks = [];
        this.stopConnectionCodes = stopConnectionCodes;

        this.shouldReconnect = true;
        this.reconnectDelay = reconnectDelay;
        this.maxReconnectDelay = maxReconnectDelay;
        this.baseReconnectDelay = reconnectDelay;
        this.maxReconnectAttempts = maxReconnectAttempts;
        this.debug = debug;
        this.currentReconnectAttempts = 0;
    }

    connect(url) {
        this.url = this.host + url;
        this._connectSocket();
    }

    _connectSocket() {
        this.socket = new WebSocket(this.url);

        this.socket.onopen = () => {
            this.currentReconnectAttempts = 0;
            this.reconnectDelay = this.baseReconnectDelay;
            this.connectedCallbacks.forEach(cb => cb());
        };

        this.socket.onclose = (event) => {
            if(event.code in this.stopConnectionCodes) {
                this.shouldReconnect = false;
                if(this.debug) {
                    console.log("Received ", event, " closing connection as code is in ", this.stopConnectionCodes);
                }
            }

            this.disconnectedCallbacks.forEach(cb => cb(event));

            if (this.shouldReconnect && this.currentReconnectAttempts < this.maxReconnectAttempts) {
                this.currentReconnectAttempts++;
                setTimeout(() => {
                    this.reconnectDelay = Math.min(this.reconnectDelay * 2, this.maxReconnectDelay);
                    this._connectSocket();
                }, this.reconnectDelay);
            }
        };

        this.socket.onmessage = (event) => {
            try {
                const message = JSON.parse(event.data);
                const { destination, payload, packetId } = message;

                if (this.debug) {
                    console.log("Received message:", { destination, packetId, payload });
                }

                if (packetId && this.pendingCallbacks[packetId]) {
                    this.pendingCallbacks[packetId](payload);
                    delete this.pendingCallbacks[packetId];
                } 

                else if (destination && this.callbacks[destination]) {
                    this.callbacks[destination](payload, packetId);
                }
            } catch (err) {
                console.error("Error handling incoming packet", event.data, err);
            }
        };
    }

    disconnect() {
        this.shouldReconnect = false;
        if (this.socket) {
            this.socket.close();
        }
    }

    retryConnect() {
        if (!this.socket || this.socket.readyState === WebSocket.CLOSED) {
            this.shouldReconnect = true;
            this.currentReconnectAttempts = 0;
            this.reconnectDelay = this.baseReconnectDelay;
            this._connectSocket();
        }
    }

    onConnected(callback) {
        this.connectedCallbacks.push(callback);
    }

    onDisconnect(callback) {
        this.disconnectedCallbacks.push(callback);
    }

    registerCallback(destination, callback) {
        this.callbacks[destination] = callback;
    }

    registerCallbacks(destinations, callback) {
        if (Array.isArray(destinations)) {
            destinations.forEach(destination => {
                this.callbacks[destination] = callback;
            });
        } else {
            this.callbacks[destinations] = callback;
        }
    }

    sendPacket(destination, payload = {}) {
        if (this.socket.readyState === WebSocket.OPEN) {
            const packet = { destination, payload };
            if (this.debug) {
                console.log("Sending packet:", packet);
            }
            this.socket.send(JSON.stringify(packet));
            return true;
        } else {
            if (this.debug) {
                console.warn("Cannot send packet, socket is not open:", { destination, payload });
            }
            return false;
        }
    }

    sendCallbackPacket(destination, callback, payload = {}) {
        const packetId = this._generatePacketId();  
        this.pendingCallbacks[packetId] = callback;  

        const packet = {
            destination,
            payload,
            packetId 
        };

        if (this.socket.readyState === WebSocket.OPEN) {
            if (this.debug) {
                console.log("Sending callback packet:", packet);
            }
            this.socket.send(JSON.stringify(packet));
            return true;
        } else {
            if (this.debug) {
                console.warn("Cannot send callback packet, socket is not open:", packet);
            }
            delete this.pendingCallbacks[packetId];
            return false;
        }
    }

    _generatePacketId() {  
        return Math.random().toString(36).substr(2, 9) + Date.now();
    }
}