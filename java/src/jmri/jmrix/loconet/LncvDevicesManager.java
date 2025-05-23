package jmri.jmrix.loconet;

import java.util.List;

import javax.annotation.concurrent.GuardedBy;

import jmri.InstanceManager;
import jmri.Programmer;
import jmri.ProgrammingMode;
import jmri.beans.PropertyChangeSupport;
import jmri.jmrit.decoderdefn.DecoderFile;
import jmri.jmrit.decoderdefn.DecoderIndexFile;
import jmri.jmrit.roster.Roster;
import jmri.jmrit.roster.RosterEntry;

import jmri.jmrix.ProgrammingTool;
import jmri.jmrix.loconet.uhlenbrock.LncvMessageContents;
import jmri.jmrix.loconet.uhlenbrock.LncvDevice;
import jmri.jmrix.loconet.uhlenbrock.LncvDevices;
import jmri.managers.DefaultProgrammerManager;
//import jmri.progdebugger.ProgDebugger;

import jmri.util.ThreadingUtil;
import jmri.util.swing.JmriJOptionPane;

/**
 * LocoNet LNCV Devices Manager
 * <p>
 * A centralized resource to help identify LocoNet "LNCV Format"
 * devices and "manage" them.
 * <p>
 * Supports the following features:
 *  - LNCV "discovery" process supported via PROG_START_ALL call
 *  - LNCV Device "destination address" change supported by writing a new value to LNCV 0 (close session next)
 *  - LNCV Device "reconfigure/reset" not supported/documented
 *  - identification of devices with conflicting "destination address"es (warning before program start)
 *  - identification of a matching JMRI "decoder definition" for each discovered
 *    device, if an appropriate definition exists (only 1 value is matched, checks for LNCV protocol support)
 *  - identification of matching JMRI "roster entry" which matches each
 *    discovered device, if an appropriate roster entry exists
 *  - ability to open a symbolic programmer for a given discovered device, if
 *    an appropriate roster entry exists
 *
 * @author B. Milhaupt Copyright (c) 2020
 * @author Egbert Broerse (c) 2021
 */

public class LncvDevicesManager extends PropertyChangeSupport
        implements LocoNetListener, jmri.Disposable {
    private final LocoNetSystemConnectionMemo memo;
    @GuardedBy("this")
    private final LncvDevices lncvDevices;

    // constant for thread name, with memo prefix appended.
    static final String ROSTER_THREAD_NAME = "rosterMatchingListLncvDM";

    public LncvDevicesManager(@javax.annotation.Nonnull LocoNetSystemConnectionMemo memo) {
        this.memo = memo;
        if (memo.getLnTrafficController() != null) {
            memo.getLnTrafficController().addLocoNetListener(~0, this);
        } else {
            log.error("No LocoNet connection available, this tool cannot function"); // NOI18N
        }
        synchronized (this) {
            lncvDevices = new LncvDevices();
        }
    }

    public synchronized LncvDevices getDeviceList() {
        return lncvDevices;
    }

    public synchronized int getDeviceCount() {
        return lncvDevices.size();
    }

    public void clearDevicesList() {
        synchronized (this) {
            lncvDevices.removeAllDevices();
        }
        jmri.util.ThreadingUtil.runOnLayoutEventually( ()-> firePropertyChange("DeviceListChanged", true, false));
    }

    /**
     * Extract module information from LNCV READREPLY/READREPLY2 message,
     * if not already in the lncvDevices list, try to find a matching decoder definition (by article number)
     * and add it. Skip if already in the list.
     *
     * @param m The received LocoNet message. Note that this same object may
     *            be presented to multiple users. It should not be modified
     *            here.
     */
    @Override
    public void message(LocoNetMessage m) {
        if (LncvMessageContents.isSupportedLncvMessage(m)) {
            if ((LncvMessageContents.extractMessageType(m) == LncvMessageContents.LncvCommand.LNCV_READ_REPLY) ||
                    //Updated 2022 to also accept undocumented Digikeijs DR5088 reply format LNCV_READ_REPLY2
                    (LncvMessageContents.extractMessageType(m) == LncvMessageContents.LncvCommand.LNCV_READ_REPLY2)) {
                // it's an LNCV ReadReply message, decode contents:
                LncvMessageContents contents = new LncvMessageContents(m);
                int art = contents.getLncvArticleNum();
                int addr = -1;
                int cv = contents.getCvNum();
                int val = contents.getCvValue();
                log.debug("LncvDevicesManager got read reply: art:{}, address:{} cv:{} val:{}", art, addr, cv, val);
                if (cv == 0) { // trust last used address
                    addr = val; // if cvNum = 0, this is the LNCV module address
                    log.debug("LNCV read reply: device address {} of LNCV returns {}", addr, val);

                    synchronized (this) {
                        if (lncvDevices.addDevice(new LncvDevice(art, addr, cv, val, "", "", -1))) {
                            log.debug("new LncvDevice added to table");
                            // Annotate the discovered device LNCV data based on address
                            for (int i = 0; i < lncvDevices.size(); ++i) {
                                LncvDevice dev = lncvDevices.getDevice(i);
                                if ((dev.getProductID() == art) && (dev.getDestAddr() == addr)) {
                                    // need to find a corresponding roster entry
                                    if (dev.getRosterName() != null && dev.getRosterName().isEmpty()) {
                                        // Yes. Try to find a roster entry which matches the device characteristics
                                        log.debug("Looking for prodID {}/adr {} in Roster", dev.getProductID(), dev.getDestAddr());

                                        // Try to find a roster entry which matches the device characteristics
                                        log.debug("Looking for adr {} in Roster", dev.getDestAddr());

                                        // threadUtil off GUI for Roster reading decoderfiles cf. Lnsv1DevicesManager
                                        ThreadingUtil.newThread(() -> {
                                            List<RosterEntry> rl;
                                            try {
                                                rl = Roster.getDefault().getEntriesMatchingCriteria(
                                                        Integer.toString(dev.getDestAddr()), // composite DCC address
                                                        null, null, Integer.toString(dev.getProductID()),
                                                        null); // TODO filter on progMode LNCV only on new roster entries
                                                log.debug("LncvDeviceManager found {} matches in Roster", rl.size());
                                                if (rl.isEmpty()) {
                                                    log.debug("No corresponding RosterEntry found");
                                                } else if (rl.size() == 1) {
                                                    log.debug("Matching RosterEntry found");
                                                    dev.setRosterEntry(rl.get(0)); // link this device to the entry

                                                    String title = rl.get(0).getDecoderModel() + " (" + rl.get(0).getDecoderFamily() + ")";
                                                    // fileFromTitle() matches by model + " (" + family + ")"
                                                    DecoderFile decoderFile = InstanceManager.getDefault(DecoderIndexFile.class).fileFromTitle(title);
                                                    if (decoderFile != null) {
                                                        // TODO check for LNCV mode
                                                        dev.setDecoderFile(decoderFile); // link to decoderFile (to check programming mode from table)
                                                        log.debug("Attached a decoderfile");
                                                    } else {
                                                        log.warn("Could not attach decoderfile {} to entry", rl.get(0).getFileName());
                                                    }

                                                } else { // matches > 1
                                                    JmriJOptionPane.showMessageDialog(null,
                                                            Bundle.getMessage("WarnMultipleLncvModsFound", art, dev.getDestAddr(), rl.size()),
                                                            Bundle.getMessage("WarningTitle"), JmriJOptionPane.WARNING_MESSAGE);
                                                    log.info("Found multiple matching roster entries. " + "Cannot associate any one to this device.");
                                                }
                                            } catch (Exception e) {
                                                log.error("Error creating Roster.matchingList: {}", e.getMessage());
                                            }
                                        }, ROSTER_THREAD_NAME + memo.getSystemPrefix()).start();
                                        // this will block until the thread completes, either by finishing or by being cancelled


                                    }
                                    // notify listeners of pertinent change to device list
                                    firePropertyChange("DeviceListChanged", true, false);
                                }
                            }
                        } else {
                            log.debug("LNCV device was already in list");
                        }
                    }
                } else {
                    log.debug("LNCV device check skipped as value not CV0/module address");
                }
            } else {
                log.debug("LNCV message not a READ REPLY [{}]", m);
            }
        } else {
            log.debug("LNCV message not recognized");
        }
    }

    public synchronized LncvDevice getDevice(int art, int addr) {
        for (int i = 0; i < lncvDevices.size(); ++ i) {
            LncvDevice dev = lncvDevices.getDevice(i);
            if ((dev.getProductID() == art) && (dev.getDestAddr() == addr)) {
                return dev;
            }
        }
        return null;
    }

    public ProgrammingResult prepareForSymbolicProgrammer(LncvDevice dev, ProgrammingTool t) {
        synchronized(this) {
            if (lncvDevices.isDeviceExistant(dev) < 0) {
                return ProgrammingResult.FAIL_NO_SUCH_DEVICE;
            }
            int destAddr = dev.getDestAddr();
            if (destAddr == 0) {
                return ProgrammingResult.FAIL_DESTINATION_ADDRESS_IS_ZERO;
            }
            int deviceCount = 0;
            for (LncvDevice d : lncvDevices.getDevices()) {
                if (destAddr == d.getDestAddr()) {
                    deviceCount++;
                }
            }
            log.debug("prepareForSymbolicProgrammer found {} matches", deviceCount);
            if (deviceCount > 1) {
                return ProgrammingResult.FAIL_MULTIPLE_DEVICES_SAME_DESTINATION_ADDRESS;
            }
        }

        if ((dev.getRosterName() == null) || (dev.getRosterName().isEmpty())) {
            return ProgrammingResult.FAIL_NO_MATCHING_ROSTER_ENTRY;
        }

        // check if roster entry still present in Roster
        RosterEntry re = Roster.getDefault().entryFromTitle(dev.getRosterName());
        if (re == null) {
            log.warn("Could not open LNSV1 Programmer because {} not found in Roster. Removed from device",
                    dev.getRosterName());
            dev.setRosterEntry(null);
            jmri.util.ThreadingUtil.runOnLayoutEventually( ()-> firePropertyChange("DeviceListChanged", true, false));
            return ProgrammingResult.FAIL_NO_MATCHING_ROSTER_ENTRY;
        }
        String name = re.getId();

        DefaultProgrammerManager pm = memo.getProgrammerManager();
        if (pm == null) {
            return ProgrammingResult.FAIL_NO_APPROPRIATE_PROGRAMMER;
        }
        Programmer p = pm.getAddressedProgrammer(false, dev.getDestAddr());
        if (p == null) {
            return ProgrammingResult.FAIL_NO_ADDRESSED_PROGRAMMER;
        }

        //if (p.getClass() != ProgDebugger.class) { // Debug in Simulator
            // ProgDebugger is used for LocoNet HexFile Sim, uncommenting above line allows testing of LNCV Tool
            if (!p.getSupportedModes().contains(LnProgrammerManager.LOCONETLNCVMODE)) {
                return ProgrammingResult.FAIL_NO_LNCV_PROGRAMMER;
            }
            p.setMode(LnProgrammerManager.LOCONETLNCVMODE);
            ProgrammingMode prgMode = p.getMode();
            if (!prgMode.equals(LnProgrammerManager.LOCONETLNCVMODE)) {
                return ProgrammingResult.FAIL_NO_LNCV_PROGRAMMER;
            }
        //}

        t.openPaneOpsProgFrame(re, name, "programmers/Comprehensive.xml", p); // NOI18N
        return ProgrammingResult.SUCCESS_PROGRAMMER_OPENED;
    }

    @Override
    public void dispose(){
        if (memo.getLnTrafficController() != null) {
            memo.getLnTrafficController().removeLocoNetListener(~0, this);
        }
    }

    public enum ProgrammingResult {
        SUCCESS_PROGRAMMER_OPENED,
        FAIL_NO_SUCH_DEVICE,
        FAIL_NO_APPROPRIATE_PROGRAMMER,
        FAIL_NO_MATCHING_ROSTER_ENTRY,
        FAIL_DESTINATION_ADDRESS_IS_ZERO,
        FAIL_MULTIPLE_DEVICES_SAME_DESTINATION_ADDRESS,
        FAIL_NO_ADDRESSED_PROGRAMMER,
        FAIL_NO_LNCV_PROGRAMMER
    }

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(LncvDevicesManager.class);

}
