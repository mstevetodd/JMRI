package jmri.jmrit.operations.automation.actions;

import java.io.File;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jmri.InstanceManager;
import jmri.jmrit.operations.locations.Location;
import jmri.jmrit.operations.locations.LocationManager;
import jmri.jmrit.operations.setup.Control;
import jmri.jmrit.operations.setup.Setup;
import jmri.jmrit.operations.trains.*;
import jmri.jmrit.operations.trains.csv.TrainCsvSwitchLists;
import jmri.jmrit.operations.trains.excel.TrainCustomManifest;
import jmri.jmrit.operations.trains.excel.TrainCustomSwitchList;

public class RunSwitchListChangesAction extends Action {

    private static final int _code = ActionCodes.RUN_SWITCHLIST_CHANGES;
    protected static final boolean IS_CHANGED = true;

    @Override
    public int getCode() {
        return _code;
    }

    @Override
    public String getName() {
        return Bundle.getMessage("RunSwitchListChanges");
    }

    @Override
    public void doAction() {
        doAction(IS_CHANGED);
    }

    /**
     * Creates a custom switch list for each location that is selected and there's
     * new work for that location.
     * <p>
     * common code see RunSwitchListAction.java
     *
     * @param isChanged if set true only locations with changes will get a custom
     *                  switch list.
     */
    protected void doAction(boolean isChanged) {
        if (getAutomationItem() != null) {
            if (!Setup.isGenerateCsvSwitchListEnabled()) {
                log.warn("Generate CSV Switch List isn't enabled!");
                finishAction(false);
                return;
            }
            // we do need one of these!
            if (!InstanceManager.getDefault(TrainCustomSwitchList.class).excelFileExists()) {
                log.warn("Manifest creator file not found!, directory name: {}, file name: {}",
                        InstanceManager.getDefault(TrainCustomSwitchList.class).getDirectoryName(),
                        InstanceManager.getDefault(TrainCustomSwitchList.class).getFileName());
                finishAction(false);
                return;
            }
            setRunning(true);
            TrainSwitchLists trainSwitchLists = new TrainSwitchLists();
            TrainCsvSwitchLists trainCsvSwitchLists = new TrainCsvSwitchLists();
            // check that both the Excel custom manifest and custom switch lists aren't busy
            // with other work
            // We've found that on some OS only one copy of Excel can be running at a time
            // this can wait thread
            if (!InstanceManager.getDefault(TrainCustomManifest.class).checkProcessReady()) {
                log.warn(
                        "Timeout waiting for excel manifest program to complete previous operation, timeout value: {} seconds",
                        Control.excelWaitTime);
            }
            // this can wait thread
            if (!InstanceManager.getDefault(TrainCustomSwitchList.class).checkProcessReady()) {
                log.warn(
                        "Timeout waiting for excel switch list program to complete previous operation, timeout value: {} seconds",
                        Control.excelWaitTime);
            }
            if (InstanceManager.getDefault(TrainCustomSwitchList.class).doesCommonFileExist()) {
                log.warn("Switch List CSV common file exists!");
            }
            for (Location location : InstanceManager.getDefault(LocationManager.class).getUniqueLocationsByNameList()) {
                if (location.isSwitchListEnabled() &&
                        (!isChanged || location.getStatus().equals(Location.MODIFIED))) {
                    File csvFile = trainCsvSwitchLists.buildSwitchList(location);
                    // also build the regular switch lists so they can be used
                    trainSwitchLists.buildSwitchList(location);
                    if (csvFile == null || !csvFile.exists()) {
                        log.error("CSV switch list file was not created for location {}", location.getName());
                        finishAction(false);
                        return;
                    }
                    location.setStatus(Location.PRINTED);
                    location.setSwitchListState(Location.SW_PRINTED);
                    InstanceManager.getDefault(TrainCustomSwitchList.class).addCsvFile(csvFile);
                    log.info("Queued switch list CSV file location ({}) for custom processing", location.getName());
                }
            }
            // Processes the CSV Manifest files using an external custom program.
            boolean status = InstanceManager.getDefault(TrainCustomSwitchList.class).process();
            if (status) {
                try {
                 // wait up to 60 seconds per file
                    status = InstanceManager.getDefault(TrainCustomSwitchList.class).waitForProcessToComplete(); 
                } catch (InterruptedException e) {
                    // TODO Auto-generated catch block
                    log.error("Thread interrupeted while waiting", e);
                }
            } else {
                log.info("No switch list changes found");
            }
            // set trains switch lists printed
            InstanceManager.getDefault(TrainManager.class).setTrainsSwitchListStatus(Train.PRINTED);
            finishAction(status);
        }
    }

    @Override
    public void cancelAction() {
        // no cancel for this action
    }

    private final static Logger log = LoggerFactory.getLogger(RunSwitchListChangesAction.class);

}
