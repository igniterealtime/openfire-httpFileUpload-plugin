/*
 * Copyright (c) 2017-2022 Ignite Realtime Foundation. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.igniterealtime.openfire.plugins.httpfileupload;

import nl.goodbytes.xmpp.xep0363.SecureUniqueId;
import nl.goodbytes.xmpp.xep0363.Slot;
import nl.goodbytes.xmpp.xep0363.SlotProvider;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.cache.Cache;
import org.jivesoftware.util.cache.CacheFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.time.Duration;
import java.util.concurrent.locks.Lock;

public class OpenfireSlotProvider implements SlotProvider
{
    private final Cache<SecureUniqueId, Slot> slotsCache;

    public OpenfireSlotProvider() {
        slotsCache = CacheFactory.createCache("HTTP File Upload Slots");

        // Unless there is specific configuration for this cache, default the max lifetime of entries in this cache to something that's fairly short.
        if (null == JiveGlobals.getProperty("cache." + slotsCache.getName().replaceAll(" ", "") + ".maxLifetime")) {
            slotsCache.setMaxCacheSize(Duration.ofMinutes(5).toMillis());
        }
    }

    @Override
    public void create(@Nonnull final Slot slot)
    {
        final Lock lock = slotsCache.getLock(slot.getUuid());
        lock.lock();
        try {
            slotsCache.put( slot.getUuid(), slot );
        } finally {
            lock.unlock();
        }
    }

    @Nullable
    @Override
    public Slot consume(@Nonnull final SecureUniqueId slotId)
    {
        final Lock lock = slotsCache.getLock(slotId);
        lock.lock();
        try {
            return slotsCache.remove(slotId);
        } finally {
            lock.unlock();
        }
    }
}
