package jmri.jmrit.operations.rollingstock.cars.gui;

import jmri.jmrit.operations.OperationsTestCase;
import org.junit.Assert;
import org.junit.jupiter.api.*;

/**
 *
 * @author Paul Bender Copyright (C) 2017
 */
public class CarsTableModelTest extends OperationsTestCase {

    @Test
    public void testCTor() {
        CarsTableModel t = new CarsTableModel(false,null,null);
        Assert.assertNotNull("exists",t);
    }

    // private final static Logger log = LoggerFactory.getLogger(CarsTableModelTest.class);

}
