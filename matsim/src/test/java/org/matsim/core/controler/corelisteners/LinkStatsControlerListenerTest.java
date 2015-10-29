/* **********************************************4************************ *
 * project: org.matsim.*
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2011 by the members listed in the COPYING,        *
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

package org.matsim.core.controler.corelisteners;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Node;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.api.experimental.events.LinkLeaveEvent;
import org.matsim.core.basic.v01.IdImpl;
import org.matsim.core.config.Config;
import org.matsim.core.config.groups.LinkStatsConfigGroup;
import org.matsim.core.controler.Controler;
import org.matsim.core.mobsim.framework.Mobsim;
import org.matsim.core.mobsim.framework.MobsimFactory;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.io.IOUtils;
import org.matsim.testcases.MatsimTestUtils;

/**
 * @author mrieser
 */
public class LinkStatsControlerListenerTest {

	@Rule public MatsimTestUtils util = new MatsimTestUtils();
	
	@Test
	public void testUseVolumesOfIteration() {
		LinkStatsConfigGroup config = new LinkStatsConfigGroup();
		LinkStatsControlerListener lscl = new LinkStatsControlerListener(config);
		
		// test defaults
		Assert.assertEquals(10, config.getWriteLinkStatsInterval());
		Assert.assertEquals(5, config.getAverageLinkStatsOverIterations());
		
		// now the real tests
		Assert.assertFalse(lscl.useVolumesOfIteration(0, 0));
		Assert.assertFalse(lscl.useVolumesOfIteration(1, 0));
		Assert.assertFalse(lscl.useVolumesOfIteration(2, 0));
		Assert.assertFalse(lscl.useVolumesOfIteration(3, 0));
		Assert.assertFalse(lscl.useVolumesOfIteration(4, 0));
		Assert.assertFalse(lscl.useVolumesOfIteration(5, 0));
		Assert.assertTrue(lscl.useVolumesOfIteration(6, 0));
		Assert.assertTrue(lscl.useVolumesOfIteration(7, 0));
		Assert.assertTrue(lscl.useVolumesOfIteration(8, 0));
		Assert.assertTrue(lscl.useVolumesOfIteration(9, 0));
		Assert.assertTrue(lscl.useVolumesOfIteration(10, 0));
		Assert.assertFalse(lscl.useVolumesOfIteration(11, 0));
		Assert.assertFalse(lscl.useVolumesOfIteration(12, 0));
		Assert.assertFalse(lscl.useVolumesOfIteration(13, 0));
		Assert.assertFalse(lscl.useVolumesOfIteration(14, 0));
		Assert.assertFalse(lscl.useVolumesOfIteration(15, 0));
		Assert.assertTrue(lscl.useVolumesOfIteration(16, 0));
		Assert.assertTrue(lscl.useVolumesOfIteration(17, 0));
		Assert.assertTrue(lscl.useVolumesOfIteration(18, 0));
		Assert.assertTrue(lscl.useVolumesOfIteration(19, 0));
		Assert.assertTrue(lscl.useVolumesOfIteration(20, 0));
		Assert.assertFalse(lscl.useVolumesOfIteration(21, 0));
		
		// change some values
		config.setWriteLinkStatsInterval(8);
		config.setAverageLinkStatsOverIterations(2);
		Assert.assertFalse(lscl.useVolumesOfIteration(0, 0));
		Assert.assertFalse(lscl.useVolumesOfIteration(1, 0));
		Assert.assertFalse(lscl.useVolumesOfIteration(2, 0));
		Assert.assertFalse(lscl.useVolumesOfIteration(3, 0));
		Assert.assertFalse(lscl.useVolumesOfIteration(4, 0));
		Assert.assertFalse(lscl.useVolumesOfIteration(5, 0));
		Assert.assertFalse(lscl.useVolumesOfIteration(6, 0));
		Assert.assertTrue(lscl.useVolumesOfIteration(7, 0));
		Assert.assertTrue(lscl.useVolumesOfIteration(8, 0));
		Assert.assertFalse(lscl.useVolumesOfIteration(9, 0));
		Assert.assertFalse(lscl.useVolumesOfIteration(10, 0));
		Assert.assertFalse(lscl.useVolumesOfIteration(11, 0));
		Assert.assertFalse(lscl.useVolumesOfIteration(12, 0));
		Assert.assertFalse(lscl.useVolumesOfIteration(13, 0));
		Assert.assertFalse(lscl.useVolumesOfIteration(14, 0));
		Assert.assertTrue(lscl.useVolumesOfIteration(15, 0));
		Assert.assertTrue(lscl.useVolumesOfIteration(16, 0));
		Assert.assertFalse(lscl.useVolumesOfIteration(17, 0));
		Assert.assertFalse(lscl.useVolumesOfIteration(18, 0));
		Assert.assertFalse(lscl.useVolumesOfIteration(19, 0));
		Assert.assertFalse(lscl.useVolumesOfIteration(20, 0));
		Assert.assertFalse(lscl.useVolumesOfIteration(21, 0));
		
		// change some values: averaging = 1
		config.setWriteLinkStatsInterval(5);
		config.setAverageLinkStatsOverIterations(1);
		Assert.assertTrue(lscl.useVolumesOfIteration(0, 0));
		Assert.assertFalse(lscl.useVolumesOfIteration(1, 0));
		Assert.assertFalse(lscl.useVolumesOfIteration(2, 0));
		Assert.assertFalse(lscl.useVolumesOfIteration(3, 0));
		Assert.assertFalse(lscl.useVolumesOfIteration(4, 0));
		Assert.assertTrue(lscl.useVolumesOfIteration(5, 0));
		Assert.assertFalse(lscl.useVolumesOfIteration(6, 0));
		Assert.assertFalse(lscl.useVolumesOfIteration(7, 0));
		Assert.assertFalse(lscl.useVolumesOfIteration(8, 0));
		Assert.assertFalse(lscl.useVolumesOfIteration(9, 0));
		Assert.assertTrue(lscl.useVolumesOfIteration(10, 0));
		Assert.assertFalse(lscl.useVolumesOfIteration(11, 0));
		Assert.assertFalse(lscl.useVolumesOfIteration(12, 0));
		Assert.assertFalse(lscl.useVolumesOfIteration(13, 0));
		Assert.assertFalse(lscl.useVolumesOfIteration(14, 0));
		Assert.assertTrue(lscl.useVolumesOfIteration(15, 0));
		Assert.assertFalse(lscl.useVolumesOfIteration(16, 0));
		Assert.assertFalse(lscl.useVolumesOfIteration(17, 0));
		Assert.assertFalse(lscl.useVolumesOfIteration(18, 0));
		Assert.assertFalse(lscl.useVolumesOfIteration(19, 0));
		Assert.assertTrue(lscl.useVolumesOfIteration(20, 0));
		Assert.assertFalse(lscl.useVolumesOfIteration(21, 0));

		// change some values: averaging = 0
		config.setWriteLinkStatsInterval(5);
		config.setAverageLinkStatsOverIterations(0);
		Assert.assertTrue(lscl.useVolumesOfIteration(0, 0));
		Assert.assertFalse(lscl.useVolumesOfIteration(1, 0));
		Assert.assertFalse(lscl.useVolumesOfIteration(2, 0));
		Assert.assertFalse(lscl.useVolumesOfIteration(3, 0));
		Assert.assertFalse(lscl.useVolumesOfIteration(4, 0));
		Assert.assertTrue(lscl.useVolumesOfIteration(5, 0));
		Assert.assertFalse(lscl.useVolumesOfIteration(6, 0));
		Assert.assertFalse(lscl.useVolumesOfIteration(7, 0));
		Assert.assertFalse(lscl.useVolumesOfIteration(8, 0));
		Assert.assertFalse(lscl.useVolumesOfIteration(9, 0));
		Assert.assertTrue(lscl.useVolumesOfIteration(10, 0));
		Assert.assertFalse(lscl.useVolumesOfIteration(11, 0));
		Assert.assertFalse(lscl.useVolumesOfIteration(12, 0));
		Assert.assertFalse(lscl.useVolumesOfIteration(13, 0));
		Assert.assertFalse(lscl.useVolumesOfIteration(14, 0));
		Assert.assertTrue(lscl.useVolumesOfIteration(15, 0));
		Assert.assertFalse(lscl.useVolumesOfIteration(16, 0));
		Assert.assertFalse(lscl.useVolumesOfIteration(17, 0));
		Assert.assertFalse(lscl.useVolumesOfIteration(18, 0));
		Assert.assertFalse(lscl.useVolumesOfIteration(19, 0));
		Assert.assertTrue(lscl.useVolumesOfIteration(20, 0));
		Assert.assertFalse(lscl.useVolumesOfIteration(21, 0));

		// change some values: interval = 0
		config.setWriteLinkStatsInterval(0);
		config.setAverageLinkStatsOverIterations(2);
		Assert.assertFalse(lscl.useVolumesOfIteration(0, 0));
		Assert.assertFalse(lscl.useVolumesOfIteration(1, 0));
		Assert.assertFalse(lscl.useVolumesOfIteration(2, 0));
		Assert.assertFalse(lscl.useVolumesOfIteration(3, 0));
		Assert.assertFalse(lscl.useVolumesOfIteration(4, 0));
		Assert.assertFalse(lscl.useVolumesOfIteration(5, 0));
		Assert.assertFalse(lscl.useVolumesOfIteration(6, 0));
		Assert.assertFalse(lscl.useVolumesOfIteration(7, 0));
		Assert.assertFalse(lscl.useVolumesOfIteration(8, 0));
		Assert.assertFalse(lscl.useVolumesOfIteration(9, 0));
		Assert.assertFalse(lscl.useVolumesOfIteration(10, 0));
		Assert.assertFalse(lscl.useVolumesOfIteration(11, 0));
		Assert.assertFalse(lscl.useVolumesOfIteration(12, 0));
		Assert.assertFalse(lscl.useVolumesOfIteration(13, 0));
		Assert.assertFalse(lscl.useVolumesOfIteration(14, 0));
		Assert.assertFalse(lscl.useVolumesOfIteration(15, 0));
		Assert.assertFalse(lscl.useVolumesOfIteration(16, 0));
		Assert.assertFalse(lscl.useVolumesOfIteration(17, 0));
		Assert.assertFalse(lscl.useVolumesOfIteration(18, 0));
		Assert.assertFalse(lscl.useVolumesOfIteration(19, 0));
		Assert.assertFalse(lscl.useVolumesOfIteration(20, 0));
		Assert.assertFalse(lscl.useVolumesOfIteration(21, 0));
		
		// change some values: interval equal averaging
		config.setWriteLinkStatsInterval(5);
		config.setAverageLinkStatsOverIterations(5);
		Assert.assertFalse(lscl.useVolumesOfIteration(0, 0));
		Assert.assertTrue(lscl.useVolumesOfIteration(1, 0));
		Assert.assertTrue(lscl.useVolumesOfIteration(2, 0));
		Assert.assertTrue(lscl.useVolumesOfIteration(3, 0));
		Assert.assertTrue(lscl.useVolumesOfIteration(4, 0));
		Assert.assertTrue(lscl.useVolumesOfIteration(5, 0));
		Assert.assertTrue(lscl.useVolumesOfIteration(6, 0));
		Assert.assertTrue(lscl.useVolumesOfIteration(7, 0));
		Assert.assertTrue(lscl.useVolumesOfIteration(8, 0));
		Assert.assertTrue(lscl.useVolumesOfIteration(9, 0));
		Assert.assertTrue(lscl.useVolumesOfIteration(10, 0));
		Assert.assertTrue(lscl.useVolumesOfIteration(11, 0));
		Assert.assertTrue(lscl.useVolumesOfIteration(12, 0));
		Assert.assertTrue(lscl.useVolumesOfIteration(13, 0));
		Assert.assertTrue(lscl.useVolumesOfIteration(14, 0));
		Assert.assertTrue(lscl.useVolumesOfIteration(15, 0));
		Assert.assertTrue(lscl.useVolumesOfIteration(16, 0));
		Assert.assertTrue(lscl.useVolumesOfIteration(17, 0));
		Assert.assertTrue(lscl.useVolumesOfIteration(18, 0));
		Assert.assertTrue(lscl.useVolumesOfIteration(19, 0));
		Assert.assertTrue(lscl.useVolumesOfIteration(20, 0));
		Assert.assertTrue(lscl.useVolumesOfIteration(21, 0));

		// change some values: averaging > interval
		config.setWriteLinkStatsInterval(5);
		config.setAverageLinkStatsOverIterations(6);
		Assert.assertFalse(lscl.useVolumesOfIteration(0, 0));
		Assert.assertTrue(lscl.useVolumesOfIteration(1, 0));
		Assert.assertTrue(lscl.useVolumesOfIteration(2, 0));
		Assert.assertTrue(lscl.useVolumesOfIteration(3, 0));
		Assert.assertTrue(lscl.useVolumesOfIteration(4, 0));
		Assert.assertTrue(lscl.useVolumesOfIteration(5, 0));
		Assert.assertTrue(lscl.useVolumesOfIteration(6, 0));
		Assert.assertTrue(lscl.useVolumesOfIteration(7, 0));
		Assert.assertTrue(lscl.useVolumesOfIteration(8, 0));
		Assert.assertTrue(lscl.useVolumesOfIteration(9, 0));
		Assert.assertTrue(lscl.useVolumesOfIteration(10, 0));
		Assert.assertTrue(lscl.useVolumesOfIteration(11, 0));
		Assert.assertTrue(lscl.useVolumesOfIteration(12, 0));
		Assert.assertTrue(lscl.useVolumesOfIteration(13, 0));
		Assert.assertTrue(lscl.useVolumesOfIteration(14, 0));
		Assert.assertTrue(lscl.useVolumesOfIteration(15, 0));
		Assert.assertTrue(lscl.useVolumesOfIteration(16, 0));
		Assert.assertTrue(lscl.useVolumesOfIteration(17, 0));
		Assert.assertTrue(lscl.useVolumesOfIteration(18, 0));
		Assert.assertTrue(lscl.useVolumesOfIteration(19, 0));
		Assert.assertTrue(lscl.useVolumesOfIteration(20, 0));
		Assert.assertTrue(lscl.useVolumesOfIteration(21, 0));
		
		// change some values: different firstIteration
		config.setWriteLinkStatsInterval(5);
		config.setAverageLinkStatsOverIterations(3);
		Assert.assertFalse(lscl.useVolumesOfIteration(4, 4));
		Assert.assertFalse(lscl.useVolumesOfIteration(5, 4));
		Assert.assertFalse(lscl.useVolumesOfIteration(6, 4));
		Assert.assertFalse(lscl.useVolumesOfIteration(7, 4));
		Assert.assertTrue(lscl.useVolumesOfIteration(8, 4));
		Assert.assertTrue(lscl.useVolumesOfIteration(9, 4));
		Assert.assertTrue(lscl.useVolumesOfIteration(10, 4));
		Assert.assertFalse(lscl.useVolumesOfIteration(11, 4));
		Assert.assertFalse(lscl.useVolumesOfIteration(12, 4));
		Assert.assertTrue(lscl.useVolumesOfIteration(13, 4));
		Assert.assertTrue(lscl.useVolumesOfIteration(14, 4));
		Assert.assertTrue(lscl.useVolumesOfIteration(15, 4));
		Assert.assertFalse(lscl.useVolumesOfIteration(16, 4));
		Assert.assertFalse(lscl.useVolumesOfIteration(17, 4));
		Assert.assertTrue(lscl.useVolumesOfIteration(18, 4));
		Assert.assertTrue(lscl.useVolumesOfIteration(19, 4));
		Assert.assertTrue(lscl.useVolumesOfIteration(20, 4));
		Assert.assertFalse(lscl.useVolumesOfIteration(21, 4));
	}
	
	@Test
	public void test_writeLinkStatsInterval() {
		Config config = this.util.loadConfig(null);
		LinkStatsConfigGroup lsConfig = config.linkStats();
		
		lsConfig.setWriteLinkStatsInterval(3);
		lsConfig.setAverageLinkStatsOverIterations(1);
		
		Controler controler = new Controler(ScenarioUtils.createScenario(config));
		controler.addMobsimFactory("dummy", new DummyMobsimFactory());
		config.controler().setMobsim("dummy");
		config.controler().setFirstIteration(0);
		config.controler().setLastIteration(7);
		
		controler.setCreateGraphs(false);
		controler.setDumpDataAtEnd(false);
		controler.getConfig().controler().setWriteEventsInterval(0);
		config.controler().setWritePlansInterval(0);
		controler.run();
		
		Assert.assertTrue(new File(config.controler().getOutputDirectory() + "ITERS/it.0/0.linkstats.txt.gz").exists());
		Assert.assertFalse(new File(config.controler().getOutputDirectory() + "ITERS/it.1/1.linkstats.txt.gz").exists());
		Assert.assertFalse(new File(config.controler().getOutputDirectory() + "ITERS/it.2/2.linkstats.txt.gz").exists());
		Assert.assertTrue(new File(config.controler().getOutputDirectory() + "ITERS/it.3/3.linkstats.txt.gz").exists());
		Assert.assertFalse(new File(config.controler().getOutputDirectory() + "ITERS/it.4/4.linkstats.txt.gz").exists());
		Assert.assertFalse(new File(config.controler().getOutputDirectory() + "ITERS/it.5/5.linkstats.txt.gz").exists());
		Assert.assertTrue(new File(config.controler().getOutputDirectory() + "ITERS/it.6/6.linkstats.txt.gz").exists());
		Assert.assertFalse(new File(config.controler().getOutputDirectory() + "ITERS/it.7/7.linkstats.txt.gz").exists());
	}
	
	@Test
	public void testReset_CorrectlyExecuted() throws IOException {
		Config config = this.util.loadConfig(null);
		LinkStatsConfigGroup lsConfig = config.linkStats();
		
		lsConfig.setWriteLinkStatsInterval(3);
		lsConfig.setAverageLinkStatsOverIterations(2);
		
		Scenario scenario = ScenarioUtils.createScenario(config);
		Node node1 = scenario.getNetwork().getFactory().createNode(scenario.createId("1"), scenario.createCoord(0, 0));
		Node node2 = scenario.getNetwork().getFactory().createNode(scenario.createId("2"), scenario.createCoord(1000, 0));
		scenario.getNetwork().addNode(node1);
		scenario.getNetwork().addNode(node2);
		Link link = scenario.getNetwork().getFactory().createLink(scenario.createId("100"), node1, node2);
		scenario.getNetwork().addLink(link);
		
		Controler controler = new Controler(scenario);
		controler.addMobsimFactory("dummy", new DummyMobsimFactory());
		config.controler().setMobsim("dummy");
		config.controler().setFirstIteration(0);
		config.controler().setLastIteration(7);
		
		controler.setCreateGraphs(false);
		controler.setDumpDataAtEnd(false);
		controler.getConfig().controler().setWriteEventsInterval(0);
		config.controler().setWritePlansInterval(0);
		controler.run();
		
		Assert.assertFalse(new File(config.controler().getOutputDirectory() + "ITERS/it.0/0.linkstats.txt.gz").exists());
		Assert.assertFalse(new File(config.controler().getOutputDirectory() + "ITERS/it.1/1.linkstats.txt.gz").exists());
		Assert.assertFalse(new File(config.controler().getOutputDirectory() + "ITERS/it.2/2.linkstats.txt.gz").exists());
		Assert.assertTrue(new File(config.controler().getOutputDirectory() + "ITERS/it.3/3.linkstats.txt.gz").exists());
		Assert.assertFalse(new File(config.controler().getOutputDirectory() + "ITERS/it.4/4.linkstats.txt.gz").exists());
		Assert.assertFalse(new File(config.controler().getOutputDirectory() + "ITERS/it.5/5.linkstats.txt.gz").exists());
		Assert.assertTrue(new File(config.controler().getOutputDirectory() + "ITERS/it.6/6.linkstats.txt.gz").exists());
		Assert.assertFalse(new File(config.controler().getOutputDirectory() + "ITERS/it.7/7.linkstats.txt.gz").exists());
		
		double[] volumes = getVolumes(config.controler().getOutputDirectory() + "ITERS/it.3/3.linkstats.txt");
		Assert.assertEquals(3, volumes[0], 1e-8);
		Assert.assertEquals(3.5, volumes[1], 1e-8);
		Assert.assertEquals(4, volumes[2], 1e-8);
		volumes = getVolumes(config.controler().getOutputDirectory() + "ITERS/it.6/6.linkstats.txt");
		Assert.assertEquals(6, volumes[0], 1e-8);
		Assert.assertEquals(6.5, volumes[1], 1e-8);
		Assert.assertEquals(7, volumes[2], 1e-8);
	}
	
	private double[] getVolumes(final String filename) throws IOException {
		BufferedReader reader = IOUtils.getBufferedReader(filename);
		reader.readLine(); // header
		String line = reader.readLine(); // link 100
		if (line == null) {
			// should never happen...
			return new double[] {Double.NaN, Double.NaN, Double.NaN};
		}
		String[] parts = line.split("\t");// [0] = linkId, [1] = matsim volume, [2] = real volume
		return new double[] {
				Double.parseDouble(parts[7]), // min	
				Double.parseDouble(parts[8]),	// avg
				Double.parseDouble(parts[9])	// max
		};
	}
	
	private static class DummyMobsim implements Mobsim {
		private final EventsManager eventsManager;
		private final int nOfEvents;
		public DummyMobsim(EventsManager eventsManager, final int nOfEvents) {
			this.eventsManager = eventsManager;
			this.nOfEvents = nOfEvents;
		}
		@Override
		public void run() {
			Id linkId = new IdImpl("100");
			for (int i = 0; i < this.nOfEvents; i++) {
				this.eventsManager.processEvent(new LinkLeaveEvent(60.0, new IdImpl(i), linkId, null));
			}
		}
	}
	
	private static class DummyMobsimFactory implements MobsimFactory {
		private int count = 1;
		@Override
		public Mobsim createMobsim(Scenario sc, EventsManager eventsManager) {
			return new DummyMobsim(eventsManager, count++);
		}
		
	}
}