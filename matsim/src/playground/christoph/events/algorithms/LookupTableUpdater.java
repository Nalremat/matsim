package playground.christoph.events.algorithms;

import java.util.ArrayList;
import java.util.List;

import org.matsim.core.mobsim.queuesim.SimulationTimer;
import org.matsim.core.mobsim.queuesim.events.QueueSimulationAfterSimStepEvent;
import org.matsim.core.mobsim.queuesim.events.QueueSimulationInitializedEvent;
import org.matsim.core.mobsim.queuesim.listener.QueueSimulationAfterSimStepListener;
import org.matsim.core.mobsim.queuesim.listener.QueueSimulationInitializedListener;
import org.matsim.population.algorithms.PlanAlgorithm;

import playground.christoph.router.DijkstraWrapper;
import playground.christoph.router.KnowledgePlansCalcRoute;
import playground.christoph.router.costcalculators.KnowledgeTravelCostWrapper;
import playground.christoph.router.costcalculators.KnowledgeTravelTimeWrapper;

/*
 * Until now, the updating Methods have been called from
 * the ReplanningQueueSimulation.
 * Now we now try to do this event based...
 * 
 * Attention: The LinkVehiclesCounter has to be added to the
 * QueueSimulation before the LookupTableUpdater is added!
 */
public class LookupTableUpdater implements QueueSimulationAfterSimStepListener, QueueSimulationInitializedListener{
	
	/*
	 * We can handle different Replanner Arrays.
	 * For Example the LeaveLinkReplanner could use
	 * another Replanner than the ActEndReplanner.
	 */
	private List<PlanAlgorithm[][]> replannerArrays;
	
	public LookupTableUpdater()
	{
		this.replannerArrays = new ArrayList<PlanAlgorithm[][]>();
	}
	
	public void addReplannerArray(PlanAlgorithm[][] array)
	{
		this.replannerArrays.add(array);
	}
	
	public void removeReplannerArray(PlanAlgorithm[][] array)
	{
		this.replannerArrays.remove(array);
	}
	
	/*
	 * Using LookupTables for the LinktravelTimes should speed up the WithinDayReplanning.
	 */
	public void updateLinkTravelTimesLookupTables(double time)
	{	
		for (PlanAlgorithm[][] replannerArray : replannerArrays)
		{
			/*
			 * We update the LookupTables only in the "Parent Replanners".
			 * Their Children are clones that use the same Maps to store
			 * the LinkTravelTimes.
			 */
			for(int i = 0; i < replannerArray.length; i++)
			{	
				// insert clone
				if (replannerArray[i][0] instanceof KnowledgePlansCalcRoute)
				{
					KnowledgePlansCalcRoute replanner = (KnowledgePlansCalcRoute)replannerArray[i][0];
					
					if (replanner.getLeastCostPathCalculator() instanceof DijkstraWrapper)
					{
						DijkstraWrapper dijstraWrapper = (DijkstraWrapper)replanner.getLeastCostPathCalculator();
						
						if (dijstraWrapper.getTravelTimeCalculator() instanceof KnowledgeTravelTimeWrapper)
						{
							((KnowledgeTravelTimeWrapper)dijstraWrapper.getTravelTimeCalculator()).updateLookupTable(time);
						}
					}
					
					if (replanner.getPtFreeflowLeastCostPathCalculator() instanceof DijkstraWrapper)
					{
						DijkstraWrapper dijstraWrapper = (DijkstraWrapper)replanner.getPtFreeflowLeastCostPathCalculator();
						
						if (dijstraWrapper.getTravelTimeCalculator() instanceof KnowledgeTravelTimeWrapper)
						{
							((KnowledgeTravelTimeWrapper)dijstraWrapper.getTravelTimeCalculator()).updateLookupTable(time);
						}
					} 
				}
			}
		}
	}
	
	/*
	 * Using LookupTables for the LinktravelCosts should speed up the WithinDayReplanning.
	 */
	public void updateLinkTravelCostsLookupTables(double time)
	{	
		for (PlanAlgorithm[][] replannerArray : replannerArrays)
		{
			/*
			 * We update the LookupTables only in the "Parent Replanners".
			 * Their Children are clones that use the same Maps to store
			 * the LinkTravelTimes.
			 */
			for(int i = 0; i < replannerArray.length; i++)
			{	
				// insert clone
				if (replannerArray[i][0] instanceof KnowledgePlansCalcRoute)
				{
					KnowledgePlansCalcRoute replanner = (KnowledgePlansCalcRoute)replannerArray[i][0];
					
					//if (replanner.getLeastCostPathCalculator() instanceof KnowledgeTravelCostWrapper)
					if (replanner.getLeastCostPathCalculator() instanceof DijkstraWrapper)
					{
						DijkstraWrapper dijstraWrapper = (DijkstraWrapper)replanner.getLeastCostPathCalculator();
						
						if (dijstraWrapper.getTravelCostCalculator() instanceof KnowledgeTravelCostWrapper)
						{
							((KnowledgeTravelCostWrapper)dijstraWrapper.getTravelCostCalculator()).updateLookupTable(time);
						}
					}
					
					//if (replanner.getPtFreeflowLeastCostPathCalculator() instanceof KnowledgeTravelCostWrapper)
					if (replanner.getPtFreeflowLeastCostPathCalculator() instanceof DijkstraWrapper)
					{
						DijkstraWrapper dijstraWrapper = (DijkstraWrapper)replanner.getPtFreeflowLeastCostPathCalculator();
						
						if (dijstraWrapper.getTravelCostCalculator() instanceof KnowledgeTravelCostWrapper)
						{
							((KnowledgeTravelCostWrapper)dijstraWrapper.getTravelCostCalculator()).updateLookupTable(time);
						}
					} 
				}
			}
		}
	}

	public void notifySimulationAfterSimStep(QueueSimulationAfterSimStepEvent e)
	{
//		System.out.println("LookupTableUpdater QueueSimulationAfterSimStepEvent " + e.getSimulationTime() + "-------------------------------------------------------------------------------");
		/*
		 *  We update our LookupTables for the next Timestep.
  		 * Update the LinkTravelTimes first because the LinkTravelCosts may use
		 * them already!
		 */
		updateLinkTravelTimesLookupTables(e.getSimulationTime() + SimulationTimer.getSimTickTime());
		updateLinkTravelCostsLookupTables(e.getSimulationTime() + SimulationTimer.getSimTickTime());
	}

	public void notifySimulationInitialized(QueueSimulationInitializedEvent e)
	{
//		System.out.println("LookupTableUpdater QueueSimulationInitializedEvent-------------------------------------------------------------------------------");
		/*
		 *  We update our LookupTables for the next Timestep.
  		 * Update the LinkTravelTimes first because the LinkTravelCosts may use
		 * them already!
		 */
		updateLinkTravelTimesLookupTables(SimulationTimer.getSimStartTime());
		updateLinkTravelCostsLookupTables(SimulationTimer.getSimStartTime());	
	}
}