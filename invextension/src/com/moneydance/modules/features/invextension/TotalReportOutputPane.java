/*
 * TotalReportOutputPane.java
 * Copyright (c) 2014, Dale K. Furrow
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in the
 *       documentation and/or other materials provided with the distribution.
 *     * Neither the name of the <organization> nor the
 *       names of its contributors may be used to endorse or promote products
 *       derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL <COPYRIGHT HOLDER> BE LIABLE FOR ANY
 * DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.moneydance.modules.features.invextension;

import com.moneydance.modules.features.invextension.SecurityReport.MetricEntry;
import com.moneydance.modules.features.invextension.TotalReport.ReportTableModel;

import javax.swing.*;
import javax.swing.table.*;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.*;
import java.io.*;
import java.text.DecimalFormat;
import java.util.*;
import java.util.List;
import java.util.logging.Level;

/**
 * An extended and formatted of CoolTable by Kurt Riede added multi sort,
 * filter, rendering of columns, rows, and cells
 *
 * @author Dale Furrow
 */
@SuppressWarnings("rawtypes")
public final class TotalReportOutputPane extends JScrollPane {
    @Serial
    private static final long serialVersionUID = 353638079867239526L;
    private static final Color LIGHT_LIGHT_GRAY = new Color(230, 230, 230);

    private final FormattedTable lockedTable;
    private final FormattedTable scrollTable;
    public int firstSort;
    public int secondSort;
    public int thirdSort = 0;
    public SortOrder firstOrder = SortOrder.ASCENDING;
    public SortOrder secondOrder = SortOrder.ASCENDING;
    public SortOrder thirdOrder = SortOrder.ASCENDING;
    public ReportTableModel model;
    public boolean closedPosHidden;
    public int closedPosColumn;
    int frozenColumns;
    int firstAggregateColumnIndex;
    int secondAggregateColumnIndex;
    private final transient ReportConfig reportConfig;
    transient TotalReport totalReport;


    public TotalReportOutputPane(TotalReport totalReport) throws NoSuchFieldException, IllegalAccessException {
        super();
        setVisible(false);
        this.totalReport = totalReport;
        this.model = totalReport.getReportTableModel();
        this.reportConfig = totalReport.getReportConfig();
        this.frozenColumns = 0; //always start with no frozen columns to correct display issues
        this.closedPosHidden = totalReport.closedPosHidden;
        closedPosColumn = totalReport.getClosedPosColumn();
        this.firstSort = totalReport.getFirstSortColumn();
        this.secondSort = totalReport.getSecondSortColumn();
        this.firstAggregateColumnIndex = firstSort;
        this.secondAggregateColumnIndex = secondSort;
        // create the two tables
        lockedTable = new FormattedTable(model, totalReport.getColumnTypes(), totalReport.getViewHeader());
        scrollTable = new FormattedTable(model, totalReport.getColumnTypes(), totalReport.getViewHeader());
        lockedTable.setName("lockedTable");
        scrollTable.setName("scrollTable");
        lockedTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        scrollTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        setViewportView(scrollTable);

        // Put the locked-column tablePane in the row header
        JViewport thisViewport = new JViewport();
        thisViewport.setBackground(Color.white);
        thisViewport.setView(lockedTable);
        setRowHeader(thisViewport);

        // Put the header of the locked-column tablePane in the top left corner
        // of the scoll pane
        JTableHeader lockedHeader = lockedTable.getTableHeader();
        lockedHeader.setReorderingAllowed(false);
        lockedHeader.setResizingAllowed(false);
        setCorner(JScrollPane.UPPER_LEFT_CORNER, lockedHeader);

        scrollTable.getSelectionModel().setSelectionMode(
                ListSelectionModel.SINGLE_SELECTION);
        lockedTable.setSelectionModel(scrollTable.getSelectionModel());
        lockedTable.getTableHeader().setReorderingAllowed(false);
        lockedTable.getTableHeader().setResizingAllowed(false);
        lockedTable.putClientProperty("terminateEditOnFocusLost", Boolean.TRUE);
        scrollTable.putClientProperty("terminateEditOnFocusLost", Boolean.TRUE);

        // Remove all non-fixed columns from the fixed tablePane
        // (since frozen columns starts at zero)
        TableColumnModel lockedColumnModel = lockedTable.getColumnModel();
        while (lockedTable.getColumnCount() > frozenColumns) {
            lockedColumnModel.removeColumn(lockedColumnModel
                    .getColumn(frozenColumns));
        }

        // Add the fixed tablePane to the scroll pane
        lockedTable.setPreferredScrollableViewportSize(lockedTable
                .getPreferredSize());

        // set a new action for the tab key
        // todo search actions by action name (not by KeyStroke)
        final Action lockedTableNextColumnCellAction = getAction(lockedTable,
                KeyEvent.VK_TAB, 0);
        final Action scrollTableNextColumnCellAction = getAction(scrollTable,
                KeyEvent.VK_TAB, 0);
        final Action lockedTablePrevColumnCellAction = getAction(lockedTable,
                KeyEvent.VK_TAB, InputEvent.SHIFT_DOWN_MASK);
        final Action scrollTablePrevColumnCellAction = getAction(scrollTable,
                KeyEvent.VK_TAB, InputEvent.SHIFT_DOWN_MASK);
        setAction(lockedTable, "selectNextColumn",
                new LockedTableSelectNextColumnCellAction(
                        lockedTableNextColumnCellAction));
        setAction(scrollTable, "selectNextColumn",
                new ScrollTableSelectNextColumnCellAction(
                        scrollTableNextColumnCellAction));
        setAction(lockedTable, "selectPreviousColumn",
                new LockedTableSelectPreviousColumnCellAction(
                        lockedTablePrevColumnCellAction));
        setAction(scrollTable, "selectPreviousColumn",
                new ScrollTableSelectPreviousColumnCellAction(
                        scrollTablePrevColumnCellAction));
        setAction(lockedTable, "selectNextColumnCell",
                new LockedTableSelectNextColumnCellAction(
                        lockedTableNextColumnCellAction));
        setAction(scrollTable, "selectNextColumnCell",
                new ScrollTableSelectNextColumnCellAction(
                        scrollTableNextColumnCellAction));
        setAction(lockedTable, "selectPreviousColumnCell",
                new LockedTableSelectPreviousColumnCellAction(
                        lockedTablePrevColumnCellAction));
        setAction(scrollTable, "selectPreviousColumnCell",
                new ScrollTableSelectPreviousColumnCellAction(
                        scrollTablePrevColumnCellAction));
        setAction(scrollTable, "selectFirstColumn",
                new ScrollableSelectFirstColumnCellAction());
        setAction(lockedTable, "selectLastColumn",
                new LockedTableSelectLastColumnCellAction());
        setVisible(true);
    }

    public static Integer maxInt(Integer... values) {
        int retInt = Integer.MIN_VALUE;
        for (Integer value : values) {
            retInt = Math.max(retInt, value);
        }
        return retInt;
    }

    public static void setColumnOrder(JTable table, LinkedList<Integer> viewHeader) {
        Integer[] viewHeaderArray = viewHeader.toArray(new Integer[0]);
        TableColumnModel columnModel = table.getColumnModel();
        TableColumn[] column = new TableColumn[viewHeaderArray.length];
        LogController.logMessage(Level.FINE, String.format("Setting Column Order, length: %d", viewHeaderArray.length));
        for (int i = 0; i < column.length; i++) {
            column[i] = columnModel.getColumn(viewHeaderArray[i]);
        }

        while (columnModel.getColumnCount() > 0) {
            columnModel.removeColumn(columnModel.getColumn(0));
        }

        for (TableColumn aColumn : column) {
            columnModel.addColumn(aColumn);
        }
    }

    public static String getDisplayValueFromObject(Object o) throws Exception {
        String outputName;
        switch (o) {
            case MetricEntry metricEntry -> {
                Double value = metricEntry.getDisplayValue();
                outputName = value.equals(SecurityReport.UndefinedReturn) ? "" : value.toString();
            }
            case InvestmentAccountWrapper investmentAccountWrapper -> outputName = investmentAccountWrapper.getName();
            case SecurityAccountWrapper securityAccountWrapper -> outputName = securityAccountWrapper.getName();
            case SecurityTypeWrapper securityTypeWrapper -> outputName = securityTypeWrapper.getName();
            case SecuritySubTypeWrapper securitySubTypeWrapper -> outputName = securitySubTypeWrapper.getName();
            case CurrencyWrapper currencyWrapper -> outputName = currencyWrapper.getName();
            case null, default -> throw new Exception("invalid attempt to get name from object");
        }
        return outputName;
    }

    public static String replaceLineBreak(String inString) {
        String lineBreak = "\n";
        return inString.replace(lineBreak, " ");
    }

    public static void createAndShowTable(TotalReport totalReport) throws NoSuchFieldException, IllegalAccessException {
        final TotalReportOutputPane thisTable = new TotalReportOutputPane(totalReport);
        final TotalReportOutputFrame outerFrame = new TotalReportOutputFrame(thisTable, totalReport.getReportTitle());
        outerFrame.showFrame();
    }

    public void adjustColumnPreferredWidths(JTable table, ColSizeOption option) {
        // strategy - get max width for cells in column and
        // make that the preferred width
        TableColumnModel columnModel = table.getColumnModel();

        for (int col = 0; col < table.getColumnCount(); col++) {
            int maxwidth = 0;
            int fontSizeIncrease = 1;
            for (int row = 0; row < table.getRowCount(); row++) {
                TableCellRenderer rend = table.getCellRenderer(row, col);
                Object value = table.getValueAt(row, col);
                Component comp = rend.getTableCellRendererComponent(table,
                        value, false, false, row, col);

                int increasedWidth = 0;
                int preferredWidth = comp.getPreferredSize().width;

                // workaround--getPreferredSize insufficient for (at least some)
                // numbers, so set width based on larger font size
                if (value instanceof MetricEntry) {
                    JLabel comp1 = (JLabel) comp;
                    Font f1 = new Font(comp1.getFont().getName(), comp1
                            .getFont().getStyle(),
                            comp1.getFont().getSize() + fontSizeIncrease);
                    comp1.setFont(f1);
                    increasedWidth = comp1.getPreferredSize().width;
                }
                // set to maximum of all obtained widths
                maxwidth = maxInt(maxwidth, preferredWidth, increasedWidth);
            } // for row
            // following code resizes columns to the maximmum of header and
            // contents
            TableColumn column = columnModel.getColumn(col);
            switch (option) {

                case MAXCONTCOLRESIZE:

                    TableCellRenderer headerRenderer = column.getHeaderRenderer();
                    if (headerRenderer == null) {
                        headerRenderer = table.getTableHeader()
                                .getDefaultRenderer();
                    }
                    Object headerValue = column.getHeaderValue();
                    Component headerComp = headerRenderer
                            .getTableCellRendererComponent(table, headerValue,
                                    false, false, -1, col); // changed to -1
                    int headerWidth = headerComp.getPreferredSize().width;
                    maxwidth = Math.max(maxwidth, headerWidth);
                    column.setPreferredWidth(maxwidth);
                    break;
                case MAXCONTRESIZE:
                    column.setPreferredWidth(maxwidth);
                    break;
                case NORESIZE:
                    break;
                default:
            }

        } // for col
    }

    public String[] getAllColumnNames() {

        String[] columnNames = new String[model.getColumnCount()];
        for (int i = 0; i < model.getColumnCount(); i++) {
            columnNames[i] = model.getColumnName(i);
        }
        return columnNames;
    }

    private void setAction(JComponent component, String name, Action action) {
        component.getActionMap().put(name, action);
    }

    private Action getAction(JComponent component, int keyCode, int modifiers) {
        final KeyStroke keyStroke = KeyStroke.getKeyStroke(keyCode, modifiers);
        Object object = component.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).get(keyStroke);
        if (object == null) {
            if (component.getParent() instanceof JComponent) {
                return getAction((JComponent) component.getParent(), keyCode,
                        modifiers);
            } else {
                return null;
            }
        } else {
            return scrollTable.getActionMap().get(object);
        }
    }

    protected int nextRow(JTable table) {
        int row = table.getSelectedRow() + 1;
        if (row == table.getRowCount()) {
            row = 0;
        }
        return row;
    }

    private int previousRow(JTable table) {
        int row = table.getSelectedRow() - 1;
        if (row == -1) {
            row = table.getRowCount() - 1;
        }
        return row;
    }

    public final void setFrozenColumns(final int numFrozenColumns) {

        rearrangeColumns(numFrozenColumns);
        frozenColumns = numFrozenColumns;
    }

    private void rearrangeColumns(final int numFrozenColumns) {
        TableColumnModel scrollColumnModel = scrollTable.getColumnModel();
        TableColumnModel lockedColumnModel = lockedTable.getColumnModel();
        if (frozenColumns < numFrozenColumns) {
            // move columns from scrollable to fixed tablePane
            for (int i = frozenColumns; i < numFrozenColumns; i++) {
                TableColumn column = scrollColumnModel.getColumn(0);
                lockedColumnModel.addColumn(column);
                scrollColumnModel.removeColumn(column);
            }
            lockedTable.setPreferredScrollableViewportSize(lockedTable
                    .getPreferredSize());
        } else if (frozenColumns > numFrozenColumns) {
            // move columns from fixed to scrollable tablePane
            for (int i = numFrozenColumns; i < frozenColumns; i++) {
                TableColumn column = lockedColumnModel
                        .getColumn(lockedColumnModel.getColumnCount() - 1);
                scrollColumnModel.addColumn(column);
                scrollColumnModel.moveColumn(
                        scrollColumnModel.getColumnCount() - 1, 0);
                lockedColumnModel.removeColumn(column);
            }
            lockedTable.setPreferredScrollableViewportSize(lockedTable
                    .getPreferredSize());
        }
    }

    public ReportTableModel getModel(){
        return model;
    }

    public ReportConfig getReportConfig() {
        return reportConfig;
    }

    class ClosedValueRowFilter extends RowFilter<ReportTableModel, Integer> {
        @Override
        @SuppressWarnings("unchecked")
        public boolean include(Entry<? extends ReportTableModel, ? extends Integer> entry) {
            boolean result = false;

            ReportTableModel reportTableModel = entry.getModel();
            int row = entry.getIdentifier();
            Object object = reportTableModel.getValueAt(row, closedPosColumn);
            MetricEntry<Number> metricEntry;
            if (object instanceof MetricEntry) {
                metricEntry = (MetricEntry<Number>) object;
                result = metricEntry.getDisplayValue() != 0.0;
            }
            return result;
        }
    }

    public void sortRows() {

        TableRowSorter<ReportTableModel> rowSorter = new TableRowSorter<>(this.model);
        // apply row sorter
        if (closedPosHidden) {
            RowFilter<ReportTableModel, Integer> rf = new ClosedValueRowFilter();
            rowSorter.setRowFilter(rf);
        } else {
            rowSorter.setRowFilter(null);
        }

        // apply custom comparator for 1st 5 rows (Strings)
        // IMPORTANT! Must implement comparator before Sortkeys!
        rowSorter.setComparator(0, objectComp);
        rowSorter.setComparator(1, objectComp);
        rowSorter.setComparator(2, objectComp);
        rowSorter.setComparator(3, objectComp);
        rowSorter.setComparator(4, objectComp);

        // Apply sortKeys
        List<RowSorter.SortKey> sortKeys = new ArrayList<>();
        sortKeys.add(new RowSorter.SortKey(firstSort, firstOrder));
        sortKeys.add(new RowSorter.SortKey(secondSort, secondOrder));
        sortKeys.add(new RowSorter.SortKey(thirdSort, thirdOrder));
        rowSorter.setSortKeys(sortKeys);

        this.scrollTable.setRowSorter(rowSorter);
        this.lockedTable.setRowSorter(rowSorter);
        setSortedTableHeader();
    }

    public void sortRows(Point loc) {
        // Create and set up the window.
        JFrame frame = new JFrame("Row Sort");

        // Create and set up the content pane.
        JComponent newContentPane = new RowSortGui(this, frame);
        newContentPane.setOpaque(true); // content panes must be opaque
        frame.setContentPane(newContentPane);
        // Display the window.
        frame.pack();
        loc.x = loc.x + 75; // moved spawned window to right
        loc.y = loc.y + 75; // moved spawned window down
        frame.setLocation(loc);
        frame.setVisible(true);
    }

    public void setSortedTableHeader() {

        for (int i = 0; i < model.getColumnCount(); i++) {
            int viewCol;
            TableColumn column;
            if (i < this.frozenColumns) {
                viewCol = this.lockedTable.convertColumnIndexToView(i);
                if (viewCol != -1) {
                    column = this.lockedTable.getColumnModel().getColumn(viewCol);

                    if (i == firstSort
                            && (firstOrder == SortOrder.ASCENDING || firstOrder == SortOrder.DESCENDING)) {
                        column.setHeaderRenderer(new ArrowHeader(this.lockedTable,
                                1, firstOrder == SortOrder.DESCENDING));
                    } else if (i == secondSort
                            && (secondOrder == SortOrder.ASCENDING || secondOrder == SortOrder.DESCENDING)) {
                        column.setHeaderRenderer(new ArrowHeader(this.lockedTable,
                                2, secondOrder == SortOrder.DESCENDING));
                    } else if (i == thirdSort
                            && (thirdOrder == SortOrder.ASCENDING || thirdOrder == SortOrder.DESCENDING)) {
                        column.setHeaderRenderer(new ArrowHeader(this.lockedTable,
                                3, thirdOrder == SortOrder.DESCENDING));
                    } else {
                        column.setHeaderRenderer(new MultiLineHeaderRenderer());
                    }
                }
            } else {
                viewCol = this.scrollTable.convertColumnIndexToView(i);
                if (viewCol != -1) {
                    column = this.scrollTable.getColumnModel().getColumn(viewCol);

                    if (i == firstSort
                            && (firstOrder == SortOrder.ASCENDING || firstOrder == SortOrder.DESCENDING)) {
                        column.setHeaderRenderer(new ArrowHeader(this.scrollTable,
                                1, firstOrder == SortOrder.DESCENDING));
                    } else if (i == secondSort
                            && (secondOrder == SortOrder.ASCENDING || secondOrder == SortOrder.DESCENDING)) {
                        column.setHeaderRenderer(new ArrowHeader(this.scrollTable,
                                2, secondOrder == SortOrder.DESCENDING));
                    } else if (i == thirdSort
                            && (thirdOrder == SortOrder.ASCENDING || thirdOrder == SortOrder.DESCENDING)) {
                        column.setHeaderRenderer(new ArrowHeader(this.scrollTable,
                                3, thirdOrder == SortOrder.DESCENDING));
                    } else {
                        column.setHeaderRenderer(new MultiLineHeaderRenderer());
                    }
                }
            }

        }

        adjustColumnPreferredWidths(this.scrollTable, ColSizeOption.MAXCONTCOLRESIZE);
        adjustColumnPreferredWidths(this.lockedTable, ColSizeOption.MAXCONTCOLRESIZE);
        this.scrollTable.getTableHeader().repaint();
        this.lockedTable.getTableHeader().repaint();
    }

    @SuppressWarnings("unchecked")
    public void switchReturnType(){
        Class<? extends ExtractorReturnBase> clazzToChange;
        for(int j = 0; j < model.getColumnCount(); j++){
            Object obj = model.getValueAt(0, j);
            if(obj instanceof MetricEntry){
                MetricEntry<Number> metricEntry = (MetricEntry<Number>) obj;
                ExtractorBase<?> extractor = metricEntry.extractor;
                if(extractor != null){
                    if(extractor.getClass().equals(ExtractorModifiedDietzReturn.class) ){
                        clazzToChange = ExtractorModifiedDietzReturn.class;
                        changeColumnMetricEntry(clazzToChange, j, metricEntry);
                    } else if (extractor.getClass().equals(ExtractorOrdinaryReturn.class)){
                        clazzToChange = ExtractorOrdinaryReturn.class;
                        changeColumnMetricEntry(clazzToChange, j, metricEntry);
                    }
                }
            }
        }
        model.fireTableDataChanged();
        sortRows();
    }

    @SuppressWarnings("unchecked")
    private void changeColumnMetricEntry(Class<? extends ExtractorReturnBase> clazzToChange,
                                         int j, MetricEntry<Number> metricEntry) {
        Object obj;
        switchExtractor(metricEntry, clazzToChange);
        for(int i = 1; i < model.getRowCount(); i++){
            obj = model.getValueAt(i, j);
            if(obj instanceof MetricEntry){
                metricEntry = (MetricEntry) obj;
                switchExtractor(metricEntry, clazzToChange);
            }
        }
    }

    private void switchExtractor(MetricEntry<Number> metricEntry, Class<? extends ExtractorReturnBase> extractorToSwitch) {
        assert metricEntry.extractor != null;
        if(extractorToSwitch.equals(ExtractorModifiedDietzReturn.class)){
            assert metricEntry.extractor instanceof ExtractorModifiedDietzReturn;
            ExtractorModifiedDietzReturn extractorModifiedDietzReturn = (ExtractorModifiedDietzReturn) metricEntry.extractor;
            ExtractorOrdinaryReturn extractorOrdinaryReturn = new ExtractorOrdinaryReturn(extractorModifiedDietzReturn);
            metricEntry.extractor = extractorOrdinaryReturn;
            metricEntry.value = extractorOrdinaryReturn.getResult();

        } else if(extractorToSwitch.equals(ExtractorOrdinaryReturn.class)){
            assert metricEntry.extractor instanceof ExtractorOrdinaryReturn;
            ExtractorOrdinaryReturn extractorOrdinaryReturn = (ExtractorOrdinaryReturn) metricEntry.extractor;
            ExtractorModifiedDietzReturn extractorModifiedDietzReturn = new ExtractorModifiedDietzReturn(extractorOrdinaryReturn);
            metricEntry.extractor = extractorModifiedDietzReturn;
            metricEntry.value = extractorModifiedDietzReturn.getResult();
        }
    }



    public void copyTableToClipboard() throws Exception {
        StringBuilder copyIn = new StringBuilder();
        int numCols = scrollTable.getViewHeader().size(); //allows for removal of columns
        int numRowsView = lockedTable.getRowCount(); // allows for filtering of closed positions

        String columnHeader;
        for (int j = 0; j < numCols; j++) {
            if (j < frozenColumns) {

                columnHeader = replaceLineBreak(lockedTable.getColumnName(j));
            } else {
                columnHeader = replaceLineBreak(scrollTable.getColumnName(j - frozenColumns));
            }
            copyIn.append(columnHeader);
            if (j < numCols - 1) {
                copyIn.append("\t");
            }
        }
        copyIn.append("\n");

        for (int i = 0; i < numRowsView; i++) {
            for (int j = 0; j < numCols; j++) {
                int modelRow;
                int modelCol;
                if (j < frozenColumns) {
                    modelRow = lockedTable.convertRowIndexToModel(i);
                    modelCol = lockedTable.convertColumnIndexToModel(j);
                    copyIn.append(lockedTable.getDisplayStringFromCell(modelRow, modelCol));

                } else {
                    modelRow = scrollTable.convertRowIndexToModel(i);
                    modelCol = scrollTable.convertColumnIndexToModel(j - frozenColumns);
                    copyIn.append(scrollTable.getDisplayStringFromCell(modelRow, modelCol));
                }
                if (j < numCols - 1)
                    copyIn.append("\t");

            }
            copyIn.append("\n");
        }
        StringSelection stsel = new StringSelection(copyIn.toString());
        Clipboard system = Toolkit.getDefaultToolkit().getSystemClipboard();

        system.setContents(stsel, stsel);
    }

    transient Comparator<Object> objectComp = (o1, o2) -> {
        try {
            String o1Str = getDisplayValueFromObject(o1);
            String o2Str = getDisplayValueFromObject(o2);
            LinkedList<String> endStrings = new LinkedList<>();
            endStrings.add("CASH");
            endStrings.add("CASH  "); //handles aggregate by ticker
            endStrings.add("-ALL");
            endStrings.add("-ALL "); //handles last row of all-aggregate

            int o1Rank = -1;
            int o2Rank = -1;

            for (String string : endStrings) {
                if (o1Str.endsWith(string))
                    o1Rank = endStrings.indexOf(string);
                if (o2Str.endsWith(string))
                    o2Rank = endStrings.indexOf(string);
            }

            if (o1Rank == o2Rank) {
                return o1Str.compareTo(o2Str);
            } else {
                return o1Rank - o2Rank;
            }
        } catch (Exception e) {
            LogController.logException(e, "Error on Report Output Pane: ");
            JOptionPane.showMessageDialog(TotalReportOutputPane.this,
                    "Error! See " + ReportControlPanel.getOutputDirectoryPath() +
                            " for details", "Error", JOptionPane.ERROR_MESSAGE);
            return 0;

        }
    };

    public enum ColType {OBJECT, DOUBLE0, DOUBLE2, DOUBLE3, PERCENT1}

    public enum ColSizeOption {NORESIZE, MAXCONTRESIZE, MAXCONTCOLRESIZE}


    public static final class MetricEntryNumberRenderer extends DefaultTableCellRenderer {
        @Serial
        private static final long serialVersionUID = -1219099935272135292L;


        private final DecimalFormat doubleFormat = new DecimalFormat("#,##0;(#,##0)");

        public MetricEntryNumberRenderer(int minDecPlaces, int maxDecPlaces) {
            super();
            this.setName("Number(" + maxDecPlaces + "," + maxDecPlaces + ")");

            doubleFormat.setMinimumFractionDigits(minDecPlaces);
            doubleFormat.setMaximumFractionDigits(maxDecPlaces);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus,
                                                       int row, int column) {
            Component cell = super.getTableCellRendererComponent(table,
                    value, isSelected, hasFocus, row, column);

            if (value instanceof MetricEntry) {
                Double d = ((MetricEntry) value).getDisplayValue();
                String text;
                if (d == null || d.isNaN() || d.equals(SecurityReport.UndefinedReturn)) {
                    text = "";
                } else if (d == 0.0) {
                    text = "-";
                } else {
                    text = doubleFormat.format(d);
                }

                JLabel renderedLabel = (JLabel) cell;
                if(d != null){
                    renderedLabel.setHorizontalAlignment(d == 0.0 ? SwingConstants.CENTER
                            : SwingConstants.RIGHT);
                    renderedLabel.setForeground(d < 0 ? Color.RED : Color.BLACK);
                }
                renderedLabel.setText(text);
            }
            return cell;
        }
    }

    static class ObjectTableCellRenderer extends DefaultTableCellRenderer {
        @Serial
        private static final long serialVersionUID = -7152447480811826901L;

        public ObjectTableCellRenderer() {
            super();
            setName("Object");
        }

        @Override
        public Component getTableCellRendererComponent(JTable table,
                                                       Object value, boolean isSelected, boolean hasFocus,
                                                       int row, int column) {
            Component cell = null;
            try {
                String objectName = getDisplayValueFromObject(value);
                cell = super.getTableCellRendererComponent(table,
                        objectName, isSelected, hasFocus, row, column);

                if (objectName != null) {
                    JLabel renderedLabel = (JLabel) cell;
                    renderedLabel.setHorizontalAlignment(SwingConstants.LEFT);
                    renderedLabel.setForeground(Color.BLACK);
                    renderedLabel.setFont(new Font(renderedLabel.getFont()
                            .getName(), Font.PLAIN, renderedLabel.getFont()
                            .getSize()));
                }
            } catch (Exception e) {
                LogController.logException(e, "Error on Report Output Pane: ");
                JOptionPane.showMessageDialog(this, "Error! See " +
                                ReportControlPanel.getOutputDirectoryPath() + " for details",
                        "Error", JOptionPane.ERROR_MESSAGE);
            }
            return cell;
        }
    }

    static class PercentTableCellRenderer extends DefaultTableCellRenderer {
        @Serial
        private static final long serialVersionUID = 8743892160294317814L;

        private final DecimalFormat pctFormat = new DecimalFormat("#.#%");

        public PercentTableCellRenderer(int minDecPlaces, int maxDecPlaces) {
            super();
            setName("Percent");

            pctFormat.setMinimumFractionDigits(minDecPlaces);
            pctFormat.setMaximumFractionDigits(maxDecPlaces);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                       boolean hasFocus, int row, int column) {
            Component cell = super.getTableCellRendererComponent(table,
                    value, isSelected, hasFocus, row, column);
            if (value instanceof MetricEntry) {
                Double d = ((MetricEntry) value).getDisplayValue();
                String text = d == null || d.isNaN() || d.equals(SecurityReport.UndefinedReturn) ? "" : pctFormat.format(d);

                JLabel renderedLabel = (JLabel) cell;
                renderedLabel.setHorizontalAlignment(SwingConstants.RIGHT);
                renderedLabel.setText(text);
                if(d!= null) renderedLabel.setForeground(d < 0 ? Color.RED : Color.BLACK);
            }
            return cell;
        }
    }

    private final class LockedTableSelectLastColumnCellAction extends
            AbstractAction {
        @Serial
        private static final long serialVersionUID = -7498538141653234651L;

        private LockedTableSelectLastColumnCellAction() {
            super();
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            if (e.getSource() == lockedTable) {
                lockedTable.transferFocus();
            }
            scrollTable.changeSelection(scrollTable.getSelectedRow(),
                    scrollTable.getColumnCount() - 1, false, false);
        }
    }

    private final class ScrollableSelectFirstColumnCellAction extends
            AbstractAction {
        @Serial
        private static final long serialVersionUID = 7004700943224579977L;

        private ScrollableSelectFirstColumnCellAction() {
            super();
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            if (e.getSource() == scrollTable) {
                scrollTable.transferFocusBackward();
            }
            lockedTable.changeSelection(lockedTable.getSelectedRow(), 0, false,
                    false);
        }
    }

    private final class LockedTableSelectNextColumnCellAction extends
            AbstractAction {
        @Serial
        private static final long serialVersionUID = 2820241653505999596L;

        private transient final Action lockedTableNextColumnCellAction;

        private LockedTableSelectNextColumnCellAction(
                Action lockedTableNextColumnCellAction) {
            super();
            this.lockedTableNextColumnCellAction = lockedTableNextColumnCellAction;
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            if (lockedTable.getSelectedColumn() == lockedTable.getColumnCount() - 1) {
                lockedTable.transferFocus();
                scrollTable.changeSelection(lockedTable.getSelectedRow(), 0,
                        false, false);
            } else {
                lockedTableNextColumnCellAction.actionPerformed(e);
            }
        }
    }

    private final class ScrollTableSelectNextColumnCellAction extends
            AbstractAction {
        @Serial
        private static final long serialVersionUID = 135412121274189994L;

        private transient final Action scrollTableNextColumnCellAction;

        private ScrollTableSelectNextColumnCellAction(
                Action scrollTableNextColumnCellAction) {
            super();
            this.scrollTableNextColumnCellAction = scrollTableNextColumnCellAction;
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            if (scrollTable.getSelectedColumn() == scrollTable.getColumnCount() - 1) {
                scrollTable.transferFocusBackward();
                lockedTable.changeSelection(nextRow(scrollTable), 0, false,
                        false);
            } else {
                scrollTableNextColumnCellAction.actionPerformed(e);
            }
        }
    }

    private final class ScrollTableSelectPreviousColumnCellAction extends
            AbstractAction {
        @Serial
        private static final long serialVersionUID = -6293074638490971318L;
        private transient final Action scrollTablePrevColumnCellAction;

        private ScrollTableSelectPreviousColumnCellAction(
                Action scrollTablePrevColumnCellAction) {
            super();
            this.scrollTablePrevColumnCellAction = scrollTablePrevColumnCellAction;
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            if (scrollTable.getSelectedColumn() == 0) {
                scrollTable.transferFocusBackward();
                lockedTable.changeSelection(scrollTable.getSelectedRow(),
                        lockedTable.getColumnCount() - 1, false, false);
            } else {
                scrollTablePrevColumnCellAction.actionPerformed(e);
            }
        }
    }

    private final class LockedTableSelectPreviousColumnCellAction extends
            AbstractAction {
        @Serial
        private static final long serialVersionUID = -290336911634305126L;

        private transient final Action lockedTablePrevColumnCellAction;

        private LockedTableSelectPreviousColumnCellAction(
                Action lockedTablePrevColumnCellAction) {
            super();
            this.lockedTablePrevColumnCellAction = lockedTablePrevColumnCellAction;
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            if (lockedTable.getSelectedColumn() == 0) {
                lockedTable.transferFocus();
                scrollTable.changeSelection(previousRow(scrollTable),
                        scrollTable.getColumnCount() - 1, false, false);
            } else {
                lockedTablePrevColumnCellAction.actionPerformed(e);
            }
        }
    }

    class FormattedTable extends JTable {

        @Serial
        private static final long serialVersionUID = -3558604379360713628L;
        private final LinkedList<Integer> viewHeader;


        private FormattedTable(TableModel model, ColType[] colFormats,
                               LinkedList<Integer> viewHeader) {
            super(model);
            this.viewHeader = viewHeader;
            TableColumn tableColumn;
            for (int i = 0; i < colFormats.length; i++) {
                ColType colType = colFormats[i];
                tableColumn = this.getColumnModel().getColumn(i);
                TableCellRenderer double0Render = new MetricEntryNumberRenderer(0, 0);
                TableCellRenderer double2Render = new MetricEntryNumberRenderer(2, 2);
                TableCellRenderer double3Render = new MetricEntryNumberRenderer(3, 3);
                TableCellRenderer percent1Render = new PercentTableCellRenderer(1, 1);
                TableCellRenderer defaultRender = new ObjectTableCellRenderer();
                switch (colType) {
                    case DOUBLE0 -> tableColumn.setCellRenderer(double0Render);
                    case DOUBLE2 -> tableColumn.setCellRenderer(double2Render);
                    case DOUBLE3 -> tableColumn.setCellRenderer(double3Render);
                    case PERCENT1 -> tableColumn.setCellRenderer(percent1Render);
                    default -> tableColumn.setCellRenderer(defaultRender);
                }
            }
            setColumnOrder(this, viewHeader);
            this.addMouseListener(new FormattedTableMouseAdapter());
        }


        String getDisplayStringFromCell(int row, int col) throws Exception {
            Object obj = model.getValueAt(row, col);
            return getDisplayValueFromObject(obj);
        }

        public TotalReport.ReportTableModel getReportTableModel() {
            return model;
        }

        public LinkedList<Integer> getViewHeader(){ return viewHeader;}


        @Override
        @SuppressWarnings("unchecked")
        public Component prepareRenderer(TableCellRenderer renderer, int row, int column) {
            Component c = super.prepareRenderer(renderer, row, column);
            // Color row based on a cell value--overrides TableCellRenders in constructor
            try {
                if (!isRowSelected(row)) {
                    c.setBackground(getBackground());
                    int modelRow = convertRowIndexToModel(row);
                    Object aggObj1 = getModel().getValueAt(modelRow, firstAggregateColumnIndex);
                    Object aggObj2 = getModel().getValueAt(modelRow, secondAggregateColumnIndex);

                    String aggType1 = getDisplayValueFromObject(aggObj1);
                    String aggType2 = getDisplayValueFromObject(aggObj2);
                    MetricEntry<Number> endValueMetricEntry = (MetricEntry<Number>) getModel().getValueAt(modelRow, closedPosColumn);
                    String typeAggregateEnd = "-ALL";
                    String nameAggregateEnd = " ";

                    if (aggType1.endsWith(nameAggregateEnd) && aggType2.endsWith(nameAggregateEnd)) {
                        c.setBackground(LIGHT_LIGHT_GRAY);
                    }
                    if (aggType1.endsWith(nameAggregateEnd + " ") && aggType2.endsWith(typeAggregateEnd)) {
                        c.setBackground(Color.lightGray);
                    }
                    if (aggType1.endsWith(typeAggregateEnd) && aggType2.endsWith(nameAggregateEnd)) {
                        c.setBackground(new Color(144, 238, 144));
                    }
                    if (aggType1.endsWith(typeAggregateEnd) && aggType2.endsWith(typeAggregateEnd)) {
                        c.setBackground(Color.GREEN);
                    }
                    if (endValueMetricEntry.getDisplayValue() == 0.0) {
                        c.setForeground(new Color(100, 100, 100));
                    } else {
                        c.setForeground(c.getForeground());
                    }
                } else {
                    c.setBackground(new Color(255, 255, 153));  // Light yellow
                }
            } catch (Exception e) {
                LogController.logException(e, "Error on Report Output Pane: ");
                JOptionPane.showMessageDialog(c, "Error! See " + ReportControlPanel.getOutputDirectoryPath() +
                        " for details", "Error", JOptionPane.ERROR_MESSAGE);
            }

            return c;
        }

        class FormattedTableMouseAdapter extends MouseAdapter {
            @Override
            @SuppressWarnings("unchecked")
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    JTable target = (JTable) e.getSource();
                    int rowViewIndex = target.getSelectedRow();
                    int rowModelIndex = convertRowIndexToModel(rowViewIndex);
                    int columnViewIndex = target.getSelectedColumn();
                    int columnModelIndex = convertColumnIndexToModel(columnViewIndex);

                    if (columnModelIndex <= 4) {
                        Object obj = model.getValueAt(rowModelIndex, 1);
                        if (obj instanceof SecurityAccountWrapper securityAccountWrapper) {
                            if (securityAccountWrapper.isTradeable()) {
                                SecurityAccountEditorForm.createAndShowSecurityEditorForm(securityAccountWrapper, FormattedTable.this);
                            }
                        }
                    } else {
                        Object obj = model.getValueAt(rowModelIndex, columnModelIndex);
                        if (obj instanceof MetricEntry) {
                            MetricEntry<Number> metricEntry = (MetricEntry<Number>) obj;
                            ExtractorReturnBase extractor;
                            if(metricEntry.extractor instanceof ExtractorReturnBase){
                                extractor = (ExtractorReturnBase) metricEntry.extractor;
                                Rectangle rectangle = FormattedTable.this.getCellRect(0, columnViewIndex, true);
                                Point screenLocation = FormattedTable.this.getLocationOnScreen();
                                Point displayPoint = new Point(screenLocation.x+ rectangle.x, screenLocation.y + rectangle.y);

                                ReturnsAuditDisplayFrame.showReturnsAuditDisplay(extractor,
                                        displayPoint, FormattedTable.this.getHeight()
                                );
                            }
                        }

                    }
                }
            }
        }
    }

    private class RowSortGui extends JPanel {
        @Serial
        private static final long serialVersionUID = -8349629256510555172L;

        public TotalReportOutputPane tablePane;
        public JFrame enclosingFrame;

        public RowSortGui(TotalReportOutputPane thisTable, JFrame enclosingFrame) {
            tablePane = thisTable;
            this.enclosingFrame = enclosingFrame;
            String[] colNames = tablePane.getAllColumnNames();
            JPanel boxPanel = new JPanel();
            JComboBox<String> firstSortBox = new JComboBox<>(colNames);
            JComboBox<String> secondSortBox = new JComboBox<>(colNames);
            JComboBox<String> thirdSortBox = new JComboBox<>(colNames);
            JComboBox<SortOrder> firstOrderBox = new JComboBox<>(SortOrder.values());
            JComboBox<SortOrder> secondOrderBox = new JComboBox<>(SortOrder.values());
            JComboBox<SortOrder> thirdOrderBox = new JComboBox<>(SortOrder.values());
            // set defaults to previous values
            firstSortBox.setSelectedIndex(tablePane.firstSort);
            secondSortBox.setSelectedIndex(tablePane.secondSort);
            thirdSortBox.setSelectedIndex(tablePane.thirdSort);
            firstOrderBox.setSelectedItem(tablePane.firstOrder);
            secondOrderBox.setSelectedItem(tablePane.secondOrder);
            thirdOrderBox.setSelectedItem(tablePane.thirdOrder);

            JButton sortButton = new JButton("Sort Table");
            sortButton.addActionListener(e -> {
                tablePane.sortRows();
                enclosingFrame.dispose();
            });
            // set sorts
            firstSortBox.addActionListener(e -> {
                @SuppressWarnings("unchecked")
                JComboBox<String> cb = (JComboBox<String>) e.getSource();
                firstSort = cb.getSelectedIndex();
            });
            secondSortBox.addActionListener(e -> {
                @SuppressWarnings("unchecked")
                JComboBox<String> cb = (JComboBox<String>) e.getSource();
                secondSort = cb.getSelectedIndex();
            });
            thirdSortBox.addActionListener(e -> {
                @SuppressWarnings("unchecked")
                JComboBox<String> cb = (JComboBox<String>) e.getSource();
                thirdSort = cb.getSelectedIndex();
            });
            // set orders within sorts
            firstOrderBox.addActionListener(e -> {
                @SuppressWarnings("unchecked")
                JComboBox<String> cb = (JComboBox<String>) e.getSource();
                firstOrder = (SortOrder) cb.getSelectedItem();
            });
            secondOrderBox.addActionListener(e -> {
                @SuppressWarnings("unchecked")
                JComboBox<String> cb = (JComboBox<String>) e.getSource();
                secondOrder = (SortOrder) cb.getSelectedItem();
            });
            thirdOrderBox.addActionListener(e -> {
                @SuppressWarnings("unchecked")
                JComboBox<String> cb = (JComboBox<String>) e.getSource();
                thirdOrder = (SortOrder) cb.getSelectedItem();
            });

            // build frame
            // set layouts
            boxPanel.setLayout(new GridLayout(3, 2));
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
            boxPanel.add(firstSortBox);
            boxPanel.add(firstOrderBox);
            boxPanel.add(secondSortBox);
            boxPanel.add(secondOrderBox);
            boxPanel.add(thirdSortBox);
            boxPanel.add(thirdOrderBox);
            add(boxPanel);
            sortButton.setAlignmentX(Component.CENTER_ALIGNMENT);
            add(sortButton);

        }

    }

    static class ArrowHeader extends JLabel implements TableCellRenderer {
        @Serial
        private static final long serialVersionUID = -1175683155743555445L;

        JTable table;
        int column;
        int sortPriority;
        boolean descending;
        transient TableCellRenderer renderer;

        public ArrowHeader(JTable table, int sortPriority, boolean descending) {
            this.table = table;
            this.sortPriority = sortPriority;
            this.descending = descending;
            this.renderer = table.getCellRenderer(-1, column);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table,
                                                       Object value, boolean isSelected, boolean hasFocus, int row,
                                                       int column) {
            this.setHorizontalTextPosition(JLabel.LEFT);
            setText(value.toString());
            Font f = new Font(this.getFont().getName(), Font.PLAIN, this
                    .getFont().getSize());
            this.setFont(f);
            this.setForeground(Color.red);
            this.setBackground(setSortColor(sortPriority)); // doesn't appear

            JLabel newLabel = new JLabel(value.toString());
            newLabel.setHorizontalTextPosition(JLabel.LEFT);
            newLabel.setForeground(Color.red);
            newLabel.setBackground(setSortColor(sortPriority)); // doesn't appear

            newLabel.setToolTipText("Sort Priority: " + sortPriority
                    + " Order: " + (descending ? "Descending" : "Ascending"));
            newLabel.setIcon(createArrow(descending, this.getFont().getSize(),
                    sortPriority));
            return newLabel;
        }

        private Color setSortColor(int sortColor) {
            if (sortColor == 1) {
                return new Color(100, 100, 100);
            } else if (sortColor == 2) {
                return new Color(160, 160, 160);
            } else {
                return new Color(220, 220, 220);
            }
        }

        private Icon createArrow(boolean descending, int size, int priority) {
            return new Arrow(descending, size, priority);
        }

        private static class Arrow implements Icon {

            private final boolean descending;
            private final int priority;
            private int size;

            public Arrow(boolean descending, int size, int priority) {
                this.descending = descending;
                this.size = size;
                this.priority = priority;
            }

            @Override
            public void paintIcon(Component c, Graphics g, int x, int y) {

                // Override base size with a value calculated from the
                // component's font.
                updateSize(c);

                Color color = c == null ? Color.BLACK : c.getForeground();
                g.setColor(color);

                int npoints = 3;
                int[] xpoints = new int[]{0, size / 2, size};
                int[] ypoints = descending ? new int[]{0, size, 0}
                        : new int[]{size, 0, size};

                Polygon triangle = new Polygon(xpoints, ypoints, npoints);

                // Center icon vertically within the column heading label.
                int dy = 0;
                if (c != null) dy = (c.getHeight() - size) / 2;
                g.translate(x, dy);
                g.drawPolygon(triangle);
                g.fillPolygon(triangle);
                g.translate(-x, -dy);

            }

            @Override
            public int getIconWidth() {
                return size;
            }

            @Override
            public int getIconHeight() {
                return size;
            }

            private void updateSize(Component c) {
                if (c != null) {
                    FontMetrics fm = c.getFontMetrics(c.getFont());
                    int baseHeight = fm.getAscent();

                    // In a compound sort, make each succesive triangle 20%
                    // smaller than the previous one.
                    size = (int) (baseHeight * 3 / 4 * Math.pow(0.8, priority));
                }
            }
        }
    } // end ArrowHeader Class

    static class MultiLineHeaderRenderer extends JList<String> implements TableCellRenderer {
        @Serial
        private static final long serialVersionUID = 8768337573084136892L;

        public MultiLineHeaderRenderer() {
            setOpaque(true);
            setForeground(UIManager.getColor("TableHeader.foreground"));
            setBackground(UIManager.getColor("TableHeader.background"));
            setBorder(UIManager.getBorder("TableHeader.cellBorder"));
            ListCellRenderer<? super String> renderer = getCellRenderer();
            ((DefaultListCellRenderer) renderer).setHorizontalAlignment(JLabel.CENTER);
            setCellRenderer(renderer);
        }

        public Component getTableCellRendererComponent(JTable table, Object value,
                                                       boolean isSelected, boolean hasFocus, int row, int column) {
            setFont(table.getFont());
            String str = (value == null) ? "" : value.toString();
            BufferedReader br = new BufferedReader(new StringReader(str));
            String line;
            Vector<String> v = new Vector<>();
            try {
                while ((line = br.readLine()) != null) {
                    v.addElement(line);
                }
            } catch (IOException ex) {
                LogController.logException(ex, "Error in TotalOutputPane");
            }
            setListData(v);
            return this;
        }
    }// End MultiLineHeader

    //
    // }
} // end EnTable1 Class
