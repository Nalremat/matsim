/* *********************************************************************** *
 * project: org.matsim.*
 * Event.java
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

import java.util.LinkedHashMap;
import java.util.Map;

public abstract class Event {

	public final static String ATTRIBUTE_TIME = "time";
	public final static String ATTRIBUTE_TYPE = "type";

	private final double time;

	public Event(final double time) {
		this.time = time;
	}

	public Map<String, String> getAttributes() {
		Map<String, String> attr = new LinkedHashMap<String, String>();
		attr.put(ATTRIBUTE_TIME, Double.toString(this.time));
		attr.put(ATTRIBUTE_TYPE, getEventType());
		return attr;
	}

	/** @return a unique, descriptive name for this event type, used to identify event types in files. */
	abstract public String getEventType();

	public double getTime() {
		return this.time;
	}
	
	public String toString() {
		Map<String,String> attr = this.getAttributes() ;
		StringBuilder eventXML = new StringBuilder("\t<event ");
		for (Map.Entry<String, String> entry : attr.entrySet()) {
			eventXML.append(entry.getKey());
			eventXML.append("=\"");
			eventXML.append(entry.getValue());
			eventXML.append("\" ");
		}
		eventXML.append(" />");
		return eventXML.toString();
	}
	
}

