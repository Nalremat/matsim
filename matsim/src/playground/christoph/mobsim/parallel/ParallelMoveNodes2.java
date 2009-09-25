///* *********************************************************************** *
// * project: org.matsim.*
// * ParallelMoveNodes.java
// *                                                                         *
// * *********************************************************************** *
// *                                                                         *
// * copyright       : (C) 2008 by the members listed in the COPYING,        *
// *                   LICENSE and WARRANTY file.                            *
// * email           : info at matsim dot org                                *
// *                                                                         *
// * *********************************************************************** *
// *                                                                         *
// *   This program is free software; you can redistribute it and/or modify  *
// *   it under the terms of the GNU General Public License as published by  *
// *   the Free Software Foundation; either version 2 of the License, or     *
// *   (at your option) any later version.                                   *
// *   See also COPYING, LICENSE and WARRANTY file                           *
// *                                                                         *
// * *********************************************************************** */
//package playground.christoph.mobsim.parallel;
//
//import java.util.List;
//
//import org.apache.log4j.Logger;
//import org.matsim.core.gbl.Gbl;
//import org.matsim.core.mobsim.queuesim.QueueNode;
//
//public class ParallelMoveNodes2 {
//	
//	private final static Logger log = Logger.getLogger(ParallelMoveNodes2.class);
//	
//	private static MoveNodesThread[] moveNodesThreads;
//	private static ThreadLocker threadLocker;
//	
//	private int numOfThreads;
//	private List<List<QueueNodeDependencies>> parallelSimNodesArray;
//	
//	int counter;
//		
//	public ParallelMoveNodes2()
//	{
//	}
//
//	/*
//	 * Hand over SubLists to the Threads. This allows
//	 * them to remove inactive Links from the list directly!
//	 * 
//	 * Warning: the SubLists are still connected to the
//	 * underlying list. We can iterate over them in the
//	 * Threads but we are not allowed to modify them!
//	 * 
//	 * Edit: Now we copy the SubLists. This allows us to
//	 * remove inactive Links directly from those Lists.
//	 */
//	public void run(double time)
//	{	
//		counter = 0;
////		for (QueueNodeDependencies queueNodeDependencie : queueNodeDependencies)
////		{
////			queueNodeDependencie.setMoved(false);
////			queueNodeDependencie.resetProcessedInNodes();
////		}
//		
//		try
//		{
//			// lock threadLocker until wait() statement listens for the notifies
//			synchronized(threadLocker) 
//			{
//				for(List<QueueNodeDependencies> nodesList : parallelSimNodesArray)
//				{
//					int subListSize = nodesList.size() /  numOfThreads;
//					
//					for (int i = 0; i < numOfThreads; i++)
//					{
//						if ((i + 1) < numOfThreads)
//						{
//							List<QueueNodeDependencies> subList = nodesList.subList(i * subListSize, (i + 1) * subListSize);
//
//							moveNodesThreads[i].setProcessableQueueNodes(subList);
//						}
//						// its the last thread, so add all remaining links!
//						else
//						{
//							List<QueueNodeDependencies> subList = nodesList.subList(i * subListSize, nodesList.size());
//							moveNodesThreads[i].setProcessableQueueNodes(subList);
//						}
//					}
//					
//					// set current Time
//					MoveNodesThread.setTime(time);
//								
//					threadLocker.setCounter(moveNodesThreads.length);
//					threadLocker.time = time;
//									
//					for (MoveNodesThread moveNodesThread : moveNodesThreads)
//					{
//						moveNodesThread.startMoving();		
//					}
//					
//					threadLocker.wait();
//				}
//			}		
//		} 
//		catch (InterruptedException e)
//		{
//			Gbl.errorMsg(e);
//		}
////		log.info("Counter: " + counter);
//	}
//	
//	public static synchronized boolean isRunning()
//	{
//		boolean stillMoving = false;
//		// if moving Nodes is finished in all Threads
//		for (MoveNodesThread moveNodesThread : moveNodesThreads)
//		{
//			if (!moveNodesThread.isWaiting())
//			{
//				stillMoving = true;
//				break;
//			}			
//		}
//		return stillMoving;
//	}
//
//	public void init(List<List<QueueNodeDependencies>> parallelSimNodesArray, int numOfThreads, boolean simulateAllNodes)
//	{
//		this.numOfThreads = numOfThreads;
//		this.parallelSimNodesArray = parallelSimNodesArray;
//		
//		threadLocker = new ThreadLocker();
//		
//		MoveNodesThread.setThreadLocker(threadLocker);
//		
//		moveNodesThreads = new MoveNodesThread[numOfThreads];
//		
//		// setup threads
//		for (int i = 0; i < numOfThreads; i++) 
//		{
//			MoveNodesThread moveNodeThread = new MoveNodesThread(simulateAllNodes);
//			moveNodeThread.setName("MoveNodes" + i);
//			moveNodeThread.setDaemon(true);	// make the Threads Daemons so they will terminate automatically
//			moveNodesThreads[i] = moveNodeThread;
//			
//			moveNodeThread.start();
//		}
//	}
//
//	private static class ThreadLocker
//	{
//		private int count = 0;
//		public double time;
//		
//		public synchronized void setCounter(int i)
//		{
//			this.count = i;
//		}
//				
//		public synchronized void incCounter()
//		{
//			count++;
//		}
//		
//		public synchronized void decCounter()
//		{
//			count--;
////			log.info("Count: " + count + ", Time: " + time);
//			if(count == 0)
//			{
////				log.info("Notify " + time);
//				notify();
//			}
//		}
//	}
//	
//	/**
//	 * The thread class that really handles the links.
//	 */
//	private static class MoveNodesThread extends Thread
//	{
//		private static ThreadLocker threadLocker;
//		private static double time = 0.0;
//		private static boolean simulationRunning = true;
//
//		private List<QueueNodeDependencies> processableQueueNodes;
//
//		private boolean isWaiting = true;
//		private boolean simulateAllNodes = false;
//		
//		public MoveNodesThread(boolean simulateAllNodes)
//		{
//			this.simulateAllNodes = simulateAllNodes;
//
//		}
//		
//		public void setProcessableQueueNodes(List<QueueNodeDependencies> processableQueueNodes)
//		{
//			this.processableQueueNodes = processableQueueNodes;
////			this.processableQueueNodes = new ArrayList<QueueNodeDependencies>();
////			this.processableQueueNodes.addAll(processableQueueNodes);
//		}
//				
//		public static void setThreadLocker (ThreadLocker tl)
//		{
//			threadLocker = tl;
//		}
//		
//		public static void setTime(final double t)
//		{
//			time = t;
//		}
//		
//		public void startMoving()
//		{
//			synchronized(this)
//			{
//				notify();
//			}
//		}
//		
//		public boolean isWaiting()
//		{
//			return isWaiting;
//		}
//		
//		@Override
//		public void run()
//		{
//			while (simulationRunning)
//			{
//				try
//				{
//					/*
//					 * threadLocker.decCounter() and wait() have to be
//					 * executed in a synchronized block! Otherwise it could
//					 * happen, that the replanning ends and the replanning of
//					 * the next SimStep executes the notify command before
//					 * wait() is called -> we have a DeadLock!
//					 */
//					synchronized(this)
//					{
//						threadLocker.decCounter();
//						wait();
//						isWaiting = false;
//					}
//							
//					/*
//					 * As long as we get new QueueNodes to move...
//					 */
//					for(QueueNodeDependencies qnd : processableQueueNodes)
//					{
//						/*
//						 * Node is moveable. Now check the criteria from 
//						 * the original QueueSimEngine.
//						 */
//						QueueNode node = qnd.getQueueNode();
//						if (node.isActive() || node.isSignalized() || simulateAllNodes)
//						{
//							qnd.getQueueNode().moveNode(time, qnd.getRandom());
//						}
//					}
//				}
//				catch (InterruptedException ie)
//				{
//					log.error("Something is going wrong here...");
//				}		
//				finally
//				{
//					isWaiting = true;
//				}
//			}	// while Simulation Running
//			
//		}	// run()
//		
//	}	// ReplannerThread
//	
//}