package jmri.jmrit.operations.locations.gui;

import jmri.jmrit.operations.OperationsTestCase;
import org.junit.Assert;
import org.junit.jupiter.api.*;

/**
 *
 * @author Paul Bender Copyright (C) 2017
 */
public class StagingTableModelTest extends OperationsTestCase {

    @Test
    public void testCTor() {
        StagingTableModel t = new StagingTableModel();
        Assert.assertNotNull("exists",t);
    }

    // private final static Logger log = LoggerFactory.getLogger(StagingTableModelTest.class);

}
