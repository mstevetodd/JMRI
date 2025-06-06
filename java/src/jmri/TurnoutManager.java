package jmri;

import java.time.LocalDateTime;
import javax.annotation.Nonnull;
import javax.annotation.CheckForNull;

/**
 * Locate a Turnout object representing some specific turnout on the layout.
 * <p>
 * Turnout objects are obtained from a TurnoutManager, which in turn is
 * generally located from the InstanceManager. A typical call sequence might be:
 * <pre>
 * Turnout turnout = InstanceManager.turnoutManagerInstance().provideTurnout("23");
 * </pre>
 * <p>
 * Each turnout has a two names. The "user" name is entirely free form, and can
 * be used for any purpose. The "system" name is provided by the system-specific
 * implementations, and provides a unique mapping to the layout control system
 * (for example LocoNet or NCE) and address within that system.
 * <p>
 * Much of the book-keeping is implemented in the AbstractTurnoutManager class,
 * which can form the basis for a system-specific implementation.
 * <p>
 * A sample use of the TurnoutManager interface can be seen in the
 * jmri.jmrit.simpleturnoutctrl.SimpleTurnoutCtrlFrame class, which provides a
 * simple GUI for controlling a single turnout.
 *
 * <p>
 * This file is part of JMRI.
 * <p>
 * JMRI is free software; you can redistribute it and/or modify it under the
 * terms of version 2 of the GNU General Public License as published by the Free
 * Software Foundation. See the "COPYING" file for a copy of this license.
 * <p>
 * JMRI is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * @author Bob Jacobsen Copyright (C) 2001
 * @see jmri.Turnout
 * @see jmri.InstanceManager
 * @see jmri.jmrit.simpleturnoutctrl.SimpleTurnoutCtrlFrame
 */
public interface TurnoutManager extends ProvidingManager<Turnout>, NameIncrementingManager {

    /**
     * String constant for Property Change to Default Turnout Thrown Speed Change.
     */
    String PROPERTY_DEFAULT_THROWN_SPEED = "DefaultTurnoutThrownSpeedChange";

    /**
     * String constant for Property Change to Default Turnout Closed Speed Change.
     */
    String PROPERTY_DEFAULT_CLOSED_SPEED = "DefaultTurnoutClosedSpeedChange";

    /**
     * Get the Turnout with the user name, then system name if needed; if that fails, create a
     * new Turnout.
     * If the name is a valid system name, it will be used for the new Turnout.
     * Otherwise, the {@link Manager#makeSystemName} method will attempt to turn it
     * into a valid system name.
     * <p>
     * This provides the same function as {@link ProvidingManager#provide}
     * which has a more generic form.
     *
     * @param name User name, system name, or address which can be promoted to
     *             system name
     * @return Never null
     * @throws IllegalArgumentException if Turnout doesn't already exist and the
     *                                  manager cannot create the Turnout due to
     *                                  an illegal name or name that can't
     *                                  be parsed.
     */
    @Nonnull
    Turnout provideTurnout(@Nonnull String name) throws IllegalArgumentException;

    /** {@inheritDoc} */
    @Override
    @Nonnull
    default Turnout provide(@Nonnull String name) throws IllegalArgumentException { return provideTurnout(name); }

    /**
     * Get an existing Turnout or return null if it doesn't exist.
     *
     * Locates via user name, then system name if needed.
     *
     * @param name User name or system name to match
     * @return null if no match found
     */
    @CheckForNull
    Turnout getTurnout(@Nonnull String name);

    /**
     * Get the Turnout with the given system name or null if no instance
     * already exists.
     *
     * @param systemName the system name
     * @return requested Turnout object or null if none exists
     */
    @CheckForNull
    @Override
    Turnout getBySystemName(@Nonnull String systemName);

    /**
     * Get the Turnout with the given user name or null if no instance
     * already exists.
     *
     * @param userName the user name
     * @return requested Turnout object or null if none exists
     */
    @CheckForNull
    @Override
    Turnout getByUserName(@Nonnull String userName);

    /**
     * Return a Turnout with the specified system and user names.
     * Lookup by UserName then provide by System Name.
     * <p>
     * Note that
     * two calls with the same arguments will get the same instance; there is
     * only one Turnout object representing a given physical turnout and
     * therefore only one with a specific system or user name.
     * <p>
     * This will always return a valid object reference; a new object will be
     * created if necessary. In that case:
     * <ul>
     * <li>If a null reference is given for user name, no user name will be
     * associated with the Turnout object created; a valid system name must be
     * provided
     * <li>If both names are provided, the system name defines the hardware
     * access of the desired turnout, and the user address is associated with
     * it. The system name must be valid.
     * </ul>
     * Note that it is possible to make an inconsistent request if both
     * addresses are provided, but the given values are associated with
     * different objects. This is a problem, and we don't have a good solution
     * except to issue warnings. This will mostly happen if you're creating
     * Turnouts when you should be looking them up.
     *
     * @param systemName the system name
     * @param userName   the user name (optional)
     * @return requested Turnout object, newly created if needed
     * @throws IllegalArgumentException if cannot create the Turnout; likely due
     *                                  to an illegal name or name that cannot
     *                                  be parsed
     */
    @Nonnull
    Turnout newTurnout(@Nonnull String systemName, @CheckForNull String userName) throws IllegalArgumentException;

    /**
     * Get text to be used for the Turnout.CLOSED state in user communication.
     * Allows text other than "CLOSED" to be used with certain hardware system
     * to represent the Turnout.CLOSED state.
     *
     * @return the textual representation of {@link jmri.Turnout#CLOSED}
     */
    @Nonnull
    String getClosedText();

    /**
     * Get text to be used for the Turnout.THROWN state in user communication.
     * Allows text other than "THROWN" to be use with certain hardware system to
     * represent the Turnout.THROWN state.
     *
     * @return the textual representation of {@link jmri.Turnout#THROWN}
     */
    @Nonnull
    String getThrownText();

    /**
     * Get a list of the valid TurnoutOperation subtypes for use with turnouts
     * of this system.
     *
     * @return a list of subtypes or an empty list if turnout operations are not
     *         supported
     */
    @Nonnull
    String[] getValidOperationTypes();

    /**
     * Get, from the user, the number of addressed bits used to control a
     * turnout. Normally this is 1, and the default routine returns one
     * automatically. Turnout Managers for systems that can handle multiple
     * control bits should override this method with one which asks the user to
     * specify the number of control bits. If the user specifies more than one
     * control bit, this method should check if the additional bits are
     * available (not assigned to another object). If the bits are not
     * available, this method should return 0 for number of control bits, after
     * informing the user of the problem.
     *
     * @param systemName the turnout system name
     * @return the bit length for turnout control
     */
    int askNumControlBits(@Nonnull String systemName);

    /**
     * Determine if the manager supports multiple control bits, as
     * {@link #askNumControlBits(java.lang.String)} will always return a value
     * even if it is not supported.
     *
     * @param systemName the turnout system name
     * @return true if manager supports multiple control bits for the turnout;
     *         false otherwise
     */
    boolean isNumControlBitsSupported(@Nonnull String systemName);

    /**
     * Get, from the user, the type of output to be used bits to control a
     * turnout. Normally this is 0 for 'steady state' control, and the default
     * routine returns 0 automatically. Turnout Managers for systems that can
     * handle pulsed control as well as steady state control should override
     * this method with one which asks the user to specify the type of control
     * to be used. The routine should return 0 for 'steady state' control, or n
     * for 'pulsed' control, where n specifies the duration of the pulse
     * (normally in seconds).
     *
     * @param systemName the turnout system name
     * @return 0 for steady state or the number of seconds for a pulse control
     */
    int askControlType(@Nonnull String systemName);

    /**
     * Determine if the manager supports the handling of pulsed and steady state
     * control as the {@link #askControlType(java.lang.String)} will always
     * return a value even if it is not supported.
     *
     * @param systemName the turnout system name
     * @return true if manager supports the control type returned by
     *         {@link #askControlType(java.lang.String)}; false otherwise
     *
     */
    boolean isControlTypeSupported(@Nonnull String systemName);

    /**
     * Get a system name for a given hardware address and system prefix.
     *
     * @param curAddress desired hardware address
     * @param prefix     system prefix used in system name
     * @return the complete turnout system name for the prefix and current
     *         address
     * @throws jmri.JmriException if unable to create a system name for the
     *                            given address, possibly due to invalid address
     *                            format
     */
    String createSystemName(@Nonnull String curAddress, @Nonnull String prefix) throws JmriException;

    void setDefaultClosedSpeed(@Nonnull String speed) throws JmriException;

    void setDefaultThrownSpeed(@Nonnull String speed) throws JmriException;

    String getDefaultThrownSpeed();

    String getDefaultClosedSpeed();

    /**
     * Get the Interval (in ms) to wait between output commands.
     * Configured in AdapterConfig, stored in memo.
     *
     * @return the (Turnout) Output Interval in milliseconds
     */
    int getOutputInterval();

    /**
     * Set the Interval (in ms) to wait between output commands.
     *
     * @param newInterval the new Output Interval in Milliseconds
     */
    void setOutputInterval(int newInterval);

    /**
     * Get end time of latest OutputInterval, calculated from the current time.
     *
     * @return end time in milliseconds or current time if no interval was set or timer has completed
     */
    @Nonnull
    LocalDateTime outputIntervalEnds();

}
