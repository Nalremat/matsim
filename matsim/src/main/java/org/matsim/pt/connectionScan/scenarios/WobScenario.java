package org.matsim.pt.connectionScan.scenarios;

import org.matsim.api.core.v01.Scenario;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.controler.AbstractModule;
import org.matsim.core.controler.Controler;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.pt.connectionScan.ConnectionScanTransitRouterProvider;
import org.matsim.pt.router.TransitRouter;

import java.io.File;

public class WobScenario {

    public static void main(String[] args) {

        File file = new File("");
        System.out.println(file.getAbsolutePath());

        String config = "../../..\\shared-svn\\projects\\ptrouting\\niedersachsen_sample_scenario/config.xml";
//        String config = "contribs\\av\\src\\test\\resources\\intermodal_scenario\\config2.xml";

        Scenario scenario = ScenarioUtils.loadScenario(ConfigUtils.loadConfig(config));

        scenario.getConfig().controler().setLastIteration(0);
//        scenario.getConfig().plansCalcRoute().getOrCreateModeRoutingParams("walk").setTeleportedModeSpeed(100d);

        Controler controler = new Controler(scenario);
        controler.addOverridingModule(new AbstractModule() {

            @Override
            public void install() {
                bind(TransitRouter.class).toProvider(ConnectionScanTransitRouterProvider.class);
            }
        });
        controler.run();
    }

}
