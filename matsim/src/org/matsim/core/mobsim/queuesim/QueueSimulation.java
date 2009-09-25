/* *********************************************************************** *
 * project: org.matsim.*
 * QueueSimulation.java
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2007, 2009 by the members listed in the COPYING,  *
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

package org.matsim.core.mobsim.queuesim;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.PriorityQueue;
import java.util.concurrent.PriorityBlockingQueue;

import org.apache.log4j.Logger;
import org.matsim.api.basic.v01.Id;
import org.matsim.api.basic.v01.TransportMode;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.ScenarioImpl;
import org.matsim.api.core.v01.network.Link;
import org.matsim.core.api.experimental.events.Events;
import org.matsim.core.basic.v01.IdImpl;
import org.matsim.core.config.Config;
import org.matsim.core.controler.Controler;
import org.matsim.core.events.AgentArrivalEventImpl;
import org.matsim.core.events.AgentDepartureEventImpl;
import org.matsim.core.events.AgentStuckEventImpl;
import org.matsim.core.gbl.Gbl;
import org.matsim.core.gbl.MatsimRandom;
import org.matsim.core.mobsim.queuesim.listener.QueueSimListenerManager;
import org.matsim.core.mobsim.queuesim.listener.QueueSimulationListener;
import org.matsim.core.network.LinkImpl;
import org.matsim.core.network.NetworkChangeEvent;
import org.matsim.core.network.NetworkLayer;
import org.matsim.core.population.LegImpl;
import org.matsim.core.population.PersonImpl;
import org.matsim.core.population.PopulationImpl;
import org.matsim.core.population.routes.NetworkRouteWRefs;
import org.matsim.core.utils.collections.Tuple;
import org.matsim.core.utils.geometry.transformations.TransformationFactory;
import org.matsim.core.utils.misc.Time;
import org.matsim.lanes.basic.BasicLaneDefinitions;
import org.matsim.lanes.basic.BasicLanesToLinkAssignment;
import org.matsim.signalsystems.basic.BasicSignalSystems;
import org.matsim.signalsystems.config.BasicSignalSystemConfigurations;
import org.matsim.vehicles.BasicVehicleImpl;
import org.matsim.vehicles.BasicVehicleType;
import org.matsim.vehicles.BasicVehicleTypeImpl;
import org.matsim.vis.netvis.VisConfig;
import org.matsim.vis.netvis.streaming.SimStateWriterI;
import org.matsim.vis.otfvis.server.OTFQuadFileHandler;
import org.matsim.vis.snapshots.writers.KmlSnapshotWriter;
import org.matsim.vis.snapshots.writers.PlansFileSnapshotWriter;
import org.matsim.vis.snapshots.writers.PositionInfo;
import org.matsim.vis.snapshots.writers.SnapshotWriter;
import org.matsim.vis.snapshots.writers.TransimsSnapshotWriter;

/**
 * Implementation of a queue-based transport simulation.
 * Lanes and SignalSystems are not initialized unless the setter are invoked.
 *
 * @author dstrippgen
 * @author mrieser
 * @author dgrether
 */
public class QueueSimulation {

	private int snapshotPeriod = 0;
	private double snapshotTime = 0.0;

	protected static final int INFO_PERIOD = 3600;
	private double infoTime = 0;

	private final Config config;
	protected final PopulationImpl plans;
	protected QueueNetwork network;
	protected NetworkLayer networkLayer;

	private static Events events = null;
	protected  SimStateWriterI netStateWriter = null;

	private final List<SnapshotWriter> snapshotWriters = new ArrayList<SnapshotWriter>();

	private PriorityQueue<NetworkChangeEvent> networkChangeEventsQueue = null;

	protected QueueSimEngine simEngine = null;

	/**
	 * Includes all agents that have transportation modes unknown to
	 * the QueueSimulation (i.e. != "car") or have two activities on the same link
 	 */
	private final PriorityQueue<Tuple<Double, DriverAgent>> teleportationList = new PriorityQueue<Tuple<Double, DriverAgent>>(30, new TeleportationArrivalTimeComparator());

	private final Date starttime = new Date();

	private double stopTime = 100*3600;

	final private static Logger log = Logger.getLogger(QueueSimulation.class);

	private AgentFactory agentFactory;

	private QueueSimListenerManager listenerManager;

	protected final PriorityBlockingQueue<DriverAgent> activityEndsList = new PriorityBlockingQueue<DriverAgent>(500, new DriverAgentDepartureTimeComparator());

	protected Scenario scenario = null;

	private BasicLaneDefinitions laneDefintions;

	/** @see #setTeleportVehicles(boolean) */
	private boolean teleportVehicles = true;
	private int cntTeleportVehicle = 0;

	private boolean useActivityDurations = true;

	private QueueSimSignalEngine signalEngine = null;

	/**
	 * Initialize the QueueSimulation without signal systems
	 * @param network
	 * @param plans
	 * @param events
	 */
	public QueueSimulation(final NetworkLayer network, final PopulationImpl plans, final Events events) {
		// In my opinion, this should be marked as deprecated in favor of the constructor with Scenario. marcel/16july2009
		this.listenerManager = new QueueSimListenerManager(this);
		Simulation.reset();
		this.config = Gbl.getConfig();
		SimulationTimer.reset(this.config.simulation().getTimeStepSize());
		setEvents(events);
		this.plans = plans;

		this.network = new QueueNetwork(network);
		this.networkLayer = network;
		this.agentFactory = new AgentFactory(this);

		this.simEngine = new QueueSimEngine(this.network, MatsimRandom.getRandom());
	}

	/**
	 * Initialize the QueueSimulation without signal systems
	 * @param scenario
	 * @param events
	 */
	public QueueSimulation(final ScenarioImpl scenario, final Events events) {
		this(scenario.getNetwork(), scenario.getPopulation(), events);
		this.scenario = scenario;
	}

	/**
	 * Adds the QueueSimulationListener instance  given as parameters as
	 * listener to this QueueSimulation instance.
	 * @param listeners
	 */
	public void addQueueSimulationListeners(final QueueSimulationListener listener){
				this.listenerManager.addQueueSimulationListener(listener);
	}

	/**
	 * Set the lanes used in the simulation
	 * @param laneDefs
	 */
	public void setLaneDefinitions(final BasicLaneDefinitions laneDefs){
		this.laneDefintions = laneDefs;
	}

	/**
	 * Set the signal systems to be used in simulation
	 * @param signalSystems
	 * @param basicSignalSystemConfigurations
	 */
	public void setSignalSystems(final BasicSignalSystems signalSystems, final BasicSignalSystemConfigurations basicSignalSystemConfigurations){
		this.signalEngine  = new QueueSimSignalEngine(this);
		this.signalEngine.setSignalSystems(signalSystems, basicSignalSystemConfigurations);
	}

	public final void run() {
		prepareSim();
		this.listenerManager.fireQueueSimulationInitializedEvent();
		//do iterations
		boolean cont = true;
		while (cont) {
			double time = SimulationTimer.getTime();
			beforeSimStep(time);
			this.listenerManager.fireQueueSimulationBeforeSimStepEvent(time);
			cont = doSimStep(time);
			afterSimStep(time);
			this.listenerManager.fireQueueSimulationAfterSimStepEvent(time);
			if (cont) {
				SimulationTimer.incTime();
			}
		}
		this.listenerManager.fireQueueSimulationBeforeCleanupEvent();
		cleanupSim();
		//delete reference to clear memory
		this.listenerManager = null;
	}

	protected void createAgents() {
		if (this.plans == null) {
			throw new RuntimeException("No valid Population found (plans == null)");
		}
		BasicVehicleType defaultVehicleType = new BasicVehicleTypeImpl(new IdImpl("defaultVehicleType"));

		for (PersonImpl p : this.plans.getPersons().values()) {
			PersonAgent agent = this.agentFactory.createPersonAgent(p);

			QueueVehicle veh = new QueueVehicleImpl(new BasicVehicleImpl(agent.getPerson().getId(), defaultVehicleType));
			//not needed in new agent class
			veh.setDriver(agent); // this line is currently only needed for OTFVis to show parked vehicles
			agent.setVehicle(veh);

			if (agent.initialize()) {
				QueueLink qlink = this.network.getQueueLink(agent.getCurrentLink().getId());
				qlink.addParkedVehicle(veh);
			}
		}
	}

//	protected void prepareNetwork() {
//		this.network.beforeSim();
//	}

	public void openNetStateWriter(final String snapshotFilename, final String networkFilename, final int snapshotPeriod) {
		/* TODO [MR] I don't really like it that we change the configuration on the fly here.
		 * In my eyes, the configuration should usually be a read-only object in general, but
		 * that's hard to be implemented...
		 */
		this.config.network().setInputFile(networkFilename);
		this.config.simulation().setSnapshotFormat("netvis");
		this.config.simulation().setSnapshotPeriod(snapshotPeriod);
		this.config.simulation().setSnapshotFile(snapshotFilename);
	}

	private void createSnapshotwriter() {
		// A snapshot period of 0 or less indicates that there should be NO snapshot written
		if (this.snapshotPeriod > 0 ) {
			String snapshotFormat =  this.config.simulation().getSnapshotFormat();

			if (snapshotFormat.contains("plansfile")) {
				String snapshotFilePrefix = Controler.getIterationPath() + "/positionInfoPlansFile";
				String snapshotFileSuffix = "xml";
				this.snapshotWriters.add(new PlansFileSnapshotWriter(snapshotFilePrefix,snapshotFileSuffix));
			}
			if (snapshotFormat.contains("transims")) {
				String snapshotFile = Controler.getIterationFilename("T.veh");
				this.snapshotWriters.add(new TransimsSnapshotWriter(snapshotFile));
			}
			if (snapshotFormat.contains("googleearth")) {
				String snapshotFile = Controler.getIterationFilename("googleearth.kmz");
				String coordSystem = this.config.global().getCoordinateSystem();
				this.snapshotWriters.add(new KmlSnapshotWriter(snapshotFile,
						TransformationFactory.getCoordinateTransformation(coordSystem, TransformationFactory.WGS84)));
			}
			if (snapshotFormat.contains("netvis")) {
				String snapshotFile;

				if (Controler.getIteration() == -1 ) snapshotFile = this.config.simulation().getSnapshotFile();
				else snapshotFile = Controler.getIterationPath() + "/Snapshot";

				File networkFile = new File(this.config.network().getInputFile());
				VisConfig myvisconf = VisConfig.newDefaultConfig();
				String[] params = {VisConfig.LOGO, VisConfig.DELAY, VisConfig.LINK_WIDTH_FACTOR, VisConfig.SHOW_NODE_LABELS, VisConfig.SHOW_LINK_LABELS};
				for (String param : params) {
					String value = this.config.findParam("vis", param);
					if (value != null) {
						myvisconf.set(param, value);
					}
				}
				// OR do it like this: buffers = Integer.parseInt(Config.getSingleton().getParam("temporal", "buffersize"));
				// Automatic reasoning about buffersize, so that the file will be about 5MB big...
				int buffers = this.network.getLinks().size();
				String buffString = this.config.findParam("vis", "buffersize");
				if (buffString == null) {
					buffers = Math.max(5, Math.min(500000/buffers, 100));
				} else buffers = Integer.parseInt(buffString);

				this.netStateWriter = new QueueNetStateWriter(this.network, this.network.getNetworkLayer(), networkFile.getAbsolutePath(), myvisconf, snapshotFile, this.snapshotPeriod, buffers);
				this.netStateWriter.open();
			}
			if (snapshotFormat.contains("otfvis")) {
				String snapshotFile = Controler.getIterationFilename("otfvis.mvi");
				OTFQuadFileHandler.Writer writer = null;
				writer = new OTFQuadFileHandler.Writer(this.snapshotPeriod, this.network, snapshotFile);
//				if (this.config.scenario().isUseLanes() && ! this.config.scenario().isUseSignalSystems()) {
//					OTFConnectionManager connect = writer.getConnectionManager();
//					// data source to writer
//					connect.add(QueueLink.class, DgOtfLaneWriter.class);
//					// writer -> reader: from server to client
//					connect
//					.add(DgOtfLaneWriter.class, DgOtfLaneReader.class);
//					// reader to drawer (or provider to receiver)
//					connect.add(DgOtfLaneReader.class, DgLaneSignalDrawer.class);
//					// drawer -> layer
//					connect.add(DgLaneSignalDrawer.class, DgOtfLaneLayer.class);
//
//				}
				this.snapshotWriters.add(writer);
			}
		} else this.snapshotPeriod = Integer.MAX_VALUE; // make sure snapshot is never called
	}

	private void prepareNetworkChangeEventsQueue() {
		Collection<NetworkChangeEvent> changeEvents = (this.networkLayer).getNetworkChangeEvents();
		if ((changeEvents != null) && (changeEvents.size() > 0)) {
			this.networkChangeEventsQueue = new PriorityQueue<NetworkChangeEvent>(changeEvents.size(), new NetworkChangeEvent.StartTimeComparator());
			this.networkChangeEventsQueue.addAll(changeEvents);
		}
	}

	/**
	 * Prepare the simulation and get all the settings from the configuration.
	 */
	protected void prepareSim() {
		if (events == null) {
			throw new RuntimeException("No valid Events Object (events == null)");
		}

		prepareLanes();

		if (this.signalEngine != null) {
			this.signalEngine.prepareSignalSystems();
		}

		// Initialize Snapshot file
		this.snapshotPeriod = (int) this.config.simulation().getSnapshotPeriod();

		double startTime = this.config.simulation().getStartTime();
		this.stopTime = this.config.simulation().getEndTime();

		if (startTime == Time.UNDEFINED_TIME) startTime = 0.0;
		if ((this.stopTime == Time.UNDEFINED_TIME) || (this.stopTime == 0)) this.stopTime = Double.MAX_VALUE;

		SimulationTimer.setSimStartTime(24*3600);
		SimulationTimer.setTime(startTime);

		createAgents();

		// set sim start time to config-value ONLY if this is LATER than the first plans starttime
		double simStartTime = 0;
		DriverAgent firstAgent = this.activityEndsList.peek();
		if (firstAgent != null) {
			simStartTime = Math.floor(Math.max(startTime, firstAgent.getDepartureTime()));
		}
		this.infoTime = Math.floor(simStartTime / INFO_PERIOD) * INFO_PERIOD; // infoTime may be < simStartTime, this ensures to print out the info at the very first timestep already
		this.snapshotTime = Math.floor(simStartTime / this.snapshotPeriod) * this.snapshotPeriod;
		if (this.snapshotTime < simStartTime) {
			this.snapshotTime += this.snapshotPeriod;
		}
		SimulationTimer.setSimStartTime(simStartTime);
		SimulationTimer.setTime(SimulationTimer.getSimStartTime());

		createSnapshotwriter();

		prepareNetworkChangeEventsQueue();
	}

	private void prepareLanes(){
		if (this.laneDefintions != null){
			for (BasicLanesToLinkAssignment laneToLink : this.laneDefintions.getLanesToLinkAssignmentsList()){
				QueueLink link = this.network.getQueueLink(laneToLink.getLinkId());
				if (link == null) {
					String message = "No Link with Id: " + laneToLink.getLinkId() + ". Cannot create lanes, check lanesToLinkAssignment of signalsystems definition!";
					log.error(message);
					throw new IllegalStateException(message);
				}
				link.createLanes(laneToLink.getLanes());
			}
		}
	}

	/**
	 * Close any files, etc.
	 */
	protected void cleanupSim() {
		this.simEngine.afterSim();
		double now = SimulationTimer.getTime();
		for (Tuple<Double, DriverAgent> entry : this.teleportationList) {
			DriverAgent agent = entry.getSecond();
			events.processEvent(new AgentStuckEventImpl(now, agent.getPerson(), (LinkImpl)agent.getDestinationLink(), agent.getCurrentLeg()));
		}
		this.teleportationList.clear();

		for (DriverAgent agent : this.activityEndsList) {
			events.processEvent(new AgentStuckEventImpl(now, agent.getPerson(), (LinkImpl)agent.getDestinationLink(), agent.getCurrentLeg()));
		}
		this.activityEndsList.clear();

		for (SnapshotWriter writer : this.snapshotWriters) {
			writer.finish();
		}

		if (this.netStateWriter != null) {
			try {
				this.netStateWriter.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
			this.netStateWriter = null;
		}
		this.simEngine = null;
		QueueSimulation.events = null; // delete events object to free events handlers, if they are nowhere else referenced
	}

	protected void beforeSimStep(final double time) {
		if ((this.networkChangeEventsQueue != null) && (this.networkChangeEventsQueue.size() > 0)) {
			handleNetworkChangeEvents(time);
		}
	}

	/**
	 * Do one step of the simulation run.
	 *
	 * @param time the current time in seconds after midnight
	 * @return true if the simulation needs to continue
	 */
	protected boolean doSimStep(final double time) {
		this.moveVehiclesWithUnknownLegMode(time);
		this.handleActivityEnds(time);
		this.simEngine.simStep(time);

		if (time >= this.infoTime) {
			this.infoTime += INFO_PERIOD;
			Date endtime = new Date();
			long diffreal = (endtime.getTime() - this.starttime.getTime())/1000;
			double diffsim  = time - SimulationTimer.getSimStartTime();
			int nofActiveLinks = this.simEngine.getNumberOfSimulatedLinks();
			log.info("SIMULATION AT " + Time.writeTime(time) + ": #Veh=" + Simulation.getLiving() + " lost=" + Simulation.getLost() + " #links=" + nofActiveLinks
					+ " simT=" + diffsim + "s realT=" + (diffreal) + "s; (s/r): " + (diffsim/(diffreal + Double.MIN_VALUE)));
			Gbl.printMemoryUsage();
		}

		return (Simulation.isLiving() && (this.stopTime > time));
	}

	protected void afterSimStep(final double time) {
		if (time >= this.snapshotTime) {
			this.snapshotTime += this.snapshotPeriod;
			doSnapshot(time);
		}
	}

	private void doSnapshot(final double time) {
		if (!this.snapshotWriters.isEmpty()) {
			Collection<PositionInfo> positions = this.network.getVehiclePositions();
			for (SnapshotWriter writer : this.snapshotWriters) {
				writer.beginSnapshot(time);
				for (PositionInfo position : positions) {
					writer.addAgent(position);
				}
				writer.endSnapshot();
			}
		}

		if (this.netStateWriter != null) {
			try {
				this.netStateWriter.dump((int)time);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	public static final Events getEvents() {
		return events;
	}

	private static final void setEvents(final Events events) {
		QueueSimulation.events = events;
	}

	private final void handleUnknownLegMode(final DriverAgent agent) {
		double arrivalTime = SimulationTimer.getTime() + agent.getCurrentLeg().getTravelTime();
		this.teleportationList.add(new Tuple<Double, DriverAgent>(arrivalTime, agent));
	}

	private final void moveVehiclesWithUnknownLegMode(final double now) {
		while (this.teleportationList.peek() != null ) {
			Tuple<Double, DriverAgent> entry = this.teleportationList.peek();
			if (entry.getFirst().doubleValue() <= now) {
				this.teleportationList.poll();
				DriverAgent driver = entry.getSecond();
				Link destinationLink = driver.getDestinationLink();
				driver.teleportToLink(destinationLink);

				getEvents().processEvent(new AgentArrivalEventImpl(now, driver.getPerson(),
						destinationLink, driver.getCurrentLeg()));
				driver.legEnds(now);
			} else break;
		}
	}

	private void handleNetworkChangeEvents(final double time) {
		while ((this.networkChangeEventsQueue.size() > 0) && (this.networkChangeEventsQueue.peek().getStartTime() <= time)){
			NetworkChangeEvent event = this.networkChangeEventsQueue.poll();
			for (LinkImpl link : event.getLinks()) {
				this.network.getQueueLink(link.getId()).recalcTimeVariantAttributes(time);
			}
		}
	}

	/**
	 * Registers this agent as performing an activity and makes sure that the
	 * agent will be informed once his departure time has come.
	 *
	 * @param agent
	 *
	 * @see DriverAgent#getDepartureTime()
	 */
	protected void scheduleActivityEnd(final DriverAgent agent) {
		this.activityEndsList.add(agent);
	}

	private void handleActivityEnds(final double time) {
		while (this.activityEndsList.peek() != null) {
			DriverAgent agent = this.activityEndsList.peek();
			if (agent.getDepartureTime() <= time) {
				this.activityEndsList.poll();
				agent.activityEnds(time);
			} else {
				return;
			}
		}
	}

	/**
	 * Informs the simulation that the specified agent wants to depart from its current activity.
	 * The simulation can then put the agent onto its vehicle on a link or teleport it to its destination.
	 *
	 * @param agent
	 * @param link the link where the agent departs
	 */
	protected void agentDeparts(final DriverAgent agent, final Link link) {
		double now = SimulationTimer.getTime();

		LegImpl leg = agent.getCurrentLeg();

		events.processEvent(new AgentDepartureEventImpl(now, agent.getPerson(), (LinkImpl) link, leg));

		if (leg.getMode().equals(TransportMode.car)) {
			NetworkRouteWRefs route = (NetworkRouteWRefs) leg.getRoute();
			Id vehicleId = route.getVehicleId();
			if (vehicleId == null) {
				vehicleId = agent.getPerson().getId(); // backwards-compatibility
			}
			QueueLink qlink = this.network.getQueueLink(link.getId());
			QueueVehicle vehicle = qlink.removeParkedVehicle(vehicleId);
			if (vehicle == null) {
				if (this.teleportVehicles) {
					if (agent instanceof PersonAgent) {
						vehicle = ((PersonAgent) agent).getVehicle();
						if (vehicle.getCurrentLink() != null) {
							if (this.cntTeleportVehicle < 9) {
								this.cntTeleportVehicle++;
								log.info("teleport vehicle " + vehicle.getId() + " from link " + vehicle.getCurrentLink().getId() + " to link " + link.getId());
								if (this.cntTeleportVehicle == 9) {
									log.info("No more occurrences of teleported vehicles will be reported.");
								}
							}
							QueueLink qlinkOld = this.network.getQueueLink(vehicle.getCurrentLink().getId());
							qlinkOld.removeParkedVehicle(vehicle.getId());
						}
					}
				} else {
					throw new RuntimeException("car not available for agent " + agent.getPerson().getId() + " on link " + link.getId());
				}
			}
			if (vehicle != null) {
				vehicle.setDriver(agent);
			}
			if ((route.getEndLink() == link) && (agent.chooseNextLink() == null)) {
				qlink.processVehicleArrival(now, vehicle);
			} else {
				qlink.addDepartingVehicle(vehicle);
			}
		} else {
			// unknown leg mode
			this.handleUnknownLegMode(agent);
		}
	}

	public boolean addSnapshotWriter(final SnapshotWriter writer) {
		return this.snapshotWriters.add(writer);
	}

	public boolean removeSnapshotWriter(final SnapshotWriter writer) {
		return this.snapshotWriters.remove(writer);
	}

	public void setAgentFactory(final AgentFactory fac) {
		this.agentFactory = fac;
	}


	/** Specifies whether the simulation should track vehicle usage and throw an Exception
	 * if an agent tries to use a car on a link where the car is not available, or not.
	 * Set <code>teleportVehicles</code> to <code>true</code> if agents always have a
	 * vehicle available. If the requested vehicle is parked somewhere else, the vehicle
	 * will be teleported to wherever it is requested to for usage. Set to <code>false</code>
	 * will generate an Exception in the case when an tries to depart with a car on link
	 * where the car is not parked.
	 *
	 * @param teleportVehicles
	 */
	public void setTeleportVehicles(final boolean teleportVehicles) {
		this.teleportVehicles = teleportVehicles;
	}

	private static class TeleportationArrivalTimeComparator implements Comparator<Tuple<Double, DriverAgent>>, Serializable {
		private static final long serialVersionUID = 1L;
		public int compare(final Tuple<Double, DriverAgent> o1, final Tuple<Double, DriverAgent> o2) {
			int ret = o1.getFirst().compareTo(o2.getFirst()); // first compare time information
			if (ret == 0) {
				ret = o2.getSecond().getPerson().getId().compareTo(o1.getSecond().getPerson().getId()); // if they're equal, compare the Ids: the one with the larger Id should be first
			}
			return ret;
		}
	}

	public QueueNetwork getQueueNetwork() {
		return this.network;
	}

	public Scenario getScenario() {
		return this.scenario;
	}


	public boolean isUseActivityDurations() {
		return this.useActivityDurations;
	}

	public void setUseActivityDurations(final boolean useActivityDurations) {
		this.useActivityDurations = useActivityDurations;
		log.info("QueueSimulation is working with activity durations: " + this.isUseActivityDurations());
	}

	public SignalEngine getQueueSimSignalEngine() {
		return this.signalEngine;
	}

}
