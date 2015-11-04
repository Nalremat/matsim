/* *********************************************************************** *
 * project: org.matsim.*
 * AgentStuckEvent.java
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2007, 2008 by the members listed in the COPYING,  *
 *                   LICENSE and WARRANTY file.                            *
 * email           : info at matsim dot org                                *
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 *   This program is free software; you can redistribute it and/or modify  *
 *   it under the terms of the GNU General Public License as published by  *
 *   the Free Software Foundation; either version 2 of the License, or     *
 *   (at your option) any later version.                                   *
 *   See also COPYING, LICENSE and WARRANTY file                           *
 *                                                                         *
 * *********************************************************************** */

package org.matsim.core.api.experimental.events;

import java.util.Map;

import org.matsim.api.core.v01.Id;
import org.matsim.core.api.internal.HasPersonId;

public class AgentStuckEvent extends Event implements HasPersonId {

	public static final String EVENT_TYPE = "stuckAndAbort";
	public static final String ATTRIBUTE_LINK = "link";
	public static final String ATTRIBUTE_LEGMODE = "legMode";
	public static final String ATTRIBUTE_PERSON = "person";

	private final Id personId;
	private final Id linkId;
	private final String legMode;

	public AgentStuckEvent(final double time, final Id agentId, final Id linkId, final String legMode) {
		super(time);
		this.personId = agentId;
		this.linkId = linkId;
		this.legMode = legMode;
	}

	@Override
	public Map<String, String> getAttributes() {
		Map<String, String> attr = super.getAttributes();
		attr.put(ATTRIBUTE_LINK, (this.linkId == null ? null : this.linkId.toString()));
		if (this.legMode != null) {
			attr.put(ATTRIBUTE_LEGMODE, this.legMode);
		}
		attr.put(ATTRIBUTE_PERSON, this.personId.toString());
		return attr;
	}

	public String getLegMode() {
		return this.legMode;
	}

	public Id getLinkId() {
		return this.linkId;
	}

	@Override
	public String getEventType() {
		return EVENT_TYPE;
	}

	public Id getPersonId() {
		return this.personId;
	}

}