/*
 * Copyright (c) 2014 Red Hat, Inc. and others
 *
 * Red Hat licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 *
 */

package io.vertx.config.impl.spi;

import io.vertx.config.spi.ConfigStore;
import io.vertx.config.spi.utils.JsonObjectHelper;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.internal.VertxInternal;
import io.vertx.core.json.JsonObject;

import java.util.concurrent.atomic.AtomicReference;

/**
 * An implementation of configuration store loading the content from the system properties.
 *
 * @author <a href="http://escoffier.me">Clement Escoffier</a>
 */
public class SystemPropertiesConfigStore implements ConfigStore {
  private final VertxInternal vertx;
  private final boolean cache;
  private final Boolean rawData;
  private final Boolean hierarchical;

  private AtomicReference<Buffer> cached = new AtomicReference<>();

  public SystemPropertiesConfigStore(Vertx vertx, JsonObject configuration) {
    this.vertx = (VertxInternal) vertx;
    cache = configuration.getBoolean("cache", true);
    rawData = configuration.getBoolean("raw-data", false);
    hierarchical = configuration.getBoolean("hierarchical", false);
  }

  @Override
  public Future<Buffer> get() {
    Buffer value = cached.get();
    if (value == null) {
      value = JsonObjectHelper.from(System.getProperties(), rawData, hierarchical).toBuffer();
      if (cache) {
        cached.set(value);
      }
    }
    return vertx.getOrCreateContext().succeededFuture(value);
  }

  @Override
  public Future<Void> close() {
    return vertx.getOrCreateContext().succeededFuture();
  }
}
