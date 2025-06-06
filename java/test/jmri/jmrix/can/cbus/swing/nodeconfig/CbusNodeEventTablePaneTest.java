package jmri.jmrix.can.cbus.swing.nodeconfig;

import jmri.jmrix.can.CanSystemConnectionMemo;
import jmri.jmrix.can.cbus.node.CbusNode;
import jmri.jmrix.can.cbus.node.CbusNodeEvent;
import jmri.jmrix.can.cbus.node.CbusNodeEventTableDataModel;
import jmri.util.*;

import org.junit.Assert;
import org.junit.jupiter.api.*;

import org.netbeans.jemmy.operators.JFrameOperator;
import org.netbeans.jemmy.operators.JTableOperator;

/**
 * Test simple functioning of CbusNodeEventTablePane
 *
 * @author Paul Bender Copyright (C) 2016
 * @author Steve Young Copyright (C) 2019
 */
@jmri.util.junit.annotations.DisabledIfHeadless
public class CbusNodeEventTablePaneTest {

    @Test
    public void testCtor() {
        t = new CbusNodeEventTablePane(null);
        Assert.assertNotNull("exists",t);
    }

    @Test
    public void testCtorWithModelAndInit() {

        nodeModel = new CbusNodeEventTableDataModel(null, memo, 3,CbusNodeEventTableDataModel.MAX_COLUMN);

        t = new CbusNodeEventTablePane(nodeModel);
        t.initComponents(memo);
        t.setNode(null);

        Assert.assertNotNull("exists",t);

        nodeModel.dispose();
        t.dispose();

    }

    @Test
    public void testSetNode() {

        nodeModel = new CbusNodeEventTableDataModel(null, memo, 3,CbusNodeEventTableDataModel.MAX_COLUMN);

        t = new CbusNodeEventTablePane(nodeModel);
        t.initComponents(memo);
        t.setHideEditButton();
        // check pane has loaded something
        JmriJFrame f = new JmriJFrame();
        f.add(t);
        f.setTitle(t.getTitle());

        CbusNode node = new CbusNode(memo,12345);
        // set node to 4 ev vars per event, para 5
        node.getNodeParamManager().setParameters(new int[]{8,1,2,3,4,4,6,7,8});

        CbusNodeEvent ev = new CbusNodeEvent(memo,0,7,12345,-1,4);  // nn, en, thisnode, index, maxevvar
        CbusNodeEvent eva = new CbusNodeEvent(memo,257,111,12345,-1,4);  // nn, en, thisnode, index, maxevvar

        ev.setEvArr(new int[]{1,2,3,4});

        node.getNodeEventManager().addNewEvent(ev);
        node.getNodeEventManager().addNewEvent(eva);

        t.setNode(node);

        Assert.assertNotNull("exists",t);

        ThreadingUtil.runOnGUI( () -> {
            f.pack();
            f.setVisible(true);
        });

        JTableOperator tbl = new JTableOperator(new JFrameOperator(f), 0);

        tbl.waitCell("7",0,1);
        tbl.waitCell("257",1,0);
        tbl.waitCell("111",1,1);

        Assert.assertTrue("2 rows",tbl.getRowCount()==2);

        JUnitUtil.dispose(f);

    }

    private CbusNodeEventTablePane t;
    private CanSystemConnectionMemo memo = null;
    private CbusNodeEventTableDataModel nodeModel;

    @BeforeEach
    public void setUp() {
        JUnitUtil.setUp();
        memo = new CanSystemConnectionMemo();
    }

    @AfterEach
    public void tearDown() {
        t = null;
        nodeModel = null;
        Assertions.assertNotNull(memo);
        memo.dispose();
        memo = null;
        JUnitUtil.tearDown();
    }

    // private final static Logger log = LoggerFactory.getLogger(CbusNodeEventTablePaneTest.class);

}
