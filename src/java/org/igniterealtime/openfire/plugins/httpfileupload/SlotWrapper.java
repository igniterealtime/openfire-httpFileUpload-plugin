/*
 * Copyright (c) 2025 Ignite Realtime Foundation. All rights reserved.
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

import nl.goodbytes.xmpp.xep0363.SecureUUID;
import nl.goodbytes.xmpp.xep0363.SecureUniqueId;
import nl.goodbytes.xmpp.xep0363.Slot;
import org.xmpp.packet.JID;

import javax.annotation.Nonnull;
import javax.xml.bind.annotation.XmlRootElement;
import java.io.Serializable;
import java.lang.reflect.Field;
import java.util.Date;

/**
 * A JAXB wrapper for {@link nl.goodbytes.xmpp.xep0363.Slot}
 *
 * @author Guus der Kinderen, guus@goodbytes.nl
 */
@XmlRootElement
public class SlotWrapper implements Serializable
{
    private String uuid;
    private Long creationDate;
    private String filename;
    private String creator;
    private Long size;

    public static SlotWrapper wrap(@Nonnull final Slot slot)
    {
        return new SlotWrapper(
            slot.getUuid() == null ? null : slot.getUuid().toString(),
            slot.getCreationDate() == null ? null : slot.getCreationDate().getTime(),
            slot.getFilename(),
            slot.getCreator() == null ? null : slot.getCreator().toString(),
            slot.getSize()
        );
    }

    public static Slot unwrap(@Nonnull final SlotWrapper slotWrapper)
    {
        final SecureUniqueId uuid = slotWrapper.getUuid() == null ? null : SecureUUID.fromString(slotWrapper.getUuid());
        final Date creationDate = slotWrapper.getCreationDate() == null ? null : new Date(slotWrapper.getCreationDate());
        final String filename = slotWrapper.getFilename();
        final JID creator = slotWrapper.getCreator() == null ? null : new JID(slotWrapper.getCreator());
        final long size = slotWrapper.getSize();

        final Slot result = new Slot(creator, filename, size);

        try {
            final Field uuidField = result.getClass().getDeclaredField("uuid");
            try {
                uuidField.setAccessible(true);
                uuidField.set(result, uuid);
            } finally {
                uuidField.setAccessible(false);
            }

            final Field creationDateField = result.getClass().getDeclaredField("creationDate");
            try {
                creationDateField.setAccessible(true);
                creationDateField.set(result, creationDate);
            } finally {
                creationDateField.setAccessible(false);
            }
        } catch (IllegalAccessException | NoSuchFieldException e) {
            throw new IllegalStateException("Unable to unwrap Slot. This likely is a bug in Openfire. Please report this problem!", e);
        }

        return result;
    }

    public SlotWrapper()
    {
    }

    public SlotWrapper(String uuid, Long creationDate, String filename, String creator, Long size)
    {
        this.uuid = uuid;
        this.creationDate = creationDate;
        this.filename = filename;
        this.creator = creator;
        this.size = size;
    }

    public String getUuid()
    {
        return uuid;
    }

    public void setUuid(String uuid)
    {
        this.uuid = uuid;
    }

    public Long getCreationDate()
    {
        return creationDate;
    }

    public void setCreationDate(Long creationDate)
    {
        this.creationDate = creationDate;
    }

    public String getFilename()
    {
        return filename;
    }

    public void setFilename(String filename)
    {
        this.filename = filename;
    }

    public String getCreator()
    {
        return creator;
    }

    public void setCreator(String creator)
    {
        this.creator = creator;
    }

    public Long getSize()
    {
        return size;
    }

    public void setSize(Long size)
    {
        this.size = size;
    }
}
