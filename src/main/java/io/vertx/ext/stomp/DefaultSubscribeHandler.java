/*
 *  Copyright (c) 2011-2015 The original author or authors
 *  ------------------------------------------------------
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  and Apache License v2.0 which accompanies this distribution.
 *
 *       The Eclipse Public License is available at
 *       http://www.eclipse.org/legal/epl-v10.html
 *
 *       The Apache License v2.0 is available at
 *       http://www.opensource.org/licenses/apache2.0.php
 *
 *  You may elect to redistribute this code under either of these licenses.
 */

package io.vertx.ext.stomp;

import io.vertx.core.Handler;
import io.vertx.ext.stomp.utils.Headers;

import java.util.List;

/**
 * STOMP compliant actions executed when receiving a {@code SUBSCRIBE} frame.
 * <p/>
 * This handler is thread safe.
 *
 * @author <a href="http://escoffier.me">Clement Escoffier</a>
 */
public class DefaultSubscribeHandler implements Handler<ServerFrame> {
  @Override
  public void handle(ServerFrame serverFrame) {
    Frame frame = serverFrame.frame();
    StompServerConnection connection = serverFrame.connection();
    String id = frame.getHeader(Frame.ID);
    String destination = frame.getHeader(Frame.DESTINATION);
    String ack = frame.getHeader(Frame.ACK);
    if (ack == null) {
      ack = "auto";
    }

    if (destination == null || id == null) {
      connection.write(Frames.createErrorFrame(
          "Invalid subscription",
          Headers.create(
              frame.getHeaders()), "The 'destination' and 'id' headers must be set"));
      connection.close();
      return;
    }

    // Ensure that the subscription id is unique
    int count = 0;
    for (Destination dest : connection.handler().getDestinations()) {
      List<String> ids = dest.getSubscriptions(connection);
      count += ids.size();
      if (ids.contains(id)) {
        connection.write(Frames.createErrorFrame(
            "Invalid subscription",
            Headers.create(frame.getHeaders()), "'id'" +
                " already used by this connection."));
        connection.close();
        return;
      }
      if (count + 1 > connection.server().options().getMaxSubscriptionsByClient()) {
        connection.write(Frames.createErrorFrame(
            "Invalid subscription",
            Headers.create(frame.getHeaders()), "Too many subscriptions"));
        connection.close();
        return;
      }
    }

    final Destination dest = connection.handler().getOrCreateDestination(destination);
    if (dest != null) {
      if (dest.subscribe(connection, frame) == null) {
        // Access denied
        connection.write(Frames.createErrorFrame(
            "Access denied",
            Headers.create(frame.getHeaders()), "The destination has been rejected by the server"));
        connection.close();
        return;
      }
    } else {
      connection.write(Frames.createErrorFrame(
          "Invalid subscription",
          Headers.create(frame.getHeaders()), "The destination has been rejected by the server"));
      connection.close();
      return;
    }

    Frames.handleReceipt(frame, connection);
  }
}
