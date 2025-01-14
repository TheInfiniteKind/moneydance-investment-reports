/*
 * TotalReport.java
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

import com.moneydance.modules.features.invextension.CompositeReport.COMPOSITE_TYPE;
import com.moneydance.modules.features.invextension.TotalReportOutputPane.ColType;

import javax.swing.table.AbstractTableModel;
import java.io.Serial;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.logging.Level;
import java.util.stream.Collectors;


/**
 * Base class for collection of security reports and composite reports
 *
 * @author Dale Furrow
 * @version 1.0
 * @since 1.0
 */

public abstract class TotalReport {
    protected boolean closedPosHidden; // indicates if closed positions are hidden
    protected int numFrozenColumns; //indicate number of columns initially frozen
    AggregationController aggregationController;
    private HashSet<SecurityReport> securityReports;
    private HashSet<CompositeReport> compositeReports;
    private final DateRange dateRange;
    private final Boolean isHierarchy; // indicates if second aggregate is a subset of the first aggregate
    private final Boolean outputSingle; // indicates a composite report with only one security report will print
    private final LinkedList<String> modelHeader;
    private LinkedList<Integer> viewHeader;
    protected ReportConfig reportConfig;
    private BulkSecInfo currentInfo;
    private final ColType[] colTypes;

    public TotalReport(ReportConfig reportConfig, BulkSecInfo currentInfo, ColType[] colTypes,
                       LinkedList<String> modelHeader) throws Exception {
        this.reportConfig = reportConfig;
        this.currentInfo = currentInfo;
        this.aggregationController = reportConfig.getAggregationController();
        this.outputSingle = reportConfig.isOutputSingle();
        this.numFrozenColumns = reportConfig.getNumFrozenColumns();
        this.closedPosHidden = reportConfig.isClosedPosHidden();
        this.modelHeader = modelHeader;
        this.viewHeader = reportConfig.getViewHeader();
        this.colTypes = colTypes;
        this.dateRange = reportConfig.getDateRange();
        isHierarchy = aggregationController.isHierarchy();
        securityReports = new HashSet<>();
        compositeReports = new HashSet<>();

    }

    public static String getClassSimpleNameFromReportTypeName(String reportTypeName) throws Exception {
        if (reportTypeName.equals(TotalFromToReport.reportTypeName)) {
            return TotalFromToReport.class.getSimpleName();
        } else if (reportTypeName.equals(TotalSnapshotReport.reportTypeName)) {
            return TotalSnapshotReport.class.getSimpleName();
        } else {
            throw new Exception("unrecognized reportTypeName");
        }
    }

    @SuppressWarnings("unused")
    public HashSet<SecurityReport> getSecurityReports() {
        return securityReports;
    }
    @SuppressWarnings("unused")
    public HashSet<CompositeReport> getCompositeReports() {
        return compositeReports;
    }

    /**
     * fetches all security reports and composites
     *
     * @return array list of component reports
     */
    public ArrayList<ComponentReport> getReports() {
        ArrayList<ComponentReport> componentReports = new ArrayList<>();
        componentReports.addAll(securityReports);
        componentReports.addAll(compositeReports);
        return componentReports;
    }

    public DateRange getReportDate() {
        return dateRange;
    }

    /**
     * Generates array of report line objects
     *
     * @return report table 2d array
     */
    public Object[][] getReportTable() throws NoSuchFieldException, IllegalAccessException {
        if (securityReports.isEmpty()) {
            return new Object[0][0];
        } else {
            HashSet<ComponentReport> allReports = new HashSet<>(securityReports);
            if (outputSingle) {
                allReports.addAll(compositeReports);
            } else {
                allReports.addAll(compositeReports.stream()
                        .filter(compositeReport -> compositeReport
                                .getSecurityReports().size() > 1).collect(Collectors.toList()));
            }
            int i = 0;
            int cols = 0;
            Object[][] table = null;
            for (ComponentReport componentReport : allReports) {
                if (i == 0) {
                    cols = componentReport.toTableRow().length;
                    table = new Object[allReports.size()][cols];
                }
                Object[] row = componentReport.toTableRow();
                System.arraycopy(row, 0, table[i], 0, cols);
                i++;
            }
            return table;
        } // end else
    }

    /**
     * generates appropriate leaf-level Security Report
     *
     * @param securityAccountWrapper input security account wrapper
     * @param thisDateRange          input date range
     * @return leaf-level security report
     */
    public abstract SecurityReport getLeafSecurityReport(
            SecurityAccountWrapper securityAccountWrapper, DateRange thisDateRange);

    /**
     * Generates "All-Securities" Composite Report
     * @return composite for all security reports
     */
    public abstract CompositeReport getAllCompositeReport(DateRange dateRange,
                                                          AggregationController aggregationController);

    /**
     * Designates which column controls the "closed-position" report GUI function
     *
     * @return column index for position
     */
    public abstract int getClosedPosColumn();

    /**
     * Determines type of each column for GUI output.
     *
     * @return column type for a given column
     */
    public ColType[] getColumnTypes() {
        return colTypes;
    }

    /**
     * generates initial index of frozen column for GUI.
     *
     * @return initial index of frozen column
     */
    @SuppressWarnings("unused")
    public int getFrozenColumn() {
        return numFrozenColumns;
    }

    /**
     * Generates GUI Model Columns
     *
     * @return model header
     */
    public LinkedList<String> getModelHeader() {
        return modelHeader;
    }

    /**
     * Generates GUI Columns (reordered subset of Model columns)
     *
     * @return view header
     */
    public LinkedList<Integer> getViewHeader() {
        return viewHeader;
    }

    public void setViewHeader(LinkedList<Integer> viewHeader){
        this.viewHeader = viewHeader;
    }

    /**
     * writes view header to a string where integers are separated by commas
     *
     * @return string to be saved to preferences
     */
    public String writeViewHeaderToString() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < this.viewHeader.size(); i++) {
            sb.append(i == viewHeader.size() - 1 ? viewHeader.get(i) : viewHeader.get(i) + ",");
        }
        return sb.toString();
    }


    /**
     * Generates GUI title
     *
     * @return report title
     */
    public abstract String getReportTitle();

    public int getFirstSortColumn() {
        return getModelHeader().indexOf(aggregationController.getFirstAggregator().getColumnName());
    }


    public int getSecondSortColumn() {
        return getModelHeader().indexOf(aggregationController.getSecondAggregator().getColumnName());
    }

    public void calcReport() {

        //produce all leaf-level Security Reports
        for (InvestmentAccountWrapper invWrapper : currentInfo.getInvestmentWrappers()) {
            for (SecurityAccountWrapper secWrapper : invWrapper.getSecurityAccountWrappers()) {
                SecurityReport thisReport = getLeafSecurityReport(secWrapper, dateRange);
                securityReports.add(thisReport);
            }
        }
        LogController.logMessage(Level.FINE, String.format("Producing %d leaf security reports",
                securityReports.size()));

        // generate "All Securities" composite, add to composite reports
        CompositeReport allRept = getAllCompositeReport(dateRange, aggregationController);
        compositeReports.add(allRept);

        //generate composites and add Security Reports to them
        for (SecurityReport securityReport : securityReports) {
            for (CompositeReport compositeReport : compositeReports) {
                compositeReport.addTo(securityReport);
            }
            // generate composite based on first aggregate
            compositeReports.add(securityReport.getCompositeReport(
                    aggregationController, COMPOSITE_TYPE.FIRST));
            // if second AggClass isn't AllAggregate, need 1 or 2 more
            // aggregates
            if (securityReport.getAggregator(aggregationController.getSecondAggregator()) != AllAggregate
                    .getInstance()) {
                compositeReports.add(securityReport.getCompositeReport(aggregationController, COMPOSITE_TYPE.BOTH));
                // if second aggregate a subset of first, don't need
                // second aggregate alone (line above suffices)
                if (!isHierarchy)
                    compositeReports.add(securityReport.getCompositeReport(aggregationController,
                            COMPOSITE_TYPE.SECOND));
            }
        }
        LogController.logMessage(Level.FINE, String.format("Producing %d composite security reports",
                compositeReports.size()));
    }

    public void displayReport() throws SecurityException,
            IllegalArgumentException, NoSuchFieldException,
            IllegalAccessException {
        TotalReportOutputPane.createAndShowTable(this);
    }

    public ReportTableModel getReportTableModel() throws NoSuchFieldException, IllegalAccessException {
        return new ReportTableModel(getReportTable(), getModelHeader());
    }

    public ReportConfig getReportConfig() {
        return reportConfig;
    }

    /*
         * Class provides a generic TableModel which receives data from the
         * reporting methods above.
         */
    public final class ReportTableModel extends AbstractTableModel {
        @Serial
        private static final long serialVersionUID = -3662731131946834218L;
        public String[] columnNames;
        public transient Object[][] data;

        public ReportTableModel(Object[][] body, LinkedList<String> colNameList) {
            super();

            assert (body != null);

            this.columnNames = colNameList.toArray(new String[0]);
            this.data = body;
        }

        public void refreshReport(BulkSecInfo newCurrentInfo) throws Exception{
            TotalReport.this.securityReports = new HashSet<>();
            TotalReport.this.compositeReports = new HashSet<>();
            TotalReport.this.currentInfo = newCurrentInfo;
            TotalReport.this.calcReport();
            Object[][] newData = getReportTable();
            if(!isSameDimension(newData)) throw new Exception("Error on Refresh--different dimensions!");
            for (int i = 0; i < data.length; i++){
                Object [] row = data[i];
                Object [] newRow = newData[i];
                System.arraycopy(newRow, 0, row, 0, row.length);
            }
            fireTableDataChanged();
        }

        private boolean isSameDimension(Object[][] newData){
            boolean sameDimension = true;
            if(newData.length != data.length){
                return false;
            } else {
                for (int i = 0; i < data.length; i++)
                    if
                    (data[i].length != newData[i].length) {
                        sameDimension = false;
                        break;
                    }
                return  sameDimension;
            }
        }

        @Override
        public int getColumnCount() {
            return columnNames.length;
        }

        @Override
        public int getRowCount() {
            return data.length;
        }

        @Override
        public String getColumnName(int col) {
            return columnNames[col];
        }

        @Override
        public Object getValueAt(int row, int col) {
            return data[row][col];
        }

        @SuppressWarnings({"rawtypes"})
        @Override
        public Class getColumnClass(int c) {
            return getValueAt(0, c).getClass();
        }

        // Allows table to be editable
        @Override
        public boolean isCellEditable(int row, int col) {
            // Note that the data/cell address is constant, no matter where the
            // cell appears onscreen.
            return false;
        }

        // allows table to be editable
        @Override
        public void setValueAt(Object value, int row, int col) {
            data[row][col] = value;
            fireTableCellUpdated(row, col);
        }
    }
}