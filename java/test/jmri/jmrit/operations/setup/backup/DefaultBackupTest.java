package jmri.jmrit.operations.setup.backup;

import jmri.jmrit.operations.OperationsTestCase;
import org.junit.Assert;
import org.junit.jupiter.api.*;

/**
 *
 * @author Paul Bender Copyright (C) 2017
 */
public class DefaultBackupTest extends OperationsTestCase {

    @Test
    public void testCTor() {
        DefaultBackup t = new DefaultBackup();
        Assert.assertNotNull("exists",t);
    }

    // private final static Logger log = LoggerFactory.getLogger(DefaultBackupTest.class);

}
